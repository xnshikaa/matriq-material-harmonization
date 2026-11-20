import { useCallback, useEffect, useMemo, useState } from 'react';
import { IntroVideo } from './components/IntroVideo';
import { Sidebar } from './components/Sidebar';
import { TopBar } from './components/TopBar';
import { Authentication } from './pages/Authentication';
import { Dashboard } from './pages/Dashboard';
import { Organizations } from './pages/Organizations';
import { MaterialCatalogues } from './pages/MaterialCatalogues';
import { HarmonizationWorkspace } from './pages/HarmonizationWorkspace';
import { ReviewQueue } from './pages/ReviewQueue';
import { UnifiedRegistry } from './pages/UnifiedRegistry';
import { api } from './services/api';
import { isPending } from './types/workspace';
import type { Account, ReviewAction, Workspace } from './types/workspace';
import type { Organization } from './types';

const pages = new Set(['dashboard', 'organizations', 'catalogues', 'harmonization', 'review-queue', 'registry']);

function readRoute() {
  const [page, detail = ''] = location.pathname.split('/').filter(Boolean);
  return { page: pages.has(page) ? page : 'dashboard', detail };
}

export function App() {
  const [introDone, setIntroDone] = useState(Boolean(sessionStorage.getItem('matriq_intro_played')) || location.pathname !== '/');
  const finishIntro = useCallback(() => setIntroDone(true), []);
  const [account, setAccount] = useState<Account | null | undefined>();
  const [route, setRoute] = useState(readRoute);

  const refreshSession = useCallback(async () => {
    try {
      setAccount((await api.session()).user);
    } catch {
      setAccount(null);
    }
  }, []);

  useEffect(() => {
    void refreshSession();
    const pop = () => setRoute(readRoute());
    const expire = () => {
      setAccount(null);
      void refreshSession();
    };
    window.addEventListener('popstate', pop);
    window.addEventListener('matriq:expired', expire);
    return () => {
      window.removeEventListener('popstate', pop);
      window.removeEventListener('matriq:expired', expire);
    };
  }, [refreshSession]);

  function navigate(page: string, detail = '') {
    if (!pages.has(page)) return;
    history.pushState(null, '', '/' + page + (detail ? '/' + encodeURIComponent(detail) : ''));
    setRoute({ page, detail });
    window.scrollTo(0, 0);
  }

  if (!introDone) return <IntroVideo onFinish={finishIntro} />;
  if (!account) return <Authentication onAuthenticated={setAccount} />;

  return <Application key={account.id} user={account} route={route} navigate={navigate} onLogout={async () => {
    await api.logout();
    setAccount(null);
  }} />;
}

function Application({ user, route, navigate, onLogout }: {
  user: Account; route: { page: string; detail: string };
  navigate: (page: string, detail?: string) => void; onLogout: () => Promise<void>;
}) {
  const [workspace, setWorkspace] = useState<Workspace>();
  const [collapsed, setCollapsed] = useState(() => window.matchMedia('(max-width: 760px)').matches);
  const [scope, setScope] = useState('ALL');
  const [error, setError] = useState('');
  const [harmonizing, setHarmonizing] = useState(false);

  const refresh = useCallback(async () => {
    setError('');
    try {
      setWorkspace(await api.workspace());
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Workspace could not be loaded.');
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const data = useMemo(() => {
    if (!workspace) return null;
    const sources = new Map(workspace.records.map(source => [source.material.id, source]));
    const batches = new Map(workspace.batches.map(batch => [batch.id, batch]));
    const counts = new Map<string, number>();
    for (const source of workspace.records) {
      const code = source.material.cpse;
      counts.set(code, (counts.get(code) ?? 0) + 1);
    }
    const latestBatch = new Map([...workspace.batches].sort((a, b) => a.uploadDate.localeCompare(b.uploadDate))
      .map(batch => [batch.organizationCode, batch.uploadDate]));
    const organizations = workspace.organizations.map((org): Organization => ({
      ...org, id: org.code, recordCount: counts.get(org.code) ?? 0, lastUploadDate: latestBatch.get(org.code) ?? '',
      status: 'ACTIVE',
    }));
    const inScope = (code: string | undefined) => scope === 'ALL' || scope === code;
    const proposals = workspace.proposals.filter(proposal =>
      inScope(sources.get(proposal.recordA)?.material.cpse) || inScope(sources.get(proposal.recordB)?.material.cpse));
    return {
      sources, batches, organizations, proposals,
      records: workspace.records.filter(source => inScope(source.material.cpse)),
      scopedOrganizations: organizations.filter(org => inScope(org.code)),
      scopedBatches: workspace.batches.filter(batch => inScope(batch.organizationCode)).slice().reverse(),
      registry: workspace.registry.filter(identity => identity.group.members.some(member => inScope(member.cpse))),
    };
  }, [workspace, scope]);

  function go(page: string, detail?: string) {
    if (page === 'catalogues' && detail && detail !== 'upload') {
      setScope(detail);
      navigate(page, 'upload');
    } else {
      navigate(page, detail);
    }
  }

  async function harmonize() {
    setHarmonizing(true);
    setError('');
    try {
      setWorkspace(await api.harmonize());
      navigate('harmonization');
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Harmonization failed.');
    } finally {
      setHarmonizing(false);
    }
  }

  async function review(id: string, action: ReviewAction, note: string) {
    setWorkspace(await api.review(id, action, note));
  }

  async function withdrawClarification(id: string, decisionId: string) {
    setWorkspace(await api.withdrawClarification(id, decisionId));
  }

  async function withdrawApproval(id: string, decisionId: string) {
    setWorkspace(await api.withdrawApproval(id, decisionId));
  }

  return <div className="ops-shell flex min-h-screen bg-[var(--background)] text-[var(--text-primary)]">
    <Sidebar activeTab={route.page} onSelectTab={go} collapsed={collapsed} onToggleCollapse={() => setCollapsed(!collapsed)}
      pendingReviewCount={data?.proposals.filter(isPending).length ?? 0} />
    <div className="flex min-w-0 flex-1 flex-col">
      <TopBar activeTab={route.page} isHarmonizing={harmonizing} onRunHarmonization={() => void harmonize()}
        activeOrgFilter={scope} onSelectOrgFilter={setScope} organizations={data?.organizations ?? []} user={user}
        onLogout={async () => {
          try { await onLogout(); } catch { setError('Sign-out failed. Try again.'); }
        }} />
      <main className="mx-auto w-full max-w-[106rem] flex-1 p-3 sm:p-6 xl:p-7">
        {error && <div role="alert" className="panel mb-4 flex items-center gap-3 rounded p-3 text-sm text-[var(--conflict)]">{error}
          {!workspace && <button className="btn btn-secondary btn-sm" onClick={() => void refresh()}>Retry</button>}</div>}
        {(!workspace || !data) && !error && <div role="status" className="grid gap-4"><span className="sr-only">Loading workspace…</span><div className="ops-skeleton" /><div className="grid gap-3 sm:grid-cols-3">{[1, 2, 3].map(item => <div key={item} className="ops-skeleton" />)}</div></div>}
        {workspace && data && <>
          {route.page === 'dashboard' && <Dashboard records={data.records} batches={data.scopedBatches} registry={data.registry} proposals={data.proposals}
            scope={scope} sources={data.sources} organizations={data.scopedOrganizations} decisions={workspace.decisions} run={workspace.run} onNavigate={go} />}
          {route.page === 'organizations' && <Organizations organizations={data.scopedOrganizations} batches={workspace.batches} onAddOrganization={async organization => {
            setWorkspace(await api.addOrganization(organization));
            setScope('ALL');
          }} onNavigateToCatalogues={code => go('catalogues', code)} />}
          {route.page === 'catalogues' && <MaterialCatalogues key={scope + route.detail} initiallyUploading={route.detail === 'upload'} organizations={data.organizations}
            preselectedOrgCode={scope === 'ALL' ? undefined : scope} onImportComplete={refresh} recentBatches={data.scopedBatches} />}
          {route.page === 'harmonization' && <>
            {workspace.run && <p className="mb-3 text-xs text-[var(--text-secondary)]">
              Last network run covered {workspace.run.records} accepted records and compared {workspace.run.comparisons} pairs.
              {workspace.run.records < workspace.records.length && ' Run harmonization to include newer records.'}
            </p>}
            <HarmonizationWorkspace proposals={data.proposals} records={data.records} sources={data.sources} batches={data.batches}
              onReview={id => go('review-queue', id)} />
          </>}
          {route.page === 'review-queue' && <ReviewQueue key={route.detail + ':' + scope} proposals={data.proposals} initialCase={route.detail}
            sources={data.sources} batches={data.batches} history={workspace.decisions} canReview={user.reviewer} reviewerId={user.id}
            onDecision={review} onWithdrawClarification={withdrawClarification} onWithdrawApproval={withdrawApproval} />}
          {route.page === 'registry' && <UnifiedRegistry key={route.detail + ':' + scope} registry={data.registry} initialId={route.detail}
            sources={data.sources} batches={data.batches} history={workspace.decisions} />}
        </>}
      </main>
      <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-[#d8d6ce] bg-[#faf9f5] px-4 py-2.5 font-mono text-[10px] text-[var(--text-secondary)]"><span><strong className="text-[var(--navy)]">MATRIQ</strong> / Material Intelligence &amp; Harmonization Platform</span><span>One Material. One Identity.</span></footer>
    </div>
  </div>;
}

export default App;
