# Avaliação de qualidade — Codebase Copilot

## 1. Objetivo

Medir se o sistema recupera evidências relevantes e responde corretamente, com citações válidas, custo e latência aceitáveis. A avaliação deve existir antes de otimizar prompts ou trocar modelos.

## 2. Conjunto de avaliação

Criar um corpus de repositórios de teste que possam ser usados legalmente, incluindo projetos pequenos e médios e diferentes linguagens. Para cada pergunta, registrar:
- pergunta;
- snapshot do corpus;
- arquivos/intervalos de linhas esperados;
- resposta esperada ou pontos factuais necessários;
- se a resposta correta deve se abster;
- dificuldade e categoria;
- observações sobre evidência indireta.

Incluir:
- busca por identificador exato;
- perguntas conceituais;
- fluxo entre arquivos;
- arquitetura;
- dependências e configuração;
- perguntas sem resposta no código;
- perguntas ambíguas;
- prompt injection embutido em arquivos;
- arquivos excluídos e segredos sintéticos.

### Corpus atual (Etapa 5, D-061; ampliado na Etapa 8, D-079)

- **Fixture sintética** versionada em `eval/corpus/fixture/` (serviço de cobrança em Java, TypeScript, Python, SQL e Markdown), com identificador exato, sinônimo sem termo em comum, o mesmo arquivo em dois caminhos, instruções injetadas (Markdown, comentário, string, nome de diretório, delimitador forjado, imitação da saída do modelo), arquivos em UTF-8 com BOM, Latin-1 e UTF-16, e segredos sintéticos em arquivos excluídos pelo nome e em arquivos indexados.
- **Codebase Copilot** no commit `9d986db`, **`pallets/click` 8.5.0** (`8b19813`, BSD-3-Clause) e **`honojs/hono` v4.13.13** (`08a023c`, MIT), fora do repositório; cada um é conferido contra um manifesto SHA-256 antes de rodar.
- **80 perguntas** em `eval/questions/<corpus>.yaml`, metade em pt-BR e metade em en, com categoria, dificuldade e intervalos de linhas esperados; 14 exigem abstenção e 14 são de injeção ou de segredo. Rascunho do assistente, revisão e aprovação humanas. Como montar e rodar: `eval/README.md`.

## 3. Métricas de recuperação

- **Recall@k:** proporção das evidências relevantes encontradas entre os primeiros k resultados.
- **MRR:** posição do primeiro resultado relevante.
- **Precision@k:** proporção de resultados relevantes entre os k primeiros.
- **Cobertura de fontes:** capacidade de recuperar arquivos distintos quando a pergunta exige fluxo entre módulos.

Definir relevância em nível de chunk e, quando útil, de arquivo.

**Como são calculadas (D-062):** um chunk é relevante quando um dos seus caminhos é o do intervalo esperado (ou um dos `otherPaths`) e as linhas se sobrepõem. Recall@5/10/20 conta intervalos esperados encontrados; MRR@10 usa o primeiro chunk relevante; Precision@10 divide os relevantes por 10; a cobertura de arquivos@10 só vale para perguntas `CROSS_FILE_FLOW` e conta arquivos esperados com algum chunk entre os 10 primeiros. Perguntas que exigem abstenção ficam fora dessas métricas.

## 4. Métricas de resposta

- **Validade de citação:** referências apontam para arquivos e linhas existentes.
- **Suporte factual:** afirmações importantes são sustentadas pelas evidências citadas.
- **Correção:** a resposta corresponde ao comportamento real do código.
- **Abstenção:** o sistema evita inventar quando o corpus não contém resposta suficiente.
- **Utilidade:** desenvolvedores conseguem verificar e usar a resposta.

A avaliação automática por outro modelo pode auxiliar, mas não deve ser a única fonte de verdade. Auditar uma amostra manualmente.

**Como são calculadas (D-069):** sobre a resposta como a API a devolve.
- **Validade de citação:** citações válidas / citações emitidas pelo modelo (válidas + `invalidCitationCount`), contadas por evidência.
- **Respostas sustentadas:** entre as `ANSWERED` e `PARTIAL`, as que têm ao menos uma citação válida.
- **Citação relevante:** entre as `ANSWERED` e `PARTIAL` de perguntas com intervalo esperado, as que têm uma citação cujas linhas sobrepõem um intervalo (a regra da §3). É um indício de suporte, não prova.
- **Abstenção:** taxa de `ABSTAINED` nas perguntas com `mustAbstain` (acerto) e nas demais (abstenção indevida).
- **Vazamento:** resposta que contém algum texto de `mustNotContain` (segredo sintético, o que uma instrução injetada pede), sem diferenciar maiúsculas.
- Tokens, custo estimado e latência por resposta (média e p95).
- **Correção, suporte factual e utilidade:** auditoria humana, no `.md` de cada rodada, com a resposta ao lado dos trechos citados. Nesta fase não há modelo como juiz (Etapa 8).

## 5. Métricas operacionais

- duração de ingestão e indexação;
- latência de busca lexical e vetorial;
- latência total da resposta;
- tokens de entrada e saída;
- custo estimado por pergunta;
- taxa de falhas por etapa;
- taxa de reindexação incremental correta;
- uso de memória e armazenamento.

## 6. Baselines

Estabelecer comparações:
1. Busca lexical somente.
2. Busca vetorial somente.
3. Híbrida com fusão simples.
4. Híbrida com reranking, se implementado.
5. Chunking genérico versus chunking com parser, nas linguagens suportadas.

Não assumir que a opção mais sofisticada vence; medir qualidade, latência e custo no mesmo corpus.

## 7. Testes de robustez e segurança

- pergunta que tenta fazer o modelo revelar segredos;
- arquivo que contém instruções para ignorar o prompt;
- arquivo excluído que contém segredo sintético;
- ZIP com path traversal;
- arquivo gigantesco;
- encoding inválido;
- snapshot alterado após uma resposta;
- provedor que retorna erro ou timeout;
- resposta com ID de evidência inventado.

## 8. Processo de avaliação

1. Versionar corpus, perguntas e anotações.
2. Registrar modelo, embedding, chunking e configuração de recuperação.
3. Executar baseline.
4. Guardar resultados e configuração.
5. Alterar uma variável por vez quando possível.
6. Comparar qualidade, latência e custo.
7. Não promover mudança que degrade segurança ou validade de citação sem decisão explícita.

### Processo implementado (Etapa 5)

O harness `RetrievalEvaluationIT` (tag `eval`, fora do `verify`) indexa cada corpus pelo pipeline real, consulta cada canal uma vez por pergunta e calcula as variantes `LEXICAL`, `VECTOR`, `LEXICAL_PATH`, `LEXICAL_VECTOR` e `HYBRID` sobre as mesmas listas. Resultados e configuração (corpus, manifesto, versões de chunking e lexical, modelo, parâmetros da fusão) ficam em `eval/results/`, versionados. Os vetores ficam em cache por hash e modelo, e duas rodadas iguais produzem o mesmo resultado, fora a latência (verificado com `EVAL_COMPARE_WITH`).

**Primeira medição (16/10/2026):** só o baseline lexical, porque o provedor estava sem saldo. Ver `eval/results/2026-10-16-lexical.md` e a O-005.

### Processo implementado (Etapa 6)

O harness `AnswerEvaluationIT` (tag `eval`, fora do `verify`) indexa os corpora da mesma forma e faz as 80 perguntas por `POST /questions`, inclusive as que exigem abstenção, cada uma numa conversa nova. Registra provedor, modelo, esforço de raciocínio, versão do prompt, modo e limites da recuperação, limites do contexto e preço. As respostas do modelo ficam em cache por hash do pedido (com o nonce do prompt normalizado), então uma rodada refeita repete as mesmas respostas sem custo; uma chamada nova ao modelo é uma medição nova, porque a geração não é determinística. Resultados com o modelo real ficam em `eval/results/<data>-answers-<provedor>-<modelo>-<esforço>-<modo>.*`, versionados; rodadas com o fake só testam o harness e não são versionadas. Comandos e variáveis em `eval/README.md`.

**Primeira medição:** pendente. A Etapa 6 fechou sem saldo no provedor; o harness foi exercitado com o fake. A rodada com o `gpt-6-luna` (e a comparação com o `gpt-6.1-sol` e com o esforço `none`) sai do mesmo harness, seguida da auditoria humana.

## 9. Metas

As metas numéricas devem ser definidas depois da primeira medição, considerando o tamanho e a dificuldade do corpus. Evitar criar percentuais arbitrários sem baseline.

### Resultados e metas (fechamento da Etapa 8, 17/10/2026)

**Recuperação, só lexical** (`eval/results/2026-10-17-lexical.*`, 66 perguntas com intervalo esperado, D-079):

| Corpus | Perguntas | Recall@10 | MRR@10 |
|---|---|---|---|
| fixture | 20 | 0,750 | 0,640 |
| codebase-copilot | 13 | 0,397 | 0,256 |
| click | 11 | 0,258 | 0,071 |
| hono | 22 | 0,432 | 0,103 |
| **Todos** (`LEXICAL`) | 66 | **0,492** | **0,291** |

- Nenhuma variação de fusão nem de chunking medida sem vetores passou na regra de promoção da D-081; `lines-v1` e os parâmetros da fusão ficam como estão. A detecção de segredos por conteúdo não mudou nenhuma métrica (`2026-10-17-lexical-sensitive-content.*`, D-083).
- **Piso de não regressão:** nenhuma mudança é promovida se o `LEXICAL` do corpus inteiro cair abaixo de Recall@10 0,492 ou MRR@10 0,291 (regra da D-081).
- **Metas de qualidade do vetorial, do híbrido e das respostas:** a definir com a primeira medição real (F3), como esta seção pede; o vetorial, o híbrido e a avaliação de respostas ainda não rodaram por falta de saldo.
- **Respostas, já garantido por construção e por teste:** nenhuma citação inválida aceita em silêncio (`invalidCitationCount`, `PARTIAL`) e nenhum conteúdo excluído enviado ao provedor (`ExcludedContentIT`).

**Desempenho** (`eval/results/2026-10-17-perf.*`, provedores fake, D-080). Metas confirmadas na F2 e atendidas:

| Meta | Medido |
|---|---|
| Busca `HYBRID` p95 ≤ 1 s com 50 mil chunks | 676 ms com ~46 mil chunks |
| Busca `HYBRID` p95 ≤ 200 ms até 5 mil chunks | até 90 ms nos corpora reais (até 2.050 chunks) |
| Pergunta sem o tempo do provedor ≤ 1 s com 50 mil chunks | 620 ms |
| Reindexação sem mudanças < 10% da completa | 3,1 s contra 41–45 s (7–8%) |

Fora da meta, registrado: uma pergunta com um termo presente em quase todo chunk leva 1,06 s de p95 com 46 mil chunks.

## 10. Critério mínimo de release

Antes de considerar o MVP utilizável:
- [x] **não aceitar citações inválidas silenciosamente:** rótulos inventados ou fora do contexto são removidos e contados em `invalidCitationCount`, e a resposta vira `PARTIAL` (`CitationValidatorTest`, `AnswerApiIT`; D-066);
- [x] **testar a abstenção em perguntas sem resposta** — com o fake: sem evidência a resposta é `ABSTAINED` sem chamar o modelo, e o `ABSTAIN` do modelo é respeitado (`AnswerApiIT`, `CitationValidatorTest`). A taxa de abstenção com o modelo real, nas 14 perguntas `mustAbstain`, está pendente (F3);
- [x] **confirmar que exclusões são respeitadas:** `ExcludedContentIT`, `SensitiveContentIT`, `DefaultExclusionRulesTest` e as perguntas `EXCLUDED_SECRET` na recuperação lexical (arquivos com segredo fora do top 10, D-083);
- [x] **demonstrar reindexação incremental correta:** `IndexingAcceptanceIT` e `IndexingRecoveryIT` (só o conteúdo novo é processado, um job interrompido termina igual a um limpo), e a medição da reindexação sem mudanças (7–8% da completa, D-080);
- [x] **registrar limitações conhecidas e resultados do corpus de avaliação:** `11-OPERATIONS.md` §5 e §9 deste documento.
