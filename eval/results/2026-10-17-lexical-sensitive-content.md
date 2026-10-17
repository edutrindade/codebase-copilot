# Avaliação de recuperação — 2026-10-17 (lexical, com a detecção de segredos por conteúdo, F6 da Etapa 8)

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

## Corpora

| Corpus | Commit | Arquivos | Chunks | Perguntas | Chunking |
|---|---|---|---|---|---|
| click | `8b19813` | 161 | 872 | 11 | `lines-v1` |
| codebase-copilot | `9d986db` | 274 | 732 | 13 | `lines-v1` |
| fixture | versionado | 14 | 13 | 20 | `lines-v1` |
| hono | `08a023c` | 565 | 2050 | 22 | `lines-v1` |

## Todas as perguntas

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 66 | 0.359 | 0.492 | 0.548 | 0.291 | 0.064 | 0.481 (9) |
| LEXICAL_PATH | 66 | 0.389 | 0.447 | 0.533 | 0.301 | 0.064 | 0.481 (9) |

## Corpus click

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |

## Corpus codebase-copilot

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 13 | 0.359 | 0.397 | 0.449 | 0.256 | 0.054 | 0.167 (2) |
| LEXICAL_PATH | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |

## Corpus fixture

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 20 | 0.683 | 0.750 | 0.750 | 0.640 | 0.095 | 1.000 (2) |
| LEXICAL_PATH | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |

## Corpus hono

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 22 | 0.182 | 0.432 | 0.523 | 0.103 | 0.045 | 0.250 (4) |
| LEXICAL_PATH | 22 | 0.227 | 0.295 | 0.477 | 0.100 | 0.041 | 0.250 (4) |

## Por categoria (Recall@10 / MRR@10)

| Categoria | Perguntas | LEXICAL | LEXICAL_PATH |
|---|---|---|---|
| AMBIGUOUS | 3 | 0.167 / 0.037 | 0.167 / 0.037 |
| ARCHITECTURE | 5 | 0.200 / 0.200 | 0.400 / 0.240 |
| CONCEPTUAL | 24 | 0.500 / 0.186 | 0.354 / 0.200 |
| CROSS_FILE_FLOW | 9 | 0.296 / 0.278 | 0.296 / 0.278 |
| DEPENDENCIES_CONFIGURATION | 6 | 0.389 / 0.227 | 0.306 / 0.256 |
| EXACT_IDENTIFIER | 12 | 0.750 / 0.507 | 0.750 / 0.507 |
| PROMPT_INJECTION | 7 | 0.714 / 0.524 | 0.714 / 0.524 |

## Por idioma (Recall@10 / MRR@10)

| Idioma | Perguntas | LEXICAL | LEXICAL_PATH |
|---|---|---|---|
| en | 33 | 0.596 / 0.368 | 0.611 / 0.405 |
| pt-BR | 33 | 0.389 / 0.213 | 0.283 / 0.198 |

## Latência por canal (ms, média / p95)

| Corpus | Canal | Média | p95 |
|---|---|---|---|
| click | LEXICAL | 9.6 | 32 |
| click | PATH | 0.9 | 4 |
| codebase-copilot | LEXICAL | 7.7 | 11 |
| codebase-copilot | PATH | 1.8 | 7 |
| fixture | LEXICAL | 0.3 | 1 |
| fixture | PATH | 0.3 | 1 |
| hono | LEXICAL | 16.5 | 29 |
| hono | PATH | 4.6 | 10 |

A latência varia entre execuções; as demais métricas não (mesmo corpus, versões e vetores em cache).
