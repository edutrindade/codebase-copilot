# Contratos da API — Codebase Copilot

## 1. Convenções

- Base path: `/api/v1`. Todos os caminhos abaixo são relativos a ele.
- REST/JSON.
- UUID v7 para identificadores (D-024).
- Erros em ProblemDetail (RFC 9457) com `code` estável em inglês e mensagem segura em pt-BR (D-022, D-025).
- OpenAPI code-first como contrato principal; o contrato gerado é versionado em `apps/backend/openapi.json` (D-023).
- Validar entrada com Bean Validation.
- Identificador malformado no caminho (ex.: `/projects/abc`) responde `400 VALIDATION_FAILED`, com `field` igual ao nome do parâmetro (`projectId`).
- Toda resposta, de sucesso ou de erro, traz o cabeçalho `X-Request-Id` com o mesmo `requestId` usado nos logs. O `requestId` é sempre gerado pelo backend (UUID v7); um `X-Request-Id` enviado pelo cliente é ignorado.
- Não devolver caminhos absolutos do sistema operacional.
- Endpoints de consulta devem operar sobre um snapshot explícito ou sobre o snapshot ativo claramente indicado.

### Paginação (D-030)

Listagens paginadas aceitam `page` (base 0, padrão 0) e `size` (padrão 20, máximo 100). Valores fora da faixa respondem `400 VALIDATION_FAILED`; página além da última devolve `items` vazio.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

Workspaces e projetos são ordenados por `createdAt` decrescente (desempate por `id` decrescente). A lista de arquivos de um snapshot também usa offset, ordenada por `relativePath` (D-045); cursor fica para quando a medição justificar.

## 2. Formato de erro

Erros usam `ProblemDetail` (RFC 9457) do Spring, com `Content-Type: application/problem+json` e as propriedades extras `code` e `requestId` (D-022):

```json
{
  "type": "about:blank",
  "title": "Projeto não encontrado",
  "status": 404,
  "detail": "O projeto informado não existe ou foi removido.",
  "instance": "/api/v1/projects/0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e",
  "code": "PROJECT_NOT_FOUND",
  "requestId": "0192f5c4-7e3b-7a2f-8d4e-3a5b7c9d1e2f"
}
```

Erros de validação acrescentam `errors`, com itens contendo **apenas** `field` e `message`. O valor rejeitado **nunca** é devolvido:

```json
{
  "type": "about:blank",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "A requisição contém campos inválidos.",
  "instance": "/api/v1/workspaces",
  "code": "VALIDATION_FAILED",
  "requestId": "0192f5c4-7e3c-7c3a-9e5f-4b6c8d0e2f3a",
  "errors": [
    { "field": "name", "message": "O nome é obrigatório." }
  ]
}
```

`title`, `detail` e `errors[].message` são em pt-BR, resolvidos por `MessageSource` a partir do `code`, com as chaves `error.<CODE>.title` e `error.<CODE>.detail` (D-022, D-025). O `code` é estável e em inglês.

Não incluir stack traces, credenciais, conteúdo sensível, valores rejeitados ou caminhos internos nos erros destinados ao frontend.

### Códigos genéricos

Além dos códigos de cada recurso, qualquer endpoint pode responder (D-022):

| Status | `code` | Quando |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | Corpo ilegível: JSON inválido, tipo incompatível ou valor de enum desconhecido. |
| 404 | `RESOURCE_NOT_FOUND` | Rota inexistente. |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP não suportado pela rota. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` não suportado. |
| 500 | `INTERNAL_ERROR` | Falha inesperada. |

`INTERNAL_ERROR` **nunca** devolve detalhes da exceção (mensagem, classe, causa ou stack trace): `title` e `detail` são textos fixos, e o `requestId` é a única informação para correlacionar com o log.

## 3. Workspaces

### `POST /workspaces`
Cria workspace.

Request:
```json
{ "name": "Meus projetos" }
```

`name` é opcional. Ausente, `null` ou vazio após o `trim`, assume "Meus projetos". O valor passa por `trim` e normalização NFC; depois disso, tem no máximo 100 caracteres e não contém caracteres de controle. Nomes repetidos são permitidos (D-032).

Response: `201 Created`, objeto com `id`, `name`, `projectCount` (0 na criação) e `createdAt`.

Erros: `400 VALIDATION_FAILED`.

### `GET /workspaces`
Lista workspaces com a contagem de projetos (`projectCount`), no mesmo formato da criação. Paginada (§1, D-030).

### `GET /workspaces/{workspaceId}`
Retorna detalhes do workspace, incluindo `projectCount`.

Erros: `404 WORKSPACE_NOT_FOUND`.

### `DELETE /workspaces/{workspaceId}`
Remove o workspace (D-021).

Responses:
- `204 No Content`;
- `404 WORKSPACE_NOT_FOUND`;
- `409 WORKSPACE_NOT_EMPTY` se houver projetos. Os projetos devem ser removidos antes.

## 4. Raízes locais

Seleção segura de pasta local (D-018). O backend expõe apenas as raízes permitidas configuradas em `app.local-roots` (D-029); o cliente nunca envia nem recebe caminhos absolutos.

### `GET /local-roots`
Lista as raízes permitidas.

Response `200 OK`:
```json
[
  { "key": "default", "label": "Projetos" }
]
```

Sem nenhuma raiz configurada, devolve `[]`. O fluxo ZIP continua disponível.

### `GET /local-roots/{rootKey}/directories?path=<relativo>`
Lista os subdiretórios imediatos de `path` (relativo à raiz; vazio ou ausente = a própria raiz).

Response `200 OK` (D-038):
```json
{
  "path": "clientes",
  "directories": [
    { "name": "api", "relativePath": "clientes/api", "hasChildren": true, "readable": true }
  ],
  "truncated": false
}
```

Regras:
- `path` passa pela mesma normalização sintática de `relativePath` (§5) e é devolvido normalizado;
- cada segmento é verificado sem seguir links; um caminho que passa por link é tratado como inexistente (D-037);
- apenas subdiretórios reais são listados: links e arquivos são omitidos, assim como diretórios cujo nome teria `\` ou caracteres de controle;
- nomes em NFC, ordenados sem distinção de maiúsculas (desempate pela ordem sensível);
- `hasChildren` indica se há ao menos um subdiretório real; `readable: false` indica um diretório que o backend não consegue listar;
- no máximo `app.browse.max-directories` itens (padrão 1000); acima disso, `truncated: true`.

Erros, nesta ordem:
1. `400 VALIDATION_FAILED` (`field = "path"`): NUL, `\` ou caracteres de controle;
2. `404 ROOT_NOT_FOUND`, inclusive sem raízes configuradas;
3. `400 PATH_OUTSIDE_ROOT`: `/` inicial ou segmento `..`;
4. `404 DIRECTORY_NOT_FOUND`: inexistente, não é diretório ou passa por link;
5. `409 DIRECTORY_NOT_READABLE`.

## 5. Projetos

### `POST /workspaces/{workspaceId}/projects`
Cria projeto. A origem é definida na criação (D-020).

Pasta local:
```json
{
  "name": "codebase-copilot",
  "sourceType": "LOCAL_FOLDER",
  "rootKey": "default",
  "relativePath": "codebase-copilot"
}
```

ZIP (o arquivo é enviado depois, em `POST /projects/{projectId}/imports/zip`):
```json
{
  "name": "projeto-zip",
  "sourceType": "ZIP"
}
```

Response: `201 Created` com o projeto, no formato de `GET /projects/{projectId}`, e cabeçalho `Location`.

`name` é obrigatório: passa por `trim` e normalização NFC; depois disso, não pode ser vazio, tem no máximo 100 caracteres e não contém caracteres de controle. Nomes repetidos são permitidos (D-032).

Em `LOCAL_FOLDER`, `rootKey` e `relativePath` são obrigatórios (`null` responde `400 VALIDATION_FAILED`). Em `ZIP`, ambos devem estar ausentes.

`relativePath` passa por normalização **sintática** e o valor normalizado é o que a resposta devolve (D-020, D-032):
- Unicode NFC;
- `/` é o único separador; barras repetidas viram uma só;
- barras finais e segmentos `.` são removidos;
- `""` e `"."` (e equivalentes como `"./"`) representam **a própria raiz** e são devolvidos como `""`;
- `/` inicial (caminho absoluto) ou qualquer segmento `..` → `400 PATH_OUTSIDE_ROOT`;
- NUL, barra invertida (`\`) ou outros caracteres de controle → `400 VALIDATION_FAILED`;
- a comparação é sensível a maiúsculas.

A criação **não acessa o sistema de arquivos**: a existência da pasta, o tipo e os links simbólicos são verificados na pré-análise da Etapa 2 (D-018), que também poderá avisar sobre projetos que diferem apenas por maiúsculas/minúsculas.

Erros, na ordem em que são verificados (D-032). Para `ZIP`, apenas os passos 1 e 2 se aplicam:
1. `400 VALIDATION_FAILED`: campos obrigatórios, regras de `name`, `rootKey`/`relativePath` informados para `ZIP`, NUL, `\` ou caracteres de controle em `relativePath`;
2. `404 WORKSPACE_NOT_FOUND`;
3. `409 LOCAL_ROOTS_NOT_CONFIGURED`: `LOCAL_FOLDER` sem nenhuma raiz configurada (D-029);
4. `404 ROOT_NOT_FOUND`;
5. `400 PATH_OUTSIDE_ROOT`: `/` inicial ou segmento `..`;
6. `409 PROJECT_ALREADY_EXISTS`: o workspace já tem projeto com a mesma `rootKey` e o mesmo `relativePath` normalizado. Nomes iguais são permitidos; projetos ZIP não são verificados (D-020).

### `GET /workspaces/{workspaceId}/projects`
Lista os projetos do workspace. Paginada (§1, D-030).

Erros: `404 WORKSPACE_NOT_FOUND`.

### `GET /projects/{projectId}`
Retorna metadados, origem, snapshot ativo e estado de indexação (D-044). A listagem de projetos traz os mesmos campos, obtidos em lote por página:

```json
{
  "id": "0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e",
  "workspaceId": "0192f5c4-7e3b-7a2f-8d4e-3a5b7c9d1e2f",
  "name": "codebase-copilot",
  "source": {
    "type": "LOCAL_FOLDER",
    "rootKey": "default",
    "relativePath": "codebase-copilot"
  },
  "activeSnapshot": {
    "id": "…", "status": "READY", "completedAt": "2026-10-16T12:00:05Z",
    "counts": { "files": 980, "indexedFiles": 975, "unsupportedFiles": 4, "failedFiles": 1, "chunks": 4120, "bytes": 7340032 }
  },
  "indexing": null,
  "outdatedReasons": ["SETTINGS_CHANGED"],
  "createdAt": "2026-10-15T12:00:00.123456Z"
}
```

`source` é polimórfico, com discriminador `type`. Para projetos `ZIP`, traz `type` e `currentImport` (`id` e `status`), `null` até o primeiro upload concluído. O import atual só muda quando um novo upload conclui com sucesso; um import em andamento aparece em `GET /projects/{projectId}/imports` (§6).

- `activeSnapshot`: snapshot usado nas consultas (`READY` ou `PARTIAL`), `null` antes da primeira indexação concluída (D-044).
- `indexing`: job de indexação ativo `{ id, status, stage, progress: { total, completed } }`, ou `null`.
- `outdatedReasons`: por que o snapshot ativo pode não refletir a origem: `SETTINGS_CHANGED` (configurações alteradas depois dele), `SOURCE_REIMPORTED` (projeto ZIP com outro import atual), `CHUNKING_CHANGED` (estratégia de chunking nova), `EMBEDDING_MODEL_CHANGED` (provedor, modelo ou dimensão configurados diferentes dos do snapshot) e `EMBEDDINGS_MISSING` (snapshot indexado sem embeddings, com provedor agora configurado), `LEXICAL_INDEX_MISSING` (snapshot sem índice lexical) e `LEXICAL_INDEX_CHANGED` (índice lexical de outra versão do tokenizador). `EMBEDDING_MODEL_CHANGED` e `EMBEDDINGS_MISSING` só aparecem com provedor configurado (D-050); os dois lexicais, sempre (D-056). Lista vazia quando está em dia ou sem snapshot. Mudanças no disco de uma pasta local não são detectadas; o usuário reindexa.

Erros: `404 PROJECT_NOT_FOUND`.

### `GET /projects/{projectId}/inventory`
Pré-análise e inventário (F-02, F-04), calculados a cada chamada e não persistidos (D-035). A origem é a pasta do projeto `LOCAL_FOLDER` ou a árvore extraída do import atual do projeto `ZIP` (D-033). Arquivos são lidos apenas como bytes; links nunca são seguidos e arquivos especiais nunca são abertos (D-037).

Response `200 OK`:
```json
{
  "projectId": "…",
  "scannedAt": "2026-10-16T12:00:00Z",
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

- **Estados dos arquivos:** incluído, excluído, não suportado (binário) ou ilegível.
- **`reason`:** `DEFAULT_RULE` (com `ruleId`), `CUSTOM_PATTERN` (com `pattern`), `SYMLINK`, `SPECIAL_FILE`, `DEPTH_LIMIT`, `TOO_LARGE`, `BINARY` (byte NUL nos primeiros 8 KB) e `UNREADABLE`.
- `byDirectory` agrega pelo primeiro nível (`""` = arquivos da raiz). Diretórios excluídos não são percorridos; seus arquivos não entram nas contagens.
- `examples`: até 20 caminhos relativos por grupo, em ordem lexicográfica.
- `language`: id estável em minúsculas, detectado por nome, extensão e shebang; `text` quando não reconhecido.
- **Avisos:** `CASE_VARIANT_PROJECT` (outro projeto do workspace aponta para a mesma pasta com outras maiúsculas, D-020), `SCAN_LIMIT_REACHED`, `PROJECT_SIZE_LIMIT_EXCEEDED` e `NO_INCLUDED_FILES`.
- Limites: `app.inventory.max-entries` (200.000), `max-depth` (64), `max-file-bytes` (1 MB) e `max-project-bytes` (500 MB), os dois últimos ajustáveis por projeto.

Erros, nesta ordem:
1. `404 PROJECT_NOT_FOUND`;
2. `LOCAL_FOLDER`: `409 SOURCE_ROOT_NOT_CONFIGURED` (a `rootKey` saiu da configuração, D-020); depois `409 SOURCE_DIRECTORY_NOT_FOUND`, `409 SOURCE_IS_SYMLINK` ou `409 SOURCE_NOT_READABLE`, na ordem dos segmentos;
3. `ZIP`: `409 SOURCE_NOT_IMPORTED`, se não houver import atual.

### `GET /projects/{projectId}/settings`
Exclusões e limites do projeto (D-036). Sem configuração salva, devolve os padrões, com `version = 0` e `updatedAt = null`.

```json
{
  "excludePatterns": ["docs/generated/"],
  "disabledDefaultRules": ["vendor"],
  "maxFileBytes": null,
  "maxProjectBytes": null,
  "effectiveMaxFileBytes": 1048576,
  "effectiveMaxProjectBytes": 524288000,
  "defaultRules": [
    { "id": "vendor", "category": "DEPENDENCIES", "patterns": ["vendor/"], "exceptions": [],
      "overridable": true, "disabled": true }
  ],
  "version": 1,
  "updatedAt": "2026-10-16T12:00:00Z"
}
```

Erros: `404 PROJECT_NOT_FOUND`.

### `PUT /projects/{projectId}/settings`
Substitui as configurações (D-038). Request: `{ excludePatterns, disabledDefaultRules, maxFileBytes, maxProjectBytes }`; listas ausentes são vazias, e limites `null` voltam ao padrão. Response: o mesmo formato do `GET`, com `version` incrementada.

Validação (todos os erros numa única resposta, com `field` indexado, ex.: `excludePatterns[3]`):
- até 100 padrões, cada um com 1 a 200 caracteres, na sintaxe da D-036; duplicados são removidos, preservando a ordem;
- `disabledDefaultRules`: ids existentes e desativáveis (regras `SENSITIVE`, `VCS` e `MEDIA_BINARY` não podem ser desativadas);
- limites entre 1 KB e os tetos `app.inventory.max-file-bytes-limit` (10 MB) e `max-project-bytes-limit` (2 GB).

Erros, nesta ordem: `400 VALIDATION_FAILED`, `404 PROJECT_NOT_FOUND`. A mudança vale para o próximo inventário e para a próxima indexação; o projeto passa a informar `SETTINGS_CHANGED` em `outdatedReasons`.

### `DELETE /projects/{projectId}`
Remove projeto, imports, índices, snapshots e histórico em cascata (D-021).

Responses:
- `204 No Content`;
- `404 PROJECT_NOT_FOUND`;
- `409 PROJECT_BUSY` se houver job ativo no projeto, de importação ou de indexação (D-041). Cancelamento de job é evolução futura.

A remoção trava a linha do projeto antes de verificar os jobs, o que a serializa com a criação concorrente de jobs. As pastas dos imports são apagadas depois do commit.

## 6. Importação ZIP

O projeto `ZIP` é criado primeiro (seção 5) e o arquivo é enviado depois.

### `POST /projects/{projectId}/imports/zip`
Upload `multipart/form-data`, com o arquivo na parte `file`.

Response: `202 Accepted` com o import no formato de `GET /imports/{importJobId}` (estado `PENDING`) e cabeçalho `Location`. A extração roda em segundo plano depois do commit (D-034).

Regras:
- válido apenas para projetos com `sourceType` `ZIP`;
- tamanho máximo do arquivo em `app.import.max-zip-bytes` (padrão 100 MB, hipótese a ajustar); os limites do multipart do Spring derivam dele, e a aplicação não inicia se forem menores (D-028);
- no máximo um job ativo por projeto, de importação ou de indexação, garantido por índice único (seguro sob concorrência, D-041);
- cada upload cria um novo import em `DATA_DIR/imports/<importJobId>` (D-028);
- `current_import_job_id` do projeto só é substituído quando o novo import conclui com sucesso; a pasta do anterior é então apagada (D-033);
- o nome original do arquivo não é persistido nem logado.

Erros, nesta ordem:
1. `400 VALIDATION_FAILED` (`field = "file"`): parte ausente ou vazia;
2. `413 UPLOAD_TOO_LARGE`: arquivo acima de `app.import.max-zip-bytes` (o limite do multipart responde o mesmo código antes de a requisição chegar ao serviço);
3. `404 PROJECT_NOT_FOUND`;
4. `409 PROJECT_SOURCE_MISMATCH`: o projeto é `LOCAL_FOLDER` (D-020);
5. `409 PROJECT_BUSY`: já existe job ativo no projeto (import ou indexação).

**Extração segura** (`07-SECURITY-PRIVACY-COSTS.md` §4): limites contados pelos bytes realmente gravados (`max-entries` 50.000, `max-uncompressed-bytes` 1 GB, `max-entry-bytes` 100 MB, `max-compression-ratio` 100 a partir de `compression-ratio-min-bytes` 1 MB, `max-depth` 64, `max-path-length` 1.024); nomes absolutos, com `..`, `\`, letra de unidade ou caracteres de controle são recusados; entradas repetidas (inclusive por maiúsculas em FS sem distinção de caixa) são recusadas; links nunca são criados. Violações terminam o job como `REJECTED`; falhas de E/S, como `FAILED`.

### `GET /imports/{importJobId}`
```json
{
  "id": "…",
  "projectId": "…",
  "status": "REJECTED",
  "compressedBytes": 2048,
  "uncompressedBytes": null,
  "entryCount": null,
  "error": { "code": "ZIP_UNSAFE_PATH", "message": "O ZIP contém caminhos inseguros (…)." },
  "createdAt": "2026-10-16T12:00:00Z",
  "completedAt": "2026-10-16T12:00:01Z"
}
```

`status`: `PENDING`, `EXTRACTING`, `COMPLETED`, `REJECTED` ou `FAILED`. `error.code` é um código estável (`05-DATA-MODEL.md`, `import_jobs`); `error.message` vem em pt-BR e nunca contém nomes tirados do arquivo. Jobs interrompidos por um encerramento da aplicação terminam como `FAILED` com `IMPORT_INTERRUPTED` na próxima inicialização.

Erros: `404 IMPORT_NOT_FOUND`.

### `GET /projects/{projectId}/imports`
Lista os imports do projeto, do mais novo para o mais antigo, no formato acima. Paginada (§1, D-030).

Erros: `404 PROJECT_NOT_FOUND`.

## 7. Indexação e busca

A indexação é sempre incremental por conteúdo (D-039, D-045): todo arquivo incluído é lido e tem o hash calculado; só conteúdos novos geram conteúdo e chunks. Mudanças de configuração ou de estratégia de chunking são cobertas pelo mesmo mecanismo. Um novo job reprocessa os arquivos que estavam `FAILED` no snapshot anterior.

### `POST /projects/{projectId}/index-jobs`
Cria um snapshot (`BUILDING`) e um job de indexação. Sem corpo.

Response: `202 Accepted` com o job no formato de `GET /index-jobs/{jobId}` (estado `PENDING`) e cabeçalho `Location`. O processamento roda em segundo plano, por uma fila em tabela (D-040).

Erros, nesta ordem:
1. `404 PROJECT_NOT_FOUND`;
2. `409 SOURCE_ROOT_NOT_CONFIGURED`, `SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE` ou `SOURCE_NOT_IMPORTED`: os mesmos do inventário (§5), verificados sem varrer;
3. `409 PROJECT_BUSY`: já existe job ativo no projeto, de importação ou de indexação (D-041).

### `GET /index-jobs/{jobId}`
```json
{
  "id": "…",
  "projectId": "…",
  "snapshotId": "…",
  "status": "RUNNING",
  "stage": "PROCESSING_FILES",
  "progress": { "total": 980, "completed": 400 },
  "attempts": 1,
  "error": null,
  "createdAt": "2026-10-16T12:00:00Z",
  "startedAt": "2026-10-16T12:00:01Z",
  "completedAt": null
}
```

- `status`: `PENDING`, `RUNNING`, `COMPLETED` ou `FAILED`.
- `stage`: `SCANNING`, `PROCESSING_FILES`, `LEXICAL`, `EMBEDDING` ou `FINALIZING` (`null` antes de começar). `progress` conta arquivos; nas etapas `LEXICAL` e `EMBEDDING`, conta os chunks sem índice lexical da versão atual e os chunks a enviar ao provedor (D-050, D-056). Com `provider=none`, a etapa `EMBEDDING` é pulada.
- `attempts`: quantas vezes o job foi retirado da fila. Um job interrompido (queda da aplicação, por exemplo) volta à fila quando o heartbeat vence e é retomado automaticamente, até `app.indexing.max-attempts` (padrão 3).
- `error.code` (D-044): `SOURCE_ROOT_NOT_CONFIGURED`, `SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE`, `SOURCE_NOT_IMPORTED`, `SCAN_LIMIT_REACHED`, `PROJECT_SIZE_LIMIT_EXCEEDED`, `NO_INCLUDED_FILES`, `INDEX_IO_ERROR` ou `INDEX_ATTEMPTS_EXHAUSTED`; `error.message` em pt-BR, sem caminhos.
- Códigos da etapa de embeddings (D-051, D-054): `EMBEDDING_PROVIDER_UNAUTHORIZED`, `EMBEDDING_PROVIDER_UNAVAILABLE`, `EMBEDDING_RATE_LIMITED`, `EMBEDDING_QUOTA_EXCEEDED`, `EMBEDDING_REQUEST_REJECTED` e `EMBEDDING_CHUNK_LIMIT_EXCEEDED`. Nenhuma mensagem traz detalhes do provedor.
  - `EMBEDDING_QUOTA_EXCEEDED`: créditos, limite de gasto ou limite de uso da conta no provedor esgotados. O job falha sem repetir. Mensagem: "A cota ou o limite de gastos da conta no provedor de embeddings foi atingido. Verifique os créditos e os limites da conta e tente novamente."

Não há `retry`: um job `FAILED` é refeito criando outro, que reaproveita o conteúdo já gravado (D-045).

Erros: `404 INDEX_JOB_NOT_FOUND`.

### `GET /projects/{projectId}/index-jobs`
Lista os jobs de indexação do projeto, do mais novo para o mais antigo, no formato acima. Paginada (§1, D-030).

Erros: `404 PROJECT_NOT_FOUND`.

### `GET /projects/{projectId}/snapshots`
Lista os snapshots do projeto, do mais novo para o mais antigo, no formato de `GET /snapshots/{snapshotId}` sem `inventory`. Paginada (§1, D-030).

Erros: `404 PROJECT_NOT_FOUND`.

### `GET /snapshots/{snapshotId}`
```json
{
  "id": "…",
  "projectId": "…",
  "status": "PARTIAL",
  "active": true,
  "settingsVersion": 2,
  "chunkingVersion": "lines-v1",
  "sourceImportId": null,
  "counts": { "files": 980, "indexedFiles": 975, "unsupportedFiles": 4, "failedFiles": 1, "chunks": 4120, "bytes": 7340032 },
  "changes": { "added": 3, "modified": 12, "removed": 1, "unchanged": 959 },
  "inventory": { "…": "formato de GET /projects/{projectId}/inventory" },
  "embedding": { "provider": "openai", "model": "text-embedding-3-small", "dimensions": 1536, "tokens": 412380 },
  "lexical": { "version": "ident-v1" },
  "createdAt": "2026-10-16T12:00:00Z",
  "completedAt": "2026-10-16T12:00:05Z",
  "prunedAt": null
}
```

- `status`: `BUILDING`, `READY`, `PARTIAL` (com arquivos `FAILED`, ainda assim ativado) ou `FAILED` (não ativado).
- `changes`: comparação dos arquivos `INDEXED` com o snapshot ativo anterior; `null` enquanto `BUILDING`, em `FAILED` e no primeiro snapshot.
- `inventory`: agregado da varredura que originou o snapshot, sem os avisos que dependem do momento da consulta.
- `prunedAt`: preenchido quando a poda removeu os arquivos do snapshot (D-044); a lista de arquivos fica vazia.
- `embedding`: modelo dos vetores do snapshot, ou `null` quando foi indexado com `provider=none` (D-053). `tokens` soma o uso reportado pelo provedor no job; `null` se algum lote não reportou, `0` se todos os chunks já estavam no cache (D-054). Também aparece na listagem.
- `lexical`: versão do índice lexical do snapshot, ou `null` quando foi indexado antes da busca lexical (D-056). Também aparece na listagem.

Erros: `404 SNAPSHOT_NOT_FOUND`.

### `POST /projects/{projectId}/search`
Busca trechos de um snapshot pela pergunta, nos modos lexical, vetorial ou híbrido (D-060). Nos modos `VECTOR` e `HYBRID`, a pergunta é enviada ao provedor de embeddings configurado (D-054); o `LEXICAL` não usa o provedor.

```json
{ "query": "como o reembolso é calculado?", "snapshotId": null, "limit": 10, "mode": "HYBRID" }
```

- `query`: obrigatório, de 1 a 2.000 caracteres, não em branco.
- `snapshotId`: opcional; ausente = snapshot ativo. Informado, precisa ser do projeto, `READY` ou `PARTIAL` e não podado.
- `limit`: opcional, de 1 a 50 (padrão 10).
- `mode`: opcional, `LEXICAL`, `VECTOR` ou `HYBRID` (nome exato); ausente ou `null` = `HYBRID`.
  - `LEXICAL`: full-text sobre os termos da pergunta, com identificadores separados como na indexação (D-055 a D-057).
  - `VECTOR`: similaridade de cosseno exata (D-049).
  - `HYBRID`: fusão RRF (k=60) das buscas lexical, vetorial e por caminhos de arquivo citados na pergunta, com 50 candidatos por busca (D-058, D-059).

Response `200`:
```json
{
  "snapshotId": "…",
  "mode": "HYBRID",
  "model": { "provider": "openai", "name": "text-embedding-3-small", "dimensions": 1536 },
  "lexicalVersion": "ident-v1",
  "results": [
    {
      "chunkId": "…",
      "path": "src/billing/RefundService.java",
      "otherPaths": [],
      "startLine": 12,
      "endLine": 58,
      "content": "…",
      "score": 0.0325,
      "ranks": { "lexical": 2, "vector": 1, "path": null }
    }
  ]
}
```

- `model`: `null` no modo `LEXICAL`. `lexicalVersion`: `null` no modo `VECTOR`.
- `score` depende do modo: 1 − distância de cosseno (`VECTOR`), `ts_rank_cd` (`LEXICAL`) ou RRF (`HYBRID`). Maior é melhor; só serve para ordenar dentro de uma resposta. Empates são resolvidos pelo caminho, pela linha inicial e pelo id do chunk; no `HYBRID`, antes deles, pelo melhor rank individual.
- `ranks`: posição do trecho (a partir de 1) em cada busca feita, ou `null` quando ela não o trouxe. Nos modos de um canal só, só esse canal tem rank; a busca por caminhos só roda no `HYBRID` e só quando a pergunta cita um arquivo do snapshot.
- Cada chunk aparece uma vez. Se o mesmo conteúdo está em vários arquivos do snapshot, `path` é o primeiro caminho em ordem lexicográfica e `otherPaths` traz os demais (D-039).
- Pergunta só com stopwords não tem termos: o canal lexical não traz nada (sem erro).

Erros, nesta ordem:
1. `400 VALIDATION_FAILED` (`query`, `limit` e `mode`, antes de qualquer consulta);
2. `404 PROJECT_NOT_FOUND`;
3. `404 SNAPSHOT_NOT_FOUND`, inclusive snapshot de outro projeto;
4. `409 SNAPSHOT_NOT_SEARCHABLE`: projeto sem snapshot ativo, ou snapshot `BUILDING`, `FAILED` ou podado;
5. `409 SNAPSHOT_LEXICAL_MISMATCH` (`LEXICAL` e `HYBRID`): snapshot sem índice lexical ou de outra versão do tokenizador; reindexe o projeto;
6. `409 EMBEDDINGS_NOT_AVAILABLE` (`VECTOR` e `HYBRID`): `app.embeddings.provider=none`; o modo `LEXICAL` continua disponível;
7. `409 SNAPSHOT_EMBEDDING_MISMATCH` (`VECTOR` e `HYBRID`): snapshot sem embeddings ou de outro provedor, modelo ou dimensão; reindexe o projeto;
8. erros do provedor (D-051), sem detalhes do provedor na mensagem: `502 EMBEDDING_PROVIDER_UNAVAILABLE`, `502 EMBEDDING_PROVIDER_UNAUTHORIZED`, `502 EMBEDDING_QUOTA_EXCEEDED`, `502 EMBEDDING_REQUEST_REJECTED` e `429 EMBEDDING_RATE_LIMITED`. A busca não repete chamadas ao provedor.

O log registra modo, snapshot, quantidade de resultados e duração por canal, nunca a pergunta (D-054, D-060).

## 8. Perguntas e respostas

### `POST /projects/{projectId}/questions`

Request:
```json
{
  "question": "Como a autenticação funciona?",
  "snapshotId": "uuid",
  "conversationId": null,
  "options": {
    "maxEvidence": 8,
    "answerLength": "MEDIUM"
  }
}
```

Response conceitual:
```json
{
  "answerId": "uuid",
  "conversationId": "uuid",
  "status": "ANSWERED",
  "answer": "Resposta baseada nas evidências recuperadas.",
  "evidence": [
    {
      "id": "uuid",
      "citationLabel": "E1",
      "path": "src/example/File.java",
      "startLine": 10,
      "endLine": 28,
      "snippet": "Trecho real do arquivo...",
      "validationStatus": "VALID"
    }
  ],
  "metrics": {
    "latencyMs": 1240,
    "inputTokens": null,
    "outputTokens": null,
    "estimatedCost": null,
    "costStatus": "UNKNOWN"
  }
}
```

Os valores acima são exemplos de contrato, não dados reais. O backend deve derivar as evidências do conjunto recuperado e validar cada referência.

### `GET /answers/{answerId}`
Busca resposta e evidências persistidas.

## 9. Arquivos e evidências

### `GET /snapshots/{snapshotId}/files?status=&pathPrefix=`
Lista os arquivos incluídos pela varredura do snapshot (substitui `GET /projects/{projectId}/files`, para o snapshot ser explícito, D-045). Paginada (§1, D-030), ordenada por `relativePath`. Arquivos excluídos pelas regras não aparecem; estão no `inventory` do snapshot.

```json
{
  "items": [
    { "relativePath": "src/App.java", "status": "INDEXED", "reason": null, "language": "java",
      "sizeBytes": 2048, "lineCount": 80, "contentHash": "9f86d0…", "chunkCount": 2 },
    { "relativePath": "data/dump.sql", "status": "UNSUPPORTED", "reason": "LINE_TOO_LONG", "language": "sql",
      "sizeBytes": 524288, "lineCount": null, "contentHash": null, "chunkCount": 0 }
  ],
  "page": 0, "size": 20, "totalItems": 2, "totalPages": 1
}
```

- `status`: `INDEXED`, `UNSUPPORTED` ou `FAILED`, com `reason` (D-042):
  - `UNSUPPORTED`: `BINARY`, `INVALID_ENCODING` ou `LINE_TOO_LONG`;
  - `FAILED`: `READ_ERROR` ou `CHANGED_DURING_READ`.
- Filtros: `status` (ex.: `FAILED` ou `UNSUPPORTED`, para listar os arquivos não indexados, D-044) e `pathPrefix` (prefixo de diretório, na mesma normalização de `relativePath`).
- Snapshot podado: lista vazia (`prunedAt` no snapshot).

Erros: `400 VALIDATION_FAILED` (filtros), `404 SNAPSHOT_NOT_FOUND`.

### `GET /snapshots/{snapshotId}/files/content?path=...`
Retorna o conteúdo do arquivo como estava no snapshot, a partir de `file_contents` (D-039). Implementação na Etapa 6/7. Só arquivos `INDEXED` têm conteúdo; nada é lido do disco nesta chamada.

### `GET /projects/{projectId}/evidence/{evidenceId}`
Retorna a evidência como foi citada (Etapa 6, D-046): caminho (`path`) e demais caminhos com o mesmo conteúdo (`otherPaths`), linhas, hash do conteúdo, trecho copiado no momento da resposta e `snapshotStatus`. Se o snapshot foi podado, a resposta continua `200` com a cópia e `snapshotStatus: "PRUNED"` ("snapshot removido"); o visualizador então não abre o arquivo inteiro.

## 10. Histórico

### `GET /projects/{projectId}/conversations`
Lista conversas.

### `GET /conversations/{conversationId}`
Retorna mensagens e respostas com evidências.

## 11. Contratos e implementação

- Publicar OpenAPI; o contrato versionado (`apps/backend/openapi.json`) é verificado por teste (D-023).
- O contrato publica as respostas de erro como `application/problem+json` com o schema `Problem` (enum com todos os `code`) e `FieldError`. Por operação: `500 INTERNAL_ERROR` sempre; `400 VALIDATION_FAILED` quando há parâmetros ou corpo; `400 MALFORMED_REQUEST` e `415 UNSUPPORTED_MEDIA_TYPE` quando há corpo; códigos de domínio declarados no endpoint com `@ApiErrors`.
- Todos os campos dos schemas de resposta (`*Response`) são marcados como obrigatórios, já que são sempre serializados (valores ausentes vêm como `null`).
- Gerar tipos do frontend a partir do contrato.
- Versionar mudanças incompatíveis.
- Testar validação, erros, limites e respostas com evidências.
- Não colocar lógica de negócio apenas no controller.
- Controllers Spring devem delegar para casos de uso/serviços de aplicação.
