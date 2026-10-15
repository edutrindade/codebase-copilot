# Contratos da API — Codebase Copilot

## 1. Convenções

- Base path sugerido: `/api/v1`.
- REST/JSON.
- UUIDs para identificadores.
- Erros com código estável e mensagem segura.
- OpenAPI como contrato principal.
- Validar entrada com Bean Validation.
- Não devolver caminhos absolutos do sistema operacional sem necessidade.
- Endpoints de consulta devem operar sobre um snapshot explícito ou sobre o snapshot ativo claramente indicado.

## 2. Formato de erro

```json
{
  "code": "PROJECT_NOT_FOUND",
  "message": "O projeto não foi encontrado.",
  "requestId": "uuid",
  "details": []
}
```

Não incluir stack traces, credenciais, conteúdo sensível ou caminhos internos nos erros destinados ao frontend.

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
Retorna detalhes e projetos associados.

## 4. Projetos

### `POST /workspaces/{workspaceId}/projects`
Cria projeto a partir de uma pasta selecionada por um mecanismo local seguro ou de um ZIP previamente enviado.

**Nota de implementação:** navegador e backend local não compartilham automaticamente acesso irrestrito ao sistema de arquivos. A implementação deverá escolher e documentar um mecanismo seguro para seleção/importação local. Não aceitar um caminho arbitrário do cliente sem validação e limites.

### `GET /projects/{projectId}`
Retorna metadados, snapshot ativo e estado de indexação.

### `GET /projects/{projectId}/inventory`
Retorna linguagens, diretórios, contagens e exclusões.

### `PATCH /projects/{projectId}/settings`
Atualiza padrões de inclusão/exclusão e limites. Requer validação e pode exigir reindexação.

## 5. Importação ZIP

### `POST /projects/{projectId}/imports/zip`
Upload multipart com limites rígidos de tamanho.

Response: `202 Accepted` com `importJobId` e estado inicial.

O serviço precisa validar path traversal, quantidade de entradas, tamanho descompactado e tipos permitidos.

## 6. Indexação

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

## 7. Perguntas e respostas

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

## 8. Arquivos e evidências

### `GET /projects/{projectId}/files`
Lista arquivos indexados com paginação e filtros.

### `GET /projects/{projectId}/files/content?path=...&snapshotId=...`
Retorna conteúdo permitido para visualização, respeitando raiz e snapshot. Preferir identificadores internos em vez de confiar em caminhos arbitrários.

### `GET /projects/{projectId}/evidence/{chunkId}`
Retorna trecho e metadados da evidência.

## 9. Histórico

### `GET /projects/{projectId}/conversations`
Lista conversas.

### `GET /conversations/{conversationId}`
Retorna mensagens e respostas com evidências.

### `DELETE /projects/{projectId}`
Remove projeto, índices, snapshots e histórico conforme a política de exclusão.

## 10. Contratos e implementação

- Publicar OpenAPI.
- Gerar ou validar tipos de frontend a partir do contrato.
- Versionar mudanças incompatíveis.
- Testar validação, erros, limites e respostas com evidências.
- Não colocar lógica de negócio apenas no controller.
- Controllers Spring devem delegar para casos de uso/serviços de aplicação.
