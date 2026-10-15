# Modelo de dados — Codebase Copilot

## 1. Princípios

- Toda evidência pertence a um snapshot específico.
- Hashes possibilitam reindexação incremental.
- Metadados de provedor e estratégia permitem reproduzir e avaliar resultados.
- Conteúdo sensível não deve aparecer em logs.
- Esquema é versionado com Flyway.
- A representação vetorial precisa ser compatível com o modelo de embedding configurado.

## 2. Entidades principais

### `workspaces`
- `id` UUID, PK
- `name`
- `created_at`
- `updated_at`

### `projects`
- `id` UUID, PK
- `workspace_id` FK
- `name`
- `source_type` (`LOCAL_FOLDER`, `ZIP`)
- `root_label` (rótulo amigável; evitar persistir caminho absoluto desnecessariamente)
- `active_snapshot_id` nullable
- `created_at`
- `updated_at`

### `project_settings`
- `project_id` PK/FK
- `include_patterns` JSONB
- `exclude_patterns` JSONB
- `max_file_bytes`
- `max_project_bytes`
- `settings_version`
- `updated_at`

### `snapshots`
- `id` UUID, PK
- `project_id` FK
- `status` (`PENDING`, `SCANNING`, `INDEXING`, `READY`, `PARTIAL`, `FAILED`)
- `source_fingerprint`
- `chunking_version`
- `embedding_provider`
- `embedding_model`
- `embedding_dimensions`
- `started_at`
- `completed_at`
- `error_summary` (sanitizado)

### `files`
- `id` UUID, PK
- `project_id` FK
- `relative_path`
- `content_hash`
- `size_bytes`
- `language`
- `mime_type`
- `status` (`INDEXED`, `EXCLUDED`, `UNSUPPORTED`, `FAILED`)
- `exclusion_reason`
- `last_seen_snapshot_id`
- `created_at`
- `updated_at`

Índice único recomendado para arquivo ativo: `(project_id, relative_path)`; histórico por snapshot deve ser representado explicitamente se for necessário reconstruir versões antigas.

### `chunks`
- `id` UUID, PK
- `project_id` FK
- `snapshot_id` FK
- `file_id` FK
- `relative_path`
- `content_hash`
- `start_line`
- `end_line`
- `content`
- `language`
- `chunk_type`
- `symbol_name` nullable
- `token_count_estimate`
- `chunking_version`
- `created_at`

### `chunk_embeddings`
- `chunk_id` FK/PK ou chave composta apropriada
- `provider`
- `model`
- `dimensions`
- `embedding` vector(dimensão configurada)
- `created_at`

**Nota:** a dimensão de `vector` pode ser fixa por tabela ou separada por coleção/modelo. Não misturar dimensões incompatíveis. A decisão final depende do provedor escolhido.

### `index_jobs`
- `id` UUID, PK
- `project_id` FK
- `snapshot_id` FK
- `status`
- `stage`
- `progress_total`
- `progress_completed`
- `attempt_count`
- `started_at`
- `completed_at`
- `error_code`
- `error_message_sanitized`

### `conversations`
- `id` UUID, PK
- `project_id` FK
- `snapshot_id` FK
- `title`
- `created_at`
- `updated_at`

### `messages`
- `id` UUID, PK
- `conversation_id` FK
- `role` (`USER`, `ASSISTANT`, `SYSTEM_SUMMARY`)
- `content`
- `created_at`

### `answers`
- `id` UUID, PK
- `message_id` FK
- `status` (`ANSWERED`, `PARTIAL`, `ABSTAINED`, `FAILED`)
- `provider`
- `model`
- `retrieval_metadata` JSONB
- `latency_ms`
- `input_tokens` nullable
- `output_tokens` nullable
- `estimated_cost` nullable
- `cost_currency` nullable
- `cost_status` (`MEASURED`, `ESTIMATED`, `UNKNOWN`)
- `created_at`

### `answer_evidence`
- `answer_id` FK
- `chunk_id` FK
- `citation_label`
- `start_line`
- `end_line`
- `rank_lexical` nullable
- `rank_vector` nullable
- `final_rank`
- `validation_status`

## 3. Relações

```text
Workspace 1---N Project
Project   1---N Snapshot
Project   1---N File
Snapshot  1---N Chunk
File      1---N Chunk
Project   1---N Conversation
Conversation 1---N Message
Message   1---0..1 Answer
Answer    N---N Chunk (via answer_evidence)
Project   1---N IndexJob
```

## 4. Busca lexical

Avaliar `tsvector`/`tsquery` do PostgreSQL, com configuração apropriada para identificadores de código. Busca por símbolos e caminhos pode exigir campos normalizados ou uma consulta adicional. A implementação deve preservar termos como `getUserById`, `HTTPClient` e nomes de arquivos.

## 5. Migrações e acesso

- Usar Flyway para criar e evoluir o esquema.
- Não depender de `ddl-auto` em produção; usar migrações explícitas.
- Preferir transações para substituir chunks de um arquivo.
- Fazer operações de indexação idempotentes.
- Usar SQL explícito e testes de integração para pgvector e recuperação híbrida.
- Manter dados de domínio separados de tipos de framework quando viável.

## 6. Retenção e limpeza

Definir política para:
- snapshots antigos;
- conversas e respostas;
- chunks e embeddings órfãos;
- jobs expirados;
- exclusão de projeto e remoção de dados locais.

Excluir um projeto deve remover índices e histórico associados, conforme a política de retenção documentada.
