# Segurança, privacidade e custos

## 1. Modelo de ameaça

O repositório importado é conteúdo não confiável. Pode conter:
- segredos;
- código malicioso;
- instruções de prompt injection;
- arquivos enormes ou compactados de forma abusiva;
- caminhos malformados;
- dados pessoais ou propriedade intelectual.

O objetivo é limitar o que o sistema lê, armazena, registra e envia para provedores.

## 2. Princípios de segurança

- Não executar código importado.
- Restringir leitura à raiz selecionada.
- Validar e normalizar caminhos.
- Aplicar limites de tamanho e quantidade.
- Tratar conteúdo do repositório como dados, nunca como instruções.
- Não enviar arquivos excluídos ao modelo.
- Não registrar segredos nem conteúdo completo por padrão.
- Minimizar permissões e dependências.
- Tornar explícito quando um provedor remoto recebe trechos.

## 3. Importação de pasta

O mecanismo de acesso à pasta deve:
- canonicalizar a raiz;
- rejeitar caminhos que escapem da raiz;
- tratar links simbólicos com política explícita;
- limitar profundidade, arquivos e tamanho;
- evitar seguir diretórios de dependências e artefatos;
- lidar com arquivos alterados durante a leitura;
- não executar hooks, scripts ou ferramentas do projeto.

A experiência de selecionar pasta em uma aplicação web local exige uma decisão técnica explícita; não presumir que o browser pode enviar qualquer caminho local ao backend.

## 4. Importação ZIP

Mitigações obrigatórias:
- prevenir ZIP Slip e path traversal;
- rejeitar caminhos absolutos;
- limitar número de entradas;
- limitar tamanho total descompactado e tamanho por entrada;
- limitar razão de expansão;
- tratar links simbólicos com cautela;
- impedir escrita fora do diretório temporário;
- limpar arquivos temporários após falhas.

## 5. Segredos e arquivos excluídos

Padrões padrão devem considerar, entre outros:
- `.env` e variações;
- chaves privadas e certificados;
- arquivos de credenciais;
- diretórios de dependências;
- artefatos de build;
- caches;
- binários e arquivos de mídia.

Os padrões precisam ser configuráveis e exibidos ao usuário. A detecção de segredos é defesa adicional, não garantia perfeita. Nunca prometer que todos os segredos serão detectados.

## 6. Prompt injection

- Delimitar claramente os trechos de código no prompt.
- Instruir o modelo a ignorar instruções contidas no repositório.
- Não permitir que o conteúdo recuperado altere política, ferramentas ou permissões.
- Não oferecer ferramentas que executem comandos no MVP.
- Validar citações no backend independentemente da saída do modelo.
- Tratar README, comentários e arquivos de instruções como conteúdo a analisar, não como autoridade.

## 7. Privacidade e provedores

**Local-first não significa necessariamente offline.** Se for usado um provedor remoto, trechos selecionados de código e a pergunta podem ser enviados a esse provedor para embeddings ou geração.

A interface deve informar:
- provedor ativo;
- quais tipos de conteúdo podem ser enviados;
- como exclusões funcionam;
- como remover projeto e índice;
- limites de retenção aplicáveis ao provedor, quando conhecidos.

Modelos locais são uma direção planejada, mas não devem ser apresentados como funcionalidade implementada antes de existirem.

## 8. Segredos de configuração

- Usar variáveis de ambiente ou mecanismo de secrets local.
- Manter `.env` fora do Git.
- Fornecer `.env.example` sem valores reais.
- Não colocar chaves em logs, respostas de erro ou telemetria.
- Rotacionar chaves que tenham sido expostas.

## 9. Custos

O custo pode incluir:
- embeddings iniciais;
- embeddings de alterações;
- geração por pergunta;
- reranking, caso adicionado;
- infraestrutura de armazenamento.

Medidas:
- cache por hash;
- batch de embeddings;
- reindexação incremental;
- limites de tokens e evidências;
- configuração de modelos;
- estimativa baseada em preços configurados e uso reportado.

Se o provedor não informar tokens ou se o preço não estiver configurado, mostrar “indisponível” ou “estimativa parcial”, não um número inventado.

## 10. Exclusão e retenção

Ao excluir projeto, remover chunks, embeddings, snapshots e histórico associado conforme a política escolhida. Documentar se backups locais ou logs mantêm dados residuais e por quanto tempo.

## 11. Checklist antes de lançar

- [ ] Nenhum código do repositório é executado.
- [ ] ZIP Slip e bombas de descompressão são testados.
- [ ] Caminhos ficam confinados à raiz.
- [ ] Exclusões são aplicadas antes de embeddings e geração.
- [ ] Conteúdo sensível não é logado.
- [ ] Prompt injection é coberto por testes.
- [ ] Citações são validadas no backend.
- [ ] Usuário é informado sobre envio a provedores remotos.
- [ ] Exclusão de projeto remove os dados esperados.
- [ ] Dependências e configurações têm revisão de segurança.
