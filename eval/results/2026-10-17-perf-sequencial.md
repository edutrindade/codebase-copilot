# Medição de desempenho — 2026-10-17 (canais em sequência, antes da F2)

Gerado pelo `PerformanceEvaluationIT`; os valores exatos estão no JSON de mesmo nome.

## Ambiente

- `os`: Mac OS X 26.6.2
- `arch`: aarch64
- `cpus`: 10
- `memory`: 16.0 GB
- `java`: OpenJDK 64-Bit Server VM 25.0.4.1
- `jvmMemoryArguments`: []
- `heapMax`: 4.0 GB
- `postgres`: 17.11 (Debian 17.11-1.pgdg12+2) (Testcontainers, pgvector)

## Configuração

- `embeddings`: fake, 1536 dimensões (só o custo local)
- `generation`: fake (sem o tempo do provedor)
- `maxChunksPerJob`: 200000
- `searchRuns`: 50 (mais 5 de aquecimento)
- `questionRuns`: 20
- `searchLimit`: 10
- `synthetic`: 3000 arquivos de 200 a 1480 linhas, vocabulário de 5000, semente 42
- `mutation`: 3 rodadas de 10%

## Árvores medidas

| Árvore | Arquivos | Chunks | Tamanho | Varredura (ms) | Import ZIP |
|---|---|---|---|---|---|
| click | 161 | 872 | 1.5 MB | 80 | — |
| codebase-copilot | 274 | 732 | 1.3 MB | 62 | — |
| fixture | 18 | 17 | 12.2 kB | 7 | — |
| hono | 565 | 2050 | 4.8 MB | 80 | — |
| synthetic | 3000 | 46547 | 112.0 MB | 260 | 34.2 MB em 619 ms |

## Indexação (ms)

| Árvore | Execução | Arquivos alterados | Total | SCANNING | PROCESSING_FILES | LEXICAL | EMBEDDING | FINALIZING |
|---|---|---|---|---|---|---|---|---|
| click | completa | 166 | 758 | 33 | 159 | 318 | 153 | 19 |
| click | sem mudanças | 0 | 56 | 6 | 18 | — | — | 16 |
| codebase-copilot | completa | 274 | 481 | 13 | 172 | 171 | 86 | 23 |
| codebase-copilot | sem mudanças | 0 | 146 | 14 | 60 | — | — | 60 |
| fixture | completa | 22 | 33 | — | 5 | 5 | 5 | 6 |
| fixture | sem mudanças | 0 | 22 | — | 5 | — | — | 6 |
| hono | completa | 585 | 1110 | 28 | 322 | 424 | 230 | 85 |
| hono | sem mudanças | 0 | 158 | 31 | 46 | — | — | 76 |
| synthetic | completa | 3000 | 91110 | 119 | 10556 | 27981 | 17789 | 34644 |
| synthetic | sem mudanças | 0 | 3357 | 1290 | 1902 | 88 | — | 56 |
| synthetic | 10% alterados, rodada 1 | 300 | 6025 | 108 | 1010 | 3551 | 540 | 796 |
| synthetic | 10% alterados, rodada 2 | 300 | 6344 | 104 | 1124 | 3710 | 588 | 794 |
| synthetic | 10% alterados, rodada 3 | 300 | 6506 | 126 | 1181 | 3862 | 531 | 797 |

## Busca e pergunta (ms)

| Árvore | Operação | Execuções | Média | p50 | p95 | Máximo |
|---|---|---|---|---|---|---|
| click | LEXICAL | 50 | 6.1 | 6 | 8 | 9 |
| click | VECTOR | 50 | 35.4 | 37 | 41 | 42 |
| click | HYBRID | 50 | 44.2 | 42 | 57 | 82 |
| click | pergunta (sem provedor) | 20 | 50.2 | 45 | 57 | 104 |
| codebase-copilot | LEXICAL | 50 | 6.4 | 6 | 11 | 15 |
| codebase-copilot | VECTOR | 50 | 32.5 | 33 | 34 | 35 |
| codebase-copilot | HYBRID | 50 | 38.8 | 38 | 42 | 56 |
| codebase-copilot | pergunta (sem provedor) | 20 | 44.4 | 44 | 47 | 52 |
| fixture | LEXICAL | 50 | 1.0 | 1 | 1 | 1 |
| fixture | VECTOR | 50 | 1.1 | 1 | 2 | 2 |
| fixture | HYBRID | 50 | 2.7 | 2 | 5 | 6 |
| fixture | pergunta (sem provedor) | 20 | 6.8 | 6 | 8 | 9 |
| hono | LEXICAL | 50 | 9.1 | 8 | 16 | 17 |
| hono | VECTOR | 50 | 80.8 | 80 | 86 | 94 |
| hono | HYBRID | 50 | 91.7 | 91 | 99 | 131 |
| hono | pergunta (sem provedor) | 20 | 99.2 | 98 | 108 | 116 |
| synthetic | LEXICAL | 50 | 246.3 | 260 | 479 | 492 |
| synthetic | VECTOR | 50 | 350.9 | 317 | 459 | 1042 |
| synthetic | HYBRID | 50 | 590.3 | 456 | 1062 | 1123 |
| synthetic | LEXICAL, termo comum | 50 | 341.3 | 330 | 412 | 417 |
| synthetic | HYBRID, termo comum | 50 | 696.5 | 689 | 826 | 1123 |
| synthetic | pergunta (sem provedor) | 20 | 577.5 | 478 | 772 | 1636 |

## Memória

| Árvore | Heap máximo | Pico de heap | Pico de RSS |
|---|---|---|---|
| click | 4.0 GB | 186.3 MB | 465.2 MB |
| codebase-copilot | 4.0 GB | 200.8 MB | 442.1 MB |
| fixture | 4.0 GB | 200.5 MB | 418.3 MB |
| hono | 4.0 GB | 218.0 MB | 447.0 MB |
| synthetic | 4.0 GB | 277.8 MB | 498.1 MB |

## Armazenamento depois de cada execução (linhas / tamanho)

| Árvore | Execução | `file_contents` | `snapshot_files` | `chunks` | `chunk_lexical` | `chunk_embeddings` | Total |
|---|---|---|---|---|---|---|---|
| click | completa | 158 / 800.0 kB (índices 40.0 kB) | 161 / 232.0 kB (índices 168.0 kB) | 872 / 1.8 MB (índices 328.0 kB) | 872 / 7.4 MB (índices 4.0 MB) | 872 / 7.2 MB (índices 128.0 kB) | 17.4 MB |
| click | sem mudanças | 158 / 800.0 kB (índices 40.0 kB) | 322 / 352.0 kB (índices 256.0 kB) | 872 / 1.8 MB (índices 328.0 kB) | 872 / 7.4 MB (índices 4.0 MB) | 872 / 7.2 MB (índices 128.0 kB) | 17.5 MB |
| codebase-copilot | completa | 274 / 752.0 kB (índices 56.0 kB) | 274 / 600.0 kB (índices 504.0 kB) | 732 / 1.3 MB (índices 256.0 kB) | 732 / 5.0 MB (índices 2.8 MB) | 732 / 6.1 MB (índices 112.0 kB) | 13.7 MB |
| codebase-copilot | sem mudanças | 274 / 752.0 kB (índices 56.0 kB) | 548 / 1.0 MB (índices 912.0 kB) | 732 / 1.3 MB (índices 256.0 kB) | 732 / 5.0 MB (índices 2.8 MB) | 732 / 6.1 MB (índices 112.0 kB) | 14.2 MB |
| fixture | completa | 17 / 64.0 kB (índices 16.0 kB) | 19 / 80.0 kB (índices 64.0 kB) | 17 / 96.0 kB (índices 48.0 kB) | 17 / 136.0 kB (índices 64.0 kB) | 17 / 200.0 kB (índices 16.0 kB) | 576.0 kB |
| fixture | sem mudanças | 17 / 64.0 kB (índices 16.0 kB) | 38 / 88.0 kB (índices 72.0 kB) | 17 / 96.0 kB (índices 48.0 kB) | 17 / 136.0 kB (índices 64.0 kB) | 17 / 200.0 kB (índices 16.0 kB) | 584.0 kB |
| hono | completa | 541 / 1.2 MB (índices 88.0 kB) | 565 / 664.0 kB (índices 520.0 kB) | 2050 / 3.5 MB (índices 736.0 kB) | 2050 / 10.2 MB (índices 6.0 MB) | 2017 / 16.6 MB (índices 280.0 kB) | 32.1 MB |
| hono | sem mudanças | 541 / 1.2 MB (índices 88.0 kB) | 1130 / 1.1 MB (índices 928.0 kB) | 2050 / 3.5 MB (índices 736.0 kB) | 2050 / 10.2 MB (índices 6.0 MB) | 2017 / 16.6 MB (índices 280.0 kB) | 32.6 MB |
| synthetic | completa | 3000 / 47.1 MB (índices 488.0 kB) | 3000 / 3.7 MB (índices 3.1 MB) | 46530 / 86.6 MB (índices 15.1 MB) | 46530 / 443.2 MB (índices 142.8 MB) | 46530 / 380.7 MB (índices 6.0 MB) | 961.3 MB |
| synthetic | sem mudanças | 3000 / 47.1 MB (índices 488.0 kB) | 6000 / 4.6 MB (índices 3.5 MB) | 46530 / 86.6 MB (índices 15.1 MB) | 46530 / 443.2 MB (índices 142.8 MB) | 46530 / 380.7 MB (índices 6.0 MB) | 962.2 MB |
| synthetic | 10% alterados, rodada 1 | 3300 / 51.8 MB (índices 528.0 kB) | 9000 / 7.4 MB (índices 5.8 MB) | 51160 / 95.1 MB (índices 16.5 MB) | 51160 / 489.8 MB (índices 159.3 MB) | 51160 / 419.6 MB (índices 7.7 MB) | 1.0 GB |
| synthetic | 10% alterados, rodada 2 | 3600 / 56.5 MB (índices 560.0 kB) | 12000 / 8.9 MB (índices 6.8 MB) | 55901 / 103.9 MB (índices 18.0 MB) | 55901 / 533.8 MB (índices 172.7 MB) | 55901 / 461.6 MB (índices 11.4 MB) | 1.1 GB |
| synthetic | 10% alterados, rodada 3 | 3900 / 61.3 MB (índices 560.0 kB) | 12000 / 9.9 MB (índices 7.2 MB) | 60663 / 112.8 MB (índices 19.6 MB) | 60663 / 576.2 MB (índices 184.5 MB) | 60663 / 500.4 MB (índices 11.9 MB) | 1.2 GB |

Os tamanhos são de `pg_total_relation_size` (tabela, índices e TOAST) com o banco só com a árvore medida; linhas apagadas pela poda só liberam espaço depois do autovacuum. A latência varia entre execuções.
