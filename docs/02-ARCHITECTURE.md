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
- Biblioteca de componentes e gerenciamento de estado decididos na Etapa 7, junto com o scaffold de `apps/web` (D-026).
- Cliente HTTP gerado ou tipado a partir do OpenAPI, se isso reduzir divergência.

### Backend
- Java 25 e Spring Boot 4.1.x (D-014).
- Maven com wrapper `./mvnw` (D-015).
- Spring Web MVC para REST.
- Bean Validation para validação de requests.
- Spring AI 2.0.x para integrações de modelo e embeddings, a partir da Etapa 4, somente no módulo `providers` e encapsulado por interfaces internas (D-006, D-014).
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
Abstrai geração, embeddings e telemetria do provedor. Spring AI pode ser usado dentro do adaptador, mas tipos do framework não devem vazar para o domínio.

### History & Metrics
Persiste perguntas, respostas, evidências, latência, tokens e custo estimado.

## 5. Decisões importantes

- **Monólito modular:** menor complexidade operacional para o MVP.
- **Sem execução do repositório:** o importador lê texto; não roda scripts, build tools nem hooks.
- **Spring AI atrás de portas:** permite mudar provedor ou adicionar modelos locais sem reescrever o domínio.
- **SQL especializado na recuperação:** o ranking híbrido precisa ser observável e testável; abstrações genéricas não devem esconder o comportamento.
- **Processamento assíncrono:** começar com jobs persistidos e execução assíncrona no backend; o mecanismo está em definição na O-009 (antes da Etapa 3). Separar worker apenas se consumo de recursos, escalabilidade ou confiabilidade justificarem.
- **Versionamento por snapshot:** respostas devem estar ligadas ao snapshot que forneceu as evidências.

## 6. Estrutura de repositório sugerida

```text
codebase-copilot/
  apps/
    web/            (Etapa 7, D-026)
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

1. Validar workspace, projeto e snapshot ativo.
2. Gerar consulta lexical e vetor da pergunta.
3. Recuperar candidatos por canais lexical e vetorial.
4. Fundir e reranquear candidatos.
5. Selecionar contexto dentro do orçamento configurado.
6. Chamar o provedor de geração com instruções para tratar o código como dado não confiável.
7. Validar que cada referência aponta para chunk/arquivo/linhas reais.
8. Remover ou sinalizar referências inválidas; se faltar suporte, abster-se ou pedir refinamento.
9. Persistir resposta, evidências e métricas.

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
| Versões de Java, Spring Boot e Spring AI | D-014 |
| Maven ou Gradle | D-015 (Maven) |
| JPA, JDBC ou abordagem híbrida | D-016 (sem JPA) |
| Busca lexical e algoritmo de fusão | D-017 (provisória); comparação final em O-005 |
| Provedor inicial de chat e embeddings | O-004 (em aberto) |
| Biblioteca de UI e estado do frontend | Etapa 7 (D-026) |
