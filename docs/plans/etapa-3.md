# Plano — Etapa 3: Snapshots, hashes e chunking

**Status:** em andamento (branch `etapa-3`). Propostas P1 a P7 aprovadas em 16/10/2026, com ajustes, e registradas como D-039 a D-046.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 3), `docs/plans/etapa-2.md` (pendências), `04-RAG-PIPELINE.md` §4 e §11, `05-DATA-MODEL.md`, D-009, D-012, D-016, D-021, D-027, D-031, D-033 a D-036, O-010.

## Escopo

**Dentro:**
- modelo de snapshots, conteúdo de arquivos e chunks (D-039, resolve a O-008);
- `index_jobs` com fila em tabela, heartbeat e limite de tentativas (D-040, resolve a O-009);
- um job ativo por projeto, de qualquer tipo, garantido por índice único parcial (D-041);
- leitura segura do conteúdo, hash SHA-256 por arquivo e classificação do texto (D-042);
- chunking genérico por linhas, com parâmetros provisórios (D-043, O-010 continua aberta);
- linguagem e metadados por arquivo do snapshot;
- reindexação incremental por conteúdo, com reprocessamento dos arquivos que falharam;
- inventário persistido por snapshot, resumo de mudanças e lista de arquivos não indexados (D-044);
- snapshot ativo no projeto e indicação de desatualização;
- poda de snapshots e limpeza de conteúdo e chunks sem snapshot;
- testes de idempotência e de recuperação de falhas.

**Pendências herdadas da Etapa 2 e onde são resolvidas:**

| Pendência | Onde |
|---|---|
| `PROJECT_BUSY` também para jobs de indexação | D-041; F0 e F3 |
| Persistência do inventário (tabela `files`) e decisões O-008 e O-009 | D-039 e D-040; F2, F3 e F4 |
| Uso de `settings_version` para decidir a reindexação | D-044; F6 |
| Recusar a indexação com `PROJECT_SIZE_LIMIT_EXCEEDED` | D-044; F3 |

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Embeddings, `chunk_embeddings` e colunas de provedor em `snapshots` | Etapa 4 |
| Busca vetorial filtrada por snapshot (risco registrado na D-039) | Etapa 4 |
| `identifier_terms`, `lexical_tsv` e índices `pg_trgm` | Etapa 5 (migração própria) |
| Endpoint de conteúdo para o visualizador (`GET …/files/content`) | Etapa 6/7 (o modelo já guarda o conteúdo) |
| `answer_evidence` com cópia da evidência (D-046) | Etapa 6 (a decisão já vale para a poda desta etapa) |
| `chunk_type` e `symbol_name` preenchidos por AST | Etapa 9 (D-027) |
| Pular o hash de arquivos com tamanho e data de modificação iguais | Etapa 8, se a medição justificar |
| Outras codificações além de UTF-8 | Etapa 8, com casos no corpus |
| Cancelamento de job | sem previsão (D-021) |
| Detecção automática de mudanças em pasta local (watcher) | sem previsão |
| Vários processos do backend sobre o mesmo banco | fora do MVP (limitação documentada na D-040) |

## Decisões aprovadas

Todas aprovadas em 16/10/2026. Os ajustes pedidos na aprovação já estão incorporados.

### P1 — Modelo de snapshots, conteúdo e chunks (resolve O-008) → D-039, F2, F4

- **Chunks ligados à versão do conteúdo**, não ao snapshot (simplifica a recomendação original da O-008, sem `snapshot_chunks`):
  - **`file_contents`** `(project_id, content_hash)`: conteúdo decodificado, deduplicado por projeto; atende o visualizador;
  - **`snapshot_files`** `(snapshot_id, relative_path)`: estado de cada arquivo incluído naquele snapshot (hash, tamanho, linguagem, estado e motivo). Substitui a tabela `files`;
  - **`chunks`** por `(project_id, content_hash, chunking_version, ordinal)`: imutáveis. Arquivo inalterado reaproveita os chunks sem escrita; a busca por snapshot é um `JOIN` de `snapshot_files` com `chunks` pelo hash e pela `chunking_version` do snapshot.
- **Deduplicação por projeto**, não global: a remoção do projeto apaga tudo em cascata. O cache global por `chunk_hash` vale para embeddings (Etapa 4).
- **Mesmo conteúdo em caminhos diferentes:** a recuperação devolve o chunk **uma vez** e resolve os caminhos por `snapshot_files`. A citação usa um caminho (o primeiro em ordem lexicográfica, para ser determinística) e lista os demais (`otherPaths`), com as mesmas linhas.
- **Inventário persistido:** o agregado da varredura fica em `snapshots.inventory` (JSONB). Arquivos excluídos não viram linhas. O `GET /projects/{id}/inventory` continua sob demanda (D-035), como pré-análise.
- **Limpeza de conteúdo e chunks sem snapshot**, ao fim de cada job (inclusive os que falham), na mesma transação da poda:
  1. a poda (D-044) apaga as linhas de `snapshot_files` dos snapshots podados e dos `FAILED`;
  2. apagam-se os chunks do projeto cuja combinação `(content_hash, chunking_version)` não é usada por nenhum snapshot não podado (`snapshot_files` `INDEXED` × `snapshots.chunking_version`), o que também remove chunks de versões antigas de chunking;
  3. apagam-se os `file_contents` do projeto sem nenhuma referência em `snapshot_files`, com os chunks restantes em cascata.

  Um job em andamento protege o próprio conteúdo: seu snapshot é `BUILDING`, não podado, e suas linhas de `snapshot_files` são gravadas na mesma transação do conteúdo.
- **Risco registrado para a Etapa 4:** busca vetorial com filtro por snapshot (via `JOIN`) sobre índice HNSW pode devolver menos resultados que o pedido, porque o filtro é aplicado depois da busca aproximada. Avaliar no corpus a varredura iterativa do pgvector (`hnsw.iterative_scan`) ou uma alternativa (índice parcial por projeto, busca exata em projetos pequenos).
- **Custo:** o texto é guardado duas vezes (conteúdo inteiro e chunks com sobreposição), limitado por `max-project-bytes`.

### Ajuste da P1 — Evidências independentes da retenção → D-046, Etapa 6

- `answer_evidence` guarda **cópia** do caminho, das linhas, do hash do conteúdo e do trecho no momento da resposta, além das referências ao snapshot e ao chunk.
- Podar um snapshot, ou limpar seus chunks, **nunca** apaga respostas nem evidências: as referências ficam soltas (sem FK com cascata), e a cópia continua legível.
- Ao abrir uma evidência cujo snapshot foi podado, a API devolve a cópia com `snapshotStatus: "PRUNED"` ("snapshot removido"), em vez de erro.
- Nesta etapa, a decisão só é registrada e refletida no modelo e no contrato; a implementação é da Etapa 6.

### P2 — Fila de jobs de indexação (resolve O-009) → D-040, F3, F5

- `index_jobs` em `PENDING` → `RUNNING` → `COMPLETED` ou `FAILED`.
- **Retirada com `SELECT … FOR UPDATE SKIP LOCKED`** numa transação curta, que marca `RUNNING`, incrementa `attempt_count` e grava `heartbeat_at`. O trabalho roda fora dessa transação, em workers do processo (`app.indexing.max-concurrent`, padrão 1).
- **Heartbeat** a cada lote (`heartbeat-interval`, 10 s). Job `RUNNING` com heartbeat mais antigo que `lease-timeout` (60 s) volta a ser elegível: cobre queda, `shutdownNow` e thread travada, sem código de inicialização.
- **Limite de tentativas:** ao ser retirado com `attempt_count` igual a `max-attempts` (3), o job termina `FAILED` com `INDEX_ATTEMPTS_EXHAUSTED`.
- **Despertar:** sondagem a cada `poll-interval` (2 s) e sinal imediato após o commit que cria o job.
- Transições condicionadas ao estado **e** ao `attempt_count` retirado: um worker cujo lease expirou não sobrescreve quem retomou o job.
- **Limitação:** o MVP assume um processo do backend por banco; dois funcionariam (`SKIP LOCKED` e lease), mas não é cenário testado.
- A D-034 (imports em executor em processo) não muda.

### P3 — Um job ativo por projeto, garantido no banco → D-041, F0, F3

- **Registro de jobs do projeto** (`project_jobs`, do módulo `project`): uma linha por job, de import ou de indexação, com `kind` e `active`. **Índice único parcial `(project_id) WHERE active`**: no máximo um job ativo por projeto, de qualquer tipo, seguro sob concorrência e sem `SELECT` prévio.
- Os IDs de `import_jobs` e `index_jobs` são FK para `project_jobs (id)`, então todo job tem o registro.
- `ProjectService.startJob(projectId, jobId, kind)` insere o registro na transação que cria o job e traduz as violações: índice único → `409 PROJECT_BUSY`; FK do projeto → `404 PROJECT_NOT_FOUND`. `ProjectService.finishJob(jobId)` desativa o registro **na mesma transação** da transição terminal do job.
- `DELETE /projects/{id}` continua travando a linha do projeto e consulta `project_jobs`, sem porta. `ProjectImports.hasActiveImport` sai.
- Os índices únicos parciais próprios de `import_jobs` e `index_jobs` continuam como defesa adicional por tipo.
- **Origem da indexação:** `importjob.scan` expõe a fachada pública `ProjectSources`, que resolve a origem com os mesmos erros do inventário e percorre a árvore com o `SafeScanner`, emitindo cada arquivo **incluído** (caminho relativo, tamanho, linguagem e `Path` interno, que nunca sai do backend).
- **Dependências:** `indexing` → `importjob` (fachada) → `project`. ArchUnit: `project` e `importjob` não dependem de `indexing`; `codeanalysis` não depende de `indexing`.

### P4 — Leitura, classificação e hash → D-042, F1, F4

- Cada arquivo é lido **uma vez**, com `NOFOLLOW_LINKS` e leitura limitada a `maxFileBytes + 1`. Hash e conteúdo saem dos mesmos bytes.
- **Hash:** SHA-256 dos bytes brutos, hexadecimal minúsculo.
- **Estados por arquivo do snapshot:**
  - `INDEXED`: texto válido, com conteúdo e chunks;
  - `UNSUPPORTED`, com motivo: `BINARY` (byte NUL em qualquer posição; a varredura só olha os primeiros 8 KB), `INVALID_ENCODING` (não é UTF-8 válido) e `LINE_TOO_LONG` (D-043);
  - `FAILED`, **só** para erro de leitura (`READ_ERROR`, inclusive arquivo removido) ou arquivo alterado durante a leitura (`CHANGED_DURING_READ`: tamanho ou data de modificação diferentes antes e depois da leitura, ou tamanho acima do limite efetivo).
- **Decodificação:** UTF-8 estrito, com BOM removido.
- **Linhas:** separadas por `\n`, `\r\n` ou `\r`, numeradas a partir de 1. O conteúdo é guardado como decodificado, sem normalizar quebras.
- Conteúdo nunca é logado; logs trazem ids, contagens, códigos e durações.

### P5 — Chunking genérico inicial (O-010, provisória) → D-043, F1

- Janela de até **60 linhas** e **4.000 caracteres**, o que vier primeiro.
- Sobreposição de **10 linhas** entre janelas consecutivas.
- **Corte preferencial em linha em branco** nas últimas 15 linhas da janela.
- **Linha com mais de 4.000 caracteres:** o arquivo inteiro vira `UNSUPPORTED` com `LINE_TOO_LONG` (minificados e dados), sem chunks.
- Arquivo vazio ou só com linhas em branco: `INDEXED` com zero chunks.
- `token_count_estimate` = ⌈caracteres / 4⌉, até existir o tokenizador do provedor (Etapa 4).
- `chunking_version` = `lines-v1`; qualquer mudança de parâmetro ou de algoritmo cria uma versão nova. Os parâmetros ficam no código.
- `chunk_hash` = SHA-256 de `chunking_version` + texto do chunk (chave do cache de embeddings, Etapa 4).

### P6 — Ciclo de vida do snapshot, ativação e poda → D-044, F3 a F6

- **Um snapshot por job.** Estados: `BUILDING` → `READY`, `PARTIAL` ou `FAILED`. Etapa e progresso ficam no job.
- **Etapas do job:** `SCANNING` → `PROCESSING_FILES` → `FINALIZING` (a Etapa 4 acrescenta `EMBEDDING`).
- **Falhas que encerram o job** (snapshot `FAILED`, não ativado): origem indisponível ao rodar (códigos `SOURCE_*`), `SCAN_LIMIT_REACHED` (árvore incompleta faria arquivos parecerem removidos), `PROJECT_SIZE_LIMIT_EXCEEDED`, `NO_INCLUDED_FILES`, `INDEX_IO_ERROR` e `INDEX_ATTEMPTS_EXHAUSTED`.
- **Arquivos `FAILED`** não encerram o job: o snapshot termina `PARTIAL` e **é ativado**. Arquivos `UNSUPPORTED` não são falha: com eles, o snapshot pode ser `READY`.
- **Arquivos não indexados visíveis:** o snapshot traz `failedFiles` e `unsupportedFiles`, e a lista sai de `GET /snapshots/{id}/files?status=FAILED|UNSUPPORTED`, com o motivo de cada um.
- **Ativação** em `FINALIZING`, numa transação: contagens, resumo de mudanças, estado, `projects.active_snapshot_id`, job `COMPLETED` e `finishJob`.
- **Resumo de mudanças** contra o snapshot ativo anterior, sobre os arquivos `INDEXED`: `added`, `modified`, `removed` e `unchanged` por caminho e hash.
- **Desatualização:** o snapshot guarda `settings_version`, `chunking_version` e, em ZIP, `source_import_job_id`; o projeto informa `outdatedReasons` (`SETTINGS_CHANGED`, `SOURCE_REIMPORTED`, `CHUNKING_CHANGED`). Mudanças no disco de pasta local não são detectadas sem nova varredura.
- **Poda:** além do ativo, mantêm-se os `retained-snapshots` (padrão 3) mais recentes `READY`/`PARTIAL`. Os mais antigos são podados (`pruned_at` preenchido; a linha fica como histórico, sem `snapshot_files`), e os `FAILED` perdem os `snapshot_files` ao falhar. Em seguida, a limpeza da D-039. Evidências não são afetadas (D-046).

### P7 — Ajustes de contrato → D-045, F3, F4, F6

- `POST /projects/{projectId}/index-jobs` **sem corpo**: `mode` sai, porque toda indexação é incremental por conteúdo. A resposta é o job completo.
- `POST /index-jobs/{jobId}/retry` sai: jobs interrompidos são retomados automaticamente (D-040), e um job `FAILED` é refeito criando outro.
- **Um novo job reprocessa os arquivos cujo estado anterior era `FAILED`:** todo arquivo é lido de novo a cada job, e um arquivo `FAILED` não tem hash nem conteúdo a reaproveitar. Arquivos `UNSUPPORTED` também são reavaliados, já que podem ter mudado.
- Novos: `GET /projects/{projectId}/index-jobs` (paginado), `GET /projects/{projectId}/snapshots`, `GET /snapshots/{snapshotId}` e `GET /snapshots/{snapshotId}/files`, que substitui `GET /projects/{projectId}/files`.
- Novos `ErrorCode`: `INDEX_JOB_NOT_FOUND` e `SNAPSHOT_NOT_FOUND`. Códigos do job em `index.error.<CODE>` (pt-BR), sem coluna de mensagem, como nos imports.

## Princípios de execução

- Valem os das etapas anteriores: fatias entregáveis, `./mvnw verify` verde ao fim de cada uma, `openapi.json` regenerado e commitado (`-Dopenapi.update=true`, D-023).
- **Nunca executar conteúdo importado** (D-012): arquivos são lidos como bytes e decodificados como texto, nada mais.
- **Não logar** caminhos, nomes nem conteúdo de arquivos. Logs: `indexJobId`, `snapshotId`, `projectId`, contagens, durações e códigos.
- **SQL explícito** (D-016): `JdbcClient` para toda a indexação. JSONB do inventário convertido com o `ObjectMapper` da aplicação.
- **Idempotência por construção:** inserts de conteúdo e chunks com `ON CONFLICT DO NOTHING` sobre a chave natural.
- **Testes de integração:**
  - continuam os quatro contextos da Etapa 2;
  - intervalos curtos de sondagem, heartbeat e lease nos testes;
  - todo teste que cria um job espera o estado terminal com Awaitility, e o `DatabaseCleaner` limpa as tabelas novas;
  - falhas injetadas por uma porta de leitura de arquivos substituível no teste, não por ganchos no código de produção.

## Fatias, na ordem

### F0 — Registro de jobs, varredura por arquivo e fachada de origem (V6)

**Entregas**
- **Migração V6** (abaixo): `project_jobs`, preenchida com os imports existentes, e FK de `import_jobs (id)` para ela.
- **`project`:**
  - enum `JobKind` (`IMPORT`, `INDEX`) e repositório de `project_jobs` por `JdbcClient`;
  - `ProjectService.startJob(projectId, jobId, kind)` e `finishJob(jobId)` (D-041);
  - `DELETE /projects/{id}` consulta `project_jobs`; `ProjectImports.hasActiveImport` é removido.
- **`importjob`:**
  - o upload registra o job com `startJob` antes do insert do import, na mesma transação; a precedência do contrato não muda;
  - toda transição terminal (`COMPLETED`, `REJECTED`, `FAILED`, inclusive `IMPORT_INTERRUPTED` na recuperação) chama `finishJob` na mesma transação.
- **`SafeScanner` emite cada arquivo incluído** para um ouvinte, além de agregar; o inventário não muda.
- **`importjob.scan.ProjectSources`** (pública): `resolve(projectId)` com os erros `SOURCE_*` do inventário (o `InventoryService` passa a usá-la) e `scan(projectId, listener)`, que devolve o agregado, a `settingsVersion` e os limites efetivos usados, e emite os arquivos incluídos (`relativePath`, `sizeBytes`, `language`, `Path`).
- ArchUnit: `project` e `importjob` não dependem de `indexing`; `codeanalysis` não depende de `indexing`.

**Testes**
- Esquema: segundo registro ativo no mesmo projeto viola `project_jobs_active_unique_idx`; import sem registro viola a FK; cascata da remoção do projeto apaga registros e imports.
- Contrato e integração:
  - os testes de upload, import e remoção da Etapa 2 passam (os auxiliares que inserem imports via `JdbcClient` passam a inserir o registro);
  - registro ativo de `INDEX` inserido via `JdbcClient` → upload responde `409 PROJECT_BUSY`, e `DELETE` também;
  - import concluído, rejeitado ou interrompido pela recuperação → registro inativo, e o projeto aceita novo upload;
  - o teste de concorrência do upload continua respondendo `409 PROJECT_BUSY` (a espera passa a ser no índice de `project_jobs`).
- Unitário do `SafeScanner`: a sequência de arquivos incluídos sobre a árvore de teste é a esperada, em ordem determinística, sem nenhum excluído.
- Integração da `ProjectSources`: os arquivos emitidos batem com `includedFiles` e `includedBytes` do agregado; erros `SOURCE_*` iguais aos do inventário.

### F1 — Classificação, hash e chunking (puro, sem banco)

**Entregas** (pacote `indexing.content`, sem Spring):
- `ContentHash`: SHA-256 hexadecimal dos bytes.
- `TextClassifier`: UTF-8 estrito com BOM removido; resultado `TEXT` (texto e linhas), ou `UNSUPPORTED` com `BINARY`, `INVALID_ENCODING` ou `LINE_TOO_LONG`.
- `LineChunker` com os parâmetros da D-043 e `CHUNKING_VERSION = "lines-v1"`; cada chunk traz `ordinal`, `startLine`, `endLine`, `content`, `chunkHash` e `tokenCountEstimate`.

**Testes** (unitários, tabela de casos):
- `TextClassifier`: UTF-8 válido, com BOM, inválido, NUL depois de 8 KB, `\r\n`, `\r` isolado, sem quebra final, vazio, linha com exatamente 4.000 e com 4.001 caracteres.
- `LineChunker`, como propriedades sobre vários textos gerados:
  - toda linha não vazia está em pelo menos um chunk;
  - `startLine ≤ endLine`, dentro de `1..lineCount`, e o texto do chunk é exatamente o das linhas indicadas;
  - nenhum chunk excede 60 linhas nem 4.000 caracteres;
  - sobreposição de no máximo 10 linhas entre consecutivos;
  - mesma entrada → mesmos chunks e hashes.
- Casos pontuais: uma linha; corte preferencial em linha em branco; só linhas em branco (zero chunks).
- `chunkHash` muda com a versão ou o texto, e não depende do caminho.

### F2 — Modelo de snapshots, conteúdo e chunks (V7)

**Entregas**
- Migração V7 (abaixo).
- Repositórios em `indexing` por `JdbcClient`, com inserts em lote.

**Testes** (integração do esquema, SQL direto):
- `CHECK`s de estado de `index_jobs`, `snapshots` e `snapshot_files` (`INDEXED` exige hash e não tem motivo; `UNSUPPORTED` e `FAILED` exigem motivo e não têm hash);
- `active_snapshot_id` de outro projeto viola a FK composta;
- `snapshot_files` com hash inexistente em `file_contents` viola a FK;
- chunk repetido na chave natural é ignorado com `ON CONFLICT DO NOTHING`;
- índice job sem registro em `project_jobs` viola a FK;
- **cascata:** remover um projeto com snapshot ativo, arquivos, conteúdos, chunks e jobs apaga tudo sem erro (referência circular `projects` ↔ `snapshots`; FKs `NO ACTION` entre tabelas apagadas na mesma instrução).

### F3 — Fila de jobs e etapa de varredura

**Entregas**
- **`POST /projects/{projectId}/index-jobs`** (sem corpo). Na ordem:
  1. `404 PROJECT_NOT_FOUND`;
  2. `409 SOURCE_ROOT_NOT_CONFIGURED`, `SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE` ou `SOURCE_NOT_IMPORTED` (`ProjectSources.resolve`, sem varrer);
  3. `409 PROJECT_BUSY` (`startJob`);
  4. insert do snapshot (`BUILDING`) e do job (`PENDING`) na mesma transação;
  5. após o commit, sinal aos workers.

  Resposta: `202 Accepted` com o job e `Location`.
- **`GET /index-jobs/{jobId}`:** `{ id, projectId, snapshotId, status, stage, progress: { total, completed }, attempts, error: { code, message } | null, createdAt, startedAt, completedAt }`; `404 INDEX_JOB_NOT_FOUND`.
- **`GET /projects/{projectId}/index-jobs`:** paginado (D-030), do mais novo para o mais antigo.
- **Fila e workers (D-040):** retirada com `SKIP LOCKED`, heartbeat, lease, limite de tentativas, sinal após commit; pool encerrado com `shutdownNow` no `@PreDestroy`.
- **Etapa `SCANNING`:** varre pela fachada, grava `snapshots.inventory`, `settings_version` e limites, e aplica as falhas da D-044. Nesta fatia, o job termina depois da varredura sem ativar o snapshot (provisório; a F4 completa o pipeline).
- Mensagens `index.error.<CODE>` e `error.INDEX_JOB_NOT_FOUND.*`.

**Testes**
- Contrato: criação → 202 com `Location`; estados até o terminal; `GET` e listagem paginada; `INDEX_JOB_NOT_FOUND`; precedência 404 → `SOURCE_*` → `PROJECT_BUSY`, um caso por fronteira.
- Um job por projeto, nos quatro sentidos: indexação com import ativo, upload com indexação ativa, segunda indexação, `DELETE` com indexação ativa → `409 PROJECT_BUSY`.
- Falhas da varredura: `SCAN_LIMIT_REACHED`, `PROJECT_SIZE_LIMIT_EXCEEDED`, `NO_INCLUDED_FILES` e pasta removida entre o `POST` e a execução (`SOURCE_DIRECTORY_NOT_FOUND`); em todas, o registro do job fica inativo.
- Fila: dois jobs de projetos diferentes com `max-concurrent = 2` são processados uma vez cada.
- Todo código de erro do job tem mensagem.

### F4 — Arquivos, conteúdo, chunks, ativação e poda

**Entregas**
- **Etapa `PROCESSING_FILES`**, em lotes de `batch-files` (100) por transação:
  1. lê cada arquivo pela porta de leitura (D-042), calcula hash e classifica;
  2. `INDEXED` com hash novo no projeto: insere `file_contents` e chunks (`ON CONFLICT DO NOTHING`);
  3. insere a linha de `snapshot_files` com estado e motivo;
  4. atualiza progresso e heartbeat.
- **Etapa `FINALIZING`** (D-044): contagens, resumo de mudanças, estado, ativação, job `COMPLETED` e `finishJob` numa transação; depois, poda e limpeza (D-039).
- **`GET /projects/{projectId}/snapshots`** (paginado) e **`GET /snapshots/{snapshotId}`:** `{ id, projectId, status, active, settingsVersion, chunkingVersion, sourceImportId, counts: { files, indexedFiles, unsupportedFiles, failedFiles, chunks, bytes }, changes: { added, modified, removed, unchanged } | null, inventory, createdAt, completedAt, prunedAt }`; `404 SNAPSHOT_NOT_FOUND`.
- **`GET /snapshots/{snapshotId}/files?status=&pathPrefix=`:** paginado, por `relativePath`; itens `{ relativePath, status, reason, language, sizeBytes, lineCount, contentHash, chunkCount }`. Snapshot podado responde lista vazia, e o snapshot traz `prunedAt`.

**Testes (aceite da etapa)**

Árvore `LOCAL_FOLDER` criada pelo teste, indexada, alterada e reindexada:

| Mudança entre as indexações | Esperado no segundo snapshot |
|---|---|
| arquivo novo | `added`; novo `file_contents` e chunks |
| arquivo alterado | `modified`; hash novo; chunks antigos continuam ligados ao snapshot anterior |
| arquivo removido | `removed`; ausente de `snapshot_files` |
| arquivo passa a ser excluído (`PUT /settings`) | `removed`; `settingsVersion` novo no snapshot |
| arquivo excluído deixa de ser (regra desativada) | `added` |
| arquivo inalterado | `unchanged`; **nenhum** insert em `file_contents` ou `chunks` |
| arquivo movido, mesmo conteúdo | `removed` + `added`, sem novos chunks |
| dois arquivos com o mesmo conteúdo | um `file_contents`, dois `snapshot_files` |
| arquivo `FAILED` no primeiro job, legível no segundo | `INDEXED` e `added` (reprocessado, D-045) |

Além disso:
- o ativo muda só quando o segundo job conclui;
- UTF-8 inválido, NUL depois de 8 KB e linha de 4.001 caracteres → `UNSUPPORTED` (`INVALID_ENCODING`, `BINARY`, `LINE_TOO_LONG`), snapshot `READY`, listados por `status=UNSUPPORTED`;
- arquivo removido entre a varredura e a leitura, e arquivo alterado durante a leitura (porta de leitura do teste) → `FAILED` (`READ_ERROR`, `CHANGED_DURING_READ`), snapshot `PARTIAL` e ativado, listados por `status=FAILED`;
- ZIP: indexar, novo upload, reindexar → mesmas regras, e `sourceImportId` acompanha o import atual;
- chunks batem com o arquivo: para uma amostra, as linhas `startLine..endLine` do `file_contents` são exatamente o `content` do chunk;
- poda com `retained-snapshots = 1`: o terceiro job poda o primeiro; conteúdo e chunks usados só por ele somem; os compartilhados ficam;
- nenhuma resposta contém caminho absoluto; o log capturado não contém caminhos relativos da árvore.

### F5 — Recuperação e idempotência

**Entregas**
- Retomada: ao retirar um job com `attempt_count > 1`, o worker apaga os `snapshot_files` daquele snapshot e recomeça pela varredura; conteúdo e chunks já gravados são reaproveitados pela deduplicação.
- `INDEX_ATTEMPTS_EXHAUSTED` ao atingir `max-attempts`, com `finishJob`.

**Testes**
- **Queda no meio:** a porta de leitura do teste lança erro não tratado depois de N arquivos; o heartbeat é envelhecido via `JdbcClient`; o job é retomado e termina `COMPLETED` com resultado **idêntico** ao de uma execução sem falha (`snapshot_files`, contagens de `file_contents` e `chunks`, `changes`).
- **Reexecução do mesmo lote:** nenhuma contagem muda.
- **Worker atrasado:** tenta finalizar depois da retomada → o `UPDATE` não afeta linhas.
- **Tentativas esgotadas:** `FAILED INDEX_ATTEMPTS_EXHAUSTED`, snapshot `FAILED`, ativo anterior mantido, projeto livre.
- **Queda real simulada:** job `RUNNING` com heartbeat antigo inserido via `JdbcClient` → retomado e concluído.

### F6 — Estado de indexação no projeto

**Entregas**
- `GET /projects/{projectId}` e a listagem ganham, sem N+1 (consulta em lote por página):
  - `activeSnapshot`: `{ id, status, completedAt, counts }` ou `null`;
  - `indexing`: o job ativo `{ id, status, stage, progress }` ou `null`;
  - `outdatedReasons` (D-044), vazia quando em dia ou sem snapshot.
- Os dados chegam a `project` por uma porta (`ProjectIndexes`) implementada por `indexing` (D-031).

**Testes**
- Sem indexação: `activeSnapshot = null`, `indexing = null`, `outdatedReasons = []`.
- Durante: `indexing` com etapa e progresso.
- Depois: `activeSnapshot` preenchido; `PUT /settings` → `SETTINGS_CHANGED`; novo upload concluído → `SOURCE_REIMPORTED`; reindexar → lista vazia.
- `CHUNKING_CHANGED` com snapshot inserido via `JdbcClient` com outra versão.

### F7 — Fechamento

- Aceite: arquivos novos, alterados e removidos refletidos corretamente (F4), com idempotência e recuperação demonstradas (F5).
- `openapi.json` final commitado.
- Valores da O-010 confirmados ou ajustados em `10-DECISIONS.md`.
- `04-RAG-PIPELINE.md` (§4 e §11), `02-ARCHITECTURE.md` (processamento assíncrono), `07-SECURITY-PRIVACY-COSTS.md` (leitura e armazenamento de conteúdo), `09-IMPLEMENTATION-ROADMAP.md` (status); `05` e `06` revisados contra o implementado.
- README: `app.indexing.*` e onde fica o conteúdo indexado.
- Seção "Resultado e desvios" neste arquivo.

## Migrações

### V6__create_project_jobs.sql

```sql
CREATE TABLE project_jobs (
    id         uuid        PRIMARY KEY,
    project_id uuid        NOT NULL
                           CONSTRAINT project_jobs_project_id_fkey
                           REFERENCES projects (id) ON DELETE CASCADE,
    kind       text        NOT NULL
                           CONSTRAINT project_jobs_kind_valid
                           CHECK (kind IN ('IMPORT', 'INDEX')),
    active     boolean     NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX project_jobs_active_unique_idx
    ON project_jobs (project_id)
    WHERE active;

INSERT INTO project_jobs (id, project_id, kind, active, created_at)
SELECT id, project_id, 'IMPORT', status IN ('PENDING', 'EXTRACTING'), created_at
FROM import_jobs;

ALTER TABLE import_jobs
    ADD CONSTRAINT import_jobs_project_job_fkey
        FOREIGN KEY (id) REFERENCES project_jobs (id);
```

- O preenchimento não viola o índice: `import_jobs_active_unique_idx` já garante no máximo um import ativo por projeto.
- `import_jobs_project_job_fkey` é `NO ACTION`: a cascata da remoção do projeto apaga as duas tabelas na mesma instrução.
- Nomes traduzidos: `project_jobs_active_unique_idx` → `PROJECT_BUSY`; `project_jobs_project_id_fkey` → `PROJECT_NOT_FOUND`.

### V7__create_snapshots_and_index_jobs.sql

Esboço; os nomes seguem o padrão das migrações anteriores.

```sql
CREATE TABLE snapshots (
    id                     uuid        PRIMARY KEY,
    project_id             uuid        NOT NULL
                                       CONSTRAINT snapshots_project_id_fkey
                                       REFERENCES projects (id) ON DELETE CASCADE,
    status                 text        NOT NULL
                                       CONSTRAINT snapshots_status_valid
                                       CHECK (status IN ('BUILDING', 'READY', 'PARTIAL', 'FAILED')),
    source_import_job_id   uuid,
    settings_version       integer     NOT NULL,
    chunking_version       text        NOT NULL,
    inventory              jsonb,
    file_count             integer,
    indexed_file_count     integer,
    unsupported_file_count integer,
    failed_file_count      integer,
    chunk_count            integer,
    total_bytes            bigint,
    added_count            integer,
    modified_count         integer,
    removed_count          integer,
    unchanged_count        integer,
    created_at             timestamptz NOT NULL,
    completed_at           timestamptz,
    pruned_at              timestamptz,
    CONSTRAINT snapshots_project_id_id_key UNIQUE (project_id, id),
    CONSTRAINT snapshots_source_import_fkey
        FOREIGN KEY (project_id, source_import_job_id)
        REFERENCES import_jobs (project_id, id),
    CONSTRAINT snapshots_state_consistency CHECK (
        (status = 'BUILDING' AND completed_at IS NULL AND pruned_at IS NULL)
     OR (status IN ('READY', 'PARTIAL') AND completed_at IS NOT NULL
            AND file_count IS NOT NULL AND chunk_count IS NOT NULL)
     OR (status = 'FAILED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX snapshots_project_created_at_id_idx
    ON snapshots (project_id, created_at DESC, id DESC);

CREATE TABLE index_jobs (
    id                 uuid        PRIMARY KEY
                                   CONSTRAINT index_jobs_project_job_fkey
                                   REFERENCES project_jobs (id),
    project_id         uuid        NOT NULL
                                   CONSTRAINT index_jobs_project_id_fkey
                                   REFERENCES projects (id) ON DELETE CASCADE,
    snapshot_id        uuid        NOT NULL
                                   CONSTRAINT index_jobs_snapshot_id_key UNIQUE,
    status             text        NOT NULL
                                   CONSTRAINT index_jobs_status_valid
                                   CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    stage              text
                                   CONSTRAINT index_jobs_stage_valid
                                   CHECK (stage IN ('SCANNING', 'PROCESSING_FILES', 'FINALIZING')),
    progress_total     integer,
    progress_completed integer,
    attempt_count      integer     NOT NULL DEFAULT 0,
    heartbeat_at       timestamptz,
    error_code         text,
    created_at         timestamptz NOT NULL,
    started_at         timestamptz,
    completed_at       timestamptz,
    CONSTRAINT index_jobs_snapshot_fkey
        FOREIGN KEY (project_id, snapshot_id)
        REFERENCES snapshots (project_id, id) ON DELETE CASCADE,
    CONSTRAINT index_jobs_state_consistency CHECK (
        (status = 'PENDING' AND completed_at IS NULL AND error_code IS NULL)
     OR (status = 'RUNNING' AND completed_at IS NULL AND error_code IS NULL
            AND heartbeat_at IS NOT NULL)
     OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND error_code IS NULL)
     OR (status = 'FAILED' AND completed_at IS NOT NULL AND error_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX index_jobs_active_unique_idx
    ON index_jobs (project_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX index_jobs_claim_idx
    ON index_jobs (created_at)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX index_jobs_project_created_at_id_idx
    ON index_jobs (project_id, created_at DESC, id DESC);

CREATE TABLE file_contents (
    project_id   uuid        NOT NULL
                             CONSTRAINT file_contents_project_id_fkey
                             REFERENCES projects (id) ON DELETE CASCADE,
    content_hash text        NOT NULL,
    content      text        NOT NULL,
    size_bytes   bigint      NOT NULL,
    line_count   integer     NOT NULL,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (project_id, content_hash)
);

CREATE TABLE snapshot_files (
    snapshot_id   uuid   NOT NULL
                         CONSTRAINT snapshot_files_snapshot_id_fkey
                         REFERENCES snapshots (id) ON DELETE CASCADE,
    project_id    uuid   NOT NULL,
    relative_path text   NOT NULL,
    status        text   NOT NULL
                         CONSTRAINT snapshot_files_status_valid
                         CHECK (status IN ('INDEXED', 'UNSUPPORTED', 'FAILED')),
    reason        text,
    content_hash  text,
    language      text   NOT NULL,
    size_bytes    bigint NOT NULL,
    PRIMARY KEY (snapshot_id, relative_path),
    CONSTRAINT snapshot_files_content_fkey
        FOREIGN KEY (project_id, content_hash)
        REFERENCES file_contents (project_id, content_hash),
    CONSTRAINT snapshot_files_state_consistency CHECK (
        (status = 'INDEXED' AND content_hash IS NOT NULL AND reason IS NULL)
     OR (status = 'UNSUPPORTED'
            AND reason IS NOT NULL
            AND reason IN ('BINARY', 'INVALID_ENCODING', 'LINE_TOO_LONG')
            AND content_hash IS NULL)
     OR (status = 'FAILED'
            AND reason IS NOT NULL
            AND reason IN ('READ_ERROR', 'CHANGED_DURING_READ')
            AND content_hash IS NULL)
    )
);

CREATE INDEX snapshot_files_content_idx
    ON snapshot_files (project_id, content_hash);

CREATE TABLE chunks (
    id                   uuid    PRIMARY KEY,
    project_id           uuid    NOT NULL,
    content_hash         text    NOT NULL,
    chunking_version     text    NOT NULL,
    ordinal              integer NOT NULL,
    start_line           integer NOT NULL,
    end_line             integer NOT NULL,
    content              text    NOT NULL,
    chunk_hash           text    NOT NULL,
    token_count_estimate integer NOT NULL,
    CONSTRAINT chunks_natural_key
        UNIQUE (project_id, content_hash, chunking_version, ordinal),
    CONSTRAINT chunks_content_fkey
        FOREIGN KEY (project_id, content_hash)
        REFERENCES file_contents (project_id, content_hash) ON DELETE CASCADE,
    CONSTRAINT chunks_lines_valid CHECK (start_line >= 1 AND end_line >= start_line)
);

CREATE INDEX chunks_chunk_hash_idx ON chunks (chunk_hash);

ALTER TABLE projects
    ADD COLUMN active_snapshot_id uuid,
    ADD CONSTRAINT projects_active_snapshot_fkey
        FOREIGN KEY (id, active_snapshot_id)
        REFERENCES snapshots (project_id, id)
        ON DELETE SET NULL (active_snapshot_id);
```

- FK composta em `active_snapshot_id`, como em `current_import_job_id`: o ativo pertence ao projeto, e `SET NULL (coluna)` mantém a cascata.
- `snapshot_files_content_fkey` sem `ON DELETE` (`NO ACTION`, verificada no fim da instrução): impede apagar conteúdo referenciado na limpeza, sem bloquear a cascata da remoção do projeto. Coberto por teste na F2.
- `active` do snapshot é derivado de `projects.active_snapshot_id`.
- Nomes traduzidos: `index_jobs_project_id_fkey` e `snapshots_project_id_fkey` → `PROJECT_NOT_FOUND`. O `PROJECT_BUSY` vem de `project_jobs` (V6).
- Ficam para migrações próprias: colunas lexicais e `pg_trgm` (Etapa 5); `chunk_embeddings` e colunas de provedor (Etapa 4); `answer_evidence` (Etapa 6, D-046).

## Configuração (`app.indexing.*`, hipóteses a medir na Etapa 8)

| Propriedade | Padrão | Uso |
|---|---|---|
| `max-concurrent` | 1 | workers de indexação |
| `poll-interval` | 2 s | sondagem da fila |
| `heartbeat-interval` | 10 s | atualização do lease |
| `lease-timeout` | 60 s | quando um job `RUNNING` volta à fila |
| `max-attempts` | 3 | tentativas por job |
| `batch-files` | 100 | arquivos por transação |
| `retained-snapshots` | 3 | snapshots anteriores mantidos com arquivos |

Padrões em `IndexingProperties`, sem entradas no `application.yml`, como `ImportProperties` e `InventoryProperties`.

## Riscos

| Risco | Mitigação |
|---|---|
| Pasta local muda durante a indexação. | Hash e conteúdo dos mesmos bytes; atributos comparados antes e depois da leitura (`CHANGED_DURING_READ`); snapshot é "melhor esforço" no tempo, documentado. |
| Import ZIP troca a origem durante a indexação. | Um job ativo por projeto, garantido por `project_jobs_active_unique_idx` (D-041). |
| Registro em `project_jobs` fica ativo depois do fim do job e bloqueia o projeto. | `finishJob` na mesma transação de toda transição terminal; testes por transição (F0, F3, F5). |
| Varredura truncada faz arquivos parecerem removidos. | `SCAN_LIMIT_REACHED` encerra o job sem ativar o snapshot. |
| Retomada duplica dados ou um worker atrasado sobrescreve outro. | `ON CONFLICT DO NOTHING`; transições condicionadas ao `attempt_count`; testes da F5. |
| Lease curto retoma um job ainda vivo. | Heartbeat por lote e lease 6× maior que o intervalo; o worker atrasado não consegue finalizar. |
| Limpeza apaga conteúdo de um job em andamento. | A limpeza só considera snapshots não podados, inclusive `BUILDING`; `snapshot_files` e conteúdo gravados na mesma transação; FK `NO ACTION` como rede. |
| Volume no banco: texto em conteúdo e chunks, linhas por reindexação. | Limites de projeto; deduplicação por hash; poda (D-044); medir na Etapa 8. |
| **Etapa 4:** busca vetorial filtrada por snapshot (`JOIN`) em índice HNSW devolve menos resultados que o pedido. | Avaliar `hnsw.iterative_scan` do pgvector ou alternativa no corpus (D-039). |
| Arquivos em Latin-1 ou outras codificações ficam fora do índice. | `UNSUPPORTED INVALID_ENCODING` listado por arquivo; casos no corpus da Etapa 8. |
| Arquivos com linhas longas legítimas (SQL, dados) ficam fora. | `LINE_TOO_LONG` listado; revisar com a avaliação (O-010). |
| Parâmetros de chunking inadequados. | Valores provisórios (O-010); `chunking_version` permite trocar sem migração. |
| Testes assíncronos instáveis. | Intervalos curtos nos testes; Awaitility até o terminal; projetos próprios por teste; `DatabaseCleaner` atualizado. |
| `JOIN` de `snapshot_files` com `chunks` caro na busca. | Índice `snapshot_files_content_idx` e chave natural de `chunks`; `EXPLAIN` sobre o corpus nas Etapas 4 e 5. |

## Resultado e desvios

A preencher no fechamento (F7). Desvios registrados durante as fatias:

**F0**
- `ProjectService.finishJobs(Collection<UUID>)` em vez de `finishJob(UUID)`: a recuperação de imports encerra vários jobs numa transação.
- A fachada pública `ProjectSources.scan(projectId)` devolve a lista de arquivos incluídos já ordenada por `relativePath`, em vez de expor um ouvinte; o ouvinte fica interno ao pacote (`SafeScanner.Listener`) e o inventário não coleta arquivos.
- `ScanResult` passou a ser público, para a indexação persistir o agregado (F3).
- O preenchimento de `project_jobs` com os imports existentes, na V6, não tem teste automatizado: exigiria dados anteriores à migração.

**F1**
- O texto do chunk junta as linhas com `\n`, qualquer que seja a quebra original; `file_contents` guarda o texto como decodificado (D-042).
- Comprimento de linha e de chunk contado em unidades UTF-16 (`String.length()`), nos dois limites.
- Detalhes do `lines-v1` fixados na implementação: chunks nunca começam nem terminam em linha em branco; depois de um corte em linha em branco não há sobreposição (a fronteira já é natural); depois de um corte pelo limite, a sobreposição é de até 10 linhas, mas o chunk seguinte começa pelo menos na metade do anterior, para não gerar chunks redundantes quando o corte é por caracteres.
- As propriedades do `LineChunker` são verificadas sobre 200 textos gerados com sementes fixas.

**F2**
- Os testes de esquema mostraram que `reason IN (…)` com `reason` nulo passa no `CHECK` (o resultado é `NULL`); a V7 exige `reason IS NOT NULL` em `UNSUPPORTED` e `FAILED`.
- A F2 entrega só o `IndexStore` (conteúdo, chunks e arquivos do snapshot); os repositórios de jobs e snapshots ficam para a F3, que os usa.
- `IndexStore.chunkedHashes` decide o que já está pronto: conteúdo com chunks da versão atual, ou conteúdo em branco (que não tem chunks). O teste de branco no SQL usa um conjunto de espaços contido no do `String.isBlank()`, então uma divergência só causa reprocessamento, nunca chunks faltando.

**F3**
- Worker: um despachante (`SmartLifecycle`, sobe depois do contexto) retira jobs e os entrega a um pool fixo de `max-concurrent` threads; o sinal após o commit acorda o despachante.
- Heartbeat: além de cada lote, é renovado durante a varredura (a cada arquivo incluído, no máximo uma vez por `heartbeat-interval`), para que árvores grandes não percam o lease. `ProjectSources` ganhou `scan(projectId, Runnable)` e `settingsVersion(projectId)`.
- Exceção inesperada no pipeline: o job volta para `PENDING` na hora (a tentativa já foi contada), sem esperar o lease; na última tentativa, termina `FAILED INDEX_IO_ERROR`. `INDEX_ATTEMPTS_EXHAUSTED` fica para jobs cujo worker morreu em todas as tentativas (retirados de novo com o lease vencido).
- `settings_version` é gravada na criação do snapshot e atualizada na varredura, que é a que vale.
- Provisório da F3 (substituído na F4): sem a etapa de arquivos, o job conclui com o snapshot `READY`, contagens zeradas e sem ativação.
- `SCAN_LIMIT_REACHED` é coberto por teste unitário de `IndexRunner.check`: o limite de entradas é global e baixá-lo exigiria outro contexto de teste.
- Os executores do worker são criados a cada `start()`: o Spring Test pausa contextos em cache (chama `stop()`) e os retoma depois.
- Correção fora do escopo: `Directories.deleteRecursively` passou a ignorar entradas removidas durante a remoção. Havia uma corrida no `ImportApiIT` entre a remoção da pasta de um import, depois do commit, e a limpeza do teste seguinte; a nova configuração de testes a tornou frequente.

**F4**
- O record `Project` ganhou `activeSnapshotId`, e `ProjectService.activateSnapshot` troca o ativo e devolve o anterior, usado no resumo de mudanças; tudo na transação de finalização, junto com a poda e a limpeza.
- `SourceFileReader` só abre arquivos regulares (sem seguir links) e compara tamanho, data de modificação e `fileKey` antes e depois da leitura; diferença, ou tamanho acima do limite, vira `CHANGED_DURING_READ`.
- Lotes limitados também por 16 MB de bytes lidos, além de `batch-files`.
- Conteúdo já com chunks da versão atual não é decodificado de novo; arquivos `UNSUPPORTED` não têm conteúdo guardado e são reclassificados a cada job.
- `inventory` do snapshot usa o schema `ScanResult` no OpenAPI (os mesmos campos de `GET /inventory`) e vem `null` nas listagens.
- Falhas de leitura e poda são testadas num contexto próprio (`IndexingEdgeCasesIT`): precisam de um spy no leitor e de `retained-snapshots = 1`.
- "Nenhum insert para arquivo inalterado" é verificado pelas contagens de `file_contents` e pelo id do chunk, que não muda.

**F5**
- O código de recuperação entrou na F3 e na F4 (retomada que apaga os `snapshot_files` parciais, tentativas, transições condicionadas); a F5 entrega os testes.
- A queda no meio é simulada por uma exceção não tratada do leitor no quinto arquivo, com lotes de 2: o job volta para a fila na hora (D-040, desvio da F3), e não por heartbeat envelhecido. O worker morto é coberto à parte, com um job `RUNNING` de heartbeat antigo.
- A reexecução do mesmo lote é coberta pelo `IndexStoreIT` (inserts repetidos não mudam as contagens).
- Os testes que precisam do spy no leitor herdam de `SpiedReaderContext`, para dividir um único contexto extra: com o `@MockitoSpyBean` declarado em cada classe, o Spring criava um contexto por classe.

**F6**
- A porta `ProjectIndexes` recebe os próprios `Project` da página (precisa de `activeSnapshotId` e `currentImportJobId`) e devolve o estado em três consultas em lote: snapshots ativos, jobs ativos e versões das configurações.
- A versão das configurações vem de `ProjectSettingsVersions` (novo, em `importjob.scan`), que só depende do repositório: usar `ProjectSources` criaria um ciclo `ProjectService` → `ProjectIndexes` → `ProjectSources` → `ProjectService`.
- `POST /workspaces/{id}/projects` também devolve os campos novos, com `activeSnapshot` e `indexing` nulos e `outdatedReasons` vazia.
