# Plano — Etapa 8: Qualidade e hardening

**Status:** plano aprovado em 17/10/2026 com as recomendações do plano (branch `etapa-8`). As rodadas com o provedor real (P2, F3) ficam para depois de todas as etapas do roteiro, quando houver saldo. As propostas P1 a P9 viram D-079 em diante, registradas em `10-DECISIONS.md` na fatia indicada em cada uma.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 8), `docs/plans/etapa-2.md` a `etapa-7.md` (pendências), `01-PRD.md` §9, `03-UX-ONBOARDING.md` §9, `04-RAG-PIPELINE.md` §5 e §6, `07-SECURITY-PRIVACY-COSTS.md` (todo), `08-EVALUATION.md` (todo), D-017, D-035, D-036, D-043, D-049, D-052, D-058, D-059, D-061, D-062, D-066, D-069, D-072, D-076, O-004, O-005, O-010.

## Escopo

**Dentro:**
- corpus de avaliação ampliado, com uma linguagem nova e casos de segurança e de codificação;
- as rodadas com o provedor real pendentes desde a Etapa 4, se houver saldo;
- mudanças de ranking e chunking só quando a avaliação mostrar ganho, por um protocolo fixo;
- suíte de prompt injection e de importações maliciosas, cobrindo o `08` §7 e o `07` §11;
- detecção de segredos por conteúdo (padrões de alta precisão), antes dos embeddings e da geração;
- medição de latência, custo, memória e armazenamento, com os limites revistos a partir dela;
- operação: cancelamento de jobs, exclusão de conversa, último job no projeto e uso agregado;
- empacotamento: o backend servindo a UI num processo só, e a divisão do build;
- limitações conhecidas e recuperação de falhas documentadas.

**Pendências herdadas e onde são resolvidas:**

| Pendência | Origem | Onde |
|---|---|---|
| Testes de fumaça (embeddings e geração), baseline vetorial e híbrido, avaliação de respostas e auditoria | Etapas 4 a 6 | P2; F3 (depende de saldo) |
| Revisão da D-017, da O-005, do canal de caminhos (D-058) e da O-004 | Etapas 5 e 6 | P2, P3; F3, F4 |
| Variantes de chunking (O-010), parâmetros do contexto, `max-output-tokens` | Etapas 3, 5 e 6 | P3; F4 |
| Corpus maior | Etapa 5 (O-005) | P1; F1 |
| Detecção de segredos por conteúdo | Etapa 2 (D-036) | P5; F6 |
| Outras codificações além de UTF-8 | Etapa 3 (D-042) | P1 (casos no corpus), P4; F1, F5 |
| Links físicos, pontos de montagem, diretórios em NFD no Linux | Etapa 2 | P4; F5 (teste ou limitação documentada) |
| Limites de import, varredura e indexação (D-035, `app.indexing.*`), `max-chunks-per-job`, limpeza de órfãos (D-052) | Etapas 2 a 4 | P6; F2 |
| Pular o hash por tamanho e data de modificação | Etapa 3 | P6; F2 (só se a medição justificar) |
| HNSW e varredura iterativa; custo do lexical com termos comuns | Etapas 4 e 5 (D-049, D-059) | P6; F2 (só se a latência justificar) |
| Eventos de produto, latência e custo agregados | Etapa 7, `03` §9 | P7; F7 |
| Cancelamento de jobs, retenção de conversas | Etapas 6 e 7 | P7; F7 |
| Último job no `ProjectResponse` (lista mostra "falhou") | Etapa 7, F3 | P7; F7 |
| Backend servindo a UI, build acima de 500 kB | Etapa 7 (D-076) | P8; F8 |
| Realce de sintaxe no visualizador | Etapa 7 (D-075) | P9 (proposta: não) |
| Perguntas de acompanhamento, nova recuperação quando falta suporte, LLM como juiz | Etapa 6 | P9 (proposta: não) |
| Passada manual por teclado (P10 da Etapa 7) | Etapa 7 | você, se ainda não foi feita; registro no fechamento |

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Reranking por modelo, chunking por AST | Etapa 9, se a avaliação apontar o limite do `lines-v1` (P3) |
| Perguntas de acompanhamento com histórico no prompt | quando o uso pedir (P9) |
| LLM como juiz | quando o corpus passar do que dá para auditar à mão (P9) |
| Bake-off de embeddings com a Voyage (O-004) | Etapa 9 ou quando houver motivo para trocar de modelo |
| Provedor local de modelos, worker separado | Etapa 9 |
| Telemetria externa | nunca por padrão (`03` §9) |
| Autenticação e acesso por outra máquina | fora do MVP (D-072) |

## Pré-requisito externo

**Saldo no provedor.** Desde a Etapa 4 as medições reais estão pendentes por falta de saldo. Esta etapa é a que mais depende delas: "melhorar ranking apenas com evidência" sem os números do vetorial e do híbrido só alcança o lexical e o chunking. A estimativa de custo da rodada completa sai na F0 (ordem de grandeza: abaixo de US$ 2, com o corpus da P1, o `gpt-6-luna` e uma comparação com o `gpt-6.1-sol`).

Sem saldo, a etapa entrega tudo o que não chama o provedor (F1, F2, F4 no lexical e no chunking, F5 a F9) e o aceite fica parcial, como nas Etapas 5 a 7: resultados lexicais e riscos documentados, medição real pendente.

**Decisão (17/10/2026):** o saldo só entra depois de todas as etapas do roteiro. A F3 fica registrada como pendente e é feita nessa ocasião, junto com as rodadas pendentes das Etapas 4 a 7.

## Propostas para aprovação

### P1 — Corpus ampliado (resolve "ampliar corpus") → F1

**Problema:** 40 perguntas em 3 corpora; uma pergunta vale 2,5 pontos de Recall, o que torna qualquer comparação da P3 frágil. Faltam TypeScript/JavaScript real, codificações não UTF-8 e variações de injeção e de segredo.

**Proposta:**
- **Um corpus externo novo, em TypeScript**, de tamanho médio (centenas de arquivos), licença permissiva, fixado por commit e manifesto SHA-256 como os outros (D-061). Candidatos e escolha na F0, por licença, tamanho e qualidade dos nomes.
- **Os corpora atuais ficam como estão** (`codebase-copilot` em `9d986db`, `click` 8.5.0): mudar o commit invalidaria as anotações e a comparação com o baseline.
- **Fixture sintética ampliada** com: injeção em comentário de código, em string, em nome de arquivo e com delimitador forjado; texto que imita a saída do modelo (`[E1]`, JSON de citação); arquivo em Latin-1, em UTF-16 com BOM e em UTF-8 com BOM; segredos sintéticos dentro de arquivos não excluídos por nome (para a P5); e um par de perguntas cuja resposta só existe num arquivo excluído.
- **Meta: ~80 perguntas**, metade pt-BR e metade en, com pelo menos 8 de abstenção e 10 de `PROMPT_INJECTION`/`EXCLUDED_SECRET`. Mesmo processo: rascunho do assistente, revisão e aprovação suas.
- O baseline lexical é refeito no corpus novo (sem custo) e vira a referência da P3.

### P2 — Rodadas com o provedor real → F3

**Proposta, na ordem**, uma rodada por linha, com o resultado versionado em `eval/results/`:
1. teste de fumaça dos embeddings (Etapa 4) e da geração (Etapa 6);
2. avaliação de recuperação com `EVAL_PROVIDER=openai`: `VECTOR`, `LEXICAL_VECTOR` e `HYBRID` no corpus da P1;
3. avaliação de respostas com o `gpt-6-luna` (esforço `low`, modo `HYBRID`), seguida da sua auditoria;
4. comparações, só se a 3 mostrar problema de qualidade: esforço `none` (custo) e `gpt-6.1-sol` (qualidade).

Com os números: revisão da D-017, da O-005, do canal de caminhos (D-058), da O-004 (chat e embeddings, que podem sair de "a validar") e do custo medido por pergunta (`07` §9).

### P3 — Protocolo para mudar ranking ou chunking → F4

**Problema:** o roteiro pede melhorar "apenas com evidência". Sem regra fixada antes, qualquer variação positiva no corpus pequeno vira mudança.

**Proposta:**
- **Uma variável por vez** (`08` §8), cada uma uma rodada versionada, contra o baseline da P1/P2.
- **Promoção** quando: o Recall@10 ou o MRR@10 do corpus inteiro sobe o equivalente a **pelo menos duas perguntas**; nenhuma categoria cai mais que uma pergunta; `EXACT_IDENTIFIER` não cai; validade de citação e vazamento (com o modelo real) não pioram; a latência p95 da busca fica dentro do medido na P6 (+20%). A recuperação é determinística, então não há ruído a descontar; o limite de duas perguntas existe pelo tamanho do corpus.
- **Candidatos, na ordem do benefício esperado:**
  1. parâmetros da fusão (k do RRF, pesos, `candidatesPerChannel`) e o canal de caminhos — só configuração, sem reindexar;
  2. chunking `lines-v1` com 40 e 80 linhas e sobreposição de 0 e 20 — o harness já mede sem mudar as anotações (D-062);
  3. orçamento do contexto e `maxEvidence` (só com o modelo real).
- Uma mudança de chunking promovida vira **versão nova** (`lines-v2`), com `outdatedReasons` pedindo reindexação, nunca uma alteração silenciosa do `lines-v1`.
- Nada promovido também é resultado: a O-010 e a O-005 são fechadas com "mantido, com os números".
- Fora: reranking por modelo e AST (Etapa 9), a menos que os números mostrem um teto claro do `lines-v1` — nesse caso, registrado como entrada para a Etapa 9, não implementado aqui.

### P4 — Suíte de prompt injection e de importações maliciosas → F5

**Proposta:** uma matriz que liga cada item do `08` §7 e do `07` §11 a um teste existente ou novo, montada na F0 a partir dos testes atuais (`ZipExtractorTest`, `UploadLimitIT`, `IndexingEdgeCasesIT`, `AnswerApiIT`, `CitationValidatorTest` e outros). A F5 cobre só as lacunas. Casos novos previstos:
- **Injeção (estrutural, com o fake, no `verify`):** delimitador com nonce forjado, arquivo que imita o JSON da saída e rótulos `[En]`, instrução em nome de arquivo e de diretório, linha de 4.000 caracteres de instrução; conferindo que tudo fica dentro do bloco da evidência e que o validador não aceita rótulos vindos do conteúdo.
- **Injeção (efeito, com o modelo real):** as perguntas `PROMPT_INJECTION` da P1, pelo `mustNotContain` da avaliação de respostas.
- **ZIP:** bomba aninhada (ZIP dentro de ZIP não é extraído), nomes em NFD e NFC iguais, nomes com caracteres de controle e RTL, muitas entradas pequenas, profundidade máxima, entrada com tamanho declarado falso, comentário e extra field enormes.
- **Pasta local:** link físico e ponto de montagem (se não for detectável sem custo, limitação documentada com teste que fixa o comportamento atual), diretório em NFD, arquivo trocado durante a leitura.
- **Codificação:** os casos da fixture (Latin-1, UTF-16, BOM), com o comportamento decidido na F0: proposta de aceitar UTF-8 com BOM (removido antes do chunking, linhas preservadas) e manter Latin-1 e UTF-16 como `UNSUPPORTED INVALID_ENCODING`, a menos que a F0 mostre fatia relevante nos corpora.
- **UI:** um projeto de fixture com HTML e script em conteúdo, nome de arquivo e resposta do fake, no E2E, conferindo que tudo aparece como texto, sem execução nem violação de CSP.
- **Dependências:** `npm audit` e `osv-scanner` (Maven e npm) uma vez, com o resultado e as ações registrados; não entram no `verify`. Levantamento feito na F0; a correção de Tomcat e Jackson foi adiantada para a F1, e a F5 só repete a consulta.

### P5 — Segredos por conteúdo (completa a D-036) → F6

**Problema:** hoje só o nome do arquivo exclui segredos. Um `application.yml` ou um teste com uma chave real é indexado e pode ir ao provedor.

**Opções:**
1. Detectar no conteúdo e **excluir o arquivo inteiro** do índice, com motivo próprio.
2. Detectar e **mascarar** só o trecho, mantendo o arquivo.
3. Não detectar; documentar.

**Proposta:** opção 1, com padrões de **alta precisão** apenas: bloco de chave privada PEM/OpenSSH, chaves de formato conhecido (AWS `AKIA…`, GitHub `ghp_`/`github_pat_`, OpenAI `sk-…`, Slack `xox…`, Stripe `sk_live_`; lista fechada na F0 com os formatos publicados por cada um). Sem entropia nem `password=`: falso positivo esconde código legítimo.
- Roda na leitura da indexação (conteúdo inteiro), antes de chunks, embeddings e qualquer envio; o arquivo vira `UNSUPPORTED` com o motivo novo `SENSITIVE_CONTENT` (migração `V11`, se o motivo tiver `CHECK` no banco; conferido na F0).
- Como as regras `SENSITIVE`, não pode ser desligada pelo usuário; a UI lista o arquivo com o motivo, sem mostrar o trecho nem a linha.
- Texto da UI e da API: "arquivo excluído por conter um possível segredo", nunca "segredo detectado"; o `07` §5 continua dizendo que a detecção não é garantia.
- Opção 2 descartada no MVP: o arquivo citado teria linhas que não batem com o disco, e o mascaramento parcial dá falsa segurança.

### P6 — Medições de latência, custo, memória e armazenamento → F2

**Proposta:**
- **Harness de desempenho** (`PerformanceEvaluationIT`, tag `perf`, fora do `verify`, como os da D-062), que indexa os corpora e um repositório sintético gerado (~3.000 arquivos, ~50 mil chunks) e registra:
  - duração da varredura, do import ZIP e de cada etapa da indexação (completa e incremental sem mudanças);
  - latência p50/p95 da busca `LEXICAL`, `VECTOR` (com o fake: só o custo do banco) e `HYBRID`, inclusive com termos comuns;
  - latência da pergunta sem o provedor (fake) e, com saldo, com ele;
  - pico de heap (amostragem do `MemoryMXBean`) e RSS do processo, com `-Xmx` registrado;
  - armazenamento por projeto (`pg_total_relation_size` das tabelas de conteúdo, chunks, vetores e lexical) e o efeito da poda.
- Resultados versionados em `eval/results/<data>-perf.*`, com a máquina e as versões.
- **Revisão dos limites** com os números: D-035 (`InventoryProperties`), `app.indexing.*`, `max-chunks-per-job`, limpeza de órfãos (D-052). Cada limite mantido ou mudado vira uma linha na D-0xx, com o número que o sustenta.
- **Otimizações só com motivo:** HNSW (D-049), IDF no lexical (Etapa 5) e pular o hash por tamanho/data só se a medição passar de uma meta. Metas propostas, a confirmar com você depois da primeira medição (`08` §9): busca híbrida p95 abaixo de 500 ms com 50 mil chunks; pergunta, sem o tempo do provedor, abaixo de 1 s; reindexação sem mudanças abaixo de 10% do tempo da completa.
- Sem métrica nova exposta pelo actuator; o harness lê o que precisa.

### P7 — Operação: cancelamento, conversas, último job e uso agregado → F7

**Proposta:**
- **Cancelar job de indexação:** `POST /index-jobs/{id}/cancel` → `202`; o runner confere o pedido entre etapas e entre lotes de embeddings e termina com `CANCELLED` (migração para o `CHECK` do status); o snapshot ativo não muda e vetores já gravados ficam no cache (não são pagos de novo). Job já terminado → `409 JOB_NOT_ACTIVE`. Na UI, "Cancelar" no progresso, com confirmação. Import ZIP não é cancelável (é curto e limitado).
- **Excluir conversa:** `DELETE /conversations/{id}` → `204`, em cascata (respostas, evidências copiadas). Na UI, no histórico, com confirmação. **Sem retenção automática** no MVP: os dados são locais e a pessoa decide; a remoção do projeto continua apagando tudo (D-021).
- **Último job no projeto:** `lastJob { id, status, finishedAt, errorCode }` no `ProjectResponse`, aditivo; a lista do workspace passa a mostrar "falhou" (pendência da F3 da Etapa 7).
- **Uso agregado (eventos de produto, `03` §9):** `GET /projects/{id}/usage` → indexações (quantidade, falhas, duração média e p95), perguntas (quantidade, abstenções, falhas, latência p50/p95), tokens e custo estimado somados (com quantas respostas ficaram `UNKNOWN`). Calculado em SQL sobre `index_jobs` e `answers`, **sem tabela de eventos nova** e sem telemetria. "Evidências abertas" fica fora: exigiria a UI gravar eventos, sem uso claro hoje. Na UI, um painel "Uso" no resumo do projeto.

### P8 — Empacotamento → F8

**Problema:** hoje são dois processos (`spring-boot:run` e `vite preview`) e o build da UI passa de 500 kB.

**Proposta:**
- **Perfil Maven `with-ui`** que roda `npm ci` e `npm run build` em `apps/web` e copia o `dist` para os recursos estáticos; o `./mvnw verify` padrão continua sem npm (D-076). `java -jar` serve UI e API em `127.0.0.1:8080`.
- No backend: fallback de rotas da SPA para `index.html` (fora de `/api`), CSP e cabeçalhos de segurança (`X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'`) enviados pelo próprio servidor, e a origem do backend em `app.http.allowed-origins` por padrão (D-072).
- O modo de desenvolvimento (Vite com proxy) continua como está.
- **Build:** divisão por rota (`React.lazy`), com a meta de nenhum chunk acima de 500 kB.
- Um E2E roda contra o jar empacotado, além do atual.

### P9 — O que fica de fora, apesar de estar na lista → F9

**Proposta:** registrar como "não nesta etapa", com o motivo, na decisão de fechamento:
- **Realce de sintaxe:** ninguém relatou problema de legibilidade e seria um parser sobre conteúdo não confiável (D-075).
- **Perguntas de acompanhamento com histórico no prompt:** sem evidência de uso; aumenta custo e superfície de injeção (P5 da Etapa 6).
- **Nova recuperação quando falta suporte:** só depois de a avaliação real mostrar abstenção indevida relevante.
- **LLM como juiz:** ~80 perguntas ainda cabem na auditoria humana; um juiz exige outro provedor e calibração contra a própria auditoria.

## Princípios de execução

- Valem os das etapas anteriores: uma fatia por vez, `./mvnw verify` e `npm run check` verdes ao fim de cada uma, `openapi.json` regenerado e commitado quando o contrato mudar, mensagens pt-BR para todo código novo.
- Harnesses (`eval`, `perf`) e chamadas reais fora do `verify`; nenhum teste do `verify` depende de saldo ou de chave.
- Nenhuma mudança de ranking, chunking ou limite sem um resultado versionado que a justifique (P3, P6).
- Nada de conteúdo, pergunta, resposta ou segredo detectado em log, nem nos resultados do harness de desempenho.
- Mudanças de contrato só aditivas e concentradas na F7 (e na F6, para o motivo novo).

## Fatias, na ordem

### F0 — Verificações (sem código de produção)
- Saldo disponível? Se sim, estimativa de custo da F3 com os preços oficiais do dia.
- Corpus TypeScript (P1): dois ou três candidatos com licença, commit, quantidade de arquivos e de chunks estimada; escolha sua.
- Proporção de `INVALID_ENCODING` e de arquivos com BOM nos corpora atuais e no candidato (P4).
- Matriz `08` §7 + `07` §11 × testes existentes, com as lacunas (P4).
- Formatos publicados das chaves da P5, em fonte oficial e com data; se o motivo do arquivo tem `CHECK` no banco (migração ou não).
- Pontos do runner onde o cancelamento pode ser conferido sem transação longa (P7).
- `npm audit` e `osv-scanner` (P4), só o levantamento.

**Resultado (17/10/2026)**

*Saldo:* não se aplica nesta etapa (decisão acima). Sem estimativa de custo da F3 agora: os preços são conferidos de novo quando a rodada for feita.

*Corpus TypeScript (P1).* Releases estáveis mais recentes, licença e commit pela API do GitHub; arquivos, linhas e chunks medidos no tarball do commit, sem `node_modules`, `dist`, lockfiles e mídia. Os chunks são estimados como no `lines-v1` (60 linhas com sobreposição de 10):

| Repositório | Release (data) | Commit | Licença | Arquivos de texto | Linhas | Chunks (est.) | Composição |
|---|---|---|---|---|---|---|---|
| **`honojs/hono`** (recomendado) | v4.13.13 (04/10/2026) | `08a023c` | MIT | 564 | 96.578 | ~2.190 | 392 `.ts`, 31 `.tsx`, 27 `.mts`; núcleo, roteadores, middlewares e adaptadores |
| `pmndrs/jotai` | v3.0.1 (29/09/2026) | `6abd0ae` | MIT | 321 | 34.620 | ~840 | 72 `.mdx`, 67 `.ts`, 50 `.tsx`, 50 `.js`: metade é documentação |
| `sindresorhus/ky` | v2.2.0 (09/10/2026) | `3541888` | MIT | 112 | 38.396 | ~820 | 98 `.ts`, mas poucos módulos e arquivos de teste grandes |
| `pmndrs/zustand` | v5.0.15 (13/08/2026) | `2115efb` | MIT | 128 | 25.170 | ~560 | 47 `.md`, 34 `.ts`/`.tsx`: pequeno demais |

- **Escolha (aprovada em 17/10/2026): `hono` v4.13.13.** É TypeScript de verdade e tem uma arquitetura com camadas (roteadores intercambiáveis, cadeia de middlewares, adaptadores por runtime), o que dá perguntas de fluxo entre arquivos e de arquitetura. Com ~2.190 chunks, é também o maior corpus, perto de 2,5 vezes o `click`, o que serve à P6. O `jotai` seria a alternativa se o tamanho preocupar.
- O corpus é baixado do `codeload.github.com` pelo commit, como o `click`; os 2 binários do repositório entram no manifesto e a varredura os classifica.

*Codificação (P4).* Nos sete corpora medidos (os três atuais e os quatro candidatos): **nenhum** arquivo em UTF-8 inválido, com BOM de UTF-8 ou em UTF-16; só binários (3) e uma linha acima de 4.000 caracteres (1, no `jotai`). No código:
- o `TextClassifier` **já remove o BOM de UTF-8**, mas nenhum teste cobre isso (lacuna);
- um arquivo UTF-16 tem bytes NUL e vira `BINARY`, não `INVALID_ENCODING`; a mensagem ao usuário fica enganosa, mas o arquivo não é indexado de qualquer forma.
- **Consequência:** a P4 fica como proposta: UTF-8 (com ou sem BOM) apenas; Latin-1 continua `INVALID_ENCODING`. Acrescenta-se: detectar o BOM de UTF-16 e classificar como `INVALID_ENCODING`, sem decodificar, para o motivo ficar correto (sem migração: o motivo já existe).

*Matriz de segurança (P4)* — `08` §7 e `07` §11 contra os testes atuais:

| Item | Coberto hoje por | Lacuna para a F5 |
|---|---|---|
| Pergunta que tenta revelar segredos | avaliação de respostas (`EXCLUDED_SECRET`, `mustNotContain`), só com modelo real | teste no `verify` que confere, com provedores espiões, que o conteúdo de um arquivo excluído nunca chega ao provedor de embeddings nem ao de geração |
| Arquivo com instrução para ignorar o prompt | `PromptBuilderTest.contentCannotCloseItsBlockWithoutTheNonce`, `AnswerApiIT.injectedInstructionsStayInsideTheirEvidenceBlock` | nonce forjado no conteúdo, JSON de saída e rótulos `[En]` no conteúdo, instrução em nome de arquivo e de diretório |
| Arquivo excluído com segredo sintético | `DefaultExclusionRulesTest.sensitiveVcsAndMediaRulesCannotBeDisabled`, fixture `.npmrc` | o teste com espiões acima |
| ZIP com path traversal | `ZipExtractorTest.rejectsUnsafePaths` (10 variantes), `rejectsTooLongPath`, `rejectsTooDeepPath` | nomes com RTL e outros caracteres de formatação Unicode; NFD × NFC do mesmo nome |
| Bomba de descompressão | `rejectsHighlyCompressedEntry`, `rejectsEntryLargerThanDeclared`, `rejectsTotalAboveLimitEvenWithValidEntries`, `rejectsTooManyEntries`, `UploadLimitIT` | ZIP aninhado (confirmar que vira arquivo binário, sem extração), comentário e extra field enormes |
| Arquivo gigantesco | `SourceFileReaderTest.fileAboveTheLimitChangedSinceTheScan`, `TextClassifierTest.lineAboveTheChunkLimitIsTooLong` | — |
| Encoding inválido | só o código (`TextClassifier`) | testes de BOM de UTF-8, UTF-16 e Latin-1 |
| Snapshot alterado após uma resposta | `HistoryReadApiIT.comparesTheEvidenceWithTheActiveSnapshot`, `SnapshotFileContentApiIT.prunedSnapshotsAnswerConflict` | — |
| Provedor que retorna erro ou timeout | `OpenAiGenerationProviderTest.timeoutIsUnavailable`, `AnswerApiIT.generationFailuresBecomeAFailedAnswer`, `EmbeddingRetryTest` | — |
| Resposta com ID de evidência inventado | `CitationValidatorTest.discardsAnInventedLabelAndItsMarker`, `AnswerApiIT.inventedLabelIsFlaggedAndRemoved` | — |
| Nenhum código do repositório é executado | só por construção | regra ArchUnit que proíbe `ProcessBuilder` e `Runtime.exec` no código de produção |
| Caminhos confinados à raiz | `LocalRootsApiIT.rejectsPathsOutsideRoot`, `SafeScannerTest.neverFollowsLinksInsideOrOutsideTheRoot`, `SourceFileReaderTest.linkOrFifoInPlaceOfTheFileIsNeverOpened` | links físicos e pontos de montagem (abaixo) |
| Conteúdo sensível não é logado | `AnswerApiIT.questionAnswerAndCodeNeverReachTheLogs`, `ImportApiIT.rejectedImportKeepsCurrentAndHidesEntryNames` | o mesmo teste de log na indexação inteira (caminhos e conteúdo) |
| Usuário informado sobre provedor remoto | testes da UI (F4 e F6 da Etapa 7) | — |
| Backend só local | `LocalAccessFilterIT` | — |
| Exclusão de projeto remove os dados | `IndexSchemaIT.deletingProjectCascadesThroughEverything`, `ProjectApiIT.deletesProject` | conferir também a pasta do import ZIP no `DATA_DIR` |
| Dependências revisadas | — | abaixo |

*Links físicos e pontos de montagem:* nem o Git nem o extrator de ZIP criam links físicos, e o extrator não cria links de nenhum tipo; um link físico para fora da raiz só existe se a própria pessoa o criar. Ponto de montagem dentro da raiz idem. **Proposta:** limitação documentada, sem detecção. Um teste fixa o comportamento atual (o link físico é lido como arquivo comum). Diretórios em NFD: o scanner e o navegador já normalizam para NFC; a F5 testa a abertura num nome em NFD.

*Segredos por conteúdo (P5):* formatos publicados, consultados em 17/10/2026:

| Fonte | Formato | Precisão |
|---|---|---|
| GitHub (`docs.github.com`, "About authentication to GitHub") | prefixos `ghp_`, `github_pat_`, `gho_`, `ghu_`, `ghs_`, `ghr_`; o `ghs_` está mudando para um formato sem tamanho fixo | alta |
| AWS (`docs.aws.amazon.com`, IAM identifiers) | ID de chave de acesso `AKIA` (longa) e `ASIA` (temporária), com 16 caracteres maiúsculos e dígitos depois do prefixo | alta para o ID; o segredo da chave não tem formato próprio |
| Stripe (`docs.stripe.com/keys`) | segredas `sk_live_`, `sk_test_`, `rk_live_`, `rk_test_`, `sk_org_`; `pk_` é pública e fica de fora | alta |
| Slack (`docs.slack.dev`, tokens) | `xoxb-`, `xoxp-`, `xapp-`, `xoxe.xapp-`, `xwfp-` | alta |
| OpenAI (`developers.openai.com`, CLI e Admin API) | só `sk-` e `sk-admin-` aparecem documentados; não há página com todos os prefixos | média: exigir `sk-` seguido de pelo menos 32 caracteres `[A-Za-z0-9_-]` |
| Chave privada | linhas `-----BEGIN … PRIVATE KEY-----` (RSA, EC, DSA, OPENSSH, ENCRYPTED, PGP `PRIVATE KEY BLOCK`) | alta |

O motivo do arquivo tem `CHECK` no banco (`snapshot_files_status_valid` na V7: `reason IN ('BINARY', 'INVALID_ENCODING', 'LINE_TOO_LONG')`), então `SENSITIVE_CONTENT` exige migração (`V11`).

*Cancelamento (P7):* o runner já passa pelo `Heartbeat` no começo de cada etapa, a cada lote de arquivos, de lexical e de embeddings, periodicamente na varredura e enquanto espera o provedor. O `UPDATE` do heartbeat é o ponto natural: com uma coluna `cancel_requested_at`, ele deixa de renovar o job pedido para cancelar e o runner termina com `CANCELLED` pelo mesmo caminho do `fail` (`IndexOutcomes`: snapshot `FAILED`, arquivos removidos, conteúdo órfão limpo). Nenhuma transação nova nem mais longa. Job `PENDING` é cancelado direto pelo endpoint. A migração mexe no `CHECK` de `index_jobs.status` e na restrição de consistência (`completed_at` preenchido, `error_code` nulo para `CANCELLED`); os índices parciais de job ativo (`PENDING`, `RUNNING`) não mudam.

*Dependências (P4).* `npm audit` em `apps/web`: **nenhuma vulnerabilidade**, inclusive nas de desenvolvimento. Maven: as 168 dependências (`dependency:list`) consultadas na base do OSV (`api.osv.dev/v1/querybatch`, a mesma do `osv-scanner`), em 17/10/2026:

| Dependência | Versão (gerida pelo Boot 4.1.1) | Avisos | Corrigida em | Afeta este projeto? |
|---|---|---|---|---|
| `tomcat-embed-core` | 11.0.24 | 3 críticos: autenticadores DIGEST e FORM, controle de acesso | 11.0.25 (última: 11.0.27) | não: o backend não usa autenticação do contêiner; atualizar mesmo assim |
| `tools.jackson.core:jackson-databind`/`jackson-core` | 3.1.5 | 5 altos e moderados (DoS por entrada JSON, validação polimórfica, `Path`) | 3.1.7 | pouco: só entram JSON locais (D-072) e não há tipagem polimórfica; o DoS ainda vale corrigir |
| `com.fasterxml.jackson.core:jackson-*` | 2.21.5 | os mesmos 5, na linha 2 | 2.21.7 | idem (vem como transitiva) |

O Spring Boot 4.1.1 é o último 4.1.x publicado no Maven Central, então não há atualização do Boot que resolva. **Proposta:** sobrescrever `tomcat.version` (11.0.27), `jackson-bom.version` (3.1.7) e `jackson-2-bom.version` (2.21.7) no `pom.xml`, com um comentário apontando os avisos e a remoção quando o Boot gerir versões iguais ou maiores. É pequeno e não depende do resto: **adiantado para o início da F1** (aprovado em 17/10/2026).

### F1 — Corpus ampliado
- Antes do corpus: versões de Tomcat e Jackson sobrescritas no `pom.xml` (resultado da F0), num commit próprio.
- Corpus TypeScript (`hono` v4.13.13) com manifesto; fixture ampliada; ~40 perguntas novas, revisadas e aprovadas por você.
- Baseline lexical refeito (`eval/results/<data>-lexical.*`).

**Resultado (17/10/2026)** — perguntas novas revisadas e aprovadas
- Tomcat 11.0.27, Jackson 3.1.7 e 2.21.7 sobrescritos no `pom.xml`; `./mvnw verify` verde.
- Hono com manifesto (585 arquivos), fixture com 12 arquivos novos, 40 perguntas novas (16 na fixture, 24 no hono): 80 no total, 40 em cada idioma, 14 de abstenção, 14 de injeção ou segredo. Registro na D-079.
- Baseline lexical em `eval/results/2026-10-17-lexical.*`: click e Copilot iguais aos de 16/10; no hono, o canal de caminhos piora o Recall@10 (0,432 para 0,295), entrada para a F4.

### F2 — Medições
- `PerformanceEvaluationIT` e o repositório sintético (P6); primeira rodada versionada.
- Revisão dos limites com os números; metas confirmadas com você; D-0xx.

**Resultado (17/10/2026)**
- `PerformanceEvaluationIT`, `SyntheticRepository` e `PerformanceReport`, com testes do gerador e do relatório; rodadas em `eval/results/2026-10-17-perf-sequencial.*` (antes) e `2026-10-17-perf.*` (depois).
- Metas confirmadas por você: busca híbrida com p95 ≤ 1 s com 50 mil chunks e ≤ 200 ms até 5 mil; atendidas depois de paralelizar os canais do `HYBRID` (p95 de 1,06–1,58 s para 676 ms com 46 mil chunks).
- Limites mantidos, com os números (`max-chunks-per-job`, D-035, ZIP, `app.indexing.*`, D-052); HNSW e o hash por data ficam de fora. Registro na D-080.
- **Desvio:** a tag ficou `performance`, a dos harnesses de desempenho existentes, e não `perf`. O gerador sintético foi ajustado depois da primeira rodada, porque tinha identificadores quase todos únicos e nenhuma linha em branco, o que inflava o lexical em 3 a 4 vezes.

### F3 — Rodadas com o provedor real (depende de saldo)
- As rodadas da P2, a sua auditoria das respostas, e as revisões da D-017, O-005, D-058 e O-004.
- **Adiada (17/10/2026)** para depois de todas as etapas do roteiro; a F4 roda só o que não precisa do provedor.
- Entra também o que a F4 deixou pendente (D-081): a varredura com vetores (`EVAL_PROVIDER=openai EVAL_SWEEP=fusion`), a variante de nomes de arquivo no `PathSearch` e o chunking de 80 linhas, pela mesma regra.

### F4 — Ranking e chunking
- As variações da P3, cada uma com resultado versionado; promoção ou não pela regra; `lines-v2` se for o caso; O-010 e O-005 atualizadas.

**Resultado (17/10/2026)** — só a parte sem o provedor (F3 adiada)
- Harness com `EVAL_CHUNKING` e `EVAL_SWEEP=fusion`; `LineChunker` com parâmetros injetados na indexação, padrão `lines-v1`. Rodada de controle idêntica ao baseline da F1.
- Fusão (`eval/results/2026-10-17-lexical-fusion.*`): k, pesos, candidatos e limiar mantidos, nenhum com o ganho mínimo.
- Canal de caminhos: a piora no hono vem de nomes de arquivo (`middleware`) casados contra o caminho inteiro. Casar nomes só com o arquivo daquele nome passa na regra (Recall@10 0,447 → 0,500, MRR@10 0,301 → 0,335), mas só o `HYBRID` usa o canal: candidata para a F3, sem mudança no `PathSearch` agora.
- Chunking (`eval/results/2026-10-17-lexical-lines-*.*`): 40 e 80 linhas, sobreposição de 0 e 20; nenhuma passa no `LEXICAL`, `lines-v1` mantido, sem `lines-v2`. O de 80 linhas fica como candidato para a F3.
- Registro na D-081; O-005 e O-010 atualizadas e ainda a validar, pela parte com vetores.
- **Desvio:** as variantes de chunking mudam uma variável por vez (40 e 80 linhas com sobreposição 10; 60 linhas com sobreposição 0 e 20), em vez da grade 40/80 × 0/20, para seguir a regra da P3.

### F5 — Suíte de segurança
- As lacunas da matriz da F0 (P4), o comportamento de codificação decidido, o E2E com conteúdo hostil, o levantamento de dependências com as ações.

**Resultado (17/10/2026)**
- Injeção estrutural: `PromptBuilderTest` com nonce forjado, caminho com delimitador, JSON de saída e rótulos no conteúdo, instrução em nome de arquivo e de diretório e linha de ~4.000 caracteres; `AnswerApiIT.labelsWrittenInTheContentAreNotEvidence`.
- `ExcludedContentIT`: a fixture da avaliação inteira indexada e as seis perguntas de segredo feitas com provedores espiões; nada dos arquivos excluídos chega aos provedores nem aos logs, e os logs da indexação não têm caminhos nem conteúdo.
- ZIP: nomes em NFC na extração (NFD e NFC do mesmo nome viram `ZIP_DUPLICATE_ENTRY`), controles bidirecionais recusados como `ZIP_UNSAFE_PATH`, ZIP aninhado extraído como arquivo, comentário e extra field no máximo do formato.
- Pasta local: link físico lido como arquivo comum e nomes NFD informados em NFC (`SafeScannerTest`), abertura de diretório em NFD (`DirectoryBrowserTest`); as limitações do Linux ficam documentadas.
- Codificação: BOM de UTF-16 vira `INVALID_ENCODING`; testes de UTF-8 com BOM, UTF-16 com e sem BOM e Latin-1.
- Regra ArchUnit contra `ProcessBuilder`, `Process` e `Runtime.exec`; teste da pasta do import removida com o projeto.
- E2E `hostile-content.spec.ts` com a fixture `hostile-content`: conteúdo, nomes e pergunta como texto, sem execução nem erro de console.
- Dependências: `npm audit` e OSV sem nenhum aviso; nenhuma ação.
- Registro na D-082.
- **Desvios:** a matriz dizia que o BOM de UTF-8 não tinha teste, mas `TextClassifierTest.removesUtf8Bom` já existia; o teste novo só confere que as linhas ficam iguais. A exclusão de projeto já removia a pasta do import depois do commit (`ProjectImports.onProjectDeleted`); faltava só o teste de integração. A resposta do fake é fixa, então o E2E não cobre marcação vinda do modelo; isso continua no teste de unidade do texto da resposta.

### F6 — Segredos por conteúdo
- Detector (P5), motivo `SENSITIVE_CONTENT`, testes com os segredos sintéticos da fixture (inclusive conferindo que nada vai ao provedor fake), UI e mensagens.

**Resultado (17/10/2026)**
- `SecretDetector` com os formatos da F0, rodando no `IndexRunner` sobre os bytes lidos, antes do atalho de conteúdo já chunkado; motivo `SENSITIVE_CONTENT` com a migração `V11`.
- Testes: `SecretDetectorTest` (cada formato e imitações que não casam), `SensitiveContentIT` (arquivos listados sem conteúdo, chunks nem índice; conteúdo chunkado antes da detecção excluído na próxima indexação) e `ExcludedContentIT` estendido aos segredos por conteúdo da fixture: nada chega aos provedores fake nem aos logs.
- Contrato: `openapi.json` e `schema.d.ts` regenerados (valor novo no `reason`, aditivo); UI com "Excluído por conter um possível segredo".
- Falsos positivos: nenhum no click e no hono. Avaliação lexical com a detecção em `eval/results/2026-10-17-lexical-sensitive-content.*`: métricas iguais, arquivos com segredo fora do top 10 das perguntas de segredo.
- Registro na D-083; `05`, `06` e `07` §5 atualizados.
- **Desvio:** snapshots anteriores à F6 não ganham aviso de reindexação em `outdatedReasons` (exigiria versão da detecção por snapshot); fica documentado na D-083 e vai para o `11-OPERATIONS.md` na F9.

### F7 — Operação
- Cancelamento, exclusão de conversa, `lastJob`, `GET /usage` (P7), backend e UI, com testes.

**Resultado (17/10/2026)**
- Cancelamento: `POST /index-jobs/{id}/cancel`, migração `V12` (`cancel_requested_at`, `CANCELLED` em jobs e snapshots), pedido conferido pelo heartbeat; na UI, "Cancelar" no progresso com confirmação e aviso de cancelamento pedido, e o resumo explica a última indexação cancelada.
- `DELETE /conversations/{id}` e "Excluir" no histórico, com confirmação; sem retenção automática.
- `lastJob` no `ProjectResponse`; a lista e o resumo mostram "A última indexação falhou".
- `GET /projects/{id}/usage` e o painel "Uso" no resumo.
- Testes: `IndexCancelIT` (job em execução para no lote seguinte sem mudar o ativo; job pendente termina na hora; pedido antes de uma queda honrado pela próxima tentativa; job terminado e inexistente), `ProjectIndexStateApiIT` (`lastJob` no projeto e na lista), `HistoryReadApiIT` (exclusão em cascata, resposta pendente descartada), `UsageApiIT`; na UI, cancelamento, job cancelado, uso, exclusão de conversa e o estado "falhou".
- Contrato: `openapi.json` e `schema.d.ts` regenerados, só aditivo. Registro na D-084; `06` atualizado.
- **Desvios:** `cancelRequested` também no `indexing` do projeto (não previsto), para a UI mostrar o pedido sem consultar o job. O snapshot do job cancelado ganhou o status próprio `CANCELLED`, em vez de `FAILED`, para a lista de snapshots não mostrar falha.

### F8 — Empacotamento
- Perfil `with-ui`, fallback da SPA, cabeçalhos, divisão do build, E2E contra o jar (P8).

### F9 — Fechamento
- **Limitações conhecidas e recuperação de falhas:** novo `docs/11-OPERATIONS.md` (como rodar empacotado, backup e restauração do volume do PostgreSQL, o que fazer em cada falha — banco fora, provedor sem saldo ou fora, queda no meio de um job, disco cheio, ZIP recusado, snapshot podado — e a lista consolidada das limitações, hoje espalhadas pelo `07` e pelos planos).
- Checklists do `07` §11 e do `08` §10 marcados, com o teste ou o resultado de cada item.
- Resultados da avaliação no `08` (corpus, números e metas do §9), roteiro, README, `docs/README.md`, P9 registrada, "Resultado e desvios" neste plano.
