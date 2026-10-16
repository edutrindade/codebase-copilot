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

A seleção de pasta segue a D-018: o backend expõe apenas raízes permitidas configuradas, a UI navega dentro delas e o cliente envia somente caminhos relativos, que o backend canonicaliza e valida. Links simbólicos não são seguidos. O browser nunca envia caminhos locais arbitrários ao backend.

Política implementada na Etapa 2 (D-037):
- cada segmento do caminho é verificado sem seguir links; um caminho que passa por link é recusado (navegação: `404 DIRECTORY_NOT_FOUND`; inventário: `409 SOURCE_IS_SYMLINK`);
- na varredura, links aparecem como `SYMLINK` e nunca são seguidos, nem para dentro da raiz;
- FIFOs, sockets e dispositivos aparecem como `SPECIAL_FILE` e nunca são abertos;
- arquivos são lidos apenas como bytes (amostra de 8 KB aberta com `NOFOLLOW_LINKS`), com limites de entradas, profundidade e tamanho;
- o `DATA_DIR` não pode estar dentro de uma raiz nem contê-la, para que arquivos extraídos nunca apareçam na navegação (D-028).

**Limitações conhecidas:** links físicos e pontos de montagem dentro da raiz não são detectados, e uma troca concorrente de diretório por link durante a varredura (TOCTOU) não é totalmente evitável sem `openat`. O modelo de ameaça é conteúdo malicioso, não um atacante concorrente no disco local.

Leitura do conteúdo na indexação (Etapa 3, D-042):
- só os arquivos incluídos pela varredura são lidos, cada um uma única vez, sem seguir links e só se ainda for um arquivo regular (um FIFO ou link colocado no lugar depois da varredura não é aberto);
- a leitura é limitada ao tamanho máximo do arquivo; atributos diferentes antes e depois da leitura marcam o arquivo como `FAILED` (`CHANGED_DURING_READ`), e ele não é indexado;
- o conteúdo é tratado apenas como texto UTF-8; nada é interpretado nem executado;
- logs da indexação trazem apenas ids, contagens, códigos e durações, nunca caminhos ou conteúdo.

## 4. Importação ZIP

Mitigações obrigatórias:
- prevenir ZIP Slip e path traversal;
- rejeitar caminhos absolutos;
- limitar número de entradas;
- limitar tamanho total descompactado e tamanho por entrada;
- limitar razão de expansão;
- tratar links simbólicos com cautela;
- impedir escrita fora da pasta do job (`DATA_DIR/imports/<importJobId>`, D-028);
- apagar a pasta do job quando ele falhar ou for substituído; a do import atual é retida como origem do projeto (D-033).

Implementação da Etapa 2:
- os limites são contados pelos bytes realmente gravados, nunca pelos tamanhos declarados no arquivo, o que também cobre entradas sobrepostas;
- o nome de cada entrada é validado (sem `/` inicial, letra de unidade, `..`, `\` ou caracteres de controle, com profundidade e comprimento máximos), e o destino resolvido precisa ficar dentro da pasta do job;
- arquivos são criados com `CREATE_NEW`: entradas repetidas, ou que colidem por maiúsculas em sistemas sem distinção de caixa, recusam o ZIP;
- o extrator nunca cria links: uma entrada de link vira arquivo comum;
- ZIPs inválidos, truncados ou criptografados são recusados;
- mensagens de erro e logs nunca contêm nomes de entradas.

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

No MVP, segredos são excluídos apenas por padrões de nome, em regras que o usuário não pode desativar (`SENSITIVE`, D-036). A detecção por conteúdo fica para a Etapa 8.

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

Desde a Etapa 3, o conteúdo dos arquivos indexados fica no PostgreSQL (`file_contents` e `chunks`), deduplicado por projeto. A remoção do projeto apaga tudo em cascata; snapshots antigos são podados e o conteúdo sem snapshot é removido ao fim de cada indexação (D-039, D-044). Backups do banco mantêm cópias desse conteúdo; isso fica a cargo de quem opera o ambiente.

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
