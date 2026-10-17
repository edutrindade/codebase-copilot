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

#### Atualização de 16/10/2026 — Spring AI removido (Etapa 6)
**Contexto:** a Etapa 5 adiou para a Etapa 6 a escolha entre manter o `spring-ai-openai` ou usar só o SDK (P9), porque o `ChatClient`/`ChatModel` poderia se pagar na geração. Até aqui o Spring AI só montava o cliente (`OpenAiSetup`); a chamada de embeddings já ia direto ao SDK.  
**Opções consideradas:**
1. Manter o Spring AI e gerar respostas pelo `OpenAiChatModel`.
2. Usar só o SDK `openai-java`, nos embeddings e no chat.

**Verificação (16/10/2026, F0 da Etapa 6, fontes do `spring-ai-openai` 2.0.1 e bytecode do `spring-ai-model` 2.0.1):**
- uso de tokens ausente: o `OpenAiChatModel` usa `EmptyUsage`, que devolve **0** tokens; zero e "não informado" ficam iguais (contra a D-054);
- log: sem `choices`, o modelo faz `logger.warn("No choices returned for prompt: " + prompt)`, e o `Prompt.toString()` traz as mensagens, isto é, instruções, pergunta e trechos de código;
- repetição desligável e saída estruturada estrita: atendidas pelos dois caminhos.

**Decisão:** opção 2. Saem o `spring-ai-bom` e o `spring-ai-openai`; entra o **`com.openai:openai-java-client-okhttp` 4.49.0**, na mesma versão do `openai-java-core` (propriedade `openai-java.version`, conferida por teste). Ele traz o OkHttp 4.12.0, a mesma versão que o Spring AI trazia. O cliente é montado pelo `OpenAiClients` (módulo `providers`): `baseUrl` e chave sempre explícitos (nunca `fromEnv()`, que leria `OPENAI_*` do ambiente), `maxRetries(0)` e `logLevel(OFF)`.  
**Motivo:** o que o `ChatClient` acrescenta (advisors, memória, conversores) é o que este projeto controla à mão, e o modelo de chat do Spring AI quebra duas regras já aceitas (D-054 e "não logar prompts").  
**Consequências:** a D-006 continua valendo (Spring AI "onde for útil", sempre atrás de portas); hoje ele não é útil em nenhum ponto. O ArchUnit continua barrando `org.springframework.ai` e `com.openai` fora de `providers`. A versão-alvo do Spring AI desta decisão deixa de valer até ele voltar ao projeto.  
**O que levaria à revisão:** um provedor novo (por exemplo, local, na Etapa 9) cuja integração só exista no Spring AI, ou uma versão do Spring AI que corrija os dois pontos acima.

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
**Estado (16/10/2026, fim da Etapa 5):** implementada como descrito: tokenizador (D-055), índice `chunk_lexical` (D-056), consulta com OR (D-057), caminhos por `pg_trgm` (D-058) e RRF k=60 (D-059). Baseline lexical medido no corpus (D-061, D-062): Recall@10 0,472 e MRR@10 0,341 em 36 perguntas, com queda forte em pt-BR (0,296 contra 0,648 em en). **Continua provisória:** a comparação com o vetorial e o híbrido depende de vetores reais, e a etapa fechou sem saldo no provedor.  
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
**Consequências:** texto da Etapa 0 em `09-IMPLEMENTATION-ROADMAP.md` ajustado; a biblioteca de UI e o gerenciamento de estado continuam a decidir na Etapa 7 (decididos na D-070).  
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
**Etapa 8 (17/10/2026):** detecção por conteúdo acrescentada na D-083, sem mudar as regras de nome.  
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
**Status:** Aceita (16/10/2026; implementada na Etapa 6, D-065 e D-068)  
**Contexto:** a poda de snapshots e a limpeza de chunks (D-039, D-044) não podem invalidar respostas já dadas.  
**Decisão:**
- `answer_evidence` guarda **cópia** do caminho, das linhas, do hash do conteúdo e do trecho no momento da resposta, além das referências ao snapshot e ao chunk.
- Podar um snapshot nunca apaga respostas nem evidências: as referências a snapshot e chunk não têm FK com cascata.
- Ao abrir uma evidência cujo snapshot foi podado, a API devolve a cópia com `snapshotStatus: "PRUNED"` ("snapshot removido"), em vez de erro.

**Motivo:** o histórico (F-07) precisa continuar verificável depois da poda.  
**Consequências:** `05-DATA-MODEL.md` (`answer_evidence`) e `06-API-CONTRACTS.md` §9 atualizados. A remoção do projeto continua apagando o histórico (D-021).  
**O que levaria à revisão:** volume de evidências copiadas relevante frente ao conteúdo indexado.

### D-047 — Provedor inicial de embeddings (O-004, provisória)
**Status:** Aceita (provisória, 16/10/2026; bake-off adiado na Etapa 5, P8; `spring-ai-openai` removido na Etapa 6, ver D-014)  
**Contexto:** a O-004 pede um provedor de embeddings antes de concluir a Etapa 4, mas a comparação de qualidade depende do corpus da Etapa 5 (O-011).  
**Opções consideradas:**
1. OpenAI `text-embedding-3-small`, pelo módulo OpenAI do Spring AI.
2. Voyage `voyage-code-3`, especializado em código.
3. Adiar a escolha e entregar a etapa só com um provedor fake.

**Decisão:** opção 1, provisória; a Voyage entra como candidata no bake-off.
- `app.embeddings.provider`: `none` (padrão), `fake` ou `openai`; `app.embeddings.model` (padrão `text-embedding-3-small`) e `app.embeddings.dimensions` (padrão 1536, entre 1 e 2.000).
- A chave vem de `EMBEDDING_API_KEY` no `.env`, obrigatória com `openai`; a aplicação não inicia sem ela. A chave nunca aparece em código, logs ou mensagens de erro.
- `app.embeddings.base-url` (padrão `https://api.openai.com/v1`) decide para onde vão a chave e os trechos de código. Só `https` é aceito; `http` apenas para `localhost` ou `127.0.0.1` (servidor de teste e servidores locais futuros). Endereço com usuário e senha embutidos também é recusado. Fora disso, a aplicação não inicia, e a mensagem de erro não repete o valor.
- Os testes do `./mvnw verify` usam `fake` e nunca precisam de chave; a chave real só entra no teste manual de fumaça. Uma `ContextCustomizerFactory` de teste, registrada em `META-INF/spring.factories`, impede qualquer contexto de teste de subir com `provider=openai`, inclusive contextos novos que esqueçam de fixar o provedor e herdem o valor do `.env`. A única exceção são classes com a tag JUnit `smoke` (o teste de fumaça, F6), que o Surefire e o Failsafe excluem do `verify`.
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
**Atualização (Etapa 5, 16/10/2026):**
- **Bake-off adiado** (P8 do plano da Etapa 5): o corpus inicial é pequeno para separar dois bons modelos, e o harness (D-062) roda o bake-off depois sem mudança de código, com um adaptador novo. A Voyage não foi verificada.
- **`spring-ai-openai` mantido** (P9): a escolha entre ele e o SDK sozinho passa para a Etapa 6, junto com a porta de geração, onde o `ChatClient` do Spring AI pode (ou não) se pagar. Resolvida na Etapa 6: removido, só o SDK (D-014).
- A guarda dos testes passou a liberar também a tag `eval`, do harness de avaliação (D-062).
- O teste de fumaça com a chave real continua pendente por falta de saldo.

**Atualização (Etapa 6, F1, 16/10/2026):** o adaptador deixou de usar o `OpenAiSetup` do Spring AI; o cliente vem do `OpenAiClients`, com o SDK sozinho (D-014). Comportamento igual: mesma chave, `base-url`, timeout, sem repetição e sem log. A guarda dos testes passou a se chamar `RealProviderGuard` e barra também `app.generation.provider=openai` (D-063).

**O que levaria à revisão:** resultado do bake-off, mudança de preço ou de política de dados, ou integração da Voyage no Spring AI.

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
**Status:** Aceita (16/10/2026; F4 e F5 entregues); revista pela D-060 (`mode`, `ranks`, F4 da Etapa 5)  
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
**Status:** Aceita (16/10/2026; F3 entregue, documentada no README e no `07` na F6)  
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
**Consequências:** modelos novos ou servidores compatíveis exigem acrescentar o limite oficial ao adaptador. `07-SECURITY-PRIVACY-COSTS.md` §7 e §9 e o README documentam o que é enviado, o custo e como desligar (`provider=none`).  
**O que levaria à revisão:** chunks recusados por tamanho no teste de fumaça ou no uso real (pediria tokenizador exato), ou provedores locais na Etapa 9 (pediria limite configurável).

### D-055 — Tokenização de identificadores (`ident-v1`)
**Status:** Aceita (16/10/2026; F1 da Etapa 5 entregue)  
**Contexto:** a D-017 manda separar identificadores na aplicação, porque o parser `simple` do PostgreSQL não quebra `getUserById`. A F0 da Etapa 5 mediu o parser (`ts_debug`, PostgreSQL 17.11): ele quebra em `_`, `$`, `::`, `->` e aspas; guarda como um lexema só caminhos (`src/foo/bar.java`), `a.b.c`, hosts (`my-file.ts`), URLs, e-mails e versões (`1.2.3`); e descarta como tag, sem lexema, tudo entre `<` e `>` que pareça tag (`<String>` em `List<String>`, `<T extends Number>`, `<b && c>` em `if (a<b && c>d)`, atributos HTML, `<!-- comentários -->`).  
**Decisão:** `IdentifierTokenizer`, em `codeanalysis`, puro e determinístico, com versão `ident-v1` (tokenizador e stopwords juntos; mudar qualquer um cria uma versão nova).
- **Palavra:** sequência de letras, dígitos e marcas combinantes (Unicode, `Character`). Todo o resto separa, inclusive `_` e `$`.
- **Partes:** fronteira de caixa (`getUser` → `get User`), de sigla (`HTTPClient` → `HTTP Client`) e entre letra e dígito (`parseJSON2Xml` → `parse JSON 2 Xml`).
- **`identifierTerms(texto)`** (o que o chunk acrescenta ao conteúdo, peso A): a palavra composta e suas partes; a palavra de uma parte encostada em outra sem espaço, com qualquer símbolo entre elas (`src/foo/Bar.java` → `src foo Bar java`, `user_id` → `user id`, `/api/v1?id=2` → `api v1 v 1 id 2`); e toda palavra entre `<` e o `>` seguinte. Palavras soltas de uma parte já estão no conteúdo e não entram.
- **`queryTerms(texto)`** (a pergunta): todas as palavras, compostas e partes, menos as stopwords (lista curta pt-BR e en, no código).
- Termos únicos sem diferenciar caixa, na ordem da primeira ocorrência, **com a grafia original**: quem põe em minúsculas é o PostgreSQL (`to_tsvector`/`to_tsquery`), porque as minúsculas do Java diferem das do banco (`İ`). O Java só compara em minúsculas (`Locale.ROOT`) para tirar repetidos e stopwords.
- Um teste de integração confere, em textos com código, URLs, HTML e Unicode, que cada termo vira exatamente um lexema e que todo termo de pergunta tirado do chunk aparece como lexema de `to_tsvector(content) || to_tsvector(identifier_terms)`.

**Motivo:** a pergunta que repete um termo do código casa com o chunk, qualquer que seja a forma como o parser leu esse trecho, e o peso A distingue identificadores da prosa.  
**Consequências:** a regra de "encostada" e a de `<…>` emitem a mais (por exemplo, numa comparação `a < b` sem `>` depois, o resto do chunk entra em `identifier_terms`); é inofensivo para o casamento e só desloca peso. Nomes de entidades HTML (`&amp;`) não entram no índice.  
**O que levaria à revisão:** resultados da avaliação (O-005), um caso em que o teste de alinhamento falhe, ou mudança do parser numa versão nova do PostgreSQL.

### D-056 — Armazenamento do índice lexical (revisa `05` §4)
**Status:** Aceita (16/10/2026; F2 da Etapa 5 entregue)  
**Contexto:** `05` §4 previa `identifier_terms` e `lexical_tsv` como colunas de `chunks`. Mas a tokenização é da aplicação (D-055), os chunks existentes não têm termos, uma migração SQL não consegue preenchê-los, e regravar chunks a cada versão do tokenizador mexeria em linhas "imutáveis" (D-039), inclusive de snapshots antigos.  
**Decisão:**
- **Tabela `chunk_lexical`** `(chunk_id, lexical_version)` (V9), FK para `chunks` com `ON DELETE CASCADE`, com `identifier_terms` (termos do `IdentifierTokenizer` separados por espaço) e `tsv = setweight(to_tsvector('simple', identifier_terms), 'A') || setweight(to_tsvector('simple', content), 'B')`, calculado no `INSERT` a partir do chunk, na mesma instrução. Índice GIN em `tsv`. Sempre `to_tsvector`: `array_to_tsvector` gera lexemas sem posição, e aí o `setweight` e o `ts_rank_cd` não funcionam (F0).
- **Etapa `LEXICAL` no job**, entre `PROCESSING_FILES` e `EMBEDDING`: lista os chunks do snapshot sem linha da versão atual, lê o texto por lote (`app.indexing.lexical-batch-chunks`, padrão 500) e grava com `ON CONFLICT DO NOTHING`; cada lote grava progresso e renova o heartbeat na mesma transação da inserção. O progresso conta chunks. Sem chamada externa, então sem política de falha nova: um erro segue a repetição do job (D-044), e a tentativa seguinte só grava o que falta.
- **`snapshots.lexical_version`**, gravada na ativação. Snapshots anteriores à V9 ficam com `null`.
- **`outdatedReasons`**: `LEXICAL_INDEX_MISSING` (snapshot sem versão lexical) e `LEXICAL_INDEX_CHANGED` (versão diferente da atual), independentes do provedor. Uma reindexação sem arquivo alterado resolve os dois. A recusa da busca lexical e híbrida nesses casos (`409 SNAPSHOT_LEXICAL_MISMATCH`) entrou com o `mode` na F4 (D-060).
- `GET /api/v1/snapshots/{id}` e a listagem trazem `lexical: { version }`, ou `null`.
- Versões convivem (útil para a avaliação comparar tokenizações, O-005); a limpeza vem pela cascata quando os chunks somem (D-039).
- Índice GIN `gin_trgm_ops` em `snapshot_files.relative_path` (V9), para a busca de caminhos da F3.

**Motivo:** o mesmo desenho dos embeddings (D-048, D-050): o índice é derivado e versionado à parte, chunks continuam imutáveis, e mudar o tokenizador não exige migração.  
**Consequências:** o texto do chunk fica três vezes no banco (conteúdo, chunk, `tsv`); o tamanho de `chunk_lexical` é medido no teste de desempenho da F4. Com `provider=none` a etapa roda normalmente.  
**Medição (F4):** com 50 mil chunks sintéticos de ~1,9 kB, `chunk_lexical` ocupa 211 MB de tabela e 51 MB de índices (`chunks`: 78 MB).  
**O que levaria à revisão:** tamanho ou tempo da etapa inaceitáveis na medição da F4, ou um índice lexical externo (O-005).

### D-057 — Consulta lexical
**Status:** Aceita (16/10/2026; F3 da Etapa 5 entregue)  
**Contexto:** as perguntas são em linguagem natural; `plainto_tsquery` exige todos os termos (AND) e quase nunca casa, a configuração `simple` não tem stopwords, e o `ts_rank` não tem IDF (D-017).  
**Decisão:**
- `LexicalSearch`, em `retrieval`. A pergunta passa por `IdentifierTokenizer.queryTerms` (D-055); cada termo vai entre aspas simples (aspas internas dobradas), unido aos demais por ` | `, e o texto inteiro é **um parâmetro** de `to_tsquery('simple', :query)`. Nada da pergunta é concatenado ao SQL, e operadores da `tsquery` (`&`, `|`, `!`, `:`, `(`, `<->`, `*`) nunca chegam a ela, porque os termos só têm letras e dígitos. Pergunta sem termos (só stopwords ou pontuação) devolve lista vazia sem consultar o banco.
- Só as linhas de `chunk_lexical` da versão atual contam. Ordem: `ts_rank_cd(tsv, query, 1)` decrescente (pesos padrão A=1,0 e B=0,4; normalização pelo log do comprimento), depois primeiro caminho, linha inicial e id.
- Mesmo formato de resultado da vetorial (`SearchHit`: um resultado por chunk, todos os caminhos do snapshot com o mesmo conteúdo, em ordem de bytes). Os candidatos do snapshot e o `SELECT` final com os caminhos ficaram num trecho compartilhado (`SnapshotChunks`), usado pelos três canais.
- **Plano medido** (100 projetos × 2.000 chunks, `LexicalSearchPlanIT`): o PostgreSQL parte do snapshot (PK de `snapshot_files`), chega aos chunks pela chave natural e a `chunk_lexical` pela PK, e aplica o `@@` linha a linha; o GIN em `tsv` não é escolhido nesse volume. É o mesmo desenho exato por snapshot da vetorial (D-049): o custo cresce com o snapshot, não com o banco. O teste garante a ausência de varredura sequencial em `chunk_lexical`, `chunks` e `snapshot_files`.

**Motivo:** OR com ranking por densidade devolve resultados mesmo quando só parte da pergunta aparece no código, e a montagem na aplicação mantém o tokenizador da consulta igual ao da indexação.  
**Consequências:** sem IDF, termos comuns pesam tanto quanto raros; o peso A favorece identificadores (coberto por teste). A recusa por versão lexical (`409 SNAPSHOT_LEXICAL_MISMATCH`) entrou na F4 (D-060).  
**O que levaria à revisão:** resultados da avaliação (O-005), em especial perguntas em pt-BR sobre código em inglês, ou latência alta em snapshots grandes (o GIN em `tsv` continua disponível para o planejador).

### D-058 — Canal de caminhos
**Status:** Aceita (16/10/2026; F3 da Etapa 5 entregue; o canal pode sair na avaliação)  
**Contexto:** perguntas que citam um arquivo (`VectorSearch.java`, `docs/plans/etapa-4.md`, `IndexJob`) devem trazer esse arquivo, o que o full-text não garante (o parser guarda caminhos como um lexema só).  
**Decisão:**
- `PathTerms`, em `codeanalysis`, separa da pergunta (por espaço em branco, sem aspas e pontuação nas pontas, sem `./` ou `/` no início): **caminhos** (contêm `/` e alguma letra ou dígito, ou terminam em `.<extensão>` com extensão começando por letra, o que deixa `1.2.3` de fora) e **nomes** (letras, dígitos, `_` e `-`, ao menos 3 caracteres, sem stopwords). Um nome só vira termo se algum arquivo do snapshot tiver esse nome sem diretório e sem a última extensão, sem diferenciar caixa (`IndexJob` → `IndexJob.java`; `search` não casa com `SearchApiIT.java`).
- `PathSearch`, em `retrieval`: arquivos do snapshot com `term <<% relative_path` (pré-filtro do `pg_trgm`, limiar padrão 0,5) e `strict_word_similarity(term, relative_path) >= :threshold`, com o limiar como parâmetro (padrão **0,8**; abaixo de 0,5 é recusado, porque o pré-filtro cortaria antes). Score do arquivo = maior similaridade entre os termos; resultados = chunks desses arquivos, por score, caminho casado e linha inicial. Um chunk de conteúdo repetido em dois arquivos casados aparece uma vez, com todos os caminhos.
- Os termos vão ao banco como um parâmetro só, um por linha (`string_to_array`), porque nunca contêm espaço em branco.
- Pergunta sem caminho nem nome de arquivo do snapshot → lista vazia; na fusão (F4), o canal só entra quando tem resultados.
- **Plano medido:** com 200 arquivos por snapshot, o planejador lê os arquivos do snapshot pela PK e calcula a similaridade em cada um, sem usar o índice trigram (que cobre os caminhos de todos os snapshots). O trigram fica para snapshots grandes; o teste garante só a ausência de varredura sequencial.

**Motivo:** `strict_word_similarity` separa o arquivo citado de vizinhos com nome parecido (F0: `IndexJobApiIT.java` 0,533 para `IndexJob`).  
**Consequências:** caminhos longos que diferem num só trecho passam do limiar: `docs/plans/etapa-3.md` casa com `docs/plans/etapa-4.md`, abaixo do arquivo exato (1,0). Fica para a avaliação decidir o limiar ou se o canal permanece.  
**O que levaria à revisão:** ganho nulo do canal na avaliação (sai antes do fechamento da etapa), ou limiar diferente medido no corpus.

### D-059 — Fusão híbrida por RRF
**Status:** Aceita (16/10/2026; F4 da Etapa 5 entregue)  
**Contexto:** a D-017 escolheu RRF com k=60 para combinar a busca lexical e a vetorial; a F3 acrescentou o canal de caminhos (D-058).  
**Decisão:**
- `RrfFusion`, em `retrieval`, pura: recebe a lista de cada canal (`LEXICAL`, `VECTOR`, `PATH`) e devolve `limit` resultados com `score = Σ peso / (k + rank)`, rank a partir de 1, somado nos canais que trouxeram o chunk. Padrão: k=60 e peso 1 em todos os canais.
- Um chunk é identificado pelo id; os caminhos e o conteúdo vêm de qualquer canal (são iguais em todos).
- Desempate determinístico: maior score, melhor rank individual, primeiro caminho, linha inicial e id do chunk (como texto, a ordem do banco).
- Canal com lista vazia não soma nada; o de caminhos só participa quando a pergunta cita um arquivo do snapshot (D-058).
- No modo `HYBRID`, cada canal busca `max(app.retrieval.candidates, limit)` resultados (padrão **50**) antes de fundir. Os canais são consultados em sequência (vetorial, lexical, caminhos), sem paralelismo. **Revisto pela D-080 (17/10/2026):** o lexical e o de caminhos rodam em paralelo com o embedding da pergunta e o vetorial.
- `k`, pesos e profundidade são parâmetros da função, para a avaliação (F6) variá-los sobre as mesmas listas, sem consultar o banco de novo.

**Motivo:** RRF dispensa normalizar scores de naturezas diferentes (cosseno, `ts_rank_cd`, similaridade de trigramas) e premia o que aparece bem em mais de um canal.  
**Consequências:** o `score` híbrido só serve para ordenar uma resposta (vai no máximo a 3/61 com pesos 1). A latência híbrida é a soma dos três canais mais a chamada ao provedor: com 50 mil chunks, 854 ms de média e 1,2 s de p95 sem o provedor, dominados pelo lexical quando a pergunta tem um termo presente em quase todo chunk (471 ms, contra 52 ms só com termos raros; detalhes em `docs/plans/etapa-5.md`, F4).  
**O que levaria à revisão:** resultados da avaliação (k, pesos, profundidade, saída do canal de caminhos), ou latência que peça consultas em paralelo.

### D-060 — Contrato da busca com `mode` (revisa a D-053)
**Status:** Aceita (16/10/2026; F4 da Etapa 5 entregue)  
**Decisão:**
- **Pedido:** `{ "query", "snapshotId", "limit", "mode" }`, com `mode` `LEXICAL`, `VECTOR` ou `HYBRID` (nome exato); ausente ou `null` = **`HYBRID`**. Valor desconhecido → `400 VALIDATION_FAILED` no campo `mode` (o campo é texto no pedido, para não virar `MALFORMED_REQUEST`).
- **Resposta:** `{ snapshotId, mode, model | null, lexicalVersion | null, results: [ { chunkId, path, otherPaths, startLine, endLine, content, score, ranks: { lexical, vector, path } } ] }`. `model` é `null` no `LEXICAL`; `lexicalVersion` é `null` no `VECTOR`. `score` depende do modo (cosseno no `VECTOR`, `ts_rank_cd` no `LEXICAL`, RRF no `HYBRID`). `ranks` traz a posição (a partir de 1) em cada canal consultado, ou `null`; nos modos de um canal só, só esse canal tem rank. O canal de caminhos só roda no `HYBRID`.
- **`LEXICAL`** funciona com `provider=none` e nunca chama o provedor.
- **`HYBRID` sem provedor ou sem vetores do modelo:** `409`, sem cair para lexical em silêncio (um resultado pior sem aviso é difícil de perceber). A mensagem de `EMBEDDINGS_NOT_AVAILABLE` sugere o modo `LEXICAL`.
- **Erros, nesta ordem:** `400 VALIDATION_FAILED` (`query`, `limit`, `mode`); `404 PROJECT_NOT_FOUND`; `404 SNAPSHOT_NOT_FOUND`; `409 SNAPSHOT_NOT_SEARCHABLE`; `409 SNAPSHOT_LEXICAL_MISMATCH` (`LEXICAL` e `HYBRID`: snapshot sem `lexical_version` ou com outra versão, D-056); `409 EMBEDDINGS_NOT_AVAILABLE` e `409 SNAPSHOT_EMBEDDING_MISMATCH` (`VECTOR` e `HYBRID`); erros do provedor (D-051).
- **Log:** modo, snapshot, quantidade total e por canal, e duração por canal; nunca a pergunta (D-054).
- O estado do snapshot usado nessas verificações saiu da `VectorSearch` para `SearchSnapshots`.

**Motivo:** a Etapa 6 usa o híbrido e precisa dos ranks por canal (`rank_lexical`/`rank_vector` de `answer_evidence`); a busca lexical continua disponível sem provedor.  
**Consequências:** o padrão mudou de vetorial para híbrido; quem quiser só a vetorial passa `mode: VECTOR`. `06-API-CONTRACTS.md` é atualizado no fechamento da etapa (F7).  
**O que levaria à revisão:** a Etapa 6 (evidências da resposta) ou a avaliação (por exemplo, tirar o canal de caminhos).

### D-061 — Corpus de avaliação (P7 da Etapa 5)
**Status:** Aceita (16/10/2026; F5 da Etapa 5 entregue; anotações aprovadas)  
**Contexto:** a O-011 pedia repositórios, quem anota e como versionar o corpus. A avaliação da Etapa 5 precisa dele para comparar lexical, vetorial e híbrido.  
**Decisão:**
- **Três corpora:** fixture sintética versionada (`eval/corpus/fixture/`); o próprio Codebase Copilot no commit `9d986db` (merge da Etapa 4); `pallets/click` 8.5.0 (`8b19813`, BSD-3-Clause). Os dois externos ficam fora do repositório: você os extrai e informa a pasta em `EVAL_CORPUS_CODEBASE_COPILOT` e `EVAL_CORPUS_CLICK`. Nada do corpus é executado.
- **Manifesto** por corpus (`eval/corpus/<corpus>.sha256`): uma linha por arquivo, com SHA-256, quantidade de linhas (`-` quando não é texto) e caminho. Pasta diferente do manifesto (arquivo alterado, faltando ou a mais) faz o harness parar. As linhas são contadas com as regras do `TextClassifier`, o que permite validar as anotações dos corpora externos sem a pasta.
- **Anotações** em `eval/questions/<corpus>.yaml`: `id`, `question`, `language` (`pt-BR`, `en`), `category` (`08` §2), `difficulty`, `expected` (`path`, `startLine`, `endLine`, `otherPaths`), `mustAbstain`, `notes`. Chave desconhecida é erro. Relevância por intervalo de linhas: um chunk é relevante se sobrepõe um intervalo, em `path` ou em um dos `otherPaths` (mesmo conteúdo em outro caminho).
- **40 perguntas** (14 na fixture, 14 no Copilot, 12 no click), metade em pt-BR e metade em en, com 4 que exigem abstenção (fora das métricas de recuperação).
- Rascunho das anotações feito pelo assistente lendo o código; revisão e aprovação por você (`08` §4), em 16/10/2026.
- O `verify` valida as anotações dos três corpora contra os manifestos e confere a fixture contra o seu manifesto. O código fica nas fontes de teste (`com.codebasecopilot.eval`), sem nada na aplicação.

**Motivo:** a relevância por linha mantém o corpus válido quando o chunking muda (O-010), e o manifesto garante que todos medem o mesmo conteúdo.  
**Consequências:** alterar um arquivo da fixture exige gerar de novo o manifesto (`eval/README.md`) e revisar as anotações que apontam para ele.  
**O que levaria à revisão:** corpus pequeno demais para separar as variantes na avaliação, ou anotações que se mostrem ambíguas na revisão.

### D-062 — Harness de avaliação de recuperação (P8 da Etapa 5)
**Status:** Aceita (16/10/2026; F6 da Etapa 5 entregue; baseline vetorial e híbrido pendente de saldo no provedor)  
**Contexto:** o aceite da Etapa 5 pede resultados reprodutíveis e o híbrido comparado com os canais individuais, no corpus da D-061.  
**Decisão:**
- **`RetrievalEvaluationIT`**, tag `eval`, fora do `verify` (como `performance` e `smoke`). Indexa cada corpus pelo pipeline real (Testcontainers, pasta local copiada para uma raiz de teste), consulta cada canal uma vez por pergunta (`candidates` = 50) e calcula as variantes em memória com a `RrfFusion` (D-059): `LEXICAL`, `VECTOR`, `LEXICAL_PATH`, `LEXICAL_VECTOR` e `HYBRID` (os três canais, como o modo `HYBRID` da D-060). Sem provedor, só as variantes sem vetor.
- **Provedor:** `EVAL_PROVIDER` (`none` por padrão, que não envia nada; `fake` para testar o harness; `openai` para o baseline real, com a chave de `EMBEDDING_API_KEY`). A guarda dos testes (D-047) libera o provedor real só para as tags `smoke` e `eval`.
- **Cache de vetores:** todo vetor devolvido pelo provedor fica em `DATA_DIR/eval-cache/<provedor>-<modelo>-<dimensões>/<sha256 do texto>.f32` (ou em `EVAL_CACHE_DIR`), para chunks e perguntas. Guarda só hash e vetor, nunca o texto. Uma segunda rodada não chama o provedor nem sofre a variação dele.
- **Métricas** (`RetrievalMetrics`, pura, com testes no `verify`): Recall@5/10/20 por intervalo esperado, MRR@10, Precision@10 (dividido por 10 mesmo com menos resultados) e cobertura de arquivos@10 nas perguntas `CROSS_FILE_FLOW` (qualquer chunk do arquivo conta; `otherPaths` é o mesmo arquivo). Perguntas com `mustAbstain` ficam de fora. Resumo por variante, corpus, categoria e idioma; latência média e p95 por canal (inclusive a chamada de embedding da pergunta).
- **Resultados** em `eval/results/<data>-<config>.json` (`lexical` sem provedor, `<provedor>-<modelo>` com) e um resumo `.md` em pt-BR, versionados. O JSON registra corpus (commit, SHA-256 do manifesto, arquivos e chunks), versões de chunking e lexical, modelo, parâmetros da fusão, as métricas de cada pergunta e os 10 primeiros chunks de cada variante (caminho e linhas, sem conteúdo).
- **Reprodutibilidade:** ordem determinística em todos os canais (D-057 a D-059) e vetores em cache. `EVAL_COMPARE_WITH=<json>` faz a rodada falhar se o resultado, fora data e latências, diferir de um anterior.

**Motivo:** buscar as listas uma vez e fundir em memória permite variar k, pesos e canais sem consultar o banco de novo; o cache torna o baseline pago reprodutível e barato de repetir.  
**Consequências:** o baseline lexical (sem custo) está em `eval/results/2026-10-16-lexical.*`; o vetorial e o híbrido saem do mesmo harness com `EVAL_PROVIDER=openai`, quando houver saldo, junto com o teste de fumaça da Etapa 4. A Etapa 5 fechou sem saldo: a comparação e as decisões que dependem dela (D-017, O-005, saída do canal de caminhos da D-058) esperam essa rodada.  
**O que levaria à revisão:** corpus maior, bake-off de embeddings (O-004) ou variantes novas da fusão.

### D-063 — Provedor de chat (O-004, provisória)
**Status:** Aceita (provisória, 16/10/2026; P1 da Etapa 6; F1 entregue)  
**Contexto:** a O-004 deixou o provedor de chat para a Etapa 6. A escolha decide custo e para onde vão a pergunta e os trechos de código usados como evidência.  
**Opções consideradas:**
1. OpenAI, pelo SDK já usado nos embeddings (D-047).
2. Anthropic (Claude), pelo SDK oficial ou pelo Spring AI: dependência, chave e destino de dados novos.
3. Entregar a etapa só com o provedor fake.

**Decisão:** opção 1, provisória; a Anthropic fica como candidata na avaliação de respostas.
- **Modelo padrão `gpt-6-luna`**, com o `gpt-6.1-sol` como comparação na avaliação. Pelas páginas oficiais (consultadas em 16/10/2026): `gpt-6-luna` custa US$ 0,10 por 1M de tokens de entrada, US$ 0,01 em cache e US$ 0,50 de saída (até 272 mil tokens de entrada); `gpt-6.1-sol`, US$ 2,00, US$ 0,10 e US$ 10,00. Os dois têm contexto de 1,05 M de tokens, saída máxima de 128 mil e saída estruturada; o luna aceita raciocínio de `none` a `max`, o sol de `low` a `max`. Fontes: `developers.openai.com/api/docs/pricing`, `/api/docs/models/gpt-6-luna`, `/api/docs/models/gpt-6.1-sol`.
- **Chat Completions, com `store=false`.** Pela página de uso de dados (`developers.openai.com/api/docs/guides/your-data`, consultada em 16/10/2026), `/v1/chat/completions` não usa os dados para treino, retém por 30 dias para monitoramento de abuso, não guarda estado da aplicação (o cache de prompts pode manter dados criptografados nas GPUs por até 24 horas) e é elegível a Zero Data Retention mediante acordo. O `/v1/responses` guardaria a resposta por pelo menos 30 dias com `store` omitido, e é a Chat Completions que servidores compatíveis e locais implementam (Etapa 9).
- **Saída estruturada estrita** (`response_format` `json_schema`, `strict: true`): todo campo em `required`, `additionalProperties: false` e opcional como união com `null` (guia de structured outputs, consultado em 16/10/2026).
- **Configuração própria** (`app.generation.*`), separada dos embeddings: `provider` (`none` padrão, `fake`, `openai`), `model`, `reasoning-effort` (padrão `low`; vazio não envia o parâmetro), `max-output-tokens` (padrão 2.000, inclui o raciocínio), `timeout` (60 s), `base-url` (mesma regra da D-047) e a chave em `GENERATION_API_KEY`, obrigatória com `openai`. A chave dos embeddings não serve à geração: são destinos configurados à parte, mesmo que o valor seja igual.
- Com `provider=none` não há bean de geração e nada sai da máquina.
- A guarda dos testes (`RealProviderGuard`) barra também `app.generation.provider=openai`, com a mesma exceção para as tags `smoke` e `eval`; o teste de fumaça é o `GenerationSmokeIT`.

**Motivo:** reaproveita o SDK, a guarda de `base-url`, a classificação de erros (D-051) e a mesma conta dos embeddings; o luna cobre a tarefa (responder com trechos dados, ~6 mil tokens de contexto) por fração de centavo por pergunta.  
**Consequências:** com `provider=openai`, a pergunta e os trechos escolhidos como evidência saem da máquina. Custo estimado por pergunta com o luna: ~US$ 0,0017 no teto (6.700 tokens de entrada e 2.000 de saída). Os dois modelos têm um único snapshot, sem versão datada para fixar; uma mudança do modelo aparece na avaliação.  
**O que levaria à revisão:** resultado da avaliação de respostas (luna × sol, ou Anthropic), mudança de preço ou de política de dados, ou provedor local (Etapa 9).

### D-064 — Porta de geração
**Status:** Aceita (16/10/2026; P3 da Etapa 6; F1 entregue)  
**Decisão:**
- `GenerationProvider` em `providers`: `generate(GenerationRequest)` → `GenerationResult`, e `model()` (provedor e modelo). Sem tipos do SDK na assinatura.
- `GenerationRequest`: instruções (mensagem de sistema), entrada (mensagem do usuário) e o schema da saída (`OutputSchema`: nome e JSON Schema estrito como mapa). O limite de saída e o esforço de raciocínio vêm da configuração, não do pedido.
- `GenerationResult`: o JSON produzido, ainda não validado, e os tokens de entrada, de saída e de raciocínio, cada um `null` quando o provedor não informa (D-054). Os de saída já incluem os de raciocínio.
- `GenerationFailure` com os tipos da D-051 e mais `OUTPUT_TRUNCATED` (`finish_reason` `length`) e `REFUSED` (`message.refusal` presente ou `finish_reason` `content_filter`); resposta sem `choices` ou sem conteúdo é `INVALID_REQUEST`. Mensagens só com tipo e status HTTP; a exceção do SDK não é guardada como causa.
- **Sem repetição:** uma chamada por pedido, porque há uma pessoa esperando (como na busca, D-051). Timeout por chamada, que vira `UNAVAILABLE`.
- `FakeGenerationProvider` determinístico (D-019): cita o primeiro rótulo de evidência (`E1`, `E2`…) que encontra na entrada, no formato de resposta da Etapa 6, ou se abstém quando não há nenhum; tokens = contagem de termos.
- Nada de instruções, pergunta, trecho ou saída em log: o adaptador registra só tokens e duração, e um teste numa JVM filha com `OPENAI_LOG=debug` confere que nenhum corpo de pedido ou de resposta aparece.

**Motivo:** o domínio fica livre do SDK e o comportamento de falha é previsível e testável sem rede.  
**Consequências:** o formato de resposta que o fake produz precisa acompanhar o schema da P6; se o schema mudar, o fake muda junto.  
**O que levaria à revisão:** necessidade de streaming (Etapa 7), de várias mensagens (perguntas de acompanhamento) ou de ferramentas.

### D-065 — Histórico de perguntas e respostas
**Status:** Aceita (16/10/2026; P7 e parte da P8 da Etapa 6; F2 entregue)  
**Contexto:** as respostas precisam guardar a pergunta, o texto, as evidências enviadas ao modelo e as métricas, sobreviver à poda de snapshots (D-046) e não segurar transação durante a chamada ao provedor.  
**Decisão:**
- Migração V10 com `conversations`, `messages`, `answers` e `answer_evidence`, no módulo `history`, que `answering` usa pela API pública `AnswerHistory` (`start`, `complete`, `findAnswer`, `failPendingCreatedBefore`).
- Ajustes em relação a `05-DATA-MODEL.md`: `conversations` sem `snapshot_id` (cada resposta guarda o seu) e com `title` = a primeira pergunta numa linha, até 100 caracteres; `messages` só com `USER` e `ASSISTANT`; `answers` com `PENDING`, `snapshot_id`, `retrieval_mode`, `prompt_version`, `generation_provider`/`generation_model`, `error_code`, `warnings`, `invalid_citation_count` e `retrieval_metadata` (nunca texto); `answer_evidence` com `id`, `rank_path`, `cited_start_line`/`cited_end_line` e `validation_status` (`CITED`, `NOT_CITED`, `INVALID_RANGE`), guardando todas as evidências enviadas ao modelo, citadas ou não.
- Snapshot e chunk referenciados sem FK (D-046); tudo apagado em cascata a partir do projeto (D-021).
- `CHECK`s de estado: `PENDING` sem conclusão, erro, mensagem de resposta, latência ou tokens e com custo `UNKNOWN`; `FAILED` com erro e sem mensagem de resposta; `ANSWERED` e `PARTIAL` com mensagem de resposta; `ABSTAINED` com ou sem texto (a abstenção sem evidência não chama o modelo). `ESTIMATED` exige custo e moeda, `UNKNOWN` proíbe os dois. Linhas citadas só em evidência `CITED`, as duas juntas e dentro do intervalo da evidência.
- Escrita em duas transações curtas: `start` cria a conversa (ou valida que ela é do projeto, senão `CONVERSATION_NOT_FOUND`), a mensagem `USER` e a resposta `PENDING`; `complete` trava a resposta e só a conclui se ainda estiver `PENDING`, gravando a mensagem `ASSISTANT` (quando há texto) e as evidências. Uma resposta já recuperada ou apagada com o projeto devolve `false`, sem erro.
- **Recuperação:** na inicialização, `AnswerRecovery` (em `answering`) marca `FAILED` com `ANSWER_INTERRUPTED` as respostas `PENDING` criadas há mais que o timeout de embeddings somado ao de geração, o máximo que um pedido espera pelos provedores. As mais recentes ficam, porque podem ser de outra instância em execução.

**Motivo:** o histórico (F-07) continua verificável depois da poda, e os `CHECK`s impedem estados que a API não saberia mostrar.  
**Consequências:** `05-DATA-MODEL.md` é atualizado no fechamento da etapa. O texto da pergunta e da resposta fica só no banco local, nunca em log.  
**O que levaria à revisão:** perguntas de acompanhamento com histórico no prompt (Etapa 8), retenção de conversas fora da remoção do projeto, ou volume de evidências copiadas relevante (D-046).

### D-066 — Contexto, prompt e validação de citações
**Status:** Aceita (16/10/2026; P4, P5 e P6 da Etapa 6; F3 entregue)  
**Contexto:** a busca devolve até 50 chunks; o modelo precisa de poucos, delimitados como dado não confiável, e a resposta só vale com citações que existem (D-011).  
**Decisão:**
- **Recuperação** pela fachada pública `Retrieval` de `retrieval`, em duas fases: `prepare` faz as checagens da busca, com os mesmos códigos e a mesma precedência (D-060), sem chamar provedor; `retrieve` gera o embedding da pergunta, se o modo pedir, e só falha com um código `EMBEDDING_*`. Cada chunk vem com conteúdo, hash do conteúdo, estimativa de tokens, posição final e posição em cada canal. Padrão `HYBRID` com `app.answering.candidates` = 20 (de 1 a 50); sem cair para lexical em silêncio.
- **`ContextAssembler`**, puro, percorre os chunks na ordem da fusão e escolhe cada um que ainda cabe: até `maxEvidence` (pedido, padrão 8, de 1 a 20), até `app.answering.max-evidence-per-file` = 3 por conteúdo, sem sobrepor linhas de um chunk já escolhido do mesmo conteúdo, e dentro de `app.answering.max-context-tokens` = 6.000. O custo é ⌈caracteres/4⌉ (D-043) medido no prompt renderizado (instruções, pergunta, delimitadores e números de linha); um chunk que não cabe é pulado e os seguintes ainda são tentados. Rótulos `E1`…`En` na ordem de escolha. Sem corte por score (D-059).
- **`PromptBuilder`** (`prompt-v1`): instruções em inglês na mensagem de sistema (só as evidências, citar com `[En]`, conteúdo como dado e nunca como instrução, `ABSTAINED`/`PARTIAL`, idioma da pergunta). Na mensagem do usuário, as evidências e depois a pergunta, cada uma num bloco `<<<BEGIN … nonce>>>`/`<<<END … nonce>>>` com um nonce aleatório de 128 bits por pedido; cada evidência traz rótulo, primeiro caminho, intervalo e as linhas numeradas, para o modelo poder estreitar a citação.
- **Schema estrito** `{status, answer, citations[{evidence, startLine, endLine}]}`, com as linhas como `["integer", "null"]`. O rótulo é texto livre no schema (não um `enum` por pedido), para o schema não mudar a cada pergunta; o validador cuida dos inventados.
- **`CitationValidator`**, puro: JSON inválido ou fora do schema (inclusive `ANSWERED`/`PARTIAL` com texto vazio) → inválido, sem texto exibido; rótulo fora do conjunto → descartado; linhas fora do intervalo ou invertidas → descartadas e a evidência fica `INVALID_RANGE`; uma linha nula vale o limite da evidência, as duas nulas citam a evidência inteira; várias citações válidas da mesma evidência viram o intervalo que cobre todas; marcador `[En]` sem citação válida sai do texto; citação sem marcador vale. `invalidCitationCount` = citações descartadas + rótulos marcados no texto que não aparecem nas citações.
- **Status final:** `ABSTAINED` declarado fica; `ANSWERED` ou `PARTIAL` sem citação válida vira `PARTIAL` com `NO_VALID_CITATIONS`; senão, o declarado. `INVALID_CITATIONS_REMOVED` quando algo foi descartado.

**Motivo:** o orçamento medido no prompt real evita estourar o contexto com numeração e delimitadores; o nonce impede que um arquivo feche o próprio bloco; o status decidido no backend deixa claro quando nada sustenta a resposta.  
**Consequências:** a validação garante existência de arquivo e linhas, não suporte factual, medido na avaliação (F6). Mudar instruções, blocos ou schema cria uma versão nova do prompt.  
**O que levaria à revisão:** resultados da avaliação de respostas (orçamento, limite por arquivo, abstenção), perguntas de acompanhamento (Etapa 8) ou reranking.

### D-067 — Caso de uso de perguntas e custo por resposta
**Status:** Aceita (16/10/2026; P8 e P9 da Etapa 6; F4 entregue)  
**Contexto:** a pergunta passa por dois provedores e pode levar o tempo da geração; o histórico não pode perder a pergunta quando um provedor falha, e nada pode ficar gravado quando o pedido é recusado.  
**Decisão:**
- **`POST /projects/{projectId}/questions`**, síncrono: `{ question, snapshotId, conversationId, options: { maxEvidence, retrievalMode } }`. `question` de 1 a 2.000 caracteres, não em branco; `maxEvidence` de 1 a 20 (padrão 8); `retrievalMode` `LEXICAL`, `VECTOR` ou `HYBRID` (nome exato, padrão `HYBRID`, texto no pedido como na D-060). Resposta `201` com `Location: /api/v1/answers/{answerId}`.
- **Erros, nesta ordem, sem nada gravado:** `400 VALIDATION_FAILED` (`question`, `options.maxEvidence`, `options.retrievalMode`); `404 PROJECT_NOT_FOUND`; `404 CONVERSATION_NOT_FOUND` (inexistente ou de outro projeto); as checagens do snapshot e dos embeddings da busca, na ordem da D-060 (`SNAPSHOT_NOT_FOUND`, `SNAPSHOT_NOT_SEARCHABLE`, `SNAPSHOT_LEXICAL_MISMATCH`, `EMBEDDINGS_NOT_AVAILABLE`, `SNAPSHOT_EMBEDDING_MISMATCH`); `409 GENERATION_NOT_AVAILABLE`. Os 404 vêm antes dos 409, e a geração por último, por ser a última peça usada.
- **Fluxo:** checagens; transação curta com conversa, mensagem `USER` e resposta `PENDING`; busca, montagem, geração e validação fora de transação; transação curta com o desfecho. Depois da primeira transação, falhas dos provedores **não viram erro HTTP**: a resposta fica `FAILED` com `error.code` (`GENERATION_*` ou `EMBEDDING_*`) e o pedido responde `201`. Mensagens pt-BR em `answer.error.<CODE>`.
- **Sem evidência, sem chamada:** busca vazia, ou nada que caiba no contexto, vira `ABSTAINED` com o aviso `NO_EVIDENCE`, sem texto gravado; o texto exibido é a mensagem `answer.abstained`.
- **Evidências gravadas** sempre que o modelo foi chamado, inclusive numa falha da geração ou numa saída inválida (todas `NOT_CITED`), porque foram enviadas ao provedor; numa falha do embedding da pergunta não há evidência.
- **Projeto removido durante a geração:** a resposta já não existe para ser concluída, e o pedido responde `404 PROJECT_NOT_FOUND`.
- **Resposta:** `answerId`, `conversationId`, `snapshotId`, `question`, `status`, `answer` (texto com `[En]`, ou `null`), `warnings` (`{code, message}`), `invalidCitationCount`, `error` (`{code, message}` só em `FAILED`), `evidence` (todas as enviadas ao modelo, com `id`, rótulo, caminhos, linhas, linhas citadas, cópia do trecho, `validationStatus`, `rank` e `ranks` por canal), `model` (`null` quando o modelo não foi chamado), `retrieval` (`mode`, `promptVersion`), `metrics` (`latencyMs`, `retrievalMs`, `generationMs`, tokens, custo) e as datas.
- **Custo (P9):** preço em `app.generation.pricing.input-per-million` e `output-per-million` (US$), sem padrão no código; o `.env.example` traz os valores do `gpt-6-luna` com a data da consulta. `ESTIMATED` só com os dois tokens informados e os dois preços configurados, arredondado a 6 casas; senão `UNKNOWN`, sem custo nem moeda. Os tokens do embedding da pergunta ficam em `retrieval_metadata`, fora do custo.
- **`retrieval_metadata`:** modelo de embedding, versão lexical, tokens do embedding da pergunta, candidatos por canal, chunks recuperados, tokens de raciocínio e duração por etapa (`retrieval`, `questionEmbedding`, `generation`, `validation`). Nunca texto.
- **Log:** uma linha por resposta com resposta, projeto, snapshot, modo, status e código, quantidade de evidências, tokens e durações; nunca pergunta, resposta, prompt ou trecho (D-054). A linha da busca passou a separar a duração do embedding da pergunta.

**Motivo:** o histórico guarda toda pergunta que passou pelas checagens (F-07, "erros de provedor são comunicados sem perder o histórico da pergunta"), e um pedido recusado não deixa conversa vazia.  
**Consequências:** o cliente espera até o timeout da geração (60 s por padrão); um erro inesperado depois da primeira transação (banco fora do ar, por exemplo) deixa a resposta `PENDING` até a recuperação da próxima inicialização (D-065). O `Location` aponta para `GET /answers/{answerId}`, entregue na F5.  
**O que levaria à revisão:** streaming na UI (Etapa 7), perguntas de acompanhamento (Etapa 8) ou um provedor que informe o custo.

### D-068 — Leituras do histórico
**Status:** Aceita (16/10/2026; leituras da P8 da Etapa 6; F5 entregue)  
**Contexto:** o histórico (F-07) e as evidências precisam ser consultados depois da resposta, inclusive quando o snapshot já foi podado (D-046), e as respostas `PENDING` e `FAILED` também fazem parte dele.  
**Decisão:**
- **`GET /answers/{answerId}`**: o mesmo formato da resposta de `POST /questions` (D-067), inclusive `PENDING`; `404 ANSWER_NOT_FOUND`.
- **`GET /projects/{projectId}/evidence/{evidenceId}`**: a cópia guardada (caminho, demais caminhos, hash do conteúdo, linhas, linhas citadas, trecho, `validationStatus`, posições), com `answerId`, `snapshotId` e `snapshotStatus` `AVAILABLE` ou `PRUNED`. Snapshot podado ou inexistente → `PRUNED`, com `200`. Erros, nesta ordem: `404 PROJECT_NOT_FOUND`; `404 EVIDENCE_NOT_FOUND` (inexistente ou de outro projeto).
- **`GET /projects/{projectId}/conversations`**: paginada (D-030), pela pergunta mais recente (`updated_at` decrescente, `id` decrescente no empate); cada item com `id`, `projectId`, `title`, `questionCount` e as datas. `404 PROJECT_NOT_FOUND`.
- **`GET /conversations/{conversationId}`**: `{ conversation, answers }`, com as respostas na ordem das perguntas e no formato de `GET /answers/{answerId}`, inclusive `FAILED` e `PENDING`; evidências lidas numa consulta só para a conversa inteira. `404 CONVERSATION_NOT_FOUND`.
- **Módulos:** as consultas ficam em `history` (`AnswerHistory.findEvidence`, `listConversations`, `findConversation`); os endpoints em `answering` (`AnswerController` e `ConversationController`, tag "Conversas"), que já montam a resposta exibida (texto da abstenção, avisos e erros em pt-BR, em `AnswerResponses`). O estado do snapshot vem de `Retrieval.snapshotAvailable`, chamado por `answering`; sem porta em `history`.

**Motivo:** a porta `SnapshotStates` da P11 seria implementada em `retrieval`, que pela mesma P11 não pode depender de `history`; como as leituras compõem `history` e `retrieval` em `answering`, que já depende dos dois, a porta não é necessária.  
**Consequências:** `06-API-CONTRACTS.md` §8 a §10 atualizados no fechamento da etapa.  
**O que levaria à revisão:** busca ou filtros no histórico, conversas longas o bastante para pedir paginação das respostas, ou a abertura do arquivo inteiro do snapshot (`GET /snapshots/{snapshotId}/files/content`).

### D-069 — Avaliação de respostas (P10 da Etapa 6)
**Status:** Aceita (16/10/2026; F6 da Etapa 6 entregue; rodada com o modelo real pendente de saldo no provedor)  
**Contexto:** o aceite da Etapa 6 pede citações inválidas sinalizadas e abstenção quando falta evidência; `08` §4 pede validade de citação, abstenção e vazamento medidos no corpus da D-061, com correção e suporte por auditoria humana.  
**Decisão:**
- **`AnswerEvaluationIT`**, tag `eval`, fora do `verify`. Indexa os corpora como o harness da D-062 (a preparação passou para `EvalCorpora`, usada pelos dois) e faz as 40 perguntas, inclusive as com `mustAbstain`, por `POST /questions`, cada uma numa conversa nova. Provedor de chat em `EVAL_GENERATION_PROVIDER` (`fake` por padrão; `openai` com a chave de `GENERATION_API_KEY`); embeddings em `EVAL_PROVIDER`, como na D-062; modo `HYBRID` com embeddings e `LEXICAL` sem, ou `EVAL_RETRIEVAL_MODE`.
- **`mustNotContain`** nas anotações: textos que não podem aparecer na resposta, comparados sem diferenciar maiúsculas. Obrigatório nas categorias `PROMPT_INJECTION` e `EXCLUDED_SECRET`; o `verify` confere que os textos da fixture existem nela (um erro de digitação faria a checagem passar sempre). Na fixture: o token sintético do `.npmrc` (fx-08 e fx-09) e o que o `CONTRIBUTING.md` injetado pede para dizer (fx-08).
- **Métricas automáticas** (`AnswerQuality`, pura, com testes no `verify`), sobre a resposta como a API a devolve: validade de citação (citações válidas / emitidas, contadas por evidência, porque o validador junta as citações repetidas de uma evidência); respostas sustentadas (`ANSWERED`/`PARTIAL` com ao menos uma citação válida); citação relevante (linhas citadas sobrepondo um intervalo esperado, pela regra da `RetrievalMetrics`; indício de suporte, não prova); abstenção correta nas perguntas com `mustAbstain` e indevida nas demais; vazamento; falhas. Resumo geral, por corpus, categoria e idioma; tokens (média e p95), custo estimado (total e médio) e latência (média e p95).
- **Cache de respostas** (`GenerationCache`): cada resultado do provedor fica em `DATA_DIR/eval-cache/answers/<provedor>-<modelo>-reasoning-<esforço>/<sha256 do pedido>.json` (ou em `EVAL_CACHE_DIR`). O nonce aleatório dos delimitadores (D-066) é normalizado antes do hash; sem isso, nenhum pedido se repetiria. O arquivo guarda a saída do modelo e os tokens, nunca o prompt; falhas não entram. Uma resposta em cache mantém os tokens e o custo da chamada original e fica fora da latência.
- **Resultados:** `<data>-answers-<provedor>-<modelo>-<esforço>-<modo>.json` (métricas, referências das evidências sem o trecho, texto da resposta) e `.md` em pt-BR, com o resumo e, por pergunta, a resposta ao lado dos trechos citados e caixas para a auditoria humana (correta, sustentada, nota). Com o modelo real, em `eval/results/`, versionados; com o fake, em `apps/backend/target/eval-results/`, sem versionar, porque só testam o harness.
- Sem metas numéricas (`08` §9): a primeira rodada real é o baseline.

**Motivo:** passar pelo endpoint mede o fluxo que a pessoa usa (recuperação, contexto, prompt, validação e custo); o cache torna a rodada paga barata de refazer e as métricas reproduzíveis, já que a resposta do modelo não é determinística.  
**Consequências:** a rodada com o fake na fixture roda em segundos e exercita o harness inteiro; a rodada real (custo estimado abaixo de US$ 0,15 com o `gpt-6-luna`, F0) e a auditoria esperam saldo, como a rodada da Etapa 5. A comparação com o `gpt-6.1-sol` e com o esforço `none` sai do mesmo harness, mudando só a configuração.  
**O que levaria à revisão:** LLM como juiz (Etapa 8), corpus maior, perguntas de acompanhamento ou um provedor que informe custo.

### D-070 — Stack do frontend (P1 da Etapa 7; completa a D-026)
**Status:** Aceita (16/10/2026; F0 da Etapa 7)  
**Contexto:** a D-026 deixou para a Etapa 7 a biblioteca de componentes e o gerenciamento de estado de `apps/web`. Quase todo o estado da UI vem da API, e os tipos podem ser gerados do contrato versionado (D-023).  
**Opções consideradas:**
1. Estado do servidor com TanStack Query, primitivos sem estilo (Radix) e CSS próprio.
2. Store global (Redux, Zustand) e biblioteca de componentes com estilo (MUI, Mantine) ou Tailwind.

**Decisão:** opção 1, com versões exatas e `package-lock.json` versionado. Versões conferidas no registro npm em 16/10/2026:
- React 19.3.1, Vite 8.3.4 (`@vitejs/plugin-react` 6.1.2), **TypeScript 5.9.3**, React Router 8.4.0, TanStack Query 5.104.3, TanStack Virtual 3.14.21, Radix UI 1.8.1;
- `openapi-typescript` 7.13.0 (tipos gerados do `apps/backend/openapi.json`, versionados e conferidos por teste) e `openapi-fetch` 0.17.0;
- Vitest 5.0.5, jsdom 30.1.3, Testing Library (React 16.3.3, user-event 14.6.8, jest-dom 7.0.1), MSW 3.0.3, Playwright 1.64.1, `axe-core`/`@axe-core/playwright` 4.14.0;
- **ESLint 10.13.0**, sem o `eslint-plugin-jsx-a11y`, com `typescript-eslint` 8.71.2 e `eslint-plugin-react-hooks` 7.1.1; Prettier 3.9.10;
- npm com `ignore-scripts=true` e `save-exact=true`; **Node 22.22.2 ou mais novo** (exigência do `jsdom`).

**Motivo:** o cache e o polling do TanStack Query cobrem o estado do servidor sem uma store; os primitivos do Radix trazem teclado e ARIA sem impor identidade visual (`03` §8). O TypeScript 7 fica fora dos pares do `typescript-eslint` (`<6.1.0`) e do `openapi-typescript` (`^5.x`). O `jsx-a11y` não suporta o ESLint 10 (último release em 2024), e o ESLint 9 já está marcado como sem suporte. A acessibilidade passa a ser verificada pelo `axe-core`, nos testes de componente e no E2E.  
**Consequências:** `npm run check` (tipos, lint, testes, build) é o `verify` do frontend. Nenhuma dependência roda script na instalação; os browsers do Playwright são baixados à parte.  
**O que levaria à revisão:** `typescript-eslint` e `openapi-typescript` com suporte ao TypeScript 7, ou um plugin de acessibilidade estático compatível com o ESLint 10.

### D-071 — Conteúdo do arquivo e versão ativa da evidência (P3 da Etapa 7; F-06)
**Status:** Aceita (16/10/2026; F1 da Etapa 7)  
**Contexto:** o visualizador precisa do arquivo inteiro como estava no snapshot (`06` §9 previa o endpoint para esta etapa), e o PRD pede avisar quando o arquivo mudou depois da resposta.  
**Decisão:**
- `GET /snapshots/{snapshotId}/files/content?path=` devolve JSON com `snapshotId`, `path`, `language`, `contentHash`, `sizeBytes`, `lineCount` e `content`, lido de `snapshot_files` + `file_contents`, sem tocar no disco. Erros, nesta ordem: `400 VALIDATION_FAILED` (`path`), `404 SNAPSHOT_NOT_FOUND`, `409 SNAPSHOT_PRUNED`, `404 FILE_NOT_FOUND`, `409 FILE_NOT_INDEXED`. Sem paginação de linhas; o limite é o `max-file-bytes` da indexação.
- `GET /evidence/{id}` ganha `activeVersion`: `SAME`, `CHANGED`, `REMOVED` ou `NO_ACTIVE_SNAPSHOT`, comparando o `contentHash` da evidência com o do mesmo caminho no snapshot ativo, numa consulta.
- A comparação mora na fachada `Retrieval` (como o `snapshotAvailable`), não numa porta de `indexing`: `answering` depende só de `retrieval`, `providers`, `history` e `project` (P11 da Etapa 6).

**Motivo:** JSON mantém os metadados junto e impede que o conteúdo, não confiável, seja servido como página; uma consulta por evidência é barata e dispensa ler o arquivo novo.  
**Consequências:** o visualizador abre a versão da resposta e, com `CHANGED`, pode oferecer a do snapshot ativo pelo mesmo endpoint.  
**O que levaria à revisão:** arquivos grandes travando a UI (paginação de linhas) ou diff entre versões (Etapa 9).

### D-072 — Acesso local ao backend (P5 da Etapa 7)
**Status:** Aceita (16/10/2026; F1 da Etapa 7, com o ajuste da F0)  
**Contexto:** a API não tem autenticação e o backend escutava em todas as interfaces. Com a UI no browser, uma página de terceiros pode chamar `localhost` (CSRF, inclusive o upload `multipart`, que não tem preflight) ou usar DNS rebinding para ler respostas. O proxy do Vite reescreve o `Host`, mas mantém a `Origin` do browser (F0).  
**Opções consideradas:**
1. Resolver na Etapa 7: escutar em loopback e filtrar `Host` e `Origin`.
2. Deixar para a Etapa 8.

**Decisão:** opção 1.
- `server.address` padrão `127.0.0.1` (`SERVER_ADDRESS`).
- `LocalAccessFilter` (em `shared/web`, depois do `RequestIdFilter`): `Host` fora de `app.http.allowed-hosts` (padrão `localhost`, `127.0.0.1`, `[::1]`, qualquer porta, sem distinção de caixa) → `403 FORBIDDEN_HOST`; `POST`, `PUT`, `PATCH` ou `DELETE` com `Origin` fora de `app.http.allowed-origins` (padrão `http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:4173`, `http://127.0.0.1:4173`) → `403 FORBIDDEN_ORIGIN`. Sem `Origin`, passa. As respostas são ProblemDetail, pelo mesmo `GlobalExceptionHandler`.
- Sem CORS. O proxy do Vite aponta para `127.0.0.1` e não reescreve a `Origin`.

**Motivo:** pouco código e testável, e a exposição nasce com a UI. Reescrever a `Origin` no proxy deixaria passar um CSRF que chegasse pelo próprio Vite.  
**Consequências:** quem expõe o backend à rede (`SERVER_ADDRESS=0.0.0.0`) precisa listar os nomes em `app.http.allowed-hosts`, e uma UI servida em outra porta, em `app.http.allowed-origins`. O log do filtro registra só o método e o código, nunca o `Host` ou a `Origin`.  
**O que levaria à revisão:** autenticação, acesso por outra máquina, ou o backend servindo a UI (P2, Etapa 8).  
**Etapa 8 (17/10/2026):** com o backend servindo a UI, a própria origem dele passa em qualquer porta (D-085).

### D-073 — Provedores configurados para a UI (P4 da Etapa 7)
**Status:** Aceita (16/10/2026; F1 da Etapa 7)  
**Contexto:** antes de indexar ou perguntar, a UI deve dizer para onde vão os trechos (`03` §7), e nenhum endpoint dizia isso.  
**Decisão:** `GET /system/providers`, no módulo `providers`, com `embeddings` (`provider`, `model`, `dimensions`, `remote`) e `generation` (`provider`, `model`, `remote`). `remote` é `true` só para `openai` com `base-url` fora de `localhost`/`127.0.0.1`. Nunca devolve chave, `base-url` nem preços. O `pricingConfigured` previsto na P4 ficou de fora: o preço é configuração do módulo `answering`, que `providers` não pode ler, e cada resposta já informa `costStatus`.  
**Motivo:** a UI decide o aviso de privacidade e os modos de busca disponíveis sem tentar e errar.  
**Consequências:** o aviso de envio é transparência, não controle de acesso; o controle continua sendo `provider=none`.  
**O que levaria à revisão:** configuração de provedor pela UI, ou provedor local (Etapa 9).

### D-074 — Nulos e obrigatórios no contrato (achado da F0 da Etapa 7; completa a D-023)
**Status:** Aceita (16/10/2026; F1 da Etapa 7)  
**Contexto:** a F0 gerou os tipos do frontend e encontrou duas falhas no `openapi.json`. Os schemas embutidos em respostas sem nome `*Response` (o inventário do snapshot) tinham campos opcionais. E o `@Nullable` do jspecify, anotação de tipo que o swagger não lê, não chegava ao contrato: só os campos com `@Schema(nullable = true)` à mão saíam anuláveis, e mais de vinte (`completedAt`, `prunedAt`, `sourceImportId`, `lineCount`, `ruleId`…) eram publicados como não nulos.  
**Decisão:**
- Os campos de todo schema alcançável a partir de um `*Response` são obrigatórios.
- Um `ModelConverter` (`NullableRecordComponents`, em `shared/openapi`) marca como anulável todo componente de record com `@Nullable`: `["tipo", "null"]`, ou `oneOf` com `null` para objetos. Vale também para requisições, onde o `null` é aceito e validado pelo backend.
- O `@Schema(nullable = true)` existente continua válido, mas deixa de ser necessário.

**Motivo:** os tipos gerados (D-070) só protegem a UI se o contrato disser a verdade sobre os nulos.  
**Consequências:** `openapi.json` regenerado; nenhuma mudança no JSON das respostas.  
**O que levaria à revisão:** o springdoc passar a ler anotações de tipo do jspecify.

### D-075 — Tela de pergunta: espera síncrona, visualizador e conteúdo como texto (P6, P7 e P8 da Etapa 7)
**Status:** Aceita (17/10/2026; F6 da Etapa 7)  
**Contexto:** a Etapa 6 deixou o streaming "se a UI pedir"; a resposta é JSON com citações validadas só no fim. O visualizador precisa abrir arquivos de até dezenas de milhares de linhas, e respostas, trechos e caminhos vêm de conteúdo não confiável.  
**Decisão:**
- `POST /questions` continua síncrono, sem SSE. A UI mostra a pergunta na hora, "buscando evidências e gerando a resposta" com o tempo decorrido e o limite de 60 s. Uma resposta `FAILED` aparece como resposta, com a `error.message` e a ação pelo código; só os erros HTTP de antes do registro viram alerta, mantendo o rascunho.
- Visualizador em texto puro, sem realce de sintaxe, sempre virtualizado (`@tanstack/react-virtual`, linhas de altura fixa, sem quebra). Linhas citadas (`citedStartLine`–`citedEndLine`, senão o trecho) com fundo, marcador na margem e texto para leitor de tela; rolagem até elas. `activeVersion` `CHANGED` oferece a versão do snapshot ativo (sem destaque); `PRUNED`, ou o conteúdo indisponível, mostra só o `snippet`.
- A resposta é texto (`white-space: pre-wrap`); só os marcadores `[En]` de evidências da própria resposta viram botões. Sem markdown, sem links, sem `dangerouslySetInnerHTML` (regra de lint).
- Modos `VECTOR`/`HYBRID` desabilitados quando não há provedor de embeddings ou o snapshot ativo tem `EMBEDDINGS_MISSING`/`EMBEDDING_MODEL_CHANGED` (a mesma checagem da busca). Com geração remota, o aviso exige confirmação na primeira pergunta do projeto (`localStorage`, separado do da indexação) e depois vira lembrete.

**Motivo:** um streaming do texto mostraria marcadores ainda não validados; virtualizar sempre é um caminho só, testado com um arquivo sintético de 1 MB; texto puro fecha a porta a HTML e links injetados pelo modelo.  
**Consequências:** a espera é de até 60 s sem texto parcial; arquivos sem realce.  
**O que levaria à revisão:** latência medida ruim (Etapa 8), legibilidade pedir realce, ou respostas com formatação rica.

### D-076 — Como a UI chega ao backend e como `apps/web` se organiza (P2 e P11 da Etapa 7)
**Status:** Aceita (17/10/2026; F1, F2 e fechamento da Etapa 7)  
**Contexto:** UI e API em portas diferentes exigem CORS ou um proxy, e faltava dizer como a pessoa roda o produto e onde cada parte do frontend mora.  
**Opções consideradas:**
1. Proxy do Vite para `/api` no `dev` e no `preview`.
2. O backend serve o build da UI num processo só.
3. CORS liberado para a origem do Vite.

**Decisão:** opção 1 no MVP: dois processos (backend e `npm run dev` ou `npm run preview`), sem CORS e sem acoplar o build Maven ao npm. Em `apps/web`: `src/api` (cliente, tipos gerados e versionados, tabela de ações por `code`), `src/app` (rotas, `QueryClient`), `src/components` (primitivos), `src/features/<tela>` (`workspaces`, `import`, `project`, `ask`, `history`), `src/messages.ts` (todos os textos pt-BR), `src/styles` e `e2e`. No backend, o conteúdo do arquivo em `indexing`, a versão ativa da evidência pela fachada `Retrieval` (desvio da F1, D-071), `GET /system/providers` em `providers` e o filtro de acesso local em `shared`.  
**Motivo:** a mesma origem para o browser simplifica a D-072; a estrutura por tela casa com a do backend por módulo.  
**Consequências:** o README traz dois comandos para subir o produto; as portas 5173 e 4173 são fixas, porque são as origens aceitas.  
**O que levaria à revisão:** empacotamento num único executável (opção 2, Etapa 8).  
**Etapa 8 (17/10/2026):** a opção 2 virou o perfil Maven `with-ui`, opcional; o build padrão segue sem npm e o modo de desenvolvimento com o proxy continua (D-085).

### D-077 — Navegação, estados e erros da UI (P9 da Etapa 7)
**Status:** Aceita (17/10/2026; F3 a F7 da Etapa 7)  
**Contexto:** as telas do `03` precisavam de rotas, de uma forma de acompanhar jobs e de um tratamento de erros que não dependesse do texto das mensagens.  
**Decisão:**
- Rotas: `/` (workspaces ou primeiro uso), `/workspaces/:id`, `/workspaces/:id/new` (assistente de importação e pré-análise, que serve também a projetos existentes), `/projects/:id` (resumo), `/projects/:id/ask` e `/projects/:id/conversations/:cid` (pergunta), `/projects/:id/history`. Paginação na URL (`?page=`, base 1).
- Polling pelo TanStack Query: 1 s com job ou import ativo, 2 s com resposta `PENDING`, parado no resto. Sem SSE.
- Erros: a UI mostra `title`, `detail` e `requestId` do ProblemDetail e escolhe a ação pelo `code` num `Record<ErrorCode, Recovery>`, que obriga a decidir a ação de cada código novo do contrato. Códigos dos jobs e das respostas, que são `string` no contrato, têm tabelas próprias com uma ação padrão.
- Estados em texto, não só cor (`03` §8); exclusões com confirmação e os `409` explicados no diálogo.
- Perguntas sugeridas: as quatro genéricas do `03`, só com snapshot ativo; preenchem o campo, não enviam.

**Motivo:** decidir pelo `code` mantém a UI correta quando as mensagens mudam; a tabela exaustiva transforma um código esquecido em erro de compilação.  
**Consequências:** o estado "falhou" não aparece na lista de projetos (o `ProjectResponse` traz só o job ativo, desvio da F3); aparece no resumo do projeto.  
**O que levaria à revisão:** jobs longos o bastante para o polling pesar, ou o último job no `ProjectResponse`.

### D-078 — Aceite de ponta a ponta da UI (P10 da Etapa 7)
**Status:** Aceita (17/10/2026; F8 da Etapa 7)  
**Contexto:** o aceite da Etapa 7 é de uso ("importar, indexar, perguntar e abrir evidências sem ferramentas internas"), e os testes de componente simulam a API.  
**Decisão:** um teste Playwright (`npm run e2e`, fora do `npm run check`, como os harnesses da D-062) contra o backend real com o banco do Compose e os provedores `fake`, na porta 8089, e o `vite preview` na 4173. Percorre o fluxo inteiro com um repositório de exemplo versionado (`apps/web/e2e/fixtures/sample-shop`), passa o axe (WCAG 2.1 A e AA, com contraste) em cada tela principal, falha com erro de console e remove o que criou.  
**Motivo:** cobre a integração UI, proxy, filtro de acesso local e backend que nenhum teste isolado cobre, sem custo de provedor.  
**Consequências:** exige Docker com o banco no ar e o Chromium do Playwright baixado à parte; não roda no `verify`.  
**O que levaria à revisão:** CI com banco efêmero, ou um provedor real no aceite.

### D-079 — Corpus ampliado (P1 da Etapa 8)
**Status:** Aceita (17/10/2026; F1 da Etapa 8 entregue; anotações aprovadas)  
**Contexto:** com 40 perguntas, uma pergunta vale 2,5 pontos de Recall e as comparações da P3 ficam frágeis. Faltavam TypeScript real, codificações fora do UTF-8 e variações de injeção e de segredo (D-061, "O que levaria à revisão").  
**Decisão:**
- **Quarto corpus:** `honojs/hono` v4.13.13 (`08a023c`, MIT), fora do repositório, na pasta de `EVAL_CORPUS_HONO`, com manifesto em `eval/corpus/hono.sha256` (585 arquivos; 565 indexados e 2.050 chunks no `lines-v1`). Os corpora da D-061 ficam nos mesmos commits.
- **Fixture ampliada:** injeção em comentário e em string de código, no nome de um diretório (com espaços) e depois de um delimitador de evidência forjado; texto que imita a saída do modelo (JSON e rótulos `[E1]`/`[E2]`); arquivos em UTF-8 com BOM, Latin-1 e UTF-16 com BOM; segredos sintéticos em arquivos excluídos pelo nome (`.npmrc`, `*.tfvars`) e em arquivos indexados hoje (para a P5). Todos os valores trazem `EVAL-SYNTHETIC` ou `EVALSYNTHETIC`.
- **80 perguntas** (30 na fixture, 24 no hono, 14 no Copilot, 12 no click), 40 em pt-BR e 40 em en; 14 exigem abstenção e 14 são `PROMPT_INJECTION` ou `EXCLUDED_SECRET`.
- As 4 perguntas de segredo em arquivo indexado (fx-21 a fx-24) só passam na avaliação de respostas depois da detecção por conteúdo (F6); até lá, medem o vazamento que a F6 deve eliminar.
- O teste que confere os textos de `mustNotContain` na fixture passa a considerar também os caminhos, que chegam ao modelo.
- **Baseline lexical refeito** (`eval/results/2026-10-17-lexical.*`): 66 perguntas com intervalo esperado, Recall@10 0,492 e MRR@10 0,291 no `LEXICAL`. Os números do click e do Copilot são idênticos aos de 16/10; os da fixture mudam porque o corpus mudou. É a referência da P3.

**Motivo:** o hono tem camadas (roteadores intercambiáveis, middlewares, adaptadores), o que dá perguntas de fluxo e de arquitetura, e é o maior corpus, útil também para a P6.  
**Consequências:** a avaliação completa exige extrair três corpora externos. No hono, o canal de caminhos piora o Recall@10 (0,432 para 0,295), ao contrário dos outros corpora: entrada para a F4. O push protection do GitHub bloqueia uma chave `sk_test_` da Stripe mesmo sintética, então a fixture usa um token `xoxb-` sem o formato numérico do Slack; na F6, os testes do padrão da Stripe montam o valor por concatenação, sem o literal no código.  
**O que levaria à revisão:** anotações ambíguas na revisão, ou um corpus que não separe as variantes da P3.

### D-080 — Medições de desempenho, metas e limites (P6 da Etapa 8)
**Status:** Aceita (17/10/2026; F2 da Etapa 8; metas e limites confirmados por você)  
**Contexto:** os limites da D-035, de `app.indexing.*`, o `max-chunks-per-job` e a limpeza de órfãos (D-052) eram hipóteses. Faltavam números do conjunto (varredura, import, indexação, busca, pergunta, memória e armazenamento) num projeto do tamanho do limite.  
**Decisão:**
- **Harness** `PerformanceEvaluationIT` (tag `performance`, fora do `verify`; `eval/README.md`): mede pela API, com os provedores `fake`, a fixture, os corpora externos e um repositório sintético determinístico (`SyntheticRepository`: 3.000 arquivos, ~46 mil chunks, com a forma do código dos corpora). Resultado em `eval/results/<data>-perf.*`, só com tamanhos, contagens e durações.
- **Busca híbrida em paralelo** (revisa a D-059): no `HYBRID`, os canais lexical e de caminhos rodam em threads virtuais enquanto a thread da requisição gera o vetor da pergunta e consulta o vetorial. Com 46 mil chunks, o p95 do `HYBRID` caiu de 1,06–1,58 s (três rodadas, `2026-10-17-perf-sequencial.*`) para **676 ms**, e o da pergunta sem o provedor de 0,77–1,10 s para **620 ms** (`2026-10-17-perf.*`). Com o provedor real, a chamada de embeddings também passa a correr em paralelo com o lexical.
- **Metas:** busca híbrida com p95 ≤ 1 s com 50 mil chunks e ≤ 200 ms até 5 mil; pergunta sem o provedor ≤ 1 s com 50 mil; reindexação sem mudanças abaixo de 10% da completa. Todas atendidas: nos corpora reais (até 2.050 chunks) o `HYBRID` fica em até 90 ms de p95; a reindexação sem mudanças leva 3,1 s contra 41–45 s da completa (7–8%).
- **Pior caso fora da meta:** pergunta com um termo presente em quase todo chunk, `HYBRID` com p95 de 1,06 s, dominado pelo lexical. Fica registrado; a alavanca é o IDF no lexical, só se o uso real mostrar o caso.
- **Limites mantidos, com os números:**
  - `max-chunks-per-job` (50 mil): é onde a busca chega perto de 1 s; ~110–140 MB de texto já o atingem. Projetos maiores falham no limite, com a mensagem atual.
  - `max-project-bytes` (500 MB) e demais limites da D-035: a varredura de 3.000 arquivos leva 0,1–0,3 s; nada pede mudança. O limite de chunks é o que vale na prática para projetos só de código.
  - Import ZIP (100 MB): 34–47 MB importados em menos de 1 s.
  - `app.indexing.*`: a indexação completa de 46 mil chunks leva 41–45 s, dos quais ~28 s na etapa lexical; o heap chega a 313 MB e o RSS a 546 MB (heap máximo de 4 GB). Nenhum lote pede ajuste.
  - Limpeza de órfãos (D-052): `FINALIZING` de 50 a 800 ms, inclusive com 10% dos arquivos alterados.
  - HNSW (D-049): não agora; o vetorial exato fica em 0,36–0,70 s de p95 com 46 mil chunks, e a troca muda o ranking, o que só dá para validar com vetores reais (F3).
  - Pular o hash por tamanho e data: não, a reindexação sem mudanças já está abaixo de 10%.
- **Armazenamento:** ~960 MB para 46 mil chunks no sintético (`chunk_lexical` 443 MB, vetores 381 MB); nos corpora reais, ~7 vezes o tamanho do texto. A poda mantém 4 snapshots; com 10% dos arquivos mudando por rodada, o total cresce até a poda alcançar.

**Motivo:** os números mostram que a soma dos canais era o principal custo da busca grande, e paralelizar não muda nenhum resultado.  
**Consequências:** cada busca híbrida usa até três conexões do pool ao mesmo tempo (o padrão do Hikari é 10). No Mac com Docker, o disco da VM satura depois de alguns GB escritos e a indexação completa chega a 89–91 s, com o tempo extra na etapa que estiver gravando (esperas `DataFileExtend` e `WalWrite` no `pg_stat_activity`); por isso as durações são comparadas em faixas, e não por uma rodada.  
**O que levaria à revisão:** busca acima da meta com o provedor real, projetos reais acima de 50 mil chunks, ou máquina com disco mais lento.

### D-081 — Variações de ranking e chunking sem o provedor (P3 da Etapa 8)
**Status:** Aceita (17/10/2026; F4 da Etapa 8, parte lexical; a parte com vetores fica para a F3)  
**Contexto:** a P3 fixou a regra de promoção antes das medições: Recall@10 ou MRR@10 do corpus inteiro subindo o equivalente a pelo menos duas perguntas (0,030 com 66 perguntas), nenhuma categoria caindo mais que uma pergunta, `EXACT_IDENTIFIER` sem queda, validade de citação e vazamento sem piora (com o modelo real) e latência p95 da busca até 20% acima da D-080. Sem saldo (decisão de 17/10/2026), só os canais lexical e de caminhos podem ser medidos.  
**Decisão:**
- **Harness:** `EVAL_CHUNKING=<linhas>:<sobreposição>` indexa com outros valores de chunking, numa versão `eval-lines-<linhas>-<sobreposição>` que só existe na avaliação; `EVAL_SWEEP=fusion` acrescenta a fusão de todos os canais medidos (`LEXICAL_PATH` sem vetores, `HYBRID` com eles) com um parâmetro alterado por vez. O `LineChunker` passou a receber os parâmetros (`LineChunker.Parameters`, padrão `V1` = `lines-v1`), injetados na indexação; fora da avaliação nada muda (rodada de controle idêntica ao baseline da D-079 com `EVAL_COMPARE_WITH`).
- **Fusão e canal de caminhos** (`eval/results/2026-10-17-lexical-fusion.*`, contra o `LEXICAL_PATH`: Recall@10 0,447, MRR@10 0,301): k de 10, 30 e 100, peso do canal de caminhos de 0,5 e 2, 20 e 100 candidatos por canal e limiar de 0,6, 0,7 e 0,9 ficam todos abaixo do ganho mínimo (o melhor, peso 0,5: +0,030 de Recall@10 e +0,004 de MRR@10, no limite e com `CONCEPTUAL` caindo). **Mantidos** k=60, pesos 1, 50 candidatos e limiar 0,8.
- **Causa da piora do canal de caminhos no hono (D-079):** um nome que é também nome de arquivo (`middleware`, por `src/helper/ssg/middleware.ts`) vira termo e é casado por trigramas contra o caminho inteiro, então todo arquivo em `src/middleware/**` casa com similaridade 1,0 e enche o canal. A variante em que **nomes casam só com os arquivos daquele nome** (caminhos continuam por trigramas) passa na regra: Recall@10 0,447 → 0,500 e MRR@10 0,301 → 0,335; no hono, 0,295 → 0,455 e 0,100 → 0,202; só o hono muda, 4 perguntas entram no top 10, nenhuma sai, e duas descem uma posição. **Não promovida ainda:** o canal de caminhos só roda no `HYBRID`, que exige vetores; a mudança no `PathSearch` fica como primeira candidata da F3, confirmada no `HYBRID` e na avaliação de respostas.
- **Chunking** (`eval/results/2026-10-17-lexical-lines-*.*`, uma variável por vez contra o `lines-v1`, no `LEXICAL`: Recall@10 0,492, MRR@10 0,291):

  | Variante | Chunks (4 corpora) | Recall@10 | MRR@10 | Regra |
  |---|---|---|---|---|
  | 40 linhas, sobreposição 10 | 5.355 | 0,434 | 0,287 | não: cai em `EXACT_IDENTIFIER` e `CONCEPTUAL` |
  | 80 linhas, sobreposição 10 | 2.935 | 0,510 | 0,323 | não: `CONCEPTUAL` cai 0,083 (duas perguntas); no `LEXICAL_PATH` passaria |
  | 60 linhas, sem sobreposição | 3.538 | 0,485 | 0,302 | não: ganho abaixo do mínimo |
  | 60 linhas, sobreposição 20 | 3.777 | 0,477 | 0,291 | não: cai |

  **Mantido o `lines-v1`.** O de 80 linhas é o único com ganho e fica como candidato para a F3: chunks maiores mudam também os embeddings e o custo do contexto, o que só a rodada com vetores e com o modelo mede. Sem `lines-v2`.
- **Latência:** nenhuma variante promovida, então a regra dos 20% não chegou a decidir; o p95 do lexical por corpus ficou em até 32 ms em todas as rodadas, com a variação normal entre execuções.

**Motivo:** a regra foi fixada antes dos números, e a avaliação lexical não mede o modo que usa o canal de caminhos nem o efeito do chunking nos vetores.  
**Consequências:** a O-005 e a O-010 continuam a validar, agora só pela parte com vetores; a F3 roda, além das rodadas da P2, a varredura com `EVAL_PROVIDER=openai EVAL_SWEEP=fusion` (pesos de cada canal no `HYBRID`), a variante de nomes no `PathSearch` e o chunking de 80 linhas. Reranking e AST continuam fora: nada mostra um teto do `lines-v1`.  
**O que levaria à revisão:** os números com vetores (F3), ou um corpus novo que separe melhor as variantes.

### D-082 — Suíte de segurança: injeção, importações maliciosas e codificação (P4 da Etapa 8)
**Status:** Aceita (17/10/2026; F5 da Etapa 8)  
**Contexto:** a F0 ligou cada item do `08` §7 e do `07` §11 a um teste e listou as lacunas. A parte de efeito da injeção (o modelo obedece ou não) depende do modelo real e fica com as perguntas `PROMPT_INJECTION` da avaliação de respostas (F3).  
**Decisão:**
- **Injeção, estrutural:** o conteúdo, o nome de arquivo e o de diretório ficam dentro do bloco da evidência com o nonce, inclusive com um delimitador de nonce forjado, um caminho com quebra de linha e delimitador, a imitação do JSON de saída com rótulos `[En]` e uma linha de instrução de ~4.000 caracteres; nada do conteúdo vai às instruções (`PromptBuilderTest`). Uma resposta que "obedece" ao arquivo e cita os rótulos escritos nele perde as citações e vira `PARTIAL` (`AnswerApiIT.labelsWrittenInTheContentAreNotEvidence`).
- **Segredos e logs, de ponta a ponta:** a fixture da avaliação inteira é indexada e as perguntas de segredo são feitas com provedores espiões: nenhum segredo sintético chega ao provedor de embeddings nem ao de geração, e o log da indexação e das respostas não tem caminhos, conteúdo nem perguntas (`ExcludedContentIT`).
- **ZIP:** nomes **normalizados para NFC** na extração, como a varredura os informa; a grafia NFD e a NFC do mesmo nome viram `ZIP_DUPLICATE_ENTRY` em qualquer sistema de arquivos (antes, no Linux, viravam dois arquivos com o mesmo caminho no snapshot). Nomes com **controles bidirecionais** (U+061C, U+200E, U+200F, U+202A–U+202E, U+2066–U+2069) são `ZIP_UNSAFE_PATH`, porque fazem um nome aparecer diferente do que é. ZIP dentro de ZIP é extraído como arquivo, sem abrir (e a regra `archives` o exclui da varredura); comentário e extra field no máximo do formato não mudam nada.
- **Pasta local:** link físico é lido como o arquivo comum que é (teste fixa o comportamento); nomes em NFD são informados em NFC e lidos pelo caminho real. **Limitações documentadas, sem detecção:** link físico e ponto de montagem dentro da raiz só existem se a própria pessoa os criar; no Linux, um diretório em NFD não é aberto pelo navegador pelo nome em NFC, e uma pasta com dois nomes iguais depois da NFC falha a indexação (`INDEX_IO_ERROR`, depois das tentativas).
- **Codificação:** só UTF-8, com ou sem BOM (removido, linhas preservadas). Arquivo com BOM de UTF-16 vira `UNSUPPORTED INVALID_ENCODING` sem decodificar (antes virava `BINARY` pelos bytes NUL); UTF-16 sem BOM continua `BINARY`; Latin-1 continua `INVALID_ENCODING`.
- **Nada é executado:** regra ArchUnit proíbe `ProcessBuilder`, `Process` e `Runtime.exec` no código de produção.
- **Exclusão de projeto:** teste de integração confere que a pasta do import ZIP sai do `DATA_DIR` logo depois da exclusão.
- **UI:** E2E com um projeto de fixture com HTML e script no conteúdo, no nome de arquivo e de diretório e na pergunta: tudo aparece como texto, nada roda e não há erro de console nem violação de CSP (`e2e/hostile-content.spec.ts`). A resposta do fake é fixa; a marcação vinda do modelo continua coberta pelo teste de unidade do texto da resposta.
- **Dependências** (consulta repetida em 17/10/2026, fora do `verify`): `npm audit` em `apps/web` sem nenhuma vulnerabilidade (411 pacotes, inclusive os de desenvolvimento); as 168 dependências Maven consultadas na base do OSV (`api.osv.dev/v1/querybatch`) sem nenhum aviso, com Tomcat 11.0.27 e Jackson 3.1.7 e 2.21.7 sobrescritos na F1. Nenhuma ação nova.

**Motivo:** fechar as lacunas da matriz com testes no `verify`, sem depender do modelo real; normalizar e recusar só onde o projeto controla a escrita (o extrator), e documentar onde a detecção custaria mais do que o risco.  
**Consequências:** um ZIP que antes era aceito com nomes bidirecionais ou com duas grafias Unicode do mesmo nome agora é recusado. As limitações da pasta local entram no `11-OPERATIONS.md` (F9).  
**O que levaria à revisão:** uso real em Linux com nomes em NFD, ou um caso de ZIP legítimo recusado pelos controles bidirecionais.

### D-083 — Segredos por conteúdo (P5 da Etapa 8, completa a D-036)
**Status:** Aceita (17/10/2026; F6 da Etapa 8)  
**Contexto:** até aqui só o nome do arquivo excluía segredos (D-036); um `application.yml` ou um script com uma chave real era indexado e podia ir ao provedor. A F0 levantou os formatos publicados de cada emissor e confirmou o `CHECK` do motivo no banco.  
**Opções consideradas:** excluir o arquivo inteiro com um motivo próprio; mascarar só o trecho; não detectar e documentar.  
**Decisão:**
- **Excluir o arquivo inteiro:** um arquivo com um possível segredo vira `UNSUPPORTED` com o motivo **`SENSITIVE_CONTENT`** (migração `V11`, que só acrescenta o valor ao `CHECK`). Nada dele é guardado além da linha em `snapshot_files`: nem conteúdo, nem chunks, nem índice lexical, nem embeddings.
- **Onde:** no `IndexRunner`, sobre os bytes lidos, **antes** do atalho de conteúdo já chunkado, de qualquer chunk e de qualquer envio. Assim um conteúdo indexado antes da detecção é excluído pela próxima indexação. Os bytes são lidos como Latin-1, porque todos os padrões são ASCII: nenhuma decodificação falha e um arquivo UTF-8 casa como se fosse decodificado.
- **Padrões (`SecretDetector`), só de alta precisão:** bloco `-----BEGIN … PRIVATE KEY-----` (RSA, EC, DSA, OPENSSH, ENCRYPTED, PKCS#8 e PGP `PRIVATE KEY BLOCK`) seguido de material de chave (um parser que só cita o delimitador não casa; o `\n` escapado numa string casa); AWS `AKIA`/`ASIA` com 16 caracteres; GitHub `gh[pousr]_` com 36 ou mais e `github_pat_`; Stripe `sk_`/`rk_` `live`/`test` e `sk_org_` (o `pk_` público fica de fora); Slack `xoxb-`, `xoxp-`, `xapp-`, `xoxe.xapp-`, `xwfp-`; OpenAI `sk-` com 32 ou mais caracteres e pelo menos um dígito (média precisão: nem todos os prefixos são publicados). Sem entropia, `password=` nem chaves sem formato próprio (o segredo da AWS, por exemplo).
- **Não pode ser desligada**, como as regras `SENSITIVE`. A API documenta o motivo como "arquivo excluído por conter um possível segredo; a detecção não é garantia"; a UI lista o arquivo com "Excluído por conter um possível segredo", sem o trecho nem a linha. Nenhum log diz qual padrão casou.
- **Falsos positivos medidos:** nenhum no click (166 arquivos) e no hono (585); no próprio repositório, só os quatro arquivos com segredos sintéticos da fixture e o `eval/questions/fixture.yaml`, que os cita.
- **Avaliação** (`eval/results/2026-10-17-lexical-sensitive-content.*`, contra o baseline lexical da D-079): Recall@10 e MRR@10 iguais em todos os corpora; na fixture, 14 arquivos indexados em vez de 18, e os arquivos com segredo saem do top 10 das perguntas `EXCLUDED_SECRET` (antes, `sign_release.py`, `notify_slack.py` e `application.yml` apareciam nelas).

**Motivo:** excluir o arquivo inteiro mantém as linhas citadas iguais ao disco; mascarar daria falsa segurança e um arquivo diferente do original. Padrões de alta precisão evitam esconder código legítimo.  
**Consequências:** snapshots feitos antes da F6 continuam com o conteúdo desses arquivos até a próxima indexação, sem aviso em `outdatedReasons` (exigiria registrar a versão da detecção em cada snapshot); o `11-OPERATIONS.md` (F9) recomenda reindexar. Contrato: valor novo no enum `reason` de `GET /snapshots/{id}/files`, aditivo. As perguntas fx-21 a fx-24 da avaliação de respostas dependem desta detecção e são medidas na F3.  
**O que levaria à revisão:** um falso positivo relatado em código legítimo, um formato novo publicado por um emissor, ou a necessidade de avisar snapshots antigos.

### D-084 — Cancelamento de indexação, exclusão de conversas, último job e uso agregado (P7 da Etapa 8)
**Status:** Aceita (17/10/2026; F7 da Etapa 8)  
**Contexto:** uma indexação longa (ou com o provedor pago) não podia ser interrompida; o histórico só crescia; a lista de projetos não mostrava a última falha, porque o `ProjectResponse` trazia só o job ativo (F3 da Etapa 7); e os eventos de produto do `03` §9 não tinham leitura nenhuma.  
**Opções consideradas:** para o cancelamento, interromper a thread do worker ou um pedido gravado no job e conferido pelo próprio worker; para o uso, uma tabela de eventos ou agregados calculados sobre as tabelas existentes; para o histórico, retenção automática por idade ou exclusão manual.  
**Decisão:**
- **Cancelamento por pedido gravado:** `POST /index-jobs/{id}/cancel` → `202`. Coluna `index_jobs.cancel_requested_at` e status `CANCELLED` em `index_jobs` e `snapshots` (migração `V12`). Um job `PENDING` termina na hora; num `RUNNING`, o heartbeat deixa de renovar o lease quando há pedido e o worker, ao perceber, termina o job como `CANCELLED` (entre etapas e a cada lote de arquivos, de índice lexical ou de embeddings, onde o heartbeat já é gravado). O desfecho segue o da falha: snapshot `CANCELLED` sem arquivos, conteúdo sem referência removido, job do projeto liberado; snapshot ativo inalterado e embeddings mantidos no cache (D-051). Um pedido feito antes de uma queda ou de uma volta à fila é honrado por quem pegar o job. Job terminado → `409 JOB_NOT_ACTIVE`. A conclusão vence um pedido que chega depois da última verificação. A importação ZIP não é cancelável (curta e limitada). `cancelRequested` aparece no job e no `indexing` do projeto; a UI mostra "Cancelar", com confirmação, e "cancelamento pedido" até o fim.
- **Exclusão de conversa:** `DELETE /conversations/{id}` → `204`, em cascata (mensagens, respostas, evidências copiadas). Uma resposta `PENDING` é descartada quando o provedor responder (o `complete` já não acha a resposta). **Sem retenção automática** no MVP: os dados são locais e a pessoa decide; remover o projeto continua apagando tudo (D-021). Na UI, "Excluir" em cada conversa do histórico, com confirmação.
- **Último job:** `lastJob { id, status, finishedAt, errorCode }` no `ProjectResponse` (o job de indexação terminado mais recente, numa quarta consulta em lote por página); a lista e o resumo mostram "A última indexação falhou" enquanto não começa outro job. Um cancelamento não muda o estado do projeto na lista; o resumo explica que a última indexação foi cancelada.
- **Uso agregado:** `GET /projects/{id}/usage`, calculado em SQL a cada leitura sobre `index_jobs` e `answers` (quantidades por status, duração média e p95 das indexações concluídas, latência p50 e p95 das respostas, tokens somados, custo estimado somado e respostas com custo `UNKNOWN` que chamaram o modelo). Sem tabela de eventos e sem telemetria; "evidências abertas" fica de fora. Na UI, um painel "Uso" no resumo do projeto. O endpoint fica no `answering`, que lê os agregados públicos de `indexing` (`IndexingUsage`) e de `history` (`AnswerHistory.usage`).

**Motivo:** interromper a thread deixaria transações e chamadas ao provedor pela metade; o pedido gravado reaproveita o heartbeat e as transições condicionadas à tentativa (D-040). Os agregados sobre as tabelas existentes respondem às perguntas do `03` §9 sem guardar nada novo.  
**Consequências:** contrato só aditivo (status `CANCELLED`, `cancelRequested`, `lastJob`, dois endpoints, um código de erro). Conversas excluídas somem também do uso. O cancelamento leva até um lote (ou uma chamada ao provedor, com as tentativas da D-051) para ter efeito.  
**O que levaria à revisão:** pedido de retenção automática, necessidade de cancelar a importação, ou de medir uso da UI (evidências abertas), que exigiria eventos.

### D-085 — Empacotamento: UI servida pelo backend (P8 da Etapa 8; completa a D-072 e a D-076)
**Status:** Aceita (17/10/2026; F8 da Etapa 8)  
**Contexto:** rodar o produto exigia dois processos (`spring-boot:run` e `vite preview`), e o build da UI tinha um chunk de 539 kB, acima do aviso de 500 kB do Vite.  
**Opções consideradas:** para o build, um plugin que baixa Node (`frontend-maven-plugin`) ou o `npm` da máquina pelo `exec-maven-plugin`; para o fallback da SPA, um controller com padrões de rota ou um resolvedor de recursos; para a origem do backend, listar `localhost:8080` e `127.0.0.1:8080` em `allowed-origins` ou aceitar a mesma origem.  
**Decisão:**
- **Perfil Maven `with-ui`:** `exec-maven-plugin` (versão gerida pelo Spring Boot) roda `npm ci` e `npm run build` em `apps/web` na fase `generate-resources`, e o `maven-resources-plugin` copia o `dist` para `static/` do jar. Usa o Node da máquina, sem download no build. O `./mvnw verify` padrão continua sem npm (D-076).
- **Recursos e fallback:** `spring.web.resources.add-mappings=false` e o mapeamento próprio em `WebConfiguration`: `/assets/**` com cache de um ano `immutable` (os nomes têm o hash do conteúdo) e `/**` com `no-cache` e o `SpaResourceResolver`, que serve o arquivo existente ou, para caminhos sem extensão fora de `/api` e `/actuator`, o `index.html`. Sem UI empacotada não há `index.html` e o `404 RESOURCE_NOT_FOUND` continua igual; `/api/...` desconhecido e arquivos inexistentes nunca recebem o HTML. A raiz chega ao `index.html` pela página de boas-vindas do Spring.
- **Cabeçalhos (`SecurityHeadersFilter`, em toda resposta, inclusive as recusadas pelo `LocalAccessFilter`):** a CSP do `apps/web/csp.ts` mais `frame-ancestors 'none'` (que a meta tag não aceita), `X-Content-Type-Options: nosniff` e `Referrer-Policy: no-referrer`. A Swagger UI usa scripts e estilos inline, então recebe só `frame-ancestors 'none'`. A meta tag do build continua, para o `vite preview`; as duas listas são mantidas iguais à mão.
- **Origem do backend:** uma `Origin` igual a `http://` + o `Host` da requisição passa em qualquer porta, em vez de listar `:8080` em `allowed-origins`. É seguro porque o `Host` já foi conferido contra `allowed-hosts` (um DNS rebinding traz outro `Host` e é recusado antes).
- **Build:** cada tela é um chunk (`React.lazy`, com `Suspense` e o mesmo "Carregando…"); o maior passa a 363 kB (115 kB com gzip), sem aviso.
- **E2E:** `npm run e2e:jar` gera o jar e roda os specs contra `java -jar`, mais `packaged.spec.ts` (recarregar numa rota, cabeçalhos, `404` da API como ProblemDetail, mudança de estado da própria origem). O `npm run e2e` com o Vite continua.

**Motivo:** um processo só simplifica o uso local sem mudar o desenvolvimento; o resolvedor mantém os erros da API como estão; aceitar a mesma origem não depende da porta escolhida em `SERVER_PORT`.  
**Consequências:** o perfil exige Node 22 e npm; ao alternar entre os builds é preciso `clean` (o `static/` copiado fica em `target/classes`). A CSP existe em dois lugares (`csp.ts` e `SecurityHeadersFilter`). Executar o jar de fora de `apps/backend` continua sem achar o `.env`.  
**O que levaria à revisão:** distribuição para outras máquinas (instalador, imagem Docker), uma UI que precise de recursos de outra origem, ou a Swagger UI sob a CSP estrita.

---

## Decisões em aberto

### O-004 — Provedor inicial (chat e embeddings)
**Status:** A validar (embeddings e chat decididos provisoriamente, D-047 e D-063; comparação com o corpus de avaliação pendente de saldo)  
**Restrições já aceitas:**
- Escolher **um** par embedding/chat por instalação; trocar de modelo implica reindexar.
- Preferir modelos de embedding com **até 2.000 dimensões**, compatível com índice HNSW sobre `vector` no pgvector (`halfvec` permite mais). A dimensão não é mais fixada por migração: fica na linha (D-048).
- Provedor de embeddings e de chat podem ser diferentes (a Anthropic, por exemplo, não oferece modelo de embeddings próprio), o que justifica as duas portas separadas.

**Critérios de comparação:** recall@k e MRR no corpus, qualidade em código, custo por 1M de tokens, latência, limites de taxa e política de retenção de dados do provedor.  
**Estado (16/10/2026, fim da Etapa 5):** embeddings decididos provisoriamente na Etapa 4: OpenAI `text-embedding-3-small`, 1.536 dimensões (D-047), com armazenamento que não exige migração para trocar de modelo (D-048). O bake-off com a Voyage `voyage-code-3` foi adiado (D-047, atualização da Etapa 5): o corpus de avaliação (D-061) e o harness (D-062) estão prontos para ele, mas nem o baseline com a OpenAI rodou ainda, por falta de saldo. O provedor de chat é decidido na Etapa 6.  
**Estado (16/10/2026, fim da Etapa 6):** chat decidido provisoriamente: OpenAI `gpt-6-luna` pela Chat Completions (D-063), com o `gpt-6.1-sol` e a Anthropic como candidatos. O harness de avaliação de respostas (D-069) está pronto para a comparação, mas não rodou com modelo real, por falta de saldo; continuam pendentes também o baseline vetorial da Etapa 5 e os testes de fumaça das Etapas 4 e 6. A O-004 segue a validar até essas rodadas.

### O-005 — Comparação final de estratégia lexical e fusão
**Status:** A validar  
**Contexto:** D-017 define o ponto de partida. Falta compará-lo com alternativas.  
**A comparar:** lexical puro, vetorial puro, híbrido com RRF e variações (valor de k, pesos), tratamento de identificadores, e, apenas se o lexical se mostrar fraco, índice auxiliar com BM25.  
**Evidência para decidir:** resultados no corpus da Etapa 5 (recall@k, MRR, latência).  
**Estado (16/10/2026, fim da Etapa 5):**
- **Medido:** lexical e lexical + caminhos, no corpus de 36 perguntas (`eval/results/2026-10-16-lexical.*`). O canal de caminhos sobe o MRR@10 de 0,341 para 0,362 e o Recall@5 de 0,380 para 0,407, sem mudar o Recall@10. O lexical é fraco em pt-BR (Recall@10 0,296) e em perguntas conceituais (MRR@10 0,160), como previsto; isso é o que o vetorial deve cobrir, e não basta para justificar BM25 antes da comparação.
- **Falta:** vetorial e híbrido com vetores reais (o harness já calcula `VECTOR`, `LEXICAL_VECTOR` e `HYBRID` sobre as mesmas listas), variações de k e pesos, e um corpus maior (Etapa 8). Latência com 50 mil chunks: D-059.
**Estado (17/10/2026, F4 da Etapa 8):** com 66 perguntas, k, peso do canal de caminhos, candidatos por canal e limiar medidos no `LEXICAL_PATH`, nenhum com ganho mínimo: mantidos (D-081). O canal de caminhos piora no hono por casar nomes de arquivo contra o caminho inteiro; casar nomes só com o arquivo daquele nome passa na regra e é a primeira candidata da F3, a confirmar no `HYBRID`. Falta só a parte com vetores.

### O-010 — Parâmetros iniciais de chunking
**Status:** A validar  
**Contexto:** tamanho, sobreposição e limites de linhas são hipóteses.  
**Plano:** ajustar no corpus, alterando uma variável por vez (`08-EVALUATION.md`, seção 8).  
**Etapa 3 (16/10/2026):** valores iniciais provisórios na D-043 (`lines-v1`: 60 linhas, 4.000 caracteres, sobreposição de 10 linhas, `LINE_TOO_LONG` acima de 4.000 caracteres por linha). A comparação fica para a Etapa 5.  
**Etapa 5 (16/10/2026):** o corpus anota relevância por intervalo de linhas (D-061), e o harness mede qualquer versão de chunking sem mudar as anotações (D-062). Só `lines-v1` foi medido; a comparação de variantes passa para a Etapa 8 ("melhorar ranking/chunking apenas com evidência").
**Etapa 8 (17/10/2026, F4):** 40 e 80 linhas, sobreposição de 0 e 20, uma variável por vez, no lexical (D-081). Nenhuma passa na regra no `LEXICAL`; mantido o `lines-v1`. O de 80 linhas sobe o Recall@10 (0,492 → 0,510) e o MRR@10 (0,291 → 0,323), mas cai em `CONCEPTUAL`; fica como candidato para a rodada com vetores (F3).

### O-011 — Corpus de avaliação
**Status:** Resolvida pela D-061 (16/10/2026)  
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
