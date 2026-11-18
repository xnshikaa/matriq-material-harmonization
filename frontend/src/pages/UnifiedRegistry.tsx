import { EmptyState, OrganisationBadges, PageHeading, StatusChip } from '../components/OperationsUI';
import { useState } from 'react';
import { Search, Download, Share2, ShieldCheck, Table as TableIcon } from 'lucide-react';
import { SourceDetails } from '../components/SpecificationComparison';
import type { Batch, CatalogueSource, Workspace } from '../types/workspace';

function csvCell(value: string) {
  const safe = /^[\s]*[=+@-]|^[\t\r]/.test(value) ? "'" + value : value;
  return '"' + safe.replaceAll('"', '""') + '"';
}

export function UnifiedRegistry({ registry, initialId, sources, batches, history }: {
  registry: Workspace['registry']; initialId: string; sources: Map<number, CatalogueSource>; batches: Map<string, Batch>; history: Workspace['decisions'];
}) {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [category, setCategory] = useState('ALL');
  const [mode, setMode] = useState<'table' | 'network'>('table');
  const [selected, setSelected] = useState(initialId);
  const needle = search.trim().toLowerCase();
  const categories = [...new Set(registry.flatMap(({ group }) => group.members.map(member => sources.get(member.id)?.category).filter(Boolean)))].sort();
  const specifications = (members: Workspace['registry'][number]['group']['members']) => [...new Set(members.flatMap(member => {
    const source = sources.get(member.id);
    return [source?.specification, source?.dimensions, source?.grade].filter(Boolean);
  }))].join(' · ');
  const filtered = registry.filter(({ group }) => (category === 'ALL' || group.members.some(member => sources.get(member.id)?.category === category))
    && [group.nationalCode, specifications(group.members), ...group.members.flatMap(member =>
      [member.description, member.cpse, member.code])].some(value => value.toLowerCase().includes(needle)));
  const offset = Math.min(page, Math.max(0, Math.ceil(filtered.length / 20) - 1)) * 20;
  const active = filtered.find(({ group }) => group.id === selected) ?? (mode === 'network' ? filtered[offset] : undefined);

  function exportCsv() {
    const header = ['MATRIQ Material Code', 'Organisation', 'Source identifier', 'Source description', 'Catalogue file', 'CSV record'];
    const rows = filtered.flatMap(({ group }) => group.members.map(member => {
      const source = sources.get(member.id);
      return [group.nationalCode, member.cpse, member.code, member.description,
        (source && batches.get(source.batchId)?.fileName) ?? '', source ? String(source.row) : ''];
    }));
    const url = URL.createObjectURL(new Blob([[header, ...rows].map(row => row.map(csvCell).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'matriq-unified-registry.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const members = new Set(active?.group.members.map(member => member.id) ?? []);
  const provenance = history.filter(decision => decision.action === 'APPROVE' && members.has(decision.recordA) && members.has(decision.recordB));
  return <div className="ops-page">
    <PageHeading title="MATRIQ Unified Material Registry" description="Verified identities, source records and reviewer decisions."
      actions={<button className="btn btn-secondary btn-sm" onClick={exportCsv} disabled={!filtered.length}><Download className="h-4 w-4" /> Export CSV</button>} />
    <div className="panel flex flex-wrap items-center gap-2 rounded p-3">
      <button className={`btn btn-sm ${mode === 'table' ? 'btn-cobalt' : 'btn-secondary'}`} onClick={() => setMode('table')}><TableIcon className="h-4 w-4" /> Table</button>
      <button className={`btn btn-sm ${mode === 'network' ? 'btn-cobalt' : 'btn-secondary'}`} onClick={() => setMode('network')}><Share2 className="h-4 w-4" /> Provenance Network</button>
      <label className="flex items-center gap-2 rounded border border-[var(--border)] px-2 py-1 text-sm"><Search className="h-4 w-4" />
        <input aria-label="Search verified identities" placeholder="Search verified identities" value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} />
      </label>
      <select aria-label="Material category" className="rounded border border-[var(--border)] bg-white px-2 py-1 text-xs" value={category} onChange={event => { setCategory(event.target.value); setPage(0); }}>
        <option value="ALL">All material categories</option>
        {categories.map(value => <option key={value} value={value}>{value}</option>)}
      </select>
    </div>
    {filtered.length === 0 ? <div className="panel ops-panel"><EmptyState kind="registry" title="No verified material identities in this view." detail="Confirmed identities and their source codes appear here." /></div>
      : <div className="panel ops-panel"><h3 className="ops-panel-heading"><ShieldCheck size={15} /> Verified identities <span className="ops-panel-count">{filtered.length}</span></h3>
        <div className="overflow-x-auto">
        <table className="ops-table w-full min-w-[43rem]">
          <thead className="bg-[var(--muted-light)]"><tr>{['MATRIQ Material Code', 'Material description and specifications', 'Organisations / source codes', 'Verification', 'Issued'].map(label => <th className="p-3" key={label}>{label}</th>)}</tr></thead>
          <tbody>{filtered.slice(offset, offset + 20).map(({ group, issuedAt }) => <tr key={group.id} className="border-t border-[var(--border)]">
            <td className="p-3"><button className="font-mono font-bold text-[var(--cobalt)]" onClick={() => setSelected(group.id)}>{group.nationalCode}</button></td>
            <td><strong>{group.members[0].description}</strong><p className="mt-1 text-[var(--text-secondary)]">{specifications(group.members) || 'No technical attributes supplied'}</p></td>
            <td><OrganisationBadges codes={group.members.map(member => member.cpse)} /><p className="mt-1 font-mono">{group.members.map(member => member.code).join(' · ')}</p></td>
            <td><StatusChip tone="verified">Verified</StatusChip></td>
            <td className="p-3">{new Date(issuedAt).toLocaleDateString()}</td>
          </tr>)}</tbody>
        </table></div>
        <div className="flex flex-wrap items-center justify-between gap-2 p-3 text-xs">
          <span>{offset + 1}–{Math.min(offset + 20, filtered.length)} of {filtered.length} verified identities</span>
          <div className="flex gap-2"><button className="btn btn-secondary btn-sm" disabled={!offset} onClick={() => setPage(offset / 20 - 1)}>Previous</button>
            <button className="btn btn-secondary btn-sm" disabled={offset + 20 >= filtered.length} onClick={() => setPage(offset / 20 + 1)}>Next</button></div>
        </div>
      </div>}
    {active && <section className="panel ops-panel">
      <h3 className="ops-panel-heading"><ShieldCheck size={15} /> Identity record <span className="ops-panel-count">{active.group.nationalCode}</span></h3>
      <div className="space-y-3 p-3">
      <div className="flex flex-wrap justify-between gap-2">
        <div><h4 className="text-sm font-semibold">{active.group.members[0].description}</h4><p className="mt-1 text-xs text-[var(--text-secondary)]">{specifications(active.group.members)}</p></div>
        {mode === 'table' && <button className="btn btn-secondary btn-sm" onClick={() => setSelected('')}>Close details</button>}
      </div>
      {mode === 'network' && <div className="flex flex-wrap gap-2 rounded bg-[var(--muted-light)] p-3 text-sm"><strong>{active.group.nationalCode}</strong>
        {active.group.members.map(member => <span key={member.id}>↔ {member.cpse} · {member.code}</span>)}</div>}
      <div className="grid gap-2 md:grid-cols-2">{active.group.members.map(member => {
        const source = sources.get(member.id);
        return source && <SourceDetails key={member.id} source={source} batch={batches.get(source.batchId)} />;
      })}</div>
      <section className="grid gap-2 border-t border-[var(--border)] pt-3">
        <h4 className="label-tech">Decision provenance</h4>
        {provenance.length === 0 && <p className="text-xs">No linked reviewer decisions are available for this identity.</p>}
        {provenance.map(decision => <div key={decision.id} className="rounded border border-[var(--border)] p-2.5 text-xs">
          <strong>{decision.reviewerName}</strong> · Case {decision.proposalId} · {new Date(decision.timestamp).toLocaleString()}
          <p className="mt-1">{decision.rationale}</p>
        </div>)}
      </section>
      </div>
    </section>}
  </div>;
}
