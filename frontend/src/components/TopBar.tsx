import { Play, RefreshCw, Building2, User } from 'lucide-react';
import type { Account } from '../types/workspace';

interface Props {
  activeTab: string;
  isHarmonizing: boolean;
  onRunHarmonization: () => void;
  activeOrgFilter: string;
  onSelectOrgFilter: (org: string) => void;
  organizations: Array<{ code: string; name: string }>;
  user: Account;
  onLogout: () => Promise<void>;
}

const titles: Record<string, string> = {
  dashboard: 'Dashboard',
  organizations: 'Organisations',
  catalogues: 'Material Catalogues',
  harmonization: 'Harmonization',
  'review-queue': 'Review Queue',
  registry: 'Unified Registry',
};

export function TopBar({ activeTab, isHarmonizing, onRunHarmonization, activeOrgFilter,
  onSelectOrgFilter, organizations, user, onLogout }: Props) {
  return (
    <header className="sticky top-0 z-20 flex flex-wrap items-center gap-x-5 gap-y-2 border-b border-[var(--border)] bg-white px-4 py-3 sm:px-6">
      <div className="flex items-center space-x-2 text-xs font-mono">
        <span className="label-tech">MATRIQ</span>
        <span className="text-[var(--text-muted)]">/</span>
        <span className="font-semibold text-[var(--navy)]">{titles[activeTab]}</span>
      </div>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 sm:gap-4">
        <label className="inline-flex min-w-0 flex-1 items-center gap-2 px-2.5 py-1.5 rounded border border-[var(--border)] bg-[var(--muted-light)] text-xs font-mono">
          <Building2 className="w-3.5 h-3.5" aria-hidden />
          <span className="hidden text-[var(--text-muted)] lg:inline">Scope:</span>
          <select aria-label="Organisation scope" className="min-w-0 flex-1 truncate bg-transparent" value={activeOrgFilter}
            onChange={e => onSelectOrgFilter(e.target.value)}>
            <option value="ALL">All Organisations</option>
            {organizations.map(org => <option key={org.code} value={org.code}>{org.code} — {org.name}</option>)}
          </select>
        </label>
        <button className="btn btn-cobalt" onClick={onRunHarmonization} disabled={isHarmonizing}>
          {isHarmonizing ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
          {isHarmonizing ? 'Harmonizing…' : 'Harmonize Materials'}
        </button>
        <details className="relative">
          <summary aria-label={user.name} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-xs">
            <span className="rounded-full border border-[var(--border)] bg-[var(--muted-light)] p-2"><User size={15} aria-hidden /></span>
            <span className="min-w-0"><strong className="block max-w-36 truncate">{user.name}</strong><span className="hidden font-mono text-[10px] text-[var(--text-muted)] sm:block">{user.reviewer ? 'Authorized Reviewer' : 'Standard User'}</span></span>
          </summary>
          <div className="absolute right-0 mt-2 min-w-40 whitespace-nowrap rounded border border-[var(--border)] bg-white p-3 shadow-lg">
            <p className="mb-2 text-xs text-[var(--text-secondary)]">{user.reviewer ? 'Authorized Reviewer' : 'Standard User'}</p>
            <button className="btn btn-secondary btn-sm w-full" onClick={() => void onLogout()}>Sign Out</button>
          </div>
        </details>
      </div>
    </header>
  );
}
