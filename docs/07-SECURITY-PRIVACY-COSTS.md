# Segurança, privacidade e custos

## 1. Modelo de ameaça

O repositório importado é conteúdo não confiável. Pode conter:
- segredos;
- código malicioso;
- instruções de prompt injection;
- arquivos enormes ou compactados de forma abusiva;
- caminhos malformados;
- dados pessoais ou propriedade intelectual.

O objetivo é limitar o que o sistema lê, armazena, registra e envia para provedores.

## 2. Princípios de segurança

- Não executar código importado.
- Restringir leitura à raiz selecionada.
- Validar e normalizar caminhos.
- Aplicar limites de tamanho e quantidade.
- Tratar conteúdo do repositório como dados, nunca como instruções.
- Não enviar arquivos excluídos ao modelo.
- Não registrar segredos nem conteúdo completo por padrão.
- Minimizar permissões e dependências.
- Tornar explícito quando um provedor remoto recebe trechos.

## 3. Importação de pasta

O mecanismo de acesso à pasta deve:
- canonicalizar a raiz;
- rejeitar caminhos que escapem da raiz;
- tratar links simbólicos com política explícita;
- limitar profundidade, arquivos e tamanho;
- evitar seguir diretórios de dependências e artefatos;
- lidar com arquivos alterados durante a leitura;
- não executar hooks, scripts ou ferramentas do projeto.

A seleção de pasta segue a D-018: o backend expõe apenas raízes permitidas configuradas, a UI navega dentro delas e o cliente envia somente caminhos relativos, que o backend canonicaliza e valida. Links simbólicos não são seguidos. O browser nunca envia caminhos locais arbitrários ao backend.

Política implementada na Etapa 2 (D-037):
- cada segmento do caminho é verificado sem seguir links; um caminho que passa por link é recusado (navegação: `404 DIRECTORY_NOT_FOUND`; inventário: `409 SOURCE_IS_SYMLINK`);
- na varredura, links aparecem como `SYMLINK` e nunca são seguidos, nem para dentro da raiz;
- FIFOs, sockets e dispositivos aparecem como `SPECIAL_FILE` e nunca são abertos;
- arquivos são lidos apenas como bytes (amostra de 8 KB aberta com `NOFOLLOW_LINKS`), com limites de entradas, profundidade e tamanho;
- o `DATA_DIR` não pode estar dentro de uma raiz nem contê-la, para que arquivos extraídos nunca apareçam na navegação (D-028).

**Limitações conhecidas:** links físicos e pontos de montagem dentro da raiz não são detectados, e uma troca concorrente de diretório por link durante a varredura (TOCTOU) não é totalmente evitável sem `openat`. O modelo de ameaça é conteúdo malicioso, não um atacante concorrente no disco local.

Leitura do conteúdo na indexação (Etapa 3, D-042):
- só os arquivos incluídos pela varredura são lidos, cada um uma única vez, sem seguir links e só se ainda for um arquivo regular (um FIFO ou link colocado no lugar depois da varredura não é aberto);
- a leitura é limitada ao tamanho máximo do arquivo; atributos diferentes antes e depois da leitura marcam o arquivo como `FAILED` (`CHANGED_DURING_READ`), e ele não é indexado;
- o conteúdo é tratado apenas como texto UTF-8; nada é interpretado nem executado;
- logs da indexação trazem apenas ids, contagens, códigos e durações, nunca caminhos ou conteúdo.

## 4. Importação ZIP

Mitigações obrigatórias:
- prevenir ZIP Slip e path traversal;
- rejeitar caminhos absolutos;
- limitar número de entradas;
- limitar tamanho total descompactado e tamanho por entrada;
- limitar razão de expansão;
- tratar links simbólicos com cautela;
- impedir escrita fora da pasta do job (`DATA_DIR/imports/<importJobId>`, D-028);
- apagar a pasta do job quando ele falhar ou for substituído; a do import atual é retida como origem do projeto (D-033).

Implementação da Etapa 2:
- os limites são contados pelos bytes realmente gravados, nunca pelos tamanhos declarados no arquivo, o que também cobre entradas sobrepostas;
- o nome de cada entrada é validado (sem `/` inicial, letra de unidade, `..`, `\` ou caracteres de controle, com profundidade e comprimento máximos), e o destino resolvido precisa ficar dentro da pasta do job;
- arquivos são criados com `CREATE_NEW`: entradas repetidas, ou que colidem por maiúsculas em sistemas sem distinção de caixa, recusam o ZIP;
- o extrator nunca cria links: uma entrada de link vira arquivo comum;
- ZIPs inválidos, truncados ou criptografados são recusados;
- mensagens de erro e logs nunca contêm nomes de entradas.

## 5. Segredos e arquivos excluídos

Padrões padrão devem considerar, entre outros:
- `.env` e variações;
- chaves privadas e certificados;
- arquivos de credenciais;
- diretórios de dependências;
- artefatos de build;
- caches;
- binários e arquivos de mídia.

Os padrões precisam ser configuráveis e exibidos ao usuário. A detecção de segredos é defesa adicional, não garantia perfeita. Nunca prometer que todos os segredos serão detectados.

Segredos são excluídos por padrões de nome, em regras que o usuário não pode desativar (`SENSITIVE`, D-036), e, desde a Etapa 8, também por conteúdo: um arquivo com bloco de chave privada ou com uma chave de formato publicado (AWS, GitHub, Stripe, Slack, OpenAI) vira `UNSUPPORTED` com o motivo `SENSITIVE_CONTENT`, antes de chunks, embeddings e qualquer envio, sem que a detecção possa ser desligada (D-083). Só padrões de alta precisão: senhas, chaves sem formato próprio e segredos de alta entropia continuam passando. A UI e a API dizem "excluído por conter um possível segredo", nunca "segredo detectado", e não mostram o trecho nem a linha.

## 6. Prompt injection

- Delimitar claramente os trechos de código no prompt.
- Instruir o modelo a ignorar instruções contidas no repositório.
- Não permitir que o conteúdo recuperado altere política, ferramentas ou permissões.
- Não oferecer ferramentas que executem comandos no MVP.
- Validar citações no backend independentemente da saída do modelo.
- Tratar README, comentários e arquivos de instruções como conteúdo a analisar, não como autoridade.

**Implementado na Etapa 6 (D-066, D-069):**
- Cada evidência e a pergunta vão num bloco delimitado por um nonce aleatório de 128 bits por pedido; um arquivo com linhas de delimitador não fecha o próprio bloco, porque não conhece o nonce. As instruções dizem que o conteúdo dos blocos é dado, nunca instrução.
- A geração não tem ferramentas, e a saída é um JSON estrito; o backend valida rótulos e linhas e decide o status final.
- Testes: `AnswerApiIT` confere que as instruções injetadas ficam dentro do bloco da evidência; a fixture de avaliação tem um `CONTRIBUTING.md` com instrução injetada, e a avaliação de respostas falha a pergunta cuja resposta repete o que a injeção pede (`mustNotContain`).
- Limitação conhecida: a delimitação reduz o risco, não o elimina; o modelo ainda pode seguir uma instrução do repositório. A medição com o modelo real está pendente (`08` §8).

## 7. Privacidade e provedores

**Local-first não significa necessariamente offline.** Se for usado um provedor remoto, trechos selecionados de código e a pergunta podem ser enviados a esse provedor para embeddings ou geração.

A interface deve informar:
- provedor ativo;
- quais tipos de conteúdo podem ser enviados;
- como exclusões funcionam;
- como remover projeto e índice;
- limites de retenção aplicáveis ao provedor, quando conhecidos.

Modelos locais são uma direção planejada, mas não devem ser apresentados como funcionalidade implementada antes de existirem.

### Embeddings (Etapa 4)

O provedor é escolhido por `EMBEDDING_PROVIDER` (`app.embeddings.provider`), D-047 e D-054:

| Provedor | O que sai da máquina |
|---|---|
| `none` (padrão) | Nada. Não há busca vetorial (`409 EMBEDDINGS_NOT_AVAILABLE`). |
| `fake` | Nada. Vetores calculados localmente, só para testes e demonstração. |
| `openai` | O texto dos chunks de arquivos `INDEXED` sem vetor no cache e o texto de cada pergunta de busca, enviados a `app.embeddings.base-url` com a chave `EMBEDDING_API_KEY`. |

- Arquivos excluídos (segredos, `.env`, dependências), não suportados, binários ou com falha nunca são enviados.
- Cada texto é enviado no máximo uma vez por modelo: o cache por `chunk_hash` vale para todos os projetos (D-052).
- `app.embeddings.base-url` decide o destino dos dados e da chave: só `https`, ou `http` para `localhost`/`127.0.0.1`; qualquer outro endereço impede a inicialização (D-047).
- Política da OpenAI para `/v1/embeddings` (consultada em 16/10/2026): os dados não são usados para treino, ficam retidos por até 30 dias para monitoramento de abuso e o endpoint é elegível a Zero Data Retention mediante acordo (D-047).
- Logs trazem só quantidades, códigos e duração; nunca texto de chunk, pergunta ou chave (D-054). O log do SDK da OpenAI fica desligado, mesmo com `OPENAI_LOG` no ambiente.
- Para não enviar nada: `EMBEDDING_PROVIDER=none`. Os snapshots continuam sendo criados; ao configurar um provedor depois, o projeto informa `EMBEDDINGS_MISSING` até ser reindexado.

### Geração (Etapa 6)

O provedor de chat é escolhido por `GENERATION_PROVIDER` (`app.generation.provider`), separado dos embeddings (D-063):

| Provedor | O que sai da máquina |
|---|---|
| `none` (padrão) | Nada. `POST /questions` responde `409 GENERATION_NOT_AVAILABLE`. |
| `fake` | Nada. Respostas determinísticas, só para testes e demonstração. |
| `openai` | A cada pergunta: as instruções, a pergunta e os trechos escolhidos como evidência (até 8 por padrão, cerca de 6.000 tokens), enviados a `app.generation.base-url` com a chave `GENERATION_API_KEY`. |

- Só trechos de arquivos `INDEXED` podem virar evidência; arquivos excluídos, segredos por nome, binários e não suportados nunca são enviados. Pergunta sem evidência não chama o modelo.
- Endpoint `/v1/chat/completions` com `store=false`. Pela política da OpenAI consultada em 16/10/2026: os dados não são usados para treino, ficam retidos por até 30 dias para monitoramento de abuso, o cache de prompts pode manter dados criptografados por até 24 horas e o endpoint é elegível a Zero Data Retention mediante acordo (D-063).
- `app.generation.base-url` segue a mesma regra dos embeddings: só `https`, ou `http` para `localhost`/`127.0.0.1`.
- A pergunta, a resposta e a cópia das evidências ficam só no banco local (histórico), apagadas com o projeto (D-021). Logs trazem ids, status, quantidades, tokens e durações; nunca pergunta, resposta, prompt ou trecho (D-054, D-067). O log do SDK fica desligado.
- Para não enviar nada: `GENERATION_PROVIDER=none` (e `EMBEDDING_PROVIDER=none` para a pergunta também não sair na busca vetorial).

### O que a interface mostra (Etapa 7)

`GET /system/providers` diz, para embeddings e geração, o provedor (`none`, `fake`, `openai`), o modelo e se os dados saem da máquina (`remote`: `openai` com `base-url` fora de `localhost`/`127.0.0.1`). Nunca devolve a chave, a `base-url` nem os preços (D-073). A UI usa isso para o aviso antes de indexar ou perguntar com um provedor remoto (`03` §7).

### Acesso ao backend local (Etapa 7)

A API não tem autenticação: quem alcança a porta lê o código indexado e pode apagar projetos. Com a UI no browser, uma página qualquer também pode tentar chamar `localhost`. Mitigações (D-072):
- o backend escuta só em `127.0.0.1` (`SERVER_ADDRESS`; trocar por `0.0.0.0` expõe a API à rede e exige ajustar `app.http.allowed-hosts`);
- requisições com `Host` fora de `app.http.allowed-hosts` (padrão `localhost`, `127.0.0.1`, `[::1]`, em qualquer porta) respondem `403 FORBIDDEN_HOST`, o que barra o DNS rebinding;
- `POST`, `PUT`, `PATCH` e `DELETE` com cabeçalho `Origin` fora de `app.http.allowed-origins` (padrão: os servidores do Vite em `localhost`/`127.0.0.1`, portas 5173 e 4173) respondem `403 FORBIDDEN_ORIGIN`, o que barra o CSRF, inclusive o upload `multipart`, que o browser envia sem preflight. Sem `Origin` (curl, scripts), passa;
- nenhuma origem cruzada é liberada por CORS: o browser continua impedindo outras páginas de ler as respostas;
- o proxy do Vite repassa a `Origin` do browser sem reescrever, para que um CSRF que passe por ele também seja recusado; o próprio Vite recusa `Host` desconhecido.

**Limitação conhecida:** outro processo local, ou uma página servida numa origem permitida, continua tendo acesso; o modelo de ameaça é a rede e páginas de terceiros, não a própria máquina.

## 8. Segredos de configuração

- Usar variáveis de ambiente ou mecanismo de secrets local.
- Manter `.env` fora do Git.
- Fornecer `.env.example` sem valores reais.
- Não colocar chaves em logs, respostas de erro ou telemetria.
- Rotacionar chaves que tenham sido expostas.

## 9. Custos

O custo pode incluir:
- embeddings iniciais;
- embeddings de alterações;
- geração por pergunta;
- reranking, caso adicionado;
- infraestrutura de armazenamento.

Medidas:
- cache por hash;
- batch de embeddings;
- reindexação incremental;
- limites de tokens e evidências;
- configuração de modelos;
- estimativa baseada em preços configurados e uso reportado.

Se o provedor não informar tokens ou se o preço não estiver configurado, mostrar “indisponível” ou “estimativa parcial”, não um número inventado.

### Embeddings (Etapa 4)

- **Preço:** `text-embedding-3-small` custa US$ 0,02 por 1M de tokens de entrada (página oficial de preços, consultada em 16/10/2026; D-047). Indexar 1M de tokens de código custa cerca de US$ 0,02; reindexar sem mudanças custa zero.
- **Uso registrado:** `embedding_tokens` do snapshot soma os tokens reportados pelo provedor no job. Se algum lote não reportar, fica `null` (D-054).
- **Limite por job:** `app.embeddings.max-chunks-per-job` (padrão 50.000) conta os chunks a enviar. Acima dele, o job falha com `EMBEDDING_CHUNK_LIMIT_EXCEEDED` antes de qualquer chamada, e o snapshot ativo não muda.
- **Cache e reindexação:** só chunks sem vetor do modelo configurado são enviados; trocar de modelo exige reenviar todos os chunks do snapshot (D-050).
- **Falhas:** repetição só para limite de taxa e indisponibilidade, até `app.embeddings.max-retries` (D-051); cota esgotada falha na hora com `EMBEDDING_QUOTA_EXCEEDED`. Vetores gravados antes de uma falha ficam no cache e não são pagos de novo.
- **Limites de entrada:** chunks de até 4.000 caracteres e lotes validados na inicialização contra os limites do modelo (8.192 tokens por entrada, 2.048 entradas e 300.000 tokens por requisição), contando um token por caractere (D-054).
- **Limitação conhecida:** um chunk recusado pelo provedor (`INVALID_REQUEST`, por exemplo um texto fora do ASCII que passe do limite de tokens) falha o job inteiro com `EMBEDDING_REQUEST_REJECTED`; o lote não é dividido para descobrir qual texto foi recusado. Isolar o lote fica para quando o uso real mostrar o problema.

### Geração (Etapa 6)

- **Preço:** `gpt-6-luna` custa US$ 0,10 por 1M de tokens de entrada e US$ 0,50 de saída; `gpt-6.1-sol`, US$ 2,00 e US$ 10,00 (página oficial, consultada em 16/10/2026; D-063). O preço usado na estimativa vem de `GENERATION_PRICE_INPUT_PER_MILLION` e `GENERATION_PRICE_OUTPUT_PER_MILLION`; o `.env.example` traz os do luna.
- **Custo por pergunta:** estimado, no teto, em ~US$ 0,0017 com o luna (~6.700 tokens de entrada e até 2.000 de saída, raciocínio incluído) e ~US$ 0,033 com o sol. A rodada da avaliação (40 perguntas) fica abaixo de US$ 0,15 com o luna. **O custo medido está pendente:** a avaliação com o modelo real ainda não rodou, por falta de saldo (D-069).
- **Uso registrado:** cada resposta guarda os tokens informados pelo provedor e o custo estimado (`ESTIMATED`), ou `UNKNOWN` sem tokens ou sem preço; os tokens do embedding da pergunta ficam em `retrieval_metadata`, fora do custo (D-067).
- **Limites:** `maxEvidence` (padrão 8, até 20), `app.answering.max-context-tokens` (6.000), `app.generation.max-output-tokens` (2.000, com o raciocínio), `app.generation.reasoning-effort` (`low`) e `app.generation.timeout` (60 s). Sem repetição de chamadas: uma falha vira resposta `FAILED`, sem novo custo.

## 10. Exclusão e retenção

Ao excluir projeto, remover chunks, embeddings, snapshots e histórico associado conforme a política escolhida. Documentar se backups locais ou logs mantêm dados residuais e por quanto tempo.

Desde a Etapa 3, o conteúdo dos arquivos indexados fica no PostgreSQL (`file_contents` e `chunks`), deduplicado por projeto. A remoção do projeto apaga tudo em cascata; snapshots antigos são podados e o conteúdo sem snapshot é removido ao fim de cada indexação (D-039, D-044). Backups do banco mantêm cópias desse conteúdo; isso fica a cargo de quem opera o ambiente.

## 11. Checklist antes de lançar

- [ ] Nenhum código do repositório é executado.
- [ ] ZIP Slip e bombas de descompressão são testados.
- [ ] Caminhos ficam confinados à raiz.
- [ ] Exclusões são aplicadas antes de embeddings e geração.
- [ ] Conteúdo sensível não é logado.
- [ ] Prompt injection é coberto por testes.
- [ ] Citações são validadas no backend.
- [ ] Usuário é informado sobre envio a provedores remotos.
- [ ] Backend só aceita conexões e requisições locais (D-072).
- [ ] Exclusão de projeto remove os dados esperados.
- [ ] Dependências e configurações têm revisão de segurança.
