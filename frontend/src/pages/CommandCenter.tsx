import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { MaterialRecord, HarmonizationResult, MaterialGroup } from '../types';
import { Play, Database, Layers, CheckCircle2, AlertTriangle, ArrowRight, Activity, Award } from 'lucide-react';

interface CommandCenterProps {
  onNavigate: (tabId: string) => void;
  onRefresh: () => void;
}

export const CommandCenter: React.FC<CommandCenterProps> = ({ onNavigate, onRefresh }) => {
  const [records, setRecords] = useState<MaterialRecord[]>([]);
  const [groups, setGroups] = useState<MaterialGroup[]>([]);
  const [stats, setStats] = useState<Record<string, any>>({});
  const [loading, setLoading] = useState(true);
  const [runningPipeline, setRunningPipeline] = useState(false);
  const [pipelineResult, setPipelineResult] = useState<HarmonizationResult | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [m, g, s] = await Promise.all([
        api.getMaterials(),
        api.getGroups(),
        api.getMaterialStats(),
      ]);
      setRecords(m);
      setGroups(g);
      setStats(s);
    } catch (err) {
      console.error('Failed to load command center data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleRunHarmonization = async () => {
    setRunningPipeline(true);
    try {
      const res = await api.runHarmonization();
      setPipelineResult(res);
      await loadData();
      onRefresh();
    } catch (err: any) {
      alert(`Harmonization failed: ${err.message}`);
    } finally {
      setRunningPipeline(false);
    }
  };

  const handleLoadDemoDataset = async () => {
    try {
      // Ingest the 3 sample CSV files via backend or frontend trigger
      const res = await fetch('/data/sample/cpcl_sample.csv');
      if (res.ok) {
        const text = await res.text();
        const file = new File([text], 'cpcl_sample.csv', { type: 'text/csv' });
        await api.uploadCsv(file, 'CPCL');
      }
      const res2 = await fetch('/data/sample/iocl_sample.csv');
      if (res2.ok) {
        const text2 = await res2.text();
        const file2 = new File([text2], 'iocl_sample.csv', { type: 'text/csv' });
        await api.uploadCsv(file2, 'IOCL');
      }
      const res3 = await fetch('/data/sample/ongc_sample.csv');
      if (res3.ok) {
        const text3 = await res3.text();
        const file3 = new File([text3], 'ongc_sample.csv', { type: 'text/csv' });
        await api.uploadCsv(file3, 'ONGC');
      }
      await loadData();
      onRefresh();
      alert('Sample datasets for CPCL, IOCL, and ONGC successfully imported!');
    } catch (err: any) {
      alert(`Failed to load demo data: ${err.message}`);
    }
  };

  const companyCounts = records.reduce((acc, r) => {
    acc[r.companyName] = (acc[r.companyName] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  return (
    <div className="space-y-8">
      {/* Top Hero & Mission Card */}
      <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 sm:p-8 shadow-sm relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-full bg-tech-grid opacity-30 pointer-events-none" />
        <div className="max-w-3xl">
          <div className="inline-flex items-center space-x-2 px-2.5 py-1 rounded bg-[#EBF0FC] text-[#315BEA] font-mono text-xs font-semibold mb-4">
            <span className="w-1.5 h-1.5 rounded-full bg-[#315BEA]" />
            <span>SIH26099 • Ministry of Petroleum & Natural Gas</span>
          </div>
          <h2 className="text-3xl font-black text-[#172C48] tracking-tight font-serif mb-3">
            Intelligent Material Code Harmonization Engine
          </h2>
          <p className="text-sm text-[#566579] leading-relaxed mb-6">
            MATRIQ reconciles redundant, non-standardized industrial catalogs across CPCL, IOCL, ONGC, and other PSUs. 
            By uniting <strong>KMP String Search</strong> for technical standards and <strong>Disjoint Set Union (DSU)</strong> 
            for transitive graph clustering, the platform assigns unified <strong>National Material Codes (NMC)</strong> 
            while safeguarding all original organization-specific part numbers.
          </p>

          <div className="flex flex-wrap gap-3">
            <button
              onClick={handleRunHarmonization}
              disabled={runningPipeline || records.length === 0}
              className="inline-flex items-center space-x-2 px-5 py-2.5 bg-[#315BEA] hover:bg-[#2548C2] text-white text-xs font-semibold rounded-sm transition shadow-sm cursor-pointer disabled:opacity-50"
            >
              <Play className={`w-4 h-4 ${runningPipeline ? 'animate-spin' : ''}`} />
              <span>{runningPipeline ? 'Running Harmonization Pipeline...' : 'Run Harmonization Engine'}</span>
            </button>

            <button
              onClick={handleLoadDemoDataset}
              className="inline-flex items-center space-x-2 px-4 py-2.5 bg-[#F2F0E9] hover:bg-[#E4E0D5] text-[#172C48] border border-[#CBD2DC] text-xs font-semibold rounded-sm transition cursor-pointer"
            >
              <Database className="w-4 h-4 text-[#172C48]" />
              <span>Load PSU Demo Catalogs (CPCL / IOCL / ONGC)</span>
            </button>

            <button
              onClick={() => onNavigate('algorithm-lab')}
              className="inline-flex items-center space-x-2 px-4 py-2.5 bg-white hover:bg-[#F2F0E9] text-[#172C48] border border-[#DFE3E9] text-xs font-semibold rounded-sm transition cursor-pointer"
            >
              <Activity className="w-4 h-4 text-[#315BEA]" />
              <span>Open Algorithm Viva Lab</span>
            </button>
          </div>
        </div>
      </div>

      {/* KPI Metrics Ribbon */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white border border-[#DFE3E9] p-5 rounded-sm shadow-sm">
          <div className="text-[11px] font-mono text-[#849DBE] uppercase tracking-wider font-semibold">Total Catalog Records</div>
          <div className="text-3xl font-black font-mono text-[#172C48] mt-2">{records.length}</div>
          <div className="text-[11px] text-[#566579] mt-1">Across {Object.keys(companyCounts).length} industrial entities</div>
        </div>

        <div className="bg-white border border-[#DFE3E9] p-5 rounded-sm shadow-sm">
          <div className="text-[11px] font-mono text-[#849DBE] uppercase tracking-wider font-semibold">Discovered DSU Groups</div>
          <div className="text-3xl font-black font-mono text-[#315BEA] mt-2">{groups.length}</div>
          <div className="text-[11px] text-[#566579] mt-1">Transitive component clusters</div>
        </div>

        <div className="bg-white border border-[#DFE3E9] p-5 rounded-sm shadow-sm">
          <div className="text-[11px] font-mono text-[#849DBE] uppercase tracking-wider font-semibold">Pending Review Queue</div>
          <div className="text-3xl font-black font-mono text-[#E77848] mt-2">
            {stats.pendingMatches !== undefined ? stats.pendingMatches : 0}
          </div>
          <div className="text-[11px] text-[#566579] mt-1">Awaiting human sign-off</div>
        </div>

        <div className="bg-white border border-[#DFE3E9] p-5 rounded-sm shadow-sm">
          <div className="text-[11px] font-mono text-[#849DBE] uppercase tracking-wider font-semibold">Assigned NMC Codes</div>
          <div className="text-3xl font-black font-mono text-[#107C41] mt-2">
            {stats.nationalCodes !== undefined ? stats.nationalCodes : 0}
          </div>
          <div className="text-[11px] text-[#566579] mt-1">Registered National Codes</div>
        </div>
      </div>

      {/* Pipeline Status Banner if run */}
      {pipelineResult && (
        <div className="bg-[#F6FAF1] border border-[#C6E6B8] p-4 rounded-sm flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <CheckCircle2 className="w-5 h-5 text-[#107C41]" />
            <div>
              <div className="text-xs font-bold text-[#172C48]">Harmonization Run Completed Successfully</div>
              <div className="text-xs text-[#566579]">
                Processed {pipelineResult.totalRecords} records • {pipelineResult.candidateMatches} candidate proposals • {pipelineResult.groupsFormed} DSU groups formed in {pipelineResult.executionDurationMs} ms
              </div>
            </div>
          </div>
          <button
            onClick={() => onNavigate('harmonization')}
            className="text-xs font-semibold text-[#315BEA] hover:underline flex items-center space-x-1"
          >
            <span>Inspect Matches</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Multi-Stage Algorithm Pipeline Flow Visual */}
      <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-bold text-[#172C48] font-mono">MATRIQ Harmonization Processing Pipeline</h3>
            <p className="text-xs text-[#566579]">Seven-stage deterministic pipeline from raw catalog ingest to national code registration</p>
          </div>
          <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-[#F2F0E9] text-[#172C48] border border-[#DFE3E9]">
            Zero False-Negatives Guaranteed
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-7 gap-3 text-xs">
          <div className="p-3 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
            <div className="font-mono font-bold text-[#172C48] mb-1">01. INGESTION</div>
            <div className="text-[#566579]">CSV validation, schema mapping, duplicate checks</div>
          </div>
          <div className="p-3 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
            <div className="font-mono font-bold text-[#172C48] mb-1">02. NORMALIZE</div>
            <div className="text-[#566579]">Trie abbreviation expansion, unit conversions</div>
          </div>
          <div className="p-3 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
            <div className="font-mono font-bold text-[#172C48] mb-1">03. BLOOM FILTER</div>
            <div className="text-[#566579]">O(k) duplicate signature screening (CO5)</div>
          </div>
          <div className="p-3 bg-[#EBF0FC] border border-[#BAC9DD] rounded-sm">
            <div className="font-mono font-bold text-[#315BEA] mb-1">04. KMP MATCHER</div>
            <div className="text-[#172C48]">O(n+m) technical spec locator (Anshika)</div>
          </div>
          <div className="p-3 bg-[#FDF4E7] border border-[#F4CD97] rounded-sm">
            <div className="font-mono font-bold text-[#E77848] mb-1">05. ATTRIBUTE GATE</div>
            <div className="text-[#172C48]">Hard gate: Category, grade, dimension verification</div>
          </div>
          <div className="p-3 bg-[#EBF0FC] border border-[#BAC9DD] rounded-sm">
            <div className="font-mono font-bold text-[#315BEA] mb-1">06. DSU CLUSTER</div>
            <div className="text-[#172C48]">O(α(n)) transitive grouping & rebuild (Anshika)</div>
          </div>
          <div className="p-3 bg-[#F3F9EA] border border-[#C6E6B8] rounded-sm">
            <div className="font-mono font-bold text-[#107C41] mb-1">07. NATIONAL CODE</div>
            <div className="text-[#172C48]">Deterministic NMC registry issuance</div>
          </div>
        </div>
      </div>

      {/* Catalog Preview & Organization Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Participating PSUs */}
        <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm">
          <h3 className="text-sm font-bold text-[#172C48] font-mono uppercase tracking-wider mb-4">
            Participating Organizations
          </h3>
          <div className="space-y-3">
            {Object.keys(companyCounts).length === 0 ? (
              <div className="text-xs text-[#849DBE] py-4 text-center">
                No catalogs imported yet. Click "Load PSU Demo Catalogs" above.
              </div>
            ) : (
              Object.entries(companyCounts).map(([company, count]) => (
                <div key={company} className="flex items-center justify-between p-3 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                  <div>
                    <div className="font-bold text-xs text-[#172C48]">{company}</div>
                    <div className="text-[11px] text-[#566579]">Industrial Material Master</div>
                  </div>
                  <div className="text-right">
                    <span className="font-mono font-bold text-xs text-[#315BEA] bg-[#EBF0FC] px-2 py-0.5 rounded">
                      {count} items
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Recent Catalog Records */}
        <div className="lg:col-span-2 bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-bold text-[#172C48] font-mono uppercase tracking-wider">
              Catalog Sampler
            </h3>
            <button
              onClick={() => onNavigate('import')}
              className="text-xs text-[#315BEA] font-medium hover:underline flex items-center space-x-1"
            >
              <span>View All Records</span>
              <ArrowRight className="w-3 h-3" />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#DFE3E9] bg-[#F9F9FB] font-mono text-[#849DBE]">
                  <th className="py-2.5 px-3">Company</th>
                  <th className="py-2.5 px-3">Original Code</th>
                  <th className="py-2.5 px-3">Description</th>
                  <th className="py-2.5 px-3">Spec / Grade</th>
                  <th className="py-2.5 px-3">NMC Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#DFE3E9]">
                {records.slice(0, 5).map((r) => (
                  <tr key={r.id} className="hover:bg-[#F9F9FB] transition">
                    <td className="py-2.5 px-3 font-semibold text-[#172C48]">{r.companyName}</td>
                    <td className="py-2.5 px-3 font-mono text-[#E77848] font-medium">{r.originalMaterialCode}</td>
                    <td className="py-2.5 px-3 text-[#566579] max-w-xs truncate">{r.originalDescription}</td>
                    <td className="py-2.5 px-3 font-mono text-[#315BEA]">{r.technicalSpecification || r.grade || '—'}</td>
                    <td className="py-2.5 px-3">
                      {r.nationalMaterialCode ? (
                        <span className="font-mono text-[10px] bg-[#EBF0FC] text-[#315BEA] px-2 py-0.5 rounded font-semibold">
                          {r.nationalMaterialCode}
                        </span>
                      ) : (
                        <span className="text-[10px] bg-[#F2F0E9] text-[#849DBE] px-2 py-0.5 rounded font-mono">
                          {r.status}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
                {records.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-[#849DBE]">
                      No material records in memory. Please import CSV catalogs to begin.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
