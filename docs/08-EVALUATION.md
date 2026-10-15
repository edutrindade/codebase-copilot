# Avaliação de qualidade — Codebase Copilot

## 1. Objetivo

Medir se o sistema recupera evidências relevantes e responde corretamente, com citações válidas, custo e latência aceitáveis. A avaliação deve existir antes de otimizar prompts ou trocar modelos.

## 2. Conjunto de avaliação

Criar um corpus de repositórios de teste que possam ser usados legalmente, incluindo projetos pequenos e médios e diferentes linguagens. Para cada pergunta, registrar:
- pergunta;
- snapshot do corpus;
- arquivos/intervalos de linhas esperados;
- resposta esperada ou pontos factuais necessários;
- se a resposta correta deve se abster;
- dificuldade e categoria;
- observações sobre evidência indireta.

Incluir:
- busca por identificador exato;
- perguntas conceituais;
- fluxo entre arquivos;
- arquitetura;
- dependências e configuração;
- perguntas sem resposta no código;
- perguntas ambíguas;
- prompt injection embutido em arquivos;
- arquivos excluídos e segredos sintéticos.

## 3. Métricas de recuperação

- **Recall@k:** proporção das evidências relevantes encontradas entre os primeiros k resultados.
- **MRR:** posição do primeiro resultado relevante.
- **Precision@k:** proporção de resultados relevantes entre os k primeiros.
- **Cobertura de fontes:** capacidade de recuperar arquivos distintos quando a pergunta exige fluxo entre módulos.

Definir relevância em nível de chunk e, quando útil, de arquivo.

## 4. Métricas de resposta

- **Validade de citação:** referências apontam para arquivos e linhas existentes.
- **Suporte factual:** afirmações importantes são sustentadas pelas evidências citadas.
- **Correção:** a resposta corresponde ao comportamento real do código.
- **Abstenção:** o sistema evita inventar quando o corpus não contém resposta suficiente.
- **Utilidade:** desenvolvedores conseguem verificar e usar a resposta.

A avaliação automática por outro modelo pode auxiliar, mas não deve ser a única fonte de verdade. Auditar uma amostra manualmente.

## 5. Métricas operacionais

- duração de ingestão e indexação;
- latência de busca lexical e vetorial;
- latência total da resposta;
- tokens de entrada e saída;
- custo estimado por pergunta;
- taxa de falhas por etapa;
- taxa de reindexação incremental correta;
- uso de memória e armazenamento.

## 6. Baselines

Estabelecer comparações:
1. Busca lexical somente.
2. Busca vetorial somente.
3. Híbrida com fusão simples.
4. Híbrida com reranking, se implementado.
5. Chunking genérico versus chunking com parser, nas linguagens suportadas.

Não assumir que a opção mais sofisticada vence; medir qualidade, latência e custo no mesmo corpus.

## 7. Testes de robustez e segurança

- pergunta que tenta fazer o modelo revelar segredos;
- arquivo que contém instruções para ignorar o prompt;
- arquivo excluído que contém segredo sintético;
- ZIP com path traversal;
- arquivo gigantesco;
- encoding inválido;
- snapshot alterado após uma resposta;
- provedor que retorna erro ou timeout;
- resposta com ID de evidência inventado.

## 8. Processo de avaliação

1. Versionar corpus, perguntas e anotações.
2. Registrar modelo, embedding, chunking e configuração de recuperação.
3. Executar baseline.
4. Guardar resultados e configuração.
5. Alterar uma variável por vez quando possível.
6. Comparar qualidade, latência e custo.
7. Não promover mudança que degrade segurança ou validade de citação sem decisão explícita.

## 9. Metas

As metas numéricas devem ser definidas depois da primeira medição, considerando o tamanho e a dificuldade do corpus. Evitar criar percentuais arbitrários sem baseline.

## 10. Critério mínimo de release

Antes de considerar o MVP utilizável:
- não aceitar citações inválidas silenciosamente;
- testar a abstenção em perguntas sem resposta;
- confirmar que exclusões são respeitadas;
- demonstrar reindexação incremental correta;
- registrar limitações conhecidas e resultados do corpus de avaliação.
