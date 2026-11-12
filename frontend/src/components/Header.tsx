import React from 'react';
import { Layers, Database, ShieldCheck, Activity, Award } from 'lucide-react';

interface HeaderProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  recordCount: number;
  approvedGroupCount: number;
  isBackendHealthy: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  setActiveTab,
  recordCount,
  approvedGroupCount,
  isBackendHealthy,
}) => {
  const tabs = [
    { id: 'command-center', label: 'Command Center', icon: Activity },
    { id: 'import', label: 'Import Materials', icon: Database },
    { id: 'harmonization', label: 'Harmonization Workspace', icon: Layers },
    { id: 'network', label: 'Material Network', icon: Layers },
    { id: 'registry', label: 'National Registry', icon: Award },
    { id: 'review', label: 'Review & Approvals', icon: ShieldCheck },
    { id: 'algorithm-lab', label: 'Algorithm Lab', icon: Activity },
    { id: 'benchmarks', label: 'Benchmarks & Viva', icon: Award },
  ];

  return (
    <header className="bg-[#172C48] text-white border-b border-[#243E63] sticky top-0 z-50 shadow-md">
      {/* Top Banner: Project Metadata & Team Attribution */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-2.5 border-b border-[#243E63]/80 flex flex-wrap items-center justify-between text-xs text-[#9BB1CC]">
        <div className="flex items-center space-x-3">
          <span className="font-mono bg-[#315BEA] text-white px-2 py-0.5 rounded font-semibold tracking-wider">
            SIH26099
          </span>
          <span className="text-[#DFE3E9] font-medium">
            Ministry of Petroleum & Natural Gas / CPCL
          </span>
          <span className="hidden sm:inline text-[#6A84A6]">•</span>
          <span className="hidden sm:inline text-[#BAC9DD]">
            Advanced Algorithms CIA 3
          </span>
        </div>

        <div className="flex items-center space-x-4 mt-1 sm:mt-0">
          <div className="flex items-center space-x-2">
            <span className="text-[#849DBE]">Engineers:</span>
            <span className="text-white font-medium bg-[#1F395C] px-2 py-0.5 rounded border border-[#2D4F7C]">
              Anshika Gupta <span className="text-[#D6ED65] font-mono text-[10px]">(KMP + DSU)</span>
            </span>
            <span className="text-white font-medium bg-[#1F395C] px-2 py-0.5 rounded border border-[#2D4F7C]">
              Hetanshi Vora <span className="text-[#89BAF9] font-mono text-[10px]">(Bloom + Trie)</span>
            </span>
          </div>

          <div className="flex items-center space-x-1.5 pl-2 border-l border-[#243E63]">
            <span
              className={`w-2 h-2 rounded-full ${
                isBackendHealthy ? 'bg-[#D6ED65] animate-pulse' : 'bg-red-400'
              }`}
            />
            <span className="font-mono text-[11px] text-[#DFE3E9]">
              {isBackendHealthy ? 'BACKEND LIVE' : 'CONNECTING'}
            </span>
          </div>
        </div>
      </div>

      {/* Main Bar: Logo, Slogan & Metrics */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center space-x-4">
          <div className="w-10 h-10 rounded bg-gradient-to-br from-[#315BEA] to-[#1F395C] border border-[#5278F2] flex items-center justify-center font-mono font-black text-xl text-white shadow-inner">
            M
          </div>
          <div>
            <div className="flex items-baseline space-x-2">
              <h1 className="text-2xl font-black tracking-tight text-white font-mono">
                MATRIQ
              </h1>
              <span className="text-[11px] font-mono tracking-widest text-[#D6ED65] uppercase font-bold">
                Harmonization Engine
              </span>
            </div>
            <p className="text-xs text-[#9BB1CC] tracking-wide">
              ONE MATERIAL. ONE IDENTITY.
            </p>
          </div>
        </div>

        {/* Quick KPI stats */}
        <div className="flex items-center space-x-6 text-right">
          <div className="border-r border-[#243E63] pr-6 hidden md:block">
            <div className="text-[11px] text-[#849DBE] uppercase tracking-wider font-mono">Catalog Records</div>
            <div className="text-lg font-bold font-mono text-white">{recordCount}</div>
          </div>
          <div className="border-r border-[#243E63] pr-6 hidden md:block">
            <div className="text-[11px] text-[#849DBE] uppercase tracking-wider font-mono">Approved NMC Groups</div>
            <div className="text-lg font-bold font-mono text-[#D6ED65]">{approvedGroupCount}</div>
          </div>
          <div className="hidden lg:block text-left">
            <div className="text-[11px] text-[#849DBE] uppercase tracking-wider font-mono">Core Algorithms</div>
            <div className="text-xs font-mono text-[#BAC9DD]">KMP • DSU • Bloom • Trie</div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 border-t border-[#243E63]/80">
        <nav className="flex space-x-1 overflow-x-auto py-1 scrollbar-none" aria-label="Tabs">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center space-x-2 px-3.5 py-2 text-xs font-medium rounded-t transition-all whitespace-nowrap cursor-pointer ${
                  isActive
                    ? 'bg-[#F2F0E9] text-[#172C48] font-semibold border-t-2 border-[#315BEA] shadow-sm'
                    : 'text-[#9BB1CC] hover:text-white hover:bg-[#1E3657]'
                }`}
              >
                <Icon className={`w-3.5 h-3.5 ${isActive ? 'text-[#315BEA]' : 'text-[#6A84A6]'}`} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </nav>
      </div>
    </header>
  );
};
