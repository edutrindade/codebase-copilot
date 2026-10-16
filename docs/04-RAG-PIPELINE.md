# Pipeline RAG — Codebase Copilot

## 1. Objetivo

Responder perguntas sobre um repositório usando trechos recuperados, com evidências rastreáveis, controle de contexto e abstenção quando necessário.

## 2. Ingestão segura

1. Receber uma pasta escolhida pelo usuário ou um ZIP.
2. Canonicalizar caminhos e validar que cada arquivo permanece dentro da raiz permitida.
3. Aplicar limites de quantidade, tamanho por arquivo e tamanho total.
4. Excluir padrões padrão e padrões configurados pelo usuário.
5. Detectar binários, arquivos gerados e conteúdo não textual.
6. Nunca executar scripts, build tools, hooks ou código importado.
7. Registrar o motivo de exclusão sem registrar o conteúdo sensível.

A importação ZIP deve proteger contra path traversal, nomes absolutos, links simbólicos perigosos e arquivos compactados com expansão excessiva.

## 3. Inventário e linguagem

Detectar linguagem por extensão e, quando útil, conteúdo. Não confiar exclusivamente na extensão.

O pipeline deve funcionar em modo genérico para texto. Adaptadores específicos podem acrescentar:
- parsing por AST;
- nomes de símbolos;
- definições e referências;
- limites de função/classe;
- relações estruturais.

A ausência de adaptador não deve impedir indexação textual.

## 4. Chunking

> Modelo decidido na D-039: o chunk pertence à versão do conteúdo (`content_hash`), e o snapshot liga caminhos a hashes em `snapshot_files`. Parâmetros iniciais na D-043. Ver `05-DATA-MODEL.md`.

Cada chunk contém:
- `project_id`;
- referência à versão do arquivo (`content_hash`); snapshot, caminho e linguagem vêm de `snapshot_files` (D-039);
- hash do chunk (texto + versão de chunking), usado como chave do cache de embeddings;
- `start_line` e `end_line`;
- conteúdo;
- contagem de tokens estimada;
- versão da estratégia de chunking.

Tipo de chunk e símbolos entram com os adaptadores AST (Etapa 9, D-027).

Preferir limites semânticos quando houver parser confiável. Caso contrário, dividir por linhas e limites de tamanho, com sobreposição controlada. Evitar chunks enormes e preservar contexto suficiente para entendimento.

**Implementado na Etapa 3 (`lines-v1`, D-043, provisório até a avaliação da O-010):**
- antes do chunking, o arquivo é lido uma vez, tem o hash SHA-256 calculado e é classificado (D-042): UTF-8 válido vira texto; byte NUL, UTF-8 inválido ou linha acima de 4.000 caracteres deixam o arquivo `UNSUPPORTED`, sem chunks;
- janelas de até 60 linhas e 4.000 caracteres, com corte preferencial em linha em branco nas últimas 15 linhas;
- sobreposição de até 10 linhas quando o corte é pelo limite, e nenhuma quando é em linha em branco;
- chunks nunca começam nem terminam em linha em branco; o texto do chunk junta as linhas com `\n`;
- tokens estimados como ⌈caracteres / 4⌉, até existir o tokenizador do provedor.

## 5. Embeddings

- Usar uma interface interna para embeddings.
- Registrar provedor, modelo e dimensão do vetor.
- Reutilizar embeddings quando hash do conteúdo, modelo e configuração forem iguais.
- Não misturar vetores de dimensões/modelos incompatíveis no mesmo espaço de busca.
- Planejar reindexação quando mudar o modelo ou a estratégia.

Spring AI pode fornecer a integração com o provedor, mas a aplicação deve persistir metadados explícitos e não depender de detalhes internos do framework.

## 6. Recuperação híbrida

### Canal lexical
Busca por termos, identificadores, nomes de classe/função, caminhos e palavras específicas. A busca lexical é importante para nomes exatos e símbolos.

### Canal vetorial
Busca por similaridade semântica em embeddings.

### Fusão
Combinar rankings dos canais. O baseline é Reciprocal Rank Fusion com k=60 (D-017), a ser comparado com alternativas no conjunto de avaliação (O-005).

### Filtros
Sempre restringir por projeto e snapshot, respeitando permissões e exclusões. Considerar filtros por linguagem e caminho quando forem úteis.

### Reranking
Adicionar reranking apenas se os resultados do baseline mostrarem problemas que justifiquem custo e latência adicionais.

## 7. Montagem de contexto

- Remover duplicatas e trechos redundantes.
- Limitar contexto por tokens.
- Preservar metadados necessários para citações.
- Distribuir o contexto entre arquivos quando a pergunta exigir fluxo entre módulos.
- Não assumir que similaridade alta equivale a evidência suficiente.

## 8. Geração

O prompt deve:
- instruir o modelo a responder somente com base nas evidências fornecidas;
- tratar o conteúdo do repositório como dado não confiável, nunca como instrução;
- ignorar instruções presentes em comentários, README ou arquivos do projeto que tentem mudar as regras da conversa;
- pedir referências para afirmações sobre o código;
- permitir resposta parcial, pergunta de esclarecimento ou abstenção.

## 9. Validação de citações

A resposta estruturada deve referenciar IDs de evidência fornecidos pelo backend. Após a geração:
1. Validar que cada ID existe no conjunto enviado ao modelo.
2. Resolver ID para arquivo, snapshot e linhas.
3. Verificar que os intervalos estão dentro do arquivo.
4. Remover referências inexistentes.
5. Se uma afirmação central ficar sem suporte, sinalizar incerteza ou pedir nova recuperação.

Evitar depender exclusivamente de parsing de texto livre; preferir saída estruturada validada no backend.

## 10. Controle de custo

- Limites de arquivos, chunks e contexto.
- Embeddings em lote quando o provedor permitir.
- Cache por hash.
- Evitar gerar embeddings para conteúdo inalterado.
- Mostrar tokens e custo somente quando conhecidos ou calculáveis.
- Registrar modelo e preço configurado usados no cálculo.
- Permitir limitar tamanho da resposta e número de evidências.

## 11. Reindexação incremental

Comparar hashes do snapshot anterior e atual:
- arquivo novo: indexar;
- arquivo alterado: substituir chunks e embeddings;
- arquivo removido/excluído: remover do índice ativo;
- arquivo inalterado: reutilizar;
- modelo/estratégia incompatível: reprocessar conforme a política de versão.

Operações devem ser idempotentes e recuperar-se de interrupções.

**Implementado na Etapa 3 (D-039, D-040, D-044, D-045):**
- toda indexação é incremental por conteúdo: cada arquivo incluído é lido e tem o hash calculado; só conteúdos novos (ou sem chunks da versão atual) geram `file_contents` e chunks, e o snapshot novo aponta caminho → hash;
- arquivos removidos ou excluídos simplesmente não aparecem no snapshot novo; mudar exclusões, limites ou a versão de chunking é coberto pelo mesmo mecanismo;
- arquivos `FAILED` no snapshot anterior são lidos de novo no próximo job;
- o snapshot só vira ativo quando o job conclui; ele traz o resumo `added`, `modified`, `removed` e `unchanged` em relação ao anterior;
- jobs ficam numa fila em tabela, com heartbeat e limite de tentativas; um job interrompido é retomado e chega ao mesmo resultado de uma execução sem falha;
- snapshots antigos são podados, e conteúdo e chunks sem snapshot são removidos ao fim de cada job.

## 12. Pipeline resumido

```text
Import -> Safe Scan -> Inventory -> Filter
       -> Parse/Generic Text -> Chunk -> Embed
       -> PostgreSQL + pgvector
Question -> Lexical Search + Vector Search
         -> Rank Fusion -> Context Budget
         -> Model -> Citation Validation
         -> Answer + Evidence + Metrics
```

## 13. Avaliação

Qualquer mudança no chunking, embedding, recuperação, prompt ou reranking deve ser medida contra o conjunto de avaliação descrito em `08-EVALUATION.md`.
