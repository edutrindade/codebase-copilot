# Plano — Etapa 5: Busca lexical e recuperação híbrida

**Status:** aprovado em 16/10/2026, com os ajustes da F0 em P1, P3 e P4. As propostas P1 a P9 viram D-055 em diante, registradas em `10-DECISIONS.md` na fatia indicada em cada uma.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 5), `docs/plans/etapa-4.md` (pendências), `04-RAG-PIPELINE.md` §6 e §13, `05-DATA-MODEL.md` §4, `08-EVALUATION.md`, D-016, D-017, D-039, D-043, D-047, D-049, D-050, D-053, O-004, O-005, O-010, O-011.

## Escopo

**Dentro:**
- tokenização de identificadores na aplicação (camelCase, snake_case, siglas, caminhos), igual na indexação e na consulta;
- índice lexical por chunk (full-text `simple` com pesos) e busca lexical restrita a projeto e snapshot;
- busca de caminhos por `pg_trgm` em `snapshot_files.relative_path`;
- fusão por RRF (k=60) e `mode` (`LEXICAL`, `VECTOR`, `HYBRID`) em `POST /projects/{projectId}/search`;
- corpus de avaliação versionado, com anotações por arquivo e intervalo de linhas (resolve a O-011);
- harness de avaliação fora do `verify`, com Recall@k, MRR, Precision@k e cobertura de arquivos, e resultados versionados;
- baseline lexical × vetorial × híbrido no mesmo corpus (aceite da etapa).

**Pendências herdadas e onde são resolvidas:**

| Pendência | Origem | Onde |
|---|---|---|
| `mode` no endpoint de busca | D-053 | P6; F4 |
| Colunas lexicais e `pg_trgm` em caminhos | `05` §4, D-017 | P2, P4; F2, F3 |
| Bake-off de embeddings (O-004) | D-047 | P8; F7 (condicional) |
| Manter `spring-ai-openai` ou usar só o SDK | D-047 | P9 |
| Comparar parâmetros de chunking (O-010) | D-043 | P7 |
| HNSW com varredura iterativa | D-049 | fica para a Etapa 8 (só a latência híbrida é medida aqui) |
| Teste de fumaça com chave real | Etapa 4, F6 | pré-requisito da F6 (precisa de saldo) |

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Reranking | só se o baseline mostrar necessidade (`04` §6); não antes da Etapa 8 |
| BM25 ou índice lexical externo | só se o lexical se mostrar fraco no corpus (O-005) |
| Montagem de contexto e geração | Etapa 6 |
| Métricas de resposta (citação, abstenção) | Etapa 6; o corpus já anota as perguntas sem resposta |
| Índice HNSW | Etapa 8, se a latência justificar |
| Símbolos por AST (`symbol_name`) | Etapa 9 (D-027) |

## Pré-requisito externo

O aceite compara o híbrido com o vetorial, e isso exige vetores reais: o provedor fake (D-047) aproxima textos por termos em comum, então um "baseline vetorial" com ele mede uma busca lexical disfarçada. **Sem saldo na conta do provedor, F0 a F5 andam normalmente, mas a F6 só produz o baseline lexical.** O custo esperado do corpus é de centavos de dólar (estimativa exata na F0, pelo total de tokens estimados e o preço da D-047). O teste de fumaça pendente da Etapa 4 roda na mesma ocasião.

## Propostas para aprovação

### P1 — Tokenização de identificadores → F1

**Problema:** o parser do full-text do PostgreSQL não separa `getUserById` em `get user by id`, e a D-017 decidiu fazer isso na aplicação.

**Proposta:**
- `IdentifierTokenizer` puro, em `codeanalysis` (o módulo de análise de texto; `indexing` e `retrieval` passam a depender dele, sem ciclo).
- Extrai tokens com letras e dígitos; `_`, `$` e os demais símbolos separam (o parser também descarta `_` e `$`).
- Identificador com fronteiras de caixa ou de dígito: emite a forma unida em minúsculas e as partes. `getUserById` → `getuserbyid get user by id`; `HTTPClient` → `httpclient http client`; `parseJSON2Xml` → `parsejson2xml parse json 2 xml`. A forma unida já está no conteúdo (peso B); repeti-la no peso A é o que faz o identificador valer mais que a prosa.
- `snake_case`: só as partes. `user_id` → `user id`, porque o parser quebra `_` e a forma unida nunca vira lexema (F0).
- Token de uma parte entra só quando está colado a outro por `.`, `/`, `@` ou `-` (sem espaço): `src/foo/Bar.java` → `src foo bar java`; `repo.find` → `repo find`; `my-file.ts` → `my file ts`. O parser guarda essas sequências como um lexema só (`file`, `host`, `email`, `version`), e as partes não existiriam no índice (F0). Fora disso, o token já está no `content` e não entra. Termos repetidos no chunk entram uma vez.
- Unicode por `Character.isLetter`/`isDigit`. As minúsculas do Java (`Locale.ROOT`) servem só para tirar repetidos e stopwords: a normalização que vale é a do PostgreSQL (`to_tsvector`/`to_tsquery`), porque as duas diferem (`İ`, F0).
- Versão própria, `ident-v1`, gravada com o índice lexical (P2): mudar a tokenização cria uma versão nova, como o chunking.
- O comportamento do parser medido na F0 (`ts_debug('simple', …)`) fica registrado na decisão. Um teste de integração confere que todo termo do tokenizador aparece como lexema em `to_tsvector('simple', identifier_terms)`, para os dois não se desalinharem.

### P2 — Armazenamento do índice lexical (revisa `05` §4) → F2

**Problema:** `05` previa `identifier_terms` e `lexical_tsv` como colunas de `chunks`. Mas os chunks existentes não têm termos, a tokenização é da aplicação (uma migração SQL não consegue preencher), e uma migração Java chamando o tokenizador mudaria de comportamento quando ele mudasse.

**Opções:**
1. Colunas em `chunks` (como em `05`), preenchidas no insert e por uma etapa do job para chunks antigos; mudar o tokenizador reescreve linhas de chunks "imutáveis" (D-039), inclusive de snapshots antigos.
2. **Tabela `chunk_lexical`** `(chunk_id, lexical_version)`, FK para `chunks` com `ON DELETE CASCADE`, com `identifier_terms` e `tsv` (`setweight(to_tsvector('simple', identifier_terms), 'A') || setweight(to_tsvector('simple', content), 'B')`, calculado no insert), índice GIN em `tsv`. Sempre por `to_tsvector`: `array_to_tsvector` gera lexemas sem posição, e aí o `setweight` não tem efeito e o `ts_rank_cd` dá zero (F0).

**Proposta:** opção 2, no mesmo desenho dos embeddings (D-048, D-050):
- O job preenche as linhas que faltam para a versão atual numa etapa `LEXICAL`, entre `PROCESSING_FILES` e `EMBEDDING`, em lotes com heartbeat. Sem chamada externa, então não tem política de falha nova.
- O snapshot ganha `lexical_version`, gravada na ativação. Snapshots antigos (`null`) ou de outra versão: o projeto informa `LEXICAL_INDEX_MISSING`/`LEXICAL_INDEX_CHANGED` em `outdatedReasons`, a busca lexical e a híbrida respondem `409 SNAPSHOT_LEXICAL_MISMATCH`, e uma reindexação sem arquivo alterado resolve.
- Versões convivem (útil para a avaliação comparar tratamentos de identificadores, O-005); a limpeza vem de graça pela cascata com os chunks.
- Migração `V9`. O texto do chunk fica três vezes no banco (conteúdo, chunk, `tsv`); o tamanho é medido no teste de desempenho.

### P3 — Consulta lexical → F3

**Problema:** as perguntas são em linguagem natural ("como o reembolso é calculado?"). `plainto_tsquery` exige todos os termos (AND) e quase nunca casa; a configuração `simple` não tem stopwords, e o `ts_rank` não tem IDF (limitação conhecida da D-017).

**Proposta:**
- A pergunta passa pelo mesmo tokenizador (P1); os termos viram uma `tsquery` montada na aplicação com **OR**: cada termo entre aspas simples (aspas internas dobradas), unidos por ` | `, e o texto passado como parâmetro a `to_tsquery('simple', :q)`. Sem aspas, `&`, `!`, `:` e `(` dão erro de sintaxe; o cast `:q::tsquery` não é usado porque não normaliza; nada é concatenado ao SQL (F0). Pergunta sem termos é tratada antes do SQL.
- Lista curta de stopwords pt-BR e en no código (artigos, preposições, pronomes interrogativos), versionada junto com `ident-v1`. Pergunta só com stopwords → resultado vazio, não erro.
- Ordem: `ts_rank_cd(tsv, q, 1)` (pesos padrão A=1,0, B=0,4; normalização por comprimento), depois caminho, linha inicial e id, como na vetorial.
- Mesmo formato de resultado da vetorial (um resultado por chunk, `path` + `otherPaths`, D-039). O SQL de candidatos do snapshot sai de `VectorSearch` para um trecho compartilhado.

### P4 — Caminhos por `pg_trgm` → F3

**Proposta:**
- Índice GIN `gin_trgm_ops` em `snapshot_files.relative_path` (V9).
- Termos da pergunta com cara de caminho ou nome de arquivo (contêm `/` ou terminam em `.<extensão>`, ou casam com um nome de arquivo do snapshot) geram uma terceira lista: arquivos por `strict_word_similarity` acima de um limiar, e, em cada arquivo, os chunks na ordem das linhas. O operador `<<%` (usa o índice; limiar padrão 0,5) é o pré-filtro, e `strict_word_similarity(:term, relative_path) >= :threshold` filtra com o limiar como parâmetro, não pela GUC. Limiar inicial **0,8**, ajustado na avaliação. O `word_similarity` foi descartado na F0 por aceitar arquivos parecidos demais (0,889 para `IndexJobApiIT.java` com o termo `IndexJob`).
- Essa lista entra na fusão como um canal a mais **só quando existe** (pergunta sem termo de caminho não muda nada).
- Se a avaliação não mostrar ganho, o canal sai antes do fechamento (é a parte mais especulativa do plano).

### P5 — Fusão → F4

**Proposta:**
- RRF com k=60 (D-017), na aplicação, sobre as listas de cada canal: `score = Σ 1/(k + rank)`.
- Cada canal busca `app.retrieval.candidates` (padrão 50) antes de fundir; a fusão devolve `limit`.
- Desempate determinístico: maior score, melhor rank individual, caminho, linha inicial, id.
- `k`, profundidade e pesos por canal são parâmetros da fusão, para a avaliação variá-los **sem consultar o banco de novo** (as listas de cada pergunta são buscadas uma vez).
- Canal vetorial e lexical consultados em sequência nesta etapa; paralelizar só se a medição pedir.

### P6 — Contrato da busca (revisa a D-053) → F4

**Proposta:**
- `mode`: `LEXICAL`, `VECTOR` ou `HYBRID`; **padrão `HYBRID`** (ainda não há cliente do endpoint, e é o modo que a Etapa 6 vai usar).
- Resposta: `{ snapshotId, mode, model | null, lexicalVersion | null, results: [ { chunkId, path, otherPaths, startLine, endLine, content, score, ranks: { lexical | null, vector | null, path | null } } ] }`. `score` depende do modo (cosseno, `ts_rank_cd` ou RRF) e só serve para ordenar dentro de uma resposta. `ranks` alimenta `rank_lexical`/`rank_vector` de `answer_evidence` na Etapa 6.
- `LEXICAL` funciona com `provider=none` e não envia nada ao provedor.
- `HYBRID` com `provider=none`, ou com snapshot sem vetores do modelo: **`409`** (`EMBEDDINGS_NOT_AVAILABLE` / `SNAPSHOT_EMBEDDING_MISMATCH`), sem cair para lexical em silêncio. A alternativa (degradar para lexical e informar `mode: LEXICAL` na resposta) também é razoável; preferi a resposta explícita porque um resultado pior sem aviso é difícil de perceber.
- Ordem dos erros: `400 VALIDATION_FAILED` (`query`, `limit`, `mode`); `404 PROJECT_NOT_FOUND`; `404 SNAPSHOT_NOT_FOUND`; `409 SNAPSHOT_NOT_SEARCHABLE`; `409 SNAPSHOT_LEXICAL_MISMATCH` (`LEXICAL`, `HYBRID`); `409 EMBEDDINGS_NOT_AVAILABLE` e `409 SNAPSHOT_EMBEDDING_MISMATCH` (`VECTOR`, `HYBRID`); erros do provedor (D-051).
- Log: modo, snapshot, quantidade por canal e duração por canal; nunca a pergunta (D-054).

### P7 — Corpus de avaliação (resolve a O-011) → F5

**Proposta, em três partes:**
1. **Fixture sintética versionada** (`eval/corpus/fixture/`, poucos arquivos em Java, TypeScript, Python, SQL e Markdown), para casos que repositórios reais não garantem: identificador exato, sinônimo sem termo em comum, mesmo conteúdo em dois caminhos, arquivo com prompt injection, arquivo excluído com segredo sintético, pergunta sem resposta.
2. **O próprio Codebase Copilot** num commit fixo, extraído por você com `git archive <commit>` para uma pasta fora do repositório. É médio, real, multiarquivo, e você conhece bem para anotar.
3. **Um repositório open source** de outra linguagem, licença permissiva, num commit fixo (candidatos conferidos na F0, por exemplo um projeto Python ou TypeScript pequeno e conhecido). Também baixado por você; não fica no repositório.

Para as partes 2 e 3, o harness recebe a pasta por variável de ambiente e confere um manifesto (SHA-256 de cada arquivo) antes de rodar; pasta diferente do manifesto → o harness para. Nada do corpus é executado.

**Anotações** em `eval/questions/<corpus>.yaml` (SnakeYAML já vem com o Spring Boot; confirmar na F0): pergunta, idioma, categoria (`08` §2), dificuldade, intervalos esperados (`path`, `startLine`, `endLine`), `mustAbstain`, notas. **Relevância por intervalo de linhas, não por id de chunk**: um chunk é relevante se sobrepõe um intervalo esperado. Isso deixa o corpus válido quando o chunking muda (O-010).

**Tamanho inicial:** cerca de 40 perguntas, metade em pt-BR e metade em en, porque pergunta em português sobre código em inglês é justamente onde o lexical deve perder. Perguntas sem resposta ficam fora das métricas de recuperação e servem à Etapa 6.

**Quem anota:** eu rascunho perguntas e intervalos lendo o código; você revisa e aprova cada uma (`08` §4 pede auditoria humana). Um teste no `verify` valida que as anotações da fixture apontam para arquivos e linhas existentes.

**O-010 (chunking):** o harness já mede qualquer chunking (relevância por linha), mas nesta etapa só roda `lines-v1`. Comparar variantes fica para a Etapa 8 ("melhorar ranking/chunking apenas com evidência"), com a O-010 atualizada.

### P8 — Harness, métricas e bake-off → F6, F7

**Proposta:**
- `RetrievalEvaluationIT`, tag `eval`, fora do `verify` (como `performance` e `smoke`): indexa cada corpus pelo pipeline real (Testcontainers), roda cada pergunta nos canais uma vez e calcula as variantes da fusão em memória (P5).
- Métricas (`08` §3): Recall@5/10/20 por intervalo esperado, MRR@10, Precision@10, cobertura de arquivos nas perguntas de fluxo entre módulos, latência por canal (média e p95); tudo por modo, por categoria e por idioma. Cálculo das métricas em classe pura, com testes unitários no `verify`.
- **Reprodutibilidade (aceite):** ordem determinística em todos os canais; vetores do corpus e das perguntas guardados num cache em arquivo em `DATA_DIR/eval-cache/`, por hash e modelo, para rodar de novo sem pagar e sem variação do provedor; o resultado registra corpus (commit e hash do manifesto), versões de chunking e lexical, modelo e parâmetros da fusão. Rodar duas vezes gera métricas idênticas (fora a latência), verificado no fechamento.
- Resultados em `eval/results/<data>-<config>.json` e um resumo em Markdown, versionados.
- Sem metas numéricas (`08` §9): a primeira medição é o baseline.
- **Bake-off de embeddings (O-004), condicional:** só com seu ok, saldo e chave. Exige um adaptador Voyage (confirmar na F0 se o Spring AI 2.0.x tem módulo; senão, HTTP próprio em `providers`), mais uma variável de chave e mais um destino dos dados (`07` §7). Recomendo **adiar** o bake-off: o corpus inicial é pequeno para separar dois bons modelos, e o harness fica pronto para rodá-lo depois sem mudar nada. Nesse caso a O-004 continua provisória com a OpenAI.

### P9 — `spring-ai-openai` ou só o SDK (pendência da D-047)

**Proposta:** manter como está nesta etapa e decidir na Etapa 6, junto com a porta de geração: é lá que o `ChatClient` do Spring AI pode (ou não) se pagar. A D-047 é atualizada com o novo prazo.

## Princípios de execução

- Valem os das etapas anteriores: fatias entregáveis, `./mvnw verify` verde ao fim de cada uma, `openapi.json` regenerado e commitado, `@Tag`/`@Operation` em todo endpoint alterado.
- SQL explícito e testes de integração com Testcontainers para toda consulta nova (D-016); termos da pergunta sempre como parâmetro.
- `verify` sem provedor real nem chave; o harness e as medições ficam em tags fora dele.
- Nem pergunta nem texto de chunk em log.
- ArchUnit: `retrieval` e `indexing` podem depender de `codeanalysis`; `codeanalysis` continua sem depender de módulos de domínio.

## Fatias, na ordem

### F0 — Verificações (sem código de produção)
- `ts_debug('simple', …)` para os casos da P1; comportamento de `to_tsquery` com termos escapados; `word_similarity` e limiar em caminhos.
- SnakeYAML disponível sem dependência nova.
- Candidatos do repositório open source (licença, tamanho, linguagem) e commit fixo; commit do Codebase Copilot para o corpus.
- Estimativa de tokens e custo do corpus com o preço da D-047.
- Se o bake-off for aprovado: integração Voyage no Spring AI 2.0.x, preço e política de dados em fonte oficial.

**Resultado (16/10/2026)**

Verificado num contêiner descartável `pgvector/pgvector:pg17` (PostgreSQL 17.11, `pg_trgm` 1.6, `vector` 0.8.7, `datctype` `en_US.utf8`), a mesma imagem do Compose e dos testes.

*Parser `simple` (`ts_debug`), casos da P1:*

| Entrada | Tokens do parser | Lexemas |
|---|---|---|
| `user_id` | `user`, `_` (branco), `id` | `user`, `id` (nunca `user_id`) |
| `a.b.C` | `file` | `a.b.c` |
| `src/foo/Bar.java`, `apps/backend/pom.xml` | `file` | o caminho inteiro, em minúsculas |
| `https://example.com/api/v1?id=2` | `protocol`, `url`, `host`, `url_path` | `example.com/api/v1?id=2`, `example.com`, `/api/v1?id=2` |
| `1.2.3` | `version` | `1.2.3` |
| `dev@example.com` | `email` | `dev@example.com` |
| `my-file.ts` | `host` | `my-file.ts` (sem as partes) |
| `kebab-case-name` | `asciihword` + partes | `kebab-case-name`, `kebab`, `case`, `name` |
| `getUserById`, `HTTPClient`, `parseJSON2Xml` | `asciiword`/`numword` | a forma unida em minúsculas, sem partes |
| `$scope`, `foo::bar`, `x->y`, `"quoted"` | o símbolo vira branco | `scope`; `foo`, `bar`; `x`, `y`; `quoted` |
| `ação_calculada` | `word`, `_`, `asciiword` | `ação`, `calculada` |

Consequências para a P1:
- A regra "tokens de uma parte não entram em `identifier_terms` (já estão no `content`)" **não vale** para tokens colados por `.`, `/`, `@` ou `-` em host: em `src/foo/Bar.java`, `repo.find`, `a.b.C`, `my-file.ts`, `dev@example.com` e `1.2.3`, as partes (`bar`, `java`, `find`…) não existem no `tsv` do conteúdo. **Ajuste (incorporado à proposta):** um token de uma parte entra em `identifier_terms` quando está colado a outro por `.`, `/`, `@` ou `-` (sem espaço). Emitir a mais é inofensivo, porque os termos são únicos por chunk.
- O original com `_` (`user_id`) não serve para nada: o parser o quebra de novo em `user` e `id`. **Ajuste (incorporado à proposta):** emitir só as partes, sem a forma com `_`. A forma unida de camelCase (`getuserbyid`) continua: ela já está no conteúdo (peso B), e o peso A é o que faz o identificador valer mais que a prosa.
- `$` some no parser; fica como separador.
- Teste incluído na F1, além dos unitários: um teste de integração confere que cada termo que o tokenizador produz aparece como lexema em `to_tsvector('simple', identifier_terms)`, para o tokenizador e o parser não se desalinharem.

*`tsv` (P2):* `array_to_tsvector` gera lexemas **sem posição**; com ele, o `setweight` não tem efeito e o `ts_rank_cd` ignora os termos (rank 0 no caso `bar | java`, contra 0,80 com `to_tsvector`). O `tsv` precisa vir de `setweight(to_tsvector('simple', identifier_terms), 'A') || setweight(to_tsvector('simple', content), 'B')`, como está na P2. Confirmado.

*`tsquery` (P3):*
- Sem aspas, os caracteres especiais dão erro: `a & (` e `!` → `no operand in tsquery`; `foo:bar` → `syntax error in tsquery`.
- Com cada termo entre aspas simples (aspas internas dobradas), nunca dá erro, mas o parser roda **dentro** das aspas: `'a&b'` vira `'a' <-> 'b'` (frase) e `'user_id'` vira `'user' <-> 'id'`. Como os termos do tokenizador só têm letras e dígitos (com o ajuste acima), isso não acontece na prática.
- Termo só com pontuação (`'&&'`) → `tsquery` vazia, com `NOTICE`, e nenhum resultado. A aplicação deve tratar a pergunta sem termos antes do SQL (já previsto na P3).
- **Usar `to_tsquery('simple', :q)`, não o cast `:q::tsquery`:** o cast não normaliza, e as minúsculas do Java diferem das do PostgreSQL (`İ` vira `i̇` com `Locale.ROOT` e `i` no banco). Com `to_tsquery`, pergunta e conteúdo passam pela mesma normalização; as minúsculas do Java ficam só para tirar repetidos e stopwords.
- `websearch_to_tsquery('simple', 'a or b')` também monta OR, mas reinterpreta `-` e aspas da pergunta. A montagem na aplicação continua sendo a escolha.

*Caminhos (P4), nos 273 caminhos deste repositório:*

| Termo | Arquivo esperado | Vizinho mais próximo (`word_similarity` / `strict_word_similarity`) |
|---|---|---|
| `VectorSearch.java` | 1,000 / 1,000 | `VectorSearchPerformanceIT.java`: 0,667 / 0,531 |
| `IndexJob` | 1,000 / 1,000 | `IndexJobApiIT.java`: 0,889 / 0,533 |
| `ProviderProperties` | 1,000 / 1,000 | `ProviderPropertiesTest.java`: 0,944 / 0,773 |
| `etapa-4.md` | 1,000 / 1,000 | `etapa-3.md`: 0,692 / 0,692 |
| `pom.xml` | 1,000 / 1,000 | 0,125 / 0,091 |
| `search` (não é caminho) | — | `SearchApiIT.java`: 0,857 / 0,462 |

- O `word_similarity` é permissivo demais (0,889 e 0,944 para outros arquivos). **Ajuste (incorporado à proposta):** `strict_word_similarity`, com o operador `<<%` (usa o índice; limiar padrão 0,5) como pré-filtro e `strict_word_similarity(:term, relative_path) >= :threshold` no SQL, com o limiar como parâmetro (não pela GUC, que exigiria `SET` por sessão). Limiar inicial **0,8**, ajustado na avaliação.
- Com 54.600 caminhos (200 snapshots), o plano usa o GIN trigram (`Bitmap Index Scan`) e roda em 8 ms. O índice não filtra por snapshot: as linhas de outros snapshots saem no `Filter` (1.599 de 1.600 no teste). Aceitável com a poda de snapshots; medido de novo no teste de desempenho da F4.

*SnakeYAML:* `org.yaml:snakeyaml` 2.6 já vem, em escopo `compile`, pelo `spring-boot-starter` 4.1.1 (versão gerenciada pelo BOM do Boot). Sem dependência nova; a F5 a declara explicitamente no `pom.xml`, sem versão, para não depender de uma transitiva.

*Corpus (P7):*
- **Codebase Copilot:** commit `9d986db5897aae9c71c7e37e3f98288ea30b884e` (merge da Etapa 4 em `main`), 274 arquivos, 1,36 MB de texto. Extração: `git archive 9d986db | tar -x -C <pasta fora do repositório>`.
- **Open source**, pela API do GitHub em 16/10/2026 (tamanho = soma dos arquivos de texto da árvore na tag):

| Repositório | Linguagem | Licença | Tag e commit | Arquivos | Texto | Observação |
|---|---|---|---|---|---|---|
| **`pallets/click`** (recomendado) | Python | BSD-3-Clause | `8.5.0`, `8b19813f2bfca99f1018a587a8cf54fc959f2e5d` | 161 | 1,50 MB (442 kB em `src/`, 469 kB de testes, 206 kB de docs) | Fluxos entre módulos claros (decorators → core → parser → types), docs boas para perguntas conceituais |
| `psf/requests` | Python | Apache-2.0 | `v2.34.2`, `6e83187b8feb273ed4c6cdab5efd8d54901dfab3` | 123 | 2,85 MB, quase todo um logo `.ai` (217 kB em `requests/`) | Código pequeno; boa alternativa se o click for grande para anotar |
| `sindresorhus/ky` | TypeScript | MIT | `v2.2.0`, `3541888878c4d303a8bc2ad61815f4e048f55a5d` | 113 | 1,32 MB (195 kB em `source/`, 871 kB de testes) | Dois terços são testes; menos fluxos para perguntar |

*Custo estimado do corpus* (Copilot + click + fixture, ~2,9 MB de texto; ×1,2 pela sobreposição de 10 em 60 linhas da D-043 → ~3,5 M caracteres), com US$ 0,02 por 1 M de tokens (D-047):
- teto, contando um token por caractere (a conta da D-054): ~3,5 M tokens → **~US$ 0,07**;
- esperado, com ~4 caracteres por token em código: ~0,9 M tokens → **~US$ 0,02**;
- perguntas: 40 × ~30 tokens, desprezível. Com o cache de vetores (P8), uma reindexação ou uma nova rodada do harness não custa de novo. Arquivos excluídos pela varredura reduzem o total.

*Bake-off (P8):* não verificado. A P8 recomenda adiar, e a verificação da Voyage só entra se você aprovar o bake-off.

### F1 — Tokenizador de identificadores
- `IdentifierTokenizer` e stopwords em `codeanalysis`; regra nova no ArchUnit.
- **Testes:** camelCase, PascalCase, snake_case (só as partes), kebab-case, siglas (`HTTPClient`, `parseJSON`), dígitos, `$`, Unicode, termos repetidos, tokens de uma parte soltos (não entram) e colados por `.`, `/`, `@` ou `-` (entram), caminhos, e-mails e versões, pergunta só com stopwords, mesma saída para o mesmo texto; teste de integração de alinhamento com o parser (P1).

**Como foi entregue (16/10/2026)**
- `IdentifierTokenizer` (`identifierTerms`, `queryTerms`, `VERSION = "ident-v1"`) e `Stopwords` em `codeanalysis`; registrado na D-055.
- ArchUnit: `codeanalysis` não depende de nenhum outro módulo de domínio (a regra antiga só barrava `importjob`).
- Desvios da P1, todos apontados pelo teste de alinhamento ou pelo parser:
  - **"Colada" vale para qualquer símbolo sem espaço**, não só `.`, `/`, `@` e `-`: o parser também junta o que vem depois de `?`, `=` e `&` numa URL (`/api/v1?id=2` vira um lexema só), e uma lista que imitasse o parser ficaria sempre incompleta.
  - **Palavras entre `<` e `>` sempre entram:** o parser lê `<String>`, `<T extends Number>`, `<b && c>` (em `if (a<b && c>d)`), atributos HTML e `<!-- … -->` como tag e não gera lexema nenhum. A F0 não tinha testado esses casos.
  - **Os termos guardam a grafia original**, não minúsculas: o PostgreSQL normaliza no `to_tsvector`/`to_tsquery`, e assim a normalização é uma só (P1, `İ`).
- Correção à parte: o `ErrorHandlingTest` (`@WebMvcTest`) não fixava `app.embeddings.provider`, e a guarda da D-047 o derrubava quando o `.env` local tinha `openai`. Agora fixa `none`.

### F2 — Índice lexical (V9)
- `chunk_lexical`, GIN em `tsv`, GIN trigram em `snapshot_files.relative_path`, `snapshots.lexical_version`, etapa `LEXICAL` em `index_jobs_stage_valid`.
- Etapa `LEXICAL` no job; `lexical_version` na ativação; `outdatedReasons` com `LEXICAL_INDEX_MISSING`/`LEXICAL_INDEX_CHANGED`; `lexical` no detalhe do snapshot.
- **Testes:** job novo preenche todos os chunks do snapshot; reindexação sem mudança não regrava nada; snapshot antigo sem índice lexical é preenchido por reindexação sem arquivo alterado; retomada do job no meio da etapa chega ao mesmo resultado; remoção do projeto apaga as linhas em cascata; funciona com `provider=none`.

**Como foi entregue (16/10/2026)**
- V9 como no esboço; `ChunkLexicalRepository` e `LexicalStage` em `indexing`; `snapshots.lexical_version` gravada na ativação; registrado na D-056.
- `lexical: { version } | null` no snapshot (detalhe e listagem); `LEXICAL` em `stage`; `LEXICAL_INDEX_MISSING` e `LEXICAL_INDEX_CHANGED` em `outdatedReasons`; `openapi.json` regenerado.
- Lote da etapa configurável (`app.indexing.lexical-batch-chunks`, padrão 500), para o teste interromper o job entre dois lotes.
- `LexicalIndexingIT` roda num contexto com `provider=none` e um spy no repositório lexical: cobre todos os testes da fatia (reindexação sem mudança verificada pelo `xmin` das linhas). `IndexSchemaIT` cobre a etapa nova e a cascata.
- `409 SNAPSHOT_LEXICAL_MISMATCH` fica para a F4, junto com o `mode`.

### F3 — Busca lexical e caminhos
- `LexicalSearch` e `PathSearch` em `retrieval`, com o trecho de candidatos compartilhado com `VectorSearch`.
- **Testes:** `getUserById` encontra pela forma original, por "user by id" e por "get user"; termos com caracteres especiais da `tsquery` (`&`, `|`, `!`, `:`, `'`, `(`) não quebram a consulta; isolamento por projeto e snapshot; um resultado por chunk com `otherPaths`; nome de arquivo na pergunta traz os chunks desse arquivo; desempate estável; plano de execução usa os índices GIN com volume (como o `ChunkEmbeddingPlanIT`).

**Como foi entregue (16/10/2026)**
- `LexicalSearch` e `PathSearch` em `retrieval`; `SnapshotChunks` com o SQL compartilhado pelos três canais (a `VectorSearch` passou a usá-lo, sem mudar o resultado); `SearchHit` substitui `VectorSearch.Hit`. `PathTerms` em `codeanalysis`. Registrado na D-057 e na D-058. O endpoint não muda nesta fatia (F4).
- **Testes:** `LexicalSearchIT` (formas de `getUserById`, inclusive em pt-BR e em maiúsculas; operadores e injeção na pergunta; pergunta só com stopwords; isolamento por snapshot e projeto; `otherPaths`; desempate repetível; peso A acima da prosa; `limit`), `PathSearchIT` (arquivo citado em ordem de linhas, sem o vizinho `VectorSearchPerformanceIT.java`; nome de arquivo sem extensão; isolamento; `otherPaths`; limiar inválido), `PathTermsTest` e `LexicalSearchPlanIT`.
- **Desvios:**
  - **Planos de execução:** com 100 projetos e 200 arquivos por snapshot, nenhuma das duas buscas usa o índice GIN: o PostgreSQL parte do snapshot pela PK, o que é melhor nesse volume (só lê o snapshot pedido). O teste garante a ausência de varredura sequencial, não o uso do GIN; os índices ficam para snapshots grandes, e a F4 mede a latência com 50 mil chunks.
  - **Limiar de caminhos:** com diretórios em comum, `docs/plans/etapa-3.md` passa de 0,8 para o termo `docs/plans/etapa-4.md` (o F0 mediu só o nome do arquivo, 0,692). O arquivo exato vem primeiro; o limiar é revisto na avaliação.
  - Um nome sem extensão só vira termo de caminho se algum arquivo do snapshot tiver esse nome (consulta extra, dentro do snapshot), e palavras com menos de 3 caracteres ou stopwords nunca viram.

### F4 — Fusão e contrato
- `RrfFusion` pura; `mode` no endpoint; `ranks` na resposta; códigos novos (`SNAPSHOT_LEXICAL_MISMATCH`) com mensagens pt-BR; `openapi.json`.
- Teste de desempenho (tag `performance`): latência lexical e híbrida com 50 mil chunks, e tamanho de `chunk_lexical`; resultado registrado aqui.
- **Testes:** RRF com listas conhecidas (cálculo, desempate, item em um só canal, lista vazia); cada modo com o fake; `LEXICAL` com `provider=none` não chama o provedor; precedência dos erros por modo; `mode` inválido → `400`; padrão `HYBRID`; a pergunta não aparece nos logs.

**Como foi entregue (16/10/2026)**
- `RrfFusion` pura em `retrieval` (canais `LEXICAL`, `VECTOR`, `PATH`; `k`, pesos e `limit` como parâmetros); `SearchMode`; `RetrievalProperties` (`app.retrieval.candidates`, padrão 50); `SearchSnapshots` com o estado do snapshot (saiu da `VectorSearch`, agora com `lexical_version`). Registrado na D-059 e na D-060; D-053 marcada como revista.
- `mode` no endpoint (padrão `HYBRID`), `mode`, `model | null` e `lexicalVersion | null` na resposta, `ranks` por resultado; `SNAPSHOT_LEXICAL_MISMATCH` com mensagem pt-BR; a mensagem de `EMBEDDINGS_NOT_AVAILABLE` sugere o modo `LEXICAL`; `openapi.json` regenerado.
- **Testes:** `RrfFusionTest` (cálculo, item num só canal, empate por rank e por caminho, linha e id, pesos e `k`, listas vazias, `limit`, repetibilidade, parâmetros inválidos); `SearchServiceTest` (sem provedor: modos vetoriais recusados, padrão `HYBRID`, `LEXICAL` sem tocar nos canais vetorial e de caminhos, precedência); `SearchApiIT` (cada modo com o fake e seus `ranks`, arquivo citado entra pelo canal de caminhos, `mode` ausente ou `null`, `mode` inválido, matriz de precedência por modo, `LEXICAL` com o provedor quebrado, pergunta só com stopwords, log sem a pergunta); `SearchWithoutProviderIT` (`provider=none` de ponta a ponta).
- **Desvios:**
  - `mode` é texto no pedido, e não enum, para um valor desconhecido dar `400 VALIDATION_FAILED` no campo, como pede a P6 (com enum, o Jackson responderia `MALFORMED_REQUEST`). Só os nomes exatos valem (`lexical` é recusado).
  - No `HYBRID`, cada canal busca `max(candidates, limit)`, para um `limit` acima de `candidates` não devolver menos resultados.
  - O canal de caminhos roda só no `HYBRID`; os modos `LEXICAL` e `VECTOR` são um canal cada.
  - Um teste da fusão pegou que `new EnumMap<>(map)` falha com mapa vazio; a fusão percorre os canais na ordem do enum.

**Desempenho** (`HybridSearchPerformanceIT`, tag `performance`, 5.000 arquivos × 10 chunks = 50 mil chunks de ~300 palavras sintéticas, ~1,9 kB cada, vocabulário de 5.000 palavras com distribuição enviesada, 40 termos de identificador por chunk, 1.536 dimensões; 50 execuções, PostgreSQL do Testcontainers no Mac de desenvolvimento):

| Medida | Média | p95 | Máx. |
|---|---|---|---|
| Lexical, só termos raros (`limit` 10) | 52 ms | 86 ms | 100 ms |
| Lexical, com um termo presente em quase todo chunk (`limit` 10) | 471 ms | 674 ms | 742 ms |
| Híbrido: 3 canais × 50 candidatos + RRF, sem a chamada ao provedor | 854 ms | 1.212 ms | 2.031 ms |

- `chunk_lexical`: 50 mil linhas, **211 MB de tabela + 51 MB de índices**, contra 78 MB de `chunks`. O `tsv` com posições ocupa ~2,7× o chunk.
- **Onde vai o tempo do lexical:** no pior caso, ~32 mil linhas casam com o `@@`; ler o `tsv` delas (TOAST) leva ~100 ms e calcular `ts_rank_cd` em todas antes do `LIMIT` leva ~300 ms (`EXPLAIN ANALYZE` no teste). Não é o plano (com um snapshot só no banco, a varredura sequencial é a escolha certa): é o custo de ranquear todos os casamentos de um termo comum, a falta de IDF da D-017. Opções para a Etapa 8, se a avaliação ou o uso real pedirem: descartar da consulta termos presentes em grande parte do snapshot, `ts_rank` no lugar de `ts_rank_cd`, ou índice RUM/BM25 (O-005). Nada mudou nesta fatia.
- O híbrido soma os três canais (o vetorial exato mediu 213 ms de média com `limit` 10 na Etapa 4, D-049; não foi medido separado aqui) e ainda precisa da chamada ao provedor. O paralelismo entre canais (P5) também fica para quando a medição real pedir.

### F5 — Corpus
- Fixture, manifestos, formato YAML e anotações (rascunho meu, revisão sua). Esta fatia depende de você e pode começar em paralelo assim que o plano for aprovado.
- **Testes:** validador das anotações (arquivo existe, linhas dentro do arquivo, categoria e idioma conhecidos) no `verify` para a fixture; manifesto divergente faz o harness parar.

### F6 — Harness e baselines
- `RetrievalEvaluationIT`, métricas, cache de vetores, relatório.
- Baseline lexical (sem custo); vetorial e híbrido com o provedor real quando houver saldo, junto com o teste de fumaça da Etapa 4.
- **Testes:** métricas com rankings conhecidos (Recall@k, MRR, Precision@k, cobertura, sobreposição de intervalo, `otherPaths` contando como o mesmo arquivo); duas execuções com o cache geram o mesmo resultado.

### F7 — Fechamento
- Aceite: resultados reprodutíveis e híbrido comparado com os baselines individuais.
- Bake-off, se aprovado na P8.
- Decisões: D-017 confirmada ou revista com os números; O-005 atualizada (o que foi medido, o que falta); O-011 resolvida; O-010 e O-004 com o novo estado; D-047 (P9), D-053 (P6).
- Docs: `04` §6, `05` (`chunk_lexical`, `snapshots`, §4), `06` (busca), `08` (corpus e processo reais), `09` (status), README (harness, como montar o corpus, custo).
- Seção "Resultado e desvios" neste arquivo.

## Migração

### V9 (esboço)

```sql
CREATE TABLE chunk_lexical (
    chunk_id         uuid     NOT NULL
                              REFERENCES chunks (id) ON DELETE CASCADE,
    lexical_version  text     NOT NULL,
    identifier_terms text     NOT NULL,
    tsv              tsvector NOT NULL,
    PRIMARY KEY (chunk_id, lexical_version)
);
CREATE INDEX chunk_lexical_tsv_idx ON chunk_lexical USING gin (tsv);

CREATE INDEX snapshot_files_path_trgm_idx
    ON snapshot_files USING gin (relative_path gin_trgm_ops);

ALTER TABLE snapshots ADD COLUMN lexical_version text;

ALTER TABLE index_jobs
    DROP CONSTRAINT index_jobs_stage_valid,
    ADD CONSTRAINT index_jobs_stage_valid CHECK (stage IN
        ('SCANNING', 'PROCESSING_FILES', 'LEXICAL', 'EMBEDDING', 'FINALIZING'));
```

- `tsv` calculado no `INSERT` a partir de `identifier_terms` (aplicação) e de `chunks.content` (SQL), na mesma instrução.
- Snapshots existentes ficam com `lexical_version` nulo até a próxima indexação (P2).
