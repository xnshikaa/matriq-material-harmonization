import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { NationalMaterialCode } from '../types';
import { Award, Download, Search, CheckCircle, ChevronDown, ChevronRight, Layers, FileSpreadsheet } from 'lucide-react';

interface NationalRegistryProps {
  onRefresh: () => void;
}

export const NationalRegistry: React.FC<NationalRegistryProps> = ({ onRefresh }) => {
  const [codes, setCodes] = useState<NationalMaterialCode[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [expandedCode, setExpandedCode] = useState<string | null>(null);
  const [issuing, setIssuing] = useState(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const data = await api.getNationalCodes();
      setCodes(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAssignCodes = async () => {
    setIssuing(true);
    try {
      const res = await api.assignNationalCodes();
      alert(`Issued ${res.assigned} new National Material Codes!`);
      await loadData();
      onRefresh();
    } catch (err: any) {
      alert(`Issuance failed: ${err.message}`);
    } finally {
      setIssuing(false);
    }
  };

  const handleExportCsv = () => {
    if (codes.length === 0) return;
    const rows = [
      ['NationalMaterialCode', 'CanonicalDescription', 'Category', 'Grade', 'Specification', 'Company', 'OriginalCode', 'OriginalDescription']
    ];

    codes.forEach((c) => {
      if (c.companyMappings && c.companyMappings.length > 0) {
        c.companyMappings.forEach((m) => {
          rows.push([
            c.code,
            `"${(c.canonicalDescription || '').replace(/"/g, '""')}"`,
            c.materialCategory || '',
            c.grade || '',
            c.technicalSpecification || '',
            m.companyName,
            m.originalMaterialCode,
            `"${(m.originalDescription || '').replace(/"/g, '""')}"`
          ]);
        });
      } else {
        rows.push([
          c.code,
          `"${(c.canonicalDescription || '').replace(/"/g, '""')}"`,
          c.materialCategory || '',
          c.grade || '',
          c.technicalSpecification || '',
          '', '', ''
        ]);
      }
    });

    const csvContent = "data:text/csv;charset=utf-8," + rows.map(e => e.join(",")).join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `MATRIQ_National_Material_Registry_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const filteredCodes = codes.filter((c) => {
    const q = searchQuery.toLowerCase();
    const matchCode = c.code.toLowerCase().includes(q);
    const matchDesc = (c.canonicalDescription || '').toLowerCase().includes(q);
    const matchCompany = c.companyMappings?.some(
      m => m.companyName.toLowerCase().includes(q) || m.originalMaterialCode.toLowerCase().includes(q)
    );
    return matchCode || matchDesc || matchCompany;
  });

  return (
    <div className="space-y-6">
      {/* Header & Actions */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
        <div>
          <h2 className="text-2xl font-black text-[#172C48] font-mono tracking-tight flex items-center space-x-2">
            <Award className="w-6 h-6 text-[#315BEA]" />
            <span>National Material Code (NMC) Registry</span>
          </h2>
          <p className="text-xs text-[#566579] mt-1">
            Official central registry of standardized national material identifiers mapped to participating PSU material codes.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={handleAssignCodes}
            disabled={issuing}
            className="inline-flex items-center space-x-1.5 px-4 py-2 bg-[#172C48] hover:bg-[#1E3657] text-white rounded text-xs font-semibold font-mono cursor-pointer disabled:opacity-50"
          >
            <Award className="w-3.5 h-3.5 text-[#D6ED65]" />
            <span>{issuing ? 'Assigning Codes...' : 'Assign NMC to Approved Groups'}</span>
          </button>

          <button
            onClick={handleExportCsv}
            disabled={codes.length === 0}
            className="inline-flex items-center space-x-1.5 px-3 py-2 bg-white border border-[#DFE3E9] hover:bg-[#F2F0E9] text-[#172C48] rounded text-xs font-semibold font-mono cursor-pointer disabled:opacity-50"
          >
            <FileSpreadsheet className="w-3.5 h-3.5 text-[#107C41]" />
            <span>Export Registry CSV</span>
          </button>
        </div>
      </div>

      {/* Search Bar */}
      <div className="bg-white border border-[#DFE3E9] rounded-sm p-4 shadow-sm flex items-center space-x-3">
        <Search className="w-4 h-4 text-[#849DBE]" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search by National Code (e.g. NMC-2026), Company Code (PIPE-001, P-9281), or Technical Specification..."
          className="w-full text-xs outline-none bg-transparent placeholder-[#849DBE] text-[#172C48] font-mono"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="text-xs text-[#849DBE] hover:text-[#172C48]"
          >
            Clear
          </button>
        )}
      </div>

      {/* Registry Table */}
      <div className="bg-white border border-[#DFE3E9] rounded-sm shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-[#DFE3E9] bg-[#F9F9FB] font-mono text-[#849DBE]">
                <th className="py-3 px-4 w-10"></th>
                <th className="py-3 px-4">National Material Code</th>
                <th className="py-3 px-4">Canonical Description</th>
                <th className="py-3 px-4">Category / Type</th>
                <th className="py-3 px-4">Spec / Grade</th>
                <th className="py-3 px-4 text-right">Linked Companies</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#DFE3E9]">
              {filteredCodes.map((item) => {
                const isExpanded = expandedCode === item.code;
                return (
                  <React.Fragment key={item.code}>
                    <tr
                      onClick={() => setExpandedCode(isExpanded ? null : item.code)}
                      className="hover:bg-[#F9F9FB] transition cursor-pointer"
                    >
                      <td className="py-3 px-4 text-center text-[#849DBE]">
                        {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      </td>
                      <td className="py-3 px-4 font-mono font-bold text-[#315BEA]">
                        {item.code}
                      </td>
                      <td className="py-3 px-4 text-[#172C48] font-medium max-w-md">
                        {item.canonicalDescription || 'Standard Industrial Material'}
                      </td>
                      <td className="py-3 px-4 font-mono text-[#566579]">
                        {item.materialCategory || '—'} / {item.materialType || '—'}
                      </td>
                      <td className="py-3 px-4 font-mono text-[#172C48] font-semibold">
                        {item.technicalSpecification || '—'} {item.grade ? `(${item.grade})` : ''}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <span className="font-mono text-xs font-bold bg-[#EBF0FC] text-[#315BEA] px-2.5 py-1 rounded">
                          {item.companyMappings?.length || 0} Organizations
                        </span>
                      </td>
                    </tr>

                    {/* Expandable Mapped Company Codes */}
                    {isExpanded && (
                      <tr className="bg-[#FAF9F5] border-b border-[#DFE3E9]">
                        <td colSpan={6} className="py-4 px-8">
                          <div className="space-y-2">
                            <div className="text-[11px] font-mono text-[#849DBE] uppercase font-bold tracking-wider">
                              Mapped Organization Part Numbers (Original Codes Preserved Permanently):
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                              {item.companyMappings?.map((mapping, mIdx) => (
                                <div
                                  key={mIdx}
                                  className="p-3 bg-white border border-[#DFE3E9] rounded-sm text-xs shadow-2xs"
                                >
                                  <div className="flex items-center justify-between mb-1">
                                    <span className="font-bold text-[#172C48]">{mapping.companyName}</span>
                                    <span className="font-mono font-bold text-[#E77848] bg-[#FDF4E7] px-1.5 py-0.5 rounded text-[11px]">
                                      {mapping.originalMaterialCode}
                                    </span>
                                  </div>
                                  <p className="text-[11px] text-[#566579] line-clamp-2 mt-1">
                                    {mapping.originalDescription}
                                  </p>
                                </div>
                              ))}
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
              {filteredCodes.length === 0 && !loading && (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-xs text-[#849DBE]">
                    {codes.length === 0
                      ? 'No National Material Codes assigned yet. Approve matches and click "Assign NMC to Approved Groups".'
                      : `No National Codes found matching "${searchQuery}".`}
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
