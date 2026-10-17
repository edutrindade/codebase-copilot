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
  },
} as const;
