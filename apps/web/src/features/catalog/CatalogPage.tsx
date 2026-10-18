import { FolderOpen, Pencil, Play, Plus, RefreshCw, Settings, Trash } from 'lucide-react';
import { useRef, useState, type ReactNode } from 'react';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Loading } from '../../components/Loading';
import { ProblemAlert } from '../../components/ProblemAlert';
import { ApiError } from '../../api/errors';
import { Breadcrumbs } from '../../components/ui/Breadcrumbs';
import { Button, ButtonLink, type ButtonVariant } from '../../components/ui/Button';
import { Callout, type CalloutTone } from '../../components/ui/Callout';
import { Card } from '../../components/ui/Card';
import { CopyButton } from '../../components/ui/CopyButton';
import { Dialog } from '../../components/ui/Dialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { HoverCard } from '../../components/ui/HoverCard';
import { SelectField, TextAreaField, TextField } from '../../components/ui/Field';
import { IconButton } from '../../components/ui/IconButton';
import { Kbd } from '../../components/ui/Kbd';
import { Menu, MenuItem, MenuLabel, MenuSeparator } from '../../components/ui/Menu';
import { PageHeader } from '../../components/ui/PageHeader';
import { Popover } from '../../components/ui/Popover';
import { Progress } from '../../components/ui/Progress';
import { RelativeTime } from '../../components/ui/RelativeTime';
import { SegmentedControl } from '../../components/ui/SegmentedControl';
import { Skeleton } from '../../components/ui/Skeleton';
import { Splitter } from '../../components/ui/Splitter';
import { Stat, StatGrid } from '../../components/ui/Stat';
import { StatusBadge, type Tone } from '../../components/ui/StatusBadge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../components/ui/Tabs';
import './CatalogPage.css';

// Development only (routes.tsx keeps it out of the build), so the sample text stays here, not in messages.ts.

const SWATCHES = [
  '--color-bg',
  '--color-surface',
  '--color-sunken',
  '--color-text',
  '--color-text-muted',
  '--color-border',
  '--color-border-strong',
  '--color-accent',
  '--color-accent-soft',
  '--color-success',
  '--color-warning',
  '--color-danger',
  '--color-info',
  '--color-highlight',
];
const VARIANTS: ButtonVariant[] = ['primary', 'secondary', 'ghost', 'danger', 'danger-ghost'];
const TONES: Tone[] = ['neutral', 'accent', 'progress', 'success', 'warning', 'danger', 'info'];
const CALLOUT_TONES: CalloutTone[] = ['neutral', 'info', 'success', 'warning', 'danger'];
const FIVE_MINUTES_AGO = new Date(Date.now() - 5 * 60_000).toISOString();
const YESTERDAY = new Date(Date.now() - 26 * 3_600_000).toISOString();

const sampleProblem = new ApiError(404, {
  type: 'about:blank',
  title: 'Projeto não encontrado',
  status: 404,
  detail: 'O projeto informado não existe ou foi removido.',
  instance: '/api/v1/projects/1',
  code: 'PROJECT_NOT_FOUND',
  requestId: '0192f5c4-7e3b-7a2f-8d4e-3a5b7c9d1e2f',
});

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card as="section" className="stack catalog__section">
      <h2>{title}</h2>
      {children}
    </Card>
  );
}

function SplitSample() {
  const [split, setSplit] = useState(50);
  const container = useRef<HTMLDivElement>(null);
  return (
    <div
      ref={container}
      className="catalog__split"
      style={{ gridTemplateColumns: `${split}% auto 1fr` }}
    >
      <div id="catalog-left" className="catalog__pane">
        Conversa
      </div>
      <Splitter
        value={split}
        min={30}
        max={70}
        onChange={setSplit}
        containerRef={container}
        controls="catalog-left"
        label="Divisor de exemplo"
        valueText={(percent) => `Esquerda com ${percent}%`}
        decreaseLabel="Estreitar a esquerda"
        increaseLabel="Alargar a esquerda"
      />
      <div className="catalog__pane">Evidências e código</div>
    </div>
  );
}

export function CatalogPage() {
  const [version, setVersion] = useState<'answered' | 'current'>('answered');
  const [dialogOpen, setDialogOpen] = useState(false);
  return (
    <div className="stack catalog">
      <PageHeader
        title="Catálogo de componentes"
        description="Só no modo de desenvolvimento. Troque o tema no topo para conferir os dois."
      />

      <Section title="Cores">
        <ul className="catalog__swatches">
          {SWATCHES.map((token) => (
            <li key={token}>
              <span className="catalog__swatch" style={{ background: `var(${token})` }} />
              <code>{token}</code>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="Tipografia">
        <h1>Título de página (h1)</h1>
        <h2>Título de seção (h2)</h2>
        <h3>Subtítulo (h3)</h3>
        <p>Corpo de texto em Inter, 14 px. Respostas citam o código com evidências verificáveis.</p>
        <p className="muted">Texto secundário, com contraste AA nos dois temas.</p>
        <p>
          Código: <code>src/main/java/shop/RefundService.java</code> · 0O 1lI {'{}'} [] =&gt;
        </p>
        <p className="tabular">Números tabulares: 1.234 · 98,7 ms · US$ 0,0042</p>
      </Section>

      <Section title="Botões">
        {(['md', 'sm'] as const).map((size) => (
          <div key={size} className="actions">
            {VARIANTS.map((variant) => (
              <Button key={variant} variant={variant} size={size}>
                {variant}
              </Button>
            ))}
          </div>
        ))}
        <div className="actions">
          <Button variant="primary" icon={Plus}>
            Novo projeto
          </Button>
          <Button icon={RefreshCw}>Atualizar</Button>
          <Button variant="primary" pending>
            Indexando…
          </Button>
          <Button disabled>Desabilitado</Button>
          <ButtonLink to="/_ui" icon={FolderOpen}>
            Link como botão
          </ButtonLink>
        </div>
        <div className="actions">
          <IconButton label="Editar" icon={Pencil} />
          <IconButton label="Configurações" icon={Settings} variant="secondary" />
          <IconButton label="Excluir" icon={Trash} variant="danger-ghost" size="sm" />
          <CopyButton text="0192f5c4-7e3b" label="Copiar o identificador" />
          <CopyButton text="src/App.java" label="Copiar caminho" showLabel />
        </div>
      </Section>

      <Section title="Selos de estado">
        <div className="actions">
          {TONES.map((tone) => (
            <StatusBadge key={tone} tone={tone}>
              {tone}
            </StatusBadge>
          ))}
        </div>
      </Section>

      <Section title="Avisos">
        {CALLOUT_TONES.map((tone) => (
          <Callout
            key={tone}
            tone={tone}
            title={`Aviso ${tone}`}
            actions={
              <Button size="sm" icon={Play}>
                Ação
              </Button>
            }
          >
            <p>O snapshot ativo pode não refletir as configurações atuais do projeto.</p>
          </Callout>
        ))}
        <ProblemAlert error={sampleProblem} />
      </Section>

      <Section title="Navegação">
        <Breadcrumbs
          items={[
            { label: 'Workspaces', to: '/' },
            { label: 'Loja', to: '/_ui' },
            { label: 'sample-shop', to: '/_ui' },
            { label: 'Perguntar' },
          ]}
        />
        <Tabs defaultValue="overview">
          <TabsList aria-label="Seções do exemplo">
            <TabsTrigger value="overview">Visão geral</TabsTrigger>
            <TabsTrigger value="ask">Perguntar</TabsTrigger>
            <TabsTrigger value="files">Arquivos</TabsTrigger>
          </TabsList>
          <TabsContent value="overview">Conteúdo da visão geral.</TabsContent>
          <TabsContent value="ask">Conteúdo de perguntar.</TabsContent>
          <TabsContent value="files">Conteúdo de arquivos.</TabsContent>
        </Tabs>
        <p>
          Atalho: <Kbd>⌘</Kbd> <Kbd>K</Kbd> · atualizado <RelativeTime iso={FIVE_MINUTES_AGO} /> ·
          criado <RelativeTime iso={YESTERDAY} />
        </p>
      </Section>

      <Section title="Sobreposições">
        <div className="actions">
          <Menu trigger={<Button>Abrir menu</Button>} align="start">
            <MenuLabel>Projeto</MenuLabel>
            <MenuItem icon={Play} onSelect={() => undefined}>
              Indexar de novo
            </MenuItem>
            <MenuItem icon={Settings} onSelect={() => undefined}>
              Configurações
            </MenuItem>
            <MenuSeparator />
            <MenuItem icon={Trash} tone="danger" onSelect={() => undefined}>
              Excluir
            </MenuItem>
          </Menu>
          <Dialog
            open={dialogOpen}
            onOpenChange={setDialogOpen}
            trigger={<Button>Abrir diálogo</Button>}
            title="Diálogo de exemplo"
            description={<p>Um diálogo com título, descrição e ações.</p>}
            footer={
              <>
                <Button onClick={() => setDialogOpen(false)}>Cancelar</Button>
                <Button variant="primary" onClick={() => setDialogOpen(false)}>
                  Confirmar
                </Button>
              </>
            }
          />
          <ConfirmDialog
            trigger="Excluir"
            triggerLabel="Excluir o projeto de exemplo"
            title="Excluir o projeto de exemplo?"
            description={<p>Nada é excluído de verdade aqui.</p>}
            confirmLabel="Excluir"
            onConfirm={() => new Promise((resolve) => setTimeout(resolve, 800))}
          />
        </div>
      </Section>

      <Section title="Dados">
        <StatGrid>
          <Stat label="Arquivos" value="1.240" />
          <Stat label="Trechos" value="4.120" />
          <Stat label="Latência p95" value="1,9 s" />
          <Stat label="Custo" value="US$ 0,0125" hint="estimado" />
        </StatGrid>
        <Progress value={42} max={100} label="Arquivos processados: 42 de 100" />
        <Progress value={null} label="Aguardando" />
      </Section>

      <Section title="Carregando e vazio">
        <Loading />
        <Skeleton width="12rem" height="2rem" />
        <EmptyState
          icon={FolderOpen}
          title="Nenhum projeto ainda"
          action={
            <Button variant="primary" icon={Plus}>
              Importar o primeiro projeto
            </Button>
          }
        >
          <p>Importe uma pasta local ou um ZIP para começar.</p>
        </EmptyState>
      </Section>

      <Section title="Pergunta e evidências">
        <div className="actions">
          <SegmentedControl
            label="Versão do arquivo"
            value={version}
            options={[
              { value: 'answered', label: 'Da resposta' },
              { value: 'current', label: 'Atual' },
            ]}
            onChange={setVersion}
          />
          <p>
            O total sai do serviço{' '}
            <HoverCard
              content={
                <>
                  <p>
                    <code>src/billing/RefundService.java</code>
                  </p>
                  <p className="muted">linhas 12–14</p>
                </>
              }
            >
              <button
                type="button"
                className="citation"
                aria-label="Abrir a evidência E1: src/billing/RefundService.java, linhas 12–14"
              >
                E1
              </button>
            </HoverCard>
            .
          </p>
          <Popover label="Detalhes da resposta" trigger={<Button size="sm">Detalhes</Button>}>
            <p>1,2 s · 6 evidências · US$ 0,0004 estimado</p>
          </Popover>
        </div>
        <SplitSample />
      </Section>

      <Section title="Formulários">
        <TextField
          label="Nome do workspace"
          placeholder="Meu workspace"
          hint="Até 100 caracteres."
        />
        <TextField label="Com erro" defaultValue="" error="O nome é obrigatório." />
        <TextField label="Desabilitado" defaultValue="sample-shop" disabled />
        <SelectField label="Raiz" defaultValue="default">
          <option value="default">Projetos</option>
          <option value="other">Outra raiz</option>
        </SelectField>
        <TextAreaField label="Padrões de exclusão" rows={3} wide hint="Um padrão por linha." />
        <label className="checkbox">
          <input type="checkbox" defaultChecked />
          Entendi que trechos podem ser enviados ao provedor.
        </label>
      </Section>
    </div>
  );
}
