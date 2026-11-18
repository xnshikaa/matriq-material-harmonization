import { AlertTriangle, FileText } from 'lucide-react';
import { StatusChip } from './OperationsUI';
import type { Batch, CatalogueSource, Proposal } from '../types/workspace';

function referenceUrl(value: string) {
  try {
    const url = new URL(value.trim());
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : '';
  } catch {
    return '';
  }
}

export function SourceDetails({ source, batch }: { source: CatalogueSource; batch?: Batch }) {
  const values = Object.entries(source.columns).filter(([, value]) => value.trim());
  return (
    <details className="rounded border border-[var(--border)] bg-[var(--muted-light)] p-2.5">
      <summary className="cursor-pointer text-xs font-bold"><FileText size={13} className="mr-1 inline text-[var(--cobalt)]" />{source.material.cpse} · {source.material.code}</summary>
      <p className="my-2 text-sm">{source.material.description}</p>
      <p className="text-xs text-[var(--text-secondary)]">{batch?.fileName} · CSV record {source.row}</p>
      {batch?.reference && <p className="text-xs text-[var(--text-secondary)]">{batch.reference}</p>}
      <dl className="mt-2 grid gap-x-2 gap-y-1 break-words text-xs [grid-template-columns:minmax(5rem,auto)_minmax(0,1fr)]">
        {values.map(([label, value]) => {
          const href = /url/i.test(label) ? referenceUrl(value) : '';
          return <div key={label} className="contents">
            <dt className="font-semibold">{label}</dt>
            <dd>{href ? <a className="underline" href={href} target="_blank" rel="noopener noreferrer">{value}</a> : value}</dd>
          </div>;
        })}
      </dl>
    </details>
  );
}

export function SpecificationComparison({ proposal, sources, batches }: {
  proposal: Proposal; sources: Map<number, CatalogueSource>; batches: Map<string, Batch>;
}) {
  const left = sources.get(proposal.recordA);
  const right = sources.get(proposal.recordB);
  if (!left || !right) return null;
  return (
    <div className="space-y-4">
      {proposal.status === 'CONFLICT' ? <div className="ops-alert flex items-start gap-2">
        <AlertTriangle size={15} className="mt-0.5 shrink-0" /><div><strong>Conflict profile</strong><p>{proposal.evidence.summary}</p></div>
      </div> : <p className="text-xs text-[var(--text-secondary)]">{proposal.evidence.summary}</p>}
      <h4 className="label-tech">Cross-organisation specification comparison</h4>
      <div className="overflow-x-auto rounded border border-[var(--border)]">
        <table className="ops-table w-full min-w-80">
          <thead className="bg-[var(--muted-light)]"><tr>{['Attribute', left.material.cpse + ' · ' + left.material.code, right.material.cpse + ' · ' + right.material.code, 'Evidence'].map((label, index) => <th key={index} className="p-2">{label}</th>)}</tr></thead>
          <tbody>
            {proposal.evidence.attributes.map(attribute => <tr key={attribute.name} className={attribute.state === 'CONFLICT' ? 'bg-[var(--conflict-bg)] text-[var(--conflict)]' : 'border-t border-[var(--border)]'}>
              <th scope="row" className="p-2">{attribute.name}</th>
              <td className="p-2">{attribute.left || 'Unknown'}</td><td className="p-2">{attribute.right || 'Unknown'}</td>
              <td className="p-2 font-mono"><StatusChip tone={attribute.state === 'MATCH' ? 'verified' : attribute.state === 'CONFLICT' ? 'review' : 'neutral'}>{attribute.state}</StatusChip></td>
            </tr>)}
          </tbody>
        </table>
      </div>
      <section className="rounded border border-[var(--border)] bg-[#f8fafc] p-2.5">
        <h4 className="label-tech">Technical matching evidence</h4>
        <p className="mt-1 text-xs">{proposal.evidence.sharedSpecifications.length ? proposal.evidence.sharedSpecifications.join(' · ') : 'No shared technical standards identified.'}</p>
      </section>
      <h4 className="label-tech">Original source records</h4>
      <div className="grid items-start gap-2 sm:grid-cols-2">
        {[left, right].map(source => <SourceDetails key={source.material.id} source={source} batch={batches.get(source.batchId)} />)}
      </div>
    </div>
  );
}
