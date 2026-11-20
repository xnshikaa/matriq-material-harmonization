import { ClipboardList, ShieldCheck } from 'lucide-react';
import { CandidateCard, EmptyState, PageHeading, ProposalStatusChip } from '../components/OperationsUI';
import { useState } from 'react';
import { SpecificationComparison } from '../components/SpecificationComparison';
import { isPending, proposalLabels } from '../types/workspace';
import type { Batch, CatalogueSource, Proposal, ReviewAction, Workspace } from '../types/workspace';
import { bestMaterialSearchMatch } from '../search/materialSearch';

const decisions: Record<ReviewAction, string> = { APPROVE: 'Confirm Match', SEPARATE: 'Keep Separate', CLARIFY: 'Request Clarification' };

export function ReviewQueue({ proposals, initialCase, sources, batches, history, canReview, reviewerId, onDecision, onWithdrawClarification, onWithdrawApproval }: {
  proposals: Proposal[]; initialCase: string; sources: Map<number, CatalogueSource>; batches: Map<string, Batch>;
  history: Workspace['decisions']; canReview: boolean; reviewerId: string;
  onDecision: (id: string, action: ReviewAction, note: string) => Promise<void>;
  onWithdrawClarification: (id: string, decisionId: string) => Promise<void>;
  onWithdrawApproval: (id: string, decisionId: string) => Promise<void>;
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
  const rankedRows = (showClosed ? proposals.filter(p => !isPending(p)) : pending)
    .map(item => ({ item, match: bestMaterialSearchMatch(query, [item.id, proposalLabels[item.status],
      ...[item.recordA, item.recordB].flatMap(id => {
        const source = sources.get(id);
        return source ? [source.material.description, source.material.code, source.material.cpse, source.grade,
          source.dimensions, source.specification, source.materialType] : [];
      })]) }))
    .filter(row => !query.trim() || row.match.score >= 0.38)
    .sort((a, b) => b.match.score - a.match.score || a.item.id.localeCompare(b.item.id));
  const rows = rankedRows.map(row => row.item);
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

  async function withdrawClarification(decisionId: string) {
    if (!active) return;
    setBusy(true);
    setMessage('');
    try {
      await onWithdrawClarification(active.id, decisionId);
      resetDecision();
      setMessage('Your clarification request was removed.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Clarification request could not be removed.');
    } finally {
      setBusy(false);
    }
  }

  async function withdrawApproval(decisionId: string) {
    if (!active) return;
    setBusy(true);
    setMessage('');
    try {
      await onWithdrawApproval(active.id, decisionId);
      resetDecision();
      setMessage('Your approval was withdrawn; the case is pending again.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Approval could not be withdrawn.');
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
        {active.status === 'CONFLICT' && <p className="ops-alert">Conflicting technical evidence prevents automatic unification. An authorized reviewer may approve the match with a documented rationale, keep the records separate, or request clarification.</p>}
        {message && <p role={message.endsWith('recorded.') || message.includes('recorded in MATRIQ') ? 'status' : 'alert'} className="rounded border border-[var(--border)] bg-[var(--muted-light)] p-2 text-xs">{message}</p>}
        {!canReview ? <p className="text-xs text-[var(--text-secondary)]">An authorized reviewer can record a decision.</p>
          : active.status !== 'SEPARATED' && <div className="space-y-2 border-t border-[var(--border)] pt-3">
            <div className="flex flex-wrap gap-2">
              {(active.status === 'POSSIBLE' || active.status === 'CONFLICT') && <button className="btn btn-cobalt btn-sm" disabled={busy || (active.status === 'POSSIBLE' && !active.evidence.approvable)} onClick={() => { setAction('APPROVE'); setRationale(''); setMessage(''); }}>Approve Match</button>}
              <button className="btn btn-secondary btn-sm" disabled={active.clarified || !isPending(active) || busy} onClick={() => { setAction('CLARIFY'); setRationale(''); setMessage(''); }}>Request Clarification</button>
              <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => { setAction('SEPARATE'); setRationale(''); setMessage(''); }}>Keep Separate</button>
            </div>
            {!action && <p className="text-xs text-[var(--text-secondary)]">Choose a decision, add its note, then click Record Decision to save it.</p>}
            {action && <div className="mt-3 space-y-2 rounded bg-[var(--muted-light)] p-3 text-sm">
              <label className="block text-xs font-semibold" htmlFor="review-note">{action === 'CLARIFY' ? 'Clarification request note' : 'Decision rationale'}</label>
              <textarea id="review-note" value={rationale} maxLength={2000} onChange={e => setRationale(e.target.value)} className="w-full rounded border border-[var(--border)] p-2 text-sm" rows={3}
                placeholder={action === 'CLARIFY' ? 'Describe what information or evidence is needed.' : 'Explain the basis for this engineering decision.'} />
              <div className="flex flex-wrap items-center gap-2">
              <span>Record “{decisions[action]}” for case {active.id}?</span>
              <button className="btn btn-cobalt btn-sm" disabled={busy || !rationale.trim()} onClick={() => void submit()}>{busy ? 'Saving…' : 'Record Decision'}</button>
              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => setAction(null)}>Cancel</button>
              </div>
            </div>}
          </div>}
        <div className="border-t border-[var(--border)] pt-3">
          <h4 className="mb-2 text-xs font-semibold">Decision History</h4>
          {history.filter(item => item.proposalId === active.id).slice().reverse().map(item => <div className="mb-2 rounded border border-[var(--border)] p-2 text-xs" key={item.id}>
            <strong>{decisions[item.action]} · {item.reviewerName}</strong> · {new Date(item.timestamp).toLocaleString()}
            <p className="mt-1">{item.rationale}</p>
            {canReview && item.action === 'CLARIFY' && item.reviewerId === reviewerId &&
              <button className="mt-2 text-[var(--conflict)] underline" disabled={busy} onClick={() => void withdrawClarification(item.id)}>Remove my clarification request</button>}
            {canReview && item.action === 'APPROVE' && item.reviewerId === reviewerId &&
              <button className="mt-2 text-[var(--conflict)] underline" disabled={busy} onClick={() => void withdrawApproval(item.id)}>Undo my approval</button>}
          </div>)}
          {!history.some(item => item.proposalId === active.id) && <p className="text-xs text-[var(--text-secondary)]">No decisions recorded for this case.</p>}
        </div>
        </div>
      </section>}
    </div>
  </div>;
}
