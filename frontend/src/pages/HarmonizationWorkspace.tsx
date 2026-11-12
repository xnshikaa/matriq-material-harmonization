import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { MaterialMatch, MaterialRecord } from '../types';
import { Check, X, Shield, ArrowRight, Filter, Search, Award, CheckCircle2, AlertTriangle, Layers } from 'lucide-react';

interface HarmonizationWorkspaceProps {
  onRefresh: () => void;
}

export const HarmonizationWorkspace: React.FC<HarmonizationWorkspaceProps> = ({ onRefresh }) => {
  const [matches, setMatches] = useState<MaterialMatch[]>([]);
  const [recordsMap, setRecordsMap] = useState<Record<string, MaterialRecord>>({});
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING' | 'AUTO_APPROVED' | 'APPROVED' | 'REJECTED'>('ALL');
  const [loading, setLoading] = useState(true);
  const [actingMatchId, setActingMatchId] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [allMatches, allRecords] = await Promise.all([
        api.getAllMatches(),
        api.getMaterials(),
      ]);
      setMatches(allMatches);
      const rMap: Record<string, MaterialRecord> = {};
      allRecords.forEach((r) => {
        rMap[r.id] = r;
      });
      setRecordsMap(rMap);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleApprove = async (matchId: string) => {
    setActingMatchId(matchId);
    try {
      await api.approveMatch(matchId, 'Lead Harmonization Engineer', 'Verified physical equivalence across standards');
      await loadData();
      onRefresh();
    } catch (err: any) {
      alert(`Approval failed: ${err.message}`);
    } finally {
      setActingMatchId(null);
    }
  };

  const handleReject = async (matchId: string) => {
    setActingMatchId(matchId);
    try {
      await api.rejectMatch(matchId, 'Lead Harmonization Engineer', 'Critical technical specification mismatch detected');
      await loadData();
      onRefresh();
    } catch (err: any) {
      alert(`Rejection failed: ${err.message}`);
    } finally {
      setActingMatchId(null);
    }
  };

  const filteredMatches = matches.filter((m) => {
    if (statusFilter === 'ALL') return true;
    return m.status === statusFilter;
  });

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
        <div>
          <h2 className="text-2xl font-black text-[#172C48] font-mono tracking-tight">
            Harmonization & Matching Workspace
          </h2>
          <p className="text-xs text-[#566579] mt-1">
            Review pairwise equivalences established via KMP specification detection and verified by the multi-criteria attribute gate.
          </p>
        </div>

        {/* Filter buttons */}
        <div className="flex items-center space-x-2 text-xs font-mono">
          {(['ALL', 'PENDING', 'AUTO_APPROVED', 'APPROVED', 'REJECTED'] as const).map((status) => (
            <button
              key={status}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1.5 rounded text-xs transition cursor-pointer ${
                statusFilter === status
                  ? 'bg-[#172C48] text-white font-semibold'
                  : 'bg-white border border-[#DFE3E9] text-[#566579] hover:bg-[#F2F0E9]'
              }`}
            >
              {status} ({status === 'ALL' ? matches.length : matches.filter((m) => m.status === status).length})
            </button>
          ))}
        </div>
      </div>

      {/* Verification Rule Alert Box */}
      <div className="bg-[#EBF0FC] border border-[#BAC9DD] rounded-sm p-4 text-xs text-[#172C48]">
        <div className="flex items-center space-x-2 font-mono font-bold text-[#315BEA] mb-1">
          <Shield className="w-4 h-4" />
          <span>STRICT VERIFICATION GATE (NO FALSE UNIONS)</span>
        </div>
        <p className="text-[#334A6A] leading-relaxed">
          KMP specification detection is necessary but <strong>never sufficient</strong> alone. Two items sharing ASTM A106 will only reach DSU union if they pass category alignment, dimension tolerance conversion (e.g. 6" == 150mm ±2%), and material grade compatibility. Rejecting a match immediately rebuilds the DSU structure from remaining approved edges.
        </p>
      </div>

      {/* Match Cards List */}
      <div className="space-y-4">
        {filteredMatches.map((match) => {
          const recA = recordsMap[match.recordAId];
          const recB = recordsMap[match.recordBId];
          const isActing = actingMatchId === match.id;
          const v = match.attributeVerification;

          return (
            <div
              key={match.id}
              className="bg-white border border-[#DFE3E9] rounded-sm p-5 shadow-sm hover:border-[#BAC9DD] transition space-y-4"
            >
              {/* Card Header: IDs & Status */}
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#DFE3E9] pb-3 text-xs">
                <div className="flex items-center space-x-3">
                  <span className="font-mono text-[11px] text-[#849DBE]">Match ID: {match.id.substring(0, 8)}...</span>
                  <span
                    className={`font-mono text-[10px] font-bold px-2 py-0.5 rounded ${
                      match.status === 'APPROVED' || match.status === 'AUTO_APPROVED'
                        ? 'bg-green-100 text-green-800'
                        : match.status === 'REJECTED'
                        ? 'bg-red-100 text-red-800'
                        : 'bg-amber-100 text-amber-800'
                    }`}
                  >
                    {match.status}
                  </span>
                </div>

                <div className="flex items-center space-x-4">
                  <div className="text-right">
                    <span className="text-[#849DBE] text-[11px] font-mono">Confidence Score:</span>{' '}
                    <strong className="font-mono text-[#315BEA]">{Math.round(match.confidenceScore * 100)}%</strong>
                  </div>

                  {/* Actions */}
                  {match.status === 'PENDING' && (
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={() => handleApprove(match.id)}
                        disabled={isActing}
                        className="inline-flex items-center space-x-1 px-3 py-1 bg-[#107C41] hover:bg-[#0E6837] text-white rounded text-xs font-semibold cursor-pointer disabled:opacity-50"
                      >
                        <Check className="w-3.5 h-3.5" />
                        <span>Approve</span>
                      </button>
                      <button
                        onClick={() => handleReject(match.id)}
                        disabled={isActing}
                        className="inline-flex items-center space-x-1 px-3 py-1 bg-[#C43828] hover:bg-[#A82E20] text-white rounded text-xs font-semibold cursor-pointer disabled:opacity-50"
                      >
                        <X className="w-3.5 h-3.5" />
                        <span>Reject</span>
                      </button>
                    </div>
                  )}
                </div>
              </div>

              {/* Side-by-Side Comparison */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                {/* Record A */}
                <div className="p-3.5 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-bold text-[#172C48]">{recA ? recA.companyName : 'Entity A'}</span>
                    <span className="font-mono font-bold text-[#E77848] bg-[#FDF4E7] px-2 py-0.5 rounded border border-[#F4CD97]">
                      {recA ? recA.originalMaterialCode : match.recordAId}
                    </span>
                  </div>
                  <p className="text-[#172C48] font-medium mb-2">
                    {recA ? recA.originalDescription : 'Loading record...'}
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-[11px] text-[#566579] font-mono pt-2 border-t border-[#DFE3E9]">
                    <div>Dim: <strong className="text-[#172C48]">{recA?.dimensions || '—'} {recA?.unit || ''}</strong></div>
                    <div>Grade: <strong className="text-[#172C48]">{recA?.grade || '—'}</strong></div>
                    <div className="col-span-2">Spec: <strong className="text-[#315BEA]">{recA?.technicalSpecification || '—'}</strong></div>
                  </div>
                </div>

                {/* Record B */}
                <div className="p-3.5 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-bold text-[#172C48]">{recB ? recB.companyName : 'Entity B'}</span>
                    <span className="font-mono font-bold text-[#E77848] bg-[#FDF4E7] px-2 py-0.5 rounded border border-[#F4CD97]">
                      {recB ? recB.originalMaterialCode : match.recordBId}
                    </span>
                  </div>
                  <p className="text-[#172C48] font-medium mb-2">
                    {recB ? recB.originalDescription : 'Loading record...'}
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-[11px] text-[#566579] font-mono pt-2 border-t border-[#DFE3E9]">
                    <div>Dim: <strong className="text-[#172C48]">{recB?.dimensions || '—'} {recB?.unit || ''}</strong></div>
                    <div>Grade: <strong className="text-[#172C48]">{recB?.grade || '—'}</strong></div>
                    <div className="col-span-2">Spec: <strong className="text-[#315BEA]">{recB?.technicalSpecification || '—'}</strong></div>
                  </div>
                </div>
              </div>

              {/* Attribute Verification Badges */}
              {v && (
                <div className="pt-2 border-t border-[#DFE3E9] flex flex-wrap items-center gap-2 text-[11px]">
                  <span className="text-[#849DBE] font-mono mr-1">Verification Checks:</span>
                  <span className={`px-2 py-0.5 rounded font-mono font-semibold ${v.categoryMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Category: {v.categoryMatch ? 'PASS' : 'FAIL'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-semibold ${v.typeMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Type: {v.typeMatch ? 'PASS' : 'FAIL'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-semibold ${v.gradeMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Grade: {v.gradeMatch ? 'PASS' : 'FAIL'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-semibold ${v.dimensionMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Dimensions: {v.dimensionMatch ? 'COMPATIBLE' : 'INCOMPATIBLE'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-semibold ${v.specificationMatch ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800'}`}>
                    KMP Spec: {match.matchedSpecifications.join(', ') || 'NONE'}
                  </span>
                </div>
              )}

              {/* Reviewer notes / justification */}
              {match.reviewNote && (
                <div className="p-2.5 bg-[#F9F9FB] rounded text-xs text-[#566579] font-mono">
                  <span className="text-[#849DBE]">Review Note ({match.reviewedBy}):</span> {match.reviewNote}
                </div>
              )}
            </div>
          );
        })}

        {filteredMatches.length === 0 && !loading && (
          <div className="bg-white border border-[#DFE3E9] rounded-sm p-12 text-center text-xs text-[#849DBE]">
            No material matches found matching filter "{statusFilter}".
            Click "Run Harmonization Pipeline" on the Command Center to initiate matching.
          </div>
        )}
      </div>
    </div>
  );
};
