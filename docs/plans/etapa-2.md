# Plano — Etapa 2: Importação e inventário seguro

**Status:** concluído em 16/10/2026 (branch `etapa-2`). Propostas P1 a P6 aprovadas em 15/10/2026 e registradas como D-033 a D-038. Ver "Resultado e desvios" ao final.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 2), `docs/plans/etapa-1.md` (pendências), D-012, D-018, D-020, D-021, D-028, D-029, D-031, O-009, O-012.

## Escopo

**Dentro:**
- `DATA_DIR` com validação na inicialização (D-028);
- navegação nas raízes permitidas (`GET /local-roots` e `GET /local-roots/{rootKey}/directories`);
- `import_jobs`, `projects.current_import_job_id` e `409 PROJECT_BUSY` no `DELETE /projects/{id}`;
- upload ZIP com limites, concorrência, extração segura e recuperação após queda;
- varredura segura (caminhos, links, arquivos especiais e limites) de pasta local e de ZIP extraído;
- exclusões padrão e customizadas;
- inventário por linguagem, diretório e estado (pré-análise), com avisos.

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Tabela `files`, hashes de conteúdo e persistência do inventário | Etapa 3 (O-008) |
| `index_jobs`, snapshots e fila de jobs (O-009) | Etapa 3 |
| `PROJECT_BUSY` por job de indexação | Etapa 3 (a porta da F2 já prevê) |
| Detecção de segredos por conteúdo (regex/entropia) | Etapa 8, com segredos sintéticos no corpus |
| Padrões de inclusão (whitelist) e negação (`!padrão`) | sem previsão (ver D-036 proposta) |
| Cancelamento de import em andamento | sem previsão (D-021) |
| Detecção de links físicos (hard links) e pontos de montagem dentro da raiz | Etapa 8 (limitação documentada) |
| Upload de pasta pelo navegador (`webkitdirectory`) | fora do MVP (D-018) |

## Decisões aprovadas

Todas as propostas abaixo foram aprovadas em 15/10/2026, cada uma com a opção recomendada. Cada item vira registro em `10-DECISIONS.md` na fatia indicada.

### P1 — Retenção do conteúdo extraído e fronteira import/indexação (revisa D-028) → D-033, F3
**Problema:** a D-028 apaga a pasta do job quando ele conclui, mas a indexação só existe na Etapa 3, e a própria D-028 deixou essa fronteira para a Etapa 2.
**Opções:**
1. Manter a D-028: o import só conclui depois da indexação (impossível nesta etapa).
2. O import conclui após extração e validação; a árvore extraída do **import atual** é retida como a "pasta" do projeto ZIP.

**Recomendação:** opção 2.
- `DATA_DIR/imports/<id>/source/` é a origem do projeto ZIP enquanto `<id>` for o `current_import_job_id`; a indexação (Etapa 3) a lê como lê uma pasta local.
- A pasta é apagada quando o job é `REJECTED`/`FAILED`, quando um import mais novo conclui (a do anterior), e na remoção do projeto.
- Isso também resolve o gatilho de revisão já previsto na D-028 (reindexar ZIP sem novo upload).
- Custo: disco limitado por `app.import.max-uncompressed-bytes` por projeto ZIP.

### P2 — Execução do import nesta etapa (não resolve a O-009) → D-034, F3
**Recomendação:** executor em processo (`ThreadPoolTaskExecutor` dedicado, `app.import.max-concurrent`, padrão 2), com o job submetido **após o commit**. Na inicialização, jobs `PENDING`/`EXTRACTING` viram `FAILED` com `IMPORT_INTERRUPTED` e suas pastas são apagadas. A extração é curta e recomeçar exige só novo upload, então não há retomada. A fila em tabela da O-009 continua para os jobs de indexação da Etapa 3.

### P3 — Inventário calculado sob demanda → D-035, F4
**Recomendação:** `GET /projects/{projectId}/inventory` faz a varredura segura a cada chamada e **não persiste** nada (a tabela `files` depende da O-008). A varredura é limitada por `app.inventory.max-entries` e `max-depth`; ao atingir o limite, a resposta vem com `truncated: true` e aviso. A persistência vem com os snapshots na Etapa 3.

### P4 — Regras de exclusão (resolve a O-012) → D-036, F4 e F5
**Recomendação:**
- **Segredos (O-012):** no MVP, apenas padrões de nome (grupo `SENSITIVE`). A detecção por conteúdo fica para a Etapa 8. A UI e a API nunca afirmam que segredos foram "detectados", só que arquivos foram excluídos por padrão.
- **Regras padrão** com `id` estável, categoria e flag `overridable`:
  - não desativáveis: `SENSITIVE` e `VCS`;
  - desativáveis por `id`: `DEPENDENCIES`, `BUILD_OUTPUT`, `IDE_CACHE` e `GENERATED`;
  - `MEDIA_BINARY` não é desativável (o conteúdo não seria indexável de qualquer forma).
- **Customização:** `excludePatterns` (somam-se às regras padrão) e `disabledDefaultRules` (ids). **Sem `include_patterns`:** desativar uma regra resolve o caso comum (ex.: indexar `vendor/`) sem a complexidade de reincluir caminhos dentro de diretórios podados. Isso ajusta `project_settings` em `05-DATA-MODEL.md`.
- **Sintaxe própria, independente do SO:**
  - padrões relativos à raiz do projeto, com `/` como separador;
  - `*` (qualquer sequência sem `/`), `**` (zero ou mais segmentos) e `?` (um caractere sem `/`);
  - sem `/` (exceto o final), o padrão casa o nome em qualquer profundidade; com `/`, é ancorado na raiz (o `/` inicial é opcional);
  - `/` final restringe a diretórios, e diretórios excluídos não são percorridos;
  - comparação sensível a maiúsculas;
  - `!` inicial, `[`, `]`, `\`, caracteres de controle e segmento `..` → `400 VALIDATION_FAILED`.

### P5 — Política de links, arquivos especiais e limites de varredura → D-037, F1 e F4
**Recomendação:**
- **Links simbólicos** nunca são seguidos nem listados na navegação. Na varredura, aparecem como `EXCLUDED/SYMLINK`. Se algum segmento do caminho do projeto for um link, o inventário responde `409 SOURCE_IS_SYMLINK`.
- **Arquivos especiais** (FIFO, socket, dispositivo) são `EXCLUDED/SPECIAL_FILE` e **nunca são abertos**: abrir um FIFO bloqueia a thread.
- Todo acesso usa `LinkOption.NOFOLLOW_LINKS` e atributos lidos sem seguir links. O caminho real final precisa começar pelo caminho real da raiz (defesa em profundidade).
- **Limitações documentadas:**
  - links físicos e pontos de montagem dentro da raiz não são detectados;
  - trocas concorrentes de diretório por link (TOCTOU) não são totalmente evitáveis sem `openat`.

  O modelo de ameaça é conteúdo malicioso, não um atacante concorrente no disco local.

### P6 — Ajustes de contrato (`06-API-CONTRACTS.md`) → F1, F4, F5
**Recomendação:**
- `GET /local-roots/{rootKey}/directories` devolve um **objeto** `{ path, directories, truncated }`, e não um array, para não truncar em silêncio.
- Configurações por `PUT /projects/{projectId}/settings` (substituição completa) e `GET` correspondente, em vez de `PATCH`: o JSON merge patch exigiria distinguir campo ausente de `null`, sem ganho para a UI.
- Novos códigos de erro estáveis, um por situação, para o frontend não interpretar texto (D-022).
- Novo `GET /projects/{projectId}/imports` (paginado), para a UI reencontrar um import em andamento depois de recarregar a página.

## Princípios de execução

- Valem os da Etapa 1: fatias entregáveis, `./mvnw verify` verde ao fim de cada uma, e o `openapi.json` regenerado e commitado (`-Dopenapi.update=true`, D-023).
- **Nunca executar conteúdo importado** (D-012): nada de `ProcessBuilder`, scripts, hooks ou class loading sobre arquivos importados. Os arquivos são lidos apenas como bytes.
- **Não logar** caminhos, nomes de entradas nem conteúdo de arquivos importados. Os logs trazem apenas `importJobId`, `projectId`, contagens, durações e o código de erro.
- **Sem caminhos absolutos na API:** os testes de contrato verificam que nenhuma resposta contém o caminho real das raízes nem do `DATA_DIR`.
- **Fronteiras (D-031):**
  - `importjob` depende de `project`, nunca o contrário: `project` define uma porta, e `importjob` a implementa;
  - `codeanalysis` (detecção de linguagem) não depende de `importjob`.
- **Testes de integração:**
  - a configuração compartilhada (`@IntegrationTest`) ganha `DATA_DIR` temporário e limites pequenos de import e varredura, para os testes não precisarem de arquivos grandes;
  - continuam valendo o contexto "sem raízes" e o do `@MockitoBean`;
  - a F3 acrescenta um quarto contexto (`RANDOM_PORT`), justificado no próprio teste.
- **Assincronia nos testes:** com Awaitility (dependência de teste, versão gerenciada pelo Spring Boot). Todo teste que dispara um import espera o estado terminal antes de terminar, e o `DatabaseCleaner` também limpa `DATA_DIR/imports`.

## Fatias, na ordem

### F0 — `DATA_DIR`, utilitários de caminho e infraestrutura de teste (sem migração)

**Entregas**
- **`shared.datadir.DataDirectory`** (`app.data-dir`, padrão `${DATA_DIR:~/.codebase-copilot}`), com validação na inicialização (a aplicação não sobe se falhar):
  - `~` ou `~/` inicial expandido a partir de `user.home` (`~usuario` não é suportado → falha);
  - absoluto depois da expansão; criado se não existir; diretório legível e gravável;
  - o caminho **real** não pode ser igual a nenhuma raiz permitida, estar dentro de uma delas ou contê-la (os dois sentidos, para que arquivos extraídos não apareçam na navegação);
  - fora do repositório do Codebase Copilot: sobe-se a partir de `user.dir` até achar `.git` (diretório ou arquivo, por causa de worktrees). Sem `.git` (aplicação empacotada), essa verificação é ignorada;
  - cria `imports/`; expõe `importDir(UUID)`. O caminho nunca sai do backend.
- **`RelativePaths` passa de `project` para `shared.fs`** (público), sem mudar o comportamento, para ser reusado na navegação e nos padrões.
- **`shared.fs.ConfinedPaths`:** resolve um caminho relativo normalizado dentro de um diretório base:
  - cada segmento é verificado com `NOFOLLOW_LINKS` e precisa ser um diretório real, não um link;
  - devolve um resultado tipado (`FOUND`, `NOT_FOUND`, `NOT_A_DIRECTORY`, `SYMLINK`, `NOT_READABLE`), que cada chamador traduz para o próprio `ErrorCode`;
  - confere que o caminho real resultante começa pelo caminho real da base.
- **Infraestrutura de teste:**
  - um `ApplicationContextInitializer` de teste, registrado no `@IntegrationTest` e no contexto sem raízes, cria por JVM um `DATA_DIR` temporário (em `java.io.tmpdir`, fora do repositório) e a árvore de raízes de teste em `target/test-roots/{default,work}`, incluindo links, arquivo ilegível e, quando o SO permitir, FIFO;
  - substitui `src/test/resources/local-roots`, porque o Git não preserva permissões e links não são portáveis;
  - os testes da Etapa 1 continuam passando sem mudança de comportamento.
- `application.yml`, `.env.example` e `config/application.yml.example` revisados (o `DATA_DIR` já está documentado no `.env.example`).

**Testes**
- `ApplicationContextRunner`, sem banco. A inicialização falha para:
  - `~usuario/x` e caminho relativo;
  - `DATA_DIR` que aponta para um arquivo ou que não é gravável (condicional ao SO);
  - `DATA_DIR` igual a uma raiz, dentro de uma raiz ou contendo uma raiz, inclusive quando a relação só aparece ao resolver links (raiz acessada por link);
  - `DATA_DIR` dentro de um diretório com `.git` acima de `user.dir`.
- Ainda com `ApplicationContextRunner`: `~` e `~/x` expandidos; diretório inexistente criado, junto com `imports/`.
- Unitário de `ConfinedPaths` (`@TempDir`):
  - raiz; subdiretório; inexistente; arquivo no lugar de diretório;
  - link no meio do caminho e no último segmento, apontando para dentro e para fora da base: sempre `SYMLINK`;
  - diretório ilegível (condicional).
- Os testes de `RelativePaths` são movidos junto, sem mudança.

### F1 — Navegação nas raízes permitidas

**Entregas**
- Controller e serviço em `importjob.browse` (D-031 previa as raízes em `shared` justamente para este uso).
- **`GET /local-roots`:** `[{ key, label }]`, na ordem da configuração; `[]` sem raízes.
- **`GET /local-roots/{rootKey}/directories?path=`:**
  - `path` ausente ou vazio = a própria raiz; normalização pelo `RelativePaths`;
  - resolução pelo `ConfinedPaths`;
  - lista apenas os **subdiretórios imediatos**: links e arquivos são omitidos;
  - itens `{ name, relativePath, hasChildren, readable }`, em que `hasChildren` para no primeiro subdiretório real encontrado;
  - ordem por nome sem distinção de maiúsculas, com desempate pela ordem sensível, para ser determinística;
  - limite `app.browse.max-directories` (padrão 1000); acima dele, `truncated: true`.

```json
{
  "path": "clientes",
  "directories": [
    { "name": "api", "relativePath": "clientes/api", "hasChildren": true, "readable": true }
  ],
  "truncated": false
}
```

- **Erros, nesta ordem:**
  1. `400 VALIDATION_FAILED` (`field = "path"`): NUL, `\` ou caracteres de controle;
  2. `404 ROOT_NOT_FOUND`, inclusive sem raízes configuradas;
  3. `400 PATH_OUTSIDE_ROOT`: `/` inicial ou `..`;
  4. `404 DIRECTORY_NOT_FOUND`: inexistente, não é diretório ou passa por link (os links são invisíveis, então não se revela que existem);
  5. `409 DIRECTORY_NOT_READABLE`.
- Novos `ErrorCode`: `DIRECTORY_NOT_FOUND` e `DIRECTORY_NOT_READABLE`, com as chaves de mensagem.

**Testes**
- Contrato da API, no contexto com raízes:
  - `GET /local-roots` devolve `default/Projetos` e `work/Trabalho`, sem `path`;
  - listagem da raiz e de um subnível, com ordem determinística, `hasChildren` verdadeiro e falso, e `readable: false` para diretório ilegível (condicional);
  - link para diretório, dentro ou fora da raiz, não aparece; arquivos não aparecem;
  - `path` com link no meio → 404; `../x` e `/etc` → 400 `PATH_OUTSIDE_ROOT`; `a\b` → 400 `VALIDATION_FAILED`; arquivo → 404; `rootKey` inexistente → 404;
  - `truncated` com o limite de teste reduzido;
  - nenhum corpo de resposta contém o caminho real das raízes.
- No contexto sem raízes: `GET /local-roots` → `[]`; `directories` → `404 ROOT_NOT_FOUND`.
- Precedência: um caso por fronteira entre passos vizinhos (1/2, 2/3, 3/4).

### F2 — Jobs de importação no modelo e `PROJECT_BUSY` (V4)

**Entregas**
- Migração V4 (abaixo): `import_jobs`, `projects.current_import_job_id` e as constraints.
- **Agregado `ImportJob` e `ImportJobRepository` em `importjob`:** insert explícito (como na Etapa 1) e transições de estado via `JdbcClient`, condicionadas ao estado atual (`UPDATE … WHERE status = :expected`).
- **Porta `ProjectImports` em `project`, implementada em `importjob`:**
  - `boolean hasActiveImport(UUID projectId)`;
  - `Map<UUID, ImportSummary> summaries(Collection<UUID> importIds)`, em lote, para `currentImport` em listagens sem N+1;
  - `void onProjectDeleted(UUID projectId)`: dentro da transação, coleta os IDs dos imports e registra a remoção das pastas **após o commit**.
- **`Project` ganha `currentImportJobId`:** `source.currentImport` passa a ser preenchido com `{ id, status }` pela porta (`null` até o primeiro import concluído).
- **`DELETE /projects/{id}`**, na ordem:
  1. `SELECT … FOR UPDATE` no projeto, ou `404`;
  2. `hasActiveImport` → `409 PROJECT_BUSY`;
  3. `onProjectDeleted`;
  4. `DELETE` com cascata.

  O `FOR UPDATE` conflita com o `KEY SHARE` que o insert em `import_jobs` toma no projeto. Assim, um upload concorrente ou termina antes (e o delete vê o job ativo) ou espera e falha pela FK (`PROJECT_NOT_FOUND`).
- **Método em `ProjectService` para `importjob` atualizar o import atual** (`replaceCurrentImport(projectId, importId)`), que devolve o anterior.
- Novo `ErrorCode`: `PROJECT_BUSY`.
- ArchUnit: `project` não depende de `importjob`.

**Testes**
- Integração do repositório, com SQL direto:
  - os `CHECK`s de estado e de terminalidade (`completed_at` e `error_code` coerentes com `status`);
  - segundo job ativo no mesmo projeto viola `import_jobs_active_unique_idx`;
  - `current_import_job_id` em projeto `LOCAL_FOLDER` viola o `CHECK`;
  - `current_import_job_id` apontando para import de **outro** projeto viola a FK composta.
- Cascata: remover um projeto com `current_import_job_id` preenchido apaga os imports sem erro (referência circular, com `SET NULL` só na coluna).
- Contrato da API:
  - projeto com job `PENDING` inserido via `JdbcClient` → `DELETE` responde `409 PROJECT_BUSY` com `ProblemDetail` completo, e o projeto continua existindo;
  - com o job em `COMPLETED` → 204, os imports somem, e as pastas `DATA_DIR/imports/<id>` (criadas pelo teste) são apagadas após o commit;
  - `GET /projects/{id}` de projeto ZIP com import atual traz `currentImport.id` e `status = "COMPLETED"`; a listagem traz o mesmo, sem N+1 (uma consulta em lote por página).
- Rollback: se o `DELETE` falhar depois de `onProjectDeleted`, as pastas continuam lá.

### F3 — Upload e extração ZIP

**Entregas**
- **Configuração `app.import.*`** (valores padrão são hipóteses a medir, D-028):
  - `max-zip-bytes`: 100 MB;
  - `max-entries`: 50.000;
  - `max-uncompressed-bytes`: 1 GB;
  - `max-entry-bytes`: 100 MB;
  - `max-compression-ratio`: 100, aplicado a entradas acima de 1 MB descompactado;
  - `max-depth`: 64;
  - `max-path-length`: 1.024 caracteres;
  - `max-concurrent`: 2.
- **Multipart coerente com `max-zip-bytes`:**
  - `spring.servlet.multipart.max-file-size` e `max-request-size` derivados da mesma propriedade, com o limite efetivo igual ao do request;
  - validação na inicialização de que o multipart não é menor que `max-zip-bytes`.
- **`POST /projects/{projectId}/imports/zip`** (`multipart/form-data`, parte `file`). Parte síncrona, numa transação:
  1. `413 UPLOAD_TOO_LARGE`: limite do multipart, que barra antes de chegar ao controller, ou tamanho acima de `max-zip-bytes` no serviço (defesa em profundidade);
  2. `400 VALIDATION_FAILED` (`field = "file"`): parte ausente ou vazia;
  3. `404 PROJECT_NOT_FOUND`;
  4. `409 PROJECT_SOURCE_MISMATCH`: o projeto é `LOCAL_FOLDER`;
  5. `409 PROJECT_BUSY`: violação de `import_jobs_active_unique_idx` traduzida, sem `SELECT` prévio, o que é seguro sob concorrência;
  6. insert do job em `PENDING`, com `compressed_bytes`;
  7. `transferTo` para `DATA_DIR/imports/<id>/upload.zip` (em falha, rollback e remoção da pasta);
  8. após o commit, submissão ao executor.

  Resposta: `202 Accepted` com o import no formato de `GET /imports/{id}` e cabeçalho `Location`. O nome original do arquivo não é persistido nem logado.
- **Worker (`ZipExtractor`).** Muda o estado para `EXTRACTING` e abre `upload.zip` com `java.util.zip.ZipFile` (diretório central, UTF-8). Nenhuma dependência nova.
- **Para cada entrada, a validação do nome:**
  - NUL, caracteres de controle ou `\` → rejeição;
  - `/` inicial, letra de unidade (`C:`) ou segmento `..` → rejeição;
  - profundidade e comprimento acima dos limites → rejeição;
  - segmentos vazios e `.` são removidos;
  - o destino resolvido precisa começar por `…/<id>/source/` (defesa contra ZIP Slip mesmo depois da validação).
- **Gravação:**
  - diretórios com `createDirectories` (a árvore só tem diretórios criados pelo próprio extrator, sem links);
  - arquivos com `CREATE_NEW`, que detecta entradas duplicadas e colisões por maiúsculas em sistemas de arquivos que não diferenciam caixa;
  - permissões padrão, sem bit de execução;
  - **nunca cria links**: entradas de link Unix são gravadas como arquivo comum com o texto do alvo, e a F4 as trata como texto.
- **Limites contados pelos bytes realmente gravados** (cópia limitada), nunca pelos tamanhos declarados no ZIP, que podem mentir: por entrada, total, quantidade de entradas e razão de compressão. Isso também cobre entradas sobrepostas ("better zip bomb").
- **`ZipException`**, entrada criptografada ou nome ilegível → rejeição.
- **Conclusão:**
  - **Sucesso, numa transação:** `COMPLETED`, com `uncompressed_bytes` e `entry_count`; `replaceCurrentImport`; após o commit, remoção da pasta do import anterior e de `upload.zip`.
  - **Falha:** `REJECTED` (violação de regra do conteúdo) ou `FAILED` (E/S, interrupção), com `error_code`; a pasta do job é apagada; o import atual do projeto não muda.
- **Códigos de `error_code` do job** (enum próprio, mensagens `import.error.<CODE>` em pt-BR; nunca contêm nomes de entradas):
  - `REJECTED`: `ZIP_INVALID`, `ZIP_UNSAFE_PATH`, `ZIP_DUPLICATE_ENTRY`, `ZIP_TOO_MANY_ENTRIES`, `ZIP_ENTRY_TOO_LARGE`, `ZIP_UNCOMPRESSED_TOO_LARGE`, `ZIP_COMPRESSION_RATIO_EXCEEDED`, `ZIP_TOO_DEEP`;
  - `FAILED`: `IMPORT_INTERRUPTED`, `IMPORT_IO_ERROR`.
- **Recuperação (`ApplicationRunner`):**
  - jobs `PENDING`/`EXTRACTING` → `FAILED IMPORT_INTERRUPTED`, e as pastas são apagadas;
  - remoção de pastas órfãs em `DATA_DIR/imports`, ou seja, as que não pertencem a um job ativo nem a um import atual.
- **Leitura:**
  - `GET /imports/{importJobId}`: `{ id, projectId, status, compressedBytes, uncompressedBytes, entryCount, error: { code, message } | null, createdAt, completedAt }`; `404 IMPORT_NOT_FOUND`;
  - `GET /projects/{projectId}/imports`: paginado (D-030), do mais novo para o mais antigo; `404 PROJECT_NOT_FOUND`.
- **Coluna `error_message_sanitized`:** sai do modelo. A mensagem é resolvida pelo `MessageSource` a partir do código na hora da resposta, que é a única fonte, e o texto nunca vem do conteúdo importado.
- **Novos `ErrorCode`:** `PROJECT_SOURCE_MISMATCH`, `UPLOAD_TOO_LARGE` e `IMPORT_NOT_FOUND`. O `GlobalExceptionHandler` mapeia `MaxUploadSizeExceededException` → 413 e `MissingServletRequestPartException` → `VALIDATION_FAILED`.

**Testes de segurança (aceite da etapa)**

`ZipFixtures` (em `src/test`) gera ZIPs em memória. Inclui um gravador "cru" de cabeçalhos para casos que o `ZipOutputStream` não permite: nome duplicado, tamanho declarado falso e atributo de link Unix.

Unitário do extrator, com `@TempDir` contendo um arquivo sentinela **fora** da pasta do job. Cada caso abaixo é **rejeitado com o código esperado**, o sentinela não muda, nada é criado fora de `<id>/` e a pasta do job é apagada:

| Caso | Código |
|---|---|
| `../evil.txt`, `a/../../evil.txt`, `a/./../../evil.txt` | `ZIP_UNSAFE_PATH` |
| `/etc/evil`, `C:/evil`, `C:\evil`, `a\..\b` | `ZIP_UNSAFE_PATH` |
| nome com NUL ou caractere de controle | `ZIP_UNSAFE_PATH` |
| mesmo nome duas vezes; `A.txt` e `a.txt` em FS sem distinção de caixa (condicional) | `ZIP_DUPLICATE_ENTRY` |
| entradas acima de `max-entries` | `ZIP_TOO_MANY_ENTRIES` |
| bomba real (zeros com alta compressão) | `ZIP_COMPRESSION_RATIO_EXCEEDED` ou `ZIP_UNCOMPRESSED_TOO_LARGE` |
| tamanho declarado pequeno com conteúdo real grande | `ZIP_ENTRY_TOO_LARGE` |
| soma acima de `max-uncompressed-bytes` com entradas individualmente válidas | `ZIP_UNCOMPRESSED_TOO_LARGE` |
| profundidade e comprimento acima do limite | `ZIP_TOO_DEEP` / `ZIP_UNSAFE_PATH` |
| arquivo que não é ZIP; ZIP truncado; entrada criptografada | `ZIP_INVALID` |

Casos aceitos:
- entrada de link Unix → `COMPLETED`, e o arquivo extraído **não** é link (`Files.isSymbolicLink` falso);
- ZIP vazio → `COMPLETED`, com 0 entradas;
- nomes NFD e NFC → gravados como estão; a normalização para comparação fica com a varredura.

**Outros testes**
- Contrato da API (MockMvc + Awaitility):
  - upload válido → 202 com `Location`; o estado chega a `COMPLETED`; `currentImport` do projeto aponta para ele; `upload.zip` não existe mais;
  - segundo upload válido → o atual muda, e a pasta do anterior é apagada;
  - upload rejeitado depois de um concluído → o atual não muda, e a pasta do rejeitado é apagada;
  - `GET /imports/{id}` de import rejeitado traz `error.code` e mensagem pt-BR, sem nome de entrada;
  - sem parte `file` → 400; projeto inexistente → 404; projeto `LOCAL_FOLDER` → 409 `PROJECT_SOURCE_MISMATCH`;
  - job ativo inserido via `JdbcClient` → 409 `PROJECT_BUSY`;
  - N uploads paralelos no mesmo projeto → exatamente um 202, e os demais `PROJECT_BUSY`;
  - arquivo acima do limite de teste → 413 (pela verificação do serviço);
  - `GET /projects/{id}/imports` paginado e ordenado; `IMPORT_NOT_FOUND`.
- **Limite real do multipart:** um teste com `webEnvironment = RANDOM_PORT` e cliente HTTP real verifica 413 `UPLOAD_TOO_LARGE` com `ProblemDetail`. É o único contexto extra da etapa, porque o MockMvc não aplica os limites do multipart.
- Recuperação: jobs `PENDING`/`EXTRACTING` e pastas órfãs criados via `JdbcClient` e disco → depois de invocar o componente, `FAILED IMPORT_INTERRUPTED` e pastas removidas; a pasta do import atual é preservada.
- Unitário: todo código de erro de import tem mensagem no `MessageSource` (mesmo padrão do `ErrorCodeMessagesTest`).
- Log: a saída capturada durante um import rejeitado não contém o nome de nenhuma entrada.

### F4 — Varredura segura, exclusões padrão e inventário (pré-análise)

**Entregas**
- **`codeanalysis.LanguageDetector`** (sem dependências de outros módulos):
  - por nome conhecido (`Dockerfile`, `Makefile`, `pom.xml`…), extensão e shebang em arquivos sem extensão (lido nos bytes de amostra);
  - devolve um id estável em minúsculas (`java`, `typescript`, `python`…) ou `text`.
- **`importjob.scan.DefaultExclusionRules`:** lista versionada no código, com `id`, categoria, padrões e `overridable` (P4). Lista inicial, a fechar na implementação com um teste por regra:
  - `SENSITIVE`: `.env` e `.env.*`, exceto `.env.example`, `.env.sample` e `.env.template`; `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`, `id_rsa*`, `id_ecdsa*`, `id_ed25519*`, `.npmrc`, `.pypirc`, `.netrc`, `*.tfstate`, `*.tfvars`, `.aws/` e `.ssh/`;
  - `VCS`: `.git/`, `.hg/`, `.svn/`;
  - `DEPENDENCIES`: `node_modules/`, `vendor/`, `.venv/`, `venv/`, `bower_components/`, `.gradle/`, `.m2/`;
  - `BUILD_OUTPUT`: `target/`, `build/`, `dist/`, `out/`, `.next/`, `.nuxt/`, `coverage/`, `__pycache__/`, `*.class`, `*.pyc`, `*.o`, `*.so`, `*.dll`, `*.exe`, `*.jar`, `*.war`;
  - `IDE_CACHE`: `.idea/`, `.cache/`, `.DS_Store`;
  - `GENERATED`: `*.min.js`, `*.min.css`, `*.map`, `package-lock.json`, `yarn.lock`, `pnpm-lock.yaml`;
  - `MEDIA_BINARY`: imagens, fontes, áudio, vídeo, arquivos compactados e PDF, por extensão.
- **`importjob.scan.PathPatterns`:** compilador da sintaxe da P4 para regex, puro e independente do SO.
- **`importjob.scan.SafeScanner`:** percorre a origem com `Files.walkFileTree` **sem** `FOLLOW_LINKS`. Para cada entrada, na ordem:
  1. link → `EXCLUDED/SYMLINK`;
  2. especial → `EXCLUDED/SPECIAL_FILE`, sem abrir;
  3. profundidade acima de `app.inventory.max-depth` → `EXCLUDED/DEPTH_LIMIT`;
  4. regra padrão ativa → `EXCLUDED/DEFAULT_RULE` (diretórios são podados);
  5. padrão customizado → `EXCLUDED/CUSTOM_PATTERN` (entra na F5);
  6. tamanho acima do limite efetivo → `EXCLUDED/TOO_LARGE`;
  7. amostra de 8 KB com byte NUL → `UNSUPPORTED/BINARY`;
  8. erro de leitura (permissão ou arquivo removido durante a varredura) → `UNREADABLE`;
  9. caso contrário, `INCLUDED`, com a linguagem detectada.

  Limites globais (`app.inventory.*`, hipóteses):
  - `max-entries`: 200.000; ao atingir, para com `truncated`;
  - `max-depth`: 64;
  - `max-file-bytes`: 1 MB;
  - `max-project-bytes`: 500 MB de bytes incluídos; acima disso, só aviso, e a Etapa 3 recusa indexar.
- **Resolução da origem:**
  - `LOCAL_FOLDER`: raiz pela `rootKey` + `ConfinedPaths`;
  - `ZIP`: `DataDirectory.importDir(currentImportJobId)/source`.
- **`GET /projects/{projectId}/inventory`.** Erros, nesta ordem:
  1. `404 PROJECT_NOT_FOUND`;
  2. `LOCAL_FOLDER`: `409 SOURCE_ROOT_NOT_CONFIGURED` (a `rootKey` saiu da configuração, D-020); depois, na ordem dos segmentos, `409 SOURCE_DIRECTORY_NOT_FOUND`, `409 SOURCE_IS_SYMLINK` ou `409 SOURCE_NOT_READABLE`;
  3. `ZIP`: `409 SOURCE_NOT_IMPORTED`, se não houver import atual.

```json
{
  "projectId": "…",
  "scannedAt": "2026-10-20T12:00:00Z",
  "settingsVersion": 0,
  "truncated": false,
  "totals": {
    "files": 1240, "includedFiles": 980, "includedBytes": 7340032,
    "excludedFiles": 200, "unsupportedFiles": 50, "unreadableFiles": 10,
    "excludedDirectories": 4
  },
  "byLanguage": [ { "language": "java", "files": 420, "bytes": 3145728 } ],
  "byDirectory": [
    { "path": "src", "includedFiles": 900, "excludedFiles": 12, "unsupportedFiles": 3, "unreadableFiles": 0 }
  ],
  "exclusions": [
    { "reason": "DEFAULT_RULE", "ruleId": "node-modules", "pattern": null,
      "files": 0, "directories": 1, "examples": ["web/node_modules"] }
  ],
  "warnings": [
    { "code": "CASE_VARIANT_PROJECT", "message": "…", "relatedProjectIds": ["…"] }
  ]
}
```

  - `byDirectory` agrega pelo **primeiro nível**, e os arquivos da raiz ficam em `""`. Diretórios excluídos não são percorridos, então seus arquivos não entram nas contagens; `exclusions` mostra o diretório.
  - `examples`: até 20 caminhos relativos por motivo, em ordem lexicográfica. São devolvidos ao usuário, mas nunca logados.
  - **Avisos** (`inventory.warning.<CODE>`, pt-BR):
    - `CASE_VARIANT_PROJECT`: outro projeto `LOCAL_FOLDER` do mesmo workspace, com a mesma `rootKey`, cujo `relativePath` difere só por maiúsculas e que resolve para a mesma pasta (`Files.isSameFile`), o caso de sistemas sem distinção de caixa (D-020);
    - `SCAN_LIMIT_REACHED`;
    - `PROJECT_SIZE_LIMIT_EXCEEDED`;
    - `NO_INCLUDED_FILES` (pasta sem arquivos suportados ou com tudo excluído, `03-UX-ONBOARDING.md` §6).
- Novos `ErrorCode`: `SOURCE_ROOT_NOT_CONFIGURED`, `SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE` e `SOURCE_NOT_IMPORTED`.
- ArchUnit: `codeanalysis` não depende de `importjob`.

**Testes**
- Unitário de `PathPatterns`, com tabela de casos:
  - `*.log` em qualquer profundidade; `docs/*.md` só na raiz; `**/gen/**`; `build/` casa o diretório e não um arquivo `build`;
  - `a?c`;
  - sensível a maiúsculas;
  - padrões inválidos (`!x`, `[a]`, `a\b`, `../x`) → rejeitados.
- Unitário de `DefaultExclusionRules`: um caso positivo por regra; `.env.example` incluído e `.env.local` excluído; `id_rsa.pub` excluído (decisão conservadora).
- Unitário de `LanguageDetector`: extensão, nome conhecido, shebang e desconhecido (`text`).
- **Unitário de `SafeScanner` (`@TempDir`, aceite: nada escapa da raiz):**
  - link para arquivo e para diretório **fora** da raiz → `SYMLINK`, e o conteúdo do alvo não é lido (o alvo é um arquivo ilegível ou FIFO que travaria a leitura);
  - laço de links não trava;
  - FIFO → `SPECIAL_FILE`, e o teste termina (timeout curto; condicional ao SO);
  - arquivo ilegível → `UNREADABLE`;
  - arquivo apagado durante a varredura (gancho de teste) → `UNREADABLE`, sem falhar a varredura;
  - `TOO_LARGE`, `BINARY` e `DEPTH_LIMIT`;
  - `node_modules/` podado, sem visitar o conteúdo;
  - `max-entries` → `truncated`.
- Contrato da API:
  - inventário `LOCAL_FOLDER` sobre a árvore de teste: totais, linguagens, diretórios e exclusões esperados; nenhum caminho absoluto no corpo;
  - inventário ZIP depois de um import concluído (mesmo resultado que a mesma árvore como pasta);
  - ZIP sem import → `SOURCE_NOT_IMPORTED`;
  - projeto cuja pasta não existe → `SOURCE_DIRECTORY_NOT_FOUND`; caminho que passa por link → `SOURCE_IS_SYMLINK`;
  - `rootKey` removida da configuração: projeto inserido via `JdbcClient` com `rootKey` inexistente → `SOURCE_ROOT_NOT_CONFIGURED`;
  - `CASE_VARIANT_PROJECT`: condicional a FS sem distinção de caixa (macOS padrão); no Linux, verificar que **não** há aviso;
  - `NO_INCLUDED_FILES` com pasta só de excluídos.

### F5 — Exclusões e limites customizados (V5)

**Entregas**
- Migração V5 (abaixo): `project_settings`, com linha opcional (sem linha = padrões).
- **`GET /projects/{projectId}/settings`:**

```json
{
  "excludePatterns": ["docs/generated/"],
  "disabledDefaultRules": ["vendor"],
  "maxFileBytes": null,
  "maxProjectBytes": null,
  "effectiveMaxFileBytes": 1048576,
  "effectiveMaxProjectBytes": 524288000,
  "defaultRules": [
    { "id": "vendor", "category": "DEPENDENCIES", "patterns": ["vendor/"], "overridable": true, "disabled": true }
  ],
  "version": 1,
  "updatedAt": "2026-10-20T12:00:00Z"
}
```

- **`PUT /projects/{projectId}/settings`** com `{ excludePatterns, disabledDefaultRules, maxFileBytes, maxProjectBytes }`. Listas ausentes são tratadas como vazias; limites `null` voltam ao padrão. Validação, reunindo todos os erros numa única resposta:
  - até 100 padrões, cada um com 1 a 200 caracteres, na sintaxe da P4 (`field = "excludePatterns[3]"`);
  - padrões duplicados removidos, preservando a ordem;
  - `disabledDefaultRules`: ids existentes e `overridable` (desativar `SENSITIVE` ou `VCS` → `VALIDATION_FAILED`);
  - limites entre 1 KB e os tetos `app.inventory.max-file-bytes-limit` (10 MB) e `max-project-bytes-limit` (2 GB).
- **Precedência:** `400 VALIDATION_FAILED` → `404 PROJECT_NOT_FOUND`.
- Upsert por `JdbcClient` (`INSERT … ON CONFLICT (project_id) DO UPDATE`), com `settings_version` incrementado a cada `PUT`. A versão aparece no inventário e será usada pela Etapa 3 para decidir a reindexação.
- O `SafeScanner` passa a aplicar `excludePatterns`, `disabledDefaultRules` e os limites efetivos.

**Testes**
- Contrato da API:
  - `GET` sem linha → padrões, `version = 0` e `updatedAt = null`;
  - `PUT` válido → 200, e `version` incrementa a cada chamada;
  - padrão inválido, regra inexistente, regra não desativável e limite fora da faixa → 400 com `field` indexado e sem valor rejeitado;
  - projeto inexistente → 404, e o corpo inválido continua respondendo 400 antes;
  - remoção do projeto apaga as configurações (cascata).
- Integração com o inventário:
  - padrão customizado exclui com `CUSTOM_PATTERN`;
  - desativar `vendor` inclui `vendor/`;
  - `.env` continua excluído com qualquer configuração;
  - `maxFileBytes` menor transforma arquivos em `TOO_LARGE`;
  - `settingsVersion` do inventário acompanha o `PUT`.

### F6 — Fechamento

- Aceite: testes de segurança demonstram que arquivos não escapam da raiz (F0, F1, F4) e que ZIPs maliciosos são rejeitados (F3).
- `openapi.json` final commitado.
- Registrar D-033 a D-037 (P1 a P5) em `10-DECISIONS.md` e marcar a O-012 como resolvida; revisar o texto da D-028 (retenção e `error_message_sanitized`).
- `05-DATA-MODEL.md` (`import_jobs`, `projects`, `project_settings`, retenção), `06-API-CONTRACTS.md` (§4, §5, §6 e códigos), `07-SECURITY-PRIVACY-COSTS.md` (política de links, limitações) e `09-IMPLEMENTATION-ROADMAP.md` (status).
- README: `DATA_DIR`, limites de import e varredura, e onde ficam os arquivos extraídos.
- Seção "Resultado e desvios" neste arquivo, como na Etapa 1.

## Migrações

### V4__create_import_jobs.sql

```sql
CREATE TABLE import_jobs (
    id                 uuid        PRIMARY KEY,
    project_id         uuid        NOT NULL
                                   CONSTRAINT import_jobs_project_id_fkey
                                   REFERENCES projects (id) ON DELETE CASCADE,
    status             text        NOT NULL
                                   CONSTRAINT import_jobs_status_valid
                                   CHECK (status IN ('PENDING', 'EXTRACTING', 'COMPLETED',
                                                     'REJECTED', 'FAILED')),
    compressed_bytes   bigint      NOT NULL
                                   CONSTRAINT import_jobs_compressed_bytes_valid
                                   CHECK (compressed_bytes > 0),
    uncompressed_bytes bigint,
    entry_count        integer,
    error_code         text,
    created_at         timestamptz NOT NULL,
    completed_at       timestamptz,
    CONSTRAINT import_jobs_project_id_id_key UNIQUE (project_id, id),
    CONSTRAINT import_jobs_state_consistency CHECK (
        (status IN ('PENDING', 'EXTRACTING')
            AND completed_at IS NULL AND error_code IS NULL)
     OR (status = 'COMPLETED'
            AND completed_at IS NOT NULL AND error_code IS NULL
            AND uncompressed_bytes IS NOT NULL AND entry_count IS NOT NULL)
     OR (status IN ('REJECTED', 'FAILED')
            AND completed_at IS NOT NULL AND error_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX import_jobs_active_unique_idx
    ON import_jobs (project_id)
    WHERE status IN ('PENDING', 'EXTRACTING');

CREATE INDEX import_jobs_project_created_at_id_idx
    ON import_jobs (project_id, created_at DESC, id DESC);

ALTER TABLE projects
    ADD COLUMN current_import_job_id uuid,
    ADD CONSTRAINT projects_current_import_fkey
        FOREIGN KEY (id, current_import_job_id)
        REFERENCES import_jobs (project_id, id)
        ON DELETE SET NULL (current_import_job_id),
    ADD CONSTRAINT projects_current_import_zip_only
        CHECK (current_import_job_id IS NULL OR source_type = 'ZIP');
```

- A FK composta garante que o import atual pertence ao próprio projeto. `SET NULL (coluna)`, disponível desde o PostgreSQL 15, anula só `current_import_job_id`, e não `id`.
- Que o import atual esteja `COMPLETED` é garantido pela aplicação (só o worker o preenche, na mesma transação da conclusão).
- Nomes traduzidos pelo código:
  - `import_jobs_active_unique_idx` → `PROJECT_BUSY`;
  - `import_jobs_project_id_fkey` → `PROJECT_NOT_FOUND` (upload concorrente com remoção).
- Sem `error_message_sanitized` (F3) e sem `updated_at`: as transições são registradas por `status` e `completed_at`.

### V5__create_project_settings.sql

```sql
CREATE TABLE project_settings (
    project_id             uuid        PRIMARY KEY
                                       CONSTRAINT project_settings_project_id_fkey
                                       REFERENCES projects (id) ON DELETE CASCADE,
    exclude_patterns       text[]      NOT NULL,
    disabled_default_rules text[]      NOT NULL,
    max_file_bytes         bigint,
    max_project_bytes      bigint,
    settings_version       integer     NOT NULL
                                       CONSTRAINT project_settings_version_valid
                                       CHECK (settings_version > 0),
    updated_at             timestamptz NOT NULL
);
```

- `text[]` em vez de JSONB (diferente de `05-DATA-MODEL.md`): listas de strings simples, sem converter JSON no Spring Data JDBC. O acesso é por `JdbcClient`.
- `include_patterns` sai do modelo (P4).

## Riscos

| Risco | Mitigação |
|---|---|
| Tamanhos declarados no ZIP mentem (bombas, entradas sobrepostas). | Limites contados pelos bytes gravados; testes com tamanho declarado falso e com bomba real (F3). |
| ZIP Slip por caminho que passa na validação sintática. | Validação do nome **e** verificação do destino resolvido dentro de `source/`; `CREATE_NEW`; extrator nunca cria links. |
| Abrir um FIFO ou dispositivo trava a thread. | Atributos lidos sem seguir links; especiais nunca são abertos; teste com timeout (F4). |
| Links simbólicos levam a varredura para fora da raiz. | `walkFileTree` sem `FOLLOW_LINKS`; `ConfinedPaths` segmento a segmento; caminho real conferido contra a raiz. |
| Links físicos, pontos de montagem e TOCTOU. | Fora do escopo; documentados como limitação em `07` (P5). |
| O Tomcat fecha a conexão quando o corpo excede muito o limite (`max-swallow-size`), e o cliente vê erro de rede em vez de 413. | Teste real com `RANDOM_PORT` documenta o comportamento; a UI valida o tamanho antes do envio (Etapa 7). |
| O multipart grava o upload em diretório temporário do SO antes do `transferTo`. | Aceito; o arquivo é movido para a pasta do job. Revisar se o volume justificar `spring.servlet.multipart.location` dentro do `DATA_DIR`. |
| Queda no meio da extração deixa job ativo e bloqueia o projeto (`PROJECT_BUSY`). | Recuperação na inicialização (`IMPORT_INTERRUPTED`) e remoção de pastas órfãs. |
| Referência circular `projects` ↔ `import_jobs` falhar na cascata. | Teste explícito de remoção com import atual preenchido (F2). |
| Remoção de pasta falha depois do commit (arquivo aberto, permissão). | Log com o `importJobId`; a varredura de órfãs na inicialização tenta de novo. |
| Testes assíncronos instáveis ou vazando para o teste seguinte. | Awaitility até o estado terminal; o `DatabaseCleaner` também limpa `DATA_DIR/imports`; limites pequenos nos testes. |
| Varredura lenta em repositórios grandes, já que o inventário não é persistido. | `max-entries` e `max-depth`, poda de diretórios excluídos e amostra de só 8 KB; medir na Etapa 8. |
| Testes dependentes do SO (FIFO, permissões, FS sem distinção de caixa). | `assumeTrue` com o motivo registrado, como na Etapa 1. |
| Colisão por maiúsculas no macOS rejeita ZIPs legítimos (`A.txt` e `a.txt`). | Rejeição explícita (`ZIP_DUPLICATE_ENTRY`) em vez de sobrescrever; documentado como limitação. |

## Resultado e desvios

**Aceite:** testes de segurança demonstram que arquivos não escapam da raiz e que ZIPs maliciosos são rejeitados:
- `ConfinedPathsTest` e `LocalRootsApiIT` (F0, F1): links no caminho, dentro ou fora da raiz, nunca são seguidos;
- `ZipExtractorTest` (F3): ZIP Slip, caminhos absolutos e com letra de unidade, `\`, NUL, duplicatas, colisões por maiúsculas, bombas, tamanho declarado falso, total acima do limite, profundidade, ZIPs inválidos, truncados e criptografados; em todos os casos, nada é gravado fora da pasta do job;
- `SafeScannerTest` (F4): links para arquivo, diretório, dentro da raiz e em laço não são seguidos, e FIFOs não são abertos, nem diretamente nem por link.

**Commits:** F0 `3c7257c`, F1 `67b0fd9`, F2 `2311af1`, F3 `882a117`, F4 `e267d48`, F5 `0d41abb`, F6 (documentação de fechamento).

**Pendências para a Etapa 3:**
- `PROJECT_BUSY` também para jobs de indexação (a porta `ProjectImports` é o ponto de extensão);
- persistência do inventário na tabela `files` e decisões O-008 e O-009;
- uso de `settings_version` para decidir a reindexação;
- recusar a indexação quando o aviso `PROJECT_SIZE_LIMIT_EXCEEDED` estiver presente.

**Para a Etapa 8:** medir os limites de import e de varredura; detecção de segredos por conteúdo; links físicos e pontos de montagem; diretórios em NFD no Linux (aparecem na navegação, mas não abrem).

**Desvios e acréscimos em relação ao plano:**
- **Árvore de teste (F0):** o initializer cria apenas `target/test-roots/{default,work}` vazios. Links, arquivo ilegível e FIFO entram na F1 e na F4, junto com os testes que os usam.
- **`DataDirectory` (F0):** a posição (raízes e repositório) é validada **antes** de criar o diretório, resolvendo o caminho real a partir do ancestral existente; uma configuração inválida não deixa pasta criada.
- **Nomes na navegação (F1):** os nomes de diretório são devolvidos em NFC, igual ao que o cliente envia de volta. Diretórios cujo nome tem `\` ou caracteres de controle são omitidos, porque o caminho seria recusado como entrada. Limitação: em sistemas que diferenciam formas Unicode (Linux), um diretório gravado em NFD aparece na lista, mas não pode ser aberto.
- **Mensagem de `path` inválido (F1):** reusa a chave `validation.project.relative-path-chars`, cujo texto já é genérico.
- **Árvore de teste (F1):** `target/test-roots/default` ganhou a estrutura descrita no comentário do `TestFileSystemInitializer`, e a remoção da árvore entre execuções desbloqueia diretórios sem permissão antes de apagá-los.
- **Agregado `ImportJob` (F2):** o record, o insert e as transições de estado ficam para a F3, que é quem os usa. A F2 entrega só as consultas de que a porta precisa (`ImportJobRepository`) e o enum `ImportStatus`.
- **Porta `ProjectImports` (F2):** `CurrentImport.status` é `String`, porque `project` não pode ver o enum de `importjob` (D-031).
- **Remoção de pastas (F2):** `shared.fs.Directories.deleteRecursively` (apaga links sem segui-los) e `ImportFiles`, que agenda a remoção para depois do commit e loga só o `importJobId` e a classe da exceção, porque a mensagem pode conter nomes vindos do conteúdo importado.
- **Sem N+1 (F2):** coberto pela consulta em lote do serviço (uma chamada à porta por página); não há teste que conte consultas.
- **Executor (F3):** um pool fixo criado no `ImportWorker` (`app.import.max-concurrent`), e não um bean `Executor`, que desligaria o `applicationTaskExecutor` do Spring Boot. É encerrado com `shutdownNow` no `@PreDestroy`.
- **Recuperação (F3):** roda em `SmartInitializingSingleton`, antes de o servidor web aceitar requisições, e não em `ApplicationRunner`, que rodaria depois e poderia falhar um job recém-criado por um upload.
- **Configuração (F3):** só `app.import.max-zip-bytes` fica no `application.yml`, porque o multipart deriva dele; os demais limites têm padrão em `ImportProperties`. Novo `app.import.compression-ratio-min-bytes` (1 MB), tamanho a partir do qual a razão de compressão é verificada.
- **Persistência do job (F3):** insert e transições por `JdbcClient`, não por `JdbcAggregateTemplate`, porque todas as transições são `UPDATE` condicionados ao estado.
- **Testes (F3):** cada contexto de teste ganhou seu próprio `DATA_DIR`, para a recuperação de um contexto não apagar pastas de outro. A limpeza de `DATA_DIR/imports` fica no `ImportApiIT`, não no `DatabaseCleaner`.
- **Concorrência (F3):** em vez de N uploads paralelos (a extração é rápida, então um upload posterior pode legitimamente ser aceito), o teste segura uma transação com um job `PENDING` não commitado, espera o segundo upload bloquear no índice único (`pg_stat_activity`) e confirma o `409 PROJECT_BUSY` depois do commit.
- **Não testado (F3):** a remoção da pasta quando o `transferTo` falha (rollback); o código registra a limpeza antes de gravar.
- **Regras padrão (F4):** cada grupo virou uma regra com `id` próprio (33 regras, ex.: `env-files`, `private-keys`, `node-modules`, `build-dir`, `lockfiles`, `images`), para que a F5 possa desativar só o necessário. Acréscimos à lista do plano: `*.tfstate.*` (backups) e `*.svg` em `images`. A lista completa e um caso por regra estão em `DefaultExclusionRulesTest`.
- **`exclusions` (F4):** agrupa todo motivo que não seja `INCLUDED`, inclusive `BINARY`, `UNREADABLE`, `SYMLINK` e `SPECIAL_FILE`, e não só as regras; diretórios ilegíveis contam como diretórios excluídos com motivo `UNREADABLE`.
- **Varredura (F4):** o `SafeScanner` já aceita padrões customizados; a F5 só passa a ler as configurações do projeto. A amostra de 8 KB é lida por uma função injetável, usada no teste de arquivo removido durante a varredura.
- **Configuração (F4):** os limites `app.inventory.*` têm padrão em `InventoryProperties`, sem entradas no `application.yml`.
- **Origem ZIP (F4):** `DataDirectory.importSourceDir(id)` passou a ser o único lugar que conhece o nome `source/`.
- **Configurações (F5):** `defaultRules` também traz `exceptions` (ex.: `.env.example` em `env-files`), para a UI mostrar o que a regra não exclui. Itens `null` nas listas respondem `validation.required` no índice; mais de 100 padrões geram um único erro em `excludePatterns`. A mensagem de limite fora da faixa é genérica, porque `FieldViolation` não carrega argumentos.
- **Correção no fechamento:** o `./mvnw clean verify` mostrou que o diretório de teste `locked` (sem permissões, F1) impedia o `mvn clean` de apagar `target/`. O `TestFileSystemInitializer` passou a restaurar as permissões ao fim da JVM de teste.
