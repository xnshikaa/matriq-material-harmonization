import { ArrowRight, ClipboardList, Layers3 } from 'lucide-react';
import { CandidateCard, EmptyState, PageHeading, ProposalStatusChip } from '../components/OperationsUI';
import { useState } from 'react';
import { SourceDetails, SpecificationComparison } from '../components/SpecificationComparison';
import { proposalLabels } from '../types/workspace';
import type { Batch, CatalogueSource, Proposal, ProposalStatus } from '../types/workspace';

export function HarmonizationWorkspace({ proposals, records, sources, batches, onReview }: {
  proposals: Proposal[]; records: CatalogueSource[]; sources: Map<number, CatalogueSource>;
  batches: Map<string, Batch>; onReview: (id: string) => void;
}) {
  const [mode, setMode] = useState<'proposals' | 'records'>('proposals');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState('');
  const needle = query.trim().toLowerCase();
  const sourceText = (source?: CatalogueSource) => source
    ? [source.material.code, source.material.cpse, source.material.description, source.specification, source.category].join(' ').toLowerCase()
    : '';
  const rows = records.filter(source => sourceText(source).includes(needle));
  const candidates = proposals.filter(proposal => (status === 'ALL' || proposal.status === status) &&
    [proposal.id, proposalLabels[proposal.status], sourceText(sources.get(proposal.recordA)), sourceText(sources.get(proposal.recordB))]
      .join(' ').toLowerCase().includes(needle));
  const size = mode === 'records' ? rows.length : candidates.length;
  const offset = Math.min(page, Math.max(0, Math.ceil(size / 20) - 1)) * 20;
  const visible = candidates.slice(offset, offset + 20);
  const active = visible.find(proposal => proposal.id === selected) ?? visible[0];

  return <div className="ops-page">
    <PageHeading title="Harmonization Workspace" description="Reconcile specifications against original catalogue records and engineering evidence." />
    <div className="panel flex flex-wrap gap-2 rounded p-3">
      <button className={`btn btn-sm ${mode === 'proposals' ? 'btn-cobalt' : 'btn-secondary'}`} onClick={() => { setMode('proposals'); setPage(0); }}>Proposals</button>
      <button className={`btn btn-sm ${mode === 'records' ? 'btn-cobalt' : 'btn-secondary'}`} onClick={() => { setMode('records'); setPage(0); }}>Source Records</button>
      <input className="rounded border border-[var(--border)] px-3 py-1 text-sm" aria-label="Search materials and proposals" placeholder="Search materials or specifications" value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} />
      {mode === 'proposals' && <select className="rounded border border-[var(--border)] px-3 text-sm" aria-label="Proposal status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
        <option value="ALL">All statuses</option>{Object.entries(proposalLabels).map(([key, label]) => <option key={key} value={key as ProposalStatus}>{label}</option>)}
      </select>}
    </div>
    {mode === 'records' ? <div className="panel grid gap-2 rounded p-3">{rows.slice(offset, offset + 20).map(source => <SourceDetails key={source.material.id} source={source} batch={batches.get(source.batchId)} />)}</div>
      : <div className="grid items-start gap-4 lg:grid-cols-[minmax(14rem,1fr)_minmax(0,2fr)]">
        {visible.length > 0 && <section className="panel ops-panel">
          <h3 className="ops-panel-heading"><Layers3 size={15} /> Candidate register <span className="ops-panel-count">{candidates.length}</span></h3>
          {visible.map(proposal => <CandidateCard key={proposal.id} item={proposal} sources={sources}
            selected={active?.id === proposal.id} onSelect={() => setSelected(proposal.id)} />)}
        </section>}
        {active && <section className="panel ops-panel">
          <h3 className="ops-panel-heading"><ClipboardList size={15} /> Reconciliation details <span className="ops-panel-count">Case {active.id}</span></h3>
          <div className="space-y-3 p-3">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <h4 className="text-sm font-semibold">{sources.get(active.recordA)?.material.description}</h4><ProposalStatusChip proposal={active} />
          </div>
          <SpecificationComparison proposal={active} sources={sources} batches={batches} />
          <button className="btn btn-secondary btn-sm" onClick={() => onReview(active.id)}>Open in Review Queue <ArrowRight size={13} /></button>
          </div>
        </section>}
      </div>}
    {size === 0 ? <div className="panel ops-panel"><EmptyState title={mode === 'proposals' ? 'No matching proposals' : 'No source records match this search'} detail={mode === 'proposals' ? 'Run harmonization after importing catalogues, or adjust the filters.' : undefined} /></div>
      : <div className="flex items-center justify-between gap-2 text-xs">
        <span>{offset + 1}–{Math.min(offset + 20, size)} of {size} {mode}</span>
        <div className="flex gap-2">
          <button className="btn btn-secondary btn-sm" disabled={!offset} onClick={() => setPage(offset / 20 - 1)}>Previous</button>
          <button className="btn btn-secondary btn-sm" disabled={offset + 20 >= size} onClick={() => setPage(offset / 20 + 1)}>Next</button>
        </div>
      </div>}
  </div>;
}
