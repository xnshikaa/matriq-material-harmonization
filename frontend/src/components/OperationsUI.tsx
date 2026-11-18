import { AlertTriangle, Archive, FileSearch, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import type { CatalogueSource, Proposal, ProposalStatus } from '../types/workspace';
import { proposalLabels } from '../types/workspace';

const tones: Record<ProposalStatus, string> = {
  CONFIRMED: 'verified', POSSIBLE: 'review', INSUFFICIENT: 'neutral', CONFLICT: 'review', SEPARATED: 'neutral',
};

export function PageHeading({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return <header className="ops-page-heading">
    <div><h2>{title}</h2>{description && <p>{description}</p>}</div>
    {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
  </header>;
}

export function StatusChip({ children, tone = 'neutral' }: { children: ReactNode; tone?: string }) {
  return <span className={'badge-status badge-' + tone}><span className="badge-dot bg-current" aria-hidden />{children}</span>;
}

export function ProposalStatusChip({ proposal }: { proposal: Proposal }) {
  return <StatusChip tone={tones[proposal.status]}>{proposalLabels[proposal.status]}</StatusChip>;
}

export function OrganisationBadges({ codes }: { codes: string[] }) {
  return <span className="flex flex-wrap gap-1.5">{[...new Set(codes)].map(code => <span className="ops-org-chip" key={code}>{code}</span>)}</span>;
}

export function EmptyState({ title, detail, kind = 'search' }: { title: string; detail?: string; kind?: 'search' | 'registry' | 'catalogue' }) {
  const Icon = kind === 'registry' ? ShieldCheck : kind === 'catalogue' ? Archive : FileSearch;
  return <div className="ops-empty"><Icon size={21} aria-hidden /><strong>{title}</strong>{detail && <p>{detail}</p>}</div>;
}

export function CandidateCard({ item, sources, selected, onSelect, review = false }: {
  item: Proposal; sources: Map<number, CatalogueSource>; selected: boolean; onSelect: () => void; review?: boolean;
}) {
  const left = sources.get(item.recordA);
  const right = sources.get(item.recordB);
  const conflict = item.evidence.attributes.find(attribute => attribute.state === 'CONFLICT');
  return <button type="button" onClick={onSelect} aria-pressed={selected} className="ops-case">
    <span className="flex flex-wrap items-center justify-between gap-1.5">
      <span className="ops-case-code">Case {item.id}</span>
      {review && item.status === 'CONFLICT' && <span className="ops-priority"><AlertTriangle size={12} /> Engineering priority</span>}
    </span>
    <strong className="line-clamp-2">{left?.material.description ?? right?.material.description}</strong>
    <span className="flex flex-wrap items-center gap-1.5"><OrganisationBadges codes={[left?.material.cpse, right?.material.cpse].filter((code): code is string => Boolean(code))} /><ProposalStatusChip proposal={item} /></span>
    {review && conflict && <span className="ops-case-conflict line-clamp-2">{conflict.name} · {conflict.left || 'Unknown'} / {conflict.right || 'Unknown'}</span>}
    {item.clarified && <span className="ops-case-code">Clarification requested</span>}
  </button>;
}
