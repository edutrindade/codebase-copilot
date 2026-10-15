# PRD — Codebase Copilot

**Status:** proposta aprovada para orientar o MVP  
**Produto:** Codebase Copilot  
**Tipo:** ferramenta local-first para compreensão de código

## 1. Objetivo

Ajudar desenvolvedores a entender repositórios desconhecidos com menos exploração manual, oferecendo respostas fundamentadas em evidências recuperadas do código.

## 2. Problema do usuário

A compreensão inicial de um repositório costuma exigir leitura de muitos arquivos, busca manual, navegação entre camadas e perguntas a colegas. Respostas geradas por IA sem contexto recuperado e verificável podem alucinar nomes, fluxos e comportamentos.

## 3. Usuários e cenários

### Persona inicial

Desenvolvedor que recebe um repositório existente e precisa contribuir com segurança, localizar uma regra de negócio ou investigar um fluxo.

### Casos de uso prioritários

- “Como a autenticação funciona neste projeto?”
- “Onde é calculado o valor final desta operação?”
- “Quais módulos chamam este serviço?”
- “Como um dado sai da tela e chega ao banco?”
- “Quais arquivos sustentam esta explicação?”
- “O que mudou entre dois snapshots indexados?”

## 4. Princípios de produto

- **Evidência antes de confiança:** a resposta precisa mostrar de onde veio.
- **Abstenção honesta:** se não houver evidência suficiente, dizer isso.
- **Local-first:** o projeto e os índices ficam no ambiente do usuário por padrão.
- **Transparência:** informar exclusões, progresso, erros e estimativas de custo.
- **Reversibilidade:** permitir reindexar e trocar o provedor de IA.
- **Segurança por padrão:** conteúdo do repositório é dado não confiável, nunca instrução de sistema.
- **Multilíngue pragmático:** indexação genérica primeiro; análise estrutural por adaptador.

## 5. Escopo funcional do MVP

### F-01 — Criar workspace

O usuário cria um workspace e adiciona um projeto por seleção de pasta local ou importação de ZIP.

### F-02 — Pré-análise

Antes de indexar, exibir contagem estimada de arquivos, linguagens detectadas, tamanho aproximado e exclusões previstas. Permitir revisar padrões de exclusão.

### F-03 — Indexação

- Percorrer somente arquivos permitidos dentro da raiz importada.
- Ignorar dependências, artefatos gerados, binários e arquivos configurados como excluídos.
- Detectar alterações por hash.
- Dividir conteúdo em chunks com caminho, intervalo de linhas e metadados.
- Gerar embeddings por meio de um provedor intercambiável.
- Persistir chunks e vetores no PostgreSQL/pgvector.

### F-04 — Inventário

Mostrar estrutura de diretórios, linguagens, arquivos indexados/excluídos e estado do processamento.

### F-05 — Perguntas e respostas

Permitir perguntas sobre o projeto. A resposta deve incluir citações que apontem para arquivo e linhas existentes no snapshot consultado.

### F-06 — Visualização de evidências

Abrir o arquivo e destacar o trecho referenciado. Se o arquivo tiver mudado desde o snapshot, informar a diferença de versão.

### F-07 — Histórico

Persistir perguntas, respostas, evidências e metadados de execução para permitir revisão.

### F-08 — Snapshots e reindexação incremental

Criar snapshots de indexação e reprocessar somente arquivos novos ou alterados, removendo do índice os arquivos que deixaram de existir ou foram excluídos.

### F-09 — Métricas

Exibir duração, quantidade de evidências recuperadas, tokens quando informados pelo provedor e custo estimado quando houver dados suficientes. Não inventar custo quando o preço ou o uso não estiver disponível.

## 6. Requisitos não funcionais

- Não executar arquivos do repositório.
- Limitar tamanho e quantidade de arquivos importados.
- Proteger contra ZIP Slip/path traversal.
- Não indexar segredos conhecidos por padrões padrão.
- Não registrar conteúdo sensível em logs por padrão.
- Tornar o provedor de modelo substituível.
- Ter testes para segurança de importação, recuperação e validação de citações.
- Dar feedback de progresso e permitir recuperação de falhas.
- Documentar que o uso de modelos remotos pode enviar trechos selecionados a terceiros.

## 7. Fora do escopo

- Agentes que alteram o projeto.
- Execução de testes ou comandos do repositório.
- Commit, push ou criação de PR.
- Integrações com GitHub/GitLab por OAuth.
- Colaboração multiusuário hospedada.
- Análise semântica equivalente para toda linguagem existente.
- Geração automática de documentação considerada confiável sem revisão humana.

## 8. Fluxos principais

1. Criar workspace.
2. Selecionar pasta ou ZIP.
3. Rever o resumo de arquivos e exclusões.
4. Indexar e acompanhar progresso.
5. Consultar inventário ou fazer pergunta.
6. Inspecionar as evidências.
7. Reindexar quando o código mudar.

## 9. Critérios de aceite essenciais

- Toda citação emitida corresponde a um arquivo e a linhas válidas no snapshot.
- Respostas sem evidência suficiente podem se abster.
- Arquivos excluídos não são enviados ao provedor de embeddings ou geração.
- ZIP malicioso não consegue escrever fora do diretório de destino.
- Uma alteração de arquivo provoca reindexação somente do conteúdo afetado e das dependências necessárias.
- Erros de provedor são comunicados sem perder o histórico da pergunta.
- O usuário consegue distinguir custo conhecido, estimado e indisponível.

## 10. Riscos e hipóteses

- **Risco:** a busca híbrida recupera trechos relevantes, mas não suficientes para perguntas sobre fluxos amplos.
- **Risco:** arquivos muito grandes ou gerados degradam custo e latência.
- **Risco:** código contém instruções maliciosas direcionadas ao modelo.
- **Hipótese:** referências com caminho e linhas aumentam a confiança e a utilidade.
- **Hipótese:** um fluxo dedicado de onboarding diferencia o produto de chats genéricos.

Validar por meio do conjunto de avaliação definido em `08-EVALUATION.md`.
