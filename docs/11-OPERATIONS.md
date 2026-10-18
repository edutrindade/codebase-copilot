# Operação, recuperação de falhas e limitações — Codebase Copilot

Como rodar o produto, onde ficam os dados, como fazer backup, o que fazer quando algo falha e o que o MVP não faz. Os comandos partem da raiz do repositório, salvo indicação. Configuração e variáveis: `README.md`.

## 1. Como rodar

| Modo | Comandos | Endereço |
|---|---|---|
| Empacotado (uso) | `cd apps/backend && ./mvnw -Pwith-ui clean package -DskipTests && java -jar target/backend-0.0.1-SNAPSHOT.jar` | <http://localhost:8080> (UI e API) |
| Desenvolvimento | `./mvnw spring-boot:run` em `apps/backend` e `npm run dev` em `apps/web` | <http://localhost:5173> (UI), 8080 (API) |

- Antes de tudo, o banco: `docker compose -f infra/docker-compose.yml --env-file .env up -d`.
- O backend é executado **a partir de `apps/backend`**: o `.env` é lido pelo caminho relativo `../../.env`. Fora dali, a aplicação não acha as variáveis `DB_*` e não sobe.
- O perfil `with-ui` exige Node 22 e npm na máquina. Ao alternar entre o build com e sem a UI, use `clean` (D-085).
- O backend escuta só em `127.0.0.1` e não tem autenticação (D-072). Não exponha a porta à rede.
- Saúde: `GET /actuator/health`. Logs na saída padrão, com o `requestId` de cada requisição; nunca com código, perguntas, respostas, caminhos de arquivo ou chaves.

## 2. Onde ficam os dados

| O quê | Onde | Observação |
|---|---|---|
| Projetos, snapshots, conteúdo dos arquivos, chunks, índice lexical, vetores, conversas | PostgreSQL, volume Docker `infra_pgdata` | Tudo o que a UI mostra. Remover o projeto apaga em cascata (D-021). |
| ZIPs extraídos | `DATA_DIR/imports` (padrão `~/.codebase-copilot`) | Necessários para reindexar um projeto ZIP; o import anterior é removido quando outro termina. |
| Pastas locais | Onde já estão (`PROJECTS_ROOT` ou `app.local-roots`) | Só lidas, nunca alteradas. |
| Configuração | `.env` e, opcional, `apps/backend/config/application.yml` | Fora do Git. |

Tamanho esperado no banco: cerca de 7 vezes o texto indexado (D-080). Cada projeto guarda o snapshot ativo e os 3 anteriores com arquivos (`app.indexing.retained-snapshots`); os mais antigos são podados ao fim de cada indexação, e o conteúdo sem snapshot é removido.

## 3. Backup e restauração

O backup do banco contém o **código indexado** e as conversas: guarde-o com o mesmo cuidado do repositório original.

**Backup** (com o banco no ar; o backend pode continuar rodando):

```bash
docker compose -f infra/docker-compose.yml --env-file .env exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > codebase-copilot.dump
```

As credenciais vêm das variáveis do próprio contêiner, sem aparecer na linha de comando. Para projetos ZIP, copie também `DATA_DIR/imports`; sem ele, os snapshots e as conversas continuam acessíveis, mas reindexar exige enviar o ZIP de novo.

**Restauração** (pare o backend antes):

```bash
docker compose -f infra/docker-compose.yml --env-file .env exec -T postgres \
  sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose -f infra/docker-compose.yml --env-file .env exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < codebase-copilot.dump
```

- O dump traz o histórico do Flyway e a extensão `vector`. Restaurar um dump antigo numa versão nova do backend é seguro: as migrações que faltam rodam na inicialização. O contrário (dump de uma versão mais nova num backend mais antigo) não é suportado.
- Conferido em 17/10/2026 restaurando num banco temporário: 12 migrações e pgvector 0.8.7.
- Alternativa: copiar o volume `infra_pgdata` com o banco **parado**. Copiar o volume com o PostgreSQL rodando produz uma cópia inconsistente.
- Para recomeçar do zero: `docker compose -f infra/docker-compose.yml --env-file .env down -v` apaga o volume e todos os dados. Não tem volta.

## 4. Falhas e o que fazer

Cada falha aparece na UI com uma mensagem em pt-BR e uma ação; na API, como ProblemDetail com um `code` estável (`06` §2).

### Banco fora do ar
- **Na inicialização:** o backend não sobe (a conexão e o Flyway falham). Suba o banco e inicie de novo.
- **Durante o uso:** as requisições respondem `500 INTERNAL_ERROR`, com o `requestId` para achar a linha no log. O worker de indexação registra que não conseguiu pegar um job e tenta de novo a cada intervalo. Um job em execução que falha ao gravar volta à fila até `app.indexing.max-attempts` (3) e depois termina com `INDEX_IO_ERROR`. Uma pergunta em andamento pode ficar `PENDING` e é marcada `ANSWER_INTERRUPTED` na próxima inicialização.
- **O que fazer:** subir o banco (`docker compose … up -d`); não é preciso reiniciar o backend. Refazer a indexação que falhou.

### Provedor sem saldo, sem chave válida ou fora do ar
- **Indexação:** `EMBEDDING_QUOTA_EXCEEDED` (créditos ou limite de gastos) e `EMBEDDING_PROVIDER_UNAUTHORIZED` falham o job na hora. Indisponibilidade e limite de taxa são repetidos até `app.embeddings.max-retries` e depois viram `EMBEDDING_PROVIDER_UNAVAILABLE` ou `EMBEDDING_RATE_LIMITED`. Em todos os casos, o snapshot ativo não muda e os vetores já gravados ficam no cache: o próximo job não paga por eles de novo (D-051).
- **Pergunta:** vira uma resposta `FAILED` com o código do provedor (`GENERATION_QUOTA_EXCEEDED`, `GENERATION_PROVIDER_UNAVAILABLE`, `EMBEDDING_…` para o vetor da pergunta), sem repetição e sem custo novo. Numa falha da geração, as evidências recuperadas aparecem mesmo assim.
- **O que fazer:** conferir créditos e limites da conta; corrigir a chave no `.env` e reiniciar. Para trabalhar sem provedor: `EMBEDDING_PROVIDER=none` (só a busca lexical) e `GENERATION_PROVIDER=none` (as perguntas respondem `409 GENERATION_NOT_AVAILABLE`, a busca continua).

### Queda do backend no meio de um trabalho
- **Indexação:** o job fica `RUNNING` até o heartbeat vencer (`app.indexing.lease-timeout`, 60 s) e é retomado sozinho do começo, reaproveitando o conteúdo e os vetores já gravados. Depois de 3 quedas seguidas termina com `INDEX_ATTEMPTS_EXHAUSTED`. Um pedido de cancelamento feito antes da queda é honrado na retomada (D-084).
- **Importação ZIP:** marcada `IMPORT_INTERRUPTED` na inicialização seguinte, e as pastas de importação sem job são removidas. Envie o ZIP de novo.
- **Pergunta:** marcada `ANSWER_INTERRUPTED` na inicialização seguinte. Pergunte de novo.

### Disco cheio
- **No banco (volume Docker):** as gravações falham; a indexação volta à fila e termina com `INDEX_IO_ERROR`, sem mudar o snapshot ativo. No Mac, o limite costuma ser o disco da VM do Docker, não o do Mac.
- **No `DATA_DIR`:** a importação ZIP termina com `IMPORT_IO_ERROR` e o import atual continua valendo.
- **O que fazer:** liberar espaço (projetos que não usa mais, `app.indexing.retained-snapshots` menor, imagens e volumes Docker antigos). Linhas apagadas só devolvem espaço ao sistema depois de um `VACUUM FULL`, que trava as tabelas enquanto roda: faça com o backend parado.

### ZIP recusado
A importação termina `FAILED` e o import anterior do projeto continua valendo. Códigos (`06` §6):

| Código | Causa | O que fazer |
|---|---|---|
| `UPLOAD_TOO_LARGE` (413) | ZIP acima de `app.import.max-zip-bytes` (100 MB) | Excluir dependências e build do ZIP, ou aumentar o limite |
| `ZIP_INVALID` | Arquivo corrompido ou não é ZIP | Gerar o ZIP de novo |
| `ZIP_UNSAFE_PATH` | Caminho absoluto, com `..`, controles ou caracteres bidirecionais | Revisar a origem do ZIP: é o sinal de um arquivo malicioso |
| `ZIP_DUPLICATE_ENTRY` | Dois nomes iguais (inclusive só em maiúsculas ou na grafia Unicode) | Renomear um deles |
| `ZIP_TOO_MANY_ENTRIES`, `ZIP_ENTRY_TOO_LARGE`, `ZIP_UNCOMPRESSED_TOO_LARGE`, `ZIP_COMPRESSION_RATIO_EXCEEDED`, `ZIP_TOO_DEEP` | Limites contra bombas de descompressão (D-035) | Remover o que não é código, ou ajustar `app.import.*` |

### Projeto grande demais ou sem arquivos
- `SCAN_LIMIT_REACHED`, `PROJECT_SIZE_LIMIT_EXCEEDED`, `EMBEDDING_CHUNK_LIMIT_EXCEEDED` (50 mil chunks): revisar as exclusões na pré-análise (dependências, dados, gerados) ou ajustar os limites, sabendo que a busca chega perto de 1 s com 50 mil chunks (D-080).
- `NO_INCLUDED_FILES`: as exclusões removeram tudo; revisar a pré-análise.

### Origem mudou ou sumiu
`SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE`, `SOURCE_ROOT_NOT_CONFIGURED`, `SOURCE_NOT_IMPORTED`: a pasta saiu do lugar, virou link, perdeu a permissão de leitura, a raiz saiu da configuração ou o projeto ZIP não tem import. Os snapshots existentes continuam consultáveis; para reindexar, restaurar a pasta ou a raiz, ou enviar o ZIP.

### Snapshot podado
O conteúdo do arquivo de um snapshot antigo responde `409 SNAPSHOT_PRUNED`. As evidências das respostas continuam visíveis, porque o trecho é copiado no momento da resposta (D-046); a UI mostra a cópia e oferece a versão do snapshot ativo. Para guardar mais snapshots com arquivos, aumentar `app.indexing.retained-snapshots`.

### Acesso recusado
`403 FORBIDDEN_HOST` (o endereço usado não é `localhost`, `127.0.0.1` ou `[::1]`) ou `403 FORBIDDEN_ORIGIN` (mudança de estado vinda de uma página fora das origens aceitas). Acessar pela URL local, ou listar o nome em `app.http.allowed-hosts` e a origem em `app.http.allowed-origins` (D-072, D-085).

### Indexação lenta ou presa
Acompanhar a etapa e o progresso no resumo do projeto. Um job pode ser cancelado (botão "Cancelar" ou `POST /index-jobs/{id}/cancel`); ele para no próximo lote e o snapshot ativo não muda (D-084).

### Snapshots anteriores à detecção de segredos
Snapshots feitos antes da Etapa 8 (F6) podem conter arquivos que hoje seriam excluídos por um possível segredo no conteúdo, sem aviso em `outdatedReasons` (D-083). **Reindexe** os projetos indexados antes disso.

## 5. Limitações conhecidas

Consolidadas dos documentos `02` a `08` e dos planos das Etapas 1 a 8; cada uma com a decisão que a registra.

### Acesso e segurança
- **Sem autenticação.** Só a rede e páginas de terceiros ficam de fora; outro processo da mesma máquina, ou uma página servida numa origem aceita, alcança a API (D-072).
- **Prompt injection é reduzido, não eliminado.** O conteúdo vai delimitado e as citações são validadas, mas o modelo ainda pode seguir uma instrução do repositório. O efeito com o modelo real não foi medido (D-066, D-082).
- **A detecção de segredos não é garantia.** Só nomes de arquivo e padrões de alta precisão (D-036, D-083); uma senha sem formato próprio passa.
- **A Swagger UI fica fora da CSP estrita** (usa scripts inline); o resto recebe a política completa (D-085).
- **Backups do banco contêm o código indexado** (§3).

### Importação e varredura
- **Links físicos e pontos de montagem** dentro da raiz não são detectados; uma troca concorrente de diretório por link durante a varredura (TOCTOU) não é totalmente evitável (D-037, D-082).
- **No Linux:** um diretório com nome em NFD não abre pelo navegador de pastas, e uma pasta com dois nomes iguais depois da normalização NFC falha a indexação com `INDEX_IO_ERROR` (D-082).
- **ZIP:** nomes que só diferem em maiúsculas ou na grafia Unicode são recusados (`ZIP_DUPLICATE_ENTRY`), mesmo que o projeto original os tenha. ZIP dentro de ZIP não é aberto. A importação não pode ser cancelada.
- **Só UTF-8** (com ou sem BOM). Latin-1 e UTF-16 ficam `UNSUPPORTED` (D-042, D-082).
- **Mudanças no disco não são detectadas:** a pasta local é relida só quando a pessoa indexa de novo.

### Indexação
- **Um processo do backend por banco.** Dois funcionariam (`SKIP LOCKED` e lease), mas não é um cenário testado (D-040).
- **Um chunk recusado pelo provedor** derruba o job inteiro (`EMBEDDING_REQUEST_REJECTED`); o lote não é dividido para achar o texto (D-054).
- **Limite prático de 50 mil chunks por projeto** (~110–140 MB de código); acima, o job falha no limite (D-080).
- **Cancelamento** leva até um lote (ou as tentativas de uma chamada ao provedor) para ter efeito; um pedido que chega depois da última verificação não impede a conclusão (D-084).
- **Chunking por linhas** (`lines-v1`), sem análise sintática: um chunk pode cortar uma função ao meio (D-043; AST na Etapa 10).
- **Snapshots que falharam ou foram cancelados** ficam na lista, sem arquivos.

### Busca e respostas
- **Lexical sem IDF nem BM25:** uma pergunta com um termo presente em quase todo chunk tem p95 de 1,06 s com 46 mil chunks (D-057, D-080).
- **Cada pergunta é independente:** o histórico da conversa não vai ao modelo, e não há nova recuperação quando falta suporte (D-086).
- **Sem realce de sintaxe** no visualizador (D-086).
- **Custo é estimativa**, a partir dos tokens informados e dos preços configurados; sem preço ou sem tokens, fica `UNKNOWN` (D-054, D-067).
- **O uso agregado** conta só o que está guardado: conversas excluídas saem da conta, e "evidências abertas" não é medido (D-084).

### Avaliação e provedores
- **Só a recuperação lexical foi medida.** O vetorial, o híbrido, a avaliação de respostas, os testes de fumaça das Etapas 4 e 6 e as revisões da D-017, O-004, O-005 e O-010 esperam saldo no provedor (F3 da Etapa 8; `08` §9).
- **Um par de modelos por instalação:** trocar o modelo de embeddings exige reindexar (D-048, D-050).

### Execução
- **O jar precisa rodar de `apps/backend`** para achar o `.env`; o perfil `with-ui` precisa de Node e de `clean` ao alternar (D-085).
- **Sem instalador nem imagem Docker do backend:** o empacotamento é um jar para uso local.
