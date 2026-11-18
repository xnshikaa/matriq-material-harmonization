import { AlertTriangle, ArrowRight, Building2, Database, ShieldCheck, ClipboardList, UploadCloud, Clock, BookOpen } from 'lucide-react';
import { EmptyState, OrganisationBadges, PageHeading, ProposalStatusChip, StatusChip } from '../components/OperationsUI';
import type { Workspace } from '../types/workspace';
import type { Organization } from '../types';
import { isPending } from '../types/workspace';

interface Props extends Pick<Workspace, 'records' | 'batches' | 'registry' | 'proposals'> {
  organizations: Organization[];
  decisions: Workspace['decisions'];
  run: Workspace['run'];
  sources: Map<number, Workspace['records'][number]>;
  scope: string;
  onNavigate: (page: string, id?: string) => void;
}

export function Dashboard({ records, batches, registry, proposals, organizations, sources, scope, decisions, run, onNavigate }: Props) {
  const pending = proposals.filter(isPending);
  const contributors = new Set(records.map(record => record.material.cpse)).size;
  const metrics = [
    { title: 'Material Records', value: records.length, detail: 'Accepted catalogue records', page: 'catalogues', icon: Database, tone: 'navy' },
    { title: 'Verified Material Identities', value: registry.length, detail: 'Published MATRIQ identities', page: 'registry', icon: ShieldCheck, tone: 'verified' },
    { title: 'Pending Review Decisions', value: pending.length, detail: 'Open engineering cases', page: 'review-queue', icon: ClipboardList, tone: 'warning' },
    { title: 'Contributing Organisations', value: contributors, detail: 'Organisations with accepted records', page: 'organizations', icon: Building2, tone: 'navy' },
  ];
  const discrepancies = pending.filter(proposal => proposal.status === 'CONFLICT');

  const recentIdentities = [...registry].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 4);
  const actionLabels = { APPROVE: 'Match confirmed', SEPARATE: 'Records kept separate', CLARIFY: 'Clarification requested' };
  const recentActivity = [
    ...batches.map(batch => ({
      id: 'batch-' + batch.id, timestamp: batch.uploadDate, context: batch.organizationCode,
      title: 'Catalogue imported', detail: `${batch.fileName} · ${batch.validRows.toLocaleString()} accepted records`,
    })),
    ...decisions.filter(decision => scope === 'ALL' || [decision.recordA, decision.recordB].some(id => sources.get(id)?.material.cpse === scope))
      .map(decision => ({
        id: 'decision-' + decision.id, timestamp: decision.timestamp, context: decision.reviewerName,
        title: actionLabels[decision.action], detail: `Engineering case ${decision.proposalId}`,
      })),
    ...(run ? [{ id: 'last-run', timestamp: run.timestamp, context: 'All Organisations', title: 'Harmonization completed',
      detail: `${run.records.toLocaleString()} records · ${run.comparisons.toLocaleString()} pairs compared` }] : []),
  ].sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp)).slice(0, 5);

  return <div className="ops-page dashboard-page">
    <PageHeading title="Material Intelligence Command Center" description="Catalogue coverage, engineering review and verified material identities for the selected organisation scope." />
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {metrics.map(({ title, value, detail, page, icon: Icon, tone }) => <button key={page} onClick={() => onNavigate(page)} className="panel ops-metric" data-tone={tone}>
        <span className="ops-metric-icon"><Icon size={17} aria-hidden /></span>
        <span className="label-tech">{title}</span><strong>{value.toLocaleString()}</strong>
        <span className="ops-metric-caption">{detail}</span>
      </button>)}
    </div>
    <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <div className="grid min-w-0 gap-5">
      <section className="panel ops-panel">
        <div className="dashboard-panel-header">
          <h3 className="flex items-center gap-2"><AlertTriangle className="shrink-0 text-[#ac5823]" size={18} /> Material Specification Discrepancies</h3>
          <button className="btn btn-secondary btn-sm" onClick={() => onNavigate('review-queue')}>Open Review Queue <ArrowRight size={13} /></button>
        </div>
        {discrepancies.length > 0 && <p className="dashboard-panel-copy">{discrepancies.length.toLocaleString()} open conflicts require engineering review. Conflicting records cannot be approved as equivalent.</p>}
        {discrepancies.length === 0 && <EmptyState title="No open specification conflicts" detail="Open conflicts appear here after harmonization." />}
        <div className="dashboard-discrepancy-list">
          {discrepancies.slice(0, 2).map(proposal => {
            const left = sources.get(proposal.recordA);
            const right = sources.get(proposal.recordB);
            const conflicts = proposal.evidence.attributes.filter(attribute => attribute.state === 'CONFLICT');
            return <article key={proposal.id} className="ops-discrepancy">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="ops-case-code">Case {proposal.id}</span>
                <ProposalStatusChip proposal={proposal} />
              </div>
              <h4 className="line-clamp-2 text-sm font-semibold">{left?.material.description ?? right?.material.description}</h4>
              <OrganisationBadges codes={[left?.material.cpse, right?.material.cpse].filter((code): code is string => Boolean(code))} />
              <dl className="ops-conflict-values">{conflicts.slice(0, 1).map(attribute => <div key={attribute.name}>
                <dt>{attribute.name}</dt><dd>{attribute.left || 'Unknown'} <span aria-hidden>↔</span> {attribute.right || 'Unknown'}</dd>
              </div>)}</dl>
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-[var(--text-secondary)]">{proposal.clarified ? 'Clarification requested' : 'Pending engineering review'}</span>
                <button className="btn btn-secondary btn-sm" onClick={() => onNavigate('review-queue', proposal.id)}>View Details <ArrowRight size={12} /></button>
              </div>
            </article>;
          })}
        </div>
      </section>
        <section className="panel ops-panel">
          <div className="dashboard-panel-header">
            <div><p className="label-tech">Material Registry</p><h3>Verified Material Identities</h3></div>
            <button className="dashboard-text-link" onClick={() => onNavigate('registry')}>View Registry <ArrowRight size={14} /></button>
          </div>
          {recentIdentities.length === 0 ? <EmptyState kind="registry" title="No verified material identities" detail="Reviewer-confirmed identities appear here." />
            : <div className="overflow-x-auto"><table className="ops-table w-full min-w-[28rem]">
              <thead><tr><th scope="col">Material code</th><th scope="col">Material description</th><th scope="col">Organisations</th></tr></thead>
              <tbody>{recentIdentities.map(({ group }) => <tr key={group.id}>
                <td><button className="dashboard-text-link whitespace-nowrap" onClick={() => onNavigate('registry', group.id)}>{group.nationalCode}</button></td>
                <td>{group.members[0]?.description}</td>
                <td><OrganisationBadges codes={group.members.map(member => member.cpse)} /></td>
              </tr>)}</tbody>
            </table></div>}
        </section>
      </div>
      <div className="grid min-w-0 gap-5">
        <section className="panel ops-panel">
          <div className="dashboard-panel-header"><div><p className="label-tech">Operational Workflows</p><h3>Quick Actions</h3></div></div>
          <div className="dashboard-action-list">
            {[{ page: 'catalogues', id: 'upload', label: 'Material Catalogues & Ingestion', detail: `${batches.length} catalogue batches`, icon: UploadCloud, tone: 'cobalt' },
              { page: 'review-queue', label: 'Review Pending Decisions', detail: `${pending.length.toLocaleString()} open review cases`, icon: ClipboardList, tone: 'warning' },
              { page: 'registry', label: 'Unified Material Registry', detail: `${registry.length.toLocaleString()} verified material identities`, icon: BookOpen, tone: 'verified' }]
              .map(({ page, id, label, detail, icon: Icon, tone }) => (
                <button key={page} className="dashboard-action" data-tone={tone} onClick={() => onNavigate(page, id)}>
                  <span className="dashboard-action-icon"><Icon size={17} aria-hidden /></span>
                  <span><strong>{label}</strong><span className="dashboard-action-detail">{detail}</span></span>
                  <ArrowRight size={15} className="shrink-0" aria-hidden />
                </button>))}
          </div>
        </section>
        <section className="panel ops-panel">
          <div className="dashboard-panel-header"><div><p className="label-tech">Workspace Activity</p><h3>Recent System Activity</h3></div></div>
          {recentActivity.length === 0 ? <EmptyState kind="catalogue" title="No recorded activity in this scope" />
            : <ol className="dashboard-activity">
              {recentActivity.map(event => <li key={event.id}>
                <div className="dashboard-activity-meta">
                  <time dateTime={event.timestamp}><Clock size={13} aria-hidden />{new Date(event.timestamp).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}</time>
                  <span>{event.context}</span>
                </div>
                <strong>{event.title}</strong><p>{event.detail}</p>
              </li>)}
            </ol>}
        </section>
      </div>
    </div>
    <section className="panel ops-panel">
      <h3 className="ops-panel-heading"><Building2 size={17} /><span>Catalogue Coverage</span><span className="ops-panel-count">{batches.length} batches</span></h3>
      <div className="overflow-x-auto"><table className="ops-table w-full">
        <thead><tr>{['Organisation', 'Batches', 'Accepted', 'Import status'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
        <tbody>{organizations.map(org => {
          const imports = batches.filter(batch => batch.organizationCode === org.code);
          const skipped = imports.reduce((sum, batch) => sum + batch.recordCount - batch.validRows, 0);
          return <tr key={org.code}>
            <td><button className="font-semibold text-[var(--cobalt)] hover:underline" onClick={() => onNavigate('catalogues', org.code)}>{org.name}</button></td>
            <td className="font-mono">{imports.length}</td><td className="font-mono">{org.recordCount}</td>
            <td><StatusChip tone={imports.length ? skipped ? 'review' : 'verified' : 'neutral'}>{imports.length ? skipped ? `Imported · ${skipped} skipped` : 'Imported' : 'Awaiting import'}</StatusChip></td>
          </tr>;
        })}</tbody>
      </table></div>
    </section>
  </div>;
}
