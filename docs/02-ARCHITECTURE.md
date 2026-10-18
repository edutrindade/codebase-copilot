# Arquitetura — Codebase Copilot

## 1. Decisão geral

Adotar um **monólito modular em Java + Spring Boot**, com frontend separado em React/Vite/TypeScript. Não iniciar com microserviços. A separação em módulos e portas internas permite extrair um worker ou serviço no futuro se métricas demonstrarem necessidade.

## 2. Visão de componentes

```text
React + Vite + TypeScript
          |
       REST API
          |
Java + Spring Boot (monólito modular)
  ├── Workspace / Project
  ├── Import & File Safety
  ├── Indexing Orchestrator
  ├── Code Analysis Adapters
  ├── Chunking & Embeddings
  ├── Hybrid Retrieval
  ├── Answer Generation & Citation Validation
  ├── History & Metrics
  └── Provider Ports
          |
PostgreSQL + pgvector
          |
Embedding / Chat provider (remote initially)
```

## 3. Stack

### Frontend
- React
- Vite
- TypeScript
- TanStack Query para o estado do servidor, React Router, primitivos do Radix UI com CSS próprio e `@tanstack/react-virtual` no visualizador (D-070).
- Tipos gerados do `openapi.json` com `openapi-typescript` e cliente `openapi-fetch`; um teste falha se os tipos estiverem defasados (D-070).
- Em desenvolvimento e no `preview`, o Vite faz proxy de `/api` para o backend, sem CORS (D-076).
- Desde a Etapa 9: `lucide-react` (ícones), Inter e JetBrains Mono pelo `@fontsource-variable` (D-087), `cmdk` na paleta de comandos, num chunk carregado no primeiro uso (D-090), e o Lezer (`@lezer/highlight` e um parser por linguagem) num Web Worker para o realce (D-093). Versões exatas, com pelo menos 14 dias de publicação (D-087).

### Backend
- Java 25 e Spring Boot 4.1.x (D-014).
- Maven com wrapper `./mvnw` (D-015).
- Spring Web MVC para REST.
- Bean Validation para validação de requests.
- SDK oficial `openai-java` para embeddings (Etapa 4) e geração (Etapa 6), somente no módulo `providers` e encapsulado por interfaces internas (D-006, D-014). O Spring AI saiu na Etapa 6 (D-014): o modelo de chat dele confunde tokens ausentes com zero e pode logar o prompt.
- Spring Data JDBC para CRUD de agregados pequenos; `JdbcClient` com SQL explícito para indexação e para toda a recuperação híbrida; **sem JPA** (D-016).
- Flyway para migrações.
- OpenAPI code-first com springdoc; contrato versionado em `apps/backend/openapi.json` (D-023).
- Testes unitários e de integração (Testcontainers); formatação verificada com Spotless.

### Persistência
- PostgreSQL.
- Extensão pgvector.
- Índices relacionais para metadados e busca lexical.
- Busca lexical inicial (D-017): full-text do PostgreSQL com configuração `simple`, identificadores pré-tokenizados e `pg_trgm` para caminhos e símbolos, sem serviço externo. A comparação com alternativas segue em aberto (O-005).

### Desenvolvimento local
- Docker Compose para PostgreSQL e dependências.
- Configuração por variáveis de ambiente.
- Nenhuma chave de provedor versionada no repositório.

## 4. Limites modulares

### Workspace
Gerencia workspaces e configurações de alto nível.

### Project Import
Controla a origem do projeto, validação de pasta/ZIP, limites, exclusões e inventário.

### Indexing
Orquestra snapshots, detecção de alterações, chunks, embeddings e persistência. Deve ser idempotente e recuperável após falhas.

### Code Analysis
Fornece interfaces para detectores de linguagem e adaptadores de análise estrutural. O fallback genérico deve funcionar sem parser especializado.

### Retrieval
Implementa busca lexical e vetorial, fusão de ranking, filtros por projeto/snapshot e montagem de evidências.

### Answering
Constrói prompts, chama o provedor, valida referências e registra resultado e métricas.

### Provider Integration
Abstrai geração, embeddings e telemetria do provedor por portas internas (`EmbeddingProvider`, `GenerationProvider`). Os tipos do SDK (`com.openai`) não saem do módulo; o ArchUnit confere.

### History & Metrics
Persiste perguntas, respostas, evidências, latência, tokens e custo estimado.

## 5. Decisões importantes

- **Monólito modular:** menor complexidade operacional para o MVP.
- **Sem execução do repositório:** o importador lê texto; não roda scripts, build tools nem hooks.
- **Provedores atrás de portas:** permite mudar provedor ou adicionar modelos locais sem reescrever o domínio (D-006, D-064).
- **SQL especializado na recuperação:** o ranking híbrido precisa ser observável e testável; abstrações genéricas não devem esconder o comportamento.
- **Processamento assíncrono:** começar com jobs persistidos e execução assíncrona no backend; imports em executor em processo (D-034) e indexação por fila em tabela com `SKIP LOCKED`, heartbeat e limite de tentativas (D-040). Separar worker apenas se consumo de recursos, escalabilidade ou confiabilidade justificarem.
- **Versionamento por snapshot:** respostas devem estar ligadas ao snapshot que forneceu as evidências.

## 6. Estrutura de repositório sugerida

```text
codebase-copilot/
  apps/
    web/            (React + Vite + TypeScript, D-070 e D-076)
      src/api/        cliente, tipos gerados e ações por código de erro
      src/app/        rotas, QueryClient, tema, atalhos e títulos
      src/components/ casca (barra lateral, barra superior, paleta) e ui/ (sistema de design)
      src/features/   uma pasta por tela: workspaces, import, project, ask, files, history,
                      system; ask/highlight/ tem o worker do realce
      src/styles/     camadas de CSS, tokens e fontes
      src/messages.ts textos da UI em pt-BR
      e2e/            aceite, navegação, tema, arquivos, conteúdo hostil e regressão visual
                      (Playwright), com os repositórios de exemplo
    backend/
      openapi.json  (contrato versionado, D-023)
  docs/
  infra/
    docker-compose.yml
  samples/
  .env.example
  README.md
```

No backend, organizar pacotes por domínio/módulo, evitando uma pasta global de classes genéricas que mistura regras de negócio.

Exemplo conceitual:

```text
com.codebasecopilot
  workspace/
  project/
  importjob/
  indexing/
  codeanalysis/
  retrieval/
  answering/
  providers/
  history/
  shared/
```

## 7. Fluxo de uma pergunta

Implementado na Etapa 6 (`POST /projects/{projectId}/questions`, D-066 e D-067):

1. Validar o pedido, o projeto, a conversa e o snapshot (o ativo, se nenhum for informado), com as checagens da busca e a disponibilidade da geração. Nada é gravado se alguma falhar.
2. Gravar, numa transação curta, a conversa, a pergunta e a resposta `PENDING`.
3. Fora de transação: gerar o vetor da pergunta (modos `VECTOR` e `HYBRID`), recuperar candidatos pelos canais lexical, vetorial e de caminhos e fundi-los por RRF (D-059). Sem reranking.
4. Selecionar evidências dentro do orçamento de tokens e de evidências (`ContextAssembler`). Sem evidência, abster-se sem chamar o modelo.
5. Chamar o provedor de geração com as evidências e a pergunta em blocos delimitados por um nonce, com instruções para tratar o conteúdo como dado não confiável e saída estruturada estrita.
6. Validar cada citação contra as evidências enviadas e as suas linhas (`CitationValidator`); descartar e contar as inválidas; o backend decide o status final (`ANSWERED`, `PARTIAL` ou `ABSTAINED`).
7. Gravar, noutra transação curta, o desfecho, a cópia das evidências e as métricas (tokens, custo estimado, durações). Falha de provedor depois do passo 2 vira resposta `FAILED`, sem perder a pergunta.

## 8. Observabilidade

Registrar IDs de correlação, status, duração por etapa, contagens e erros. Não registrar conteúdo completo de código ou prompts por padrão. Diferenciar:
- duração de recuperação;
- duração de embeddings;
- duração de geração;
- tokens reportados pelo provedor;
- custo calculado a partir de preço configurado;
- custo desconhecido quando não houver informação suficiente.

## 9. Testes arquiteturais

- Testes unitários por módulo.
- Testes de integração com PostgreSQL/pgvector.
- Testes de contrato da API.
- Testes de segurança para ZIP e exclusões.
- Testes de recuperação com corpus fixo.
- Testes de citações e abstenção.
- Testes de idempotência e reindexação incremental.

## 10. Rastreio de decisões

As decisões técnicas e os itens em aberto são mantidos em `10-DECISIONS.md`. Situação dos temas levantados originalmente neste documento:

| Tema | Situação |
|---|---|
| Versões de Java, Spring Boot e Spring AI | D-014 (Spring AI removido na Etapa 6) |
| Maven ou Gradle | D-015 (Maven) |
| JPA, JDBC ou abordagem híbrida | D-016 (sem JPA) |
| Busca lexical e algoritmo de fusão | D-017 (provisória); comparação final em O-005 |
| Provedor inicial de chat e embeddings | D-047 e D-063 (provisórias); bake-off em O-004 |
| Biblioteca de UI e estado do frontend | D-070 (Etapa 7) |
