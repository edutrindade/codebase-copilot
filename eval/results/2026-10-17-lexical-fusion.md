# Avaliação de recuperação — 2026-10-17 (lexical-fusion)

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
- `sweep`: fusão de todos os canais com um parâmetro alterado por vez: k [10, 30, 100], pesos [0.5, 2.0], candidatos por canal [20, 100], limiar de caminhos [0.6, 0.7, 0.9]

## Corpora

| Corpus | Commit | Arquivos | Chunks | Perguntas | Chunking |
|---|---|---|---|---|---|
| click | `8b19813` | 161 | 872 | 11 | `lines-v1` |
| codebase-copilot | `9d986db` | 274 | 732 | 13 | `lines-v1` |
| fixture | versionado | 18 | 17 | 20 | `lines-v1` |
| hono | `08a023c` | 565 | 2050 | 22 | `lines-v1` |

## Todas as perguntas

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 66 | 0.359 | 0.492 | 0.548 | 0.291 | 0.064 | 0.481 (9) |
| LEXICAL_PATH | 66 | 0.389 | 0.447 | 0.533 | 0.301 | 0.064 | 0.481 (9) |
| LEXICAL_PATH k=10 | 66 | 0.381 | 0.447 | 0.533 | 0.298 | 0.062 | 0.481 (9) |
| LEXICAL_PATH k=30 | 66 | 0.389 | 0.447 | 0.533 | 0.301 | 0.064 | 0.481 (9) |
| LEXICAL_PATH k=100 | 66 | 0.389 | 0.447 | 0.533 | 0.301 | 0.064 | 0.481 (9) |
| LEXICAL_PATH PATH=0.5 | 66 | 0.381 | 0.477 | 0.556 | 0.305 | 0.064 | 0.481 (9) |
| LEXICAL_PATH PATH=2.0 | 66 | 0.396 | 0.447 | 0.487 | 0.300 | 0.064 | 0.481 (9) |
| LEXICAL_PATH candidates=20 | 66 | 0.381 | 0.439 | 0.548 | 0.296 | 0.061 | 0.481 (9) |
| LEXICAL_PATH candidates=100 | 66 | 0.389 | 0.447 | 0.510 | 0.301 | 0.064 | 0.481 (9) |
| LEXICAL_PATH threshold=0.6 | 66 | 0.389 | 0.447 | 0.533 | 0.300 | 0.064 | 0.481 (9) |
| LEXICAL_PATH threshold=0.7 | 66 | 0.389 | 0.447 | 0.533 | 0.301 | 0.064 | 0.481 (9) |
| LEXICAL_PATH threshold=0.9 | 66 | 0.389 | 0.447 | 0.533 | 0.301 | 0.064 | 0.481 (9) |
| LEXICAL_PATH names=file | 66 | 0.434 | 0.500 | 0.578 | 0.335 | 0.071 | 0.481 (9) |

## Corpus click

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH k=10 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH k=30 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH k=100 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH PATH=0.5 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH PATH=2.0 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH candidates=20 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH candidates=100 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH threshold=0.6 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH threshold=0.7 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH threshold=0.9 | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |
| LEXICAL_PATH names=file | 11 | 0.121 | 0.258 | 0.348 | 0.071 | 0.055 | 1.000 (1) |

## Corpus codebase-copilot

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 13 | 0.359 | 0.397 | 0.449 | 0.256 | 0.054 | 0.167 (2) |
| LEXICAL_PATH | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH k=10 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH k=30 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH k=100 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH PATH=0.5 | 13 | 0.359 | 0.397 | 0.449 | 0.255 | 0.054 | 0.167 (2) |
| LEXICAL_PATH PATH=2.0 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH candidates=20 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH candidates=100 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH threshold=0.6 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH threshold=0.7 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH threshold=0.9 | 13 | 0.359 | 0.397 | 0.449 | 0.250 | 0.062 | 0.167 (2) |
| LEXICAL_PATH names=file | 13 | 0.359 | 0.397 | 0.449 | 0.251 | 0.062 | 0.167 (2) |

## Corpus fixture

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 20 | 0.683 | 0.750 | 0.750 | 0.640 | 0.095 | 1.000 (2) |
| LEXICAL_PATH | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH k=10 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH k=30 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH k=100 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH PATH=0.5 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH PATH=2.0 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH candidates=20 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH candidates=100 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH threshold=0.6 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH threshold=0.7 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH threshold=0.9 | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |
| LEXICAL_PATH names=file | 20 | 0.733 | 0.750 | 0.750 | 0.683 | 0.095 | 1.000 (2) |

## Corpus hono

| Variante | Perguntas | Recall@5 | Recall@10 | Recall@20 | MRR@10 | Precision@10 | Cobertura de arquivos@10 |
|---|---|---|---|---|---|---|---|
| LEXICAL | 22 | 0.182 | 0.432 | 0.523 | 0.103 | 0.045 | 0.250 (4) |
| LEXICAL_PATH | 22 | 0.227 | 0.295 | 0.477 | 0.100 | 0.041 | 0.250 (4) |
| LEXICAL_PATH k=10 | 22 | 0.205 | 0.295 | 0.477 | 0.089 | 0.036 | 0.250 (4) |
| LEXICAL_PATH k=30 | 22 | 0.227 | 0.295 | 0.477 | 0.100 | 0.041 | 0.250 (4) |
| LEXICAL_PATH k=100 | 22 | 0.227 | 0.295 | 0.477 | 0.100 | 0.041 | 0.250 (4) |
| LEXICAL_PATH PATH=0.5 | 22 | 0.205 | 0.386 | 0.545 | 0.109 | 0.045 | 0.250 (4) |
| LEXICAL_PATH PATH=2.0 | 22 | 0.250 | 0.295 | 0.341 | 0.095 | 0.041 | 0.250 (4) |
| LEXICAL_PATH candidates=20 | 22 | 0.205 | 0.273 | 0.523 | 0.085 | 0.032 | 0.250 (4) |
| LEXICAL_PATH candidates=100 | 22 | 0.227 | 0.295 | 0.409 | 0.100 | 0.041 | 0.250 (4) |
| LEXICAL_PATH threshold=0.6 | 22 | 0.227 | 0.295 | 0.477 | 0.096 | 0.041 | 0.250 (4) |
| LEXICAL_PATH threshold=0.7 | 22 | 0.227 | 0.295 | 0.477 | 0.100 | 0.041 | 0.250 (4) |
| LEXICAL_PATH threshold=0.9 | 22 | 0.227 | 0.295 | 0.477 | 0.100 | 0.041 | 0.250 (4) |
| LEXICAL_PATH names=file | 22 | 0.364 | 0.455 | 0.614 | 0.202 | 0.064 | 0.250 (4) |

## Por categoria (Recall@10 / MRR@10)

| Categoria | Perguntas | LEXICAL | LEXICAL_PATH | LEXICAL_PATH k=10 | LEXICAL_PATH k=30 | LEXICAL_PATH k=100 | LEXICAL_PATH PATH=0.5 | LEXICAL_PATH PATH=2.0 | LEXICAL_PATH candidates=20 | LEXICAL_PATH candidates=100 | LEXICAL_PATH threshold=0.6 | LEXICAL_PATH threshold=0.7 | LEXICAL_PATH threshold=0.9 | LEXICAL_PATH names=file |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| AMBIGUOUS | 3 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 | 0.167 / 0.037 |
| ARCHITECTURE | 5 | 0.200 / 0.200 | 0.400 / 0.240 | 0.400 / 0.240 | 0.400 / 0.240 | 0.400 / 0.240 | 0.200 / 0.200 | 0.400 / 0.267 | 0.400 / 0.240 | 0.400 / 0.240 | 0.400 / 0.240 | 0.400 / 0.240 | 0.400 / 0.240 | 0.500 / 0.500 |
| CONCEPTUAL | 24 | 0.500 / 0.186 | 0.354 / 0.200 | 0.354 / 0.200 | 0.354 / 0.200 | 0.354 / 0.200 | 0.438 / 0.213 | 0.354 / 0.190 | 0.354 / 0.200 | 0.354 / 0.200 | 0.354 / 0.200 | 0.354 / 0.200 | 0.354 / 0.200 | 0.438 / 0.219 |
| CROSS_FILE_FLOW | 9 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 | 0.296 / 0.278 |
| DEPENDENCIES_CONFIGURATION | 6 | 0.389 / 0.227 | 0.306 / 0.256 | 0.306 / 0.217 | 0.306 / 0.256 | 0.306 / 0.256 | 0.472 / 0.282 | 0.306 / 0.256 | 0.222 / 0.200 | 0.306 / 0.256 | 0.306 / 0.242 | 0.306 / 0.256 | 0.306 / 0.256 | 0.306 / 0.256 |
| EXACT_IDENTIFIER | 12 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.750 / 0.507 | 0.833 / 0.549 |
| PROMPT_INJECTION | 7 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 | 0.714 / 0.524 |

## Por idioma (Recall@10 / MRR@10)

| Idioma | Perguntas | LEXICAL | LEXICAL_PATH | LEXICAL_PATH k=10 | LEXICAL_PATH k=30 | LEXICAL_PATH k=100 | LEXICAL_PATH PATH=0.5 | LEXICAL_PATH PATH=2.0 | LEXICAL_PATH candidates=20 | LEXICAL_PATH candidates=100 | LEXICAL_PATH threshold=0.6 | LEXICAL_PATH threshold=0.7 | LEXICAL_PATH threshold=0.9 | LEXICAL_PATH names=file |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| en | 33 | 0.596 / 0.368 | 0.611 / 0.405 | 0.611 / 0.398 | 0.611 / 0.405 | 0.611 / 0.405 | 0.611 / 0.404 | 0.611 / 0.401 | 0.596 / 0.395 | 0.611 / 0.405 | 0.611 / 0.402 | 0.611 / 0.405 | 0.611 / 0.405 | 0.657 / 0.455 |
| pt-BR | 33 | 0.389 / 0.213 | 0.283 / 0.198 | 0.283 / 0.198 | 0.283 / 0.198 | 0.283 / 0.198 | 0.343 / 0.207 | 0.283 / 0.198 | 0.283 / 0.198 | 0.283 / 0.198 | 0.283 / 0.198 | 0.283 / 0.198 | 0.283 / 0.198 | 0.343 / 0.216 |

## Latência por canal (ms, média / p95)

| Corpus | Canal | Média | p95 |
|---|---|---|---|
| click | LEXICAL | 5.3 | 12 |
| click | PATH | 0.3 | 2 |
| codebase-copilot | LEXICAL | 3.9 | 5 |
| codebase-copilot | PATH | 0.5 | 3 |
| fixture | LEXICAL | 0.1 | 0 |
| fixture | PATH | 0.0 | 0 |
| hono | LEXICAL | 9.3 | 17 |
| hono | PATH | 2.0 | 5 |

A latência varia entre execuções; as demais métricas não (mesmo corpus, versões e vetores em cache).
