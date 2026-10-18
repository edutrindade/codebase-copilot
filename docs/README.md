# Codebase Copilot

Documentação inicial do **Codebase Copilot**, uma ferramenta local-first para ajudar desenvolvedores a entender uma base de código, investigar decisões arquiteturais e responder perguntas com evidências verificáveis.

## Como usar esta documentação

Leia nesta ordem:

1. [Resumo executivo](RESUMO-EXECUTIVO.md)
2. [PRD — requisitos do produto](01-PRD.md)
3. [Arquitetura e decisões técnicas](02-ARCHITECTURE.md)
4. [UX e onboarding](03-UX-ONBOARDING.md)
5. [Pipeline RAG](04-RAG-PIPELINE.md)
6. [Modelo de dados](05-DATA-MODEL.md)
7. [Contratos da API](06-API-CONTRACTS.md)
8. [Segurança, privacidade e custos](07-SECURITY-PRIVACY-COSTS.md)
9. [Avaliação de qualidade](08-EVALUATION.md)
10. [Roadmap de implementação](09-IMPLEMENTATION-ROADMAP.md)
11. [Registro de decisões](10-DECISIONS.md)
12. [Operação, recuperação de falhas e limitações](11-OPERATIONS.md)
13. [Configuração e desenvolvimento](12-DEVELOPMENT.md)

## Stack aprovada

- **Frontend:** React, Vite e TypeScript.
- **Backend:** Java com Spring Boot, organizado como monólito modular.
- **IA/RAG:** SDK oficial do provedor atrás de portas internas, substituível sem tocar no domínio (o Spring AI saiu na Etapa 6, D-014).
- **Persistência:** PostgreSQL com pgvector; SQL explícito para a recuperação híbrida quando necessário.
- **Migrações:** Flyway.
- **Execução local:** Docker Compose para dependências de infraestrutura.
- **Contrato HTTP:** REST e OpenAPI.
- **Indexação:** processamento assíncrono dentro da aplicação inicialmente; separar um worker apenas se medições justificarem.

## Princípios

- Local-first não significa automaticamente offline: um provedor remoto pode receber trechos de código selecionados.
- Nunca executar código do repositório importado.
- Toda resposta factual sobre o código deve apontar para evidências localizáveis.
- O produto deve declarar incerteza e admitir quando não encontrou evidência suficiente.
- Suporte multilíngue começa com indexação textual genérica; análise AST é adicionada por adaptadores.
- O MVP não inclui agentes autônomos nem alterações automáticas no código.

## Status

Implementação: Etapa 9 (interface premium) concluída em 18/10/2026; o MVP roda num processo só (`java -jar`, UI e API), com os resultados da avaliação lexical e de desempenho no [`08`](08-EVALUATION.md) e as limitações conhecidas em [`11-OPERATIONS.md`](11-OPERATIONS.md). As medições com o provedor real ficam para quando houver saldo. O andamento de cada etapa está no [roadmap](09-IMPLEMENTATION-ROADMAP.md), e os planos com testes e desvios em [`plans/`](plans/).

Este pacote é a base documental para orientar as decisões e a implementação. Itens marcados como **a validar** devem ser decididos por meio de protótipos, benchmarks ou testes, e não tratados como fatos já comprovados.
