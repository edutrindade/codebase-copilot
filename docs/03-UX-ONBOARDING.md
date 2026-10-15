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

## 4. Layout da tela de pergunta

Layout conceitual:
- **Coluna principal:** conversa e resposta.
- **Painel de evidências:** arquivos e linhas citados.
- **Visualizador de código:** abre o trecho selecionado e destaca as linhas.
- **Cabeçalho do projeto:** snapshot ativo, estado da indexação e opção de atualizar.

Em telas menores, os painéis podem virar abas.

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
