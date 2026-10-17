# Corpus de avaliação

Corpus e anotações da avaliação de recuperação (D-061, `docs/08-EVALUATION.md`).

| Caminho | Conteúdo |
|---|---|
| `corpus/fixture/` | Fixture sintética versionada (Java, TypeScript, Python, SQL, Markdown) |
| `corpus/<corpus>.sha256` | Manifesto: SHA-256, quantidade de linhas (`-` se não for texto) e caminho de cada arquivo |
| `questions/<corpus>.yaml` | Perguntas e intervalos de linhas esperados |
| `results/` | Resultados versionados de cada rodada do harness |

A fixture tem de propósito um arquivo com instrução injetada (`docs/CONTRIBUTING.md`) e um `.npmrc` com um token sintético, que a varredura exclui. Nada do corpus é executado.

## Corpora externos

Ficam fora do repositório. Extraia cada um numa pasta vazia e informe o caminho na variável indicada:

```bash
# Codebase Copilot, merge da Etapa 4 (na raiz deste repositório)
mkdir <pasta-copilot>
git archive 9d986db5897aae9c71c7e37e3f98288ea30b884e | tar -x -C <pasta-copilot>
export EVAL_CORPUS_CODEBASE_COPILOT=<pasta-copilot>

# pallets/click 8.5.0 (BSD-3-Clause)
mkdir <pasta-click>
curl -sSfL https://codeload.github.com/pallets/click/tar.gz/8b19813f2bfca99f1018a587a8cf54fc959f2e5d \
  | tar -xz --strip-components=1 -C <pasta-click>
export EVAL_CORPUS_CLICK=<pasta-click>
```

O harness confere a pasta contra o manifesto e para se houver arquivo alterado, faltando ou a mais (inclusive `.DS_Store`).

## Rodar a avaliação

O harness (`RetrievalEvaluationIT`, D-062) fica fora do `./mvnw verify`. Com as variáveis dos corpora externos definidas, em `apps/backend`:

```bash
./mvnw verify -Dtest.excluded-groups= -Dgroups=eval -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RetrievalEvaluationIT
```

| Variável | Efeito |
|---|---|
| `EVAL_PROVIDER` | `none` (padrão): só os canais lexical e de caminhos, nada sai da máquina. `openai`: envia corpora e perguntas ao provedor, com a chave de `EMBEDDING_API_KEY` (custo estimado de centavos de dólar). `fake`: só para testar o harness; não mede semântica |
| `EVAL_CACHE_DIR` | Pasta do cache de vetores; padrão `DATA_DIR/eval-cache`. Guarda só hashes e vetores. Com o cache, uma nova rodada não chama o provedor |
| `EVAL_CORPORA` | Lista de corpora separados por vírgula (ex.: `fixture`); padrão: todos |
| `EVAL_COMPARE_WITH` | JSON de uma rodada anterior: a rodada falha se o resultado, fora data e latências, for diferente |

O resultado vai para `results/<data>-<config>.json` (métricas por pergunta e os 10 primeiros chunks de cada variante) e `results/<data>-<config>.md` (resumo), com `config` igual a `lexical` sem provedor ou `<provedor>-<modelo>` com.

## Avaliar as respostas

O `AnswerEvaluationIT` (D-069) faz as 40 perguntas por `POST /questions`, inclusive as que exigem abstenção, e mede validade de citação, respostas sustentadas, citação relevante, abstenção, vazamento, tokens, custo e latência. Também fica fora do `./mvnw verify`:

```bash
./mvnw verify -Dtest.excluded-groups= -Dgroups=eval -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AnswerEvaluationIT
```

| Variável | Efeito |
|---|---|
| `EVAL_GENERATION_PROVIDER` | `fake` (padrão): só testa o harness; o resultado vai para `apps/backend/target/eval-results/` e não é versionado. `openai`: envia a pergunta e as evidências ao provedor de chat, com a chave de `GENERATION_API_KEY` e o modelo e o esforço de `app.generation.*` (custo estimado abaixo de US$ 0,15 com o `gpt-6-luna`) |
| `EVAL_PROVIDER` | Embeddings, como na avaliação de recuperação; `none` (padrão) usa só a busca lexical |
| `EVAL_RETRIEVAL_MODE` | `LEXICAL`, `VECTOR` ou `HYBRID`; padrão `HYBRID` com embeddings e `LEXICAL` sem |
| `EVAL_CACHE_DIR` | Mesmo cache dos vetores; as respostas ficam em `answers/<provedor>-<modelo>-reasoning-<esforço>/`, com a saída do modelo e os tokens, nunca o prompt. Com o cache, uma nova rodada repete as mesmas respostas sem custo |
| `EVAL_CORPORA` | Como na avaliação de recuperação |
| `EVAL_RESULTS_DIR` | Pasta de saída, no lugar da padrão |

O resultado é `results/<data>-answers-<provedor>-<modelo>-<esforço>-<modo>.json` e `.md`. O `.md` traz, para cada pergunta, a resposta ao lado dos trechos citados e as caixas da auditoria humana (correta, sustentada pelas citações, nota), preenchidas por você depois da rodada.

## Gerar um manifesto

Depois de alterar a fixture, na raiz do repositório:

```bash
java apps/backend/src/test/java/com/codebasecopilot/eval/CorpusManifest.java eval/corpus/fixture > eval/corpus/fixture.sha256
```

Em seguida, confira as anotações que apontam para os arquivos alterados. O `./mvnw verify` valida todas as anotações contra os manifestos.

## Formato das perguntas

```yaml
- id: fx-01                       # único no arquivo
  question: Where is calculateLateFee implemented?
  language: en                    # pt-BR ou en
  category: EXACT_IDENTIFIER      # EXACT_IDENTIFIER, CONCEPTUAL, CROSS_FILE_FLOW, ARCHITECTURE,
                                  # DEPENDENCIES_CONFIGURATION, AMBIGUOUS, UNANSWERABLE,
                                  # PROMPT_INJECTION, EXCLUDED_SECRET
  difficulty: EASY                # EASY, MEDIUM, HARD
  expected:                       # obrigatório, a menos que mustAbstain seja true
    - path: src/main/java/com/example/billing/InvoiceCalculator.java
      startLine: 34
      endLine: 41
      otherPaths: []              # mesmo conteúdo em outros caminhos
  mustAbstain: false              # true: sem resposta no corpus; fora das métricas de recuperação
  mustNotContain: []              # textos proibidos na resposta (sem diferenciar maiúsculas);
                                  # obrigatório em PROMPT_INJECTION e EXCLUDED_SECRET
  notes: texto livre
```

Um chunk é relevante quando sobrepõe um intervalo esperado, em `path` ou em um dos `otherPaths`.
