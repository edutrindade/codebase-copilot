# Avaliação de recuperação — 2026-10-17 (lexical-lines-60-0)

Gerado pelo `RetrievalEvaluationIT`; os detalhes por pergunta estão no JSON de mesmo nome.

## Configuração

- `provider`: —
- `model`: —
- `dimensions`: —
- `lexicalVersion`: ident-v1
- `candidatesPerChannel`: 50
- `rrfK`: 60
- `rrfWeights`: 1 em todos os canais
- `pathThreshold`: 0.8
- `rankingDepth`: 20
- `chunking`: 60 linhas, sobreposição de 0

## Corpora

| Corpus | Commit | Arquivos | Chunks | Perguntas | Chunking |
|---|---|---|---|---|---|
| click | `8b19813` | 161 | 815 | 11 | `eval-lines-60-0` |
| codebase-copilot | `9d986db` | 274 | 711 | 13 | `eval-lines-60-0` |
| fixture | versionado | 18 | 17 | 20 | `eval-lines-60-0` |
| hono | `08a023c` | 565 | 1995 | 22 | `eval-lines-60-0` |

## Todas as perguntas

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 66 | 0.381 | 0.485 | 0.561 | 0.302 | 0.062 | 0.481 (9) |
| LEXICAL_PATH | 66 | 0.412 | 0.439 | 0.568 | 0.312 | 0.061 | 0.481 (9) |

## Corpus click

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 11 | 0.167 | 0.212 | 0.348 | 0.144 | 0.045 | 1.000 (1) |
| LEXICAL_PATH | 11 | 0.167 | 0.212 | 0.348 | 0.144 | 0.045 | 1.000 (1) |

## Corpus codebase-copilot

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 13 | 0.359 | 0.397 | 0.474 | 0.260 | 0.054 | 0.167 (2) |
| LEXICAL_PATH | 13 | 0.359 | 0.397 | 0.474 | 0.254 | 0.062 | 0.167 (2) |

## Corpus fixture

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 20 | 0.683 | 0.750 | 0.750 | 0.640 | 0.095 | 1.000 (2) |
| LEXICAL_PATH | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |

## Corpus hono

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 22 | 0.227 | 0.432 | 0.545 | 0.098 | 0.045 | 0.250 (4) |
| LEXICAL_PATH | 22 | 0.273 | 0.295 | 0.568 | 0.094 | 0.036 | 0.250 (4) |

## Por categoria (Recall@10 / MRR@10)

| Categoria | Perguntas | LEXICAL | LEXICAL_PATH |
|---|---|---|---|
| AMBIGUOUS | 3 | 0.000 / 0.000 | 0.000 / 0.000 |
| ARCHITECTURE | 5 | 0.200 / 0.200 | 0.400 / 0.240 |
| CONCEPTUAL | 24 | 0.500 / 0.181 | 0.354 / 0.195 |
| CROSS_FILE_FLOW | 9 | 0.296 / 0.287 | 0.296 / 0.287 |
| DEPENDENCIES_CONFIGURATION | 6 | 0.389 / 0.229 | 0.306 / 0.256 |
| EXACT_IDENTIFIER | 12 | 0.750 / 0.579 | 0.750 / 0.579 |
| PROMPT_INJECTION | 7 | 0.714 / 0.524 | 0.714 / 0.524 |

## Por idioma (Recall@10 / MRR@10)

| Idioma | Perguntas | LEXICAL | LEXICAL_PATH |
|---|---|---|---|
| en | 33 | 0.581 / 0.387 | 0.596 / 0.425 |
| pt-BR | 33 | 0.389 / 0.217 | 0.283 / 0.199 |

## Latência por canal (ms, média / p95)

| Corpus | Canal | Média | p95 |
|---|---|---|---|
| click | LEXICAL | 6.2 | 16 |
| click | PATH | 0.5 | 3 |
| codebase-copilot | LEXICAL | 5.4 | 13 |
| codebase-copilot | PATH | 1.3 | 8 |
| fixture | LEXICAL | 0.2 | 1 |
| fixture | PATH | 0.2 | 1 |
| hono | LEXICAL | 10.2 | 24 |
| hono | PATH | 3.2 | 11 |

A latência varia entre execuções; as demais métricas não (mesmo corpus, versões e vetores em cache).
