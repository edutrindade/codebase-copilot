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
} as const;
