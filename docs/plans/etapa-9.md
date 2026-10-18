# Plano — Etapa 9: Interface premium

**Status:** plano aprovado em 17/10/2026 com todas as recomendações (branch `etapa-9`, a partir do `main` com a Etapa 8). F0 concluída no mesmo dia, com os ajustes registrados nela. As propostas P0 a P13 viram D-087 em diante, registradas em `10-DECISIONS.md` na fatia indicada em cada uma.
**Referências:** `01-PRD.md` (F-01 a F-09), `03-UX-ONBOARDING.md` (todo), `06-API-CONTRACTS.md` §5, §7 a §10 e §12, `07-SECURITY-PRIVACY-COSTS.md` §2, `11-OPERATIONS.md` §2 e §5, `docs/plans/etapa-7.md` (P7 a P10 e pendências), D-025, D-070, D-075, D-076, D-077, D-078, D-084, D-085, D-086.

## Por que esta etapa

As oito etapas entregaram um MVP correto, seguro e acessível, mas com uma interface mínima: o objetivo da Etapa 7 era o fluxo funcionar, e o `03` §8 deixou a identidade visual para depois. Esta etapa faz esse "depois": transforma as telas em um produto agradável de usar, sem perder nada do que já foi garantido (evidências verificáveis, conteúdo tratado como não confiável, CSP estrita, WCAG AA, erros pelo `code`).

### Diagnóstico da interface atual

| Área | Hoje | Efeito para quem usa |
|---|---|---|
| Navegação | Uma barra com o nome do produto; o resto é "← voltar" e botões soltos em cada tela | A pessoa não sabe onde está nem o que existe além da tela atual; não há caminho direto entre projetos |
| Resumo do projeto | Cerca de dez seções empilhadas (progresso, falha, desatualizado, indexar, snapshot, não indexados, snapshots, uso) | O estado importante se perde no meio; a próxima ação não fica evidente |
| Visual | Fonte do sistema, um raio, poucas cores; estados como borda colorida; botões sem ícone nem estados de hover; "Carregando…" em texto | Aparência de protótipo; hierarquia fraca; pouca diferença entre ação primária e secundária |
| Tema | Só segue o sistema operacional | Sem escolha; o escuro é uma inversão de cores, não um tema desenhado |
| Pré-análise | Tabelas e listas cruas | Difícil entender, de relance, o que será lido e o que ficará de fora (o objetivo do `03` §3) |
| Pergunta | Grade 50/50 fixa; evidências numa lista de 24rem; visualizador sem realce; resposta em texto corrido | Pouco espaço para o código; ler a resposta e conferir a fonte exige rolagem e cliques |
| Descoberta | Sem busca, sem atalhos, sem lista de conversas recentes | Tudo exige voltar para a lista e navegar de novo |

### O que já está bom e fica

Acessibilidade verificada pelo axe (WCAG 2.1 AA), link "Pular para o conteúdo", estados em texto além de cor, ação de erro escolhida pelo `code` (D-077), renderização sem HTML vindo do conteúdo (D-075), polling pelo TanStack Query, paginação na URL, um chunk por tela (D-085) e todos os textos em `messages.ts`.

## Objetivo e aceite

**Aceite:** *uma pessoa que nunca usou o produto vai do primeiro workspace à primeira resposta com a evidência aberta sem ler a documentação; em qualquer tela ela sabe onde está e qual é a próxima ação; a interface passa no axe (WCAG 2.2 AA) nos temas claro e escuro, em três larguras, sem relaxar a CSP e dentro do orçamento de desempenho da P13.*

Verificado por:
- o teste de aceite atual (`e2e/acceptance.spec.ts`) adaptado, mais os specs novos da P13;
- axe em todas as telas, nos dois temas;
- comparação visual por screenshots (P13);
- a passada manual por teclado e leitor de tela, com o roteiro deste plano (inclui a pendência da P10 da Etapa 7).

## Escopo

**Dentro:**
- identidade visual, sistema de design (tokens e componentes) e tema claro/escuro/sistema;
- casca da aplicação: barra lateral, barra superior com trilha de navegação, área do projeto com abas;
- paleta de comandos e atalhos de teclado;
- redesenho de todas as telas existentes;
- telas novas que só reorganizam o que a API já oferece: **Arquivos** (explorador do snapshot), **Configurações do projeto** (exclusões e limites fora do assistente) e **Sistema e privacidade** (provedores, destino dos dados, atalhos);
- formatação leve e segura das respostas (P7) e realce de sintaxe no visualizador (P8), revendo a D-075 e a D-086;
- responsividade, acessibilidade, regressão visual e orçamento de desempenho.

**Fora (e por quê):**

| Item | Motivo |
|---|---|
| Tailwind, MUI, Mantine ou outra biblioteca com estilo | A D-070 escolheu Radix sem estilo e CSS próprio; nada nesta etapa exige rever |
| CSS-in-JS em tempo de execução | Peso e conflito com a CSP (`style-src 'self'`) |
| Storybook | Dependência grande; um catálogo só de desenvolvimento cobre a necessidade (P2) |
| Configurar provedor ou chave pela UI | Fora do MVP (D-073); a UI continua só mostrando o que está no `.env` |
| Streaming da resposta | A D-075 continua valendo: marcadores não validados não aparecem |
| Tour guiado com sobreposições | Interrompe e envelhece mal; a orientação fica no próprio conteúdo (estados vazios e "Comece por aqui") |
| Fontes, imagens ou scripts de outra origem | CSP estrita (D-085); tudo é servido pelo próprio app |
| Mudança de contrato, salvo a P5.7 | A etapa é de frontend; a única exceção proposta é um endpoint pequeno para o explorador |
| Recursos pós-MVP (AST, comparação de snapshots, mapa arquitetural) | Etapa 10 (P0) |

## Pré-requisito externo

Nenhum. Tudo roda com os provedores `fake` e não depende de saldo (memória do projeto: rodadas reais só depois de todas as etapas).

## Propostas para aprovação

### P0 — Lugar no roteiro → F11

**Problema:** o roadmap chama de "Etapa 9" os recursos posteriores ao MVP, e cerca de 30 trechos da documentação citam esse número (AST, comparação de snapshots, provedor local).

**Opções:**
1. Esta etapa vira a **Etapa 9**, e os recursos pós-MVP passam a **Etapa 10**. Os documentos vivos (`09`, `01`, `RESUMO-EXECUTIVO`, `04`, `05`, `11`, `README`, e uma atualização datada nas decisões) passam a dizer "Etapa 10"; os planos já fechados (`etapa-3` a `etapa-8`) ficam como estão, por serem registro histórico.
2. Manter a numeração e chamar esta de "Etapa 8.5" ou "Etapa UI".

**Recomendação:** opção 1. A interface é pré-requisito natural para validar o produto com pessoas, e não depende do saldo que bloqueia a parte pós-MVP (D-086).

### P1 — Direção visual e identidade → F1

**Princípio:** *uma ferramenta de engenharia precisa e calma*. O código e as evidências são o conteúdo principal; a interface recua, usa cor com parcimônia e reserva destaque para estado e ação. Referências de padrões (não de marca): Linear, Vercel, GitHub, Raycast e Sourcegraph — barra lateral densa, tipografia nítida, superfícies neutras, um acento, atalhos por toda parte.

**Proposta:**
- **Cores:** neutros levemente frios (12 passos), **um acento índigo** para ação e seleção, semânticas para sucesso, atenção, perigo e informação, e uma cor própria para **evidência citada** (âmbar suave), que não se confunde com "atenção". Valores em OKLCH, com contraste AA conferido nos dois temas. O tema escuro é desenhado à parte (superfícies que clareiam com a elevação), não uma inversão.
- **Tipografia:** **Inter** (variável) para a interface e **JetBrains Mono** (variável) para código, caminhos e números de linha, ambas OFL, auto-hospedadas (`@fontsource-variable/*`), só o subconjunto latino. Escala de 12, 13, 14, 16, 20, 24 e 30 px; corpo em 14 px nas telas densas (pergunta, arquivos) e 16 px nas de leitura (início, assistente); `font-variant-numeric: tabular-nums` em contagens, tempos e custos.
- **Espaço e forma:** grade de 4 px; raios de 6, 8 e 12 px; três níveis de elevação (sombras suaves no claro, bordas e superfícies no escuro); ícones de 16 e 20 px, traço de 1,5 px.
- **Marca:** um símbolo SVG simples (colchetes com um ponto de "evidência"), usado na barra lateral, no favicon e nos estados vazios; o nome continua "Codebase Copilot" (D-001).
- **Ilustrações:** só nos estados vazios e no primeiro uso, em SVG inline com as cores dos tokens (respeitam o tema).

**Alternativas:** Geist + Geist Mono (OFL, mais "Vercel"), ou IBM Plex Sans + Plex Mono (mais institucional). A F1 mostra as três lado a lado no catálogo antes de fixar; a recomendação é Inter + JetBrains Mono, pela legibilidade em tamanhos pequenos e pela distinção clara entre `0/O` e `1/l/I` no código.

### P2 — Sistema de design → F1

**Proposta:**
- **Tokens em três camadas:** primitivos (`--gray-1`…`--gray-12`, `--indigo-*`), semânticos (`--color-bg`, `--color-surface-raised`, `--color-text-muted`, `--color-accent`, `--color-evidence`…) e de componente só onde precisar. As telas usam apenas os semânticos; trocar o tema troca só essa camada.
- **CSS:** `@layer reset, tokens, base, components, screens, utilities`, com um arquivo de CSS ao lado de cada componente (importado por ele, vai no chunk da tela). Os nomes continuam em BEM e globais: os testes e o E2E usam `.code-view`, `.code-view__line--cited` e `.viewer__path`, e CSS Modules mudariam isso sem ganho real num app deste tamanho. Container queries nos cartões e painéis.
- **Componentes** em `src/components/ui/`, sobre o Radix quando ele tiver o primitivo (diálogo, menu, dica, cartão flutuante, toast, abas, acordeão, área de rolagem, grupo de alternância, progresso):
  `Button` (primária, secundária, discreta, perigo; tamanhos; ícone; estado "enviando"), `IconButton`, `StatusBadge` (ícone + texto, nunca só cor), `Card`, `Callout` (substitui `.notice` e `.problem-alert`), `PageHeader`, `Breadcrumbs`, `Tabs`, `Dialog`/`ConfirmDialog`, `DropdownMenu`, `Tooltip`, `HoverCard`, `Toast`, `Skeleton`, `EmptyState`, `Stat`, `Progress`/`StageTimeline`, `Kbd`, `CopyButton`, `RelativeTime`, `SegmentedControl`, `TextField`/`Select`/`Textarea`, `Dropzone`, `Splitter`.
- **Ícones:** `lucide-react` (componentes SVG importados um a um, sem fonte de ícones, compatível com a CSP).
- **Catálogo:** rota `/_ui` só no `npm run dev` (`import.meta.env.DEV`, fora do build de produção), com cada componente em todos os estados e nos dois temas. Serve à revisão visual e aos screenshots da P13.

### P3 — Casca da aplicação e navegação → F2

**Proposta:**

```text
┌───────────────┬──────────────────────────────────────────────────────────┐
│ ◇ Codebase    │ Workspaces › Loja › sample-shop › Perguntar    ⌘K  ◐  ● Local │
│   Copilot     ├──────────────────────────────────────────────────────────┤
│               │ sample-shop   [● Pronto] snapshot de 17/10 14:02          │
│ Workspace ▾   │ Visão geral · Perguntar · Arquivos · Histórico · Config.  │
│  Loja         ├──────────────────────────────────────────────────────────┤
│               │                                                          │
│ PROJETOS      │                    conteúdo da tela                      │
│ ● sample-shop │                                                          │
│ ◌ api-legado  │                                                          │
│ + Novo projeto│                                                          │
│               │                                                          │
│ CONVERSAS     │                                                          │
│  Como a aut…  │                                                          │
│  Onde é cal…  │                                                          │
│               │                                                          │
│ ⚙ Sistema     │                                                          │
└───────────────┴──────────────────────────────────────────────────────────┘
```

- **Barra lateral** (16rem, recolhível para 3,5rem só com ícones; gaveta abaixo de 48rem): seletor de workspace, projetos do workspace com o estado (ícone + rótulo; progresso quando indexando), "Novo projeto", as cinco conversas mais recentes do projeto aberto e, no rodapé, "Sistema e privacidade". O estado recolhido fica no `localStorage` (preferência de quem usa, com `try/catch`).
- **Barra superior:** trilha (Workspaces › workspace › projeto › seção), botão da paleta de comandos (P4), alternância de tema (P9) e um indicador **Local / Remoto** a partir de `GET /system/providers`, que abre o detalhe de para onde vão os trechos (`03` §7).
- **Área do projeto:** rota aninhada com cabeçalho comum (nome, origem, estado, snapshot ativo, ação principal) e abas como navegação (`<nav>` com links e `aria-current`, não `role="tablist"`, porque cada aba é uma URL).
- **Rotas:** todas as atuais continuam (links salvos e testes). Novas: `/projects/:id/files` (P5.7), `/projects/:id/settings` (exclusões e limites, com os componentes da pré-análise; o assistente em `/workspaces/:id/new?project=` continua para o primeiro uso) e `/system`.
- **Títulos e foco:** cada rota define o `<title>` ("Perguntar · sample-shop · Codebase Copilot"), uma região `aria-live` anuncia a tela nova e o foco vai para o `<h1>`; o "Pular para o conteúdo" continua.
- **Continuar de onde parou:** o início mostra os últimos projetos abertos neste navegador (`localStorage`, conferidos contra a API; um projeto removido some da lista sem erro).
- **Transições:** as do React Router com a View Transitions API, curtas e desligadas com `prefers-reduced-motion` (a F0 confirma o suporte na versão 8.4).

### P4 — Paleta de comandos e atalhos → F3

**Proposta:**
- **⌘K / Ctrl+K** abre a paleta: ir para workspace, projeto ou conversa (entre os já carregados e uma busca pela API), ações do contexto (novo projeto, perguntar neste projeto, indexar de novo — com confirmação —, alternar tema) e **abrir arquivo** do snapshot ativo (pelo endpoint da P5.7).
- **Atalhos** (lista em `/system` e no `?`): `/` foca o campo da pergunta; ⌘Enter envia; `[` e `]` evidência anterior e próxima; `Esc` fecha painéis e diálogos; `g` seguido de `o`, `p`, `a` ou `h` vai para Visão geral, Perguntar, Arquivos ou Histórico. Nenhum atalho de letra única dispara dentro de campos de texto, e todos podem ser desligados em `/system` (WCAG 2.1.4).
- **Biblioteca:** `cmdk` (sobre o diálogo do Radix, com ARIA de combobox), se a F0 confirmar compatibilidade com React 19 e versão estável; senão, um componente próprio com o mesmo padrão ARIA.

### P5 — Telas → F4 a F8

**P5.1 — Início (`/`).**
- Primeiro uso: apresentação em três passos ilustrados (criar workspace → importar projeto → perguntar), com a frase de privacidade em linguagem simples ("o código fica na sua máquina; com um provedor remoto, só os trechos escolhidos para cada pergunta saem dela") e o campo de nome com um padrão sugerido.
- Com dados: "Continuar" (P3) e a grade de workspaces (nome, quantidade de projetos, criação), com menu "⋯" para excluir.

**P5.2 — Workspace.**
- Cartões de projeto: nome, ícone da origem (pasta ou ZIP), `StatusBadge` (inclui "falhou", pelo `lastJob`, D-084), progresso quando indexando, data do snapshot ativo e menu "⋯" (abrir, perguntar, indexar de novo, excluir).
- Estado vazio com ilustração e a ação "Importar o primeiro projeto".

**P5.3 — Assistente de novo projeto.**
- Indicador de passos horizontal: Origem · Pasta ou ZIP · Pré-análise · Indexar, com o passo atual anunciado.
- Origem como dois cartões grandes de escolha (pasta local recomendada; ZIP como alternativa), cada um com o que acontece em seguida.
- Navegador de pastas com trilha, ícones, navegação por setas e a pasta escolhida destacada.
- ZIP com área de arrastar e soltar **e** botão (alternativa ao arraste, WCAG 2.5.7), com nome, tamanho e limite. Progresso do envio só se a F0 mostrar que dá para medir sem trocar o cliente HTTP; senão, estado indeterminado com o tamanho do arquivo.
- Pré-análise em duas colunas: à esquerda, totais em `Stat` (incluídos, excluídos, não suportados, ilegíveis, tamanho), uma barra empilhada de linguagens com a tabela equivalente, diretórios com barras proporcionais e as exclusões em acordeões com motivo e exemplos; à direita, fixo, "O que acontece ao indexar" (provedor, o que sai da máquina, custo quando calculável) e o botão principal. Avisos como `Callout`.

**P5.4 — Visão geral do projeto.**
- No topo, **um só cartão de estado**, o mais importante entre: indexando (linha do tempo das etapas com progresso e "Cancelar"), última indexação falhou (motivo e ação), desatualizado (motivos e "Indexar de novo"), aguardando ZIP ou nunca indexado.
- "Comece por aqui": as perguntas sugeridas (D-077) como cartões; escolher uma abre a tela de pergunta com o campo preenchido, sem enviar.
- Coluna lateral: snapshot ativo (contagens), uso em `Stat` (perguntas, latência p50/p95, custo com "estimado" ou "indisponível", nunca inventado, `01` F-09) e os snapshots recentes em linha do tempo.
- "Arquivos não indexados" passa para a aba Arquivos, com o filtro de estado; a visão geral só mostra a contagem com o link.

**P5.5 — Perguntar.**

```text
┌──────────────────────────────┬─┬─────────────────────────────────────┐
│ Como a autenticação funciona?│ │ Evidências (6)   citadas ▾          │
│                              │ │ ▸ AuthController.java  42–58  [1]   │
│ ┌ Respondida ──────────────┐ │ │ ▸ TokenService.java    20–47  [2]   │
│ │ O login passa pelo       │ │║├─────────────────────────────────────┤
│ │ controller [1], que      │ │║│ src/…/TokenService.java · java      │
│ │ delega a geração do      │ │║│ Da resposta │ Atual     ⧉ caminho  │
│ │ token ao serviço [2].    │ │ │  20 ▌ public String issue(User u) { │
│ │ 1,2 s · 6 evidências ⧉   │ │ │  21 ▌   var claims = …              │
│ └──────────────────────────┘ │ │  22     …                           │
│ ┌──────────────────────────┐ │ │                                     │
│ │ Pergunte sobre o código… │ │ │                                     │
│ │ Híbrida ▾          ⌘↵    │ │ │                                     │
│ └──────────────────────────┘ │ │                                     │
└──────────────────────────────┴─┴─────────────────────────────────────┘
```

- A partir de 64rem: conversa à esquerda e, à direita, evidências em cima e o visualizador embaixo, com um **divisor redimensionável** (teclado: setas; `role="separator"` com valor) e a largura lembrada. Abaixo de 64rem, as abas atuais continuam (D-075, `03` §4).
- **Campo de pergunta** fixo embaixo, que cresce com o texto; modo de busca num controle compacto (os modos indisponíveis continuam desabilitados com o motivo, D-075); o aviso de provedor remoto continua com a confirmação na primeira pergunta.
- **Resposta** como cartão: `StatusBadge` (respondida, parcial, abstenção, falhou), texto com a formatação da P7, marcadores `[n]` como botões que mostram, ao passar o mouse ou focar, um `HoverCard` com caminho, linhas e as primeiras linhas do trecho; rodapé com métricas compactas ("1,2 s · 6 evidências · US$ 0,0004 estimado", detalhe num popover) e copiar a resposta.
- **Espera:** esqueleto do cartão com "Buscando evidências e gerando a resposta · 12 s de até 60 s". Nada de etapas simuladas: o progresso é concreto ou não aparece (`03` §1).
- **Evidências** agrupadas por arquivo, com ícone da linguagem, linhas, estado (citada, não citada, intervalo inválido) e seleção pelo teclado.
- **Visualizador:** cabeçalho com o caminho em trilha, linguagem e ações (copiar caminho, copiar trecho, "abrir em Arquivos"); "Da resposta / Atual" como controle segmentado quando `activeVersion` é `CHANGED`; linhas citadas com fundo, marcador na margem e texto para leitor de tela (como hoje); botão "ir para o trecho"; realce da P8.
- **Conversa vazia:** a pergunta sugerida em cartões, como na visão geral.

**P5.6 — Histórico.** Conversas agrupadas por período (hoje, ontem, últimos 7 dias, mais antigas), com título, quantidade de perguntas, tempo relativo (data exata na dica) e menu para excluir. As cinco mais recentes também aparecem na barra lateral (P3).

**P5.7 — Arquivos (nova).** Explorador do snapshot ativo: árvore de diretórios à esquerda, o visualizador da P5.5 à direita, filtro por estado (indexados, não suportados, com falha, com o motivo) e "Perguntar sobre este arquivo" (abre a pergunta com o caminho no texto).
- **Problema:** `GET /snapshots/{id}/files` filtra por prefixo, mas devolve todos os descendentes, paginados de 100 em 100; montar a árvore de um projeto com 10 mil arquivos exigiria 100 chamadas.
- **Opções:** (a) só frontend: carregar a lista inteira até um teto (por exemplo, 5 mil arquivos) e, acima dele, cair numa busca por prefixo; (b) um endpoint novo, `GET /snapshots/{id}/tree?path=`, com os filhos imediatos de um diretório (nome, tipo, contagens por estado), em `indexing`, com SQL explícito e teste de integração.
- **Recomendação:** (b). É pequeno, segue as regras do projeto (SQL explícito, Testcontainers, contrato versionado) e é a única forma de o explorador funcionar igual em qualquer tamanho. É a **única mudança de backend** da etapa, numa fatia própria, e pode ser cortada junto com a tela se você preferir manter a etapa só no frontend.

**P5.8 — Configurações do projeto (nova rota).** Exclusões e limites com os componentes da pré-análise, uma barra fixa de "Salvar alterações" quando há mudança e o aviso de que o snapshot ficará desatualizado (`SETTINGS_CHANGED`) até indexar de novo.

**P5.9 — Sistema e privacidade (nova).** Provedores configurados (local ou remoto, modelo, dimensões), o que sai da máquina e quando, onde ficam os dados (texto do `11` §2), atalhos de teclado e a preferência de tema.

**P5.10 — Erros e página não encontrada.** `RouteError` e 404 com ilustração, a mensagem atual e as ações; o `ProblemAlert` vira `Callout` de perigo, com o `requestId` copiável.

### P6 — Estados, retorno e microinterações → F1, F4 a F8

- **Esqueletos** no formato de cada tela no lugar de "Carregando…"; o texto continua para leitores de tela (`aria-busy` e rótulo oculto), e os testes que procuram "Carregando…" seguem válidos.
- **Toasts** (Radix, `aria-live="polite"`) só para confirmações: projeto criado, conversa excluída, configurações salvas, **indexação concluída ou falhou** (detectada na transição do polling da barra lateral, mesmo em outra tela). Erros que pedem ação continuam no lugar onde aconteceram.
- **Título da aba** com o progresso durante a indexação ("(45%) sample-shop · Codebase Copilot").
- **Tempo relativo** ("há 2 h") com a data exata na dica, por `Intl.RelativeTimeFormat` em pt-BR.
- **Movimento:** 120 a 200 ms, `ease-out`, só em opacidade e posição; tudo desligado com `prefers-reduced-motion`.
- **Estados de interação** consistentes em todo controle: hover, pressionado, foco visível (anel de 2 px no acento, com contraste 3:1), desabilitado com o motivo quando houver.
- **Exclusões irreversíveis** (workspace, projeto) continuam com diálogo; o botão de perigo fica à direita e o foco inicial vai para "Cancelar".

### P7 — Resposta com formatação leve e segura (revê parte da D-075) → F7

**Problema:** respostas longas em texto corrido são difíceis de ler, e o modelo usa naturalmente listas e `código`. A D-075 proibiu markdown para fechar a porta a HTML e links injetados.

**Proposta:** um formatador próprio, pequeno e testado, que transforma o texto em elementos React (nunca em HTML), reconhecendo só: parágrafos, listas (`-`, `*`, `1.`), `código inline`, blocos entre crases triplas (com botão de copiar e o realce da P8) e **negrito**. Fica de fora tudo o que abre superfície: links, imagens, HTML, tabelas e títulos (viram parágrafo). Os marcadores `[En]` continuam virando botões só para evidências da própria resposta. A regra de lint contra `dangerouslySetInnerHTML` continua.

**Alternativa:** manter o texto puro. **Recomendação:** a proposta, com testes de conteúdo hostil (HTML, `javascript:`, markdown de link e imagem, marcadores falsos) somados ao `e2e/hostile-content.spec.ts`.

### P8 — Realce de sintaxe no visualizador (revê a D-075 e a D-086) → F9

**Contexto:** a D-086 adiou o realce porque ninguém tinha pedido, e listou "pedidos de uso (realce)" como motivo de revisão. Este pedido de uma interface premium é esse motivo.

**Opções:**
1. Sem realce (como hoje).
2. Shiki: melhor qualidade, mas gera HTML (exigiria injetar marcação) e usa WebAssembly (exigiria `wasm-unsafe-eval` na CSP). Descartada.
3. Prism ou highlight.js: a API pública devolve HTML; usar os tokens internos é frágil.
4. **Lezer** (os parsers do CodeMirror): `@lezer/highlight` entrega os intervalos de cada token por callback, sem HTML; parsers em ES modules, sem `eval`.

**Proposta:** opção 4, num **Web Worker** (permitido pela CSP atual, que cai em `script-src 'self'`), carregado sob demanda por linguagem (Java, TypeScript/JavaScript, Python, Go, Rust, SQL, JSON, YAML, XML/HTML, CSS, Markdown). O worker devolve, por linha, intervalos e classes; a lista virtualizada renderiza `<span>`s com texto. Limites: arquivos acima de 300 KB ou que passem de 1,5 s no parser ficam em texto puro, com um aviso discreto; linguagem não reconhecida também. Cores do realce como tokens, com contraste AA nos dois temas. O destaque das linhas citadas continua por cima.

**Por que é seguro:** o parser só lê texto e devolve números; nada do arquivo vira marcação. **Corte possível:** a fatia é a última antes do fechamento e pode ficar de fora sem afetar o resto.

### P9 — Tema claro, escuro e do sistema → F1

- Escolha em três opções (Sistema, Claro, Escuro) na barra superior e em `/system`, salva no `localStorage`; `data-theme` no `<html>` e `color-scheme` coerente.
- **Sem piscar no carregamento, sem script inline:** um arquivo `public/theme-init.js`, síncrono no `<head>`, aplica o tema antes da pintura. É `script-src 'self'`, então a CSP não muda (nem a do `csp.ts`, nem a do `SecurityHeadersFilter`).
- `prefers-contrast: more` reforça bordas e texto secundário; `forced-colors: active` usa as cores do sistema nos estados e no foco.

### P10 — Responsividade → F2, F10

- Pontos de quebra em 40, 64 e 80rem; container queries nos componentes.
- Abaixo de 48rem: barra lateral em gaveta, abas do projeto roláveis, campo de pergunta fixo com a área segura do aparelho, alvos de toque de pelo menos 24 px (WCAG 2.5.8).
- Larguras testadas: 390, 1024 e 1440 px.

### P11 — Acessibilidade → todas as fatias, F10

- Meta passa de WCAG 2.1 AA para **2.2 AA**: foco nunca encoberto pela barra fixa (2.4.11), tamanho de alvo (2.5.8), alternativa ao arraste no divisor e na área de ZIP (2.5.7).
- axe nos testes de componente e no E2E, **nos dois temas**.
- Progresso anunciado por marcos (início, troca de etapa, fim), não a cada porcentagem.
- Estados sempre com ícone e texto; nenhum significado só na cor.
- Roteiro da passada manual no fim deste plano.

### P12 — Dependências novas → F0

Versões exatas conferidas na F0 em fonte oficial (nenhuma de memória, como na D-014 e na D-070), com licença, tamanho, compatibilidade com React 19 e ausência de scripts de instalação (`ignore-scripts=true` continua):

| Pacote | Para quê | Condição |
|---|---|---|
| `lucide-react` | ícones | — |
| `@fontsource-variable/inter`, `@fontsource-variable/jetbrains-mono` | fontes auto-hospedadas | ou as alternativas da P1 |
| `cmdk` | paleta de comandos | se a F0 confirmar React 19; senão, componente próprio |
| `@lezer/highlight` e os `@lezer/<linguagem>` | realce (P8) | se a P8 for aprovada |

O divisor redimensionável é feito à mão (um componente, com teclado e ARIA), para não somar uma dependência por um controle só.

### P13 — Qualidade, testes e orçamento → todas as fatias, F10

- **Testes de componente** para cada primitivo novo (Vitest, Testing Library, axe), com os estados do catálogo. Os testes atuais continuam passando; quando a estrutura muda, o teste é atualizado na mesma fatia, preferindo papéis e rótulos a classes.
- **E2E:** o aceite adaptado à navegação nova, mais `navigation.spec.ts` (barra lateral, trilha, paleta, atalhos, recarregar em cada rota), `theme.spec.ts` (troca, persistência, sem piscar) e, com a P5.7, `files.spec.ts`. Também no `npm run e2e:jar`.
- **Regressão visual:** `toHaveScreenshot` do Playwright em cerca de dez telas × dois temas × duas larguras, com datas e identificadores mascarados. As imagens de referência são versionadas e geradas no macOS com o Chromium do Playwright (a renderização de fontes muda entre sistemas); roda no `npm run e2e`, fora do `npm run check`.
- **Orçamento de desempenho**, medido na F0 (linha de base) e na F10:
  - JavaScript da casca (antes da primeira tela) até 175 kB com gzip (era 150 kB; revisto na F1, D-087); nenhum chunk acima de 500 kB (D-085);
  - fontes: dois arquivos `woff2` variáveis, só latino, com `preload` da fonte da interface e `size-adjust` no fallback para não deslocar o layout;
  - o arquivo sintético de 1 MB abre no visualizador sem travar, com e sem realce;
  - sem mudança na CSP.
- `npm run check` e `./mvnw verify` passando ao fim de cada fatia.

## Princípios de execução

- Uma fatia por vez, com aprovação sua antes de começar a implementação e antes de cada commit (memória do projeto).
- Antes e depois em screenshots a cada fatia de tela, para revisão.
- Nenhuma mudança de backend além da P5.7; qualquer outra necessidade vira desvio registrado.
- Textos novos em `messages.ts`, em pt-BR; código, comentários e nomes de teste em inglês; comentários só onde o porquê não é óbvio.
- Nada de conteúdo de código, pergunta ou resposta em `console` ou em logs.
- Nenhuma regressão de acessibilidade ou de segurança é aceita para ganhar visual.

## Fatias, na ordem

### F0 — Verificações (sem código de produção)
- Screenshots e medidas da interface atual (tamanho dos chunks, tempo para abrir o arquivo de 1 MB) como linha de base.
- Versões, licenças e compatibilidade das dependências da P12.
- Suporte a View Transitions no React Router 8.4 (P3).
- Protótipo descartável do Lezer num worker, no `vite preview`, com a CSP do build e o arquivo de 1 MB (P8).
- Se o envio do ZIP pode mostrar progresso com o cliente atual (P5.3).
- Resultado: ajustes nas propostas, registrados aqui antes da F1.

**Resultado (17/10/2026)**

*Linha de base (P13).* Medida com uma configuração descartável do Playwright igual à do E2E (backend real na 8089 com os provedores `fake`, `vite preview` na 4173), com o `sample-shop` e um projeto sintético com um único `Big.java` de 1.000.095 bytes e 15.848 linhas (abaixo do `max-file-bytes` de 1 MB):

| Medida | Valor |
|---|---|
| Chunk da casca (`index`, com React, Router, Query e Radix) | 363,45 kB (115,65 kB com gzip) |
| Maior chunk de tela (`ChatPage`) | 69,04 kB (21,13 kB com gzip) |
| CSS | 12,58 kB (2,86 kB com gzip) |
| Visualizador, arquivo de 1 MB: do clique na citação até a linha citada visível | 110, 108 e 110 ms (três execuções) |

- **Screenshots "antes":** 37 imagens — nove telas (início, workspace vazio e com projeto, novo projeto, pré-análise do `sample-shop` e do arquivo grande, resumo do projeto, conversa com o visualizador aberto, histórico) × claro e escuro × 1440 e 390 px, mais o primeiro uso. Serviram só à revisão da F0 e não foram guardadas (decisão de 17/10/2026); a comparação visual da F10 usa as imagens de referência da P13, geradas sobre o layout novo.
- O que elas mostram confirma o diagnóstico: no resumo do projeto, o estado e a ação ficam no meio de seis painéis do mesmo peso; na conversa, o visualizador fica abaixo da dobra a 1440 px e a lista de evidências corta o terceiro item; em 390 px, as telas são uma coluna longa sem navegação além do "voltar".

*Dependências (P12).* Versões consultadas no registro npm em 17/10/2026. **Critério adotado:** a versão mais recente com pelo menos 14 dias de publicação, por cautela com a cadeia de suprimentos (o `lucide-react` 1.59.0, por exemplo, saiu no próprio dia). Nenhum pacote tem script de instalação (os `prepare` só rodam em instalação a partir do Git, e o `ignore-scripts=true` continua):

| Pacote | Versão (publicação) | Licença | Observação |
|---|---|---|---|
| `lucide-react` | 1.51.0 (03/10/2026) | ISC | par `react ^19`; 2.130 ícones, importados um a um |
| `@fontsource-variable/inter` | 5.3.0 (19/07/2026) | OFL-1.1 | `woff2` latino, normal: 48.256 bytes |
| `@fontsource-variable/jetbrains-mono` | 5.3.0 (19/07/2026) | OFL-1.1 | `woff2` latino, normal: 40.404 bytes |
| `cmdk` | 1.1.1 (14/03/2025) | MIT | par `react ^19`; depende do `@radix-ui/react-dialog ^1.1.6`, que o `npm` reaproveita do `radix-ui` 1.8.1 (1.3.1), sem duplicar; ~11 kB sem minificar |
| `@lezer/highlight`, `@lezer/common`, `@lezer/lr` | 1.2.5, 1.5.3, 1.4.10 | MIT | núcleo do realce |
| `@lezer/java`, `javascript`, `python`, `go`, `rust`, `json`, `yaml`, `xml`, `html`, `css`, `markdown` | 1.1.4, 1.5.6, 1.1.19, 1.0.1, 1.0.3, 1.0.3, 1.0.4, 1.0.6, 1.3.13, 1.3.8, 1.7.2 | MIT | um chunk por linguagem, carregado sob demanda |

- As alternativas de fonte da P1 também estão no registro (Geist e Geist Mono 5.3.0, OFL, 29.400 e 23.128 bytes; IBM Plex 5.3.0, OFL) e entram só no catálogo da F1, para a comparação lado a lado.
- **`cmdk` sem release há 19 meses.** Funciona com React 19 pelo par declarado, mas é um risco de manutenção. Fica a escolha da P4 (usar), com duas proteções: a paleta é carregada só no primeiro ⌘K (chunk próprio, fora da casca), e um teste de componente cobre o comportamento que usamos; se quebrar numa atualização do React, o componente próprio com o mesmo padrão ARIA é a saída já prevista.
- **SQL fica sem realce:** não existe `@lezer/sql`; o parser de SQL vem no `@codemirror/lang-sql`, que puxa o `@codemirror/state`, o `language` e o `autocomplete`. Arquivos `.sql` ficam em texto puro, como as outras linguagens sem parser.

*View Transitions (P3).* O React Router 8.4 aceita `viewTransition` em `<Link>`, `<NavLink>` e `navigate()`, usa `document.startViewTransition` quando existe e ignora quando não existe (`lib/components.js`). Confirmado; não precisa de código próprio.

*Realce num worker sob a CSP (P8).* Protótipo descartável (Vite 8.3.4, o `csp.ts` de `apps/web` na meta tag, `vite preview`, Chromium do Playwright): o worker carrega o parser da linguagem por `import()`, faz `parser.parse` e `highlightTree` com o `classHighlighter` (classes `tok-*`, estilizadas por CSS) e devolve os intervalos num `Int32Array` transferido.

| Arquivo | Intervalos | Carregar o parser | Parse | Realce | Total (ida e volta) |
|---|---|---|---|---|---|
| `Big.java`, 1 MB | 121.471 | 5–16 ms | 137–156 ms | 19 ms | 174–213 ms |
| `Big.java`, primeiros 300 kB | 36.716 | 5–6 ms | 47–49 ms | 8 ms | 66–69 ms |
| `Big.java`, primeiros 100 kB | 12.336 | 6 ms | 20 ms | 5 ms | 35–36 ms |
| `ChatPage.tsx` (12 kB, dialeto `ts jsx`) | 2.382 | 6–9 ms | 10–12 ms | 3 ms | 24–28 ms |

- **Nenhuma violação de CSP e nenhum erro de console**: o worker vem da própria origem (`script-src 'self'`), sem `eval` e sem WebAssembly.
- Tamanho: o worker com o núcleo tem 27 kB (9,4 kB com gzip); cada linguagem, de 27 a 78 kB (8,8 a 29,8 kB com gzip).

*Progresso do envio do ZIP (P5.3).* O upload usa `fetch` pelo `openapi-fetch`, e o `fetch` não informa o progresso de envio (o corpo em stream com `duplex: 'half'` exige HTTP/2, e o backend local é HTTP/1.1). Mostrar bytes exigiria um `XMLHttpRequest` só para essa chamada, fora do cliente tipado e do tratamento de erros comum. Em loopback, 100 MB sobem em cerca de um segundo; o que demora é a extração, que já aparece pelo estado do import.

**Ajustes nas propostas:**
- **P5.3:** sem barra de bytes. O envio mostra "Enviando…" com o nome e o tamanho do arquivo, e depois o estado do import, como hoje.
- **P8:** o limite de 300 KB sai; o realce vale para todo arquivo aceito pela indexação (até o `max-file-bytes`, 1 MB), porque 1 MB leva cerca de 0,2 s fora da thread principal. Ficam o limite de tempo de 1,5 s (para arquivos patológicos) e a regra de **mostrar o texto puro na hora e aplicar o realce quando o worker responder**, para a abertura não ficar mais lenta que a linha de base. Linguagens: Java, TypeScript/JavaScript (com JSX), Python, Go, Rust, JSON, YAML, XML, HTML, CSS e Markdown; SQL e as demais em texto puro.
- **P12:** as versões da tabela acima, com o critério dos 14 dias registrado na D-087.
- **P13:** o orçamento da casca (150 kB com gzip) parte de 115,65 kB; sobram cerca de 34 kB para a barra lateral, a barra superior e os ícones da casca. A paleta de comandos e o realce ficam em chunks próprios, fora dela. As fontes somam 88.660 bytes (Inter e JetBrains Mono, latino, normal; o subconjunto latino cobre os acentos do pt-BR), fora do JavaScript. O visualizador continua abrindo o arquivo de 1 MB em até ~110 ms, sem realce, e o realce chega depois.

### F1 — Fundação visual
Tokens, fontes, camadas de CSS, tema com `theme-init.js` (P9), primitivos da P2 com testes, catálogo `/_ui`. As telas atuais passam a usar botões, `Callout`, `StatusBadge` e esqueletos onde a troca é direta. Registro: D-087 (direção visual e sistema de design) e D-088 (tema).

**Como foi entregue (17/10/2026)**
- **Fundação:** tokens em OKLCH com `light-dark()`, camadas de CSS, fontes auto-hospedadas com preload, `public/theme-init.js`, favicon em SVG e o menu de tema no cabeçalho atual. O `global.css` saiu: a base estiliza os controles nativos, e as classes das telas antigas foram para a camada `screens`, já sobre os tokens.
- **Componentes** em `src/components/ui/` (lista na D-087), com testes de componente e axe. Catálogo em `/_ui`, só no `npm run dev`; o build confere que ele não entra (nenhum chunk nem texto dele no `dist`).
- **Trocas diretas nas telas:** todos os botões viraram `Button`/`ButtonLink` com ícone; avisos e alertas, `Callout`; estados, `StatusBadge`; painéis, `Card`; totais, `Stat`; estados vazios, `EmptyState`; "Carregando…", esqueletos com o texto para leitores de tela; cabeçalhos das telas de workspace, projeto, histórico e pergunta, `PageHeader`; o `ProblemAlert` ganhou o botão de copiar o identificador do pedido. A página não encontrada e a de erro usam o `EmptyState`.
- **Verificação:** `npm run check` com 143 testes (eram 116); `npm run e2e` passa. Uma passada descartável com axe (WCAG 2.2 AA, com contraste) nas nove telas, nos dois temas, em 1440 e 390 px: **nenhuma violação**. O arquivo de 1 MB continua abrindo em 99 a 118 ms.

**Desvios:**
- **Componentes adiados para a fatia do primeiro uso:** `HoverCard` e `Splitter` (F7), `Toast` e `StageTimeline` (F6), `Dropzone` (F5) e `SegmentedControl` (F7). Sem uma tela que os use, a API seria um palpite.
- **Comparação de fontes no catálogo:** não feita. Com as recomendações aprovadas, Inter e JetBrains Mono já estavam escolhidas, e instalar as alternativas só para a comparação somaria dependências sem uma decisão a tomar.
- **Testes ajustados:** quatro testes conferiam "nenhum botão no alerta" para dizer "sem tentar de novo"; com o botão de copiar o identificador, passaram a procurar o botão de tentar de novo pelo nome.
- **Acessibilidade além do previsto:** a passada em 390 px achou um problema que já existia, mas o E2E não pegava, porque roda só no desktop: as tabelas com rolagem horizontal não recebiam foco pelo teclado. Corrigido com o `TableScroll` (focável, `role="group"`, nomeado pelo título da seção).
- **Orçamento da casca: estourado por pouco.** O chunk principal foi de 115,65 kB para **150,27 kB com gzip** (teto da P13: 150 kB). Quase tudo vem do menu e do tooltip do Radix, que o botão de tema trouxe para a casca: posicionamento (floating-ui), foco, camadas e rolagem somam cerca de 75 kB sem minificar. É um custo fixo: a barra lateral e a barra superior da F2 precisam dos mesmos menus e dicas, e as telas que os usarem não pagam de novo (o `ChatPage` caiu de 21,13 para 18,08 kB). O JavaScript total foi de 175,0 para 199,4 kB com gzip. **Decisão (17/10/2026):** o teto da casca sobe para **175 kB com gzip**, medido de novo ao fim da F2; a paleta e o realce continuam fora dela.

### F2 — Casca e navegação
Barra lateral, barra superior, trilha, área do projeto com abas, rotas `/projects/:id/settings` e `/system`, títulos, anúncio de rota, "Continuar de onde parou", gaveta no celular. Registro: D-089 (navegação; revê a D-077).

**Como foi entregue (17/10/2026)**
- **Casca** (`components/shell/`): barra lateral com seletor de workspace, projetos com estado e progresso, "Novo projeto", conversas recentes e "Sistema e privacidade"; recolhível (lembrada no navegador) e em gaveta abaixo de 48rem. Barra superior com trilha, indicador Local/Remoto e tema. Os links "← voltar" saíram das telas.
- **Área do projeto:** `ProjectLayout` com cabeçalho comum, abas como links e "Nova pergunta"; as telas de visão geral, pergunta e histórico recebem o projeto dela. Na visão geral, o `<h1>` "Visão geral" fica oculto visualmente, porque a aba já o mostra.
- **Telas novas:** `/projects/:id/settings` (o formulário de exclusões e limites da pré-análise, com o aviso de que o snapshot fica desatualizado) e `/system` (provedores, o que sai da máquina, onde ficam os dados e o tema).
- **Títulos, foco e anúncio** por rota (`RouteAnnouncer`, `usePageTitle`), e "Continuar de onde parou" no início.
- **Verificação:** `npm run check` com 163 testes (eram 143); `npm run e2e` e `npm run e2e:jar` passam, com o aceite adaptado às abas. Passada descartável com axe (WCAG 2.2 AA, com contraste) nas dez telas, com a gaveta aberta, nos dois temas, em 1440 e 390 px: **nenhuma violação**. Casca: 163,6 kB com gzip (teto de 175 kB), em dois arquivos abaixo de 500 kB.

**Desvios:**
- **Ordem das camadas de CSS.** Ao tirar a área do projeto e a gaveta da casca, o bundler criou um chunk comum (Radix e componentes) cujo CSS carrega antes do `index.css`; a primeira `@layer` passou a ser `components`, que ficou abaixo da `base` (o botão primário perdeu a cor do texto, pego pelo axe no E2E). Corrigido com `public/layers.css`, carregado primeiro no `index.html` e conferido por teste; a CSP não muda.
- **Sem botão da paleta na barra superior:** entra com a paleta, na F3.
- **Indicador Local/Remoto** leva à página `/system` em vez de abrir um painel flutuante: o detalhe já está lá, e um popover somaria um primitivo à casca.
- **Testes ajustados:** os que procuravam os links "← voltar" ou "Histórico de conversas" passaram a usar a trilha e as abas; os que contavam chamadas à lista de conversas aceitam a da barra lateral, que usa a mesma chave de cache. O setup dos testes ganhou respostas padrão para as consultas da casca e limpa o `localStorage` entre testes.
- **Achado anterior à F2, não corrigido:** o Radix (via `react-remove-scroll`) injeta um `<style>` para travar a rolagem quando abre um menu ou diálogo, e a CSP (`style-src 'self'`) o bloqueia, com um erro no console. Acontece desde a Etapa 7 (diálogo de confirmação) e a F1 (menu de tema); a gaveta é mais um caso. Nada quebra, mas a rolagem do fundo não trava. Proposta: tratar na F3, que traz a paleta (outro diálogo).

### F3 — Paleta de comandos e atalhos
P4, com a lista de atalhos em `/system`. Registro: D-090.

**Como foi entregue (17/10/2026)**
- **Paleta** (`components/shell/CommandPalette.tsx`, chunk próprio carregado no primeiro uso): abre com ⌘K/Ctrl+K ou pelo botão "Buscar" da barra superior. Lista as seções do projeto aberto, "Indexar de novo" (com confirmação que diz para onde vão os trechos), as conversas recentes do projeto, os projetos do workspace com o estado, os workspaces e as ações (novo projeto, todos os workspaces, sistema, atalhos e tema). O filtro do `cmdk` olha só o texto visível. Ao navegar, o foco vai para o `<h1>` da tela nova; ao fechar sem navegar, volta para onde estava.
- **Atalhos** (`app/shortcuts.ts`): um único ouvinte na casca e um registro por ação, em que a tela registrada por último vence. `?` mostra a lista num diálogo; `g` seguido de `o`, `p` ou `h` vai para Visão geral, Perguntar ou Histórico; `/` foca o campo da pergunta; `[` e `]` abrem a evidência anterior e a próxima, na ordem da lista, com anúncio para leitores de tela. Teclas sem modificador não agem em campos de texto nem dentro de diálogos e menus, e podem ser desligadas em `/system`, que também lista todos os atalhos.
- **Rolagem travada sob a CSP:** o achado da F2 foi resolvido trocando, por um alias no Vite, o `react-style-singleton` (que o Radix usa para travar a rolagem atrás de diálogos e menus) por uma versão própria que usa uma folha de estilo construída (CSSOM), que o `style-src 'self'` não bloqueia. A CSP não muda; o erro de console sumiu e a rolagem do fundo passou a travar.
- **Verificação:** `npm run check` com 184 testes (eram 163); `npm run e2e` e `npm run e2e:jar` passam, com o `e2e/navigation.spec.ts` novo (paleta, atalhos, rolagem travada e nenhum erro de console ou violação de CSP; axe WCAG 2.2 AA na paleta nos dois temas e no diálogo de atalhos). Sem o substituto do `react-style-singleton`, esse spec falha. Casca: 165,7 kB com gzip (era 163,6; teto de 175 kB); a paleta tem 19,0 kB (7,0 kB com gzip) e o diálogo de atalhos, 1,4 kB, ambos fora dela.

**Desvios:**
- **"Abrir arquivo" e `g a` ficam para a F8**, junto com o endpoint `GET /snapshots/{id}/tree` e a aba Arquivos, que ainda não existem.
- **Sem busca pela API:** a API não tem busca por nome, e a única mudança de backend da etapa é a da P5.7. A paleta lista a primeira página de workspaces, os projetos do workspace atual e as conversas do projeto aberto (as mesmas consultas da casca, do cache); o que está além disso continua nas telas, pela paginação.
- **Só os atalhos de uma tecla são desligáveis**, não "todos": a WCAG 2.1.4 trata das teclas sem modificador, e ⌘K, ⌘Enter e Esc não disparam por acidente nem atrapalham leitores de tela.
- **"Indexar de novo" pela paleta** mostra, na confirmação, o mesmo texto de privacidade do painel de indexação; confirmar vale como o "Entendi" daquele painel. O `ConfirmDialog` ganhou um modo controlado (sem gatilho) e a variante primária do botão.
- **Foco ao fechar diálogos sem gatilho:** o Radix devolve o foco ao `Trigger`; sem ele (paleta, diálogo de atalhos e a gaveta da F2), o foco caía no `<body>`. O `useReturnFocus` devolve o foco ao elemento de origem, a menos que outro elemento já o tenha recebido (o `<h1>` depois de uma navegação). Corrige também a gaveta, que tinha o problema desde a F2.
- **Dica de teclas fora do botão "Buscar":** o axe (`label-content-name-mismatch`, WCAG 2.5.3) conta o texto visível do `⌘ K` dentro do botão; a dica fica sobreposta, fora dele, sem receber cliques.
- **Paleta montada sobre o diálogo do Radix**, com o `Command` do `cmdk` dentro, em vez do `Command.Dialog`: assim ela tem título para leitores de tela e o mesmo tratamento de foco dos outros diálogos.
- **Testes instáveis sob carga:** numa execução do `npm run check` com a máquina muito carregada (load 22), dez testes antigos estouraram o tempo de 1 s das buscas do Testing Library; repetidos, todos passam. Não foi mudado nada por isso.

### F4 — Início e workspace
P5.1 e P5.2.

**Como foi entregue (17/10/2026)**
- **Primeiro uso:** apresentação com a marca, os três passos ilustrados (criar workspace → importar projeto → perguntar), a frase de privacidade num `Callout` e o formulário com o nome opcional, que explica o padrão ("Meus projetos").
- **Início com dados:** "Continuar de onde parou" em cartões de projeto, a grade de workspaces (nome, quantidade de projetos e criação em tempo relativo) com menu "⋯" (abrir, excluir) e o "Novo workspace" num cartão no fim.
- **Workspace:** cabeçalho com contagem e criação; cartões de projeto com o ícone da origem (pasta ou ZIP), `StatusBadge` (inclui "falhou"), barra de progresso quando indexando, data do último snapshot e menu "⋯" (abrir, perguntar, indexar de novo, excluir). Estado vazio com ilustração e "Importar o primeiro projeto".
- **Componentes novos:** `TileGrid`/`Tile`/`TileMenu`/`TileGridSkeleton` (cartão inteiro clicável pelo link do título, com o anel de foco no cartão; menu e dicas por cima) e as ilustrações em SVG inline sobre os tokens, que seguem o tema. O `EmptyState` aceita uma ilustração no lugar do ícone. Esqueletos no formato da grade no lugar de "Carregando…", com o texto para leitores de tela.
- **Verificação:** `npm run check` com 188 testes (eram 184); `npm run e2e` e `npm run e2e:jar` passam. Passada descartável com axe (WCAG 2.2 AA, com contraste) no primeiro uso, no início com dados e no workspace (com projetos pronto, indexando, aguardando ZIP e com falha), nos dois temas, em 1440 e 390 px: **nenhuma violação**. Casca: 166,3 kB com gzip (era 165,7; teto de 175 kB).

**Desvios:**
- **Frase de privacidade mais precisa que a do plano:** com um provedor remoto de embeddings, a indexação também envia trechos, não só as perguntas. O texto diz as duas coisas.
- **Nome sugerido como dica, não preenchido:** o campo continua vazio (o servidor assume "Meus projetos", `06` e D-032), e a dica diz isso; preencher o valor mudaria o que se envia sem a pessoa digitar.
- **"Indexar de novo" pelo menu** segue a regra da paleta (projeto com snapshot e sem indexação em andamento) e usa o mesmo diálogo, que saiu da paleta para `features/project/ReindexDialog.tsx`; os textos passaram de `palette.reindexDialog` para `reindexDialog`.
- **Cartões de "Continuar" sem menu:** são atalhos; as ações ficam no workspace.
- **Foco ao fechar um diálogo aberto por um item de menu:** o diálogo abre enquanto o item ainda tem o foco, e o item some com o menu; o foco caía no `<body>`. O `useReturnFocus` passou a devolver o foco ao botão que abriu o menu, e o `ConfirmDialog` sem gatilho passou a usá-lo, como o `Dialog`.
- **E2E de navegação falhando já no commit da F3:** o axe media o contraste do diálogo de atalhos durante a animação de entrada (as teclas ainda semitransparentes) e acusava `color-contrast`. Falhava de forma consistente também sem as mudanças da F4. O teste agora espera o fim das animações finitas antes do axe; a interface não mudou.
- **Achado anterior à F4, não corrigido:** com um menu do Radix aberto, o axe acusa `aria-hidden-focus` no resto da página (o Radix a esconde dos leitores de tela enquanto o foco fica preso no menu). Acontece desde o menu de tema da F1. Proposta: tratar na passada de acessibilidade da F10.

### F5 — Assistente e pré-análise
P5.3, incluindo a área de ZIP e a pré-análise em duas colunas.

### F6 — Visão geral do projeto
P5.4, com o cartão de estado único, "Comece por aqui", uso e snapshots; toasts de indexação e o progresso no título da aba (P6).

### F7 — Perguntar
P5.5 e a formatação da P7: divisor, campo fixo, cartões de resposta, `HoverCard` das citações, evidências agrupadas, cabeçalho do visualizador. Registro: D-091 (revê a D-075 na formatação).

### F8 — Arquivos, histórico, configurações e sistema
P5.6 a P5.9. Primeiro o endpoint `GET /snapshots/{id}/tree` (backend, contrato, teste de integração, tipos regenerados), depois a tela. Registro: D-092 (explorador e endpoint).

### F9 — Realce de sintaxe (cortável)
P8, com os limites medidos na F0. Registro: D-093 (revê a D-075 e a D-086).

### F10 — Responsividade, acessibilidade e regressão visual
Passadas nas três larguras e nos dois temas, os specs novos da P13, as imagens de referência, o orçamento de desempenho medido contra a linha de base e a passada manual (roteiro abaixo).

### F11 — Fechamento
- `03-UX-ONBOARDING.md`: §2 (navegação), §4 (layout da pergunta) e §8 (identidade visual decidida).
- `02-ARCHITECTURE.md` §3 e §6 (dependências e estrutura de `apps/web`), `06` (o endpoint da P5.7), `09` (Etapa 9 e renumeração da P0), `10-DECISIONS.md` (D-087 a D-093), `README` (screenshots).
- "Resultado e desvios" neste plano.

## Roteiro da passada manual (F10)

Só com o teclado, depois com o VoiceOver (macOS):
1. Abrir `/` sem dados, usar "Pular para o conteúdo", criar um workspace.
2. Importar `sample-shop` por pasta: escolher a origem, navegar nas pastas pelas setas, revisar a pré-análise e abrir um acordeão de exclusões.
3. Acompanhar a indexação: ouvir os marcos anunciados, cancelar uma vez e indexar de novo.
4. Pela paleta (⌘K), ir para "Perguntar"; usar uma pergunta sugerida, enviar com ⌘Enter.
5. Percorrer as citações com Tab e `[`/`]`, abrir uma, conferir o anúncio das linhas citadas, redimensionar o divisor pelas setas.
6. Abrir o arquivo em Arquivos, filtrar os não indexados, voltar pelo histórico do navegador.
7. Excluir a conversa pelo histórico e o projeto pelo menu "⋯", confirmando nos diálogos.
8. Repetir 1 e 4 no tema escuro, com "Aumentar contraste" ligado e com "Reduzir movimento" ligado.

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Etapa grande demais | Fatias independentes e entregáveis; F8 (explorador) e F9 (realce) podem ser cortadas sem afetar o resto |
| O redesenho quebra os 100 testes atuais | Mesmos papéis e rótulos; classes usadas pelo E2E mantidas; testes ajustados na mesma fatia |
| Screenshots instáveis | Fontes auto-hospedadas, dados mascarados, um sistema de referência, limiar de diferença pequeno |
| Dependências novas | Só quatro grupos, auditados na F0; divisor feito à mão |
| Realce sobre conteúdo não confiável | Worker, sem HTML, limites de tamanho e de tempo, texto puro como saída de emergência |
| Peso das fontes e da casca | Orçamento da P13, medido contra a linha de base da F0 |
| Visual "bonito" piorar a leitura de código | Visualizador e evidências validados primeiro no catálogo; legibilidade do código é o critério de desempate (`03` §8) |

## Decisões a registrar

| Proposta | Decisão | Fatia |
|---|---|---|
| P0 | renumeração do roteiro (atualização datada na D-027 e no `09`) | F11 |
| P1, P2, P12 | D-087 — direção visual, sistema de design e dependências | F1 |
| P9 | D-088 — tema | F1 |
| P3, P10 | D-089 — casca, navegação e responsividade (revê a D-077) | F2 |
| P4 | D-090 — paleta de comandos e atalhos | F3 |
| P7 | D-091 — formatação leve da resposta (revê a D-075) | F7 |
| P5.7 | D-092 — explorador e `GET /snapshots/{id}/tree` | F8 |
| P8 | D-093 — realce de sintaxe (revê a D-075 e a D-086) | F9 |

## Resultado e desvios

A preencher no fechamento.
