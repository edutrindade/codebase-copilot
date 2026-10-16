# Roadmap de implementação

## Princípios de execução

- Entregar fatias verticais testáveis.
- Criar avaliação do RAG antes de otimizações avançadas.
- Manter monólito modular.
- Não começar por agentes autônomos nem por recursos de edição de código.
- Cada etapa termina com critérios de aceite e documentação atualizada.

## Etapa 0 — Decisões e ambiente

**Objetivo:** tornar o projeto reproduzível.

- Confirmar versões de Java, Spring Boot e Spring AI.
- Escolher Maven ou Gradle.
- Criar monorepo com o backend; o scaffold do frontend (`apps/web`) fica para a Etapa 7 (D-026).
- Criar Docker Compose para PostgreSQL com pgvector.
- Configurar Flyway, testes, lint/format e `.env.example`.
- Publicar OpenAPI inicial.

**Aceite:** outra pessoa consegue iniciar o ambiente seguindo o README, sem segredos versionados.

## Etapa 1 — Fundação do domínio

**Status:** concluída em 15/10/2026 (plano, testes e desvios em `docs/plans/etapa-1.md`).

- Criar módulos de workspace e projeto.
- Criar esquema inicial por Flyway (`workspaces`, `projects` sem `active_snapshot_id`; ver `05-DATA-MODEL.md`).
- Configurar e validar na inicialização as raízes permitidas (D-029).
- Implementar CRUD essencial, com projetos `LOCAL_FOLDER` e `ZIP` (sem upload), duplicidade (D-020) e paginação (D-030).
- Criar tratamento de erro padronizado: ProblemDetail com `code`, `requestId` e mensagens pt-BR via `MessageSource` (D-022, D-025).
- Adicionar testes de integração com PostgreSQL.
- Plano detalhado: `docs/plans/etapa-1.md`.

**Aceite:** workspace e projeto podem ser criados, listados e removidos com testes.

## Etapa 2 — Importação e inventário seguro

**Status:** concluída em 16/10/2026 (plano, testes e desvios em `docs/plans/etapa-2.md`; decisões D-033 a D-038).

- Implementar a navegação nas raízes permitidas (`GET /local-roots`, D-018, D-029).
- Implementar `DATA_DIR` com validação na inicialização (D-028).
- Implementar upload ZIP com limites e concorrência (D-028).
- Adicionar `projects.current_import_job_id` (FK para `import_jobs`) e o `409 PROJECT_BUSY` em `DELETE /projects/{id}` (D-021), adiados da Etapa 1 por não existirem jobs.
- Validar caminhos, links e limites.
- Implementar exclusões padrão e customizadas.
- Gerar inventário por linguagem, diretório e estado.
- Na pré-análise, verificar existência da pasta e links simbólicos e avisar sobre projetos que diferem apenas por maiúsculas/minúsculas (D-020).
- Não executar conteúdo importado.

**Aceite:** testes de segurança demonstram que arquivos não escapam da raiz e ZIPs maliciosos são rejeitados.

## Etapa 3 — Snapshots, hashes e chunking

**Status:** concluída em 16/10/2026 (plano, testes e desvios em `docs/plans/etapa-3.md`; decisões D-039 a D-046).

- Criar snapshots e jobs.
- Calcular hashes de arquivos.
- Implementar chunking genérico com linhas.
- Registrar linguagem e metadados.
- Criar estratégia de reindexação incremental.
- Testar idempotência e recuperação de falhas.

**Aceite:** arquivos novos, alterados e removidos são refletidos corretamente.

## Etapa 4 — Embeddings e busca vetorial

- Definir porta de embeddings.
- Integrar provedor inicial por meio de adaptador.
- Armazenar vetores no pgvector.
- Implementar busca vetorial e filtros por projeto/snapshot.
- Criar cache por hash/modelo.

**Aceite:** consultas vetoriais retornam chunks do snapshot correto e os vetores têm dimensão compatível.

## Etapa 5 — Busca lexical e recuperação híbrida

- Implementar busca lexical.
- Preservar identificadores e caminhos.
- Combinar rankings.
- Criar corpus de avaliação inicial.
- Medir lexical, vetorial e híbrida.

**Aceite:** resultados são reprodutíveis e o baseline híbrido é comparado com os baselines individuais.

## Etapa 6 — Resposta com evidências

- Definir porta de geração.
- Montar contexto com limite de tokens.
- Implementar prompt de evidência e abstenção.
- Validar IDs de evidência e linhas no backend.
- Persistir perguntas, respostas, evidências e métricas.

**Aceite:** citações inválidas são rejeitadas/sinalizadas e perguntas sem evidência suficiente podem se abster.

## Etapa 7 — UX completa do MVP

- Workspace e lista de projetos.
- Fluxo de importação com pré-análise.
- Progresso e erros da indexação.
- Inventário e snapshot ativo.
- Chat e painel de evidências.
- Visualizador de código.
- Histórico e métricas.

**Aceite:** usuário consegue importar, indexar, perguntar e abrir evidências sem usar ferramentas internas.

## Etapa 8 — Qualidade e hardening

- Ampliar corpus de avaliação.
- Testar prompt injection e importações maliciosas.
- Medir latência, custo e memória.
- Melhorar ranking/chunking apenas com evidência de avaliação.
- Documentar limitações e recuperação de falhas.

**Aceite:** resultados do conjunto de avaliação e riscos conhecidos são documentados.

## Etapa 9 — Recursos posteriores ao MVP

Priorizar após validar o núcleo:
- adaptadores AST para linguagens prioritárias;
- comparação entre snapshots;
- exportação de mapa arquitetural;
- geração assistida de `AGENTS.md` com evidências e revisão humana;
- provedor local de modelos;
- worker separado, se medições justificarem;
- integração com Git, caso exista demanda clara.

## Fora do roadmap inicial

Não implementar inicialmente:
- execução de comandos no repositório;
- agentes autônomos;
- edição automática de código;
- criação de commits/PRs;
- arquitetura de microserviços;
- SaaS multiusuário.
