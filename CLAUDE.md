# Codebase Copilot

Ferramenta local-first que responde perguntas sobre um repositório com
evidências (arquivo + linhas). Documentação completa em `docs/`.
Decisões técnicas: `docs/10-DECISIONS.md` (consulte antes de mudar a stack).

## Stack

Java 25, Spring Boot 4.1.x, Maven (use `./mvnw`), PostgreSQL 17 + pgvector,
Flyway, Spring Data JDBC + JdbcClient (sem JPA), SDK `openai-java` (sem Spring AI, D-014)
apenas dentro do módulo `providers`. Frontend futuro: React + Vite + TypeScript.

## Comandos

- Banco: `docker compose -f infra/docker-compose.yml --env-file .env up -d`
- Testes/build (em apps/backend): `./mvnw verify`
- Rodar: `./mvnw spring-boot:run`

## Regras

- Monólito modular por pacotes (workspace, project, importjob, indexing,
  codeanalysis, retrieval, answering, providers, history, shared).
- Tipos do SDK de provedores (`com.openai`) nunca vazam para o domínio; use as portas
  internas de `providers`.
- Retrieval em SQL explícito, com testes de integração (Testcontainers).
- Migrações apenas via Flyway; nunca editar uma migração já aplicada.
- Nunca executar código do repositório importado.
- Jamais escrever códigos em idioma diferente do inglês.
- Nunca ler, imprimir ou commitar `.env` ou segredos.
- Não logar conteúdo de código nem prompts.

## Fluxo de trabalho

- Siga as etapas de `docs/09-IMPLEMENTATION-ROADMAP.md`, uma por vez.
- Proponha um plano curto antes de implementar mudanças grandes.
- Todo código novo vem com testes; `./mvnw verify` deve passar.
- Atualize `docs/10-DECISIONS.md` quando uma decisão mudar.

## Idioma

- Código-fonte (identificadores, comentários, logs, nomes de testes): inglês.
- Mensagens exibidas ao usuário (API e UI) e documentação em `docs/`: português (pt-BR).
- Códigos de erro estáveis (`code`) em inglês, ex.: `PROJECT_NOT_FOUND`.
