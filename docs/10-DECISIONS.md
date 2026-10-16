# Registro de decisões — Codebase Copilot

Este documento registra decisões aceitas e itens que ainda precisam de validação. Atualizar sempre que uma escolha técnica mudar.

**Última atualização:** 15/10/2026.

**Legenda de status**
- **Aceita:** decisão tomada; só muda com novo registro.
- **Aceita (provisória):** decisão tomada para destravar a implementação, mas com um experimento já previsto que pode revisá-la.
- **A validar:** ainda depende de protótipo, benchmark ou teste.

---

## Decisões aceitas

### D-001 — Nome do produto
**Status:** Aceita  
**Decisão:** Codebase Copilot.  
**Motivo:** nome aprovado para o projeto.

### D-002 — Local-first
**Status:** Aceita  
**Decisão:** o produto será local-first.  
**Consequência:** código e índices ficam no ambiente local por padrão; o uso de provedor remoto deve ser transparente. Local-first não significa offline.

### D-003 — Entrada principal por pasta local
**Status:** Aceita  
**Decisão:** seleção de pasta local é o fluxo principal; ZIP é alternativa.  
**Consequência:** o mecanismo de acesso local deve ser definido com segurança, sem presumir acesso irrestrito do browser ao sistema de arquivos (ver D-018).

### D-004 — Backend Java + Spring Boot
**Status:** Aceita  
**Decisão:** usar Java + Spring Boot, em vez de Node.js/Fastify.  
**Motivo:** aproveitar o domínio do desenvolvedor em Java/Spring Boot e aumentar o valor profissional do projeto, sem afirmar que Java seja inerentemente superior para RAG.  
**Consequência:** atualizar exemplos, arquitetura, persistência e roadmap para o ecossistema Spring.

### D-005 — Monólito modular
**Status:** Aceita  
**Decisão:** começar como monólito modular, sem microserviços.  
**Motivo:** reduzir complexidade operacional e acelerar validação do produto.  
**Consequência:** definir limites internos claros e extrair worker apenas se medições justificarem.

### D-006 — Spring AI como detalhe de integração
**Status:** Aceita  
**Decisão:** usar Spring AI onde for útil, atrás de interfaces internas.  
**Motivo:** facilitar troca de provedor e evitar acoplamento do domínio ao framework.  
**Consequência:** contratos de domínio e metadados de execução pertencem à aplicação.

### D-007 — PostgreSQL + pgvector
**Status:** Aceita  
**Decisão:** persistir metadados e embeddings em PostgreSQL/pgvector.  
**Consequência:** validar dimensão dos vetores, índices e qualidade de recuperação em testes reais.

### D-008 — Busca híbrida
**Status:** Aceita  
**Decisão:** combinar busca lexical e vetorial.  
**Motivo:** identificadores e caminhos exatos convivem com perguntas semânticas.

### D-009 — Multilíngue desde o início
**Status:** Aceita  
**Decisão:** indexação textual genérica para múltiplas linguagens; análise AST por adaptadores disponíveis.  
**Consequência:** não prometer semântica completa para todas as linguagens.

### D-010 — Provedores desacoplados
**Status:** Aceita  
**Decisão:** definir portas internas para geração e embeddings.  
**Consequência:** modelos locais podem ser adicionados posteriormente sem redesenhar o domínio.

### D-011 — Evidências verificáveis
**Status:** Aceita  
**Decisão:** respostas devem apontar para arquivos/linhas reais e ter citações validadas no backend.  
**Consequência:** IDs de evidência são fornecidos pelo backend e validados após geração. A validação garante a **existência** do arquivo e das linhas citadas; a verificação de que o trecho **sustenta** a afirmação é medida na avaliação (`08-EVALUATION.md`, "suporte factual"), não pelo validador.

### D-012 — Segurança por padrão
**Status:** Aceita  
**Decisão:** nunca executar código importado no MVP.  
**Consequência:** o produto funciona como leitor/indexador e não como agente executor.

### D-013 — `AGENTS.md` posterior
**Status:** Aceita  
**Decisão:** considerar geração assistida de `AGENTS.md` após o núcleo RAG ser validado.  
**Consequência:** qualquer documento gerado deve conter evidências e passar por revisão humana.

---

## Decisões tomadas nesta revisão (antes em aberto)

### D-014 — Versões exatas (resolve O-001)
**Status:** Aceita (provisória)  
**Contexto:** a escolha precisa de versões suportadas e compatíveis entre si, evitando combinações não validadas. Em jun/2026, fontes indicavam que o Spring AI 2.0 exige Spring Boot 4 e que o suporte aberto do Spring Boot 3.5 terminou em 30/06/2026. Havia divergência entre fontes sobre a data exata do GA do Spring AI 2.0.  
**Opções consideradas:**
1. Spring Boot 3.5 + Spring AI 1.1.x (ecossistema mais maduro, porém já fora do suporte aberto).
2. Spring Boot 4.x + Spring AI 2.0.x (suportado, com quebras de API em relação à 1.x).
**Decisão:** opção 2 — **Java 25 (LTS), Spring Boot 4.x, Spring AI 2.0.x**. Java 21 é aceitável se alguma dependência exigir.  
**Motivo:** projeto novo não deve nascer com migração obrigatória pendente. As quebras de API do Spring AI 2.0 ficam contidas no adaptador, graças à D-006.  
**Consequências:**
- Fixar versões exatas no `pom.xml` e registrar aqui a data da verificação.
- Confirmar a combinação em spring.io e start.spring.io no início da Etapa 0.
- Verificar compatibilidade de PostgreSQL, pgvector e Flyway com a versão escolhida.

**O que levaria à revisão:** incompatibilidade de alguma dependência crítica, bug bloqueante no Spring AI 2.0 ou mudança no calendário de suporte.

#### Atualização de 15/10/2026 — versões verificadas
**Contexto:** a Etapa 0 exige fixar e registrar as versões efetivamente usadas.  
**Opções consideradas:**
1. Adicionar o Spring AI ao `pom.xml` já na Etapa 0.
2. Fixar apenas o que é usado agora e adiar o Spring AI até existir uma porta de provedor.

**Decisão:** opção 2. Versões verificadas em 15/10/2026:
- **Java 25.0.4.1**;
- **Spring Boot 4.1.1**;
- **PostgreSQL 17.11** (imagem `pgvector/pgvector:pg17`);
- **Spring AI 2.0.1** como versão-alvo, que **não entra no `pom.xml` até a Etapa 4**.

**Motivo:** nenhuma etapa anterior à 4 usa o Spring AI; adicioná-lo antes traria dependências sem uso e exigiria revalidar a compatibilidade sem necessidade.  
**Consequências:**
- Na Etapa 4, confirmar que o Spring AI 2.0.1 (ou o patch 2.0.x mais recente) é compatível com o Spring Boot em uso e registrar a verificação aqui.
- A tag `pg17` da imagem é móvel; a versão exata do PostgreSQL pode mudar ao atualizar a imagem.

**O que levaria à revisão:** incompatibilidade do Spring AI 2.0.x com o Spring Boot 4.1 na Etapa 4, ou atualização da imagem que altere o comportamento do pgvector.

### D-015 — Build tool (resolve O-002)
**Status:** Aceita  
**Decisão:** **Maven com wrapper (`mvnw`)**.  
**Motivo:** familiaridade com o ecossistema, documentação do Spring orientada a Maven e ganho de flexibilidade do Gradle sem relevância para este projeto.  
**Consequência:** comandos oficiais no README: `./mvnw verify` e `./mvnw spring-boot:run`.  
**O que levaria à revisão:** necessidade de build multi-módulo complexo ou de caching que o Maven não atenda.

### D-016 — Persistência por agregado (resolve O-003)
**Status:** Aceita  
**Opções consideradas:** JPA, Spring Data JDBC, Spring JDBC (`JdbcClient`), abordagem híbrida.  
**Decisão:**
- **Spring Data JDBC** para CRUD de agregados pequenos (workspace, project, conversation).
- **`JdbcClient` com SQL explícito** para indexação (upsert em lote, substituição de chunks por arquivo) e para toda a recuperação híbrida.
- **JPA não será usado.**

**Motivo:** SQL explícito mantém o ranking observável e testável; evita lazy loading, sessão e dirty checking; JSONB e pgvector ficam mais naturais em SQL.  
**Consequência:** testes de integração com PostgreSQL/pgvector obrigatórios nas consultas de recuperação.  
**O que levaria à revisão:** repetição excessiva de código de mapeamento em agregados complexos.

### D-017 — Estratégia lexical e fusão iniciais (resolve parcialmente O-005)
**Status:** Aceita (provisória)  
**Decisão:**
- **Lexical:** full-text do PostgreSQL com configuração `simple` (sem stemming de idioma), uma coluna adicional com identificadores pré-tokenizados (`getUserById` → `get user by id`, preservando também o original) e `pg_trgm` para caminhos e símbolos.
- **Fusão:** Reciprocal Rank Fusion com k=60 como baseline.

**Motivo:** não exige serviço externo na primeira versão; é simples de observar e comparar.  
**Limitação conhecida:** o full-text do PostgreSQL não implementa BM25.  
**O que levaria à revisão:** o baseline lexical ou a fusão perderem para alternativas no corpus de avaliação (ver O-005 abaixo, que permanece aberto para a comparação).

### D-018 — Seleção de pasta local (resolve O-006)
**Status:** Aceita  
**Contexto:** o navegador não entrega caminhos locais ao backend, e aceitar caminhos arbitrários do cliente é inseguro.  
**Opções consideradas:**
1. Campo de texto com caminho absoluto livre (rejeitada: insegura).
2. Upload da pasta pelo navegador (`webkitdirectory`): funciona, mas é pesado para repositórios grandes.
3. Raízes permitidas configuradas no backend, com navegação por endpoint.

**Decisão:** opção 3.
- O backend roda localmente; apenas o PostgreSQL roda no Docker Compose.
- A configuração define uma ou mais **raízes permitidas** (ex.: `PROJECTS_ROOT`; formato detalhado na D-029).
- A UI navega e escolhe **somente dentro dessas raízes**, por endpoint que lista subdiretórios.
- O cliente envia apenas caminho **relativo** à raiz; o backend canonicaliza e valida.
- Links simbólicos **não são seguidos** por padrão.
- Se o backend for executado em container, a raiz é montada como volume **somente leitura** (`:ro`).

**Consequências:** ZIP continua como alternativa para quem não quiser configurar raízes; testes de segurança cobrem path traversal e links simbólicos.  
**O que levaria à revisão:** necessidade de rodar o backend remotamente ou em ambiente onde a configuração de raízes seja inviável.

### D-019 — Modelos locais adiados (resolve O-007 por ora)
**Status:** Aceita  
**Decisão:** não implementar integração com modelos locais no MVP.  
**Consequência:** as portas de geração e de embeddings devem ter um **provedor fake** nos testes, o que também prepara a integração futura (por exemplo, via Ollama). Modelos locais não devem ser apresentados como funcionalidade existente.

### D-020 — Origem do projeto (complementa D-018)
**Status:** Aceita  
**Contexto:** a D-018 define raízes permitidas no backend, mas o modelo de dados só previa `root_label`, insuficiente para reindexar um projeto de pasta local.  
**Opções consideradas:**
1. Persistir o caminho absoluto da pasta.
2. Persistir a chave da raiz permitida e o caminho relativo a ela.

**Decisão:** opção 2. A tabela `projects` guarda:
- `source_type` (`LOCAL_FOLDER`, `ZIP`);
- `source_root_key`: chave da raiz permitida configurada no backend, **nunca um caminho absoluto**;
- `source_relative_path`: caminho relativo à raiz.

Projetos ZIP não têm caminho; guardam uma referência ao import correspondente em `current_import_job_id` (FK anulável para `import_jobs`, sem relação com snapshots). O projeto ZIP é criado primeiro; a referência é preenchida quando um upload conclui com sucesso. `root_label` foi removido; o nome de exibição fica em `name`.  
**Motivo:** evita persistir e expor caminhos absolutos, mantém a validação na configuração do backend e permite mover a raiz sem migrar dados.  
**Consequências:**
- `05-DATA-MODEL.md` (`projects`, `import_jobs`) e `06-API-CONTRACTS.md` (criação de projeto, raízes locais, importação ZIP) atualizados em 15/10/2026.
- Se a chave de raiz deixar de existir na configuração, o projeto fica indisponível para reindexação, com erro explícito.
- **Duplicidade (15/10/2026):** um workspace não pode ter dois projetos `LOCAL_FOLDER` com o mesmo `source_root_key` + `source_relative_path` normalizado; a criação responde `409 PROJECT_ALREADY_EXISTS`. Garantido por índice único parcial (`05-DATA-MODEL.md`). Nomes iguais são permitidos; projetos ZIP não são verificados.
- **Normalização de `source_relative_path` (revisada em 15/10/2026):** puramente sintática; a criação do projeto **não acessa o sistema de arquivos** nem usa caminho canônico.
  - Unicode normalizado para **NFC**;
  - `/` é o único separador; barras repetidas viram uma só;
  - barras finais e segmentos `.` são removidos; string vazia representa a própria raiz (`""` e `"."` são equivalentes, D-032);
  - `/` inicial (caminho absoluto) ou qualquer segmento `..` é rejeitado com `400 PATH_OUTSIDE_ROOT`; NUL, barra invertida (`\`) e demais caracteres de controle, com `400 VALIDATION_FAILED` (**revisado pela D-032**: antes, `\` era aceito como caractere comum);
  - a comparação é **sensível a maiúsculas**.
- A existência da pasta, o tipo (diretório) e os links simbólicos são verificados na pré-análise da Etapa 2 (D-018), não na criação. A pré-análise também poderá avisar sobre projetos que diferem apenas por maiúsculas/minúsculas e apontam para a mesma pasta em sistemas de arquivos que não diferenciam caixa.
- **`PROJECT_SOURCE_MISMATCH` (409):** operação incompatível com o `source_type` do projeto. Uso atual: upload de ZIP (`POST /projects/{id}/imports/zip`) em projeto `LOCAL_FOLDER`. Não há hoje operação exclusiva de pasta local por projeto; se surgir uma (por exemplo, reindexar a partir da pasta), ela usa o mesmo código para projetos `ZIP`.

**O que levaria à revisão:** necessidade de importar pastas fora das raízes configuradas ou de executar o backend remotamente.

### D-021 — Remoção de projetos e workspaces
**Status:** Aceita  
**Contexto:** o aceite da Etapa 1 exige remoção, e `05-DATA-MODEL.md` §6 deixava a política em aberto.  
**Opções consideradas:**
1. Remoção em cascata também para workspaces.
2. Cascata para projetos; bloqueio da remoção de workspaces não vazios.

**Decisão:** opção 2.
- `DELETE /projects/{id}` remove em cascata imports, índices, snapshots e histórico do projeto (`05-DATA-MODEL.md` §6).
- `DELETE /projects/{id}` retorna **409** com `code` `PROJECT_BUSY` se houver job de importação ou indexação em execução. Cancelamento de job fica como evolução futura.
- `DELETE /workspaces/{id}` retorna **409** com `code` `WORKSPACE_NOT_EMPTY` se houver projetos.

**Motivo:** a remoção de um workspace inteiro apagaria vários índices e históricos de uma vez; exigir a remoção explícita dos projetos evita perda acidental.  
**Consequências:** `06-API-CONTRACTS.md` atualizado com `DELETE /workspaces/{id}` e o `DELETE /projects/{id}` na seção de projetos; testes de integração cobrem a cascata e os dois 409.  
**O que levaria à revisão:** demanda clara por remoção em massa, com confirmação explícita na UI.

### D-022 — Formato de erro
**Status:** Aceita  
**Contexto:** `06-API-CONTRACTS.md` §2 definia um formato próprio; o Spring oferece suporte nativo a RFC 9457.  
**Opções consideradas:**
1. Formato próprio (`code`, `message`, `requestId`, `details`).
2. `ProblemDetail` (RFC 9457) do Spring, com propriedades extras.

**Decisão:** opção 2 — `ProblemDetail` com as propriedades extras `code` e `requestId`. **Substitui o formato próprio de `06-API-CONTRACTS.md` §2.**
- Erros de validação acrescentam a propriedade `errors`, cujos itens contêm **apenas** `field` e `message`.
- O valor rejeitado **nunca** é devolvido, pois pode conter dados sensíveis.
- **`title` e `detail` em pt-BR**, resolvidos por `MessageSource` a partir do `code`, com as chaves `error.<CODE>.title` e `error.<CODE>.detail` (ex.: `error.PROJECT_NOT_FOUND.title`). As mensagens de `errors[].message` também vêm do `MessageSource` em pt-BR. O `code` permanece em inglês (D-025).
- Todo `code` novo exige as duas chaves; um teste deve falhar se alguma estiver ausente.

**Motivo:** padrão aberto, suporte nativo no Spring e menos código de infraestrutura.  
**Consequências:** `06-API-CONTRACTS.md` §2 atualizado; mantêm-se as regras de não expor stack traces, credenciais, conteúdo sensível ou caminhos internos.  
**O que levaria à revisão:** limitação do `ProblemDetail` que impeça representar erros de validação ou a geração de tipos do frontend.

#### Atualização de 15/10/2026 — códigos genéricos e `requestId`
**Contexto:** o planejamento da Etapa 1 mostrou erros do próprio framework (JSON ilegível, rota inexistente etc.) sem `code` definido.  
**Opções consideradas:**
1. Agrupar tudo em `VALIDATION_FAILED` e `INTERNAL_ERROR`.
2. Um código genérico por categoria de erro do framework.

**Decisão:** opção 2.
- Códigos genéricos: `MALFORMED_REQUEST` (400), `RESOURCE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `UNSUPPORTED_MEDIA_TYPE` (415) e `INTERNAL_ERROR` (500).
- `INTERNAL_ERROR` nunca devolve detalhes da exceção; só o `requestId` permite a correlação com o log.
- Identificador malformado no caminho responde `400 VALIDATION_FAILED`, com `field` igual ao nome do parâmetro.
- O `requestId` (UUID v7) é sempre gerado pelo backend, devolvido no cabeçalho `X-Request-Id` de toda resposta e incluído no MDC dos logs. Um `X-Request-Id` enviado pelo cliente é ignorado, para evitar injeção em log.

**Motivo:** códigos distintos permitem ao frontend tratar cada caso sem interpretar texto; gerar o ID no servidor garante formato e unicidade.  
**Consequências:** `06-API-CONTRACTS.md` §1 e §2 atualizados. Cada código genérico também exige as chaves `error.<CODE>.title` e `error.<CODE>.detail`.  
**O que levaria à revisão:** necessidade de propagar um ID de rastreamento vindo de um proxy ou do frontend.

### D-023 — Contrato OpenAPI
**Status:** Aceita  
**Contexto:** a Etapa 0 exige publicar o OpenAPI, e o frontend deve usar tipos derivados do contrato.  
**Opções consideradas:**
1. Contract-first (escrever o `openapi.json` e gerar código).
2. Code-first com springdoc, versionando o contrato gerado.

**Decisão:** opção 2 — code-first com **springdoc**. O `openapi.json` gerado é versionado no repositório, e um teste falha se ele mudar sem ser commitado. Os tipos do frontend serão gerados a partir dele.  
**Motivo:** uma única fonte de verdade (o código), sem perder a revisão de mudanças de contrato em diff.  
**Consequências:** mudanças de API exigem regenerar e commitar o `openapi.json`; mudanças incompatíveis seguem o versionamento de `06-API-CONTRACTS.md` §11.  
**O que levaria à revisão:** divergência recorrente entre anotações e comportamento real, ou incompatibilidade do springdoc com o Spring Boot em uso.

### D-024 — Geração de IDs
**Status:** Aceita  
**Contexto:** as entidades usam UUID; o PostgreSQL 17 não tem `uuidv7()` nativo.  
**Opções consideradas:**
1. UUID v4 (`gen_random_uuid()` no banco).
2. UUID v7 gerado na aplicação.

**Decisão:** opção 2 — UUID v7 gerado na aplicação por um `IdGenerator` no módulo `shared`.  
**Motivo:** IDs ordenáveis por tempo melhoram a localidade dos índices B-tree e permitem conhecer o ID antes do `INSERT`.  
**Consequências:** implementação própria, sem dependência externa (`UuidV7Generator`, aprovada em 15/10/2026): 48 bits de timestamp em ms + bits aleatórios via `SecureRandom`. IDs gerados no mesmo milissegundo não têm ordem estrita, o que não afeta a localidade dos índices; um contador monotônico pode ser adicionado se necessário. Os testes podem injetar um `IdGenerator` determinístico.  
**O que levaria à revisão:** migração para PostgreSQL com `uuidv7()` nativo ou disponibilidade de UUID v7 no JDK.

### D-025 — Idioma
**Status:** Aceita  
**Decisão:**
- Código-fonte e `code` de erro: **inglês** (ex.: `PROJECT_NOT_FOUND`).
- Mensagens ao usuário (API e UI) e documentação: **pt-BR**.
- Nos erros da API, `title`, `detail` e `errors[].message` são mensagens ao usuário (pt-BR, via `MessageSource` com chaves `error.<CODE>.title` e `error.<CODE>.detail`); `code` é identificador estável (inglês). Ver D-022.

**Motivo:** código e identificadores estáveis em inglês seguem a convenção do ecossistema; o público inicial lê em português.  
**Consequências:** o exemplo de `06-API-CONTRACTS.md` §2 (`code` em inglês, mensagem em pt-BR) já segue a regra; o `CLAUDE.md` reflete a mesma política.  
**O que levaria à revisão:** necessidade de internacionalizar a interface.

### D-026 — Momento do scaffold do frontend
**Status:** Aceita  
**Contexto:** a Etapa 0 previa criar o monorepo com frontend e backend, mas nenhuma etapa antes da 7 usa o frontend.  
**Opções consideradas:**
1. Criar `apps/web` na Etapa 0.
2. Adiar o scaffold de `apps/web` para a Etapa 7.

**Decisão:** opção 2.  
**Motivo:** evita manter dependências e configuração de frontend sem uso; o contrato OpenAPI (D-023) garante que os tipos poderão ser gerados quando o frontend for criado.  
**Consequências:** texto da Etapa 0 em `09-IMPLEMENTATION-ROADMAP.md` ajustado; a biblioteca de UI e o gerenciamento de estado continuam a decidir na Etapa 7.  
**O que levaria à revisão:** necessidade de validar fluxos de UX com protótipo funcional antes da Etapa 7.

### D-027 — Escopo do MVP (resolve O-013)
**Status:** Aceita  
**Contexto:** resumo executivo, PRD e roadmap divergiam sobre adaptadores AST, comparação entre snapshots e o critério de reindexação.  
**Opções consideradas:**
1. Trazer AST e comparação entre snapshots para o MVP, adiantando etapas do roadmap.
2. Manter o roadmap e ajustar o resumo executivo e o PRD.

**Decisão:** opção 2.
- Adaptadores de análise estrutural (AST) ficam **fora do MVP** (Etapa 9); o MVP usa indexação textual genérica.
- Comparação entre snapshots fica **fora do MVP** (Etapa 9).
- O critério de reindexação do PRD passa a ser "somente arquivos novos ou alterados; arquivos removidos ou excluídos saem do índice ativo", já que não há grafo de dependências no MVP.

**Motivo:** o roadmap prioriza validar o núcleo RAG antes de recursos de análise; manter os três documentos coerentes evita promessas que o MVP não cumpre.  
**Consequências:** `RESUMO-EXECUTIVO.md` e `01-PRD.md` atualizados em 15/10/2026.  
**O que levaria à revisão:** resultados da avaliação mostrarem que o chunking genérico é insuficiente para as linguagens prioritárias.

### D-028 — Diretório de dados das importações
**Status:** Aceita  
**Contexto:** a importação ZIP precisa extrair arquivos em disco com segurança, sem misturá-los ao repositório nem às raízes permitidas.  
**Opções consideradas:**
1. Diretório temporário do sistema operacional.
2. Diretório de dados da aplicação, configurável.

**Decisão:** opção 2.
- Variável `DATA_DIR`, configurável via `.env.example`, com padrão `~/.codebase-copilot`. Um `~` inicial, no padrão ou no valor configurado, é expandido pela aplicação a partir de `user.home`.
- Cada import usa uma pasta própria: `DATA_DIR/imports/<importJobId>`.
- A pasta do job é apagada quando o job conclui ou falha.
- `DATA_DIR` deve ficar **fora das raízes permitidas** (D-018) e **fora do repositório do Codebase Copilot**; a aplicação recusa iniciar se essa condição não for atendida.
- O caminho é derivado do ID e não é persistido (`05-DATA-MODEL.md`, `import_jobs`).
- **Limites de upload (15/10/2026):** tamanho máximo do ZIP em `app.import.max-zip-bytes`, padrão **100 MB** (hipótese a ajustar com medições). Os limites do multipart do Spring (`spring.servlet.multipart.max-file-size` e `max-request-size`) são configurados de forma coerente com esse valor; excedido qualquer um dos dois, a resposta é `413 UPLOAD_TOO_LARGE`.
- **Concorrência:** no máximo um import em andamento (`PENDING` ou `EXTRACTING`) por projeto; um novo upload nesse estado responde `409 PROJECT_BUSY`.

**Motivo:** isola conteúdo não confiável em local previsível e fácil de limpar; evita que arquivos extraídos sejam importados de novo como pasta local ou commitados por engano.  
**Consequências:**
- Testes de segurança cobrem a limpeza após falhas e a escrita restrita à pasta do job.
- A remoção de um projeto apaga pastas remanescentes de seus imports (D-021).
- O conteúdo extraído precisa ser consumido (pré-análise e indexação) antes de o job ser encerrado; a fronteira entre o job de importação e o de indexação será definida na Etapa 2, junto com a O-009.

**O que levaria à revisão:** necessidade de reindexar projetos ZIP sem novo upload, o que exigiria reter o conteúdo extraído.

### D-029 — Configuração das raízes permitidas (complementa D-018)
**Status:** Aceita  
**Contexto:** a D-018 define raízes permitidas, mas não como configurá-las; a API (`GET /local-roots`) precisa de `key` e `label`, e a criação de projeto `LOCAL_FOLDER` na Etapa 1 depende delas.  
**Opções consideradas:**
1. Uma única variável `PROJECTS_ROOT`.
2. Lista de raízes em propriedades do Spring, com `PROJECTS_ROOT` como atalho para o caso comum.

**Decisão:** opção 2.
- Propriedade `app.local-roots[n]` com `key`, `label` e `path`. O `path` é absoluto e **nunca é exposto pela API**.
- Caso comum, via `.env.example`: `PROJECTS_ROOT` (e `PROJECTS_ROOT_LABEL`, opcional, padrão "Projetos") gera a raiz de key `default`. Se `PROJECTS_ROOT` estiver vazio, essa raiz não é criada.
- Várias raízes são configuradas por propriedades do Spring, definindo a lista completa (por exemplo, em `apps/backend/config/application.yml`, carregado automaticamente a partir do diretório de execução e não versionado). Como o Spring substitui listas por inteiro, a raiz `default` deve ser repetida ali se for desejada.
- Validação na inicialização (a aplicação não inicia se falhar):
  - `path` absoluto, existente, diretório e legível;
  - `key` única, no formato `[a-z0-9-]+`;
  - `label` opcional; ausente, usa a `key`.
- Sem nenhuma raiz configurada:
  - `GET /local-roots` devolve lista vazia;
  - a criação de projeto `LOCAL_FOLDER` responde `409 LOCAL_ROOTS_NOT_CONFIGURED`;
  - o fluxo ZIP continua funcionando.

**Motivo:** mantém o caso comum em uma variável e permite várias raízes sem inventar formato próprio; validar na inicialização evita falhas tardias em uso.  
**Consequências:**
- Na Etapa 1: mapear `PROJECTS_ROOT`/`PROJECTS_ROOT_LABEL` em `application.yml` e implementar a validação. **Implementado (15/10/2026):** o atalho vira as propriedades `app.projects-root` e `app.projects-root-label`, separadas da lista. Se `app.local-roots` tiver itens, ela substitui o atalho por inteiro; assim, qualquer item da lista com `path` vazio impede a inicialização, sem ambiguidade sobre a origem. Os testes fixam `spring.config.location=optional:classpath:/` e `app.projects-root=` vazio, além das próprias raízes (`src/test/resources/local-roots`).
- O arquivo local `apps/backend/config/application.yml` é ignorado pelo Git; o formato é documentado no arquivo versionado `apps/backend/config/application.yml.example`, que o Spring não carrega.
- **Testes declaram suas próprias raízes** em `app.local-roots` (por exemplo, diretórios temporários registrados como propriedades do teste). Como o Spring carrega `config/application.yml` a partir do diretório de execução (`apps/backend`), a configuração local de quem roda os testes, ou a ausência dela, não pode influenciar o resultado.
- `DATA_DIR` não pode estar dentro de nenhuma raiz (D-028).
- Uma raiz removida da configuração torna indisponíveis os projetos que a usam (D-020).

**O que levaria à revisão:** necessidade de alterar raízes em tempo de execução, pela UI.

### D-030 — Paginação
**Status:** Aceita  
**Contexto:** as listagens de workspaces e projetos precisam de um formato único antes da Etapa 1.  
**Opções consideradas:**
1. Paginação por página (offset).
2. Paginação por cursor.

**Decisão:** opção 1.
- Parâmetros `page` (base 0, padrão 0) e `size` (padrão 20, máximo 100). Valores fora da faixa respondem `400 VALIDATION_FAILED`; página além da última devolve `items` vazio.
- Resposta: `{ "items", "page", "size", "totalItems", "totalPages" }`.
- Ordenação fixa por `createdAt` decrescente nas listas de workspaces e projetos, com `id` decrescente como desempate (UUID v7, D-024).

**Motivo:** volumes pequenos nessas listas; offset é simples de implementar, testar e exibir na UI.  
**Consequências:** listagens grandes (arquivos, chunks) serão reavaliadas na Etapa 2 ou 3, podendo adotar cursor.  
**O que levaria à revisão:** listas com volume que torne o `COUNT` ou o offset caros.

### D-031 — Fronteira workspace/project e raízes locais em `shared`
**Status:** Aceita  
**Contexto:** `project` precisa de `workspace` (o workspace deve existir), mas `workspace` precisa saber quantos projetos tem (`projectCount` em `GET /workspaces` e `409 WORKSPACE_NOT_EMPTY`, D-021). Uma dependência nos dois sentidos cria ciclo entre módulos. Além disso, as raízes permitidas (D-029) são usadas por `project` (validação de `rootKey`) e por `importjob` (navegação, Etapa 2).  
**Opções consideradas:**
1. A consulta de `workspace` faz `JOIN` direto na tabela `projects`.
2. Uma porta em `workspace`, implementada por `project` (inversão de dependência).

E, para as raízes: no módulo `project`, no `importjob` ou em `shared`.

**Decisão:**
- Opção 2. `workspace` define a porta (ex.: `WorkspaceProjectCounter`, com contagem individual e em lote), e `project` a implementa. A dependência tem **sentido único**: `project` depende de `workspace`, nunca o contrário.
- A FK `projects.workspace_id` usa `ON DELETE RESTRICT` como rede de segurança: se um projeto for criado em concorrência entre a verificação e o `DELETE` do workspace, a violação da FK é traduzida para `409 WORKSPACE_NOT_EMPTY`, nunca 500.
- A configuração e a consulta das raízes permitidas ficam em `shared.localroots`.
- Regras de arquitetura com **ArchUnit** (dependência de teste nova): `shared` não depende de nenhum módulo de domínio (`workspace`, `project`, `importjob`, `indexing`, `codeanalysis`, `retrieval`, `answering`, `providers`, `history`), e `workspace` não depende de `project`.

**Motivo:** mantém cada tabela sob a responsabilidade de um módulo e torna as fronteiras verificáveis por teste, e não só por convenção.  
**Consequências:** a contagem de projetos na listagem de workspaces usa uma consulta em lote pela porta (sem N+1). Novos módulos entram na lista da regra do ArchUnit.  
**O que levaria à revisão:** adoção do Spring Modulith ou necessidade de consultas que cruzem módulos com frequência.

### D-032 — Regras de nome e precedência de erros na criação de projeto
**Status:** Aceita  
**Contexto:** os documentos não definiam limites para `name` nem a ordem das verificações quando uma requisição tem mais de um problema.  
**Opções consideradas:**
1. Deixar a ordem a cargo da implementação.
2. Fixar regras de nome e uma ordem de verificação no contrato.

**Decisão:** opção 2.
- **Nome do workspace:** opcional. Ausente, `null` ou vazio após o `trim`, assume "Meus projetos".
- **Nome do projeto:** obrigatório; vazio após o `trim` responde `400 VALIDATION_FAILED`.
- **Ambos:** `trim` e normalização NFC; depois disso, no máximo 100 caracteres (contados em code points), sem caracteres de controle e sem unicidade.
- **`relativePath`:** `""` e `"."` representam a própria raiz e são gravados como `""`. NUL, barra invertida (`\`) e caracteres de controle respondem `400 VALIDATION_FAILED` (revisa a D-020, que aceitava `\` como caractere comum). Caminho absoluto ou segmento `..` responde `400 PATH_OUTSIDE_ROOT`.
- **Ordem das verificações em `POST /workspaces/{id}/projects`:**
  1. `400 VALIDATION_FAILED` (inclui caracteres inválidos em `relativePath`);
  2. `404 WORKSPACE_NOT_FOUND`;
  3. `409 LOCAL_ROOTS_NOT_CONFIGURED`;
  4. `404 ROOT_NOT_FOUND`;
  5. `400 PATH_OUTSIDE_ROOT`;
  6. `409 PROJECT_ALREADY_EXISTS`.

  Para `ZIP`, os passos 3 a 6 não se aplicam.

**Motivo:** respostas determinísticas facilitam testes e o tratamento no frontend. Rejeitar `\` evita ambiguidade entre sistemas operacionais (no Windows, `\` é separador), e o padrão de nome do workspace permite criá-lo com um clique na UI.  
**Consequências:** `05-DATA-MODEL.md` e `06-API-CONTRACTS.md` (§3 e §5) atualizados em 15/10/2026; a migração aplica o limite de nome também por `CHECK`.  
**O que levaria à revisão:** necessidade de nomes mais longos ou de unicidade de nomes por workspace.

---

## Decisões em aberto

### O-004 — Provedor inicial (chat e embeddings)
**Status:** A validar (bake-off na Etapa 5, com o corpus de avaliação)  
**Restrições já aceitas:**
- Escolher **um** par embedding/chat por instalação; trocar de modelo implica reindexar.
- Preferir modelos de embedding com **até 2.000 dimensões**, compatível com índice HNSW sobre `vector` no pgvector (`halfvec` permite mais); a dimensão é fixada por migração.
- Provedor de embeddings e de chat podem ser diferentes (a Anthropic, por exemplo, não oferece modelo de embeddings próprio), o que justifica as duas portas separadas.

**Critérios de comparação:** recall@k e MRR no corpus, qualidade em código, custo por 1M de tokens, latência, limites de taxa e política de retenção de dados do provedor.  
**Prazo:** decidir antes de concluir a Etapa 4, mesmo que provisoriamente, para fixar a dimensão do vetor na migração.

### O-005 — Comparação final de estratégia lexical e fusão
**Status:** A validar  
**Contexto:** D-017 define o ponto de partida. Falta compará-lo com alternativas.  
**A comparar:** lexical puro, vetorial puro, híbrido com RRF e variações (valor de k, pesos), tratamento de identificadores, e, apenas se o lexical se mostrar fraco, índice auxiliar com BM25.  
**Evidência para decidir:** resultados no corpus da Etapa 5 (recall@k, MRR, latência).

### O-008 — Modelo de snapshot, arquivos e conteúdo
**Status:** A decidir antes da Etapa 3 (bloqueia apenas a Etapa 3)  
**Contexto:** `chunks` possui um único `snapshot_id`, mas a reindexação incremental reaproveita chunks de arquivos inalterados. A unicidade `(project_id, relative_path)` em `files` não representa histórico por snapshot. Além disso, o F-06 exige abrir o arquivo no estado do snapshot, e o modelo atual só guarda chunks (com sobreposição).  
**Opções:**
1. Copiar chunks e embeddings a cada snapshot (simples, mas caro e contraditório com o cache por hash).
2. Tabelas de ligação `snapshot_files` e `snapshot_chunks`, com chunks e embeddings imutáveis e compartilhados por hash.

E, para o conteúdo: guardar o conteúdo completo deduplicado por hash (`file_contents`) ou reconstruí-lo a partir dos chunks.  
**Recomendação:** opção 2 para chunks e `file_contents` deduplicado para o visualizador.  
**Consequência:** exige atualizar `05-DATA-MODEL.md` e a política de retenção (limpeza de chunks sem snapshot associado).  
**Impacto na Etapa 1 (15/10/2026):** nenhum. A coluna `projects.active_snapshot_id` e a FK para `snapshots` entram na migração da Etapa 3, não na da Etapa 1.

### O-009 — Execução assíncrona dos jobs
**Status:** A decidir antes da Etapa 3  
**Contexto:** `@Async` simples perde jobs se a aplicação cair, o que conflita com o requisito de recuperação após falhas.  
**Recomendação:** fila em tabela (`index_jobs`) com `SELECT … FOR UPDATE SKIP LOCKED`, retomada de jobs interrompidos na inicialização e etapas idempotentes. Sem infraestrutura nova.  
**Revisão:** separar um worker apenas se medições de recurso ou confiabilidade justificarem (D-005).

### O-010 — Parâmetros iniciais de chunking
**Status:** A validar  
**Contexto:** tamanho, sobreposição e limites de linhas são hipóteses.  
**Plano:** definir valores iniciais conservadores na Etapa 3 e ajustá-los no corpus, alterando uma variável por vez (`08-EVALUATION.md`, seção 8).

### O-011 — Corpus de avaliação
**Status:** A decidir antes da Etapa 5  
**Questões:** quais repositórios (licença permissiva, tamanhos e linguagens variados), quem anota arquivos/linhas esperados e perguntas sem resposta, e como versionar o corpus.  
**Sugestão:** projetos próprios mais um ou dois repositórios open source conhecidos.

### O-012 — Detecção de segredos
**Status:** A decidir antes da Etapa 2  
**Questão:** usar biblioteca existente de detecção de segredos ou apenas padrões de exclusão no MVP.  
**Restrição:** a detecção é defesa adicional, nunca garantia; a interface não deve prometer que todos os segredos serão detectados.

---

## Processo de mudança

Para cada decisão relevante, registrar:
- contexto;
- opções consideradas;
- decisão;
- motivo;
- consequências;
- evidência/experimento que poderia levar à revisão.
