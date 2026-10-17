# Plano — Etapa 7: UX completa do MVP

**Status:** plano aprovado em 16/10/2026 com as recomendações do plano (branch `etapa-7`), com os ajustes da F0 em P1, P5 e P7. As propostas P1 a P11 viram D-070 em diante, registradas em `10-DECISIONS.md` na fatia indicada em cada uma.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 7), `docs/plans/etapa-6.md` (pendências), `01-PRD.md` (F-01 a F-09), `02-ARCHITECTURE.md` §1 e §3, `03-UX-ONBOARDING.md` (todo), `06-API-CONTRACTS.md` §3 a §10, `07-SECURITY-PRIVACY-COSTS.md` §2, §7 e §9, D-018, D-022, D-023, D-025, D-026, D-030, D-044, D-046, D-064, D-067, D-068.

## Escopo

**Dentro:**
- scaffold de `apps/web` (React + Vite + TypeScript), com tipos gerados do `openapi.json` (D-023, D-026);
- workspace e lista de projetos; criação por pasta local (navegação nas raízes permitidas) e por ZIP (upload e estado do import);
- pré-análise (inventário) com revisão das exclusões e dos limites antes de indexar;
- progresso e erros da indexação, com a ação de indexar de novo;
- inventário do snapshot ativo, `outdatedReasons`, snapshots e arquivos não indexados;
- chat com painel de evidências e visualizador de código com as linhas destacadas;
- histórico de conversas e métricas por resposta (duração, evidências, tokens, custo medido, estimado ou indisponível);
- aviso de envio a provedor remoto (`03` §7);
- os endpoints que a UI precisa e o backend ainda não tem (P3, P4).

**Pendências herdadas e onde são resolvidas:**

| Pendência | Origem | Onde |
|---|---|---|
| `GET /snapshots/{snapshotId}/files/content` (visualizador, F-06) | `06` §9, Etapa 6 | P3; F1 |
| "Arquivo mudou desde o snapshot" (F-06) | `01-PRD.md` | P3; F1, F6 |
| Streaming da resposta (SSE) | Etapa 6 | P6 (proposta: não) |
| Aviso de envio ao provedor na UI | Etapa 6 | P4; F1, F6 |
| Biblioteca de UI e estado do frontend | D-026, `02` §8 | P1; F0, F2 |
| Avaliação de respostas e testes de fumaça com o provedor real | Etapas 4 a 6 | fora desta etapa: continua dependendo de saldo e não bloqueia nada aqui (a UI roda com os provedores `fake`) |

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Eventos de produto (`03` §9) | Etapa 8, junto com as medições de latência e custo |
| Métricas agregadas por projeto (custo total, latência média) | Etapa 8, se o uso pedir; aqui, por resposta |
| Cancelamento de jobs, retenção de conversas | Etapa 8 |
| Realce de sintaxe por linguagem | Etapa 8, se a legibilidade pedir (P7) |
| Comparação entre snapshots, mapa arquitetural | Etapa 9 (D-027) |
| Empacotamento num único executável (backend servindo a UI) | Etapa 8 (P2) |
| Tela de configuração do provedor (chave, modelo) | fora do MVP: continua no `.env`, a UI só mostra o que está configurado |

## Pré-requisito externo

Nenhum. A UI é desenvolvida e testada com `EMBEDDING_PROVIDER=fake` e `GENERATION_PROVIDER=fake`. A única coisa nova no ambiente é o **Node.js** (a versão mínima sai na F0; hoje a máquina tem a 22.23).

## Propostas para aprovação

### P1 — Stack do frontend (resolve a parte pendente da D-026) → F0, F2

**Problema:** a D-026 deixou para esta etapa a biblioteca de componentes e o gerenciamento de estado. A escolha define as dependências de `apps/web` por muito tempo.

**Proposta** (versões exatas conferidas na F0 em fonte oficial, como na D-014; nenhuma de memória):

| Função | Escolha | Alternativa descartada e por quê |
|---|---|---|
| Base | React + Vite + TypeScript estrito (`02` §3) | — |
| Gerenciador de pacotes | npm, com `package-lock.json` versionado e versões exatas | pnpm/yarn: ferramenta a mais para instalar |
| Estado do servidor | TanStack Query (cache, polling do progresso, invalidação depois das mutações) | Redux/Zustand: quase todo o estado é do servidor; o resto cabe em `useState` e na URL |
| Rotas | React Router (modo biblioteca, sem SSR) | TanStack Router: menos difundido; o ganho de tipos é pequeno com poucas rotas |
| Cliente HTTP e tipos | `openapi-typescript` (tipos gerados do `apps/backend/openapi.json`) + `openapi-fetch` | gerador de cliente completo (orval, openapi-generator): mais código gerado para revisar |
| Componentes | Radix UI (primitivos sem estilo e acessíveis: diálogo, abas, menu, tooltip) + CSS próprio com variáveis | biblioteca com estilo (MUI, Mantine): pesada e com identidade visual que o `03` §8 deixa para depois; Tailwind: outra linguagem de estilo para manter |
| Testes | Vitest + Testing Library + MSW (API simulada pelos tipos gerados) | Jest: configuração extra com Vite |
| E2E | Playwright, fora do `verify` (P10) | Cypress: mais pesado, sem ganho aqui |
| Qualidade | ESLint (regras de hooks e de acessibilidade `jsx-a11y`) + Prettier + `tsc --noEmit` | — |

O `npm run check` (tipos, lint, testes, build) é o equivalente ao `./mvnw verify` para o frontend.

**Ajustes da F0:** TypeScript **5.9.3**, não o 7: o `typescript-eslint` aceita `<6.1.0` e o `openapi-typescript`, `^5.x`. **ESLint 10 sem o `eslint-plugin-jsx-a11y`**: o plugin não declara suporte ao ESLint 10 (último release em 2024), e o ESLint 9 está marcado como sem suporte no registro. A acessibilidade passa a ser verificada pelo `axe-core` nos testes de componente e pelo `@axe-core/playwright` no E2E. Virtualização do visualizador com `@tanstack/react-virtual` (P7).

### P2 — Como a UI chega ao backend → F2

**Problema:** UI e API em portas diferentes exigem CORS ou um proxy; e falta definir como a pessoa roda o produto.

**Opções:**
1. Em desenvolvimento, o servidor do Vite faz proxy de `/api` para o backend (mesma origem para o browser, sem CORS). Para uso, `npm run build` e `npm run preview`, com o mesmo proxy.
2. O backend serve o build da UI (`apps/web/dist` copiado para os recursos estáticos), num processo só.
3. CORS liberado para a origem do Vite.

**Proposta:** opção 1 no MVP. Sem CORS no backend (nenhuma origem cruzada liberada, o que ajuda a P5) e sem acoplar o build Maven ao npm. O README passa a ter dois comandos para subir o produto. A opção 2 fica para a Etapa 8, quando o empacotamento for discutido.

### P3 — Conteúdo do arquivo e versão atual (F-06; completa `06` §9) → F1

**Problema:** o visualizador precisa do arquivo inteiro como estava no snapshot, e o PRD pede avisar quando o arquivo mudou depois da resposta.

**Proposta:**
- `GET /snapshots/{snapshotId}/files/content?path=<relativo>` → `200` com `{ snapshotId, path, language, contentHash, sizeBytes, lineCount, content }`, lido de `snapshot_files` + `file_contents` (nada do disco). JSON, não `text/plain`, para trazer os metadados e manter o conteúdo como dado (nunca servido como página).
  - `path` com a normalização de `relativePath` (§5); a comparação é exata.
  - Erros, nesta ordem: `400 VALIDATION_FAILED` (`path`); `404 SNAPSHOT_NOT_FOUND`; `409 SNAPSHOT_PRUNED` (snapshot podado; a UI mostra a cópia da evidência); `404 FILE_NOT_FOUND` (fora do snapshot); `409 FILE_NOT_INDEXED` (`UNSUPPORTED` ou `FAILED`, sem conteúdo).
  - O tamanho é limitado pelo `max-file-bytes` da indexação (teto de 10 MB, D-036); sem paginação de linhas no MVP.
  - Log: ids e tamanho, nunca o caminho nem o conteúdo.
- Em `GET /projects/{projectId}/evidence/{evidenceId}`, um campo novo `activeVersion`: `SAME` (o snapshot ativo tem o arquivo com o mesmo `contentHash`), `CHANGED`, `REMOVED` (o ativo não tem o caminho) ou `NO_ACTIVE_SNAPSHOT`. Calculado numa consulta, sem ler conteúdo. A UI avisa "este arquivo mudou desde a resposta" e oferece abrir as duas versões.
- Mudanças só aditivas no contrato; `openapi.json` regenerado.

### P4 — O que está configurado: provedores e privacidade (`03` §7) → F1

**Problema:** antes de indexar e de perguntar, a UI precisa dizer qual provedor recebe trechos, e hoje nenhum endpoint diz isso. Também precisa saber se pode oferecer perguntas e os modos `VECTOR`/`HYBRID` sem tentar e errar.

**Proposta:** `GET /system/providers` → `{ embeddings: { provider, model, remote }, generation: { provider, model, remote, pricingConfigured } }`.
- `provider`: `none`, `fake` ou `openai`; `remote: true` quando os dados saem da máquina (`openai` com `base-url` fora de `localhost`/`127.0.0.1`).
- Nunca devolve chave, `base-url` nem preço; só se o preço existe (para a UI explicar o `UNKNOWN`).
- A UI mostra o aviso uma vez por projeto antes da primeira indexação ou pergunta com `remote: true` ("trechos selecionados podem ser enviados a <provedor>"), com link para rever as exclusões. A confirmação fica no browser (`localStorage`); não é controle de segurança, é transparência.

### P5 — Servidor local: endereço e origem (`07` §2) → F1

**Problema:** o backend não define `server.address` e escuta em todas as interfaces, sem autenticação; qualquer máquina da rede pode ler o código indexado. Com uma UI no browser, aparece também o risco de uma página maliciosa chamar `localhost` (CSRF; DNS rebinding para ler respostas). O `multipart` do upload é uma requisição "simples", sem preflight.

**Opções:**
1. Resolver agora: `server.address=127.0.0.1` por padrão (configurável por `SERVER_ADDRESS`), e um filtro que recusa com `403 FORBIDDEN_ORIGIN` requisições com `Host` fora de `localhost`/`127.0.0.1`/`[::1]` (com a porta), e requisições que mudam estado (`POST`, `PUT`, `DELETE`) com `Origin` presente e diferente do próprio host.
2. Deixar para a Etapa 8 (hardening).

**Proposta:** opção 1. É pouco código, testável, e a exposição nasce exatamente com a UI. O proxy do Vite (P2) envia o `Host` do backend e reescreve a `Origin`; verificado na F2. Atualiza `07` e o README.

**Ajuste da F0 (aprovado com a opção 1):** o proxy do Vite com `changeOrigin` reescreve o `Host` para o do backend, mas **mantém a `Origin` do browser** (`http://127.0.0.1:5173`). A regra "Origin igual ao próprio host" recusaria a própria UI. Fica assim:
- `Host` precisa ser `localhost`, `127.0.0.1` ou `[::1]` (qualquer porta); senão, `403 FORBIDDEN_HOST`.
- `POST`, `PUT` e `DELETE` com `Origin` presente precisam ter a `Origin` em `app.http.allowed-origins`, por padrão as portas padrão do Vite (`http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:4173`, `http://127.0.0.1:4173`); senão, `403 FORBIDDEN_ORIGIN`. Sem `Origin` (curl, testes), passa.
- O proxy não reescreve a `Origin`: reescrever deixaria passar um CSRF que chegasse pelo próprio Vite.
- O Vite já recusa com `403` um `Host` desconhecido, no `dev` e no `preview`, o que cobre o DNS rebinding do lado da UI.
- O alvo do proxy é `http://127.0.0.1:<porta>`, não `localhost`: o Node pode resolver `localhost` para `::1`, e o backend passa a escutar só em `127.0.0.1`.

### P6 — Pergunta síncrona, sem streaming → F6

**Problema:** a Etapa 6 deixou o SSE "se a UI pedir". A resposta é estruturada (JSON com citações validadas no fim), então um streaming do texto mostraria marcadores ainda não validados.

**Proposta:** manter o `POST /questions` síncrono. A UI mostra a pergunta na hora, um estado "buscando evidências e gerando resposta" com o tempo decorrido, e o limite (60 s) explicado. Um `FAILED` aparece como resposta com a mensagem do `error`, não como erro de tela. Revisão quando a latência medida (Etapa 8) mostrar espera ruim.

### P7 — Visualizador de código → F6

**Proposta:**
- Texto puro, numeração de linhas, linhas citadas (`citedStartLine`–`citedEndLine`, senão `startLine`–`endLine`) destacadas por fundo **e** por marcador na margem (não só cor, `03` §8), rolagem até o trecho.
- Sem realce de sintaxe no MVP: evita uma dependência grande e o parser rodando sobre conteúdo não confiável; o `03` prioriza legibilidade, que monoespaçada e contraste resolvem. Revisão na Etapa 8.
- Arquivos grandes: renderização por janela (só as linhas visíveis) a partir de um limite medido na F6 (alvo: um arquivo de 1 MB abre sem travar). **Ajuste da F0:** virtualizar sempre, sem limite. O pior caso é de dezenas de milhares de linhas (até 1 MB por arquivo, teto de 10 MB, e até 4.000 caracteres por linha, D-043), e um caminho só é mais simples de testar.
- Snapshot podado (`snapshotStatus: PRUNED`): mostra só o `snippet` da evidência, com a explicação.

### P8 — Renderização segura do conteúdo → F2, F6

**Problema:** respostas do modelo, trechos e nomes de arquivo vêm de conteúdo não confiável (prompt injection pode produzir HTML, links ou markdown enganoso).

**Proposta:**
- Nenhum `dangerouslySetInnerHTML` (regra de lint que falha o build); tudo vira texto pelo React.
- A resposta é texto com quebras de linha; os marcadores `[E1]` viram botões que abrem a evidência. Sem markdown no MVP, portanto sem links clicáveis vindos do modelo.
- CSP no `index.html` (`default-src 'self'`, sem `unsafe-inline` em script); verificado no build de `preview`.

### P9 — Navegação e telas → F3 a F7

Rotas (pt-BR na UI, inglês no código):

| Rota | Tela | Contratos |
|---|---|---|
| `/` | workspaces; sem nenhum, o primeiro uso (nome opcional, `03` §3 etapa 1) | §3 |
| `/workspaces/:id` | projetos com estado (indexando, pronto, desatualizado, falhou) e o novo projeto | §3, §5 |
| `/workspaces/:id/new` | assistente: origem (pasta ou ZIP) → navegação/ upload → pré-análise com exclusões → indexar | §4 a §7 |
| `/projects/:id` | resumo: snapshot ativo, contagens, `outdatedReasons` com ação, job em andamento, inventário, arquivos não indexados, snapshots | §5, §7, §9 |
| `/projects/:id/ask` e `/projects/:id/conversations/:cid` | chat, evidências e visualizador (abas em telas estreitas, `03` §4) | §8 a §10 |
| `/projects/:id/history` | conversas | §10 |

- Progresso por polling (TanStack Query, 1 s com job ativo, parado sem job); sem SSE para jobs.
- Erros: a UI mostra `title` e `detail` do ProblemDetail (já em pt-BR, D-022) e o `requestId`, e decide a ação pelo `code`, nunca pelo texto. Os estados do `03` §6 têm um tratamento por `code`, listado na F3.
- Perguntas sugeridas (`03` §3 etapa 5): as quatro genéricas do `03`, mostradas só com snapshot ativo; nenhuma cita arquivo.
- Exclusão de projeto e de workspace com confirmação, com os `409` explicados.
- Textos ao usuário centralizados num arquivo (`messages.ts`), em pt-BR; sem biblioteca de i18n (D-025).

### P10 — Aceite automatizado → F8

**Proposta:** um teste Playwright do aceite, de ponta a ponta: cria workspace, cria projeto por pasta local (um repositório de exemplo versionado em `apps/web/e2e/fixtures`), revisa a pré-análise, indexa, pergunta, abre a evidência no visualizador e encontra a conversa no histórico. Roda contra o backend real com o banco do Compose e os provedores `fake`, por `npm run e2e`, fora do `verify` (como os harnesses, D-062). Mais uma checagem de acessibilidade (axe) nas telas principais no mesmo teste, e uma passada manual por teclado registrada no fechamento.

### P11 — Repositório e módulos → F1, F2

- `apps/web` com `src/api` (cliente e tipos gerados), `src/features/<tela>`, `src/components` (primitivos), `src/messages.ts`.
- Os tipos gerados ficam versionados; um teste do frontend falha se estiverem diferentes do `openapi.json` (o mesmo papel do teste do contrato no backend, D-023).
- No backend, o conteúdo do arquivo fica em `indexing` (dono de `file_contents`), a versão ativa da evidência em `answering` lendo por porta de `indexing` (regra de dependências da P11 da Etapa 6), e `GET /system/providers` em `providers`. O filtro da P5 fica em `shared`.

## Princípios de execução

- Uma fatia por vez, com `./mvnw verify` e `npm run check` passando ao fim de cada uma.
- O backend só muda na F1; as fatias seguintes não mudam o contrato, salvo desvio registrado.
- Toda tela nova com teste de componente (MSW), cobrindo carregando, vazio, erro e sucesso.
- Nada de conteúdo de código, pergunta ou resposta em `console.log` nem em logs.
- Os textos ao usuário seguem os do `03`; mudança de fluxo vira desvio.

## Fatias, na ordem

### F0 — Verificações (sem código de produção)
- Versões estáveis atuais de cada dependência da P1 e a versão mínima de Node exigida pelo Vite, em fonte oficial, com data; registro na D-070.
- Confirmar que o proxy do Vite preserva o que a P5 exige (`Host`, `Origin`) e que o `openapi-typescript` lê o `openapi.json` atual (polimorfismo de `source`, enums de `code`).
- Medir o tamanho do maior `file_contents` de um projeto real indexado, para a P7.

**Resultado (16/10/2026)**

*Versões (P1)*, no registro npm (`npm view`, a fonte de publicação de cada pacote), consultadas em 16/10/2026. Últimas estáveis, com a data de publicação:

| Pacote | Versão | Publicada | Node exigido |
|---|---|---|---|
| `react`, `react-dom` | 19.3.1 | 14/10/2026 | — |
| `@types/react`, `@types/react-dom` | 19.3.0 | 09/09/2026 | — |
| `vite` | 8.3.4 | 08/10/2026 | `^20.19.0 \|\| >=22.12.0` |
| `@vitejs/plugin-react` | 6.1.2 | 05/10/2026 | igual ao Vite |
| `typescript` | **5.9.3** (a última é 7.0.2) | 30/09/2025 | — |
| `react-router` | 8.4.0 | 15/09/2026 | `>=22.22.0` |
| `@tanstack/react-query` | 5.104.3 | 15/10/2026 | — |
| `@tanstack/react-virtual` | 3.14.21 | 16/10/2026 | — |
| `radix-ui` | 1.8.1 | 12/10/2026 | — |
| `openapi-typescript` | 7.13.0 | 11/02/2026 | — |
| `openapi-fetch` | 0.17.0 | 11/02/2026 | — |
| `vitest` | 5.0.5 | 15/10/2026 | `^22.12.0 \|\| ^24 \|\| >=26` |
| `jsdom` | 30.1.3 | 15/10/2026 | `^22.22.2 \|\| ^24.15.0 \|\| >=26` |
| `@testing-library/react` | 16.3.3 | 27/08/2026 | — |
| `@testing-library/user-event` | 14.6.8 | 13/10/2026 | — |
| `@testing-library/jest-dom` | 7.0.1 | 09/08/2026 | `>=22` |
| `msw` | 3.0.3 | 10/10/2026 | `>=22.12.0` |
| `@playwright/test` | 1.64.1 | 14/10/2026 | `>=20` |
| `axe-core` / `@axe-core/playwright` | 4.14.0 | 05/10 e 15/10/2026 | `>=22.12.0` |
| `eslint` | 10.13.0 | 16/10/2026 | `^20.19.0 \|\| ^22.13.0 \|\| >=24` |
| `@eslint/js` | 10.0.1 | 06/02/2026 | igual ao ESLint |
| `typescript-eslint` | 8.71.2 | 12/10/2026 | — |
| `eslint-plugin-react-hooks` | 7.1.1 | 17/04/2026 | — |
| `globals` | 17.13.0 | 01/10/2026 | — |
| `prettier` | 3.9.10 | 10/10/2026 | — |

- **Node mínimo: 22.22.2** (`jsdom`), também acima do `react-router` (22.22.0). A máquina tem a 22.23.3 e o npm 10.9.9; vale qualquer 24.15+.
- **Conflitos de pares:** o TypeScript 7.0.2 fica fora do `typescript-eslint` 8.71.2 (`>=4.8.4 <6.1.0`) e do `openapi-typescript` 7.13.0 (`^5.x`); o 6.0.3 ainda quebra o segundo. O **5.9.3** satisfaz todos, inclusive o `msw` (`>=5.9.x`). O `eslint-plugin-jsx-a11y` 6.10.2 aceita o ESLint até o 9, e o ESLint 9.39.5 é instalado com o aviso "This version is no longer supported". Daí o ESLint 10 sem o plugin, e o `axe-core` nos testes.
- **Instalação de teste** (no scratchpad, fora do repositório): o conjunto inteiro resolve com `--save-exact`, sem `--legacy-peer-deps` e sem dependência inválida no `npm ls`. Com `--ignore-scripts`, o único pacote com script de instalação é o `fsevents` (opcional, macOS), dispensável. Proposta para a F2: `.npmrc` com `ignore-scripts=true` e `save-exact=true`; os browsers do Playwright são baixados à parte, com `npx playwright install chromium`.

*Tipos do contrato:* o `openapi-typescript` 7.13.0 gera os tipos do `openapi.json` atual sem aviso (2.679 linhas, em 0,1 s). O `source` polimórfico vira uma união discriminada por `type` (`LOCAL_FOLDER` | `ZIP`), e um `switch` sobre `p.source.type` estreita os campos com `tsc --strict`. O `Problem.code` sai como união dos códigos. **Achado:** os schemas do inventário (`ScanResult`, `Totals`, `LanguageCount`, `DirectoryCount`, `ExclusionGroup`) não terminam em `*Response`, escapam da regra "todo campo de resposta é obrigatório" (`06` §11) e geram campos opcionais (`totals?`, `files?`). A correção, só no contrato, entra na F1.

*Proxy do Vite (P2, P5):* um servidor de eco local (Node, porta 18080) atrás do Vite 8.3.4, no `dev` e no `preview`:

| Caso | `Host` no backend | `Origin` no backend |
|---|---|---|
| `changeOrigin: true`, POST do browser | `127.0.0.1:18080` | `http://127.0.0.1:15173` (preservada) |
| `changeOrigin: false` | `127.0.0.1:15173` | `http://127.0.0.1:15173` |
| `preview`, `changeOrigin: true` | `127.0.0.1:18080` | `http://127.0.0.1:15174` |

O Vite recusa `Host: evil.example` com `403` no `dev` e no `preview`, e aceita `localhost`. Nenhum dos dois acrescenta `X-Forwarded-Host`. Consequência: ajuste da P5 (lista de origens permitidas).

*Tamanho dos arquivos (P7):* o banco local não tem nenhum projeto indexado (`file_contents` vazia), então a medição foi feita neste repositório: 408 arquivos versionados, o maior com 134 KB e 1.162 linhas, p99 de 47 KB. O pior caso vem dos limites: 1 MB por arquivo por padrão (teto de 10 MB) e até 4.000 caracteres por linha (D-043), ou seja, dezenas de milhares de linhas. Consequência: ajuste da P7 (virtualizar sempre). A meta de desempenho fica na F6, com um arquivo sintético de 1 MB.

### F1 — Backend para a UI
- `GET /snapshots/{snapshotId}/files/content` e `activeVersion` na evidência (P3), com testes de integração (podado, não indexado, outro snapshot, caminho normalizado).
- `GET /system/providers` (P4).
- `server.address` e o filtro de `Host`/`Origin` (P5, com o ajuste da F0), com testes.
- Campos obrigatórios nos schemas do inventário (achado da F0).
- `openapi.json` regenerado; `06`, `07` e README atualizados; D-071 a D-073.

**Como foi entregue (16/10/2026)**
- `GET /snapshots/{snapshotId}/files/content` em `indexing` (`SnapshotService.content`, uma consulta em `snapshot_files` + `file_contents`), com os erros na ordem da P3 (`SnapshotFileContentApiIT`). Caminho vazio ou a própria raiz também é `400`.
- `activeVersion` na evidência (D-071). **Desvio:** a comparação está na fachada `Retrieval` (`SearchSnapshots.findActiveFile`), não numa porta de `indexing`, para manter as dependências de `answering` da P11 da Etapa 6 (`HistoryReadApiIT.comparesTheEvidenceWithTheActiveSnapshot`).
- `GET /system/providers` em `providers` (D-073; `ProvidersApiIT`, `ProvidersDisabledApiIT`, `ProvidersControllerTest`). **Desvio:** sem `pricingConfigured`, porque o preço é configuração de `answering`.
- `server.address=127.0.0.1` e `LocalAccessFilter` (D-072; `LocalAccessFilterIT`, `HttpPropertiesTest`). O filtro é registrado por `@Bean`, não `@Component`, para não entrar nos testes `@WebMvcTest`, que não carregam as propriedades.
- **Achado a mais (D-074):** além dos obrigatórios do inventário, o `@Nullable` do jspecify não chegava ao contrato, e mais de vinte campos anuláveis eram publicados como não nulos. Corrigido de forma geral por um `ModelConverter` (`NullableRecordComponents`), com testes em `OpenApiErrorContractIT`. O JSON das respostas não muda.
- Códigos novos: `SNAPSHOT_PRUNED`, `FILE_NOT_FOUND`, `FILE_NOT_INDEXED`, `FORBIDDEN_HOST` e `FORBIDDEN_ORIGIN`, com mensagens pt-BR.

### F2 — Scaffold de `apps/web`
- Vite + TS + dependências da P1, lint, Prettier, Vitest, MSW, tipos gerados e teste de defasagem.
- Cliente da API, tratamento do ProblemDetail, layout base, rotas vazias, tokens de cor com tema claro/escuro, CSP.
- README: Node, `npm ci`, `npm run dev`, `npm run check`.

**Como foi entregue (17/10/2026)**
- `apps/web` com as versões da D-070, instaladas com `--save-exact` e sem conflito de pares; `.npmrc` com `ignore-scripts`, `save-exact` e `engine-strict` (`engines.node >=22.22.2`). Entraram também `@testing-library/dom` 10.4.2 (par obrigatório da Testing Library) e `@types/node` 22.20.5 (configuração do Vite e testes que rodam em Node). Playwright e `@axe-core/playwright` ficam para a F8, onde são usados.
- `npm run check`: `tsc` (projetos da app e da configuração), ESLint (`typescript-eslint` estrito, regras de hooks, `no-console` e proibição de `dangerouslySetInnerHTML`, P8), Prettier, Vitest e build.
- Tipos gerados em `src/api/schema.d.ts` (`npm run api:types`); o teste de defasagem roda a CLI do `openapi-typescript` com `--check` e falha se o arquivo diferir do `openapi.json` (conferido com uma alteração proposital).
- Cliente `openapi-fetch` (`src/api/client.ts`) e `unwrap` (`src/api/errors.ts`): sucesso devolve o corpo; erro vira `ApiError` com o ProblemDetail (`code`, `requestId`), ou sem ele quando o corpo não é ProblemDetail (`502` do proxy) ou a conexão falha (`status` 0); o cancelamento do TanStack Query passa como `AbortError`. O `QueryClient` não repete erros `4xx`. `ProblemAlert` mostra `title`, `detail` e `requestId`, com textos próprios para rede e erro inesperado.
- Layout com link "Pular para o conteúdo", rotas da P9 com telas provisórias, página de não encontrado e erro de renderização dentro do layout. Textos em `src/messages.ts`.
- Tokens de cor em CSS (`src/styles/tokens.css`), com tema claro e escuro pelo `prefers-color-scheme`.
- **Desvio (CSP):** a política vai numa `<meta>` injetada só no build (`csp.ts` + plugin no `vite.config.ts`), não no `index.html` versionado: o servidor de desenvolvimento injeta o preâmbulo inline do React Refresh, que a política bloquearia. Sem `unsafe-inline` nem `unsafe-eval`.
- **MSW 3:** a opção de requisição não tratada passou a se chamar `onUnhandledFrame`; os testes falham em qualquer requisição sem handler.
- **Verificação do proxy (P5):** `vite preview` na frente do backend da F1 (provedores `fake`): `GET` e `POST` com a `Origin` da UI passam; `POST` com `Origin: http://evil.example` recebe `403 FORBIDDEN_ORIGIN`; `Host: evil.example` é recusado pelo próprio Vite. No browser (headless), as rotas abrem por link direto, sem erro de console nem violação de CSP.
- Testes: 22 (cliente e erros, retry, `ProblemAlert`, rotas, skip link, axe nas telas, CSP e regras de lint).

### F3 — Workspaces e projetos
- Primeiro uso, lista de workspaces, lista de projetos com estado, exclusões com confirmação.
- Tabela `code` → ação dos estados do `03` §6.

**Como foi entregue (17/10/2026)**
- `/`: sem workspaces, o primeiro uso (`03` §3 etapa 1: nome opcional, dados locais); com workspaces, a lista paginada (`?page=` na URL, base 1) com a contagem de projetos e o formulário de novo workspace. Criar abre o workspace.
- `/workspaces/:id`: projetos paginados com origem e estado em texto (não só cor, `03` §8), progresso do job ativo, link para o projeto e para o novo projeto; vazio com a chamada para importar. Com algum job ativo na página, a lista é consultada a cada 1 s (P9).
- Exclusões com `ConfirmDialog` (Radix `AlertDialog`): o diálogo fica aberto até a resposta e mostra o `409` no lugar (`WORKSPACE_NOT_EMPTY`, `PROJECT_BUSY`), com o aviso antecipado quando o workspace tem projetos ou o projeto tem job.
- **Tabela `code` → ação** em `src/api/recovery.ts`, um `Record<ErrorCode, Recovery>`: um `code` novo no contrato quebra o `tsc` até receber uma ação. O `ProblemAlert` mostra o texto da ação (em `messages.recovery`), os erros por campo e, só para `retry`, o botão "Tentar de novo" quando a tela oferece. Sem ProblemDetail: rede e `5xx` são `retry`; o resto, `reload`.

| Ação | Códigos | Estado do `03` §6 |
|---|---|---|
| `retry` | `INTERNAL_ERROR`, falha de rede, `5xx` sem ProblemDetail | banco indisponível |
| `reload` | `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `PROJECT_SOURCE_MISMATCH` | — (UI e servidor fora de sincronia) |
| `fixInput` | `VALIDATION_FAILED`, `PATH_OUTSIDE_ROOT` | — |
| `goBack` | `*_NOT_FOUND` de recursos (workspace, projeto, import, job, snapshot, conversa, resposta, evidência, arquivo) | — |
| `removeProjects` | `WORKSPACE_NOT_EMPTY` | — |
| `waitForJob` | `PROJECT_BUSY` | — |
| `chooseAnother` | `PROJECT_ALREADY_EXISTS`, `DIRECTORY_NOT_FOUND`, `DIRECTORY_NOT_READABLE`, `UPLOAD_TOO_LARGE`, `FILE_NOT_INDEXED` | arquivo ilegível, acima do limite |
| `checkSource` | `SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE` | — |
| `uploadZip` | `SOURCE_NOT_IMPORTED` | — |
| `reindex` | `SNAPSHOT_NOT_SEARCHABLE`, `SNAPSHOT_LEXICAL_MISMATCH`, `SNAPSHOT_EMBEDDING_MISMATCH` | snapshot desatualizado |
| `reviewServerConfig` | `LOCAL_ROOTS_NOT_CONFIGURED`, `ROOT_NOT_FOUND`, `SOURCE_ROOT_NOT_CONFIGURED` | — |
| `configureProvider` | `EMBEDDINGS_NOT_AVAILABLE`, `GENERATION_NOT_AVAILABLE`, `EMBEDDING_PROVIDER_UNAUTHORIZED` | provedor sem credenciais |
| `providerLimit` | `EMBEDDING_PROVIDER_UNAVAILABLE`, `EMBEDDING_QUOTA_EXCEEDED`, `EMBEDDING_REQUEST_REJECTED`, `EMBEDDING_RATE_LIMITED` | limite ou erro do provedor |
| `useEvidenceCopy` | `SNAPSHOT_PRUNED` | — |
| `openLocally` | `FORBIDDEN_HOST`, `FORBIDDEN_ORIGIN` | — |

  Os demais estados do `03` §6 não são erros HTTP e ficam nas telas que os mostram: pasta sem arquivos suportados e todos excluídos (aviso `NO_INCLUDED_FILES` e totais, F4), indexação parcial (`PARTIAL`, F5), snapshot desatualizado (`outdatedReasons`, F5) e nenhuma evidência suficiente (aviso `NO_EVIDENCE`, F6).
- **Desvio (estado "falhou" na lista):** o `ProjectResponse` não traz o último job, só o ativo, então a lista não distingue um projeto cuja indexação falhou. Os estados derivados são: indexando, aguardando o ZIP, não indexado, desatualizado, pronto com arquivos não indexados e pronto (`projectStatus.ts`). A falha aparece no resumo do projeto (F5, `GET /index-jobs`); buscar o último job de cada projeto da página custaria uma chamada por item, e mudar o contrato fica fora das fatias depois da F1.
- Testes: 52 (30 novos: tabela de ações, estado do projeto, `ProblemAlert`, as duas telas com carregando, vazio, erro com e sem retry, paginação, criação, exclusão com sucesso e com `409`, e axe nas telas e no diálogo).

### F4 — Importação e pré-análise
- Assistente: pasta local (navegação nas raízes, `hasChildren`, `readable`, `truncated`) ou ZIP (upload, estado do import, `REJECTED` com a mensagem).
- Pré-análise: totais, linguagens, diretórios, exclusões com exemplos, avisos; edição de padrões e regras (`PUT /settings`, erros por campo); aviso de privacidade (P4); indexar.

### F5 — Projeto, progresso e inventário
- Resumo do projeto, snapshot ativo, `outdatedReasons` com "indexar de novo".
- Progresso por etapa (`stage`, `progress`), falha com a mensagem e a ação, `PARTIAL` com os arquivos `FAILED`/`UNSUPPORTED` (filtros de `GET /files`).
- Lista de snapshots com `changes`.

### F6 — Chat, evidências e visualizador
- Pergunta, estado de espera (P6), resposta com marcadores, avisos (`NO_EVIDENCE`, `INVALID_CITATIONS_REMOVED`, `NO_VALID_CITATIONS`), `FAILED`.
- Painel de evidências (citadas primeiro; `NOT_CITED` e `INVALID_RANGE` distinguidos por texto), métricas da resposta com custo `ESTIMATED`/`UNKNOWN`.
- Visualizador (P7) com `activeVersion` e `PRUNED`.
- Modo de recuperação e `maxEvidence` num painel "avançado" recolhido; modos indisponíveis desabilitados pela P4.

### F7 — Histórico
- Lista de conversas paginada e reabertura com todas as respostas, inclusive `FAILED` e `PENDING`.

### F8 — Aceite de ponta a ponta
- Teste Playwright e axe da P10; passada manual por teclado.

### F9 — Fechamento
- Roteiro atualizado, "Resultado e desvios" neste plano, `02` (estrutura de `apps/web`), `03` se o fluxo mudou, README (estado atual), `docs/README.md`.

## Resultado e desvios

A preencher no fechamento.
