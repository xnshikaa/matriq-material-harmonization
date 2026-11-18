import React, { useState } from 'react';
import {
  UploadCloud,
  FileSpreadsheet,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  ArrowLeft,
  Info,
  Check,
  AlertCircle,
  Eye,
  RefreshCw,
  Building2,
} from 'lucide-react';
import { Organization, FieldMapping } from '../types';
import type { Batch, CatalogueValidation } from '../types/workspace';

import { api } from '../services/api';

interface MaterialCataloguesProps {
  initiallyUploading?: boolean;
  organizations: Organization[];
  preselectedOrgCode?: string;
  onImportComplete: () => Promise<void>;
  recentBatches: Batch[];
}

export const MaterialCatalogues: React.FC<MaterialCataloguesProps> = ({
  organizations,
  initiallyUploading,
  preselectedOrgCode,
  onImportComplete,
  recentBatches,
}) => {
  // Navigation sub-tab: 'BATCHES' | 'WIZARD'
  const [activeSubTab, setActiveSubTab] = useState<'BATCHES' | 'WIZARD'>(initiallyUploading ? 'WIZARD' : 'BATCHES');

  // Step indicator (1 to 7)
  const [currentStep, setCurrentStep] = useState(1);

  // Step 1 & 2: Organization & Catalogue Details
  const [selectedOrgId, setSelectedOrgId] = useState<string>(() => {
    if (preselectedOrgCode) {
      const match = organizations.find((o) => o.code === preselectedOrgCode);
      if (match) return match.id;
    }
    return organizations[0]?.id || '';
  });
  const [catalogueName, setCatalogueName] = useState('');
  const [reportingPeriod, setReportingPeriod] = useState('');
  const [sourceReference, setSourceReference] = useState('');

  // Step 3: File Upload
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [rawCsvHeaders, setRawCsvHeaders] = useState<string[]>([]);
  const [isProcessingFile, setIsProcessingFile] = useState(false);
  const [uploadFormatWarning, setUploadFormatWarning] = useState<string | null>(null);

  // Step 4: Column Mapping
  const [columnMapping, setColumnMapping] = useState<FieldMapping>({
    itemCode: '',
    description: '',
    category: '',
    materialType: '',
    grade: '',
    dimensions: '',
    unit: '',
    specification: '',
  });

  // Step 5: Validation Results
  const [validationSummary, setValidationSummary] = useState<CatalogueValidation | null>(null);

  // Step 6 & 7: Normalized preview & submission
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [importSuccessMessage, setImportSuccessMessage] = useState<string | null>(null);

  const selectedOrg = organizations.find((o) => o.id === selectedOrgId) || organizations[0];

  const handleFileSelection = async (file: File) => {
    setUploadedFile(null);
    setUploadFormatWarning(null);
    setValidationError('');
    setIsProcessingFile(true);
    try {
      const [headers] = await api.inspectCatalogue(file);
      setRawCsvHeaders(headers.map(header => header.trim()));
      const aliases: Record<keyof FieldMapping, string[]> = {
        itemCode: ['itemcode', 'code', 'materialcode', 'partno'],
        description: ['description', 'desc', 'itemdescription', 'materialdescription'],
        category: ['materialcategory', 'category', 'itemcategory'],
        materialType: ['materialtype', 'itemtype'],
        grade: ['materialgrade', 'grade'],
        dimensions: ['dimensions', 'dimension', 'dim', 'size'],
        unit: ['unit', 'uom', 'unitofmeasure'],
        specification: ['standardspecification', 'specification', 'spec'],
      };
      setColumnMapping(Object.fromEntries(Object.entries(aliases).map(([field, names]) => [
        field, headers.find(header => names.includes(header.toLowerCase().replace(/[^a-z0-9]/g, '')))?.trim() ?? '',
      ])) as unknown as FieldMapping);
      setUploadedFile(file);
      setCurrentStep(4);
    } catch (error) {
      setUploadFormatWarning(error instanceof Error ? error.message : 'The CSV could not be read.');
    } finally {
      setIsProcessingFile(false);
    }
  };
  const [validating, setValidating] = useState(false);
  const [validationError, setValidationError] = useState('');
  const importOptions = (organization: string) => ({
    organization, name: catalogueName, period: reportingPeriod, reference: sourceReference, mapping: columnMapping,
  });

  const handleValidateMapping = async () => {
    if (!uploadedFile || !selectedOrg) return;
    setValidationError('');
    setValidating(true);
    try {
      const result = await api.validateCatalogue(uploadedFile, importOptions(selectedOrg.code));
      setValidationSummary(result);
      setCurrentStep(5);
    } catch (failure) {
      setValidationError(failure instanceof Error ? failure.message : 'Validation failed.');
    } finally {
      setValidating(false);
    }
  };

  const [importError, setImportError] = useState('');
  const [committed, setCommitted] = useState<Batch>();
  const [batchSearch, setBatchSearch] = useState('');
  const [batchPage, setBatchPage] = useState(0);
  const matchingBatches = recentBatches.filter(batch =>
    [batch.fileName, batch.organizationCode, batch.name].join(' ').toLowerCase().includes(batchSearch.toLowerCase()));
  const batchOffset = Math.min(batchPage, Math.max(0, Math.ceil(matchingBatches.length / 15) - 1)) * 15;

  const handleConfirmImport = async () => {
    if (!uploadedFile || !selectedOrg) return;
    setIsSubmitting(true);
    setImportError('');
    try {
      const batch = await api.importCatalogue(uploadedFile, importOptions(selectedOrg.code));
      setCommitted(batch);
      setImportSuccessMessage(`${batch.validRows} accepted of ${batch.recordCount} submitted rows.`);
      setCurrentStep(7);
      await onImportComplete();
    } catch (failure) {
      setImportError(failure instanceof Error ? failure.message : 'Catalogue import failed.');
    } finally {
      setIsSubmitting(false);
    }
  };
  const resetWorkflow = () => {
    setCurrentStep(1);
    setUploadedFile(null);
    setRawCsvHeaders([]);
    setValidationSummary(null);
    setValidationError('');
    setImportSuccessMessage(null);
    setActiveSubTab('BATCHES');
  };

  return (
    <div className="ops-page">
      {/* Page Header */}
      <div className="ops-page-heading">
        <div>
          <h2 className="text-xl font-mono font-bold text-[var(--navy)] tracking-tight">
            Material Catalogues & Ingestion
          </h2>
          <p className="text-xs text-[var(--text-secondary)] mt-0.5">
            Operational procurement catalogues, ingested industrial batches, and enterprise CSV import workflows.
          </p>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center border border-[var(--border)] rounded p-0.5 bg-white text-xs font-mono">
          <button
            onClick={() => setActiveSubTab('BATCHES')}
            className={`px-3 py-1.5 rounded transition-colors cursor-pointer font-medium ${
              activeSubTab === 'BATCHES'
                ? 'bg-[var(--navy)] text-white shadow-xs'
                : 'text-[var(--text-secondary)] hover:text-[var(--navy)]'
            }`}
          >
            Registered Batches ({recentBatches.length})
          </button>
          <button
            onClick={() => {
              setActiveSubTab('WIZARD');
              if (currentStep === 7) setCurrentStep(1);
            }}
            className={`px-3 py-1.5 rounded transition-colors cursor-pointer font-medium flex items-center gap-1.5 ${
              activeSubTab === 'WIZARD'
                ? 'bg-[var(--navy)] text-white shadow-xs'
                : 'text-[var(--text-secondary)] hover:text-[var(--navy)]'
            }`}
          >
            <UploadCloud className="w-3.5 h-3.5" />
            <span>7-Step Ingestion Wizard</span>
          </button>
        </div>
      </div>

      {/* VIEW A: REGISTERED BATCHES & HELD-OUT DEMONSTRATION */}
      {activeSubTab === 'BATCHES' && (
        <div className="ops-page">
          {/* Active Catalogue Batches Table */}
          <div className="panel rounded overflow-hidden">
            <div className="p-4 border-b border-[var(--border)] flex items-center justify-between">
              <div>
                <span className="label-tech text-[10px]">Operational Inventory</span>
                <h3 className="font-mono text-sm font-bold text-[var(--navy)] mt-0.5">
                  Ingested Procurement Catalogues
                </h3>
              </div>
              <button
                onClick={() => setActiveSubTab('WIZARD')}
                className="btn btn-secondary btn-sm"
              >
                <UploadCloud className="w-3.5 h-3.5" />
                <span>Upload New Catalogue</span>
              </button>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 p-3 text-xs">
              <input aria-label="Search catalogue batches" placeholder="Search catalogue batches" className="rounded border border-[var(--border)] px-2 py-1"
                value={batchSearch} onChange={e => { setBatchSearch(e.target.value); setBatchPage(0); }} />
              <div className="flex items-center gap-2">
                <span>{matchingBatches.length} batches</span>
                <button className="btn btn-secondary btn-sm" disabled={!batchOffset} onClick={() => setBatchPage(batchOffset / 15 - 1)}>Previous</button>
                <button className="btn btn-secondary btn-sm" disabled={batchOffset + 15 >= matchingBatches.length} onClick={() => setBatchPage(batchOffset / 15 + 1)}>Next</button>
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="ops-table w-full">
                <thead className="bg-[var(--muted-light)] border-b border-[var(--border)] text-[var(--text-muted)] text-[10px] uppercase">
                  <tr>
                    <th className="p-3 font-semibold">Catalogue Source File</th>
                    <th className="p-3 font-semibold">Contributing Organization</th>
                    <th className="p-3 font-semibold">Ingestion Timestamp</th>
                    <th className="p-3 font-semibold">Accepted / Submitted</th>
                    <th className="p-3 font-semibold">File Size</th>
                    <th className="p-3 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border)]">
                  {matchingBatches.slice(batchOffset, batchOffset + 15).map((batch) => (
                    <tr key={batch.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="p-3 font-bold text-[var(--navy)] flex items-center gap-2">
                        <FileSpreadsheet className="w-4 h-4 text-[var(--cobalt)] flex-shrink-0" />
                        <span>{batch.fileName}</span>
                      </td>
                      <td className="p-3">
                        <span className="font-semibold text-[var(--navy)]">{organizations.find(org => org.code === batch.organizationCode)?.name}</span>
                        <span className="text-[var(--text-muted)] ml-1.5">({batch.organizationCode})</span>
                      </td>
                      <td className="p-3 text-[var(--text-secondary)]">{new Date(batch.uploadDate).toLocaleString()}</td>
                      <td className="p-3 font-bold text-[var(--navy)]">{batch.validRows} / {batch.recordCount}</td>
                      <td className="p-3 text-[var(--text-muted)]">{(batch.bytes / 1024).toFixed(1)} KB</td>
                      <td className="p-3">
                        <span className="badge-status badge-verified">
                          <CheckCircle2 className="w-3 h-3 text-[var(--verified)]" />
                          <span>{batch.validRows === batch.recordCount ? 'Imported' : 'Imported with warnings'}</span>
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* VIEW B: 7-STEP INGESTION WIZARD */}
      {activeSubTab === 'WIZARD' && (
        <div className="ops-page">
          {/* Step Progression Bar */}
          <div className="panel p-3.5 rounded">
            <div className="flex items-center justify-between text-xs font-mono overflow-x-auto pb-1">
              {[
                { num: 1, label: 'Organisation' },
                { num: 2, label: 'Catalogue Details' },
                { num: 3, label: 'Upload' },
                { num: 4, label: 'Column Mapping' },
                { num: 5, label: 'Validation' },
                { num: 6, label: 'Preview' },
                { num: 7, label: 'Summary' },
              ].map((s) => {
                const isCurrent = currentStep === s.num;
                const isCompleted = currentStep > s.num;
                return (
                  <div key={s.num} className="flex items-center space-x-2 flex-shrink-0 px-2">
                    <div
                      className={`w-6 h-6 rounded-full flex items-center justify-center font-bold text-[11px] ${
                        isCurrent
                          ? 'bg-[var(--cobalt)] text-white'
                          : isCompleted
                          ? 'bg-[var(--verified)] text-white'
                          : 'bg-slate-200 text-[var(--text-muted)]'
                      }`}
                    >
                      {isCompleted ? <Check className="w-3.5 h-3.5" /> : s.num}
                    </div>
                    <span
                      className={`text-[11px] ${
                        isCurrent ? 'font-bold text-[var(--navy)]' : 'text-[var(--text-secondary)]'
                      }`}
                    >
                      {s.label}
                    </span>
                    {s.num < 7 && <span className="text-slate-300 mx-2">›</span>}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Workflow Step Panels */}
          <div className="panel p-6 rounded space-y-6">
            {/* STEP 1: Select Organization */}
            {currentStep === 1 && (
              <div className="space-y-4 max-w-2xl">
                <div>
                  <span className="label-tech text-[10px]">Step 1 of 7</span>
                  <h3 className="text-base font-mono font-bold text-[var(--navy)] mt-1">
                    Select Contributing Organization
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                    Specify the enterprise or public sector entity providing this material catalogue export.
                  </p>
                </div>

                <div className="space-y-2 pt-2">
                  {organizations.map((org) => (
                    <label
                      key={org.id}
                      className={`flex items-center justify-between p-3.5 rounded border cursor-pointer transition-colors ${
                        selectedOrgId === org.id
                          ? 'border-[var(--cobalt)] bg-blue-50/40 text-[var(--navy)] font-semibold'
                          : 'border-[var(--border)] hover:bg-slate-50 text-[var(--text-secondary)]'
                      }`}
                    >
                      <div className="flex items-center space-x-3">
                        <input
                          type="radio"
                          name="selectedOrg"
                          value={org.id}
                          checked={selectedOrgId === org.id}
                          onChange={() => setSelectedOrgId(org.id)}
                          className="accent-[var(--cobalt)]"
                        />
                        <div>
                          <div className="font-mono text-xs text-[var(--navy)] font-bold">
                            {org.name} ({org.code})
                          </div>
                          <div className="text-[11px] text-[var(--text-muted)]">{org.type}</div>
                        </div>
                      </div>
                      <span className="text-[11px] font-mono text-[var(--text-muted)]">
                        {org.recordCount} records on file
                      </span>
                    </label>
                  ))}
                </div>

                <div className="pt-4 flex justify-end">
                  <button onClick={() => setCurrentStep(2)} disabled={!selectedOrg} className="btn btn-cobalt btn-sm shadow-xs">
                    <span>Continue to Metadata</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* STEP 2: Catalogue Metadata */}
            {currentStep === 2 && (
              <div className="space-y-4 max-w-2xl">
                <div>
                  <span className="label-tech text-[10px]">Step 2 of 7</span>
                  <h3 className="text-base font-mono font-bold text-[var(--navy)] mt-1">
                    Catalogue Metadata
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                    Provide traceable procurement metadata and audit provenance for this import batch.
                  </p>
                </div>

                <div className="space-y-3 pt-2 text-xs font-mono">
                  <div className="space-y-1">
                    <label htmlFor="catalogue-title" className="text-[var(--text-secondary)] font-semibold">Catalogue Name / Batch Title</label>
                    <input
                      type="text"
                      id="catalogue-title" maxLength={150} value={catalogueName}
                      onChange={(e) => setCatalogueName(e.target.value)}
                      className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label htmlFor="catalogue-period" className="text-[var(--text-secondary)] font-semibold">Reporting Period / Cycle</label>
                      <input
                        type="text"
                        id="catalogue-period" maxLength={100} value={reportingPeriod}
                        onChange={(e) => setReportingPeriod(e.target.value)}
                        className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                      />
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="catalogue-reference" className="text-[var(--text-secondary)] font-semibold">Source Reference / ERP Job ID</label>
                      <input
                        type="text"
                        id="catalogue-reference" maxLength={500} value={sourceReference}
                        onChange={(e) => setSourceReference(e.target.value)}
                        className="w-full p-2 rounded border border-[var(--border)] focus:outline-hidden focus:border-[var(--cobalt)]"
                      />
                    </div>
                  </div>
                </div>

                <div className="pt-4 flex items-center justify-between">
                  <button onClick={() => setCurrentStep(1)} className="btn btn-secondary btn-sm">
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back</span>
                  </button>
                  <button onClick={() => setCurrentStep(3)} className="btn btn-cobalt btn-sm shadow-xs">
                    <span>Continue to File Upload</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* STEP 3: File Upload */}
            {currentStep === 3 && (
              <div className="space-y-4 max-w-2xl">
                <div>
                  <span className="label-tech text-[10px]">Step 3 of 7</span>
                  <h3 className="text-base font-mono font-bold text-[var(--navy)] mt-1">
                    Upload Catalogue File
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                    Select a UTF-8 encoded CSV file containing item codes, descriptions, and engineering specifications.
                  </p>
                </div>

                {uploadFormatWarning && (
                  <div className="p-3.5 rounded border border-amber-200 bg-amber-50 text-amber-900 text-xs flex items-start gap-2.5">
                    <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
                    <span>{uploadFormatWarning}</span>
                  </div>
                )}

                <div className="border-2 border-dashed border-[var(--border)] rounded-lg p-8 text-center space-y-3 hover:border-[var(--cobalt)] transition-colors bg-slate-50/50">
                  <div className="w-10 h-10 rounded-full bg-blue-50 text-[var(--cobalt)] flex items-center justify-center mx-auto">
                    <UploadCloud className="w-5 h-5" />
                  </div>
                  <div>
                    <label className="cursor-pointer font-mono text-xs font-bold text-[var(--cobalt)] hover:underline">
                      <span>Click to select CSV file</span>
                      <input
                        type="file"
                        disabled={isProcessingFile}
                        accept=".csv,.txt"
                        className="sr-only"
                        onChange={(e) => {
                          const f = e.target.files?.[0];
                          if (f) handleFileSelection(f);
                        }}
                      />
                    </label>
                    <p className="text-[11px] text-[var(--text-muted)] mt-1">
                      Accepts UTF-8 CSV files up to 20 MB.
                    </p>
                  </div>
                </div>

                <div className="pt-4 flex items-center justify-between">
                  <button onClick={() => setCurrentStep(2)} className="btn btn-secondary btn-sm">
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back</span>
                  </button>
                </div>
              </div>
            )}

            {/* STEP 4: Column Mapping */}
            {currentStep === 4 && validationError && <p role="alert" className="ops-alert">{validationError}</p>}
            {currentStep === 4 && (
              <div className="space-y-4 max-w-2xl">
                <div>
                  <span className="label-tech text-[10px]">Step 4 of 7</span>
                  <h3 className="text-base font-mono font-bold text-[var(--navy)] mt-1">
                    Map Catalogue Columns
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                    Align your CSV column headers with MATRIQ standard material attributes.
                  </p>
                </div>

                <div className="space-y-2 pt-2 text-xs font-mono">
                  {[
                    { key: 'itemCode', label: 'Item Code / Material Number', required: true },
                    { key: 'description', label: 'Raw Description', required: true },
                    { key: 'category', label: 'Material Category', required: false },
                    { key: 'grade', label: 'Material Grade / Metallurgy', required: false },
                    { key: 'dimensions', label: 'Dimensions / Nominal Size', required: false },
                    { key: 'specification', label: 'Technical Standard / Spec', required: false },
                    { key: 'unit', label: 'Unit of Measure (UOM)', required: false },
                  ].map((field) => (
                    <div key={field.key} className="flex items-center justify-between p-2.5 rounded border border-[var(--border)] bg-white">
                      <div>
                        <span className="font-semibold text-[var(--navy)]">{field.label}</span>
                        {field.required && <span className="text-[var(--conflict)] ml-1">*</span>}
                      </div>
                      <select
                        value={(columnMapping as any)[field.key] || ''}
                        onChange={(e) =>
                          setColumnMapping({ ...columnMapping, [field.key]: e.target.value })
                        }
                        className="p-1.5 rounded border border-[var(--border)] bg-slate-50 font-mono text-xs w-48 focus:outline-hidden focus:border-[var(--cobalt)]"
                      >
                        <option value="">— Select Column —</option>
                        {rawCsvHeaders.map((h) => (
                          <option key={h} value={h}>
                            {h}
                          </option>
                        ))}
                      </select>
                    </div>
                  ))}
                </div>

                <div className="pt-4 flex items-center justify-between">
                  <button onClick={() => setCurrentStep(3)} className="btn btn-secondary btn-sm">
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back to Upload</span>
                  </button>
                  <button
                    onClick={handleValidateMapping}
                    disabled={validating || !columnMapping.itemCode || !columnMapping.description}
                    className="btn btn-cobalt btn-sm shadow-xs disabled:opacity-50"
                  >
                    <span>{validating ? 'Validating…' : 'Validate Ingestion Records'}</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* STEP 5: Validation Summary */}
            {currentStep === 5 && validationSummary && (
              <div className="space-y-4 max-w-2xl">
                <div>
                  <span className="label-tech text-[10px]">Step 5 of 7</span>
                  <h3 className="text-base font-mono font-bold text-[var(--navy)] mt-1">
                    Validation Results
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                    Automated structural, duplicate, and attribute integrity verification.
                  </p>
                </div>

                <div className="grid grid-cols-3 gap-3 font-mono text-xs">
                  <div className="p-3 bg-slate-50 rounded border border-[var(--border)]">
                    <span className="text-[10px] text-[var(--text-muted)] block">Total Rows</span>
                    <span className="text-lg font-bold text-[var(--navy)]">{validationSummary.totalRows}</span>
                  </div>
                  <div className="p-3 bg-emerald-50 rounded border border-emerald-200">
                    <span className="text-[10px] text-emerald-800 block">Valid Rows</span>
                    <span className="text-lg font-bold text-[var(--verified)]">{validationSummary.validRows}</span>
                  </div>
                  <div className="p-3 bg-red-50 rounded border border-red-200">
                    <span className="text-[10px] text-red-800 block">Invalid / Skipped</span>
                    <span className="text-lg font-bold text-[var(--conflict)]">{validationSummary.totalRows - validationSummary.validRows}</span>
                  </div>
                </div>

                {validationSummary.warnings.length > 0 && (
                  <div className="p-3 rounded border border-amber-200 bg-amber-50 text-xs font-mono space-y-1">
                    <div className="font-semibold text-amber-900 flex items-center gap-1.5">
                      <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
                      <span>Validation Warnings ({validationSummary.warnings.length}):</span>
                    </div>
                    {validationSummary.warnings.map((w, i) => (
                      <div key={i} className="text-amber-800 text-[11px] pl-5">
                        • {w}
                      </div>
                    ))}
                  </div>
                )}

                <div className="pt-4 flex items-center justify-between">
                  <button onClick={() => setCurrentStep(4)} className="btn btn-secondary btn-sm">
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back to Mapping</span>
                  </button>
                  <button
                    onClick={() => setCurrentStep(6)}
                    disabled={validationSummary.validRows === 0}
                    className="btn btn-cobalt btn-sm shadow-xs disabled:opacity-50"
                  >
                    <span>Preview Import Records</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* STEP 6: Normalized Preview */}
            {currentStep === 6 && (
              <div className="space-y-4">
                <div>
                  <span className="label-tech text-[10px]">Step 6 of 7</span>
                  <h3 className="text-base font-mono font-bold text-[var(--navy)] mt-1">
                    Preview Material Records
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)] mt-0.5">
                    Review the mapped source values. The preview shows the first {validationSummary?.preview.length ?? 0} accepted records of {validationSummary?.validRows ?? 0}.
                  </p>
                </div>

                <div className="border border-[var(--border)] rounded overflow-hidden">
                  <div className="overflow-x-auto max-h-80">
                    <table className="ops-table w-full">
                      <thead className="bg-[var(--muted-light)] border-b border-[var(--border)] text-[var(--text-muted)] text-[10px] uppercase sticky top-0">
                        <tr>
                          <th className="p-2.5 font-semibold">Original Code</th>
                          <th className="p-2.5 font-semibold">Raw Description</th>
                          <th className="p-2.5 font-semibold">Technical specification</th>
                          <th className="p-2.5 font-semibold">Size / Dimension</th>
                          <th className="p-2.5 font-semibold">Grade</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--border)]">
                        {validationSummary?.preview.map((rec) => (
                          <tr key={rec.row} className="hover:bg-slate-50">
                            <td className="p-2.5 font-bold text-[var(--navy)] whitespace-nowrap">{rec.material.code}</td>
                            <td className="p-2.5 max-w-xs truncate text-[var(--text-muted)] font-sans">{rec.material.description}</td>
                            <td className="p-2.5 max-w-xs truncate font-semibold text-[var(--navy)]">{rec.specification || '—'}</td>
                            <td className="p-2.5 whitespace-nowrap font-bold text-[var(--cobalt)]">{rec.dimensions || '—'}</td>
                            <td className="p-2.5 whitespace-nowrap">{rec.grade || '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className="pt-4 flex flex-wrap items-center justify-between gap-2">
                  {importError && <p role="alert" className="w-full text-xs text-[var(--conflict)]">{importError}</p>}
                  <button onClick={() => setCurrentStep(5)} className="btn btn-secondary btn-sm">
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back to Validation</span>
                  </button>
                  <button
                    onClick={handleConfirmImport}
                    disabled={isSubmitting}
                    className="btn btn-cobalt btn-sm shadow-xs"
                  >
                    {isSubmitting ? (
                      <>
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        <span>Importing Catalogue...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Confirm Catalogue Import</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* STEP 7: Import Summary */}
            {currentStep === 7 && committed && (
              <div className="space-y-4 max-w-xl text-center py-6 mx-auto">
                <div className="w-12 h-12 rounded-full bg-[var(--verified-bg)] border border-[var(--verified-border)] flex items-center justify-center text-[var(--verified)] mx-auto">
                  <CheckCircle2 className="w-6 h-6" />
                </div>

                <div className="space-y-1">
                  <h3 className="text-lg font-mono font-bold text-[var(--navy)]">
                    Catalogue Ingestion Successful
                  </h3>
                  <p className="text-xs text-[var(--text-secondary)]">
                    {importSuccessMessage}
                  </p>
                  {committed.warnings.length > 0 && <ul className="text-left text-xs">{committed.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}
                </div>

                <div className="p-4 bg-slate-50 rounded border border-[var(--border)] text-left font-mono text-xs space-y-1.5 my-4">
                  <div className="flex justify-between">
                    <span className="text-[var(--text-muted)]">Organization:</span>
                    <span className="font-bold text-[var(--navy)]">
                      {selectedOrg.name} ({selectedOrg.code})
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[var(--text-muted)]">Catalogue:</span>
                    <span className="font-medium text-[var(--navy)]">{catalogueName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[var(--text-muted)]">Reporting Period:</span>
                    <span className="text-[var(--navy)]">{reportingPeriod}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[var(--text-muted)]">Committed Records:</span>
                    <span className="font-bold text-[var(--verified)]">{committed.validRows} items</span>
                  </div>
                </div>

                <div className="flex items-center justify-center gap-3 pt-2">
                  <button onClick={resetWorkflow} className="btn btn-secondary btn-sm">
                    <span>Return to Batches & Ingestion</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
