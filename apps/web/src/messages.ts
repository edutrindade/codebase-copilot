export const messages = {
  app: {
    name: 'Codebase Copilot',
    skipToContent: 'Pular para o conteúdo',
  },
  pages: {
    workspaces: 'Workspaces',
    workspace: 'Projetos do workspace',
    newProject: 'Novo projeto',
    project: 'Projeto',
    ask: 'Perguntar',
    conversation: 'Conversa',
    history: 'Histórico',
  },
  pending: 'Esta tela ainda está em construção.',
  loading: 'Carregando…',
  notFound: {
    title: 'Página não encontrada',
    detail: 'O endereço não corresponde a nenhuma tela.',
    back: 'Voltar para os workspaces',
  },
  errors: {
    networkTitle: 'Servidor indisponível',
    networkDetail: 'Não foi possível falar com o servidor. Confira se o backend está em execução.',
    unexpectedTitle: 'Erro inesperado',
    unexpectedDetail: 'O servidor respondeu de um jeito inesperado. Tente de novo.',
    renderTitle: 'Algo deu errado nesta tela',
    renderDetail: 'Recarregue a página. Se o erro continuar, volte para os workspaces.',
    requestId: 'Identificador do pedido',
    retry: 'Tentar de novo',
  },
  recovery: {
    retry:
      'Tente de novo em instantes. Se o erro continuar, consulte o log do servidor com o identificador do pedido.',
    reload:
      'Recarregue a página. Se o erro continuar, a interface e o servidor podem estar em versões diferentes.',
    fixInput: 'Corrija os campos indicados e envie de novo.',
    goBack: 'O item pode ter sido removido. Volte para a lista e escolha outro.',
    removeProjects: 'Remova os projetos deste workspace antes de excluí-lo.',
    waitForJob: 'Aguarde o fim da importação ou da indexação em andamento e tente de novo.',
    chooseAnother: 'Escolha outra pasta ou outro arquivo.',
    checkSource:
      'Confira se a pasta de origem ainda existe, não é um link simbólico e pode ser lida; depois tente de novo.',
    uploadZip: 'Envie o ZIP do projeto antes de continuar.',
    reindex: 'Indexe o projeto de novo para atualizar o snapshot.',
    reviewServerConfig:
      'Revise as raízes permitidas na configuração do servidor e reinicie o backend.',
    configureProvider:
      'Configure o provedor e a credencial no servidor e reinicie o backend. A busca lexical continua disponível.',
    providerLimit:
      'O provedor recusou ou limitou o pedido. Aguarde, confira o saldo da conta e tente de novo.',
    useEvidenceCopy:
      'Este snapshot foi substituído; o trecho guardado na evidência continua disponível.',
    openLocally: 'Abra a interface pelo endereço local (localhost ou 127.0.0.1).',
  },
  pagination: {
    label: 'Paginação',
    previous: 'Anterior',
    next: 'Próxima',
    status: (page: number, totalPages: number) => `Página ${page} de ${totalPages}`,
  },
  confirm: {
    deleteTrigger: 'Excluir',
    cancel: 'Cancelar',
    working: 'Excluindo…',
  },
  workspaces: {
    title: 'Workspaces',
    firstUseTitle: 'Crie seu primeiro workspace',
    firstUseDetail:
      'Um workspace agrupa os projetos que você vai importar. Os dados ficam armazenados localmente, no banco desta máquina.',
    newTitle: 'Novo workspace',
    nameLabel: 'Nome (opcional)',
    namePlaceholder: 'Meus projetos',
    create: 'Criar workspace',
    creating: 'Criando…',
    projectCount: (count: number) =>
      count === 0 ? 'Nenhum projeto' : count === 1 ? '1 projeto' : `${count} projetos`,
    createdAt: (date: string) => `Criado em ${date}`,
    delete: (name: string) => `Excluir o workspace ${name}`,
    deleteTitle: (name: string) => `Excluir o workspace “${name}”?`,
    deleteDetail: 'Esta ação não pode ser desfeita.',
    deleteNotEmpty: (count: number) =>
      `Ele tem ${count === 1 ? '1 projeto' : `${count} projetos`}, que precisam ser removidos antes.`,
    deleteConfirm: 'Excluir workspace',
  },
  projects: {
    allWorkspaces: 'Todos os workspaces',
    newProject: 'Novo projeto',
    emptyTitle: 'Nenhum projeto ainda',
    emptyDetail: 'Importe uma pasta local ou envie um ZIP. O código é só lido, nunca executado.',
    sourceLocal: (rootKey: string, relativePath: string) =>
      `Pasta local: ${rootKey}/${relativePath}`,
    sourceZip: 'Arquivo ZIP',
    status: {
      INDEXING: 'Indexando',
      AWAITING_UPLOAD: 'Aguardando o ZIP',
      NOT_INDEXED: 'Não indexado',
      OUTDATED: 'Desatualizado',
      PARTIAL: 'Pronto, com arquivos não indexados',
      READY: 'Pronto',
    },
    progress: (completed: number, total: number) => `${completed} de ${total}`,
    delete: (name: string) => `Excluir o projeto ${name}`,
    deleteTitle: (name: string) => `Excluir o projeto “${name}”?`,
    deleteDetail:
      'Remove imports, índices, snapshots e histórico de conversas do projeto. Esta ação não pode ser desfeita.',
    deleteBusy: 'Há uma importação ou indexação em andamento; aguarde o fim para excluir.',
    deleteConfirm: 'Excluir projeto',
  },
  newProject: {
    backToWorkspace: 'Voltar para o workspace',
    steps: {
      label: 'Etapas da importação',
      source: 'Origem',
      upload: 'Envio do ZIP',
      review: 'Pré-análise e indexação',
      current: (step: number, total: number) => `Etapa ${step} de ${total}`,
    },
    intro: [
      'Os arquivos da pasta ou do ZIP são só lidos, nunca executados.',
      'Antes de indexar, você vê quais arquivos serão incluídos e pode ajustar as exclusões. Dependências, saídas de build, binários e arquivos sensíveis por nome (como .env) já vêm excluídos.',
      'São indexados arquivos de texto (código, configuração, documentação); binários ficam de fora. Há limites de tamanho por arquivo e por projeto, mostrados e ajustáveis na pré-análise.',
      'Se um provedor remoto estiver configurado, trechos selecionados podem ser enviados a ele; você é avisado antes.',
    ],
    sourceLegend: 'De onde vem o código?',
    sourceLocal: 'Pasta local',
    sourceLocalHint: 'Uma pasta dentro das raízes permitidas na configuração do servidor.',
    sourceZip: 'Arquivo ZIP',
    sourceZipHint: 'Você envia o arquivo depois de criar o projeto.',
    noRoots:
      'Nenhuma raiz de pastas locais está configurada no servidor. Use um arquivo ZIP ou configure as raízes permitidas e reinicie o backend.',
    nameLabel: 'Nome do projeto',
    create: 'Criar projeto',
    creating: 'Criando…',
    browser: {
      rootLabel: 'Raiz',
      location: 'Pasta atual',
      selected: (path: string) => `Pasta selecionada: ${path}`,
      empty: 'Esta pasta não tem subpastas. Ela pode ser usada como projeto.',
      truncated: 'A pasta tem mais subpastas do que é possível listar; só as primeiras aparecem.',
      unreadable: 'sem permissão de leitura',
      open: (name: string) => `Abrir ${name}`,
      backToRoot: 'Voltar para a raiz',
      subfolders: 'Subpastas',
    },
    upload: {
      title: 'Enviar o ZIP',
      detail:
        'O arquivo é extraído no servidor com limites de tamanho, quantidade de entradas e caminhos seguros. Nada dentro dele é executado.',
      fileLabel: 'Arquivo ZIP',
      submit: 'Enviar',
      uploading: 'Enviando…',
      pending: 'Na fila para extração…',
      extracting: 'Extraindo o arquivo…',
      rejectedTitle: 'O ZIP foi recusado',
      failedTitle: 'A extração falhou',
      tryAnother: 'Escolha outro arquivo e envie de novo.',
      chooseFile: 'Escolha um arquivo antes de enviar.',
    },
  },
  preAnalysis: {
    title: 'Pré-análise',
    detail: 'O que será lido, calculado agora a partir da origem. Nada foi indexado ainda.',
    scannedAt: (date: string, version: number) =>
      `Calculada em ${date} com as configurações versão ${version}.`,
    refresh: 'Recalcular',
    truncated: 'A varredura parou no limite de entradas; os números abaixo estão incompletos.',
    totalsTitle: 'Resumo',
    totals: {
      files: 'Arquivos encontrados',
      includedFiles: 'Serão indexados',
      includedBytes: 'Tamanho estimado',
      excludedFiles: 'Excluídos',
      unsupportedFiles: 'Não suportados (binários)',
      unreadableFiles: 'Ilegíveis',
      excludedDirectories: 'Pastas excluídas',
    },
    languagesTitle: 'Linguagens',
    languageColumn: 'Linguagem',
    filesColumn: 'Arquivos',
    bytesColumn: 'Tamanho',
    directoriesTitle: 'Pastas do primeiro nível',
    directoryColumn: 'Pasta',
    includedColumn: 'Incluídos',
    excludedColumn: 'Excluídos',
    unsupportedColumn: 'Não suportados',
    unreadableColumn: 'Ilegíveis',
    rootDirectory: '(arquivos da raiz)',
    none: 'Nenhum.',
    exclusionsTitle: 'Exclusões e motivos',
    exclusionCount: (files: number, directories: number) => {
      const parts: string[] = [];
      if (directories > 0) {
        parts.push(directories === 1 ? '1 pasta' : `${directories} pastas`);
      }
      if (files > 0 || directories === 0) {
        parts.push(files === 1 ? '1 arquivo' : `${files} arquivos`);
      }
      return parts.join(' e ');
    },
    examples: 'Exemplos',
    reasons: {
      DEFAULT_RULE: (ruleId: string) => `Regra padrão “${ruleId}”`,
      CUSTOM_PATTERN: (pattern: string) => `Padrão do projeto “${pattern}”`,
      SYMLINK: 'Links simbólicos (nunca são seguidos)',
      SPECIAL_FILE: 'Arquivos especiais (nunca são abertos)',
      DEPTH_LIMIT: 'Abaixo da profundidade máxima',
      TOO_LARGE: 'Acima do tamanho máximo por arquivo',
      BINARY: 'Binários',
      UNREADABLE: 'Sem permissão de leitura',
    },
    warningsTitle: 'Avisos',
    relatedProject: 'Abrir o outro projeto',
    blocking:
      'Resolva os avisos acima antes de indexar: ajuste as exclusões ou os limites e recalcule.',
  },
  settings: {
    title: 'Exclusões e limites',
    detail:
      'Valem para a próxima pré-análise e para a próxima indexação. Regras de arquivos sensíveis, controle de versão e mídia não podem ser desativadas.',
    rulesLegend: 'Regras padrão',
    ruleEnabled: (id: string) => `Aplicar a regra ${id}`,
    ruleRequired: 'obrigatória',
    categories: {
      SENSITIVE: 'Sensíveis',
      VCS: 'Controle de versão',
      DEPENDENCIES: 'Dependências',
      BUILD_OUTPUT: 'Saída de build',
      IDE_CACHE: 'IDE e cache',
      GENERATED: 'Gerados',
      MEDIA_BINARY: 'Mídia e binários',
    },
    patternsLabel: 'Padrões de exclusão do projeto (um por linha)',
    patternsHint:
      'Relativos à raiz, com “/” como separador. “*” não cruza pastas, “**” cruza; sem “/” casa o nome em qualquer pasta; “/” no fim restringe a pastas. Ex.: docs/generated/ ou *.min.js',
    patternError: (pattern: string, message: string) => `${pattern}: ${message}`,
    maxFileLabel: 'Tamanho máximo por arquivo (KB)',
    maxProjectLabel: 'Tamanho máximo do projeto (MB)',
    limitHint: (effective: string) => `Vazio usa o padrão. Valor em vigor: ${effective}.`,
    save: 'Salvar e recalcular',
    saving: 'Salvando…',
    discard: 'Descartar alterações',
    saved: 'Configurações salvas. A pré-análise foi recalculada.',
  },
  privacy: {
    title: 'Para onde vão os trechos',
    localOnly:
      'Com o provedor de embeddings configurado, a indexação não envia trechos para fora desta máquina.',
    noEmbeddings:
      'Nenhum provedor de embeddings está configurado: a indexação é só lexical e a busca vetorial fica indisponível.',
    remote: (provider: string, model: string | null) =>
      `Os trechos dos arquivos incluídos serão enviados a ${model ? `${provider} (${model})` : provider}, fora desta máquina, para gerar embeddings.`,
    review: 'Rever as exclusões',
    acknowledge: 'Entendi que trechos serão enviados ao provedor',
  },
  indexing: {
    start: 'Indexar',
    starting: 'Iniciando…',
    dirty: 'Salve ou descarte as alterações nas exclusões antes de indexar.',
    noIncluded: 'Nenhum arquivo será indexado com as configurações atuais.',
  },
} as const;
