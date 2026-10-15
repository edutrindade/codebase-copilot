# Contratos da API — Codebase Copilot

## 1. Convenções

- Base path: `/api/v1`. Todos os caminhos abaixo são relativos a ele.
- REST/JSON.
- UUID v7 para identificadores (D-024).
- Erros em ProblemDetail (RFC 9457) com `code` estável em inglês e mensagem segura em pt-BR (D-022, D-025).
- OpenAPI code-first como contrato principal; o contrato gerado é versionado em `apps/backend/openapi.json` (D-023).
- Validar entrada com Bean Validation.
- Não devolver caminhos absolutos do sistema operacional.
- Endpoints de consulta devem operar sobre um snapshot explícito ou sobre o snapshot ativo claramente indicado.

## 2. Formato de erro

Erros usam `ProblemDetail` (RFC 9457) do Spring, com `Content-Type: application/problem+json` e as propriedades extras `code` e `requestId` (D-022):

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "O projeto não foi encontrado.",
  "instance": "/api/v1/projects/0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e",
  "code": "PROJECT_NOT_FOUND",
  "requestId": "0192f5c4-7e3b-7a2f-8d4e-3a5b7c9d1e2f"
}
```

Erros de validação acrescentam `errors`, com itens contendo **apenas** `field` e `message`. O valor rejeitado **nunca** é devolvido:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
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

Não incluir stack traces, credenciais, conteúdo sensível, valores rejeitados ou caminhos internos nos erros destinados ao frontend.

## 3. Workspaces

### `POST /workspaces`
Cria workspace.

Request:
```json
{ "name": "Meus projetos" }
```

Response: `201 Created`, objeto com `id`, `name`, `createdAt`.

### `GET /workspaces`
Lista workspaces e resumo de projetos.

### `GET /workspaces/{workspaceId}`
Retorna detalhes do workspace.

### `DELETE /workspaces/{workspaceId}`
Remove o workspace (D-021).

Responses:
- `204 No Content`;
- `404 WORKSPACE_NOT_FOUND`;
- `409 WORKSPACE_NOT_EMPTY` se houver projetos. Os projetos devem ser removidos antes.

## 4. Raízes locais

Seleção segura de pasta local (D-018). O backend expõe apenas as raízes permitidas configuradas; o cliente nunca envia nem recebe caminhos absolutos.

### `GET /local-roots`
Lista as raízes permitidas.

Response `200 OK`:
```json
[
  { "key": "projects", "label": "Projetos" }
]
```

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
  "rootKey": "projects",
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

Response: `201 Created` com o projeto.

Erros:
- `404 WORKSPACE_NOT_FOUND`;
- `404 ROOT_NOT_FOUND`;
- `400 PATH_OUTSIDE_ROOT`;
- `404 DIRECTORY_NOT_FOUND`;
- `400 VALIDATION_FAILED` (por exemplo, `rootKey` informado para `ZIP`).

### `GET /workspaces/{workspaceId}/projects`
Lista os projetos do workspace, com paginação.

### `GET /projects/{projectId}`
Retorna metadados, origem, snapshot ativo e estado de indexação.

Origem:
```json
{
  "source": {
    "type": "LOCAL_FOLDER",
    "rootKey": "projects",
    "relativePath": "codebase-copilot"
  }
}
```

Para projetos `ZIP`, `source` traz `type` e `currentImport` (`id` e `status`), nulo até o primeiro upload concluído.

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
- cada upload cria um novo import em `DATA_DIR/imports/<importJobId>` (D-028);
- `current_import_job_id` do projeto só é substituído quando o novo import conclui com sucesso.

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
- Gerar tipos do frontend a partir do contrato.
- Versionar mudanças incompatíveis.
- Testar validação, erros, limites e respostas com evidências.
- Não colocar lógica de negócio apenas no controller.
- Controllers Spring devem delegar para casos de uso/serviços de aplicação.
