# UX e onboarding — Codebase Copilot

## 1. Princípios de experiência

- O usuário precisa entender o que será lido e o que será excluído antes de indexar.
- O progresso deve ser concreto e recuperável, não uma animação sem explicação.
- Respostas precisam facilitar a verificação da fonte.
- O custo e o destino dos dados devem ser explícitos.
- Evitar telas de configuração avançada antes de o usuário obter valor.

## 2. Navegação proposta

- **Workspace:** lista de projetos e atividade recente.
- **Projeto:** resumo, linguagens, diretórios, snapshot e estado da indexação.
- **Perguntar:** chat contextualizado ao projeto/snapshot.
- **Evidências:** lista de trechos recuperados com caminho, linhas e pontuação/metadados úteis.
- **Histórico:** perguntas anteriores e respostas associadas.
- **Configurações:** provedor, privacidade, exclusões, limites e estimativas.

No MVP (Etapa 7), exclusões e limites ficam na pré-análise, que também serve a projetos já indexados; provedor e preços continuam na configuração do servidor (`.env`), e a UI só mostra o que está configurado e para onde vão os trechos (D-073).

Desde a Etapa 9 (D-089, D-090, D-092): uma barra lateral com o seletor de workspace, os projetos e o estado de cada um, as conversas recentes e "Sistema e privacidade" (em gaveta abaixo de 48rem); uma barra superior com a trilha, a busca (⌘K), o indicador Local/Remoto e o tema. Dentro do projeto, abas como links: Visão geral · Perguntar · Arquivos · Histórico · Configurações. A paleta de comandos chega a qualquer seção, projeto ou conversa recente, e os atalhos (`g` + `o`/`p`/`a`/`h`, `/`, `[`/`]`, `?`) podem ser desligados em `/system`. Exclusões e limites também ficam em Configurações, fora do assistente.

## 3. Fluxo de primeiro uso

### Etapa 1 — Criar workspace
Solicitar nome opcional e explicar que os dados são armazenados localmente por padrão.

### Etapa 2 — Importar projeto
Ação principal: selecionar uma pasta local. Alternativa: enviar ZIP.

A interface deve explicar:
- quais arquivos serão lidos;
- que o sistema não executa o código;
- que modelos remotos podem receber trechos selecionados;
- limites de tamanho e tipos de arquivo suportados.

### Etapa 3 — Pré-análise
Exibir:
- número de arquivos encontrados;
- linguagens detectadas;
- tamanho estimado;
- arquivos excluídos e motivos;
- arquivos grandes ou suspeitos;
- opção para ajustar padrões de exclusão.

### Etapa 4 — Indexação
Mostrar etapas, por exemplo:
1. Inventário.
2. Filtragem.
3. Extração e chunking.
4. Embeddings.
5. Persistência e verificação.

Exibir progresso por etapa, quantidade concluída, falhas e ação para tentar novamente. A falha de um arquivo não deve necessariamente invalidar todo o projeto.

### Etapa 5 — Primeiro valor
Quando a indexação terminar, sugerir perguntas baseadas no inventário, como:
- “Resuma a arquitetura deste projeto.”
- “Quais são os pontos de entrada?”
- “Como a aplicação configura o banco de dados?”
- “Quais integrações externas aparecem no código?”

Essas sugestões devem ser geradas apenas quando houver evidência no inventário; não afirmar que um arquivo existe sem verificar.

No MVP, as sugestões são as quatro acima, genéricas e sem citar arquivo, mostradas só com snapshot ativo e com a conversa vazia; escolher uma preenche o campo, sem enviar (D-077).

## 4. Layout da tela de pergunta

Layout conceitual:
- **Coluna principal:** conversa e resposta.
- **Painel de evidências:** arquivos e linhas citados.
- **Visualizador de código:** abre o trecho selecionado e destaca as linhas.
- **Cabeçalho do projeto:** snapshot ativo, estado da indexação e opção de atualizar.

Em telas menores, os painéis podem virar abas.

No MVP, a partir de 64rem a conversa fica à esquerda e as evidências e o visualizador à direita; abaixo disso, as três áreas viram abas, e abrir uma citação leva à aba do código. O cabeçalho mostra o snapshot ativo, a indexação em andamento e o aviso de desatualizado, com link para o resumo do projeto, onde fica "indexar de novo" (D-075).

Desde a Etapa 9 (D-091, D-093): um divisor redimensionável entre a conversa e a coluna lateral (30% a 70%, também pelas setas e por dois botões), com a largura lembrada; campo da pergunta fixo embaixo; resposta como cartão, com formatação leve e as citações mostrando uma prévia ao passar o mouse ou focar; evidências agrupadas por arquivo; visualizador com o caminho em trilha, "Da resposta / Atual", "Abrir em Arquivos" e realce de sintaxe.

## 5. Formato de resposta

Uma resposta deve conter:
- resposta direta;
- explicação e ressalvas;
- referências numeradas;
- caminho e linhas por referência;
- ação para abrir cada evidência;
- indicação de quando a evidência é indireta ou insuficiente.

Exemplo ilustrativo:

> O endpoint delega a operação ao serviço de pedidos.  
> **Evidência 1:** `src/.../OrderController.java`, linhas 42–58.  
> **Evidência 2:** `src/.../OrderService.java`, linhas 20–47.

Não exibir referências ilustrativas como se fossem arquivos reais. No produto, todas as referências devem vir do índice.

## 6. Estados e erros

- Pasta sem arquivos suportados.
- Todos os arquivos excluídos.
- Arquivo ilegível.
- Arquivo acima do limite.
- Provedor sem credenciais.
- Limite ou erro do provedor.
- Banco indisponível.
- Indexação parcial.
- Snapshot desatualizado.
- Nenhuma evidência suficiente.

Cada erro deve explicar o impacto e oferecer uma ação segura, sem expor segredo ou stack trace ao usuário final.

## 7. Transparência de privacidade e custo

Antes de usar um provedor remoto, apresentar:
- nome do provedor configurado;
- que trechos selecionados podem ser enviados;
- opção para rever exclusões;
- indicação de custo estimado, se calculável.

No histórico, diferenciar valores medidos, estimados e indisponíveis.

## 8. Acessibilidade e visual

Adotar visual limpo e funcional, com hierarquia clara, contraste adequado, navegação por teclado e estados que não dependam apenas de cor. A identidade visual final é decisão posterior; priorizar legibilidade do código e das evidências.

Identidade decidida na Etapa 9 (D-087, D-088): Inter na interface e JetBrains Mono no código (auto-hospedadas), tokens de cor em OKLCH com acento índigo, tema claro, escuro ou do sistema aplicado antes da primeira pintura, ícones Lucide, cores de realce com 4,5:1 sobre o código e as linhas citadas, e movimento curto, desligado com `prefers-reduced-motion`. A meta de acessibilidade passou a WCAG 2.2 AA, conferida pelo axe nos dois temas e em 390, 1024 e 1440 px.

## 9. Eventos úteis de produto

Sem conteúdo de código, medir:
- início e conclusão da importação;
- duração de indexação;
- taxa de falha;
- pergunta enviada;
- evidências abertas;
- resposta sem evidência;
- reindexação solicitada.

Esses eventos devem respeitar o modo local-first e não enviar telemetria externa por padrão.
