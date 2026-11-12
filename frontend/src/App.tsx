import React, { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { CommandCenter } from './pages/CommandCenter';
import { ImportMaterials } from './pages/ImportMaterials';
import { HarmonizationWorkspace } from './pages/HarmonizationWorkspace';
import { MaterialNetwork } from './pages/MaterialNetwork';
import { NationalRegistry } from './pages/NationalRegistry';
import { ReviewApprovals } from './pages/ReviewApprovals';
import { AlgorithmLab } from './pages/AlgorithmLab';
import { SettingsBenchmarks } from './pages/SettingsBenchmarks';
import { api } from './services/api';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState('command-center');
  const [recordCount, setRecordCount] = useState(0);
  const [approvedGroupCount, setApprovedGroupCount] = useState(0);
  const [isBackendHealthy, setIsBackendHealthy] = useState(false);

  const checkHealthAndStats = async () => {
    try {
      const [materials, nationalCodes] = await Promise.all([
        api.getMaterials(),
        api.getNationalCodes(),
      ]);
      setRecordCount(materials.length);
      setApprovedGroupCount(nationalCodes.length);
      setIsBackendHealthy(true);
    } catch (err) {
      setIsBackendHealthy(false);
    }
  };

  useEffect(() => {
    checkHealthAndStats();
    const interval = setInterval(checkHealthAndStats, 8000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-[#F2F0E9] text-[#111A24] flex flex-col font-sans">
      {/* Top Header */}
      <Header
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        recordCount={recordCount}
        approvedGroupCount={approvedGroupCount}
        isBackendHealthy={isBackendHealthy}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'command-center' && (
          <CommandCenter
            onNavigate={(tab) => setActiveTab(tab)}
            onRefresh={checkHealthAndStats}
          />
        )}
        {activeTab === 'import' && (
          <ImportMaterials onRefresh={checkHealthAndStats} />
        )}
        {activeTab === 'harmonization' && (
          <HarmonizationWorkspace onRefresh={checkHealthAndStats} />
        )}
        {activeTab === 'network' && <MaterialNetwork />}
        {activeTab === 'registry' && (
          <NationalRegistry onRefresh={checkHealthAndStats} />
        )}
        {activeTab === 'review' && (
          <ReviewApprovals onRefresh={checkHealthAndStats} />
        )}
        {activeTab === 'algorithm-lab' && <AlgorithmLab />}
        {activeTab === 'benchmarks' && <SettingsBenchmarks />}
      </main>

      {/* Architectural Footer */}
      <footer className="bg-[#172C48] text-[#849DBE] text-xs border-t border-[#243E63] py-6 mt-auto">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="font-mono font-bold text-white flex items-center space-x-2">
              <span>MATRIQ ENGINE</span>
              <span className="text-[#315BEA]">•</span>
              <span className="text-[#D6ED65]">SIH26099</span>
            </div>
            <p className="text-[11px] text-[#9BB1CC]">
              Smart India Hackathon 2026 • Ministry of Petroleum & Natural Gas / CPCL
            </p>
          </div>

          <div className="text-right text-[11px] font-mono">
            <div>Advanced Algorithms CIA 3 Project</div>
            <div className="text-white mt-0.5">
              Anshika Gupta <span className="text-[#D6ED65]">(KMP & DSU)</span> & Hetanshi Vora <span className="text-[#89BAF9]">(Bloom & Trie)</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default App;
