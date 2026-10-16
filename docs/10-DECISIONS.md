# Registro de decisões — Codebase Copilot

Este documento registra decisões aceitas e itens que ainda precisam de validação. Atualizar sempre que uma escolha técnica mudar.

**Última atualização:** 16/10/2026.

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

#### Atualização de 16/10/2026 — Spring AI e pgvector na Etapa 4
**Contexto:** a Etapa 4 adiciona o Spring AI (D-047) e precisa confirmar a compatibilidade prevista acima.  
**Verificação (16/10/2026):**
- **Spring AI 2.0.1** é o patch 2.0.x mais recente no Maven Central (há também 2.1.0-M1, que é marco e não entra).
- start.spring.io (`/actuator/info`, `bom-ranges`) indica o Spring AI 2.0.1 para `Spring Boot >=4.0.0 and <4.2.0-M1`; o 2.1.0-M1 exige Spring Boot 4.2.
- O POM do `spring-ai-autoconfigure-model-openai` 2.0.1 é construído contra o **Spring Boot 4.1.1**, a mesma versão do projeto.
- O anúncio oficial do GA (spring.io, 12/06/2026) diz que o Spring AI 2.0 foi feito para Spring Boot 4.0/4.1 e Spring Framework 7.0, e que o módulo OpenAI passou a ter uma única variante, baseada no SDK oficial.
- **pgvector 0.8.7** na imagem `pgvector/pgvector:pg17` (imagem de 01/10/2026, PostgreSQL 17.11), conferido com `pg_available_extensions`.

**Decisão:** BOM `spring-ai-bom` 2.0.1 e apenas o módulo **`spring-ai-openai`** (core), sem o `spring-ai-starter-model-openai`. O módulo traz `openai-java-core` 4.49.0 e OkHttp 4.12.0; nenhum jar do Spring AI incluído registra autoconfiguração.  
**Motivo:** o starter traria a autoconfiguração do OpenAI, do `ChatClient` e da memória de chat, com propriedades `spring.ai.*` paralelas às `app.embeddings.*`. Sem ele, o módulo `providers` monta o modelo de embeddings só quando `app.embeddings.provider=openai` (D-006), e não há bean do Spring AI com `none` ou `fake`.  
**Consequências:**
- O SDK da OpenAI tem repetição própria; o adaptador da F1 a desliga (D-047), para que a política da D-051 seja a única.
- A troca para o patch 2.0.x seguinte exige repetir esta verificação.

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

#### Atualização de 16/10/2026 — retenção e fronteira com a indexação
**Contexto:** a Etapa 2 precisava definir a fronteira entre o import e a indexação, que só existe na Etapa 3.
**Decisão:** ver D-033. Em resumo:
- a pasta do **import atual** de um projeto ZIP é retida (`DATA_DIR/imports/<id>/source/`) e serve de origem para o inventário e a indexação;
- a pasta é apagada quando o job termina `REJECTED`/`FAILED`, quando um import mais novo conclui e na remoção do projeto;
- a coluna `error_message_sanitized` não existe: o job guarda só `error_code`, e a mensagem pt-BR vem do `MessageSource` (`import.error.<CODE>`), nunca do conteúdo importado.

**Consequência:** o gatilho de revisão acima (reindexar ZIP sem novo upload) passa a ser atendido.

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
- **Etapa 2 (16/10/2026):** o mesmo padrão vale entre `project` e `importjob`. `project` define a porta `ProjectImports` (import atual, job ativo para `PROJECT_BUSY` e limpeza das pastas na remoção), implementada por `importjob`. Regras novas no ArchUnit: `project` não depende de `importjob`, e `codeanalysis` não depende de `importjob`.  
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

### D-033 — Retenção do conteúdo extraído e fronteira import/indexação (revisa D-028)
**Status:** Aceita (16/10/2026)  
**Contexto:** a D-028 apagava a pasta do job ao concluir, mas a indexação (Etapa 3) precisa ler o conteúdo, e a fronteira entre os dois jobs estava pendente.  
**Opções consideradas:**
1. O import só conclui depois da indexação (inviável antes da Etapa 3).
2. O import conclui após extração e validação, e a árvore extraída do import atual é retida.

**Decisão:** opção 2.
- `DATA_DIR/imports/<id>/source/` é a origem do projeto ZIP enquanto `<id>` for o `current_import_job_id`; inventário e indexação a leem como leem uma pasta local.
- O `upload.zip` é apagado depois da extração.
- A pasta é apagada quando o job termina `REJECTED` ou `FAILED`, quando um import mais novo conclui (a do anterior) e na remoção do projeto, sempre depois do commit.
- Na inicialização, pastas em `DATA_DIR/imports` que não pertencem a um import atual são removidas.

**Motivo:** permite inventário e reindexação sem novo upload, com isolamento e limpeza previsíveis.  
**Consequências:** o disco usado é limitado por `app.import.max-uncompressed-bytes` por projeto ZIP; `05-DATA-MODEL.md` §6 atualizado.  
**O que levaria à revisão:** volume em disco que torne inviável reter uma cópia por projeto ZIP.

### D-034 — Execução do import na Etapa 2 (não resolve a O-009)
**Status:** Aceita (16/10/2026)  
**Contexto:** o upload responde `202` e a extração roda depois; a O-009 (fila de jobs) só precisa ser decidida para a indexação.  
**Opções consideradas:**
1. Antecipar a fila em tabela da O-009.
2. Executor em processo, com recuperação na inicialização.

**Decisão:** opção 2.
- Pool fixo de `app.import.max-concurrent` threads (padrão 2), criado pelo módulo `importjob`; o job é submetido **depois do commit** da transação que o cria.
- Na inicialização, antes de o servidor web aceitar requisições, jobs `PENDING`/`EXTRACTING` viram `FAILED` com `IMPORT_INTERRUPTED`, e suas pastas são apagadas. Não há retomada: basta reenviar o arquivo.
- Transições de estado são `UPDATE` condicionados ao estado atual, para que um job termine uma única vez.

**Motivo:** a extração é curta e recomeçar é barato; não há infraestrutura nova.  
**Consequências:** a O-009 continua aberta para os jobs de indexação da Etapa 3.  
**O que levaria à revisão:** imports longos o bastante para justificar retomada, ou a fila da O-009 cobrir também os imports.

### D-035 — Inventário calculado sob demanda
**Status:** Aceita (16/10/2026)  
**Contexto:** a pré-análise (F-02) e o inventário (F-04) precisam de contagens antes da tabela `files`, que depende da O-008.  
**Opções consideradas:**
1. Persistir o inventário já na Etapa 2.
2. Calcular a cada chamada, sem persistir.

**Decisão:** opção 2. `GET /projects/{projectId}/inventory` faz a varredura segura a cada chamada e devolve agregados (por linguagem, por diretório de primeiro nível, por motivo de exclusão, com até 20 exemplos) e avisos.
- Limites `app.inventory.max-entries` (padrão 200.000) e `max-depth` (64); ao atingir o de entradas, a resposta traz `truncated: true` e o aviso `SCAN_LIMIT_REACHED`.
- Diretórios excluídos não são percorridos; seus arquivos não entram nas contagens.

**Motivo:** não antecipa o modelo de snapshots.  
**Consequências:** a persistência vem com os snapshots na Etapa 3; o custo da varredura deve ser medido na Etapa 8.  
**O que levaria à revisão:** latência inaceitável em repositórios grandes.

#### Atualização de 16/10/2026 — persistência por snapshot
O `GET /inventory` continua sob demanda, como pré-análise. A partir da Etapa 3, cada snapshot persiste o agregado da varredura que o originou (`snapshots.inventory`) e os arquivos incluídos (`snapshot_files`), conforme a D-039.

### D-036 — Regras de exclusão (resolve O-012)
**Status:** Aceita (16/10/2026)  
**Contexto:** a Etapa 2 exige exclusões padrão e customizadas, e a O-012 pedia decidir a detecção de segredos antes dela.  
**Opções consideradas (segredos):** biblioteca de detecção por conteúdo ou apenas padrões de nome no MVP.  
**Decisão:**
- **Segredos:** apenas padrões de nome no MVP (regras `SENSITIVE`). Detecção por conteúdo fica para a Etapa 8, com segredos sintéticos no corpus. A API e a UI dizem que arquivos foram excluídos por padrão, nunca que segredos foram detectados.
- **Regras padrão** com `id` estável e categoria (`SENSITIVE`, `VCS`, `DEPENDENCIES`, `BUILD_OUTPUT`, `IDE_CACHE`, `GENERATED`, `MEDIA_BINARY`). `SENSITIVE`, `VCS` e `MEDIA_BINARY` não podem ser desativadas; as demais podem, por `id`.
- **Customização por projeto** (`project_settings`): `excludePatterns` (até 100, somados às regras padrão), `disabledDefaultRules`, `maxFileBytes` e `maxProjectBytes`. Sem padrões de inclusão: desativar uma regra cobre o caso comum.
- **Sintaxe de padrões, independente do SO:** relativos à raiz do projeto, separador `/`; `*` (sem `/`), `**` (zero ou mais segmentos) e `?`; sem `/` (exceto o final) casa o nome em qualquer profundidade; com `/` é ancorado na raiz; `/` final restringe a diretórios, que não são percorridos; sensível a maiúsculas. `!` inicial, `[`, `]`, `\`, caracteres de controle e `..` são inválidos.
- Cada `PUT` incrementa `settings_version`, exibida no inventário.

**Motivo:** padrões de nome são previsíveis e explicáveis ao usuário; a detecção por conteúdo é defesa adicional, nunca garantia (O-012).  
**Consequências:** `05-DATA-MODEL.md` (`project_settings`) e `06-API-CONTRACTS.md` (§5) atualizados.  
**O que levaria à revisão:** a avaliação mostrar segredos indexados que padrões de nome não cobrem, ou demanda por whitelist.

### D-037 — Links, arquivos especiais e limites de varredura
**Status:** Aceita (16/10/2026)  
**Contexto:** a D-018 diz que links não são seguidos, mas não definia como tratá-los na navegação, na varredura e no ZIP.  
**Decisão:**
- **Links simbólicos** nunca são seguidos: não aparecem na navegação (um caminho que passa por link responde `404 DIRECTORY_NOT_FOUND`), aparecem na varredura como `SYMLINK`, e um projeto cujo caminho passa por link responde `409 SOURCE_IS_SYMLINK` no inventário.
- **Arquivos especiais** (FIFO, socket, dispositivo) são `SPECIAL_FILE` e nunca são abertos.
- Atributos são lidos sem seguir links, a amostra é aberta com `NOFOLLOW_LINKS`, e o caminho real resolvido precisa começar pelo caminho real da raiz.
- O extrator de ZIP nunca cria links: entradas de link são gravadas como arquivo comum.
- **Limitações conhecidas:** links físicos e pontos de montagem dentro da raiz não são detectados, e uma troca concorrente de diretório por link (TOCTOU) não é totalmente evitável sem `openat`. O modelo de ameaça é conteúdo malicioso, não um atacante concorrente no disco local.

**Consequências:** `07-SECURITY-PRIVACY-COSTS.md` §3 e §4 atualizados.  
**O que levaria à revisão:** execução do backend em ambiente compartilhado, onde outro usuário possa alterar a raiz durante a varredura.

### D-038 — Ajustes de contrato da Etapa 2
**Status:** Aceita (16/10/2026)  
**Decisão:**
- `GET /local-roots/{rootKey}/directories` devolve um objeto `{ path, directories, truncated }`, para não cortar a lista em silêncio (`app.browse.max-directories`, padrão 1000). Nomes vêm em NFC; diretórios cujo nome teria `\` ou caracteres de controle são omitidos.
- Configurações por `GET` e `PUT /projects/{projectId}/settings` (substituição completa), em vez de `PATCH`.
- `GET /projects/{projectId}/imports` (paginado) permite à UI reencontrar um import em andamento.
- Códigos novos: `DIRECTORY_NOT_FOUND`, `DIRECTORY_NOT_READABLE`, `PROJECT_BUSY`, `PROJECT_SOURCE_MISMATCH`, `UPLOAD_TOO_LARGE`, `IMPORT_NOT_FOUND`, `SOURCE_ROOT_NOT_CONFIGURED`, `SOURCE_DIRECTORY_NOT_FOUND`, `SOURCE_IS_SYMLINK`, `SOURCE_NOT_READABLE` e `SOURCE_NOT_IMPORTED`.

**Motivo:** respostas sem truncamento silencioso e um código por situação, para o frontend não interpretar texto (D-022).  
**Consequências:** `06-API-CONTRACTS.md` §4, §5 e §6 atualizados.

### D-039 — Modelo de snapshots, conteúdo e chunks (resolve O-008)
**Status:** Aceita (16/10/2026)  
**Contexto:** com `chunks.snapshot_id`, chunks de arquivos inalterados não seriam reaproveitados; `files (project_id, relative_path)` não guarda histórico; e o visualizador (F-06) precisa do arquivo como estava no snapshot.  
**Opções consideradas:**
1. Copiar chunks e embeddings a cada snapshot.
2. Tabelas de ligação `snapshot_files` e `snapshot_chunks`, com chunks compartilhados (recomendação original da O-008).
3. Chunks ligados à versão do conteúdo, e o snapshot apontando caminho → hash.

**Decisão:** opção 3.
- **`file_contents`** `(project_id, content_hash)`: conteúdo decodificado, deduplicado **por projeto**; atende o visualizador.
- **`snapshot_files`** `(snapshot_id, relative_path)`: estado de cada arquivo incluído no snapshot (hash, tamanho, linguagem, estado e motivo). Substitui a tabela `files`.
- **`chunks`** por `(project_id, content_hash, chunking_version, ordinal)`, imutáveis. Arquivo inalterado reaproveita chunks sem escrita; a busca por snapshot faz `JOIN` de `snapshot_files` com `chunks` pelo hash e pela `chunking_version` do snapshot.
- **Mesmo conteúdo em caminhos diferentes:** a recuperação devolve o chunk uma vez e resolve os caminhos por `snapshot_files`. A citação usa o primeiro caminho em ordem lexicográfica e lista os demais (`otherPaths`), com as mesmas linhas.
- **Inventário persistido** por snapshot (`snapshots.inventory`, JSONB); arquivos excluídos não viram linhas. O `GET /inventory` continua sob demanda (D-035).
- **Limpeza de conteúdo e chunks sem snapshot**, ao fim de cada job, depois da poda (D-044):
  1. apagam-se os chunks cuja combinação `(content_hash, chunking_version)` não é usada por nenhum snapshot não podado, o que também remove versões antigas de chunking;
  2. apagam-se os `file_contents` sem referência em `snapshot_files`, com os chunks restantes em cascata.

  Snapshots `BUILDING` não são podados, então um job em andamento protege o próprio conteúdo.

**Motivo:** reaproveitamento sem tabelas de ligação por chunk, cascata simples na remoção do projeto e conteúdo disponível para o visualizador.  
**Consequências:**
- `05-DATA-MODEL.md` atualizado; a tabela `files` sai do modelo.
- O texto fica guardado duas vezes (conteúdo e chunks com sobreposição), limitado por `max-project-bytes`.
- O cache global de embeddings por `chunk_hash` (Etapa 4) não muda.
- **Risco para a Etapa 4:** busca vetorial com filtro por snapshot (`JOIN`) em índice HNSW pode devolver menos resultados que o pedido, porque o filtro é aplicado depois da busca aproximada. Avaliar no corpus a varredura iterativa do pgvector (`hnsw.iterative_scan`) ou alternativa (índice parcial, busca exata em projetos pequenos).

**O que levaria à revisão:** volume de armazenamento inviável ou `JOIN` de recuperação caro demais no corpus.

### D-040 — Fila de jobs de indexação (resolve O-009)
**Status:** Aceita (16/10/2026)  
**Contexto:** `@Async` simples perde jobs se a aplicação cair; a indexação é longa e precisa ser retomada.  
**Opções consideradas:**
1. Executor em processo com recuperação na inicialização, como os imports (D-034).
2. Fila em tabela com retirada por `SKIP LOCKED`, heartbeat e limite de tentativas.

**Decisão:** opção 2.
- `index_jobs` em `PENDING` → `RUNNING` → `COMPLETED` ou `FAILED`.
- Retirada com `SELECT … FOR UPDATE SKIP LOCKED` numa transação curta, que marca `RUNNING`, incrementa `attempt_count` e grava `heartbeat_at`; o trabalho roda fora dela, em workers do processo (`app.indexing.max-concurrent`, padrão 1).
- Heartbeat a cada lote (`heartbeat-interval`, 10 s); job `RUNNING` com heartbeat mais antigo que `lease-timeout` (60 s) volta a ser elegível. Cobre queda da aplicação e thread travada sem código de inicialização.
- Ao ser retirado com `attempt_count` igual a `max-attempts` (3), o job termina `FAILED` com `INDEX_ATTEMPTS_EXHAUSTED`.
- Sondagem a cada `poll-interval` (2 s) e sinal imediato após o commit que cria o job.
- Transições condicionadas ao estado e ao `attempt_count` retirado, para que um worker cujo lease expirou não sobrescreva quem retomou o job.

**Motivo:** retomada sem infraestrutura nova, segura sob concorrência.  
**Consequências:** a D-034 (imports) não muda. O MVP assume um processo do backend por banco; dois processos funcionariam, mas não são cenário testado.  
**O que levaria à revisão:** medições de recurso ou confiabilidade que justifiquem um worker separado (D-005).

#### Atualização de 16/10/2026 — implementação
- Um erro inesperado durante o job devolve-o para `PENDING` na hora (a tentativa já foi contada), sem esperar o lease; na última tentativa, o job termina `FAILED` com `INDEX_IO_ERROR`. `INDEX_ATTEMPTS_EXHAUSTED` fica para jobs cujo worker morreu em todas as tentativas.
- O heartbeat é renovado a cada lote e também durante a varredura, no máximo uma vez por `heartbeat-interval`, para que árvores grandes não percam o lease.
- Um despachante retira os jobs e os entrega a um pool fixo; o sinal após o commit o acorda sem esperar a sondagem.
- A retomada apaga os `snapshot_files` parciais do snapshot e recomeça pela varredura; conteúdo e chunks já gravados são reaproveitados.

### D-041 — Um job ativo por projeto
**Status:** Aceita (16/10/2026)  
**Contexto:** um import ZIP que conclui apaga a pasta do import anterior, que pode ser a origem de uma indexação em andamento; e a D-021 pede `409 PROJECT_BUSY` também para indexação.  
**Opções consideradas:**
1. Verificar, em cada módulo, os jobs ativos dos outros, com lock na linha do projeto.
2. Um registro único de jobs do projeto, com índice único parcial.

**Decisão:** opção 2.
- Tabela `project_jobs` (módulo `project`): uma linha por job, de import ou de indexação, com `kind` e `active`; **índice único parcial `(project_id) WHERE active`**.
- Os IDs de `import_jobs` e `index_jobs` são FK para `project_jobs (id)`.
- `ProjectService.startJob` insere o registro na transação que cria o job (violação do índice → `409 PROJECT_BUSY`; FK do projeto → `404 PROJECT_NOT_FOUND`); `finishJob` o desativa na mesma transação de toda transição terminal.
- `DELETE /projects/{id}` consulta `project_jobs` depois de travar a linha do projeto.
- A indexação lê a origem pela fachada pública `ProjectSources` de `importjob.scan`. Dependências: `indexing` → `importjob` → `project`; `project` e `importjob` não dependem de `indexing` (ArchUnit).

**Motivo:** a garantia fica no banco, segura sob concorrência, sem cada módulo conhecer os jobs dos outros.  
**Consequências:** `ProjectImports.hasActiveImport` sai; os índices únicos de cada tipo de job continuam como defesa adicional.  
**O que levaria à revisão:** necessidade de jobs simultâneos no mesmo projeto (por exemplo, reembedding em paralelo a uma indexação).

### D-042 — Leitura, classificação e hash dos arquivos
**Status:** Aceita (16/10/2026)  
**Decisão:**
- Cada arquivo é lido uma vez, com `NOFOLLOW_LINKS` e leitura limitada a `maxFileBytes + 1`; hash e conteúdo saem dos mesmos bytes.
- Hash: SHA-256 dos bytes brutos, hexadecimal minúsculo.
- Decodificação: UTF-8 estrito, com BOM removido; linhas separadas por `\n`, `\r\n` ou `\r`, numeradas a partir de 1; conteúdo guardado sem normalizar quebras.
- Estados por arquivo do snapshot:
  - `INDEXED`;
  - `UNSUPPORTED`, com motivo `BINARY` (byte NUL em qualquer posição), `INVALID_ENCODING` (não é UTF-8 válido) ou `LINE_TOO_LONG` (D-043);
  - `FAILED`, **só** para erro de leitura (`READ_ERROR`) ou arquivo alterado durante a leitura (`CHANGED_DURING_READ`: atributos diferentes antes e depois, ou tamanho acima do limite).

**Motivo:** `UNSUPPORTED` descreve o conteúdo e não é falha; `FAILED` indica um problema transitório que um novo job pode resolver.  
**Consequências:** outras codificações ficam para a Etapa 8, com casos no corpus.  
**O que levaria à revisão:** proporção relevante de arquivos `INVALID_ENCODING` no corpus.

### D-043 — Chunking genérico inicial (provisória, O-010)
**Status:** Aceita (provisória, 16/10/2026)  
**Decisão:**
- Janela de até 60 linhas e 4.000 caracteres; sobreposição de 10 linhas; corte preferencial em linha em branco nas últimas 15 linhas da janela.
- Linha com mais de 4.000 caracteres: o arquivo inteiro vira `UNSUPPORTED` com `LINE_TOO_LONG`.
- Arquivo vazio ou só com linhas em branco: `INDEXED`, sem chunks.
- `token_count_estimate` = ⌈caracteres / 4⌉ até existir o tokenizador do provedor.
- `chunking_version` = `lines-v1`, com os parâmetros no código; qualquer mudança cria uma versão nova.
- `chunk_hash` = SHA-256 de `chunking_version` + texto do chunk.

**Motivo:** valores conservadores e determinísticos, fáceis de comparar no corpus.  
**Consequências:** a O-010 continua aberta para ajustar os valores na Etapa 5.  
**O que levaria à revisão:** resultados do corpus de avaliação.

#### Atualização de 16/10/2026 — detalhes do `lines-v1`
- Chunks nunca começam nem terminam em linha em branco, e o texto do chunk junta as linhas com `\n`.
- Depois de um corte em linha em branco não há sobreposição; depois de um corte pelo limite, a sobreposição é de até 10 linhas, mas o chunk seguinte começa pelo menos na metade do anterior.
- Comprimentos de linha e de chunk são contados em unidades UTF-16.

### D-044 — Ciclo de vida do snapshot, ativação e poda
**Status:** Aceita (16/10/2026)  
**Decisão:**
- Um snapshot por job: `BUILDING` → `READY`, `PARTIAL` ou `FAILED`. Etapas do job: `SCANNING` → `PROCESSING_FILES` → `FINALIZING` (a Etapa 4 acrescenta `EMBEDDING`).
- O job falha, sem ativar o snapshot, com origem indisponível (`SOURCE_*`), `SCAN_LIMIT_REACHED`, `PROJECT_SIZE_LIMIT_EXCEEDED`, `NO_INCLUDED_FILES`, `INDEX_IO_ERROR` ou `INDEX_ATTEMPTS_EXHAUSTED`.
- Arquivos `FAILED` deixam o snapshot `PARTIAL`, que é ativado; arquivos `UNSUPPORTED` não impedem `READY`.
- A API expõe as contagens de arquivos `FAILED` e `UNSUPPORTED` do snapshot e a lista de cada um, com o motivo.
- A ativação (contagens, resumo de mudanças, `projects.active_snapshot_id`, job `COMPLETED`) é uma transação.
- Resumo de mudanças contra o snapshot ativo anterior, sobre os arquivos `INDEXED`: `added`, `modified`, `removed`, `unchanged`.
- O snapshot guarda `settings_version`, `chunking_version` e, em ZIP, o import de origem; o projeto informa `outdatedReasons` (`SETTINGS_CHANGED`, `SOURCE_REIMPORTED`, `CHUNKING_CHANGED`).
- **Poda:** além do ativo, mantêm-se os `app.indexing.retained-snapshots` (padrão 3) mais recentes `READY`/`PARTIAL`. Os mais antigos são podados (`pruned_at`; a linha fica como histórico, sem `snapshot_files`); os `FAILED` perdem os `snapshot_files` ao falhar. Em seguida, a limpeza da D-039.

**Motivo:** a falha de um arquivo não deve invalidar o projeto (`03-UX-ONBOARDING.md` §3), e uma árvore incompleta não pode virar índice ativo.  
**Consequências:** mudanças no disco de pasta local não são detectadas sem nova indexação. Resolve as pendências da Etapa 2 sobre `settings_version` e `PROJECT_SIZE_LIMIT_EXCEEDED`.  
**O que levaria à revisão:** volume de snapshots retidos, ou a Etapa 6 exigir outra regra de retenção.

### D-045 — Ajustes de contrato da Etapa 3
**Status:** Aceita (16/10/2026)  
**Decisão:**
- `POST /projects/{projectId}/index-jobs` sem corpo: o campo `mode` sai, porque toda indexação é incremental por conteúdo. A resposta é o job completo.
- `POST /index-jobs/{jobId}/retry` sai: jobs interrompidos são retomados automaticamente (D-040), e um job `FAILED` é refeito criando outro.
- Um novo job reprocessa os arquivos cujo estado anterior era `FAILED`: todo arquivo é lido de novo, e um arquivo `FAILED` não tem conteúdo a reaproveitar.
- Novos: `GET /projects/{projectId}/index-jobs`, `GET /projects/{projectId}/snapshots`, `GET /snapshots/{snapshotId}` e `GET /snapshots/{snapshotId}/files` (substitui `GET /projects/{projectId}/files`, para o snapshot ser explícito).
- Códigos novos: `INDEX_JOB_NOT_FOUND` e `SNAPSHOT_NOT_FOUND`. Códigos do job em `index.error.<CODE>` (pt-BR), sem coluna de mensagem.

**Consequências:** `06-API-CONTRACTS.md` §5, §7 e §9 atualizados.

### D-046 — Evidências independentes da retenção de snapshots
**Status:** Aceita (16/10/2026; implementação na Etapa 6)  
**Contexto:** a poda de snapshots e a limpeza de chunks (D-039, D-044) não podem invalidar respostas já dadas.  
**Decisão:**
- `answer_evidence` guarda **cópia** do caminho, das linhas, do hash do conteúdo e do trecho no momento da resposta, além das referências ao snapshot e ao chunk.
- Podar um snapshot nunca apaga respostas nem evidências: as referências a snapshot e chunk não têm FK com cascata.
- Ao abrir uma evidência cujo snapshot foi podado, a API devolve a cópia com `snapshotStatus: "PRUNED"` ("snapshot removido"), em vez de erro.

**Motivo:** o histórico (F-07) precisa continuar verificável depois da poda.  
**Consequências:** `05-DATA-MODEL.md` (`answer_evidence`) e `06-API-CONTRACTS.md` §9 atualizados. A remoção do projeto continua apagando o histórico (D-021).  
**O que levaria à revisão:** volume de evidências copiadas relevante frente ao conteúdo indexado.

### D-047 — Provedor inicial de embeddings (O-004, provisória)
**Status:** Aceita (provisória, 16/10/2026; bake-off na Etapa 5)  
**Contexto:** a O-004 pede um provedor de embeddings antes de concluir a Etapa 4, mas a comparação de qualidade depende do corpus da Etapa 5 (O-011).  
**Opções consideradas:**
1. OpenAI `text-embedding-3-small`, pelo módulo OpenAI do Spring AI.
2. Voyage `voyage-code-3`, especializado em código.
3. Adiar a escolha e entregar a etapa só com um provedor fake.

**Decisão:** opção 1, provisória; a Voyage entra como candidata no bake-off.
- `app.embeddings.provider`: `none` (padrão), `fake` ou `openai`; `app.embeddings.model` (padrão `text-embedding-3-small`) e `app.embeddings.dimensions` (padrão 1536, entre 1 e 2.000).
- A chave vem de `EMBEDDING_API_KEY` no `.env`, obrigatória com `openai`; a aplicação não inicia sem ela. A chave nunca aparece em código, logs ou mensagens de erro.
- `app.embeddings.base-url` (padrão `https://api.openai.com/v1`) decide para onde vão a chave e os trechos de código. Só `https` é aceito; `http` apenas para `localhost` ou `127.0.0.1` (servidor de teste e servidores locais futuros). Endereço com usuário e senha embutidos também é recusado. Fora disso, a aplicação não inicia, e a mensagem de erro não repete o valor.
- Os testes do `./mvnw verify` usam `fake` e nunca precisam de chave; a chave real só entra no teste manual de fumaça. Uma `ContextCustomizerFactory` de teste, registrada em `META-INF/spring.factories`, impede qualquer contexto de teste de subir com `provider=openai`, inclusive contextos novos que esqueçam de fixar o provedor e herdem o valor do `.env`.
- O SDK da OpenAI tem repetição própria, que o adaptador desliga: a política da D-051 é a única camada de repetição.

**Implementação (F1, 16/10/2026):**
- Porta `EmbeddingProvider` em `providers` (`embed(textos)` → vetores + tokens; `model()` → provedor, modelo e dimensão), sem tipos do Spring AI nem do SDK da OpenAI na assinatura. O ArchUnit passa a barrar também `com.openai` fora de `providers`.
- **Cliente montado pelo Spring AI, chamada pelo SDK:** o `OpenAiSetup` do Spring AI cria o cliente (OkHttp, `base-url`, chave, timeout), mas a chamada vai direto a `embeddings().create()` do SDK. O `OpenAiEmbeddingModel` do Spring AI lê o uso de tokens como campo obrigatório e falha quando o provedor não o informa, o que impediria o uso nulo exigido na D-054 com servidores compatíveis.
- **Nesta etapa, o Spring AI traz só o SDK e a configuração do cliente.** Como o código chama o SDK diretamente, `com.openai:openai-java-core` é declarado explicitamente no `pom.xml`. O `spring-ai-bom` não gerencia essa versão, então ela fica numa propriedade igual à que o `spring-ai-openai` declara (4.49.0 no 2.0.1), e um teste falha se as duas divergirem. A Etapa 5 decide se mantém o `spring-ai-openai` ou passa a usar só o SDK.
- No cliente: repetição interna em zero e log do SDK desligado, porque `OPENAI_LOG=debug` no ambiente registraria o corpo das requisições. Um teste roda o adaptador numa JVM filha com `OPENAI_LOG=debug` e confirma que nenhum corpo aparece na saída; um teste de controle mostra que, sem o desligamento, o SDK imprime o texto enviado.
- Timeout de `app.embeddings.timeout` por chamada (conexão e chamada inteira); estouro vira `UNAVAILABLE`.
- Classificação dos erros e repetição: D-051.
- Resposta validada antes de devolver qualquer vetor: mesma quantidade de vetores e textos, índices únicos dentro da faixa (o resultado sai na ordem dos índices), dimensão igual à configurada e componentes finitos. Qualquer divergência rejeita o lote inteiro com `INVALID_REQUEST`.
- Tokens: `total_tokens` reportado, ou `prompt_tokens` na falta dele; sem uso na resposta, `null`. Lote vazio não faz requisição.
- Mensagens de `EmbeddingFailure` trazem só tipo, status HTTP e quantidades. A exceção do SDK não é guardada como causa, porque a mensagem dela inclui o corpo da resposta do provedor.
- Com `provider=none` não há bean `EmbeddingProvider`; o provedor `fake` usa o modelo e a dimensão configurados e informa como tokens a contagem de termos.

**Preço e uso de dados (fonte oficial, consultada em 16/10/2026):**
- Preço: **US$ 0,02 por 1M de tokens de entrada** para `text-embedding-3-small` (tarifa Standard; a página não lista preço Batch para o modelo). Fonte: `developers.openai.com/api/docs/pricing`.
- Uso de dados: pela tabela de endpoints, `/v1/embeddings` **não usa os dados para treino** (sem opt-in), tem **retenção de 30 dias** para monitoramento de abuso, sem retenção de estado da aplicação, e é elegível a Zero Data Retention (mediante acordo com a OpenAI). Fonte: `developers.openai.com/api/docs/guides/your-data`.

**Motivo:** integração mais madura no Spring AI 2.0 (D-014) e API "compatível com OpenAI" oferecida por outros provedores e servidores locais, o que facilita a Etapa 9 (D-019).  
**Consequências:** o texto dos chunks indexados e as perguntas de busca saem da máquina quando `provider=openai` (D-054). Preço e política devem ser reconferidos antes de alterar esta decisão.  
**O que levaria à revisão:** resultado do bake-off da Etapa 5, mudança de preço ou de política de dados, ou integração da Voyage no Spring AI.

### D-048 — Armazenamento dos vetores (revisa a restrição da O-004)
**Status:** Aceita (16/10/2026; F2 entregue)  
**Contexto:** a O-004 previa fixar a dimensão do vetor por migração, o que obrigaria uma migração a cada troca de modelo, inclusive no bake-off da Etapa 5.  
**Opções consideradas:**
1. Coluna `vector(N)` com a dimensão do modelo escolhido.
2. Coluna `vector` sem dimensão fixa, com a dimensão na própria linha.

**Decisão:** opção 2, na migração `V8__create_chunk_embeddings.sql`.
- **`chunk_embeddings`**: PK `(chunk_hash, provider, model, dimensions)`, global (sem `project_id`); `embedding vector` sem dimensão; `CHECK (dimensions BETWEEN 1 AND 2000)` e `CHECK (vector_dims(embedding) = dimensions)` (`chunk_embeddings_dimensions_match`). Sem FK para `chunks` (o embedding é por texto e sobrevive à remoção de um projeto até a limpeza de órfãos) e sem índice aproximado (busca exata, D-049).
- **`snapshots`** ganha `embedding_provider`, `embedding_model`, `embedding_dimensions` e `embedding_tokens`; os três primeiros são todos nulos ou todos preenchidos (`snapshots_embedding_consistency`).
- **`index_jobs`**: a constraint `index_jobs_stage_valid` passa a aceitar `EMBEDDING`.
- Verificado no PostgreSQL dos testes (pgvector 0.8.7): a coluna fica com tipo `vector` sem modificador, o `CHECK` com `vector_dims` rejeita vetor de outra dimensão, e o mesmo `chunk_hash` coexiste com modelos ou dimensões diferentes.

**Implementação (F2, 16/10/2026):** `ChunkEmbeddingRepository` em `indexing`, com `JdbcClient` e SQL explícito.
- **Vetor como parâmetro:** literal de texto do pgvector (`[0.6,-0.8]`), passado como parâmetro e convertido com `CAST(:embedding AS vector)`, nunca concatenado ao SQL. Os componentes são escritos com `Float.toString`, que não depende do locale (ponto decimal sempre, inclusive com locale padrão pt-BR, coberto por teste). NaN, infinito, vetor vazio e vetor com dimensão diferente da do modelo são rejeitados antes de chegar ao banco.
- **Hashes sem embedding de um snapshot:** chunks dos arquivos do snapshot na `chunking_version` dele (D-039), sem linha em `chunk_embeddings` para o mesmo provedor, modelo e dimensão. Embedding de outro modelo ou de outra dimensão conta como faltando. Devolve hashes distintos, em ordem.
- **Inserção em lote idempotente:** `ON CONFLICT DO NOTHING`, devolvendo quantas linhas foram realmente inseridas (soma das contagens do lote; o driver não usa `reWriteBatchedInserts`, e uma contagem não informada falha em vez de virar zero).
- **Limpeza de órfãos:** apaga embeddings cujo `chunk_hash` não existe em nenhum chunk, de qualquer projeto, e devolve a quantidade.
- **Plano de execução:** com 200 mil chunks em 100 projetos, o `EXPLAIN` da consulta de hashes sem embedding usa a PK de `snapshot_files`, `chunks_natural_key` e a PK de `chunk_embeddings`, sem varredura sequencial dessas tabelas (`ChunkEmbeddingPlanIT`). Com 40 mil chunks o planejador ainda prefere varrer `chunks` com hash join, o que é mais barato nesse tamanho. Não é preciso índice novo: o índice em `chunks(chunk_hash)` já existe desde a V7 e serve à limpeza de órfãos.

**Motivo:** trocar de modelo, inclusive no bake-off, não exige migração, e vetores de modelos diferentes convivem no cache.  
**Consequências:** toda consulta filtra por provedor, modelo e dimensão. Um índice aproximado futuro precisa ser parcial por modelo, sobre `embedding::vector(N)`. O limite de 2.000 dimensões continua validado na inicialização (D-047) e no `CHECK`.  
**O que levaria à revisão:** necessidade de mais de 2.000 dimensões (`halfvec`) ou índice aproximado que não funcione bem como índice parcial.

### D-049 — Busca vetorial exata (trata o risco da D-039)
**Status:** Aceita (16/10/2026; F4 entregue)  
**Contexto:** com HNSW, o filtro por snapshot é aplicado depois da busca aproximada e pode devolver menos resultados que o pedido.  
**Opções consideradas:**
1. HNSW com varredura iterativa do pgvector (`hnsw.iterative_scan`).
2. Busca exata (distância de cosseno sobre os chunks do snapshot, sem índice aproximado).
3. Índice HNSW parcial por projeto.

**Decisão:** opção 2 como baseline.
- `VectorSearch`, no módulo `retrieval`, em SQL explícito sobre as tabelas da indexação (D-016): chunks dos arquivos do snapshot na `chunking_version` dele (D-039), unidos a `chunk_embeddings` pela PK com provedor, modelo e dimensão do modelo configurado (D-048). Vetores de outros modelos nunca entram.
- Ordem: distância de cosseno (`<=>`), depois o primeiro caminho em ordem de bytes (`COLLATE "C"`), a linha inicial e o id do chunk. `score` = 1 − distância.
- Uma linha por chunk: um chunk compartilhado por vários arquivos do snapshot traz todos os caminhos, ordenados; o primeiro vira `path` e os demais `otherPaths`.
- O vetor da pergunta vai como literal de texto convertido no SQL (`VectorLiteral`, movido para `shared.persistence`, porque agora `indexing` e `retrieval` o usam).

**Medição (16/10/2026, `VectorSearchPerformanceIT`, tag `performance`, fora do `verify`):** 50 mil chunks sintéticos de 1.536 dimensões num snapshot, 50 consultas depois de 5 de aquecimento, `limit` 10: **média de 213 ms, p95 de 313 ms, máximo de 396 ms**, num Apple M4 com 16 GB e o PostgreSQL 17 do Testcontainers (pgvector 0.8.7).  
**Motivo:** correta por construção (só considera os chunks do snapshot pedido), com custo proporcional ao tamanho do snapshot, limitado por `max-project-bytes`.  
**Consequências:** a latência cresce linearmente com os chunks do snapshot; 50 mil chunks ficam na casa de 0,2 a 0,3 s, aceitável para uso local e acima do que se quer numa busca híbrida com várias consultas por pergunta.  
**O que levaria à revisão:** a comparação da Etapa 5 ou 8 com HNSW parcial por modelo e varredura iterativa (mesmos resultados no corpus, menos latência), que a D-048 permite sem migrar dados.

### D-050 — Etapa `EMBEDDING` no job e ativação do snapshot
**Status:** Aceita (16/10/2026; F3 e F5 entregues)  
**Contexto:** o snapshot ativo precisa ser sempre pesquisável, e uma falha do provedor não pode deixar o projeto sem snapshot utilizável (D-044).  
**Decisão:**
- Nova etapa `EMBEDDING` entre `PROCESSING_FILES` e `FINALIZING`. Ela lista os `chunk_hash` do snapshot sem embedding do modelo configurado (D-048), envia em lotes de `app.embeddings.batch-size` (padrão 64) e grava com `ON CONFLICT DO NOTHING`. O progresso do job passa a contar chunks (`total` = chunks a enviar), e cada lote grava progresso e renova o heartbeat na mesma transação da inserção.
- O texto de cada lote é lido por uma consulta por lote (texto de um chunk por hash, no projeto), sem carregar o texto de todos os chunks de uma vez.
- **O snapshot só é ativado com todos os seus chunks com embedding.** Se o provedor falhar, o job termina `FAILED` e o ativo anterior continua valendo.
- A finalização grava `embedding_provider`, `embedding_model`, `embedding_dimensions` e `embedding_tokens` no snapshot, na mesma transação da ativação.
- Reindexar com o mesmo modelo não chama o provedor para chunks inalterados (cache por hash); `embedding_tokens` fica `0` nesse caso.
- Com `app.embeddings.provider=none`, a etapa é pulada e o snapshot é ativado sem metadados de modelo.
- **Estado do snapshot ativo** em `outdatedReasons`, só com provedor configurado: `EMBEDDINGS_MISSING` quando o snapshot não tem metadados de modelo (indexado com `provider=none`) e `EMBEDDING_MODEL_CHANGED` quando provedor, modelo ou dimensão diferem dos configurados. Com `provider=none`, nenhum dos dois aparece: sem modelo configurado, nada fica faltando. Nos dois casos a busca responde `409 SNAPSHOT_EMBEDDING_MISMATCH` (D-053).
- Trocar de modelo gera vetores só na próxima indexação, para todos os chunks do snapshot, mesmo sem arquivo alterado; os vetores do modelo anterior continuam no cache até seus chunks sumirem (D-052).

**Motivo:** a busca vetorial nunca encontra um snapshot ativo pela metade, e o cache evita pagar duas vezes pelo mesmo texto.  
**Consequências:** um job com muitos chunks novos fica mais longo e depende do provedor; falhas são tratadas pela D-051. O valor `EMBEDDING` aparece em `stage` na API.  
**O que levaria à revisão:** necessidade de ativar snapshots sem vetores quando o provedor estiver fora (busca só lexical, Etapa 5).

### D-051 — Falhas do provedor de embeddings e repetição
**Status:** Aceita (16/10/2026; F1, F3 e F4 entregues)  
**Contexto:** chamadas ao provedor falham por credencial, limite de taxa, cota, indisponibilidade ou pedido recusado. Algumas falhas passam com o tempo, outras não, e um job esperando o provedor não pode perder o lease (D-040).  
**Decisão:**
- **Classificação na porta** (`EmbeddingFailure.Type`, sem tipos do Spring AI nem do SDK):

  | Tipo | Quando (adaptador OpenAI) | Repete? |
  |---|---|---|
  | `UNAUTHORIZED` | 401, 403 | não |
  | `QUOTA_EXCEEDED` | 429 com `error.code` de cobrança ou `error.type` `insufficient_quota` | não |
  | `RATE_LIMITED` | demais 429 (por exemplo, sem `code` ou com `slow_down`) | sim |
  | `UNAVAILABLE` | 408, 409, 5xx, falha de rede, timeout da chamada | sim |
  | `INVALID_REQUEST` | demais 4xx; resposta ilegível ou divergente (quantidade, índice, dimensão) | não |

- **Cota esgotada é permanente.** Pelo guia oficial de códigos de erro da OpenAI (`developers.openai.com/api/docs/guides/error-codes`, consultado em 16/10/2026), os 429 de cobrança são identificados por `error.code`: `credit_balance_exhausted`, `organization_spend_limit_exceeded`, `project_spend_limit_exceeded` e `organization_usage_limit_exceeded`. O `error.type` ainda pode vir como `insufficient_quota`, e a página afirma que repetir erros de cobrança, gasto ou cota não restaura o acesso. O adaptador lê `code` e `type` do corpo só para classificar; nenhum dos dois vai para log ou mensagem.
- **Uma única camada de repetição**, `EmbeddingRetry`; o cliente da OpenAI repete zero vezes. Só `RATE_LIMITED` e `UNAVAILABLE` são repetidos, até `app.embeddings.max-retries` (padrão 4, ou seja, até 5 chamadas). Esgotadas as tentativas, a última falha é lançada.
- **Espera:** 1 s, dobrando a cada tentativa. `Retry-After` (segundos ou data HTTP) ou `retry-after-ms`, quando presentes em `RATE_LIMITED` ou `UNAVAILABLE`, substituem a espera calculada. Nenhuma espera passa de `app.embeddings.max-retry-wait` (padrão 60 s). Sem variação aleatória.
- **Heartbeat via callback:** a espera é feita em fatias de no máximo o intervalo informado por quem chama, e um callback roda após cada fatia. Uma exceção lançada pelo callback (claim perdido) interrompe a espera e se propaga sem nova tentativa. A espera é injetável (`Sleeper`), e os testes controlam o tempo sem dormir. Interrupção da thread encerra a espera com a última falha.
- **Mensagens e logs** trazem só tipo, status HTTP, quantidades e duração; a exceção do SDK não é guardada como causa.

**Códigos do job (F3):** `UNAUTHORIZED` → `EMBEDDING_PROVIDER_UNAUTHORIZED`; `QUOTA_EXCEEDED` → `EMBEDDING_QUOTA_EXCEEDED`; `RATE_LIMITED` esgotado → `EMBEDDING_RATE_LIMITED`; `UNAVAILABLE` esgotado → `EMBEDDING_PROVIDER_UNAVAILABLE`; `INVALID_REQUEST` → `EMBEDDING_REQUEST_REJECTED`. Falhas permanentes encerram o job na hora e não voltam à fila (D-040). No job, a espera é fatiada em `app.indexing.heartbeat-interval` e cada fatia renova o lease; claim perdido interrompe a espera e o worker para sem tocar no job. As mensagens pt-BR ficam em `index.error.<CODE>`, sem detalhes do provedor.

**Na busca (F4):** `UNAVAILABLE` → `502 EMBEDDING_PROVIDER_UNAVAILABLE`; `UNAUTHORIZED` → `502 EMBEDDING_PROVIDER_UNAUTHORIZED`; `QUOTA_EXCEEDED` → `502 EMBEDDING_QUOTA_EXCEEDED` (decidido em 16/10/2026: o problema é da conta no provedor, não do cliente da API); `INVALID_REQUEST` → `502 EMBEDDING_REQUEST_REJECTED` (acrescentado na F4, o plano não o previa); `RATE_LIMITED` → `429 EMBEDDING_RATE_LIMITED`. A busca não repete chamadas, porque há uma pessoa esperando; a mensagem não traz detalhes do provedor. O `Retry-After` do provedor ainda não é repassado na resposta.  
**Motivo:** repetir só o que pode passar evita custo e espera inúteis; uma só camada de repetição torna o número de chamadas previsível (com 429 e `max-retries=4`, exatamente 5 requisições chegam ao provedor).  
**Consequências:** o callback é o ponto de integração da F3 com o `Heartbeat`. A classificação de cota depende dos códigos documentados pela OpenAI e deve ser reconferida se a página mudar.  
**O que levaria à revisão:** novos códigos de erro do provedor, outro provedor com semântica diferente, ou medição mostrando repetições sincronizadas entre workers (o que pediria variação aleatória).

### D-052 — Cache de embeddings e limpeza de órfãos
**Status:** Aceita (16/10/2026; F3 entregue)  
**Contexto:** `chunk_embeddings` é global e sem FK para `chunks` (D-048); sem limpeza, vetores de conteúdos removidos ficariam para sempre.  
**Decisão:**
- Cache pela PK `(chunk_hash, provider, model, dimensions)`: o mesmo texto nunca é enviado duas vezes para o mesmo modelo, em qualquer projeto.
- **Limpeza de órfãos ao fim de cada job concluído**, na transação da finalização, depois da limpeza de chunks da D-039: apagam-se os embeddings cujo `chunk_hash` não existe mais em nenhum chunk.
- **Job com falha não limpa:** os embeddings já gravados ficam no cache, e o job seguinte retoma de onde o anterior parou, sem pagar de novo. A limpeza de um job concluído (de qualquer projeto) ou a varredura na inicialização pode apagá-los antes disso, porque os chunks do snapshot com falha são removidos.
- **Varredura na inicialização** (`ApplicationReadyEvent`): cobre a remoção de projetos sem nenhum job posterior.
- Embeddings de um modelo que deixou de ser o configurado só somem quando seus chunks somem; limpeza por modelo fica para quando houver mais de um modelo em uso real.

**Motivo:** o custo de reprocessar fica no que mudou, e o banco não acumula vetores sem uso.  
**Consequências:** a limpeza percorre `chunk_embeddings` com anti-join pelo índice de `chunks(chunk_hash)` a cada job concluído.  
**O que levaria à revisão:** limpeza lenta em bancos grandes (medição na Etapa 8) ou bake-off com vários modelos ao mesmo tempo.

### D-053 — Contrato da busca
**Status:** Aceita (16/10/2026; F4 e F5 entregues)  
**Decisão:**
- **`POST /api/v1/projects/{projectId}/search`** com `{ "query": "…", "snapshotId": null, "limit": 10 }`. `snapshotId` ausente = snapshot ativo; informado, precisa ser do projeto, `READY`/`PARTIAL` e não podado. `query` de 1 a 2.000 caracteres e não em branco; `limit` de 1 a 50 (padrão 10).
- Resposta: `{ snapshotId, model: { provider, name, dimensions }, results: [ { chunkId, path, otherPaths, startLine, endLine, content, score } ] }`, em ordem decrescente de `score` (D-049). O plano previa só `model`; virou objeto porque o provedor também identifica os vetores (D-048).
- Erros, nesta ordem: `400 VALIDATION_FAILED` (campos `query` e `limit`, antes de qualquer consulta); `404 PROJECT_NOT_FOUND`; `404 SNAPSHOT_NOT_FOUND` (inclusive de outro projeto); `409 SNAPSHOT_NOT_SEARCHABLE` (sem snapshot ativo, `BUILDING`, `FAILED` ou podado); `409 EMBEDDINGS_NOT_AVAILABLE` (`provider=none`); `409 SNAPSHOT_EMBEDDING_MISMATCH` (snapshot sem embeddings ou de outro provedor, modelo ou dimensão); erros do provedor (D-051).
- Logs da busca trazem snapshot, quantidade de resultados e duração, nunca a pergunta (D-054).
- A Etapa 5 acrescenta `mode` (`VECTOR`, `LEXICAL`, `HYBRID`) no mesmo endpoint.
- `GET /api/v1/snapshots/{id}` e a listagem de snapshots trazem `embedding: { provider, model, dimensions, tokens }`, ou `null` sem metadados de modelo; `tokens` é `null` quando o provedor não informou o uso (D-054). `outdatedReasons` do projeto ganha `EMBEDDING_MODEL_CHANGED` e `EMBEDDINGS_MISSING` (D-050).

**Motivo:** o cliente sempre sabe qual snapshot e qual modelo responderam, e cada erro tem um código estável.  
**Consequências:** `ProjectService.find` foi acrescentado para a busca ler o snapshot ativo sem montar o resumo completo do projeto.  
**O que levaria à revisão:** a Etapa 5 (modo híbrido) ou a Etapa 6 (evidências da resposta).

### D-054 — Privacidade, custo e limites de entrada dos embeddings
**Status:** Aceita (16/10/2026; F3 entregue, a F6 documenta no README e no `07`)  
**Contexto:** com `provider=openai`, trechos de código saem da máquina e cada token tem custo (D-047).  
**Decisão:**
- Só o texto de chunks de arquivos `INDEXED` vai ao provedor; arquivos excluídos, não suportados ou com falha nunca saem da máquina. A pergunta da busca também é enviada (F4).
- Logs trazem quantidades (chunks, lotes, tokens, duração) e códigos, nunca texto de chunk, pergunta ou chave (coberto por teste com saída capturada).
- `embedding_tokens` soma o uso **reportado** pelo provedor no job. Se algum lote não reportar uso, fica `null`: uma soma parcial seria um número inventado.
- **Limite por job:** `app.embeddings.max-chunks-per-job` (padrão 50.000) conta só os chunks a enviar (os do snapshot sem embedding do modelo). Acima dele, o job falha com `EMBEDDING_CHUNK_LIMIT_EXCEEDED` antes de qualquer chamada ao provedor, e o ativo anterior não muda.
- **Limites de entrada do modelo (fonte oficial, consultada em 16/10/2026):** pelo guia de embeddings (`developers.openai.com/api/docs/guides/embeddings`, coluna "Max input") e pela referência de `POST /v1/embeddings` (`developers.openai.com/api/reference/resources/embeddings/methods/create`), `text-embedding-3-small`, `text-embedding-3-large` e `text-embedding-ada-002` aceitam **8.192 tokens por entrada**; uma requisição aceita **até 2.048 entradas** e **até 300.000 tokens somados**; entrada vazia é recusada.
- A porta expõe esses limites (`InputLimits`). O adaptador OpenAI conhece só os três modelos acima: outro modelo impede a inicialização. O fake não tem limite.
- **Validação na inicialização** (com provedor configurado): `4.000` caracteres por chunk (D-043) ≤ tokens por entrada; `batch-size` ≤ entradas por requisição; `batch-size` × 4.000 ≤ tokens por requisição. Para a OpenAI, `batch-size` vai até 75 (o padrão 64 passa).
- A conta usa **um token por caractere**. Não é um limite rigoroso (texto fora do ASCII pode passar disso), mas código fica em média bem abaixo de um token por caractere. Um chunk que ainda assim exceda o limite é recusado pelo provedor e falha o job com `EMBEDDING_REQUEST_REJECTED` (limitação conhecida, igual à do lote recusado). Os chunks nunca são vazios (o chunker descarta linhas em branco nas pontas).
- Sem tokenizador exato nesta etapa.

**Motivo:** o custo e o destino dos dados ficam previsíveis, e uma configuração incompatível com o modelo falha na inicialização, não no meio de um job.  
**Consequências:** modelos novos ou servidores compatíveis exigem acrescentar o limite oficial ao adaptador. `07-SECURITY-PRIVACY-COSTS.md` e o README são atualizados na F6.  
**O que levaria à revisão:** chunks recusados por tamanho no teste de fumaça ou no uso real (pediria tokenizador exato), ou provedores locais na Etapa 9 (pediria limite configurável).

---

## Decisões em aberto

### O-004 — Provedor inicial (chat e embeddings)
**Status:** A validar (bake-off na Etapa 5, com o corpus de avaliação)  
**Restrições já aceitas:**
- Escolher **um** par embedding/chat por instalação; trocar de modelo implica reindexar.
- Preferir modelos de embedding com **até 2.000 dimensões**, compatível com índice HNSW sobre `vector` no pgvector (`halfvec` permite mais). A dimensão não é mais fixada por migração: fica na linha (D-048).
- Provedor de embeddings e de chat podem ser diferentes (a Anthropic, por exemplo, não oferece modelo de embeddings próprio), o que justifica as duas portas separadas.

**Critérios de comparação:** recall@k e MRR no corpus, qualidade em código, custo por 1M de tokens, latência, limites de taxa e política de retenção de dados do provedor.  
**Prazo:** decidir antes de concluir a Etapa 4, mesmo que provisoriamente, para fixar a dimensão do vetor na migração.

### O-005 — Comparação final de estratégia lexical e fusão
**Status:** A validar  
**Contexto:** D-017 define o ponto de partida. Falta compará-lo com alternativas.  
**A comparar:** lexical puro, vetorial puro, híbrido com RRF e variações (valor de k, pesos), tratamento de identificadores, e, apenas se o lexical se mostrar fraco, índice auxiliar com BM25.  
**Evidência para decidir:** resultados no corpus da Etapa 5 (recall@k, MRR, latência).

### O-010 — Parâmetros iniciais de chunking
**Status:** A validar  
**Contexto:** tamanho, sobreposição e limites de linhas são hipóteses.  
**Plano:** ajustar no corpus, alterando uma variável por vez (`08-EVALUATION.md`, seção 8).  
**Etapa 3 (16/10/2026):** valores iniciais provisórios na D-043 (`lines-v1`: 60 linhas, 4.000 caracteres, sobreposição de 10 linhas, `LINE_TOO_LONG` acima de 4.000 caracteres por linha). A comparação fica para a Etapa 5.

### O-011 — Corpus de avaliação
**Status:** A decidir antes da Etapa 5  
**Questões:** quais repositórios (licença permissiva, tamanhos e linguagens variados), quem anota arquivos/linhas esperados e perguntas sem resposta, e como versionar o corpus.  
**Sugestão:** projetos próprios mais um ou dois repositórios open source conhecidos.

---

## Processo de mudança

Para cada decisão relevante, registrar:
- contexto;
- opções consideradas;
- decisão;
- motivo;
- consequências;
- evidência/experimento que poderia levar à revisão.
