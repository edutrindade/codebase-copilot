# Modelo de dados — Codebase Copilot

## 1. Princípios

- Toda evidência pertence a um snapshot específico.
- Hashes possibilitam reindexação incremental.
- Metadados de provedor e estratégia permitem reproduzir e avaliar resultados.
- Conteúdo sensível não deve aparecer em logs.
- Esquema é versionado com Flyway.
- A representação vetorial precisa ser compatível com o modelo de embedding configurado.
- Identificadores são UUID v7 gerados na aplicação pelo `IdGenerator` do módulo `shared` (D-024).
- Caminhos absolutos do sistema de arquivos não são persistidos (D-020, D-028).

> **⚠️ Pendente O-008 (antes da Etapa 3):** a estrutura de `snapshots`, `files` e `chunks`, a ligação entre snapshots e chunks/arquivos e o armazenamento do conteúdo completo de arquivos para o visualizador ainda serão decididos. Os trechos marcados abaixo não devem ser implementados como estão.

## 2. Entidades principais

### `workspaces`
- `id` UUID, PK
- `name` (padrão "Meus projetos"; até 100 caracteres após `trim` e NFC, D-032)
- `created_at`
- `updated_at`

### `projects`
- `id` UUID, PK
- `workspace_id` FK
- `name` (nome de exibição; obrigatório, até 100 caracteres após `trim` e NFC, D-032)
- `source_type` (`LOCAL_FOLDER`, `ZIP`)
- `source_root_key` nullable: chave da raiz permitida configurada no backend (`app.local-roots[n].key`), nunca caminho absoluto (D-018, D-020, D-029)
- `source_relative_path` nullable: caminho relativo à raiz, com normalização sintática (NFC, separador `/`, sem barras finais nem segmentos `.`; `\` e caracteres de controle rejeitados; sem acesso ao sistema de arquivos) e comparação sensível a maiúsculas (D-020, D-032); string vazia representa a própria raiz (entradas `""` e `"."` são gravadas como `""`)
- `current_import_job_id` nullable: último import ZIP concluído com sucesso; sem relação com snapshots (D-020). Criado na migração V4 (Etapa 2), com FK composta `(id, current_import_job_id)` → `import_jobs (project_id, id)`, que garante que o import pertence ao próprio projeto.
- `created_at`
- `updated_at`

Restrições:
- `LOCAL_FOLDER`: `source_root_key` e `source_relative_path` obrigatórios; `current_import_job_id` nulo.
- `ZIP`: `source_root_key` e `source_relative_path` nulos; `current_import_job_id` nulo até o primeiro upload concluído (`CHECK projects_current_import_zip_only`). Só o worker de importação o preenche, na mesma transação que marca o job `COMPLETED`.
- Índice único parcial `(workspace_id, source_root_key, source_relative_path) WHERE source_type = 'LOCAL_FOLDER'`: impede projeto duplicado no mesmo workspace (`409 PROJECT_ALREADY_EXISTS`, D-020). Nomes iguais são permitidos; projetos ZIP não são verificados.
- `projects.current_import_job_id` e `import_jobs.project_id` formam uma referência circular: a FK do import atual usa `ON DELETE SET NULL (current_import_job_id)` (PostgreSQL 15+), que anula só essa coluna, para que a remoção em cascata do projeto (D-021) funcione.

`active_snapshot_id` (FK para `snapshots`) **não entra na migração da Etapa 1**; será adicionada na migração da Etapa 3, junto com o modelo de snapshots (O-008).

### `project_settings`
Criada na migração V5 (Etapa 2, D-036). Linha opcional: sem linha, o projeto usa os padrões.
- `project_id` PK/FK (`ON DELETE CASCADE`)
- `exclude_patterns` `text[]`: padrões customizados, somados às regras padrão
- `disabled_default_rules` `text[]`: ids de regras padrão desativáveis
- `max_file_bytes` nullable: `null` usa `app.inventory.max-file-bytes`
- `max_project_bytes` nullable: `null` usa `app.inventory.max-project-bytes`
- `settings_version`: incrementada a cada `PUT`; a Etapa 3 a usa para decidir a reindexação
- `updated_at`

As regras padrão vivem no código (`DefaultExclusionRules`), não no banco. Não há `include_patterns` (D-036).

### `import_jobs`
- `id` UUID, PK
- `project_id` FK
- `status` (`PENDING`, `EXTRACTING`, `COMPLETED`, `REJECTED`, `FAILED`)
- `compressed_bytes`
- `uncompressed_bytes` nullable
- `entry_count` nullable
- `error_code` nullable: código estável (`ZIP_INVALID`, `ZIP_UNSAFE_PATH`, `ZIP_DUPLICATE_ENTRY`, `ZIP_TOO_MANY_ENTRIES`, `ZIP_ENTRY_TOO_LARGE`, `ZIP_UNCOMPRESSED_TOO_LARGE`, `ZIP_COMPRESSION_RATIO_EXCEEDED`, `ZIP_TOO_DEEP`, `IMPORT_INTERRUPTED`, `IMPORT_IO_ERROR`)
- `created_at`
- `completed_at` nullable

Criada na migração V4 (Etapa 2). `CHECK import_jobs_state_consistency`: jobs em andamento não têm `completed_at` nem `error_code`; `COMPLETED` tem `completed_at`, `uncompressed_bytes` e `entry_count`; `REJECTED` e `FAILED` têm `completed_at` e `error_code`. A mensagem exibida vem do `MessageSource` (`import.error.<CODE>`); não há coluna de mensagem (D-028, atualização de 16/10/2026).

Índice único parcial `(project_id) WHERE status IN ('PENDING', 'EXTRACTING')`: no máximo um import em andamento por projeto (`409 PROJECT_BUSY`, D-028).

O nome original do arquivo enviado não é persistido. O diretório de trabalho é derivado do ID (`DATA_DIR/imports/<importJobId>`, D-028) e não é armazenado.

### `snapshots`
> ⚠️ Pendente O-008 (antes da Etapa 3).

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
> ⚠️ Pendente O-008 (antes da Etapa 3), incluindo a unicidade e o histórico por snapshot.

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

Índice para busca de caminhos (D-017): GIN com `gin_trgm_ops` em `relative_path`.

### `chunks`
> ⚠️ Pendente O-008 (antes da Etapa 3): ligação com snapshots e arquivos, incluindo como o chunk referencia a versão do arquivo. O hash do arquivo fica em `files.content_hash`, não no chunk. As colunas de conteúdo, `chunk_hash` e as colunas lexicais independem dessa decisão.

- `id` UUID, PK
- `project_id` FK
- `snapshot_id` FK
- `file_id` FK
- `relative_path`
- `chunk_hash`: hash do texto do chunk combinado com `chunking_version`; chave para o cache de embeddings
- `start_line`
- `end_line`
- `content`
- `language`
- `chunk_type`
- `symbol_name` nullable
- `identifier_terms` text: identificadores do chunk pré-tokenizados pela aplicação, preservando também o original (ex.: `getUserById get user by id`) (D-017)
- `lexical_tsv` tsvector: `setweight(to_tsvector('simple', identifier_terms), 'A') || setweight(to_tsvector('simple', content), 'B')` (D-017)
- `token_count_estimate`
- `chunking_version`
- `created_at`

Índices de busca (D-017):
- GIN em `lexical_tsv`;
- GIN com `gin_trgm_ops` em `symbol_name`.

A tokenização de identificadores (camelCase, snake_case, siglas como `HTTPClient`) é feita na aplicação, porque fazê-la em SQL é frágil.

### `chunk_embeddings`
- `chunk_hash` (parte da PK)
- `provider` (parte da PK)
- `model` (parte da PK)
- `dimensions` (parte da PK)
- `embedding` vector(dimensão configurada)
- `created_at`

PK: `(chunk_hash, provider, model, dimensions)`. Chunks com o mesmo texto e a mesma versão de chunking reutilizam o embedding, o que implementa o cache por hash/modelo (`04-RAG-PIPELINE.md` §5).

**Nota:** a dimensão do `vector` é fixada por migração quando o provedor for escolhido (O-004). Não misturar dimensões incompatíveis.

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
Project   1---N ImportJob
Project   1---0..1 ImportJob (import atual via current_import_job_id, somente ZIP)
Project   1---N Snapshot                  ⚠️ pendente O-008
Project   1---N File                      ⚠️ pendente O-008
Snapshot  1---N Chunk                     ⚠️ pendente O-008
File      1---N Chunk                     ⚠️ pendente O-008
Chunk     N---1 ChunkEmbedding (por chunk_hash + modelo)
Project   1---N Conversation
Conversation 1---N Message
Message   1---0..1 Answer
Answer    N---N Chunk (via answer_evidence)
Project   1---N IndexJob
```

## 4. Busca lexical

Baseline definido na D-017 (comparação final em O-005):
- full-text do PostgreSQL com configuração `simple` (sem stemming), sobre `chunks.lexical_tsv`;
- identificadores pré-tokenizados em `chunks.identifier_terms`, com peso maior que o conteúdo;
- `pg_trgm` para caminhos (`files.relative_path`) e símbolos (`chunks.symbol_name`).

A implementação deve preservar termos como `getUserById`, `HTTPClient` e nomes de arquivos.

## 5. Migrações e acesso

- Usar Flyway para criar e evoluir o esquema.
- Não depender de `ddl-auto` em produção; usar migrações explícitas.
- Preferir transações para substituir chunks de um arquivo.
- Fazer operações de indexação idempotentes.
- Usar SQL explícito e testes de integração para pgvector e recuperação híbrida.
- Manter dados de domínio separados de tipos de framework quando viável.

## 6. Retenção e limpeza

Remoção (D-021):
- Excluir um projeto remove em cascata imports (incluindo diretórios remanescentes em `DATA_DIR/imports`), snapshots, arquivos, chunks, índices, conversas, respostas e evidências.
- A exclusão de projeto é recusada com `409 PROJECT_BUSY` enquanto houver job de importação ou indexação em execução.
- Excluir um workspace com projetos é recusado com `409 WORKSPACE_NOT_EMPTY`.
- A pasta de um import é apagada quando ele termina `REJECTED` ou `FAILED`, quando um import mais novo do mesmo projeto conclui e na remoção do projeto; a do import atual é retida como origem do projeto ZIP (D-033). Na inicialização, pastas que não pertencem a um import atual são removidas.

Ainda a definir:
- snapshots antigos;
- conversas e respostas;
- chunks e embeddings órfãos (depende de O-008);
- jobs expirados.
