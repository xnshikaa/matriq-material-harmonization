import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { MaterialMatch, MaterialRecord } from '../types';
import { ShieldCheck, Check, X, AlertTriangle, UserCheck, MessageSquare, ArrowRight, ShieldAlert } from 'lucide-react';

interface ReviewApprovalsProps {
  onRefresh: () => void;
}

export const ReviewApprovals: React.FC<ReviewApprovalsProps> = ({ onRefresh }) => {
  const [pendingMatches, setPendingMatches] = useState<MaterialMatch[]>([]);
  const [recordsMap, setRecordsMap] = useState<Record<string, MaterialRecord>>({});
  const [loading, setLoading] = useState(true);
  const [reviewerName, setReviewerName] = useState('Anshika Gupta');
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({});
  const [actingId, setActingId] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [pending, allRecords] = await Promise.all([
        api.getPendingMatches(),
        api.getMaterials(),
      ]);
      setPendingMatches(pending);
      const rMap: Record<string, MaterialRecord> = {};
      allRecords.forEach((r) => { rMap[r.id] = r; });
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

  const handleApprove = async (id: string) => {
    setActingId(id);
    const note = reviewNotes[id] || 'Verified engineering specification & dimensional equivalence.';
    try {
      await api.approveMatch(id, reviewerName, note);
      await loadData();
      onRefresh();
    } catch (err: any) {
      alert(`Approval error: ${err.message}`);
    } finally {
      setActingId(null);
    }
  };

  const handleReject = async (id: string) => {
    setActingId(id);
    const note = reviewNotes[id] || 'Rejected: Critical specification or application difference.';
    try {
      await api.rejectMatch(id, reviewerName, note);
      await loadData();
      onRefresh();
    } catch (err: any) {
      alert(`Rejection error: ${err.message}`);
    } finally {
      setActingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
        <div>
          <h2 className="text-2xl font-black text-[#172C48] font-mono tracking-tight flex items-center space-x-2">
            <ShieldCheck className="w-6 h-6 text-[#107C41]" />
            <span>Human-in-the-Loop Review & Approvals</span>
          </h2>
          <p className="text-xs text-[#566579] mt-1">
            Engineers evaluate candidate equivalences prior to permanent DSU grouping. Approved edges merge clusters; rejected edges trigger dynamic DSU recomputation.
          </p>
        </div>

        <div className="flex items-center space-x-2 text-xs">
          <span className="text-[#849DBE]">Active Reviewer:</span>
          <input
            type="text"
            value={reviewerName}
            onChange={(e) => setReviewerName(e.target.value)}
            className="text-xs font-mono font-bold text-[#172C48] bg-white border border-[#CBD2DC] px-2 py-1 rounded"
          />
        </div>
      </div>

      {/* Viva Safeguard Explainer Alert */}
      <div className="bg-[#FFF8F5] border border-[#F4CD97] p-4 rounded-sm text-xs text-[#172C48]">
        <div className="flex items-center space-x-2 font-bold text-[#E77848] font-mono mb-1">
          <ShieldAlert className="w-4 h-4" />
          <span>VIVA DEFENSE PRINCIPLE: DISJOINT SET UNION ROLLBACK</span>
        </div>
        <p className="text-[#566579] leading-relaxed">
          Standard Disjoint Set Union (DSU) cannot perform $O(1)$ rollback of individual edges due to path compression. MATRIQ maintains an auditable <em>approved edge list</em>. When an item is rejected here, the engine clears DSU state and rebuilds exclusively from the remaining approved edges in $O(E \cdot \alpha(V))$ time, guaranteeing zero contamination of National Code groups.
        </p>
      </div>

      {/* Review Queue */}
      <div className="space-y-4">
        {pendingMatches.map((match) => {
          const recA = recordsMap[match.recordAId];
          const recB = recordsMap[match.recordBId];
          const isActing = actingId === match.id;
          const v = match.attributeVerification;

          return (
            <div
              key={match.id}
              className="bg-white border-2 border-[#DFE3E9] hover:border-[#315BEA] rounded-sm p-6 shadow-sm transition space-y-4"
            >
              {/* Proposal Header */}
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#DFE3E9] pb-3 text-xs">
                <div className="flex items-center space-x-2">
                  <span className="font-mono bg-[#EBF0FC] text-[#315BEA] px-2 py-0.5 rounded font-bold text-[11px]">
                    PROPOSED EQUIVALENCE
                  </span>
                  <span className="font-mono text-[#849DBE] text-[11px]">
                    Candidate ID: {match.id.slice(0, 8)}
                  </span>
                </div>
                <div className="font-mono text-xs">
                  <span className="text-[#849DBE]">Confidence Formula Score:</span>{' '}
                  <strong className="text-[#107C41]">{Math.round(match.confidenceScore * 100)}%</strong>
                </div>
              </div>

              {/* Side-by-side materials */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                  <div className="flex justify-between items-center mb-1">
                    <strong className="text-[#172C48]">{recA?.companyName || 'Entity A'}</strong>
                    <span className="font-mono font-bold text-[#E77848] bg-[#FDF4E7] px-1.5 py-0.5 rounded">
                      {recA?.originalMaterialCode}
                    </span>
                  </div>
                  <p className="font-medium text-[#172C48] mb-2">{recA?.originalDescription}</p>
                  <div className="grid grid-cols-2 gap-1 text-[11px] font-mono text-[#566579] border-t border-[#DFE3E9] pt-2">
                    <div>Category: <strong>{recA?.materialCategory || '—'}</strong></div>
                    <div>Dimensions: <strong>{recA?.dimensions || '—'} {recA?.unit || ''}</strong></div>
                    <div>Grade: <strong>{recA?.grade || '—'}</strong></div>
                    <div>Spec: <strong className="text-[#315BEA]">{recA?.technicalSpecification || '—'}</strong></div>
                  </div>
                </div>

                <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                  <div className="flex justify-between items-center mb-1">
                    <strong className="text-[#172C48]">{recB?.companyName || 'Entity B'}</strong>
                    <span className="font-mono font-bold text-[#E77848] bg-[#FDF4E7] px-1.5 py-0.5 rounded">
                      {recB?.originalMaterialCode}
                    </span>
                  </div>
                  <p className="font-medium text-[#172C48] mb-2">{recB?.originalDescription}</p>
                  <div className="grid grid-cols-2 gap-1 text-[11px] font-mono text-[#566579] border-t border-[#DFE3E9] pt-2">
                    <div>Category: <strong>{recB?.materialCategory || '—'}</strong></div>
                    <div>Dimensions: <strong>{recB?.dimensions || '—'} {recB?.unit || ''}</strong></div>
                    <div>Grade: <strong>{recB?.grade || '—'}</strong></div>
                    <div>Spec: <strong className="text-[#315BEA]">{recB?.technicalSpecification || '—'}</strong></div>
                  </div>
                </div>
              </div>

              {/* Verification checklist */}
              {v && (
                <div className="flex flex-wrap items-center gap-2 text-[11px] pt-1">
                  <span className="font-mono text-[#849DBE]">Verification Gate:</span>
                  <span className={`px-2 py-0.5 rounded font-mono font-bold ${v.categoryMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Category: {v.categoryMatch ? 'OK' : 'FAIL'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-bold ${v.gradeMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Grade: {v.gradeMatch ? 'OK' : 'FAIL'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-bold ${v.dimensionMatch ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    Dimension: {v.dimensionMatch ? 'OK' : 'FAIL'}
                  </span>
                  <span className={`px-2 py-0.5 rounded font-mono font-bold ${v.specificationMatch ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800'}`}>
                    KMP Specs: {match.matchedSpecifications.join(', ') || 'NONE'}
                  </span>
                </div>
              )}

              {/* Reviewer Note Input & Action Bar */}
              <div className="pt-3 border-t border-[#DFE3E9] flex flex-wrap items-center justify-between gap-3">
                <div className="flex-1 min-w-[280px]">
                  <input
                    type="text"
                    placeholder="Enter engineering justification or audit notes..."
                    value={reviewNotes[match.id] || ''}
                    onChange={(e) => setReviewNotes({ ...reviewNotes, [match.id]: e.target.value })}
                    className="w-full text-xs font-mono border border-[#CBD2DC] rounded p-2 bg-[#F9F9FB] outline-none focus:border-[#315BEA]"
                  />
                </div>

                <div className="flex items-center space-x-2">
                  <button
                    onClick={() => handleApprove(match.id)}
                    disabled={isActing}
                    className="inline-flex items-center space-x-1.5 px-4 py-2 bg-[#107C41] hover:bg-[#0E6837] text-white rounded text-xs font-semibold cursor-pointer disabled:opacity-50"
                  >
                    <Check className="w-4 h-4" />
                    <span>Approve & Add to DSU</span>
                  </button>
                  <button
                    onClick={() => handleReject(match.id)}
                    disabled={isActing}
                    className="inline-flex items-center space-x-1.5 px-4 py-2 bg-[#C43828] hover:bg-[#A82E20] text-white rounded text-xs font-semibold cursor-pointer disabled:opacity-50"
                  >
                    <X className="w-4 h-4" />
                    <span>Reject & Rebuild DSU</span>
                  </button>
                </div>
              </div>
            </div>
          );
        })}

        {pendingMatches.length === 0 && !loading && (
          <div className="bg-white border border-[#DFE3E9] rounded-sm p-12 text-center text-xs text-[#849DBE]">
            <Check className="w-8 h-8 mx-auto mb-2 text-[#107C41]" />
            <p className="font-bold text-[#172C48] text-sm">Review Queue Empty</p>
            <p className="mt-1">All proposed material matches have been processed or auto-approved.</p>
          </div>
        )}
      </div>
    </div>
  );
};
