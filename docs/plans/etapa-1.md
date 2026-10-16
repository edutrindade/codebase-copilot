# Plano — Etapa 1: Fundação do domínio (workspace e projeto)

**Status:** aprovado em 15/10/2026, aguardando implementação.
**Referências:** `09-IMPLEMENTATION-ROADMAP.md` (Etapa 1), D-016, D-020 a D-025, D-029 a D-032.

## Escopo

**Dentro:**
- infraestrutura de erros e `requestId`;
- raízes locais (configuração e validação);
- CRUD essencial de workspace e projeto (criar, consultar, listar e remover), com paginação e duplicidade.

**Fora (e para onde vai):**

| Item | Etapa |
|---|---|
| Rename (`PATCH`) de workspace ou projeto | sem previsão |
| `GET /local-roots` e navegação nas raízes | Etapa 2 |
| `projects.current_import_job_id` | Etapa 2, junto com `import_jobs` |
| `409 PROJECT_BUSY` | Etapa 2 |
| `active_snapshot_id` e campos de snapshot ou indexação na resposta do projeto | Etapa 3 |

## Princípios de execução

- Cada fatia é entregável e testável sozinha e termina com `./mvnw verify` verde.
- Mudanças de API regeneram e commitam o `openapi.json` (`-Dopenapi.update=true`, D-023).
- Persistência:
  - **Escrita:** Spring Data JDBC; o insert é explícito via `JdbcAggregateTemplate.insert()`, porque o ID é gerado na aplicação e um `save()` com ID preenchido faria `UPDATE`.
  - **Listagens com contagem:** `JdbcClient` com SQL explícito.
- **Dados de teste:** `TRUNCATE` antes de cada teste de integração.
- **Contextos de teste:**
  - uma configuração de raízes única e compartilhada pelos testes de integração;
  - um único contexto adicional, "sem raízes", para maximizar o cache de contexto e evitar subir vários PostgreSQL.

## Fatias, na ordem

### F0 — Infraestrutura transversal (`shared`, sem migração)

**Entregas**
- Bean `Clock` no `shared`. O `IdGenerator` já existe.
- **`RequestIdFilter`:**
  - gera um UUID v7 por requisição e ignora `X-Request-Id` vindo do cliente;
  - coloca o ID no MDC (`requestId`) e o devolve no cabeçalho `X-Request-Id` de toda resposta;
  - limpa o MDC no `finally`.
- **Padrão de log:** inclui `%X{requestId}`.
- **Códigos de erro:** enum `ErrorCode` (código mais status HTTP) e exceção `ApiException(ErrorCode)`.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`):
  - produz `ProblemDetail` com `code`, `requestId` e, em validação, `errors[{field, message}]`, sem valor rejeitado;
  - mapeia os códigos genéricos: `MALFORMED_REQUEST`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` e `INTERNAL_ERROR`;
  - `INTERNAL_ERROR` usa textos fixos, nunca a mensagem da exceção; a exceção vai para o log com o `requestId`;
  - UUID malformado no path responde `VALIDATION_FAILED`, com `field` igual ao nome do parâmetro.
- **Mensagens:** `messages.properties` (pt-BR, UTF-8) com `error.<CODE>.title` e `error.<CODE>.detail`, mais as mensagens de Bean Validation; `fallbackToSystemLocale=false`.
- **Paginação:** `PageRequestParams`, que valida `page >= 0` e `1 <= size <= 100`, e `PageResponse<T>` (D-030).
- **Regra ArchUnit** (D-031): `shared` não depende de módulos de domínio. Exige a nova dependência de teste `archunit-junit5`, com versão fixada no `pom.xml`.

**Testes**
- Unitário: todo `ErrorCode` tem `title` e `detail` no `MessageSource`. O teste falha se faltar chave.
- Unitário: limites de `page` e `size`.
- Integração (MockMvc com um controller só de teste, em `src/test`):
  - cada categoria de erro devolve `application/problem+json`, `code` correto, títulos em pt-BR e `requestId` igual ao cabeçalho `X-Request-Id`;
  - `VALIDATION_FAILED` traz `errors` com apenas `field` e `message`, e o valor enviado não aparece em nenhum lugar do corpo;
  - `INTERNAL_ERROR`: a mensagem da exceção (um marcador único) não aparece no corpo; o corpo traz apenas campos fixos e `requestId`;
  - `X-Request-Id` enviado pelo cliente é ignorado;
  - rota inexistente → `RESOURCE_NOT_FOUND`; método errado → `METHOD_NOT_ALLOWED`; `Content-Type` errado → `UNSUPPORTED_MEDIA_TYPE`; JSON ilegível → `MALFORMED_REQUEST`;
  - o `requestId` aparece no log (captura de saída) e não fica no MDC depois da requisição.
- ArchUnit: a regra de `shared` passa.

### F1 — Workspace (V2)

**Entregas**
- Migração V2 (abaixo).
- Agregado `Workspace`, `WorkspaceRepository`, `WorkspaceService` e `WorkspaceController`.
- Endpoints: `POST /workspaces`, `GET /workspaces/{id}`, `GET /workspaces` (paginado) e `DELETE /workspaces/{id}`.
- **Nome** (D-032):
  - opcional; ausente, `null` ou vazio após o `trim` vira "Meus projetos";
  - passa por `trim` e NFC; depois disso, no máximo 100 code points e sem caracteres de controle.
- **Ainda não incluídos nesta fatia:** o `DELETE` não verifica projetos e não há `projectCount`. Os dois entram na F5.

**Testes**
- Unitário (com `IdGenerator` e `Clock` determinísticos): nome padrão; `trim`; NFC; limite de 100 contado após a normalização; caracteres de controle rejeitados.
- Integração do repositório: insert e busca; ordenação `created_at DESC, id DESC` com timestamps iguais; o `CHECK` de nome no banco rejeita nomes vazios ou longos.
- Contrato da API:
  - `201` com `id`, `name` e `createdAt`; corpo vazio `{}` gera "Meus projetos";
  - nome com 101 caracteres → 400 com `errors[0].field = "name"`;
  - UUID inexistente → `404 WORKSPACE_NOT_FOUND`; UUID malformado → `400 VALIDATION_FAILED`;
  - paginação: padrão `0/20`; página além da última → `items` vazio; `size=0`, `size=101` e `page=-1` → 400; `totalItems` e `totalPages` corretos;
  - `DELETE` → 204, e o `GET` seguinte → 404.
- ArchUnit: `workspace` não depende de `project`.

### F2 — Raízes locais (`shared.localroots`, sem endpoint)

**Entregas**
- `LocalRootsProperties` (`app.local-roots[n]`: `key`, `label`, `path`).
- **Mapeamento de `PROJECTS_ROOT` e `PROJECTS_ROOT_LABEL`** (padrão "Projetos") para a raiz `default`:
  - a entrada `default` é descartada **somente** quando vem do atalho com `PROJECTS_ROOT` vazio;
  - em qualquer outra entrada, `path` vazio impede a inicialização.
- **Validação na inicialização** (a aplicação não sobe se falhar):
  - `path` absoluto, existente, diretório e legível;
  - `key` única, no formato `[a-z0-9-]+`;
  - `label` ausente usa a `key`.
- **Bean `LocalRoots`:** `isEmpty()`, `find(key)` e `list()`. O `path` absoluto nunca sai deste pacote para a API.
- **Isolamento dos testes:** as propriedades de teste sobrepõem `.env` e `config/application.yml` locais (D-029).
- `apps/backend/config/application.yml.example` revisado.

**Testes**
- Com `ApplicationContextRunner`, sem banco. A inicialização falha para:
  - `path` relativo, inexistente ou que aponta para um arquivo;
  - diretório sem permissão de leitura (ignorado se o SO não permitir simular);
  - `key` duplicada ou fora do formato.
- Ainda com `ApplicationContextRunner`:
  - `label` ausente vira a `key`;
  - `PROJECTS_ROOT` vazio não cria raiz; `PROJECTS_ROOT` válido cria `default/Projetos`;
  - `PROJECTS_ROOT_LABEL` é respeitado;
  - várias raízes por lista.
- Teste de isolamento: com valores fictícios simulando a configuração do desenvolvedor, o contexto de teste continua vendo apenas as raízes que ele mesmo declara.

### F3 — Projeto ZIP e operações comuns (V3)

**Entregas**
- Migração V3 (abaixo).
- Agregado `Project` (`AggregateReference` para o workspace), `ProjectRepository`, `ProjectService` e `ProjectController`.
- **Endpoints:**
  - `POST /workspaces/{id}/projects` (nesta fatia, apenas `ZIP`; `LOCAL_FOLDER` entra na F4);
  - `GET /projects/{id}`;
  - `GET /workspaces/{id}/projects` (paginado);
  - `DELETE /projects/{id}`.
- **Nome do projeto** (D-032): obrigatório; `trim` e NFC; não vazio; no máximo 100 code points; sem caracteres de controle.
- Request como DTO plano (`name`, `sourceType`, `rootKey`, `relativePath`), com validação entre campos.
- **Resposta polimórfica em `source`:**
  - `LOCAL_FOLDER`: `type`, `rootKey`, `relativePath`;
  - `ZIP`: `type`, `currentImport` (sempre `null` nesta etapa).
- **Precedência** (D-032): para `ZIP`, apenas `VALIDATION_FAILED`, depois `WORKSPACE_NOT_FOUND`.

**Testes**
- Integração do repositório:
  - os `CHECK`s rejeitam, direto no SQL: `ZIP` com `source_root_key`; `LOCAL_FOLDER` sem `source_relative_path`; `source_type` desconhecido;
  - a FK impede projeto em workspace inexistente.
- Contrato da API:
  - `ZIP` → 201 com `source.type = "ZIP"` e `source.currentImport = null`;
  - nome ausente, vazio após o `trim` ou com 101 caracteres → 400;
  - `rootKey` ou `relativePath` informados com `ZIP` → 400;
  - `sourceType` desconhecido → `400 MALFORMED_REQUEST`;
  - workspace inexistente → 404, e o corpo inválido continua respondendo 400 antes do 404 (ordem da D-032);
  - `GET` e listagem paginada com ordenação; listagem de workspace inexistente → 404;
  - `DELETE` → 204, e o `GET` seguinte → `404 PROJECT_NOT_FOUND`.

### F4 — Projeto LOCAL_FOLDER

**Entregas**
- `RelativePathNormalizer`, função pura no módulo `project` (D-020, D-032):
  - NFC; `/` como separador; barras repetidas colapsadas; barras finais e segmentos `.` removidos;
  - `""`, `"."` e `"./"` viram `""`;
  - `/` inicial ou segmento `..` → `PATH_OUTSIDE_ROOT`;
  - NUL, `\` ou caracteres de controle → `VALIDATION_FAILED`.
- **Precedência completa** (D-032):
  1. `VALIDATION_FAILED` (inclui caracteres inválidos em `relativePath`);
  2. `WORKSPACE_NOT_FOUND`;
  3. `LOCAL_ROOTS_NOT_CONFIGURED`;
  4. `ROOT_NOT_FOUND`;
  5. `PATH_OUTSIDE_ROOT`;
  6. `PROJECT_ALREADY_EXISTS`.
- **Duplicidade** pela violação do índice único parcial, traduzida para 409. Não há `SELECT` prévio, o que é seguro sob concorrência.
- A criação não acessa o sistema de arquivos.

**Testes**
- Unitário do normalizador, com tabela de casos:
  - `a//b/` → `a/b`; `./a/./b` → `a/b`; `""`, `"."`, `"./"` e `"//"` → `""`;
  - NFD e NFC equivalentes → mesma saída;
  - `Foo` e `foo` continuam diferentes;
  - `/abs`, `..`, `a/../b` e `a/..` → `PATH_OUTSIDE_ROOT`;
  - `a\b`, `a\u0000b` e `a\u0007b` → `VALIDATION_FAILED`;
  - `\` combinado com `..` → `VALIDATION_FAILED` (o passo 1 vence).
- Contrato da API, no contexto com raízes:
  - 201 devolve o `relativePath` normalizado;
  - `rootKey` ou `relativePath` ausentes → 400;
  - `rootKey` inexistente → `404 ROOT_NOT_FOUND`;
  - `../x` → `400 PATH_OUTSIDE_ROOT`;
  - `a` e `a/` no mesmo workspace → `409 PROJECT_ALREADY_EXISTS`;
  - mesmo caminho em outro workspace → 201; nomes iguais → 201; `Foo` e `foo` → ambos 201;
  - pasta inexistente dentro da raiz → 201, provando que não há acesso a disco.
- Contrato da API, no contexto sem raízes:
  - `LOCAL_FOLDER` → `409 LOCAL_ROOTS_NOT_CONFIGURED`;
  - workspace inexistente continua respondendo 404 antes (passo 2 antes do 3);
  - `ZIP` continua → 201.
- Precedência: um caso por fronteira entre passos vizinhos (1/2, 2/3, 3/4, 4/5, 5/6).

### F5 — Integração workspace ↔ project

**Entregas**
- Porta `WorkspaceProjectCounter` em `workspace` (contagem individual e em lote), implementada em `project` (D-031).
- `projectCount` em `GET /workspaces` (uma consulta em lote por página, sem N+1) e em `GET /workspaces/{id}`.
- `DELETE /workspaces/{id}`: verificação pela porta → `409 WORKSPACE_NOT_EMPTY`. A violação da FK `RESTRICT` (projeto criado em concorrência) também é traduzida para `409 WORKSPACE_NOT_EMPTY`, nunca 500.

**Testes**
- Integração: `projectCount` correto na lista e no detalhe, inclusive 0 e workspaces com contagens diferentes na mesma página.
- Contrato da API:
  - workspace com projeto → `DELETE` responde `409 WORKSPACE_NOT_EMPTY`;
  - depois de remover os projetos → 204.
- **Concorrência (FK `RESTRICT`):**
  - a porta é substituída por um dublê que responde 0 (`@MockitoBean`), e um projeto é inserido diretamente via `JdbcClient`;
  - o `DELETE` passa pela verificação, a FK dispara, e a resposta é `409 WORKSPACE_NOT_EMPTY` com `ProblemDetail` completo, não 500;
  - o workspace continua existindo.
- ArchUnit: `workspace` não depende de `project` (continua passando com a porta).

### F6 — Fechamento

- Aceite da etapa: workspace e projeto podem ser criados, listados e removidos, com testes.
- `openapi.json` final commitado.
- Conferir `05`, `06` e `10-DECISIONS.md` contra o comportamento implementado.
- Atualizar o README se algum comando ou variável mudar.

## Migrações

### V2__create_workspaces.sql

```sql
CREATE TABLE workspaces (
    id         uuid        PRIMARY KEY,
    name       text        NOT NULL
                           CHECK (char_length(name) BETWEEN 1 AND 100),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX workspaces_created_at_id_idx
    ON workspaces (created_at DESC, id DESC);
```

### V3__create_projects.sql

```sql
CREATE TABLE projects (
    id                   uuid        PRIMARY KEY,
    workspace_id         uuid        NOT NULL
                                     REFERENCES workspaces (id) ON DELETE RESTRICT,
    name                 text        NOT NULL
                                     CHECK (char_length(name) BETWEEN 1 AND 100),
    source_type          text        NOT NULL
                                     CHECK (source_type IN ('LOCAL_FOLDER', 'ZIP')),
    source_root_key      text,
    source_relative_path text,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    CONSTRAINT projects_source_consistency CHECK (
        (source_type = 'LOCAL_FOLDER'
            AND source_root_key IS NOT NULL
            AND source_relative_path IS NOT NULL)
     OR (source_type = 'ZIP'
            AND source_root_key IS NULL
            AND source_relative_path IS NULL)
    )
);

CREATE UNIQUE INDEX projects_local_folder_unique_idx
    ON projects (workspace_id, source_root_key, source_relative_path)
    WHERE source_type = 'LOCAL_FOLDER';

CREATE INDEX projects_workspace_created_at_id_idx
    ON projects (workspace_id, created_at DESC, id DESC);
```

Os nomes das constraints são estáveis porque o código traduz as violações pelo nome:
- `projects_local_folder_unique_idx` → `PROJECT_ALREADY_EXISTS`;
- a FK de `workspace_id` → `WORKSPACE_NOT_EMPTY`.

Por isso, a FK deve ser nomeada explicitamente na implementação (ex.: `projects_workspace_id_fkey`).

Fora da V3: `current_import_job_id` (Etapa 2) e `active_snapshot_id` (Etapa 3).

## Riscos

| Risco | Mitigação |
|---|---|
| `save()` do Spring Data JDBC faz `UPDATE` quando o ID vem preenchido. | Insert explícito via `JdbcAggregateTemplate.insert()`, coberto pelos testes de repositório. |
| `.env` ou `config/application.yml` locais vazam para os testes. | Propriedades de teste com precedência maior e um teste de isolamento (F2). |
| Muitos contextos Spring sobem vários PostgreSQL. | Uma configuração de raízes compartilhada e um contexto adicional "sem raízes". |
| Tradução de violações dependente do nome da constraint. | Nomes fixados na migração e testes que provocam cada violação. |
| Contagem de projetos com N+1 na listagem. | Porta com contagem em lote. |
| `ApplicationContextRunner` não consegue simular diretório ilegível em todo SO. | Teste condicional (`assumeTrue`), registrado como limitação. |
