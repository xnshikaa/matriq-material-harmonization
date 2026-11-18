import type { Batch } from '../types/workspace';
import { EmptyState, PageHeading, StatusChip } from '../components/OperationsUI';
import React, { useState } from 'react';
import { Building2, Plus, Search, Filter, CheckCircle2, Clock, MapPin, Mail, X } from 'lucide-react';
import { Organization } from '../types';

interface OrganizationsProps {
  organizations: Organization[];
  batches: Batch[];
  onAddOrganization: (newOrg: Organization) => Promise<void>;
  onNavigateToCatalogues: (orgCode: string) => void;
}

export const Organizations: React.FC<OrganizationsProps> = ({
  organizations,
  batches,
  onAddOrganization,
  onNavigateToCatalogues,
}) => {
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [filterType, setFilterType] = useState('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // New Organization Form State
  const [formName, setFormName] = useState('');
  const [formCode, setFormCode] = useState('');
  const [formType, setFormType] = useState('Refinery & Petrochemicals');
  const [formEmail, setFormEmail] = useState('');
  const [formLocation, setFormLocation] = useState('');

  const types = Array.from(new Set(organizations.map((o) => o.type)));

  const filteredOrgs = organizations.filter((org) => {
    const matchesSearch =
      org.name.toLowerCase().includes(search.toLowerCase()) ||
      org.code.toLowerCase().includes(search.toLowerCase()) ||
      (org.location && org.location.toLowerCase().includes(search.toLowerCase()));
    const matchesType = filterType === 'ALL' || org.type === filterType;
    return matchesSearch && matchesType;
  });

  const handleSubmitNewOrg = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim() || !formCode.trim()) return;

    const newOrg: Organization = {
      id: `org-${Date.now()}`,
      name: formName.trim(),
      code: formCode.trim().toUpperCase(),
      type: formType,
      recordCount: 0,
      lastUploadDate: '—',
      status: 'ACTIVE',
      contactEmail: formEmail.trim() || undefined,
      location: formLocation.trim() || undefined,
    };

    setError('');
    setSaving(true);
    try {
      await onAddOrganization(newOrg);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Organisation could not be registered.');
      return;
    } finally {
      setSaving(false);
    }
    setIsAddModalOpen(false);
    setFormName('');
    setFormCode('');
    setFormEmail('');
    setFormLocation('');
  };

  return (
    <div className="ops-page">
      <PageHeading title="Participating Organisations" description="Catalogue coverage, accepted material records and import activity by organisation."
        actions={<button onClick={() => setIsAddModalOpen(true)} className="btn btn-cobalt btn-sm"><Plus size={14} /> Register Organisation</button>} />

      {/* Search & Filter Bar */}
      <div className="panel p-4 rounded flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)]" />
          <input
            type="text"
            aria-label="Search organisations" placeholder="Search by name, identifier, or location..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 rounded border border-[var(--border)] bg-white text-xs font-mono placeholder:text-[var(--text-muted)] focus:outline-hidden focus:border-[var(--cobalt)]"
          />
        </div>

        <div className="flex items-center space-x-2">
          <Filter className="w-3.5 h-3.5 text-[var(--text-muted)]" />
          <span className="text-xs font-mono text-[var(--text-muted)]">Type:</span>
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            className="px-2.5 py-1 rounded border border-[var(--border)] bg-white text-xs font-mono focus:outline-hidden cursor-pointer"
          >
            <option value="ALL">All Types</option>
            {types.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Organizations Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {!filteredOrgs.length && <div className="panel rounded"><EmptyState title="No organisations match this view" /></div>}
        {filteredOrgs.map((org) => (
          <div
            key={org.id}
            className="panel border-t-2 border-t-[var(--navy)] p-4 rounded space-y-3 hover:shadow-md transition-shadow flex flex-col justify-between"
          >
            <div className="space-y-2">
              <div className="flex items-start justify-between gap-2">
                <div className="space-y-0.5">
                  <span className="px-2 py-0.5 rounded bg-[var(--navy)] text-white text-[11px] font-mono font-bold tracking-wider inline-block">
                    {org.code}
                  </span>
                  <h3 className="font-mono text-sm font-bold text-[var(--navy)] mt-1">
                    {org.name}
                  </h3>
                </div>
                <StatusChip tone={org.status === 'ACTIVE' ? 'verified' : 'neutral'}>{org.status === 'ACTIVE' ? 'Active' : 'Inactive'}</StatusChip>
              </div>

              <div className="text-xs text-[var(--text-secondary)] font-medium">
                {org.type}
              </div>

              {org.location && (
                <div className="flex items-center gap-1.5 text-xs text-[var(--text-muted)] font-mono">
                  <MapPin className="w-3.5 h-3.5 text-[var(--text-muted)] flex-shrink-0" />
                  <span className="truncate">{org.location}</span>
                </div>
              )}

              {org.contactEmail && (
                <div className="flex items-center gap-1.5 text-xs text-[var(--text-muted)] font-mono">
                  <Mail className="w-3.5 h-3.5 text-[var(--text-muted)] flex-shrink-0" />
                  <span className="truncate">{org.contactEmail}</span>
                </div>
              )}
            </div>

            <div className="pt-3 border-t border-[var(--border)] space-y-2">
              <div className="flex items-center justify-between text-xs font-mono">
                <span className="text-[var(--text-muted)]">Accepted records</span>
                <span className="font-bold text-[var(--navy)] tabular-nums">{org.recordCount}</span>
              </div>
              <div className="flex items-center justify-between text-xs font-mono">
                <span className="text-[var(--text-muted)]">Last import</span>
                <span className="text-[var(--text-secondary)]">{org.lastUploadDate ? new Date(org.lastUploadDate).toLocaleString() : 'Awaiting import'}</span>
              </div>

              <div className="flex items-center justify-between gap-2 text-xs">
                <span className="text-[var(--text-secondary)]">Catalogue batches</span>
                <strong className="font-mono">{batches.filter(batch => batch.organizationCode === org.code).length}</strong>
              </div>
              <button
                onClick={() => onNavigateToCatalogues(org.code)}
                className="w-full mt-2 btn btn-cobalt btn-sm"
              >
                <span>Upload Catalogue</span>
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Register Organization Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="relative w-full max-w-lg bg-white border border-[var(--border)] shadow-xl rounded p-6 space-y-4 animate-in fade-in duration-150">
            <div className="flex items-center justify-between border-b border-[var(--border)] pb-3">
              <div>
                <span className="label-tech text-[10px]">Platform Administration</span>
                <h3 className="font-mono text-base font-bold text-[var(--navy)]">
                  Register New Organization
                </h3>
              </div>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="p-1 rounded hover:bg-black/5 text-[var(--text-muted)] cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmitNewOrg} className="space-y-4 text-xs font-mono">
              {error && <p role="alert" className="text-[var(--conflict)]">{error}</p>}
              <div className="space-y-1">
                <label className="text-[var(--text-secondary)] font-semibold">
                  Organization Full Legal Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Bharat Petroleum Corporation Limited"
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-[var(--text-secondary)] font-semibold">
                    Identifier Code (Short) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. BPCL"
                    value={formCode}
                    onChange={(e) => setFormCode(e.target.value)}
                    className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)] uppercase"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-[var(--text-secondary)] font-semibold">
                    Industry Domain / Type
                  </label>
                  <select
                    value={formType}
                    onChange={(e) => setFormType(e.target.value)}
                    className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                  >
                    <option value="Refinery & Petrochemicals">Refinery & Petrochemicals</option>
                    <option value="Refinery & Marketing">Refinery & Marketing</option>
                    <option value="Upstream Exploration & Production">Upstream Exploration & Production</option>
                    <option value="Natural Gas Transmission & Petrochemicals">Natural Gas Transmission</option>
                    <option value="Heavy Industrial Manufacturing">Heavy Industrial</option>
                    <option value="Energy & Utilities">Energy & Utilities</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-[var(--text-secondary)] font-semibold">
                  Headquarters Location
                </label>
                <input
                  type="text"
                  placeholder="e.g. Mumbai, Maharashtra"
                  value={formLocation}
                  onChange={(e) => setFormLocation(e.target.value)}
                  className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                />
              </div>

              <div className="space-y-1">
                <label className="text-[var(--text-secondary)] font-semibold">
                  Official Procurement Contact Email
                </label>
                <input
                  type="email"
                  placeholder="e.g. materials@bharatpetroleum.in"
                  value={formEmail}
                  onChange={(e) => setFormEmail(e.target.value)}
                  className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-[var(--border)]">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="btn btn-secondary btn-sm"
                >
                  Cancel
                </button>
                <button type="submit" disabled={saving} className="btn btn-cobalt btn-sm shadow-xs">
                  Register Organization
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
