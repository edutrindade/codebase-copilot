# Corpus de avaliação

Corpus e anotações da avaliação de recuperação (D-061, `docs/08-EVALUATION.md`).

| Caminho | Conteúdo |
|---|---|
| `corpus/fixture/` | Fixture sintética versionada (Java, TypeScript, Python, SQL, Markdown) |
| `corpus/<corpus>.sha256` | Manifesto: SHA-256, quantidade de linhas (`-` se não for texto) e caminho de cada arquivo |
| `questions/<corpus>.yaml` | Perguntas e intervalos de linhas esperados |

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
  notes: texto livre
```

Um chunk é relevante quando sobrepõe um intervalo esperado, em `path` ou em um dos `otherPaths`.
