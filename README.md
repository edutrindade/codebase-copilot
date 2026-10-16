# Codebase Copilot

Ferramenta local-first que responde perguntas sobre um repositório com evidências verificáveis (arquivo + linhas). A documentação do produto e da arquitetura está em [`docs/`](docs/README.md), e as decisões técnicas em [`docs/10-DECISIONS.md`](docs/10-DECISIONS.md).

> Estado atual: Etapa 0 do [roadmap](docs/09-IMPLEMENTATION-ROADMAP.md). Só o backend existe; o frontend (`apps/web`) entra na Etapa 7.

## Pré-requisitos

- **JDK 25** no `PATH` (`java -version` deve mostrar 25).
- **Docker** com Docker Compose (para o PostgreSQL 17 + pgvector e para os testes de integração com Testcontainers).

O Maven não precisa ser instalado: use o wrapper `./mvnw`.

## Configuração

Copie o arquivo de exemplo e troque a senha:

```bash
cp .env.example .env
```

| Variável | Uso |
|---|---|
| `DB_NAME` | Nome do banco |
| `DB_USER` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco (troque o valor de exemplo) |
| `DB_PORT` | Porta do PostgreSQL no host |
| `PROJECTS_ROOT` | Opcional (a partir da Etapa 1). Caminho absoluto da pasta sob a qual ficam os repositórios que podem ser importados; gera a raiz permitida de key `default` (D-029). Vazio: só o fluxo ZIP fica disponível. |
| `PROJECTS_ROOT_LABEL` | Opcional. Rótulo da raiz `default` na UI. Padrão: "Projetos". |
| `DATA_DIR` | Opcional (a partir da Etapa 2). Diretório de dados da aplicação; imports ZIP são extraídos em `DATA_DIR/imports/<id>` (D-028). Padrão: `~/.codebase-copilot`; um `~` inicial é expandido pela aplicação. |

A porta do banco é definida uma única vez, em `DB_PORT` no `.env`, e usada em dois lugares: o Compose publica o container em `127.0.0.1:${DB_PORT}`, e o backend se conecta em `localhost:${DB_PORT}` (`application.yml`). Se a porta 5432 já estiver ocupada, altere apenas `DB_PORT`.

### Raízes permitidas e diretório de dados

O backend só lê pastas locais dentro das raízes permitidas; a UI navega nelas e envia apenas caminhos relativos (D-018). Na inicialização, a aplicação valida cada raiz (caminho absoluto, existente, diretório legível; key única no formato `[a-z0-9-]+`) e não inicia se alguma for inválida.

Para configurar **várias raízes**, copie o exemplo versionado e ajuste os caminhos:

```bash
cp apps/backend/config/application.yml.example apps/backend/config/application.yml
```

O `config/application.yml` é carregado automaticamente pelo Spring ao executar a partir de `apps/backend` e é ignorado pelo Git. A lista substitui por inteiro a raiz `default` gerada por `PROJECTS_ROOT`; o exemplo já a repete. Os testes não dependem desse arquivo: cada teste declara as próprias raízes.

O `DATA_DIR` precisa ficar **fora** de todas as raízes permitidas e fora deste repositório; caso contrário, a aplicação não inicia.

O `.env` está no `.gitignore` e nunca deve ser commitado.

## Subir o banco

Na raiz do repositório:

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

As extensões `vector` e `pg_trgm` são criadas pelo Flyway quando o backend inicia.

## Build e testes

```bash
cd apps/backend
./mvnw verify
```

O `verify` executa:

- testes unitários e de integração (os de integração sobem o próprio PostgreSQL via Testcontainers; não dependem do banco do Compose nem do `.env`);
- verificação de formatação com Spotless (google-java-format, estilo AOSP). Para corrigir a formatação: `./mvnw spotless:apply`;
- comparação do contrato OpenAPI gerado com o arquivo versionado `apps/backend/openapi.json` (D-023). Após uma mudança intencional na API, regenere com `./mvnw verify -Dopenapi.update=true` e commite o `openapi.json` junto com o código.

## Executar o backend

```bash
cd apps/backend
./mvnw spring-boot:run
```

**O backend deve ser executado a partir de `apps/backend`.** O `application.yml` importa o `.env` pelo caminho relativo `../../.env`; executado de outro diretório, as variáveis `DB_*` não são encontradas e a aplicação não inicia.

Com o backend no ar:

- Health check: <http://localhost:8080/actuator/health>
- Contrato OpenAPI: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
