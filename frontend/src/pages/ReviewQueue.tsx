import { ClipboardList, ShieldCheck } from 'lucide-react';
import { CandidateCard, EmptyState, PageHeading, ProposalStatusChip } from '../components/OperationsUI';
import { useState } from 'react';
import { SpecificationComparison } from '../components/SpecificationComparison';
import { isPending, proposalLabels } from '../types/workspace';
import type { Batch, CatalogueSource, Proposal, ReviewAction, Workspace } from '../types/workspace';

const decisions: Record<ReviewAction, string> = { APPROVE: 'Confirm Match', SEPARATE: 'Keep Separate', CLARIFY: 'Request Clarification' };

export function ReviewQueue({ proposals, initialCase, sources, batches, history, canReview, onDecision }: {
  proposals: Proposal[]; initialCase: string; sources: Map<number, CatalogueSource>; batches: Map<string, Batch>;
  history: Workspace['decisions']; canReview: boolean; onDecision: (id: string, action: ReviewAction, note: string) => Promise<void>;
}) {
  const [selected, setSelected] = useState(initialCase);
  const [showClosed, setShowClosed] = useState(Boolean(initialCase && proposals.find(p => p.id === initialCase && !isPending(p))));
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(20);
  const [rationale, setRationale] = useState('');
  const [action, setAction] = useState<ReviewAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const pending = proposals.filter(isPending);
  const rows = (showClosed ? proposals.filter(p => !isPending(p)) : pending).filter(p =>
    [p.id, proposalLabels[p.status], ...[p.recordA, p.recordB].map(id => sources.get(id)?.material.description ?? '')]
      .join(' ').toLowerCase().includes(query.toLowerCase()));
  const active = rows.find(item => item.id === selected) ?? rows[0];

  function resetDecision() {
    setRationale('');
    setMessage('');
    setAction(null);
  }

  async function submit() {
    if (!active || !action || !rationale.trim()) return;
    setMessage('');
    setBusy(true);
    try {
      await onDecision(active.id, action, rationale);
      resetDecision();
      setMessage(action === 'CLARIFY' ? 'Clarification request recorded in MATRIQ.' : 'Decision recorded.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Decision could not be recorded.');
    } finally {
      setBusy(false);
    }
  }

  return <div className="ops-page">
    <PageHeading title="Review Queue" description="Inspect conflicts and record authorized engineering decisions." />
    <div className="panel flex flex-wrap items-center gap-3 rounded p-3">
      <button className={`btn btn-sm ${!showClosed ? 'btn-cobalt' : 'btn-secondary'}`} onClick={() => { setShowClosed(false); resetDecision(); }}>Pending Reviews ({pending.length})</button>
      <button className={`btn btn-sm ${showClosed ? 'btn-cobalt' : 'btn-secondary'}`} onClick={() => { setShowClosed(true); resetDecision(); }}>Resolved ({proposals.length - pending.length})</button>
      <input aria-label="Search review cases" placeholder="Search review cases" className="rounded border border-[var(--border)] px-3 py-1 text-sm" value={query} onChange={event => { setQuery(event.target.value); setLimit(20); resetDecision(); }} />
    </div>
    <div className="grid items-start gap-4 lg:grid-cols-[minmax(14rem,1fr)_minmax(0,2fr)]">
      <section className="panel ops-panel">
        <h3 className="ops-panel-heading"><ClipboardList size={15} /> {showClosed ? 'Resolved cases' : 'Pending candidates'} <span className="ops-panel-count">{rows.length}</span></h3>
        {rows.slice(0, limit).map(item => <CandidateCard key={item.id} item={item} sources={sources} review
          selected={active?.id === item.id} onSelect={() => { setSelected(item.id); resetDecision(); }} />)}
        {rows.length > limit && <div className="p-3"><button className="btn btn-secondary btn-sm" onClick={() => setLimit(limit + 20)}>Show more cases</button></div>}
        {rows.length === 0 && <EmptyState title="No review cases in this view." />}
      </section>
      {active && <section className="panel ops-panel">
        <h3 className="ops-panel-heading"><ShieldCheck size={15} /> Engineering review <span className="ops-panel-count">Case {active.id}</span></h3>
        <div className="space-y-3 p-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h4 className="text-sm font-semibold">{sources.get(active.recordA)?.material.description}</h4><ProposalStatusChip proposal={active} />
        </div>
        <SpecificationComparison proposal={active} sources={sources} batches={batches} />
        {active.status === 'CONFLICT' && <p className="ops-alert">Confirmation requires corrected technical evidence. A reviewer can request clarification or keep the source records separate.</p>}
        {message && <p role={message.endsWith('recorded.') || message.includes('recorded in MATRIQ') ? 'status' : 'alert'} className="rounded border border-[var(--border)] bg-[var(--muted-light)] p-2 text-xs">{message}</p>}
        {!canReview ? <p className="text-xs text-[var(--text-secondary)]">An authorized reviewer can record a decision.</p>
          : active.status !== 'SEPARATED' && <div className="space-y-2 border-t border-[var(--border)] pt-3">
            <label className="block text-xs font-semibold" htmlFor="review-note">Decision rationale</label>
            <textarea id="review-note" value={rationale} maxLength={2000} onChange={e => setRationale(e.target.value)} className="w-full rounded border border-[var(--border)] p-2 text-sm" rows={3} />
            <div className="flex flex-wrap gap-2">
              <button className="btn btn-cobalt btn-sm" disabled={!active.evidence.approvable || active.status !== 'POSSIBLE' || busy} onClick={() => setAction('APPROVE')}>Confirm Match</button>
              <button className="btn btn-secondary btn-sm" disabled={active.clarified || !isPending(active) || busy} onClick={() => setAction('CLARIFY')}>Request Clarification</button>
              <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => setAction('SEPARATE')}>Keep Separate</button>
            </div>
            {action && <div className="mt-3 flex flex-wrap items-center gap-2 rounded bg-[var(--muted-light)] p-3 text-sm">
              <span>Record “{decisions[action]}” for case {active.id}?</span>
              <button className="btn btn-cobalt btn-sm" disabled={busy || !rationale.trim()} onClick={() => void submit()}>{busy ? 'Saving…' : 'Record Decision'}</button>
              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => setAction(null)}>Cancel</button>
            </div>}
          </div>}
        <div className="border-t border-[var(--border)] pt-3">
          <h4 className="mb-2 text-xs font-semibold">Decision History</h4>
          {history.filter(item => item.proposalId === active.id).slice().reverse().map(item => <div className="mb-2 rounded border border-[var(--border)] p-2 text-xs" key={item.id}>
            <strong>{decisions[item.action]} · {item.reviewerName}</strong> · {new Date(item.timestamp).toLocaleString()}
            <p className="mt-1">{item.rationale}</p>
          </div>)}
          {!history.some(item => item.proposalId === active.id) && <p className="text-xs text-[var(--text-secondary)]">No decisions recorded for this case.</p>}
        </div>
        </div>
      </section>}
    </div>
  </div>;
}
