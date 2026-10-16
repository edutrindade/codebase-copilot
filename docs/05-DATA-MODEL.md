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
- Conteúdo e chunks pertencem a uma **versão do conteúdo** (hash), não a um snapshot; o snapshot liga caminhos a hashes (D-039).

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
- `active_snapshot_id` nullable: snapshot usado nas consultas (D-044). Criado na migração da Etapa 3, com FK composta `(id, active_snapshot_id)` → `snapshots (project_id, id)` e `ON DELETE SET NULL (active_snapshot_id)`.
- `created_at`
- `updated_at`

Restrições:
- `LOCAL_FOLDER`: `source_root_key` e `source_relative_path` obrigatórios; `current_import_job_id` nulo.
- `ZIP`: `source_root_key` e `source_relative_path` nulos; `current_import_job_id` nulo até o primeiro upload concluído (`CHECK projects_current_import_zip_only`). Só o worker de importação o preenche, na mesma transação que marca o job `COMPLETED`.
- Índice único parcial `(workspace_id, source_root_key, source_relative_path) WHERE source_type = 'LOCAL_FOLDER'`: impede projeto duplicado no mesmo workspace (`409 PROJECT_ALREADY_EXISTS`, D-020). Nomes iguais são permitidos; projetos ZIP não são verificados.
- `projects.current_import_job_id` ↔ `import_jobs.project_id` e `projects.active_snapshot_id` ↔ `snapshots.project_id` formam referências circulares: as FKs do lado de `projects` usam `ON DELETE SET NULL (coluna)` (PostgreSQL 15+), que anula só essa coluna, para que a remoção em cascata do projeto (D-021) funcione.
- `active_snapshot_id` só é preenchido pelo worker de indexação, na transação que ativa o snapshot (D-044).

### `project_settings`
Criada na migração V5 (Etapa 2, D-036). Linha opcional: sem linha, o projeto usa os padrões.
- `project_id` PK/FK (`ON DELETE CASCADE`)
- `exclude_patterns` `text[]`: padrões customizados, somados às regras padrão
- `disabled_default_rules` `text[]`: ids de regras padrão desativáveis
- `max_file_bytes` nullable: `null` usa `app.inventory.max-file-bytes`
- `max_project_bytes` nullable: `null` usa `app.inventory.max-project-bytes`
- `settings_version`: incrementada a cada `PUT`; o snapshot guarda a versão usada, e o projeto fica desatualizado quando ela muda (D-044)
- `updated_at`

As regras padrão vivem no código (`DefaultExclusionRules`), não no banco. Não há `include_patterns` (D-036).

### `project_jobs`
Registro de todos os jobs do projeto, de import e de indexação (D-041). Criada na migração V6 (Etapa 3), preenchida com os imports existentes.
- `id` UUID, PK: o mesmo ID do job em `import_jobs` ou `index_jobs`, que têm FK para cá
- `project_id` FK (`ON DELETE CASCADE`)
- `kind` (`IMPORT`, `INDEX`)
- `active`: verdadeiro enquanto o job não termina; desativado na mesma transação da transição terminal do job
- `created_at`

Índice único parcial `(project_id) WHERE active`: no máximo um job ativo por projeto, de qualquer tipo (`409 PROJECT_BUSY`).

### `import_jobs`
- `id` UUID, PK; FK para `project_jobs (id)` (Etapa 3)
- `project_id` FK
- `status` (`PENDING`, `EXTRACTING`, `COMPLETED`, `REJECTED`, `FAILED`)
- `compressed_bytes`
- `uncompressed_bytes` nullable
- `entry_count` nullable
- `error_code` nullable: código estável (`ZIP_INVALID`, `ZIP_UNSAFE_PATH`, `ZIP_DUPLICATE_ENTRY`, `ZIP_TOO_MANY_ENTRIES`, `ZIP_ENTRY_TOO_LARGE`, `ZIP_UNCOMPRESSED_TOO_LARGE`, `ZIP_COMPRESSION_RATIO_EXCEEDED`, `ZIP_TOO_DEEP`, `IMPORT_INTERRUPTED`, `IMPORT_IO_ERROR`)
- `created_at`
- `completed_at` nullable

Criada na migração V4 (Etapa 2). `CHECK import_jobs_state_consistency`: jobs em andamento não têm `completed_at` nem `error_code`; `COMPLETED` tem `completed_at`, `uncompressed_bytes` e `entry_count`; `REJECTED` e `FAILED` têm `completed_at` e `error_code`. A mensagem exibida vem do `MessageSource` (`import.error.<CODE>`); não há coluna de mensagem (D-028, atualização de 16/10/2026).

Índice único parcial `(project_id) WHERE status IN ('PENDING', 'EXTRACTING')`: no máximo um import em andamento por projeto. Desde a Etapa 3, a exclusão entre tipos de job vem de `project_jobs` (D-041), e este índice fica como defesa adicional.

O nome original do arquivo enviado não é persistido. O diretório de trabalho é derivado do ID (`DATA_DIR/imports/<importJobId>`, D-028) e não é armazenado.

### `snapshots`
Um por job de indexação (D-044). As tabelas `snapshots`, `index_jobs`, `file_contents`, `snapshot_files` e `chunks` são criadas na migração V7 (Etapa 3).
- `id` UUID, PK
- `project_id` FK (`ON DELETE CASCADE`)
- `status` (`BUILDING`, `READY`, `PARTIAL`, `FAILED`)
- `source_import_job_id` nullable: import de origem, em projetos ZIP
- `settings_version`: versão das configurações usada na varredura
- `chunking_version`: estratégia de chunking (D-043)
- `inventory` JSONB: agregado da varredura, no formato de `GET /inventory` (D-035, atualização de 16/10/2026)
- contagens: `file_count`, `indexed_file_count`, `unsupported_file_count`, `failed_file_count`, `chunk_count`, `total_bytes`
- resumo de mudanças contra o snapshot ativo anterior: `added_count`, `modified_count`, `removed_count`, `unchanged_count`
- `created_at`
- `completed_at` nullable
- `pruned_at` nullable: preenchido quando a poda remove os `snapshot_files` (D-044)

- `embedding_provider`, `embedding_model`, `embedding_dimensions` nullable: modelo dos vetores do snapshot, todos nulos (indexado com `provider=none`) ou todos preenchidos (`snapshots_embedding_consistency`); V8, Etapa 4 (D-048, D-050)
- `embedding_tokens` nullable: soma dos tokens reportados pelo provedor no job; `null` se algum lote não reportou, `0` se nada foi enviado (D-054)

`READY` e `PARTIAL` (com arquivos `FAILED`) são ativáveis; `FAILED` não. Com provedor configurado, um snapshot só é ativado com todos os chunks com vetor do modelo (D-050).

### `index_jobs`
- `id` UUID, PK; FK para `project_jobs (id)`
- `project_id` FK
- `snapshot_id` FK, único (um snapshot por job)
- `status` (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`)
- `stage` nullable (`SCANNING`, `PROCESSING_FILES`, `EMBEDDING`, `FINALIZING`; `EMBEDDING` desde a V8)
- `progress_total`, `progress_completed` nullable
- `attempt_count`: incrementado a cada retirada da fila (D-040)
- `heartbeat_at` nullable: lease do worker
- `error_code` nullable: código estável (`SOURCE_*`, `SCAN_LIMIT_REACHED`, `PROJECT_SIZE_LIMIT_EXCEEDED`, `NO_INCLUDED_FILES`, `INDEX_IO_ERROR`, `INDEX_ATTEMPTS_EXHAUSTED` e os `EMBEDDING_*` da D-051 e da D-054); mensagem via `MessageSource` (`index.error.<CODE>`), sem coluna de mensagem
- `created_at`, `started_at`, `completed_at`

Retirada com `SELECT … FOR UPDATE SKIP LOCKED` sobre jobs `PENDING` ou `RUNNING` com heartbeat vencido; transições condicionadas ao estado e ao `attempt_count` (D-040).

### `file_contents`
Conteúdo decodificado de cada versão de arquivo, deduplicado por projeto (D-039).
- `project_id` FK (`ON DELETE CASCADE`)
- `content_hash`: SHA-256 dos bytes brutos (D-042)
- `content` text: UTF-8 decodificado, sem BOM, sem normalizar quebras de linha
- `size_bytes`
- `line_count`
- `created_at`

PK `(project_id, content_hash)`. Só arquivos `INDEXED` têm conteúdo. Serve de origem para o visualizador (F-06).

### `snapshot_files`
Estado de cada arquivo incluído pela varredura de um snapshot. Substitui a tabela `files` (D-039).
- `snapshot_id` FK (`ON DELETE CASCADE`)
- `project_id`
- `relative_path`
- `status` (`INDEXED`, `UNSUPPORTED`, `FAILED`)
- `reason` nullable: `UNSUPPORTED` → `BINARY`, `INVALID_ENCODING` ou `LINE_TOO_LONG`; `FAILED` → `READ_ERROR` ou `CHANGED_DURING_READ` (D-042)
- `content_hash` nullable: FK composta `(project_id, content_hash)` → `file_contents`; obrigatório só em `INDEXED`
- `language`
- `size_bytes`

PK `(snapshot_id, relative_path)`. Arquivos excluídos pelas regras não têm linha; aparecem apenas no agregado `snapshots.inventory`. Índice `(project_id, content_hash)` para a busca e a limpeza. Busca de caminhos por `pg_trgm` (D-017) sobre `relative_path` entra na Etapa 5.

### `chunks`
Imutáveis, ligados à versão do conteúdo (D-039).
- `id` UUID, PK
- `project_id`
- `content_hash`: FK composta `(project_id, content_hash)` → `file_contents` (`ON DELETE CASCADE`)
- `chunking_version`
- `ordinal`
- `start_line`
- `end_line`
- `content`
- `chunk_hash`: hash do texto do chunk combinado com `chunking_version`; chave para o cache de embeddings
- `token_count_estimate`

Chave natural única `(project_id, content_hash, chunking_version, ordinal)`: inserts idempotentes com `ON CONFLICT DO NOTHING`. Caminho e linguagem vêm de `snapshot_files`; a busca de um snapshot faz `JOIN` por `(project_id, content_hash)` e pela `chunking_version` do snapshot.

Colunas que entram em etapas posteriores:
- Etapa 5 (D-017): `identifier_terms` (identificadores pré-tokenizados pela aplicação, preservando o original, ex.: `getUserById get user by id`) e `lexical_tsv` (`setweight(to_tsvector('simple', identifier_terms), 'A') || setweight(to_tsvector('simple', content), 'B')`), com índice GIN;
- Etapa 9 (D-027): `chunk_type` e `symbol_name`, preenchidos por AST, com índice `pg_trgm` em `symbol_name`.

A tokenização de identificadores (camelCase, snake_case, siglas como `HTTPClient`) é feita na aplicação, porque fazê-la em SQL é frágil.

### `chunk_embeddings`
- `chunk_hash` (parte da PK)
- `provider` (parte da PK)
- `model` (parte da PK)
- `dimensions` (parte da PK)
- `dimensions` entre 1 e 2.000 (`chunk_embeddings_dimensions_valid`)
- `embedding` `vector` sem dimensão fixa, com `CHECK (vector_dims(embedding) = dimensions)` (`chunk_embeddings_dimensions_match`)
- `created_at`

Criada na migração V8 (Etapa 4, D-048). PK: `(chunk_hash, provider, model, dimensions)`, global (sem `project_id`) e sem FK para `chunks`. Chunks com o mesmo texto e a mesma versão de chunking reutilizam o embedding em qualquer projeto, o que implementa o cache por hash/modelo (`04-RAG-PIPELINE.md` §5). Vetores de modelos ou dimensões diferentes convivem; toda consulta filtra por provedor, modelo e dimensão.

Sem índice aproximado: a busca é exata sobre os chunks do snapshot (D-049). Um índice HNSW futuro será parcial por modelo, sobre `embedding::vector(N)`, sem migrar dados.

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
Cópia da evidência no momento da resposta, independente da retenção de snapshots (D-046).
- `answer_id` FK
- `snapshot_id`: referência sem FK com cascata; o snapshot pode ser podado
- `chunk_id` nullable: referência sem FK com cascata; o chunk pode ser limpo
- `citation_label`
- `relative_path`: caminho citado (o primeiro em ordem lexicográfica quando o mesmo conteúdo aparece em mais de um caminho, D-039)
- `other_paths` `text[]`: demais caminhos com o mesmo conteúdo no snapshot
- `content_hash`
- `start_line`
- `end_line`
- `snippet`: trecho citado
- `rank_lexical` nullable
- `rank_vector` nullable
- `final_rank`
- `validation_status`

Podar um snapshot nunca apaga respostas nem evidências; ao abrir uma evidência de snapshot podado, a API devolve a cópia indicando "snapshot removido" (D-046).

## 3. Relações

```text
Workspace 1---N Project
Project   1---N ProjectJob (registro de jobs; no máximo um ativo)
Project   1---N ImportJob            (ImportJob 1---1 ProjectJob)
Project   1---0..1 ImportJob (import atual via current_import_job_id, somente ZIP)
Project   1---N IndexJob             (IndexJob 1---1 ProjectJob)
IndexJob  1---1 Snapshot
Project   1---N Snapshot
Project   1---0..1 Snapshot (ativo via active_snapshot_id)
Snapshot  1---N SnapshotFile
SnapshotFile N---0..1 FileContent (por project_id + content_hash; só INDEXED)
FileContent 1---N Chunk (por chunking_version)
Chunk     N---1 ChunkEmbedding (por chunk_hash + modelo)
Project   1---N Conversation
Conversation 1---N Message
Message   1---0..1 Answer
Answer    1---N AnswerEvidence (cópia; referências soltas a Snapshot e Chunk)
```

## 4. Busca lexical

Baseline definido na D-017 (comparação final em O-005):
- full-text do PostgreSQL com configuração `simple` (sem stemming), sobre `chunks.lexical_tsv`;
- identificadores pré-tokenizados em `chunks.identifier_terms`, com peso maior que o conteúdo;
- `pg_trgm` para caminhos (`snapshot_files.relative_path`) e símbolos (`chunks.symbol_name`).

A implementação deve preservar termos como `getUserById`, `HTTPClient` e nomes de arquivos. A recuperação devolve cada chunk uma vez, mesmo quando o conteúdo aparece em mais de um caminho do snapshot (D-039).

## 5. Migrações e acesso

- Usar Flyway para criar e evoluir o esquema.
- Não depender de `ddl-auto` em produção; usar migrações explícitas.
- Preferir transações para gravar conteúdo, chunks e `snapshot_files` de um lote.
- Fazer operações de indexação idempotentes (`ON CONFLICT DO NOTHING` nas chaves naturais).
- Usar SQL explícito e testes de integração para pgvector e recuperação híbrida.
- Manter dados de domínio separados de tipos de framework quando viável.

## 6. Retenção e limpeza

Remoção (D-021):
- Excluir um projeto remove em cascata imports (incluindo diretórios remanescentes em `DATA_DIR/imports`), registros de jobs, snapshots, arquivos, conteúdos, chunks, conversas, respostas e evidências.
- A exclusão de projeto é recusada com `409 PROJECT_BUSY` enquanto houver job ativo em `project_jobs` (import ou indexação, D-041).
- Excluir um workspace com projetos é recusado com `409 WORKSPACE_NOT_EMPTY`.
- A pasta de um import é apagada quando ele termina `REJECTED` ou `FAILED`, quando um import mais novo do mesmo projeto conclui e na remoção do projeto; a do import atual é retida como origem do projeto ZIP (D-033). Na inicialização, pastas que não pertencem a um import atual são removidas.

Snapshots, conteúdo e chunks (D-039, D-044), ao fim de cada job de indexação:
1. **Poda:** além do ativo, mantêm-se os `app.indexing.retained-snapshots` (padrão 3) mais recentes `READY`/`PARTIAL`. Os mais antigos perdem os `snapshot_files` e recebem `pruned_at`; a linha do snapshot fica como histórico. Snapshots `FAILED` perdem os `snapshot_files` ao falhar.
2. **Chunks sem snapshot:** apagam-se os chunks cuja combinação `(content_hash, chunking_version)` não é usada por nenhum snapshot não podado (inclusive `BUILDING`).
3. **Conteúdo sem snapshot:** apagam-se os `file_contents` sem referência em `snapshot_files`, com os chunks restantes em cascata.

4. **Embeddings órfãos:** apagam-se os `chunk_embeddings` cujo `chunk_hash` não existe em nenhum chunk, de qualquer projeto. Só jobs concluídos limpam; um job com falha preserva os vetores já gravados para o job seguinte. Uma varredura na inicialização cobre projetos removidos sem job posterior (D-052).

Respostas e evidências nunca são apagadas pela poda (D-046).

Ainda a definir:
- conversas e respostas (fora a remoção do projeto);
- embeddings de um modelo que deixou de ser o configurado (hoje só somem com seus chunks, D-052);
- registros de jobs e snapshots podados antigos.
