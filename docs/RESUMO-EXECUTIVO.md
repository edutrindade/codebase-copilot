# Resumo executivo

## Visão

O Codebase Copilot é uma ferramenta local-first que acelera o entendimento de repositórios existentes. Seu foco é onboarding técnico, investigação e consulta à arquitetura, com respostas ancoradas em arquivos e linhas do código.

Não é um agente genérico que tenta executar tarefas autonomamente. O valor central é transformar uma base de código desconhecida em conhecimento pesquisável, reutilizável e auditável.

## Problema

Ao entrar em um projeto, um desenvolvedor precisa descobrir rapidamente:

- como o sistema está organizado;
- quais módulos são responsáveis por cada comportamento;
- onde estão as regras de negócio e integrações;
- como um fluxo atravessa frontend, API, persistência e serviços externos;
- quais evidências sustentam uma conclusão.

Perguntar a um modelo sem recuperação controlada pode gerar respostas convincentes, porém incorretas, desatualizadas ou sem referências úteis.

## Proposta de valor

1. Importar uma pasta local ou um ZIP.
2. Fazer inventário e indexação segura do projeto.
3. Recuperar contexto com busca lexical e vetorial.
4. Responder perguntas com referências de arquivo e linha.
5. Reaproveitar conhecimento entre perguntas e snapshots do repositório.
6. Expor métricas de recuperação, latência e consumo estimado.

## Público inicial

Desenvolvedores que precisam compreender uma base existente, especialmente em onboarding, manutenção, investigação de bugs e preparação de documentação técnica.

## MVP

- Importação de pasta local e ZIP.
- Exclusão configurável de dependências, artefatos gerados e arquivos sensíveis.
- Indexação textual multilíngue, com metadados de caminho e linhas.
- Adaptadores de análise estrutural para linguagens selecionadas, com fallback genérico.
- Embeddings e armazenamento em PostgreSQL/pgvector.
- Recuperação híbrida lexical + vetorial.
- Chat com citações verificáveis e validação das referências.
- Inventário do projeto e estado da indexação.
- Snapshots, hashes e reindexação incremental.
- Histórico de perguntas e métricas básicas de execução.

## Fora do MVP

- Execução de código importado.
- Edição automática de arquivos, commits ou pull requests.
- Agentes autônomos.
- Integrações OAuth com repositórios privados.
- Produto hospedado multiusuário.
- Promessa de análise semântica completa para todas as linguagens.
- Geração definitiva e automática de `AGENTS.md` antes de validar o RAG central.

## Arquitetura aprovada

- React + Vite + TypeScript no frontend.
- Java + Spring Boot no backend, em monólito modular.
- Spring AI encapsulado atrás de interfaces internas.
- PostgreSQL + pgvector.
- Flyway para migrações.
- REST/OpenAPI.
- Docker Compose para ambiente local.

## Critérios de sucesso

A qualidade será medida por recuperação de evidências relevantes, validade das citações, correção factual, capacidade de abstenção, latência e custo. As metas numéricas serão estabelecidas após criar um conjunto de avaliação e medir a linha de base.
