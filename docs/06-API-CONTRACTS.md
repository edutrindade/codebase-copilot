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

Workspaces e projetos são ordenados por `createdAt` decrescente (desempate por `id` decrescente). Listagens grandes (arquivos, chunks) serão reavaliadas na Etapa 2 ou 3.

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

Response `200 OK`:
```json
[
  { "name": "codebase-copilot", "relativePath": "codebase-copilot", "hasChildren": true }
]
```

Regras:
- o backend canonicaliza `path` e valida que permanece dentro da raiz;
- links simbólicos não são listados nem seguidos;
- apenas diretórios são listados.

Erros:
- `404 ROOT_NOT_FOUND`;
- `400 PATH_OUTSIDE_ROOT`;
- `404 DIRECTORY_NOT_FOUND`.

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
Retorna metadados, origem, snapshot ativo e estado de indexação. Os campos de snapshot e de indexação entram na Etapa 3; até lá, a resposta traz apenas metadados e origem:

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
  "createdAt": "2026-10-15T12:00:00.123456Z"
}
```

`source` é polimórfico, com discriminador `type`. Para projetos `ZIP`, traz `type` e `currentImport` (`id` e `status`), `null` até o primeiro upload concluído (Etapa 2).

Erros: `404 PROJECT_NOT_FOUND`.

### `GET /projects/{projectId}/inventory`
Retorna linguagens, diretórios, contagens e exclusões.

### `PATCH /projects/{projectId}/settings`
Atualiza padrões de inclusão/exclusão e limites. Requer validação e pode exigir reindexação.

### `DELETE /projects/{projectId}`
Remove projeto, imports, índices, snapshots e histórico em cascata (D-021).

Responses:
- `204 No Content`;
- `404 PROJECT_NOT_FOUND`;
- `409 PROJECT_BUSY` se houver job de importação ou indexação em execução. Cancelamento de job é evolução futura.

## 6. Importação ZIP

O projeto `ZIP` é criado primeiro (seção 5) e o arquivo é enviado depois.

### `POST /projects/{projectId}/imports/zip`
Upload multipart com limites rígidos de tamanho.

Response: `202 Accepted` com `importJobId` e estado inicial.

Regras:
- válido apenas para projetos com `sourceType` `ZIP`; caso contrário, `409 PROJECT_SOURCE_MISMATCH`;
- tamanho máximo do arquivo em `app.import.max-zip-bytes` (padrão 100 MB, hipótese a ajustar); os limites do multipart do Spring são coerentes com ele (D-028);
- no máximo um import em andamento por projeto;
- cada upload cria um novo import em `DATA_DIR/imports/<importJobId>` (D-028);
- `current_import_job_id` do projeto só é substituído quando o novo import conclui com sucesso.

Erros:
- `404 PROJECT_NOT_FOUND`;
- `409 PROJECT_SOURCE_MISMATCH`: o projeto é `LOCAL_FOLDER` (operação incompatível com o tipo de origem, D-020);
- `409 PROJECT_BUSY`: já existe import em andamento no projeto;
- `413 UPLOAD_TOO_LARGE`: arquivo acima de `app.import.max-zip-bytes` ou dos limites do multipart;
- `400 VALIDATION_FAILED`: requisição sem arquivo.

O serviço precisa validar path traversal, quantidade de entradas, tamanho descompactado e tipos permitidos.

### `GET /imports/{importJobId}`
Retorna estado, tamanhos, contagem de entradas, erros sanitizados e timestamps.

## 7. Indexação

### `POST /projects/{projectId}/index-jobs`
Cria snapshot e inicia indexação.

Request:
```json
{
  "mode": "INCREMENTAL"
}
```

Valores: `INCREMENTAL`, `FULL`.

Response: `202 Accepted`:
```json
{
  "jobId": "uuid",
  "snapshotId": "uuid",
  "status": "PENDING"
}
```

### `GET /index-jobs/{jobId}`
Retorna estado, etapa, progresso, erros sanitizados e timestamps.

### `POST /index-jobs/{jobId}/retry`
Tenta novamente etapas recuperáveis de um job falho/parcial.

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

### `GET /projects/{projectId}/files`
Lista arquivos indexados com paginação e filtros.

### `GET /projects/{projectId}/files/content?path=...&snapshotId=...`
Retorna conteúdo permitido para visualização, respeitando raiz e snapshot. Preferir identificadores internos em vez de confiar em caminhos arbitrários. A origem do conteúdo depende da O-008.

### `GET /projects/{projectId}/evidence/{chunkId}`
Retorna trecho e metadados da evidência.

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
