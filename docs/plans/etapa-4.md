# Plano — Etapa 4: Embeddings e busca vetorial

**Status:** aprovado em 16/10/2026, com ajustes. As propostas P1 a P8 viram D-047 a D-054, registradas em `10-DECISIONS.md` na fatia indicada em cada uma.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 4), `docs/plans/etapa-3.md` (pendências), `04-RAG-PIPELINE.md` §5 e §6, `05-DATA-MODEL.md` (`chunk_embeddings`), `07-SECURITY-PRIVACY-COSTS.md` §7 e §9, D-006, D-007, D-010, D-014, D-019, D-031, D-039, D-040, D-044, O-004.

## Escopo

**Dentro:**
- porta interna de embeddings no módulo `providers`, sem tipos do Spring AI fora dele (D-006, D-010);
- adaptador do provedor inicial, via Spring AI 2.0.x, e provedor fake determinístico para os testes (D-019);
- tabela `chunk_embeddings` no pgvector, com cache por `chunk_hash` + modelo;
- etapa `EMBEDDING` no job de indexação, entre os arquivos e a finalização;
- tratamento de falhas do provedor (credencial, limite de taxa, indisponibilidade);
- busca vetorial restrita a projeto e snapshot, exposta por um endpoint de busca;
- metadados do modelo no snapshot e desatualização quando o modelo muda;
- limpeza de embeddings órfãos.

**Pendências herdadas da Etapa 3 e onde são resolvidas:**

| Pendência | Onde |
|---|---|
| Etapa `EMBEDDING` antes da finalização | P4; F3 |
| Colunas de provedor e modelo em `snapshots` | P2; F2 |
| Tokenizador real no lugar da estimativa | P8 (não entra: uso reportado pelo provedor basta nesta etapa) |
| Busca vetorial filtrada por snapshot sobre HNSW (risco da D-039) | P3; F4 |
| Embeddings órfãos na limpeza | P6; F3 |

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Busca lexical, fusão e corpus de avaliação | Etapa 5 |
| Bake-off de provedores e modelos (O-004 definitiva) | Etapa 5, com o corpus |
| Índice HNSW e varredura iterativa, se a medição justificar | Etapa 5 (comparação) ou Etapa 8 (desempenho) |
| Porta de geração (chat) e montagem de contexto | Etapa 6 |
| Aviso de envio a provedor remoto na UI | Etapa 7 |
| Provedor local de modelos (Ollama ou similar) | Etapa 9 (D-019), já preparado pela porta |
| Tokenizador exato por modelo | sem previsão, enquanto os chunks ficarem muito abaixo do limite do modelo |

## Decisões aprovadas

Todas aprovadas em 16/10/2026. Os ajustes pedidos na aprovação já estão incorporados.

### P1 — Provedor inicial e integração (O-004 provisória) → D-047, F0 e F1

**Problema:** a O-004 pede um provedor de embeddings antes de concluir a Etapa 4, mas o bake-off só é possível com o corpus da Etapa 5. A escolha exige chave de API e tem custo, então é sua.

**Opções:**
1. **OpenAI `text-embedding-3-small`**, pelo módulo OpenAI do Spring AI. Integração mais madura no Spring AI; a mesma API "compatível com OpenAI" é oferecida por outros provedores e por servidores locais, o que facilita a Etapa 9.
2. **Voyage `voyage-code-3`**, especializado em código. Precisa confirmar na F0 se o Spring AI 2.0.x tem integração; sem ela, seria um adaptador HTTP próprio (o Spring AI ficaria sem uso nesta etapa).
3. Adiar a escolha e entregar a etapa só com o provedor fake.

**Decisão:** opção 1 como provisória, com a Voyage como candidata no bake-off da Etapa 5.
- **Sem chave nesta etapa:** F0 a F5 funcionam só com o provedor fake e com o servidor HTTP de teste. A chave real só entra no teste manual de fumaça da F6.
- Configuração: `app.embeddings.provider` (`none`, `fake`, `openai`), `app.embeddings.model`, `app.embeddings.dimensions` e `app.embeddings.base-url`; a chave vem de `EMBEDDING_API_KEY` no `.env`, nunca do código nem dos logs.
- **`base-url` decide o destino dos dados:** padrão no endereço oficial; `https` obrigatório; `http` só para `localhost` ou `127.0.0.1` (servidor de teste e servidores locais futuros); qualquer outro endereço impede a inicialização. O README avisa que mudar o endereço muda o destino dos dados (`07` §7).
- Na F0, confirmar no site do Spring e no start.spring.io a versão do Spring AI 2.0.x compatível com o Spring Boot em uso, o nome do módulo OpenAI e a versão da extensão pgvector da imagem; registrar na D-014.
- Na F0, preço por token e política de retenção de dados do provedor são conferidos em fonte oficial (páginas de preços e de uso de dados da OpenAI), com a data da consulta, e registrados na D-047. Nenhum valor de memória.

### P2 — Armazenamento dos vetores (revisa a restrição da O-004) → D-048, F2

**Problema:** a O-004 diz que a dimensão do vetor é fixada por migração. Isso obriga uma migração a cada troca de modelo, inclusive no bake-off.

**Opções:**
1. Coluna `vector(N)` com a dimensão do modelo escolhido; trocar de modelo exige migração.
2. **Coluna `vector` sem dimensão fixa**, com `dimensions` na própria linha e `CHECK (vector_dims(embedding) = dimensions)`. Índices aproximados, quando vierem, são parciais por modelo, sobre `embedding::vector(N)`.

**Decisão:** opção 2.
- `chunk_embeddings`: PK `(chunk_hash, provider, model, dimensions)`, como já previsto em `05-DATA-MODEL.md`; global, sem `project_id` (o mesmo texto com a mesma versão de chunking reaproveita o embedding em qualquer projeto).
- Vetores de modelos diferentes nunca se misturam: toda consulta filtra por provedor, modelo e dimensão.
- `snapshots` ganha `embedding_provider`, `embedding_model`, `embedding_dimensions` (nulos quando o provedor é `none`) e `embedding_tokens` (uso reportado pelo provedor no job).
- Limite de 2.000 dimensões mantido como validação na inicialização, para não fechar a porta ao HNSW sobre `vector`.

### P3 — Estratégia da busca vetorial (trata o risco da D-039) → D-049, F4

**Problema:** com HNSW, o filtro por snapshot é aplicado depois da busca aproximada e pode devolver menos resultados que o pedido.

**Opções:**
1. HNSW com varredura iterativa do pgvector (`hnsw.iterative_scan`).
2. **Busca exata** (distância de cosseno sobre os chunks do snapshot, sem índice aproximado).
3. Índice HNSW parcial por projeto.

**Decisão:** opção 2 como baseline nesta etapa.
- Correta por construção: só considera os chunks do snapshot pedido, então o aceite ("chunks do snapshot correto") não depende de ajuste de índice.
- Custo proporcional ao tamanho do snapshot (limitado por `max-project-bytes`), aceitável para uso local.
- **Medição manual:** um teste com 50 mil chunks sintéticos, com tag JUnit própria (`performance`) e fora do `./mvnw verify`, mede a latência da busca exata. O comando fica no README e o resultado (máquina, latência média e p95) é registrado em "Resultado e desvios" neste plano.
- A opção 1 fica para a Etapa 5 ou 8, comparada contra a busca exata (mesmos resultados no corpus, menos latência), já que a P2 permite criar o índice parcial depois sem mudar o modelo.

### P4 — Etapa `EMBEDDING` no job e ativação → D-050, F3 e F5

**Decisão:**
- Nova etapa entre `PROCESSING_FILES` e `FINALIZING`: lista os `chunk_hash` do snapshot sem embedding do modelo configurado, envia em lotes (`app.embeddings.batch-size`, padrão 64) e grava com `ON CONFLICT DO NOTHING`. Heartbeat e progresso por lote, como na Etapa 3.
- **O snapshot só é ativado com todos os seus chunks com embedding.** Assim, o snapshot ativo é sempre pesquisável; se o provedor falhar, o ativo anterior continua valendo.
- Com `app.embeddings.provider=none`, a etapa é pulada, o snapshot é ativado sem metadados de modelo e a busca vetorial responde `409 EMBEDDINGS_NOT_AVAILABLE`. Isso preserva o uso sem chave (inventário, snapshots e, na Etapa 5, busca lexical).
- Reindexar com o mesmo modelo não chama o provedor para chunks inalterados (cache por hash).
- Trocar de modelo gera embeddings só na próxima indexação. Até lá, o projeto informa `EMBEDDING_MODEL_CHANGED` em `outdatedReasons`, e a busca responde `409 SNAPSHOT_EMBEDDING_MISMATCH`: a pergunta seria embutida com o modelo novo, incompatível com os vetores do snapshot.
- **Provedor configurado, mas snapshot ativo sem embeddings** (por exemplo, indexado com `provider=none` e depois configurado com `openai`): o projeto informa `EMBEDDINGS_MISSING` em `outdatedReasons`, a busca responde `409 SNAPSHOT_EMBEDDING_MISMATCH`, e uma nova indexação gera os embeddings mesmo sem arquivo alterado (a etapa `EMBEDDING` procura chunks sem embedding do modelo configurado, não arquivos alterados).

### P5 — Falhas do provedor → D-051, F1 e F3

**Decisão:**
- O adaptador classifica os erros sem expor tipos do Spring AI: `UNAUTHORIZED` (credencial inválida ou ausente), `RATE_LIMITED`, `QUOTA_EXCEEDED` (créditos, limite de gasto ou de uso esgotados), `UNAVAILABLE` (timeout, 5xx, rede) e `INVALID_REQUEST`. Tabela completa na D-051.
- **Cota esgotada é erro permanente:** pelo guia oficial de códigos de erro da OpenAI (consultado em 16/10/2026), o 429 de cobrança é identificado por `error.code` (`credit_balance_exhausted`, `organization_spend_limit_exceeded`, `project_spend_limit_exceeded`, `organization_usage_limit_exceeded`) ou por `error.type` `insufficient_quota`, e repetir não restaura o acesso. O job termina na hora com `EMBEDDING_QUOTA_EXCEEDED`, sem repetição. O código lido do corpo não vai para log nem para mensagem.
- Dentro do job, `RATE_LIMITED` e `UNAVAILABLE` são repetidos com espera exponencial (`app.embeddings.max-retries`, padrão 4), respeitando `Retry-After` quando houver; esgotadas as repetições, o job termina `FAILED` com `EMBEDDING_PROVIDER_UNAVAILABLE` ou `EMBEDDING_RATE_LIMITED`.
- **Teto da espera:** nenhuma espera passa de `app.embeddings.max-retry-wait` (padrão 60 s), mesmo com um `Retry-After` maior.
- **Heartbeat durante a espera:** a espera é feita em intervalos menores que `heartbeat-interval`, renovando o lease a cada um, para que um job esperando o provedor não seja retomado por outro worker (D-040). Se o claim for perdido durante a espera, o job para.
- Relógio e espera são injetáveis, para os testes controlarem o tempo sem dormir.
- `UNAUTHORIZED`, `QUOTA_EXCEEDED` e `INVALID_REQUEST` encerram o job na hora: `EMBEDDING_PROVIDER_UNAUTHORIZED`, `EMBEDDING_QUOTA_EXCEEDED` e `EMBEDDING_REQUEST_REJECTED`. Não contam como tentativa a repetir pela fila (D-040), porque repetir não resolve.
- Os embeddings já gravados ficam no cache: um novo job retoma de onde parou, sem pagar de novo.
- Na busca, as mesmas falhas viram `502 EMBEDDING_PROVIDER_UNAVAILABLE`, `502 EMBEDDING_PROVIDER_UNAUTHORIZED` e `429 EMBEDDING_RATE_LIMITED`, com mensagem pt-BR sem detalhes do provedor. **Decidido antes da F4 (16/10/2026):** `QUOTA_EXCEEDED` na busca responde `502 EMBEDDING_QUOTA_EXCEEDED`, como a credencial, porque o problema é da conta no provedor e não do cliente da API.
- A inicialização falha com `provider=openai` sem chave configurada (mensagem clara no log, sem imprimir a chave).

### P6 — Cache, troca de modelo e limpeza → D-052, F3 e F5

**Decisão:**
- Cache pela PK `(chunk_hash, provider, model, dimensions)`: o mesmo texto nunca é enviado duas vezes para o mesmo modelo.
- **Limpeza de órfãos** ao fim de cada job, depois da limpeza de chunks da D-039: apagam-se os embeddings cujo `chunk_hash` não existe mais em nenhum chunk. A remoção de um projeto deixa os órfãos para o próximo job ou para a varredura na inicialização.
- Embeddings de um modelo que deixou de ser o configurado só somem quando seus chunks somem; uma limpeza por modelo fica para quando houver mais de um modelo em uso real (bake-off).

### P7 — Contrato da API → D-053, F4 e F5

**Decisão:**
- **`POST /projects/{projectId}/search`** com `{ "query": "…", "snapshotId": null, "limit": 10 }`:
  - `snapshotId` ausente = snapshot ativo; informado, precisa ser do projeto, `READY`/`PARTIAL` e não podado;
  - `query` de 1 a 2.000 caracteres; `limit` de 1 a 50 (padrão 10);
  - resposta: `{ snapshotId, model, results: [ { chunkId, path, otherPaths, startLine, endLine, content, score } ] }`, com `score` = 1 − distância de cosseno, em ordem decrescente e desempate determinístico;
  - cada chunk aparece uma vez; `path` é o primeiro caminho em ordem lexicográfica e `otherPaths` os demais (D-039);
  - a Etapa 5 acrescenta `mode` (`VECTOR`, `LEXICAL`, `HYBRID`) no mesmo endpoint.
- Erros, nesta ordem: `400 VALIDATION_FAILED`; `404 PROJECT_NOT_FOUND`; `404 SNAPSHOT_NOT_FOUND` (inclusive de outro projeto); `409 SNAPSHOT_NOT_SEARCHABLE` (sem snapshot ativo, `BUILDING`, `FAILED` ou podado); `409 EMBEDDINGS_NOT_AVAILABLE`; `409 SNAPSHOT_EMBEDDING_MISMATCH`; erros do provedor (P5).
- `GET /snapshots/{id}` e a listagem ganham `embedding: { provider, model, dimensions, tokens } | null`; `outdatedReasons` ganha `EMBEDDING_MODEL_CHANGED` e `EMBEDDINGS_MISSING` (P4).
- Códigos de job novos: `EMBEDDING_PROVIDER_UNAUTHORIZED`, `EMBEDDING_PROVIDER_UNAVAILABLE`, `EMBEDDING_RATE_LIMITED`, `EMBEDDING_QUOTA_EXCEEDED` (P5), `EMBEDDING_REQUEST_REJECTED` e `EMBEDDING_CHUNK_LIMIT_EXCEEDED` (P8).
- Mensagem pt-BR de `EMBEDDING_QUOTA_EXCEEDED` (chave `index.error.EMBEDDING_QUOTA_EXCEEDED` em `messages.properties`, criada na F3 com o código): "A cota ou o limite de gastos da conta no provedor de embeddings foi atingido. Verifique os créditos e os limites da conta e tente novamente."

### P8 — Privacidade, custo e tokens → D-054, F0, F3 e F6

**Decisão:**
- Só o texto de chunks de arquivos `INDEXED` vai ao provedor; arquivos excluídos, não suportados ou com falha nunca saem da máquina. A pergunta da busca também é enviada.
- Logs trazem quantidades (chunks, lotes, tokens, duração) e códigos, nunca texto de chunk, pergunta ou chave.
- `embedding_tokens` do snapshot soma o uso **reportado** pelo provedor; se ele não reportar, fica `null` (nada de número inventado, `07` §9).
- **Limite por job:** `app.embeddings.max-chunks-per-job` (hipótese inicial: 50.000) conta os chunks que seriam enviados ao provedor, isto é, os do snapshot ainda sem embedding do modelo configurado. Se o job excederia o limite, ele falha **antes de chamar o provedor**, com `EMBEDDING_CHUNK_LIMIT_EXCEEDED`; o ativo anterior não muda. O valor é medido no teste de fumaça e revisado na Etapa 8.
- Sem tokenizador exato nesta etapa: os chunks têm no máximo 4.000 caracteres, bem abaixo do limite de entrada dos modelos considerados; a F1 valida esse limite contra o modelo configurado na inicialização.
- `07-SECURITY-PRIVACY-COSTS.md` e o README documentam o que é enviado, para onde e como desligar (`provider=none`).

### Limitação conhecida

Um chunk rejeitado pelo provedor (`INVALID_REQUEST`) falha o job inteiro com `EMBEDDING_REQUEST_REJECTED`; o lote não é isolado para descobrir qual texto foi recusado. Isolar o lote (dividir e reenviar) fica para depois, se o teste de fumaça ou o uso real mostrarem o problema.

## Princípios de execução

- Valem os das etapas anteriores: fatias entregáveis, `./mvnw verify` verde ao fim de cada uma, `openapi.json` regenerado e commitado, `@Tag`/`@Operation` em todo endpoint novo.
- **Spring AI só em `providers`** (D-006): ArchUnit garante que nenhuma classe fora de `providers` depende de `org.springframework.ai`, e que `providers` não depende de módulos de domínio.
- **Testes do `verify` nunca chamam provedor real nem precisam de chave:** o contexto compartilhado usa `provider=fake`; o adaptador OpenAI é testado contra um servidor HTTP local de teste (o `HttpServer` do JDK, sem dependência nova), cobrindo respostas, erros e `Retry-After`.
- **Provedor fake determinístico:** vetor derivado dos termos do texto (hash de cada termo numa dimensão, normalizado), para que textos parecidos fiquem próximos e os testes de busca tenham ranking previsível.
- **SQL explícito** (D-016) para gravar e buscar vetores, com parâmetros `vector` passados como texto e convertidos no SQL.
- **Testes manuais fora do `verify`:** a medição da busca (tag `performance`) e o teste de fumaça com chave real (tag `smoke`) ficam excluídos do Surefire e do Failsafe por padrão e rodam só com comando explícito, documentado no README.

## Fatias, na ordem

### F0 — Verificações, dependências e configuração (sem migração)

**Entregas**
- Verificação registrada na D-014: Spring AI 2.0.x compatível com o Spring Boot do projeto; módulo do provedor escolhido na P1; versão da extensão `vector` na imagem `pgvector/pgvector:pg17` (`SELECT extversion`).
- Preço e política de retenção da OpenAI para o modelo, conferidos em fonte oficial e registrados na D-047 com a data (P1).
- Exclusão das tags `performance` e `smoke` na configuração do Surefire e do Failsafe.
- BOM do Spring AI e módulo do provedor no `pom.xml`, sem autoconfiguração ativa fora de `provider=openai`.
- `ProviderProperties` (`app.embeddings.*`) com validação na inicialização: provedor conhecido; `dimensions` entre 1 e 2.000; chave obrigatória para `openai`; `max-retry-wait` e `max-chunks-per-job` positivos; `base-url` em `https`, ou `http` só para `localhost`/`127.0.0.1`.
- Contextos de teste fixam `provider=fake` e chave vazia (o `application.yml` importa o `.env` também nos testes), com uma guarda registrada em `META-INF/spring.factories` que impede qualquer contexto de teste de subir com `provider=openai`.
- `.env.example` com `EMBEDDING_API_KEY` vazio e `EMBEDDING_PROVIDER=none` como padrão.
- ArchUnit: `org.springframework.ai` só em `providers`; `providers` não depende de módulos de domínio.

**Testes**
- `ApplicationContextRunner`: inicialização falha sem chave com `openai`, com provedor desconhecido e com dimensão fora da faixa; sobe com `none` e com `fake`.
- Nenhuma mensagem de erro ou log contém o valor da chave.
- `base-url`: aceita `https` e `http` para `localhost`/`127.0.0.1`; recusa `http` para outros hosts, esquemas que não sejam http(s), endereços relativos e endereços com usuário e senha, sem repetir o valor na mensagem.
- Guarda dos testes: um contexto de teste real com `provider=openai` falha ao subir.

### F1 — Porta de embeddings, provedor fake e adaptador

**Entregas**
- Em `providers`: `EmbeddingProvider` (`EmbeddingResult embed(List<String> texts)`, `ModelInfo model()`), `EmbeddingFailure` com o tipo da P5; nenhum tipo do Spring AI na assinatura.
- `FakeEmbeddingProvider` determinístico.
- `OpenAiEmbeddingProvider`, que usa o Spring AI por dentro, traduz erros e devolve o uso de tokens quando reportado. A repetição própria do cliente da OpenAI fica desligada: a única camada de repetição é a da P5.
- Repetição com espera exponencial, `Retry-After` com teto em `max-retry-wait` e espera fatiada com um gancho de heartbeat, num componente próprio, com relógio e espera injetáveis.

**Como foi entregue (16/10/2026)**
- `OpenAiEmbeddingProvider` usa o `OpenAiSetup` do Spring AI para montar o cliente e chama o SDK da OpenAI diretamente, porque o `OpenAiEmbeddingModel` falha quando a resposta não traz uso de tokens (detalhes na D-047). O SDK (`openai-java-core`) é declarado explicitamente no `pom.xml`, na mesma versão que o `spring-ai-openai` declara, e um teste confere a igualdade. A Etapa 5 decide se mantém o `spring-ai-openai` ou usa só o SDK.
- Log do SDK desligado, para que `OPENAI_LOG` não registre corpos de requisição; um teste com `OPENAI_LOG=debug` numa JVM filha confirma isso.
- `QUOTA_EXCEEDED` na porta, permanente, classificado por `error.code`/`error.type` do corpo (D-051).
- `EmbeddingRetry`: espera inicial de 1 s, dobrando a cada tentativa, até `max-retry-wait`; `Retry-After` substitui a espera calculada, com o mesmo teto. A espera é injetada como `Sleeper`; o gancho é um `Runnable` chamado após cada fatia, e uma exceção lançada por ele interrompe a espera e se propaga (a F3 usará a `LostClaimException` do heartbeat). Interrupção da thread encerra a espera com a última falha.
- Mensagens de `EmbeddingFailure` sem causa do SDK (a mensagem dela inclui o corpo da resposta).
- ArchUnit estendido a `com.openai`.
- **Não entregue, pendente para a F3:** a validação do limite de entrada contra o modelo configurado, prevista na P8. Exige o limite de entrada de cada modelo vindo de fonte oficial. **A F3 não começa sem isso.**

**Testes**
- Fake: mesmo texto → mesmo vetor; dimensão configurada; norma 1; textos com termos em comum ficam mais próximos que textos sem termos em comum.
- Adaptador contra o servidor de teste: lote com N textos → N vetores na ordem; dimensão diferente da configurada → erro; 401 → `UNAUTHORIZED`; 429 com `Retry-After` → `RATE_LIMITED` com o atraso; 500 e timeout → `UNAVAILABLE`; 400 → `INVALID_REQUEST`; uso de tokens repassado; com 429 e `max-retries=4`, o servidor recebe **exatamente 5 requisições** (1 original e 4 repetições), o que prova que só uma camada repete.
- Acrescentados na revisão da F1: 401 chega uma única vez ao servidor; resposta sem `usage` devolve tokens `null` sem falhar; 429 de cota (cada `error.code` de cobrança e `error.type` `insufficient_quota`) → `QUOTA_EXCEEDED`, chega uma única vez ao servidor e ignora `Retry-After`; 429 de taxa (sem `code` ou `slow_down`) repete e esgota em 5 requisições; o código de cota não aparece em mensagem nem na saída; com `OPENAI_LOG=debug` numa JVM filha, nenhum corpo de requisição aparece na saída (com teste de controle sem o desligamento); a versão do SDK declarada no `pom.xml` é a mesma que o `spring-ai-openai` declara.
- Repetição, com relógio controlado (sem dormir): para nos erros permanentes; respeita o máximo de tentativas; espera crescente; `Retry-After` de 300 s é limitado a 60 s; durante uma espera longa, o gancho de heartbeat é chamado a cada intervalo; se o gancho sinalizar claim perdido, a espera é interrompida.

### F2 — Modelo de vetores (V8)

**Entregas**
- Migração V8 (abaixo): `chunk_embeddings`, colunas de embedding em `snapshots` e `EMBEDDING` no `CHECK` de etapa de `index_jobs`.
- Repositório em `indexing` por `JdbcClient`: hashes sem embedding de um snapshot, inserção em lote idempotente, limpeza de órfãos.

**Como foi entregue (16/10/2026)**
- A V8 é o próximo número livre (a última migração era a V7), e o nome real da constraint de etapa é `index_jobs_stage_valid`, como no esboço. A migração segue o esboço sem mudanças.
- `ChunkEmbeddingRepository`: `missingChunkHashes(snapshotId, model)`, `insert(model, embeddings, now)` (devolve as linhas realmente inseridas) e `deleteOrphans()` (devolve as linhas apagadas). Recebe o `ModelInfo` da porta de `providers`. Detalhes na D-048.
- `VectorLiteral` monta o literal do pgvector sem depender do locale e rejeita NaN, infinito e vetor vazio; o repositório rejeita também vetor com dimensão diferente da do modelo, tudo antes do banco.
- Plano de execução conferido por teste (`ChunkEmbeddingPlanIT`, 200 mil chunks): só acessos por índice em `snapshot_files`, `chunks` e `chunk_embeddings`. Nenhum índice novo é necessário (D-048).
- A consulta devolve só hashes. A F3 vai precisar do texto de cada lote: uma consulta por lote (texto de um chunk por hash, no snapshot) evita carregar o texto de todos os chunks de uma vez.
- `IndexStage` não ganhou `EMBEDDING`, nem o `IndexRunner` mudou: entram na F3.

**Testes**
- `CHECK` de dimensão: vetor com dimensão diferente de `dimensions` é rejeitado.
- Inserção repetida não duplica; hashes sem embedding ignoram os que já têm o mesmo modelo e contam os de outro modelo como faltando.
- Limpeza remove só os órfãos.
- Acrescentados na F2: a coluna `embedding` não tem dimensão fixa; `dimensions` fora de 1 a 2.000 é rejeitado; metadados de embedding do snapshot são todos nulos ou todos preenchidos; `index_jobs` aceita a etapa `EMBEDDING`; o mesmo hash coexiste com dois modelos e com duas dimensões; o vetor gravado volta igual ao enviado; vetor com NaN, infinito ou dimensão errada é rejeitado antes do banco, sem gravar nada do lote; o literal usa ponto com o locale padrão pt-BR; embedding de outra dimensão do mesmo modelo conta como faltando; chunks de outra versão de chunking, de outro snapshot e arquivos não suportados ficam fora; hash repetido em dois arquivos aparece uma vez; a limpeza mantém hashes usados por outro projeto; `EXPLAIN` da consulta de hashes sem embedding sem varredura sequencial com 200 mil chunks.

### F3 — Etapa `EMBEDDING` no job

**Pré-condição:** validação, na inicialização, do limite de entrada do modelo configurado, com valor de fonte oficial (P8). A F3 não começa sem isso.

**Entregas**
- `IndexRunner` ganha a etapa entre arquivos e finalização (P4), com progresso em chunks e heartbeat por lote.
- Falhas do provedor encerram o job com os códigos da P5 (inclusive `EMBEDDING_QUOTA_EXCEEDED`, com a mensagem pt-BR da P7 em `messages.properties`); o ativo anterior não muda.
- Antes de chamar o provedor, conta os chunks a enviar e falha com `EMBEDDING_CHUNK_LIMIT_EXCEEDED` acima de `max-chunks-per-job` (P8).
- Esperas de repetição renovam o heartbeat (P5).
- Finalização grava provedor, modelo, dimensão e tokens no snapshot e limpa embeddings órfãos (P6).
- `provider=none`: etapa pulada, snapshot ativado sem embedding.

**Como foi entregue (16/10/2026)**
- **Pré-condição cumprida:** limites conferidos em fonte oficial (guia de embeddings e referência de `POST /v1/embeddings` da OpenAI, 16/10/2026): 8.192 tokens por entrada para `text-embedding-3-small`, `-3-large` e `ada-002`; até 2.048 entradas e 300.000 tokens somados por requisição; entrada vazia recusada. Registrado na D-054.
- A porta ganhou `InputLimits limits()`. O adaptador OpenAI só aceita os três modelos documentados (outro modelo impede a inicialização); o fake não tem limite.
- Validação na inicialização (`EmbeddingStage`, em `indexing`, porque depende do tamanho do chunk): 4.000 caracteres ≤ tokens por entrada; `batch-size` ≤ 2.048 e `batch-size` × 4.000 ≤ 300.000 (até 75 para a OpenAI). **Desvio:** a conta usa um token por caractere, que não é um limite rigoroso para texto fora do ASCII; um chunk excedente vira `EMBEDDING_REQUEST_REJECTED` (D-054).
- `EmbeddingStage` faz a etapa: hashes faltantes, limite por job antes de qualquer chamada, lotes com texto lido por consulta por lote (`chunkTexts`), progresso e heartbeat na transação de cada lote, esperas de repetição fatiadas em `heartbeat-interval` com renovação do lease (`Heartbeat.renew`).
- `IndexOutcomes.complete` grava os metadados de embedding no snapshot e chama `deleteOrphans()` depois da limpeza de chunks. A falha não limpa órfãos, para preservar o cache (D-052).
- **Acréscimo:** varredura de órfãos na inicialização (`EmbeddingOrphanSweep`), prevista na P6 mas sem fatia.
- `embedding_tokens`: soma do uso reportado; `null` se algum lote não reportar; `0` quando nada foi enviado.
- `stage` na API aceita `EMBEDDING` (`openapi.json` regenerado); seis códigos novos com mensagens pt-BR.
- Decisões registradas: D-050, D-052 e D-054; D-051 atualizada.

**Testes**
- Indexação com o fake: todo chunk do snapshot tem embedding do modelo; o snapshot traz os metadados.
- Reindexação sem mudanças: zero chamadas ao provedor (contador no fake).
- Arquivo alterado: só os chunks novos são enviados.
- Provedor falhando (fake configurável no contexto com spy): `UNAUTHORIZED` falha na hora; `UNAVAILABLE` repete e depois falha; o snapshot anterior continua ativo; um job seguinte reaproveita os embeddings já gravados.
- Remoção de projeto seguida de outro job: embeddings órfãos somem.
- Limite por job: com `max-chunks-per-job` abaixo do número de chunks novos, o job falha com `EMBEDDING_CHUNK_LIMIT_EXCEEDED` e o fake não recebe nenhuma chamada; reindexar sem mudanças (nada a enviar) não esbarra no limite.
- Heartbeat na espera: com relógio controlado e o fake respondendo `RATE_LIMITED`, o `heartbeat_at` do job avança durante a espera e o job não é retomado por outro worker.
- Rejeição: `INVALID_REQUEST` falha o job inteiro com `EMBEDDING_REQUEST_REJECTED` (limitação conhecida).
- Acrescentados na F3: conteúdo repetido em dois arquivos e arquivo binário (só os textos indexados são enviados, uma vez); nenhum texto de chunk nos logs; `QUOTA_EXCEEDED` falha na hora com a mensagem pt-BR da P7; `UNAVAILABLE` chega 5 vezes ao provedor; o limite por job conta só os chunks a enviar (30 chunks no snapshot, limite 20, reindexação sem mudanças passa); a varredura na inicialização remove órfãos; validação dos limites de entrada e do lote; modelo OpenAI sem limite documentado não inicia; `provider=none` pula a etapa. O teste do heartbeat na espera foi conferido com a renovação desligada: o job é retomado por outro worker e o teste falha.

### F4 — Busca vetorial

**Entregas**
- Módulo `retrieval`: `VectorSearch` com SQL exato (P3), filtrado por snapshot, modelo e dimensão; agrupa caminhos por conteúdo (`otherPaths`).
- `POST /projects/{projectId}/search` (P7), com `@Tag("Busca")` e `@Operation`.
- Medição manual (tag `performance`, fora do `verify`): latência da busca exata com 50 mil chunks sintéticos, com o resultado registrado neste plano (P3).

**Como foi entregue (16/10/2026)**
- Módulo `retrieval`: `VectorSearch` (SQL exato, D-049), `SearchService` (validação e precedência dos erros, D-053) e `SearchController` (`@Tag("Busca")`, `operationId` `searchProject`).
- `model` na resposta virou objeto `{ provider, name, dimensions }`.
- `INVALID_REQUEST` do provedor na busca vira `502 EMBEDDING_REQUEST_REJECTED`, código que o plano não listava. A busca não repete chamadas ao provedor e ainda não repassa `Retry-After`.
- `VectorLiteral` foi movido para `shared.persistence`; `ProjectService.find` lê o projeto sem montar o resumo.
- O `EmbeddingContext` da F3 ficou público e é reaproveitado pelo `SearchApiIT`, sem contexto Spring novo. O caso `provider=none` é coberto por teste unitário do serviço, para não subir mais um contexto com PostgreSQL.
- Medição manual registrada em "Resultado e desvios".

**Testes (aceite da etapa)**
- Dois projetos e dois snapshots do mesmo projeto com conteúdos diferentes: a busca só devolve chunks do snapshot pedido, nunca de outro projeto nem de outro snapshot.
- O snapshot ativo é o padrão; snapshot antigo não podado pode ser consultado explicitamente; podado → `409 SNAPSHOT_NOT_SEARCHABLE`.
- Ranking com o fake: o chunk que contém os termos da pergunta vem primeiro; empate resolvido de forma estável.
- Mesmo conteúdo em dois caminhos: um resultado, com `otherPaths`.
- Vetores de outro modelo no banco não interferem na busca.
- Dimensão incompatível: snapshot de outro modelo → `409 SNAPSHOT_EMBEDDING_MISMATCH`.
- `provider=none` → `409 EMBEDDINGS_NOT_AVAILABLE`; validação de `query` e `limit`; precedência dos erros.
- A pergunta não aparece nos logs.
- Acrescentados na F4: `limit` padrão 10 e explícito; pergunta em branco e com 2.001 caracteres recusadas, com 2.000 aceita; erros de `query` e `limit` juntos, antes de procurar o projeto; snapshot `BUILDING` e `FAILED`; projeto sem snapshot ativo; snapshot sem embeddings; cada falha do provedor com status e código, sem detalhe do provedor no corpo; linhas e `model` na resposta.

### F5 — Estado, contrato e troca de modelo

**Entregas**
- `embedding` em `GET /snapshots/{id}` e na listagem; `EMBEDDING_MODEL_CHANGED` e `EMBEDDINGS_MISSING` em `outdatedReasons`.
- Testes da troca de modelo: com outro modelo configurado, o projeto informa a desatualização, a busca responde `SNAPSHOT_EMBEDDING_MISMATCH`, e a reindexação gera embeddings só para o novo modelo, mantendo o cache do anterior.
- Testes de snapshot sem embeddings (P4): projeto indexado com `provider=none` (snapshot inserido por SQL sem metadados de modelo, no contexto com `fake`) → `outdatedReasons` traz `EMBEDDINGS_MISSING`; a busca responde `SNAPSHOT_EMBEDDING_MISMATCH`; uma nova indexação, sem nenhum arquivo alterado, gera os embeddings de todos os chunks, ativa o snapshot novo e a busca passa a funcionar.

**Como foi entregue (16/10/2026)**
- `Snapshot` passou a ler as colunas de embedding; `embedding: { provider, model, dimensions, tokens } | null` no detalhe e na listagem de snapshots (`openapi.json` regenerado).
- `outdatedReasons` compara o snapshot ativo com o modelo configurado (`ProjectIndexesAdapter`): `EMBEDDINGS_MISSING` sem metadados de modelo, `EMBEDDING_MODEL_CHANGED` com provedor, modelo ou dimensão diferentes. Com `provider=none` nenhum dos dois aparece (D-050).
- Nenhuma mudança na busca nem no job: `SNAPSHOT_EMBEDDING_MISMATCH` (F4) e a etapa `EMBEDDING`, que procura chunks sem vetor do modelo configurado (F3), já cobriam a troca de modelo e o snapshot sem embeddings; a F5 os confirma por teste.
- **Desvio nos testes:** o snapshot "indexado com `provider=none`" é um snapshot indexado no contexto com `fake` cujos metadados de modelo foram apagados por SQL, junto com os vetores. É o mesmo estado que o job com `none` deixa, sem montar arquivos e chunks à mão nem subir outro contexto.
- Troca de modelo simulada com o spy do fake devolvendo outro `ModelInfo`, no contexto da F3 (`EmbeddingModelChangeIT`), sem contexto Spring novo.
- Decisões atualizadas: D-050 e D-053.

**Testes**
- Detalhe e listagem de snapshots trazem provedor, modelo, dimensão e tokens; projeto sem desatualização.
- Troca de modelo: o projeto informa `EMBEDDING_MODEL_CHANGED`; a busca responde `409 SNAPSHOT_EMBEDDING_MISMATCH` sem chamar o provedor; a reindexação envia todos os chunks para o modelo novo, o snapshot passa a trazê-lo, os vetores do modelo anterior continuam no banco, a desatualização some e a busca volta a funcionar.
- Snapshot sem embeddings: `EMBEDDINGS_MISSING`, `embedding` nulo e `SNAPSHOT_EMBEDDING_MISMATCH`; a reindexação sem arquivo alterado (`changes.unchanged` = 2) envia todos os chunks, ativa o snapshot novo e a busca devolve os dois chunks.
- Unitários de `outdatedReasons`: snapshot do modelo configurado; outro modelo, outra dimensão e outro provedor; sem metadados com e sem provedor configurado; vetores ignorados com `provider=none`.

### F6 — Fechamento

- Aceite: consultas vetoriais retornam chunks do snapshot correto (F4) e os vetores têm dimensão compatível (F2, F4).
- **Teste manual de fumaça com chave real** (tag `smoke`, fora do `verify`): indexa um repositório pequeno com `provider=openai`, confere metadados e tokens no snapshot, faz uma busca e verifica que o resultado aponta para o arquivo esperado. O README documenta o comando, a chave necessária, o custo esperado (pelo preço registrado na D-047) e como rodar com o repositório de exemplo. O resultado (data, modelo, chunks, tokens, latência) é registrado neste plano.
- `openapi.json` final commitado.
- `10-DECISIONS.md`: D-047 a D-054; O-004 atualizada (provisória, bake-off na Etapa 5); D-014 com a verificação do Spring AI.
- `04` (§5 e §6), `05` (`chunk_embeddings`, `snapshots`), `06` (busca, snapshot, códigos), `07` (§7 e §9, inclusive a limitação do lote rejeitado), `09` (status) e README (provedor, chave, `provider=none`, o que é enviado, testes manuais).
- Seção "Resultado e desvios" neste arquivo.

## Migração

### V8__create_chunk_embeddings.sql (esboço)

```sql
CREATE TABLE chunk_embeddings (
    chunk_hash text        NOT NULL,
    provider   text        NOT NULL,
    model      text        NOT NULL,
    dimensions integer     NOT NULL
                           CONSTRAINT chunk_embeddings_dimensions_valid
                           CHECK (dimensions BETWEEN 1 AND 2000),
    embedding  vector      NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (chunk_hash, provider, model, dimensions),
    CONSTRAINT chunk_embeddings_dimensions_match
        CHECK (vector_dims(embedding) = dimensions)
);

ALTER TABLE snapshots
    ADD COLUMN embedding_provider   text,
    ADD COLUMN embedding_model      text,
    ADD COLUMN embedding_dimensions integer,
    ADD COLUMN embedding_tokens     bigint,
    ADD CONSTRAINT snapshots_embedding_consistency CHECK (
        (embedding_provider IS NULL AND embedding_model IS NULL
            AND embedding_dimensions IS NULL)
     OR (embedding_provider IS NOT NULL AND embedding_model IS NOT NULL
            AND embedding_dimensions IS NOT NULL)
    );

ALTER TABLE index_jobs
    DROP CONSTRAINT index_jobs_stage_valid,
    ADD CONSTRAINT index_jobs_stage_valid
        CHECK (stage IN ('SCANNING', 'PROCESSING_FILES', 'EMBEDDING', 'FINALIZING'));
```

- Sem FK de `chunk_embeddings` para `chunks`: o cache é global e sobrevive à remoção de um projeto até a limpeza de órfãos (P6).
- Sem índice aproximado nesta etapa (P3). Quando vier, será parcial por modelo, por exemplo `USING hnsw ((embedding::vector(1536)) vector_cosine_ops) WHERE model = '…' AND dimensions = 1536`.
- A busca exata usa a PK para achar o vetor de cada `chunk_hash` do snapshot.

## Configuração (`app.embeddings.*`, hipóteses a medir)

| Propriedade | Padrão | Uso |
|---|---|---|
| `provider` | `none` | `none`, `fake` (testes e demonstração) ou `openai` |
| `model` | `text-embedding-3-small` | modelo do provedor |
| `dimensions` | 1536 | dimensão pedida ao provedor e validada em cada vetor |
| `batch-size` | 64 | textos por chamada |
| `max-retries` | 4 | repetições de erros transitórios dentro do job |
| `max-retry-wait` | 60s | teto de cada espera, inclusive com `Retry-After` maior |
| `max-chunks-per-job` | 50000 | chunks a enviar por job; acima disso, `EMBEDDING_CHUNK_LIMIT_EXCEEDED` sem chamar o provedor |
| `timeout` | 30s | tempo máximo de uma chamada |
| `base-url` | `https://api.openai.com/v1` | destino da chave e dos trechos; `https`, ou `http` só para `localhost`/`127.0.0.1` |

A chave vem de `EMBEDDING_API_KEY`. Padrões em `ProviderProperties`, sem entradas no `application.yml` além do mapeamento das variáveis de ambiente.

## Riscos

| Risco | Mitigação |
|---|---|
| Spring AI 2.0.x incompatível com o Spring Boot em uso. | Verificação na F0 antes de qualquer código; se falhar, adaptador HTTP próprio atrás da mesma porta (D-006 permite). |
| Custo inesperado ao indexar repositórios grandes. | Cache por hash, só conteúdo novo é enviado, `max-project-bytes`, `max-chunks-per-job` verificado antes de chamar o provedor, tokens registrados no snapshot; padrão `provider=none`. |
| Vazamento de código ou chave em logs. | Logs só com contagens; teste com saída capturada; chave nunca impressa. |
| Limite de taxa do provedor interrompe indexações longas. | Repetição com `Retry-After` limitado por `max-retry-wait`; embeddings já gravados são reaproveitados no job seguinte. |
| Espera longa por repetição faz o lease vencer e outro worker retomar o job. | Heartbeat renovado durante a espera; teste com relógio controlado. |
| Um texto recusado pelo provedor derruba o job inteiro. | Limitação conhecida e documentada; isolar o lote fica para depois, se o uso mostrar necessidade. |
| Nenhum teste automatizado usa o provedor real. | Teste manual de fumaça com chave real na F6, documentado no README. |
| Busca exata lenta em snapshots grandes. | Medição na F4; índice HNSW parcial por modelo como evolução (P2 permite sem migrar dados). |
| Provedor fake esconder problemas de qualidade. | O fake só garante a mecânica; qualidade é medida no bake-off da Etapa 5. |
| Trocar o modelo deixa a busca indisponível até reindexar. | `EMBEDDING_MODEL_CHANGED` e `SNAPSHOT_EMBEDDING_MISMATCH` explícitos; reindexação reaproveita o restante. |
| Testes dependerem de rede. | Provedor fake no contexto compartilhado; adaptador testado contra servidor local. |

## Resultado e desvios

A preencher no fechamento (F6).

### Medição da busca exata (F4, 16/10/2026)

Comando (fora do `verify`):

```bash
./mvnw verify -Dtest.excluded-groups= -Dgroups=performance -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VectorSearchPerformanceIT
```

| Máquina | Chunks | Dimensões | Consultas | Média | p95 | Máximo |
|---|---|---|---|---|---|---|
| Apple M4, 16 GB, PostgreSQL 17 + pgvector 0.8.7 no Testcontainers | 50.000 | 1.536 | 50 (após 5 de aquecimento), `limit` 10 | 213 ms | 313 ms | 396 ms |
