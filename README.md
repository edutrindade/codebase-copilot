# Codebase Copilot

Ferramenta local-first que responde perguntas sobre um repositório com evidências verificáveis (arquivo + linhas). A documentação do produto e da arquitetura está em [`docs/`](docs/README.md), e as decisões técnicas em [`docs/10-DECISIONS.md`](docs/10-DECISIONS.md).

> Estado atual: Etapa 6 do [roadmap](docs/09-IMPLEMENTATION-ROADMAP.md) concluída (respostas com evidências). Só o backend existe; o frontend (`apps/web`) entra na Etapa 7.

## Pré-requisitos

- **JDK 25** no `PATH` (`java -version` deve mostrar 25).
- **Docker** com Docker Compose (para o PostgreSQL 17 + pgvector e para os testes de integração com Testcontainers).
- **Node.js 22.22.2 ou mais novo** e npm, para o frontend (`apps/web`, D-070).

O Maven não precisa ser instalado: use o wrapper `./mvnw`.

## Configuração

Copie o arquivo de exemplo e troque a senha:

```bash
cp .env.example .env
```

| Variável | Uso |
|---|---|
| `DB_NAME` | Nome do banco |
| `DB_USER` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco (troque o valor de exemplo) |
| `DB_PORT` | Porta do PostgreSQL no host |
| `PROJECTS_ROOT` | Opcional (a partir da Etapa 1). Caminho absoluto da pasta sob a qual ficam os repositórios que podem ser importados; gera a raiz permitida de key `default` (D-029). Vazio: só o fluxo ZIP fica disponível. |
| `PROJECTS_ROOT_LABEL` | Opcional. Rótulo da raiz `default` na UI. Padrão: "Projetos". |
| `DATA_DIR` | Opcional. Diretório de dados da aplicação; imports ZIP são extraídos em `DATA_DIR/imports/<id>/source` (D-028, D-033). Padrão: `~/.codebase-copilot`; um `~` inicial é expandido pela aplicação. |
| `EMBEDDING_PROVIDER` | Opcional. Provedor de embeddings: `none` (padrão; nada é enviado), `fake` (testes e demonstração) ou `openai` (D-047). |
| `EMBEDDING_API_KEY` | Chave da API do provedor; obrigatória com `openai`. Nunca aparece em logs nem em mensagens de erro. |
| `GENERATION_PROVIDER` | Opcional. Provedor das respostas: `none` (padrão; nada é enviado e não há respostas), `fake` (testes e demonstração) ou `openai` (D-063). |
| `GENERATION_API_KEY` | Chave da API do provedor de respostas; obrigatória com `openai`. Pode ter o mesmo valor de `EMBEDDING_API_KEY`, mas é configurada à parte. |
| `GENERATION_PRICE_INPUT_PER_MILLION` / `GENERATION_PRICE_OUTPUT_PER_MILLION` | Opcionais. Preço do modelo em US$ por 1M de tokens, para estimar o custo de cada resposta. Sem os dois, o custo fica `UNKNOWN`. |
| `SERVER_ADDRESS` | Opcional. Endereço em que o backend escuta. Padrão: `127.0.0.1`, só a própria máquina, porque a API não tem autenticação (D-072). |

A porta do banco é definida uma única vez, em `DB_PORT` no `.env`, e usada em dois lugares: o Compose publica o container em `127.0.0.1:${DB_PORT}`, e o backend se conecta em `localhost:${DB_PORT}` (`application.yml`). Se a porta 5432 já estiver ocupada, altere apenas `DB_PORT`.

### Raízes permitidas e diretório de dados

O backend só lê pastas locais dentro das raízes permitidas; a UI navega nelas e envia apenas caminhos relativos (D-018). Na inicialização, a aplicação valida cada raiz (caminho absoluto, existente, diretório legível; key única no formato `[a-z0-9-]+`) e não inicia se alguma for inválida.

Para configurar **várias raízes**, copie o exemplo versionado e ajuste os caminhos:

```bash
cp apps/backend/config/application.yml.example apps/backend/config/application.yml
```

O `config/application.yml` é carregado automaticamente pelo Spring ao executar a partir de `apps/backend` e é ignorado pelo Git. A lista substitui por inteiro a raiz `default` gerada por `PROJECTS_ROOT`; o exemplo já a repete. Os testes não dependem desse arquivo: cada teste declara as próprias raízes.

O `DATA_DIR` precisa ficar **fora** de todas as raízes permitidas (sem estar dentro de uma delas nem contê-la) e fora deste repositório; caso contrário, a aplicação não inicia. Se não existir, é criado na inicialização.

Cada import ZIP usa a pasta `DATA_DIR/imports/<importJobId>`. A árvore extraída do import atual de cada projeto é mantida como origem do projeto; as demais são apagadas quando o import falha, é substituído ou o projeto é removido, e pastas órfãs são limpas na inicialização (D-033).

### Limites de importação e inventário

Os padrões são hipóteses a ajustar com medições e podem ser alterados por propriedades do Spring (por exemplo, em `config/application.yml`):

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.import.max-zip-bytes` | 100MB | Tamanho máximo do ZIP enviado; os limites do multipart derivam dele (`413 UPLOAD_TOO_LARGE`). |
| `app.import.max-entries` | 50000 | Entradas por ZIP. |
| `app.import.max-uncompressed-bytes` | 1GB | Total descompactado por ZIP. |
| `app.import.max-entry-bytes` | 100MB | Tamanho descompactado por entrada. |
| `app.import.max-compression-ratio` | 100 | Razão de compressão máxima, verificada a partir de `app.import.compression-ratio-min-bytes` (1MB). |
| `app.import.max-depth` / `max-path-length` | 64 / 1024 | Profundidade e comprimento dos caminhos no ZIP. |
| `app.import.max-concurrent` | 2 | Extrações simultâneas. |
| `app.inventory.max-entries` / `max-depth` | 200000 / 64 | Limites da varredura do inventário. |
| `app.inventory.max-file-bytes` / `max-project-bytes` | 1MB / 500MB | Padrões por projeto, ajustáveis em `PUT /projects/{id}/settings` até `max-file-bytes-limit` (10MB) e `max-project-bytes-limit` (2GB). |
| `app.browse.max-directories` | 1000 | Itens por listagem de diretórios. |

### Indexação

A indexação (`POST /api/v1/projects/{id}/index-jobs`) lê os arquivos incluídos pela varredura e grava o conteúdo, os chunks e os snapshots no PostgreSQL (`file_contents`, `chunks`, `snapshot_files`, `snapshots`); nada é gravado no `DATA_DIR`. Remover o projeto apaga tudo em cascata. Os jobs ficam numa fila em tabela e são retomados após uma queda (D-040).

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.indexing.max-concurrent` | 1 | Jobs de indexação simultâneos. |
| `app.indexing.poll-interval` | 2s | Intervalo de sondagem da fila. |
| `app.indexing.heartbeat-interval` | 10s | Renovação do lease de um job em execução. |
| `app.indexing.lease-timeout` | 60s | Tempo sem heartbeat após o qual um job `RUNNING` volta à fila. |
| `app.indexing.max-attempts` | 3 | Tentativas por job antes de falhar. |
| `app.indexing.batch-files` | 100 | Arquivos gravados por transação. |
| `app.indexing.retained-snapshots` | 3 | Snapshots anteriores mantidos com arquivos, além do ativo. |

### Embeddings

A indexação gera um vetor por chunk e a busca (`POST /api/v1/projects/{id}/search`) compara a pergunta com os chunks do snapshot (D-047 a D-054). O provedor vem de `EMBEDDING_PROVIDER`:

- `none` (padrão): **nada sai da máquina.** Os snapshots são criados sem vetores e a busca responde `409 EMBEDDINGS_NOT_AVAILABLE`.
- `fake`: vetores calculados localmente a partir dos termos do texto, sem qualidade semântica. Serve para testes e demonstração.
- `openai`: modelo `text-embedding-3-small` da OpenAI. Exige `EMBEDDING_API_KEY` no `.env`; sem ela, a aplicação não inicia.

**O que é enviado com `openai`:** o texto dos chunks de arquivos indexados que ainda não têm vetor do modelo (o cache por conteúdo vale para todos os projetos) e o texto de cada pergunta de busca. Arquivos excluídos (`.env`, chaves, dependências etc.), binários, não suportados ou com falha nunca são enviados. Pela política da OpenAI consultada em 16/10/2026, os dados de `/v1/embeddings` não são usados para treino e ficam retidos por até 30 dias para monitoramento de abuso. Detalhes em `docs/07-SECURITY-PRIVACY-COSTS.md` §7 e §9.

**Custo:** US$ 0,02 por 1M de tokens (preço oficial de 16/10/2026). Os tokens reportados pelo provedor ficam em `embedding.tokens` do snapshot (`GET /api/v1/snapshots/{id}`); reindexar sem mudanças não envia nada.

**Trocar de modelo** (`app.embeddings.model` ou `dimensions`): o projeto passa a informar `EMBEDDING_MODEL_CHANGED` e a busca responde `409 SNAPSHOT_EMBEDDING_MISMATCH` até a próxima indexação, que gera os vetores do modelo novo.

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.embeddings.model` | `text-embedding-3-small` | Modelo do provedor. Com `openai`, só `text-embedding-3-small`, `text-embedding-3-large` e `text-embedding-ada-002`. |
| `app.embeddings.dimensions` | 1536 | Dimensão dos vetores, de 1 a 2.000. |
| `app.embeddings.batch-size` | 64 | Textos por chamada (até 75 com `openai`). |
| `app.embeddings.max-retries` | 4 | Repetições de limite de taxa e indisponibilidade dentro do job. |
| `app.embeddings.max-retry-wait` | 60s | Teto de cada espera entre repetições, inclusive com `Retry-After` maior. |
| `app.embeddings.max-chunks-per-job` | 50000 | Chunks a enviar por job; acima disso, o job falha com `EMBEDDING_CHUNK_LIMIT_EXCEEDED` sem chamar o provedor. |
| `app.embeddings.timeout` | 30s | Tempo máximo de uma chamada. |
| `app.embeddings.base-url` | `https://api.openai.com/v1` | Endereço da API do provedor. |

**Atenção:** `app.embeddings.base-url` decide para onde vão a chave e os trechos de código. Mudar esse endereço muda o destino dos dados. Só são aceitos endereços `https`; `http` é aceito apenas para `localhost` ou `127.0.0.1` (servidor de teste ou servidor local). Qualquer outro valor, ou um endereço com usuário e senha embutidos, impede a aplicação de iniciar.

### Respostas

`POST /api/v1/projects/{id}/questions` busca trechos do snapshot, envia ao modelo os que cabem no contexto e devolve a resposta com as evidências citadas e validadas (arquivo e linhas). Perguntas, respostas e evidências ficam no histórico (`GET /api/v1/projects/{id}/conversations`); detalhes em `docs/06-API-CONTRACTS.md` §8 a §10. O provedor vem de `GENERATION_PROVIDER`:

- `none` (padrão): **nada sai da máquina** e não há respostas (`409 GENERATION_NOT_AVAILABLE`). A busca continua disponível.
- `fake`: respostas determinísticas que citam a primeira evidência, sem qualidade real. Serve para testes e demonstração.
- `openai`: modelo `gpt-6-luna` da OpenAI. Exige `GENERATION_API_KEY` no `.env`; sem ela, a aplicação não inicia.

**O que é enviado com `openai`:** a cada pergunta, as instruções, a pergunta e os trechos escolhidos como evidência (até 8 por padrão, cerca de 6.000 tokens). Só trechos de arquivos indexados; arquivos excluídos nunca são enviados. Pergunta sem evidência não chama o modelo. Pela política da OpenAI consultada em 16/10/2026, os dados de `/v1/chat/completions` (enviados com `store=false`) não são usados para treino e ficam retidos por até 30 dias para monitoramento de abuso. Com o modo `VECTOR` ou `HYBRID`, a pergunta também vai ao provedor de embeddings. Detalhes em `docs/07-SECURITY-PRIVACY-COSTS.md` §7 e §9.

**Custo:** com o `gpt-6-luna` (US$ 0,10 por 1M de tokens de entrada e US$ 0,50 de saída, preço de 16/10/2026), cerca de US$ 0,0017 por pergunta no teto. Cada resposta traz os tokens e o custo estimado pelo preço configurado em `GENERATION_PRICE_*` (não é a fatura); sem preço, `costStatus` fica `UNKNOWN`.

**Como desligar:** `GENERATION_PROVIDER=none`; nenhuma pergunta ou trecho vai ao provedor de chat. Para que nem as perguntas da busca vetorial saiam da máquina, também `EMBEDDING_PROVIDER=none`. Com embeddings e geração separados, dá para manter as respostas e mandar só `retrievalMode: "LEXICAL"`, sem enviar a pergunta ao provedor de embeddings.

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.generation.model` | `gpt-6-luna` | Modelo do provedor. |
| `app.generation.reasoning-effort` | `low` | Esforço de raciocínio (`none`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`); vazio não envia o parâmetro. Os tokens de raciocínio são cobrados como saída. |
| `app.generation.max-output-tokens` | 2000 | Limite de saída, incluindo o raciocínio. Uma resposta cortada fica `FAILED` com `GENERATION_OUTPUT_TRUNCATED`. |
| `app.generation.timeout` | 60s | Tempo máximo da chamada; o pedido espera por ela. Sem repetição. |
| `app.generation.base-url` | `https://api.openai.com/v1` | Endereço da API, com a mesma regra de `app.embeddings.base-url`. |
| `app.answering.candidates` | 20 | Candidatos da busca considerados para o contexto (1 a 50). |
| `app.answering.max-context-tokens` | 6000 | Orçamento de tokens do prompt (estimado em caracteres/4). |
| `app.answering.max-evidence-per-file` | 3 | Trechos do mesmo conteúdo no contexto. |

O `.env` está no `.gitignore` e nunca deve ser commitado.

## Subir o banco

Na raiz do repositório:

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

As extensões `vector` e `pg_trgm` são criadas pelo Flyway quando o backend inicia.

## Build e testes

```bash
cd apps/backend
./mvnw verify
```

O `verify` executa:

- testes unitários e de integração (os de integração sobem o próprio PostgreSQL via Testcontainers; não dependem do banco do Compose nem do `.env`);
- verificação de formatação com Spotless (google-java-format, estilo AOSP). Para corrigir a formatação: `./mvnw spotless:apply`;
- comparação do contrato OpenAPI gerado com o arquivo versionado `apps/backend/openapi.json` (D-023). Após uma mudança intencional na API, regenere com `./mvnw verify -Dopenapi.update=true` e commite o `openapi.json` junto com o código.

### Formatação no VS Code

O `.vscode/settings.json` formata Java ao salvar com a extensão recomendada `serikb.google-java-format`, usando as mesmas regras do Spotless (`--aosp --skip-sorting-imports --skip-reflowing-long-strings`). A extensão procura o executável no `PATH`; instale-o com:

```bash
brew install google-java-format
```

O formatador padrão do VS Code (Eclipse) não segue o estilo AOSP e faz o `spotless:check` falhar.

### Testes manuais

Alguns testes ficam fora do `verify` (tags `performance`, `smoke` e `eval`) e só rodam com comando explícito, a partir de `apps/backend`, com o Docker no ar.

**Medição da busca vetorial** (`VectorSearchPerformanceIT`, D-049): grava 50 mil chunks sintéticos de 1.536 dimensões e imprime média, p95 e máximo de 50 buscas. Não chama provedor nem precisa de chave.

```bash
./mvnw verify -Dtest.excluded-groups= -Dgroups=performance -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VectorSearchPerformanceIT
```

**Fumaça com o provedor real** (`EmbeddingSmokeIT`, D-047): indexa um repositório de exemplo de quatro arquivos, escrito pelo próprio teste, com `provider=openai`; confere provedor, modelo, dimensão e tokens no snapshot e um vetor para cada chunk; faz uma busca sem palavras em comum com o arquivo esperado e confere que ele vem em primeiro. Imprime data, modelo, chunks, tokens e as latências da indexação e da busca.

```bash
./mvnw verify -Dtest.excluded-groups= -Dgroups=smoke -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=EmbeddingSmokeIT
```

- **Chave:** `EMBEDDING_API_KEY` no `.env` (ou no ambiente). O teste usa `provider=openai` qualquer que seja o `EMBEDDING_PROVIDER` do `.env`, e o endereço padrão da OpenAI.
- **Custo:** algumas centenas de tokens por execução, menos de US$ 0,0001 pelo preço da D-047.
**Fumaça da geração** (`GenerationSmokeIT`, D-063): envia um trecho sintético e uma pergunta ao modelo configurado em `app.generation.model`, com a saída estruturada estrita, e confere status, rótulo e tokens. Imprime modelo, esforço, tokens e latência. Chave em `GENERATION_API_KEY`; custo de frações de centavo.

```bash
./mvnw verify -Dtest.excluded-groups= -Dgroups=smoke -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=GenerationSmokeIT
```

- Só testes com as tags `smoke` e `eval` podem subir com `provider=openai`; em qualquer outro teste, a guarda dos testes (`RealProviderGuard`) impede o contexto de subir.

**Avaliação de recuperação** (`RetrievalEvaluationIT`, D-062): indexa os corpora de `eval/` e mede Recall@k, MRR, Precision@10, cobertura de arquivos e latência das buscas lexical, vetorial e híbrida. Grava os resultados em `eval/results/`. Os corpora externos são baixados à parte (instruções em `eval/README.md`).

```bash
EVAL_CORPUS_CODEBASE_COPILOT=<pasta> EVAL_CORPUS_CLICK=<pasta> \
  ./mvnw verify -Dtest.excluded-groups= -Dgroups=eval -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RetrievalEvaluationIT
```

- **Sem provedor** (padrão, `EVAL_PROVIDER=none`): mede só o lexical e o canal de caminhos; nada sai da máquina.
- **Com `EVAL_PROVIDER=openai`:** envia os corpora e as perguntas à OpenAI (chave de `EMBEDDING_API_KEY`). **Custo** estimado de US$ 0,02 a 0,07 na primeira rodada, pelo preço da D-047. Os vetores ficam em cache em `DATA_DIR/eval-cache`, e as rodadas seguintes não custam nada.

**Avaliação de respostas** (`AnswerEvaluationIT`, D-069): faz as 40 perguntas dos corpora por `POST /questions` e mede validade de citação, respostas sustentadas, citação relevante, abstenção, vazamento, tokens, custo e latência. O `.md` do resultado traz cada resposta ao lado dos trechos citados, para a auditoria humana.

```bash
EVAL_CORPUS_CODEBASE_COPILOT=<pasta> EVAL_CORPUS_CLICK=<pasta> EVAL_GENERATION_PROVIDER=openai \
  ./mvnw verify -Dtest.excluded-groups= -Dgroups=eval -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AnswerEvaluationIT
```

- **Padrão (`EVAL_GENERATION_PROVIDER=fake`):** só testa o harness; nada sai da máquina e o resultado vai para `apps/backend/target/eval-results/`, sem versionar.
- **Com `openai`:** envia perguntas e evidências ao provedor de chat (chave de `GENERATION_API_KEY`); resultado em `eval/results/`. **Custo** estimado abaixo de US$ 0,15 com o `gpt-6-luna`. As respostas ficam em cache em `DATA_DIR/eval-cache/answers`, e uma rodada refeita não paga de novo. Variáveis em `eval/README.md`.

## Executar o backend

```bash
cd apps/backend
./mvnw spring-boot:run
```

**O backend deve ser executado a partir de `apps/backend`.** O `application.yml` importa o `.env` pelo caminho relativo `../../.env`; executado de outro diretório, as variáveis `DB_*` não são encontradas e a aplicação não inicia.

Com o backend no ar:

- Health check: <http://localhost:8080/actuator/health>
- Contrato OpenAPI: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

O backend só atende a própria máquina (D-072): escuta em `127.0.0.1` e recusa com `403` requisições endereçadas a outro nome que não `localhost`/`127.0.0.1`/`[::1]` (`FORBIDDEN_HOST`) e mudanças de estado vindas de páginas fora das origens da UI (`FORBIDDEN_ORIGIN`). As listas ficam em `app.http.allowed-hosts` e `app.http.allowed-origins` (padrão: o Vite em `localhost`/`127.0.0.1`, portas 5173 e 4173); curl e scripts, que não enviam `Origin`, passam.

## Executar o frontend

O frontend (`apps/web`, React + Vite + TypeScript) fala com o backend por um proxy do Vite: o browser só vê a origem da UI, e `/api` é repassado para `http://127.0.0.1:8080` (sem CORS, D-072). Com o backend no ar, em outro terminal:

```bash
cd apps/web
npm ci
npm run dev
```

A UI fica em <http://localhost:5173>. Para usar o build de produção: `npm run build && npm run preview` (<http://localhost:4173>). As duas portas são fixas, porque são as origens que o backend aceita em `app.http.allowed-origins`. Se o backend estiver em outra porta, defina `BACKEND_URL` (por exemplo, `BACKEND_URL=http://127.0.0.1:8081 npm run dev`).

O `.npmrc` desliga os scripts de instalação dos pacotes (`ignore-scripts`) e grava versões exatas (`save-exact`); o `package-lock.json` é versionado.

### Verificação do frontend

```bash
cd apps/web
npm run check
```

É o equivalente ao `./mvnw verify`: tipos (`tsc`), lint (ESLint, que recusa `dangerouslySetInnerHTML` e `console`), formatação (Prettier; para corrigir, `npm run format`), testes (Vitest + Testing Library + MSW, com `axe-core` para acessibilidade) e build. O build injeta uma Content Security Policy sem scripts inline no `index.html`; o servidor de desenvolvimento não a usa, porque o React Refresh depende de um script inline.

Os tipos da API (`src/api/schema.d.ts`) são gerados do `apps/backend/openapi.json` e versionados; um teste falha se estiverem defasados. Depois de regenerar o contrato no backend, rode `npm run api:types` e commite os dois arquivos juntos.

### Teste de aceite (E2E)

Com o banco do Compose no ar:

```bash
cd apps/web
npx playwright install chromium   # uma vez, baixa o browser do Playwright
npm run e2e
```

Fica fora do `npm run check`, como os harnesses do backend. O Playwright faz o build da UI, sobe o backend real na porta 8089 (provedores `fake`, raiz local em `apps/web/e2e/fixtures`, dados em um diretório temporário) e o `vite preview` na 4173, e percorre o fluxo do MVP: cria um workspace, importa o repositório de exemplo `sample-shop`, revisa a pré-análise, indexa, pergunta pelo teclado, abre a evidência no visualizador e reabre a conversa pelo histórico. Cada tela principal passa pelo axe (WCAG 2.1 A e AA, inclusive contraste) e o teste falha com qualquer erro de console. O workspace criado é removido no fim. A porta do backend muda com `E2E_BACKEND_PORT`; a 4173 precisa estar livre, porque é uma das origens aceitas pelo backend.
