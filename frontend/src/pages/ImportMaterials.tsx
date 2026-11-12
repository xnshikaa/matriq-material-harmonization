import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { MaterialRecord, ImportBatch } from '../types';
import { Upload, FileText, Download, CheckCircle, AlertCircle, RefreshCw, Eye } from 'lucide-react';

interface ImportMaterialsProps {
  onRefresh: () => void;
}

export const ImportMaterials: React.FC<ImportMaterialsProps> = ({ onRefresh }) => {
  const [batches, setBatches] = useState<ImportBatch[]>([]);
  const [records, setRecords] = useState<MaterialRecord[]>([]);
  const [selectedCompany, setSelectedCompany] = useState('CPCL');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState<ImportBatch | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedRecord, setSelectedRecord] = useState<MaterialRecord | null>(null);

  const loadData = async () => {
    try {
      const [b, r] = await Promise.all([
        api.getImportBatches(),
        api.getMaterials(),
      ]);
      setBatches(b);
      setRecords(r);
    } catch (err: any) {
      console.error(err);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setErrorMessage(null);
    }
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setErrorMessage('Please select a CSV file to upload.');
      return;
    }
    setUploading(true);
    setErrorMessage(null);
    try {
      const batch = await api.uploadCsv(file, selectedCompany);
      setUploadResult(batch);
      setFile(null);
      await loadData();
      onRefresh();
    } catch (err: any) {
      setErrorMessage(err.message || 'File upload failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
        <div>
          <h2 className="text-2xl font-black text-[#172C48] font-mono tracking-tight">
            Import Materials & Catalog Ingestion
          </h2>
          <p className="text-xs text-[#566579] mt-1">
            Ingest company-specific material catalogs in CSV format. Schema mapping, normalization, and duplicate checks are automatically applied.
          </p>
        </div>

        {/* Quick Sample Downloads */}
        <div className="flex items-center space-x-2 text-xs">
          <span className="text-[#849DBE] font-medium hidden sm:inline">Sample Datasets:</span>
          <a
            href="/data/sample/cpcl_sample.csv"
            download
            className="inline-flex items-center space-x-1 px-2.5 py-1.5 bg-white border border-[#DFE3E9] text-[#172C48] rounded hover:bg-[#F2F0E9] font-mono"
          >
            <Download className="w-3 h-3 text-[#315BEA]" />
            <span>CPCL.csv</span>
          </a>
          <a
            href="/data/sample/iocl_sample.csv"
            download
            className="inline-flex items-center space-x-1 px-2.5 py-1.5 bg-white border border-[#DFE3E9] text-[#172C48] rounded hover:bg-[#F2F0E9] font-mono"
          >
            <Download className="w-3 h-3 text-[#315BEA]" />
            <span>IOCL.csv</span>
          </a>
          <a
            href="/data/sample/ongc_sample.csv"
            download
            className="inline-flex items-center space-x-1 px-2.5 py-1.5 bg-white border border-[#DFE3E9] text-[#172C48] rounded hover:bg-[#F2F0E9] font-mono"
          >
            <Download className="w-3 h-3 text-[#315BEA]" />
            <span>ONGC.csv</span>
          </a>
        </div>
      </div>

      {/* Upload Box & Guidelines */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm">
          <h3 className="text-sm font-bold text-[#172C48] font-mono uppercase tracking-wider mb-4">
            Upload Organization Catalog
          </h3>

          <form onSubmit={handleUpload} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-[#172C48] mb-1">
                  Issuing Organization
                </label>
                <select
                  value={selectedCompany}
                  onChange={(e) => setSelectedCompany(e.target.value)}
                  className="w-full text-xs border border-[#CBD2DC] rounded p-2 bg-[#F9F9FB] focus:border-[#315BEA] outline-none"
                >
                  <option value="CPCL">Chennai Petroleum Corporation Ltd (CPCL)</option>
                  <option value="IOCL">Indian Oil Corporation Ltd (IOCL)</option>
                  <option value="ONGC">Oil & Natural Gas Corporation (ONGC)</option>
                  <option value="BPCL">Bharat Petroleum Corporation Ltd (BPCL)</option>
                  <option value="HPCL">Hindustan Petroleum Corporation Ltd (HPCL)</option>
                  <option value="SAIL">Steel Authority of India Ltd (SAIL)</option>
                  <option value="NTPC">National Thermal Power Corporation (NTPC)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#172C48] mb-1">
                  Select CSV File
                </label>
                <input
                  type="file"
                  accept=".csv"
                  onChange={handleFileChange}
                  className="w-full text-xs border border-[#CBD2DC] rounded p-1.5 bg-[#F9F9FB] file:mr-3 file:py-1 file:px-2 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-[#315BEA] file:text-white cursor-pointer"
                />
              </div>
            </div>

            {errorMessage && (
              <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded flex items-center space-x-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMessage}</span>
              </div>
            )}

            {uploadResult && (
              <div className="p-3 bg-green-50 border border-green-200 text-green-800 text-xs rounded flex items-center space-x-2">
                <CheckCircle className="w-4 h-4 shrink-0 text-green-600" />
                <div>
                  <strong>Batch {uploadResult.id} Ingested:</strong> {uploadResult.importedCount} records imported successfully
                  {uploadResult.duplicateCount > 0 && ` (${uploadResult.duplicateCount} duplicate codes skipped)`}
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={uploading || !file}
              className="inline-flex items-center space-x-2 px-5 py-2.5 bg-[#315BEA] hover:bg-[#2548C2] text-white text-xs font-semibold rounded-sm transition cursor-pointer disabled:opacity-50"
            >
              <Upload className="w-4 h-4" />
              <span>{uploading ? 'Validating & Uploading...' : 'Upload & Process Catalog'}</span>
            </button>
          </form>
        </div>

        {/* Schema Format Guidelines */}
        <div className="bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm p-6 text-xs text-[#566579]">
          <h4 className="font-bold font-mono text-[#172C48] uppercase tracking-wider mb-2">
            Catalog Schema Standard
          </h4>
          <p className="mb-3">
            CSV files should include the following standard columns. Missing optional fields are automatically handled by Trie and KMP heuristics:
          </p>
          <ul className="list-disc pl-4 space-y-1 font-mono text-[11px] text-[#172C48]">
            <li><code>originalMaterialCode</code> (Required)</li>
            <li><code>originalDescription</code> (Required)</li>
            <li><code>materialCategory</code> (e.g. Piping, Valves)</li>
            <li><code>materialType</code> (e.g. Seamless, Welded)</li>
            <li><code>dimensions</code> (e.g. 6 inch, 150 mm)</li>
            <li><code>unit</code> (INCH, MM, CM, M, KG)</li>
            <li><code>grade</code> (e.g. Grade B, SS316, A105)</li>
            <li><code>technicalSpecification</code> (ASTM A106, API 5L)</li>
          </ul>
        </div>
      </div>

      {/* Normalization Before-and-After Inspection Modal / Drawer */}
      {selectedRecord && (
        <div className="bg-white border-2 border-[#315BEA] rounded-sm p-6 shadow-md relative">
          <div className="flex items-center justify-between border-b border-[#DFE3E9] pb-3 mb-4">
            <div className="flex items-center space-x-2">
              <Eye className="w-4 h-4 text-[#315BEA]" />
              <h3 className="font-mono font-bold text-sm text-[#172C48]">
                Normalization Audit: {selectedRecord.originalMaterialCode} ({selectedRecord.companyName})
              </h3>
            </div>
            <button
              onClick={() => setSelectedRecord(null)}
              className="text-xs font-semibold text-[#849DBE] hover:text-[#172C48]"
            >
              Close
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
              <div className="font-mono font-bold text-[#849DBE] text-[10px] uppercase mb-2">Original Raw Input</div>
              <div className="space-y-2">
                <div><span className="text-[#849DBE]">Code:</span> <strong className="font-mono text-[#E77848]">{selectedRecord.originalMaterialCode}</strong></div>
                <div><span className="text-[#849DBE]">Description:</span> <p className="font-medium text-[#172C48] mt-0.5">{selectedRecord.originalDescription}</p></div>
                <div><span className="text-[#849DBE]">Raw Dimensions:</span> {selectedRecord.dimensions || '—'}</div>
                <div><span className="text-[#849DBE]">Raw Unit:</span> {selectedRecord.unit || '—'}</div>
              </div>
            </div>

            <div className="p-4 bg-[#EBF0FC] border border-[#BAC9DD] rounded-sm">
              <div className="font-mono font-bold text-[#315BEA] text-[10px] uppercase mb-2">Normalized Representation</div>
              <div className="space-y-2">
                <div><span className="text-[#6A84A6]">Normalized Text:</span> <p className="font-medium text-[#172C48] mt-0.5">{selectedRecord.normalizedDescription || selectedRecord.originalDescription}</p></div>
                <div className="grid grid-cols-2 gap-2 mt-2">
                  <div><span className="text-[#6A84A6]">Category:</span> <strong className="block text-[#172C48] font-mono">{selectedRecord.materialCategory || '—'}</strong></div>
                  <div><span className="text-[#6A84A6]">Type:</span> <strong className="block text-[#172C48] font-mono">{selectedRecord.materialType || '—'}</strong></div>
                  <div><span className="text-[#6A84A6]">Spec Standard:</span> <strong className="block text-[#315BEA] font-mono">{selectedRecord.technicalSpecification || '—'}</strong></div>
                  <div><span className="text-[#6A84A6]">Grade:</span> <strong className="block text-[#172C48] font-mono">{selectedRecord.grade || '—'}</strong></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Ingested Records Master Table */}
      <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-[#172C48] font-mono uppercase tracking-wider">
            Ingested Records ({records.length})
          </h3>
          <span className="text-xs text-[#849DBE]">Click "Inspect" to view normalization breakdown</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-[#DFE3E9] bg-[#F9F9FB] font-mono text-[#849DBE]">
                <th className="py-2.5 px-3">Company</th>
                <th className="py-2.5 px-3">Original Code</th>
                <th className="py-2.5 px-3">Raw Description</th>
                <th className="py-2.5 px-3">Category</th>
                <th className="py-2.5 px-3">Dimension</th>
                <th className="py-2.5 px-3">Spec / Grade</th>
                <th className="py-2.5 px-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#DFE3E9]">
              {records.map((r) => (
                <tr key={r.id} className="hover:bg-[#F9F9FB] transition">
                  <td className="py-2.5 px-3 font-semibold text-[#172C48]">{r.companyName}</td>
                  <td className="py-2.5 px-3 font-mono text-[#E77848] font-medium">{r.originalMaterialCode}</td>
                  <td className="py-2.5 px-3 text-[#566579] max-w-sm truncate">{r.originalDescription}</td>
                  <td className="py-2.5 px-3 font-mono text-[#172C48]">{r.materialCategory || '—'}</td>
                  <td className="py-2.5 px-3 font-mono text-[#172C48]">{r.dimensions || '—'} {r.unit || ''}</td>
                  <td className="py-2.5 px-3 font-mono text-[#315BEA] font-medium">{r.technicalSpecification || r.grade || '—'}</td>
                  <td className="py-2.5 px-3 text-right">
                    <button
                      onClick={() => setSelectedRecord(r)}
                      className="px-2 py-1 text-[11px] font-mono bg-white border border-[#CBD2DC] hover:bg-[#F2F0E9] text-[#172C48] rounded cursor-pointer"
                    >
                      Inspect
                    </button>
                  </td>
                </tr>
              ))}
              {records.length === 0 && (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-[#849DBE]">
                    No catalogs loaded yet. Use the upload form above or sample downloads.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
