# Codebase Copilot

Ferramenta local-first que responde perguntas sobre um repositório com evidências verificáveis (arquivo + linhas). A documentação do produto e da arquitetura está em [`docs/`](docs/README.md), e as decisões técnicas em [`docs/10-DECISIONS.md`](docs/10-DECISIONS.md).

> Estado atual: Etapa 2 do [roadmap](docs/09-IMPLEMENTATION-ROADMAP.md) concluída (importação e inventário seguro). Só o backend existe; o frontend (`apps/web`) entra na Etapa 7.

## Pré-requisitos

- **JDK 25** no `PATH` (`java -version` deve mostrar 25).
- **Docker** com Docker Compose (para o PostgreSQL 17 + pgvector e para os testes de integração com Testcontainers).

O Maven não precisa ser instalado: use o wrapper `./mvnw`.

## Configuração

Copie o arquivo de exemplo e troque a senha:

```bash
cp .env.example .env
```

| Variável | Uso |
|---|---|
| `DB_NAME` | Nome do banco |
| `DB_USER` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco (troque o valor de exemplo) |
| `DB_PORT` | Porta do PostgreSQL no host |
| `PROJECTS_ROOT` | Opcional (a partir da Etapa 1). Caminho absoluto da pasta sob a qual ficam os repositórios que podem ser importados; gera a raiz permitida de key `default` (D-029). Vazio: só o fluxo ZIP fica disponível. |
| `PROJECTS_ROOT_LABEL` | Opcional. Rótulo da raiz `default` na UI. Padrão: "Projetos". |
| `DATA_DIR` | Opcional. Diretório de dados da aplicação; imports ZIP são extraídos em `DATA_DIR/imports/<id>/source` (D-028, D-033). Padrão: `~/.codebase-copilot`; um `~` inicial é expandido pela aplicação. |
| `EMBEDDING_PROVIDER` | Opcional. Provedor de embeddings: `none` (padrão; nada é enviado), `fake` (testes e demonstração) ou `openai` (D-047). |
| `EMBEDDING_API_KEY` | Chave da API do provedor; obrigatória com `openai`. Nunca aparece em logs nem em mensagens de erro. |

A porta do banco é definida uma única vez, em `DB_PORT` no `.env`, e usada em dois lugares: o Compose publica o container em `127.0.0.1:${DB_PORT}`, e o backend se conecta em `localhost:${DB_PORT}` (`application.yml`). Se a porta 5432 já estiver ocupada, altere apenas `DB_PORT`.

### Raízes permitidas e diretório de dados

O backend só lê pastas locais dentro das raízes permitidas; a UI navega nelas e envia apenas caminhos relativos (D-018). Na inicialização, a aplicação valida cada raiz (caminho absoluto, existente, diretório legível; key única no formato `[a-z0-9-]+`) e não inicia se alguma for inválida.

Para configurar **várias raízes**, copie o exemplo versionado e ajuste os caminhos:

```bash
cp apps/backend/config/application.yml.example apps/backend/config/application.yml
```

O `config/application.yml` é carregado automaticamente pelo Spring ao executar a partir de `apps/backend` e é ignorado pelo Git. A lista substitui por inteiro a raiz `default` gerada por `PROJECTS_ROOT`; o exemplo já a repete. Os testes não dependem desse arquivo: cada teste declara as próprias raízes.

O `DATA_DIR` precisa ficar **fora** de todas as raízes permitidas (sem estar dentro de uma delas nem contê-la) e fora deste repositório; caso contrário, a aplicação não inicia. Se não existir, é criado na inicialização.

Cada import ZIP usa a pasta `DATA_DIR/imports/<importJobId>`. A árvore extraída do import atual de cada projeto é mantida como origem do projeto; as demais são apagadas quando o import falha, é substituído ou o projeto é removido, e pastas órfãs são limpas na inicialização (D-033).

### Limites de importação e inventário

Os padrões são hipóteses a ajustar com medições e podem ser alterados por propriedades do Spring (por exemplo, em `config/application.yml`):

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.import.max-zip-bytes` | 100MB | Tamanho máximo do ZIP enviado; os limites do multipart derivam dele (`413 UPLOAD_TOO_LARGE`). |
| `app.import.max-entries` | 50000 | Entradas por ZIP. |
| `app.import.max-uncompressed-bytes` | 1GB | Total descompactado por ZIP. |
| `app.import.max-entry-bytes` | 100MB | Tamanho descompactado por entrada. |
| `app.import.max-compression-ratio` | 100 | Razão de compressão máxima, verificada a partir de `app.import.compression-ratio-min-bytes` (1MB). |
| `app.import.max-depth` / `max-path-length` | 64 / 1024 | Profundidade e comprimento dos caminhos no ZIP. |
| `app.import.max-concurrent` | 2 | Extrações simultâneas. |
| `app.inventory.max-entries` / `max-depth` | 200000 / 64 | Limites da varredura do inventário. |
| `app.inventory.max-file-bytes` / `max-project-bytes` | 1MB / 500MB | Padrões por projeto, ajustáveis em `PUT /projects/{id}/settings` até `max-file-bytes-limit` (10MB) e `max-project-bytes-limit` (2GB). |
| `app.browse.max-directories` | 1000 | Itens por listagem de diretórios. |

### Indexação

A indexação (`POST /api/v1/projects/{id}/index-jobs`) lê os arquivos incluídos pela varredura e grava o conteúdo, os chunks e os snapshots no PostgreSQL (`file_contents`, `chunks`, `snapshot_files`, `snapshots`); nada é gravado no `DATA_DIR`. Remover o projeto apaga tudo em cascata. Os jobs ficam numa fila em tabela e são retomados após uma queda (D-040).

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.indexing.max-concurrent` | 1 | Jobs de indexação simultâneos. |
| `app.indexing.poll-interval` | 2s | Intervalo de sondagem da fila. |
| `app.indexing.heartbeat-interval` | 10s | Renovação do lease de um job em execução. |
| `app.indexing.lease-timeout` | 60s | Tempo sem heartbeat após o qual um job `RUNNING` volta à fila. |
| `app.indexing.max-attempts` | 3 | Tentativas por job antes de falhar. |
| `app.indexing.batch-files` | 100 | Arquivos gravados por transação. |
| `app.indexing.retained-snapshots` | 3 | Snapshots anteriores mantidos com arquivos, além do ativo. |

### Embeddings

Com `EMBEDDING_PROVIDER=openai`, o texto dos chunks indexados e as perguntas de busca são enviados ao provedor (`docs/07-SECURITY-PRIVACY-COSTS.md` §7). Com `none`, nada sai da máquina.

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.embeddings.base-url` | `https://api.openai.com/v1` | Endereço da API do provedor. |

**Atenção:** `app.embeddings.base-url` decide para onde vão a chave e os trechos de código. Mudar esse endereço muda o destino dos dados. Só são aceitos endereços `https`; `http` é aceito apenas para `localhost` ou `127.0.0.1` (servidor de teste ou servidor local). Qualquer outro valor, ou um endereço com usuário e senha embutidos, impede a aplicação de iniciar.

O `.env` está no `.gitignore` e nunca deve ser commitado.

## Subir o banco

Na raiz do repositório:

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

As extensões `vector` e `pg_trgm` são criadas pelo Flyway quando o backend inicia.

## Build e testes

```bash
cd apps/backend
./mvnw verify
```

O `verify` executa:

- testes unitários e de integração (os de integração sobem o próprio PostgreSQL via Testcontainers; não dependem do banco do Compose nem do `.env`);
- verificação de formatação com Spotless (google-java-format, estilo AOSP). Para corrigir a formatação: `./mvnw spotless:apply`;
- comparação do contrato OpenAPI gerado com o arquivo versionado `apps/backend/openapi.json` (D-023). Após uma mudança intencional na API, regenere com `./mvnw verify -Dopenapi.update=true` e commite o `openapi.json` junto com o código.

### Formatação no VS Code

O `.vscode/settings.json` formata Java ao salvar com a extensão recomendada `serikb.google-java-format`, usando as mesmas regras do Spotless (`--aosp --skip-sorting-imports --skip-reflowing-long-strings`). A extensão procura o executável no `PATH`; instale-o com:

```bash
brew install google-java-format
```

O formatador padrão do VS Code (Eclipse) não segue o estilo AOSP e faz o `spotless:check` falhar.

## Executar o backend

```bash
cd apps/backend
./mvnw spring-boot:run
```

**O backend deve ser executado a partir de `apps/backend`.** O `application.yml` importa o `.env` pelo caminho relativo `../../.env`; executado de outro diretório, as variáveis `DB_*` não são encontradas e a aplicação não inicia.

Com o backend no ar:

- Health check: <http://localhost:8080/actuator/health>
- Contrato OpenAPI: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
