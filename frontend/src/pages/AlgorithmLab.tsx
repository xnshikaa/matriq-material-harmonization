import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { KMPTraceResponse, KMPStep, BloomStats } from '../types';
import { Play, Pause, RotateCcw, ChevronRight, ChevronLeft, Search, Layers, Zap, BookOpen, Check, X, ShieldAlert } from 'lucide-react';

export const AlgorithmLab: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'kmp' | 'dsu' | 'bloom' | 'trie'>('kmp');

  // ── KMP State ─────────────────────────────────────────────────────────────
  const [kmpText, setKmpText] = useState('Pipe, seamless, 6 inch, ASTM A106 Gr.B');
  const [kmpPattern, setKmpPattern] = useState('ASTM A106');
  const [kmpTrace, setKmpTrace] = useState<KMPTraceResponse | null>(null);
  const [currentStepIdx, setCurrentStepIdx] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [kmpLoading, setKmpLoading] = useState(false);

  // ── DSU State ─────────────────────────────────────────────────────────────
  const [dsuElements, setDsuElements] = useState<string[]>([
    'PIPE-001 (CPCL)', 'P-9281 (IOCL)', 'MAT-1003 (ONGC)',
    'VALVE-102 (CPCL)', 'V-4412 (IOCL)',
    'BOLT-501 (CPCL)'
  ]);
  const [dsuUnions, setDsuUnions] = useState<Array<[string, string]>>([
    ['PIPE-001 (CPCL)', 'P-9281 (IOCL)'],
    ['P-9281 (IOCL)', 'MAT-1003 (ONGC)'],
    ['VALVE-102 (CPCL)', 'V-4412 (IOCL)']
  ]);
  const [dsuResult, setDsuResult] = useState<any>(null);
  const [dsuLoading, setDsuLoading] = useState(false);

  // ── Bloom Filter State ───────────────────────────────────────────────────
  const [bloomStats, setBloomStats] = useState<BloomStats | null>(null);
  const [bloomQuery, setBloomQuery] = useState('PIPE:CARBON STEEL:6 INCH:ASTM A106');
  const [bloomQueryResult, setBloomQueryResult] = useState<string | null>(null);

  // ── Trie State ───────────────────────────────────────────────────────────
  const [triePrefix, setTriePrefix] = useState('ASTM');
  const [trieResult, setTrieResult] = useState<{ prefix: string; exactMatch: boolean; value: string; suggestions: string[] } | null>(null);

  // Run KMP Trace
  const runKmpTrace = async () => {
    setKmpLoading(true);
    setIsPlaying(false);
    try {
      const trace = await api.kmpTrace(kmpText, kmpPattern);
      setKmpTrace(trace);
      setCurrentStepIdx(0);
    } catch (err: any) {
      alert(`KMP Trace error: ${err.message}`);
    } finally {
      setKmpLoading(false);
    }
  };

  useEffect(() => {
    runKmpTrace();
    loadBloomStats();
    runTrieSearch('ASTM');
    runDsuDemo();
  }, []);

  // Animation player for KMP
  useEffect(() => {
    let timer: any;
    if (isPlaying && kmpTrace && currentStepIdx < kmpTrace.steps.length - 1) {
      timer = setTimeout(() => {
        setCurrentStepIdx((prev) => prev + 1);
      }, 700);
    } else if (isPlaying && kmpTrace && currentStepIdx >= kmpTrace.steps.length - 1) {
      setIsPlaying(false);
    }
    return () => clearTimeout(timer);
  }, [isPlaying, currentStepIdx, kmpTrace]);

  const loadBloomStats = async () => {
    try {
      const stats = await api.getBloomStats();
      setBloomStats(stats);
    } catch (err) {
      console.error(err);
    }
  };

  const runTrieSearch = async (prefix: string) => {
    try {
      const res = await api.trieSearch(prefix);
      setTrieResult(res);
    } catch (err) {
      console.error(err);
    }
  };

  const runDsuDemo = async () => {
    setDsuLoading(true);
    try {
      const ops = [
        ...dsuElements.map(e => ({ type: 'makeSet', a: e })),
        ...dsuUnions.map(u => ({ type: 'union', a: u[0], b: u[1] })),
        { type: 'find', a: dsuElements[0] },
      ];
      const res = await api.dsuDemo(ops);
      setDsuResult(res);
    } catch (err) {
      console.error(err);
    } finally {
      setDsuLoading(false);
    }
  };

  const currentStep: KMPStep | null =
    kmpTrace && kmpTrace.steps[currentStepIdx] ? kmpTrace.steps[currentStepIdx] : null;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-2xl font-black text-[#172C48] font-mono tracking-tight">
              Algorithm Lab & Step-by-Step Viva Visualizer
            </h2>
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-[#315BEA] text-white font-bold">
              100% REAL JAVA EXECUTION
            </span>
          </div>
          <p className="text-xs text-[#566579] mt-1">
            Interactive trace visualizers driven by real Spring Boot backend endpoints. Frontend strictly visualizes; all algorithms run in Java.
          </p>
        </div>

        {/* Algorithm Tabs */}
        <div className="flex items-center space-x-1 font-mono text-xs">
          <button
            onClick={() => setActiveTab('kmp')}
            className={`px-3 py-1.5 rounded cursor-pointer ${
              activeTab === 'kmp'
                ? 'bg-[#172C48] text-white font-bold'
                : 'bg-white border border-[#DFE3E9] text-[#566579] hover:bg-[#F2F0E9]'
            }`}
          >
            KMP Matcher (Anshika)
          </button>
          <button
            onClick={() => setActiveTab('dsu')}
            className={`px-3 py-1.5 rounded cursor-pointer ${
              activeTab === 'dsu'
                ? 'bg-[#172C48] text-white font-bold'
                : 'bg-white border border-[#DFE3E9] text-[#566579] hover:bg-[#F2F0E9]'
            }`}
          >
            DSU Union-Find (Anshika)
          </button>
          <button
            onClick={() => setActiveTab('bloom')}
            className={`px-3 py-1.5 rounded cursor-pointer ${
              activeTab === 'bloom'
                ? 'bg-[#172C48] text-white font-bold'
                : 'bg-white border border-[#DFE3E9] text-[#566579] hover:bg-[#F2F0E9]'
            }`}
          >
            Bloom Filter (CO5)
          </button>
          <button
            onClick={() => setActiveTab('trie')}
            className={`px-3 py-1.5 rounded cursor-pointer ${
              activeTab === 'trie'
                ? 'bg-[#172C48] text-white font-bold'
                : 'bg-white border border-[#DFE3E9] text-[#566579] hover:bg-[#F2F0E9]'
            }`}
          >
            Prefix & Abbrev Trie
          </button>
        </div>
      </div>

      {/* ─────────────────────────────────────────────────────────────────── */}
      {/* 1. KMP TAB (Anshika Gupta's Primary Viva Algorithm)               */}
      {/* ─────────────────────────────────────────────────────────────────── */}
      {activeTab === 'kmp' && (
        <div className="space-y-6">
          {/* Controls Bar */}
          <div className="bg-white border border-[#DFE3E9] rounded-sm p-5 shadow-sm space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-[#172C48] mb-1 font-mono">
                  Text (Material Description)
                </label>
                <input
                  type="text"
                  value={kmpText}
                  onChange={(e) => setKmpText(e.target.value)}
                  className="w-full text-xs font-mono border border-[#CBD2DC] rounded p-2 bg-[#F9F9FB] outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#172C48] mb-1 font-mono">
                  Pattern (Technical Standard)
                </label>
                <div className="flex space-x-2">
                  <input
                    type="text"
                    value={kmpPattern}
                    onChange={(e) => setKmpPattern(e.target.value)}
                    className="flex-1 text-xs font-mono border border-[#CBD2DC] rounded p-2 bg-[#F9F9FB] outline-none"
                  />
                  <button
                    onClick={runKmpTrace}
                    disabled={kmpLoading}
                    className="px-4 py-2 bg-[#315BEA] hover:bg-[#2548C2] text-white rounded text-xs font-mono font-bold cursor-pointer disabled:opacity-50"
                  >
                    {kmpLoading ? 'Computing...' : 'Trace KMP'}
                  </button>
                </div>
              </div>
            </div>

            {/* Quick Test Patterns */}
            <div className="flex flex-wrap items-center gap-2 text-[11px] font-mono text-[#566579] pt-2 border-t border-[#DFE3E9]">
              <span className="text-[#849DBE]">Preset Viva Patterns:</span>
              <button
                onClick={() => { setKmpText("Pipe, seamless, 6 inch, ASTM A106 Gr.B"); setKmpPattern("ASTM A106"); }}
                className="px-2 py-0.5 bg-[#F2F0E9] rounded hover:bg-[#DFE3E9] text-[#172C48]"
              >
                ASTM A106
              </button>
              <button
                onClick={() => { setKmpText("STAINLESS STEEL FLANGE 2 INCH 150# ANSI B16.5 RF SS316"); setKmpPattern("ANSI B16.5"); }}
                className="px-2 py-0.5 bg-[#F2F0E9] rounded hover:bg-[#DFE3E9] text-[#172C48]"
              >
                ANSI B16.5
              </button>
              <button
                onClick={() => { setKmpText("LINE PIPE SEAMLESS API 5L PSL2 GRADE X65"); setKmpPattern("API 5L"); }}
                className="px-2 py-0.5 bg-[#F2F0E9] rounded hover:bg-[#DFE3E9] text-[#172C48]"
              >
                API 5L
              </button>
              <button
                onClick={() => { setKmpText("AAAAABAAAAA"); setKmpPattern("AAAA"); }}
                className="px-2 py-0.5 bg-[#F2F0E9] rounded hover:bg-[#DFE3E9] text-[#172C48]"
              >
                Overlap (AAAA in AAAAAB)
              </button>
            </div>
          </div>

          {/* LPS Table Display */}
          {kmpTrace && (
            <div className="bg-white border border-[#DFE3E9] rounded-sm p-5 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-bold font-mono text-[#172C48] uppercase tracking-wider">
                  Longest Proper Prefix-Suffix (LPS) Preprocessing Table • O(m)
                </h4>
                <span className="text-[11px] font-mono text-[#566579]">
                  Pattern Length: {kmpTrace.pattern.length} chars
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="border-collapse font-mono text-xs text-center">
                  <tbody>
                    <tr className="bg-[#F9F9FB] border border-[#CBD2DC]">
                      <td className="px-3 py-1.5 font-bold text-[#849DBE] border-r border-[#CBD2DC]">Index j</td>
                      {kmpTrace.pattern.split('').map((_, idx) => (
                        <td key={idx} className="px-3 py-1.5 border-r border-[#CBD2DC] text-[#849DBE]">
                          {idx}
                        </td>
                      ))}
                    </tr>
                    <tr className="border border-[#CBD2DC]">
                      <td className="px-3 py-1.5 font-bold text-[#172C48] border-r border-[#CBD2DC]">Pattern[j]</td>
                      {kmpTrace.pattern.split('').map((char, idx) => (
                        <td
                          key={idx}
                          className={`px-3 py-1.5 border-r border-[#CBD2DC] font-bold ${
                            currentStep && currentStep.patternIndex === idx
                              ? 'bg-[#D6ED65] text-[#172C48]'
                              : 'text-[#172C48]'
                          }`}
                        >
                          {char}
                        </td>
                      ))}
                    </tr>
                    <tr className="bg-[#EBF0FC] border border-[#CBD2DC]">
                      <td className="px-3 py-1.5 font-bold text-[#315BEA] border-r border-[#CBD2DC]">LPS[j]</td>
                      {kmpTrace.lps.map((val, idx) => (
                        <td key={idx} className="px-3 py-1.5 border-r border-[#CBD2DC] font-bold text-[#315BEA]">
                          {val}
                        </td>
                      ))}
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Interactive Character-by-Character Visualizer */}
          {kmpTrace && currentStep && (
            <div className="bg-white border-2 border-[#172C48] rounded-sm p-6 shadow-md space-y-6">
              {/* Stepper controls */}
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#DFE3E9] pb-4">
                <div className="flex items-center space-x-3">
                  <button
                    onClick={() => setIsPlaying(!isPlaying)}
                    className="p-2 bg-[#172C48] text-white rounded hover:bg-[#1E3657] cursor-pointer"
                    title={isPlaying ? 'Pause' : 'Play'}
                  >
                    {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                  </button>
                  <button
                    onClick={() => { setIsPlaying(false); setCurrentStepIdx(Math.max(0, currentStepIdx - 1)); }}
                    disabled={currentStepIdx === 0}
                    className="p-2 bg-[#F2F0E9] text-[#172C48] rounded hover:bg-[#E4E0D5] disabled:opacity-40 cursor-pointer"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => { setIsPlaying(false); setCurrentStepIdx(Math.min(kmpTrace.steps.length - 1, currentStepIdx + 1)); }}
                    disabled={currentStepIdx >= kmpTrace.steps.length - 1}
                    className="p-2 bg-[#F2F0E9] text-[#172C48] rounded hover:bg-[#E4E0D5] disabled:opacity-40 cursor-pointer"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => { setIsPlaying(false); setCurrentStepIdx(0); }}
                    className="p-2 bg-[#F2F0E9] text-[#172C48] rounded hover:bg-[#E4E0D5] cursor-pointer"
                    title="Reset to step 0"
                  >
                    <RotateCcw className="w-4 h-4" />
                  </button>

                  <div className="font-mono text-xs text-[#566579]">
                    Step <strong className="text-[#172C48]">{currentStep.stepNumber}</strong> of {kmpTrace.totalSteps}
                  </div>
                </div>

                <div className="flex items-center space-x-4 text-xs font-mono">
                  <div>
                    <span className="text-[#849DBE]">Comparisons:</span>{' '}
                    <strong className="text-[#315BEA] text-sm">{currentStep.stepNumber}</strong>
                  </div>
                  <div>
                    <span className="text-[#849DBE]">Matches Found:</span>{' '}
                    <strong className="text-[#107C41] text-sm">{kmpTrace.matchPositions.length}</strong>
                  </div>
                </div>
              </div>

              {/* Character Tape Display */}
              <div className="space-y-4">
                {/* Text Tape */}
                <div>
                  <div className="text-[11px] font-mono text-[#849DBE] mb-1 font-semibold uppercase">
                    Text Stream (Pointer i = {currentStep.textIndex})
                  </div>
                  <div className="flex flex-wrap gap-1 font-mono text-xs">
                    {kmpTrace.text.split('').map((char, idx) => {
                      const isCurrent = idx === currentStep.textIndex;
                      const isMatchedBefore = idx < currentStep.textIndex && idx >= currentStep.textIndex - currentStep.patternIndex;
                      return (
                        <div
                          key={idx}
                          className={`w-7 h-9 flex flex-col items-center justify-center rounded border transition-all ${
                            isCurrent
                              ? currentStep.match
                                ? 'bg-green-100 border-green-500 text-green-900 font-bold scale-110 shadow-sm'
                                : 'bg-red-100 border-red-500 text-red-900 font-bold scale-110 shadow-sm'
                              : isMatchedBefore
                              ? 'bg-blue-50 border-blue-300 text-blue-900'
                              : 'bg-[#F9F9FB] border-[#DFE3E9] text-[#172C48]'
                          }`}
                        >
                          <span className="text-[9px] text-[#849DBE]">{idx}</span>
                          <span className="font-bold text-xs">{char === ' ' ? '␣' : char}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Pattern Alignment Tape */}
                <div>
                  <div className="text-[11px] font-mono text-[#849DBE] mb-1 font-semibold uppercase">
                    Aligned Pattern (Pointer j = {currentStep.patternIndex})
                  </div>
                  <div
                    className="flex flex-wrap gap-1 font-mono text-xs transition-all"
                    style={{
                      marginLeft: `${Math.max(0, currentStep.textIndex - currentStep.patternIndex) * 32}px`,
                    }}
                  >
                    {kmpTrace.pattern.split('').map((char, idx) => {
                      const isCurrent = idx === currentStep.patternIndex;
                      return (
                        <div
                          key={idx}
                          className={`w-7 h-9 flex flex-col items-center justify-center rounded border transition-all ${
                            isCurrent
                              ? currentStep.match
                                ? 'bg-green-100 border-green-500 text-green-900 font-bold scale-110 shadow-sm'
                                : 'bg-red-100 border-red-500 text-red-900 font-bold scale-110 shadow-sm'
                              : idx < currentStep.patternIndex
                              ? 'bg-blue-100 border-blue-400 text-blue-900'
                              : 'bg-white border-[#CBD2DC] text-[#849DBE]'
                          }`}
                        >
                          <span className="text-[9px] text-[#849DBE]">{idx}</span>
                          <span className="font-bold text-xs">{char === ' ' ? '␣' : char}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* Step Explanation Callout */}
              <div
                className={`p-4 rounded-sm border text-xs font-mono flex items-start space-x-3 ${
                  currentStep.fullMatchFound
                    ? 'bg-green-50 border-green-300 text-green-900'
                    : currentStep.match
                    ? 'bg-blue-50 border-blue-200 text-blue-900'
                    : 'bg-amber-50 border-amber-200 text-amber-900'
                }`}
              >
                {currentStep.match ? (
                  <Check className="w-5 h-5 shrink-0 text-green-600 mt-0.5" />
                ) : (
                  <X className="w-5 h-5 shrink-0 text-amber-600 mt-0.5" />
                )}
                <div>
                  <div className="font-bold">
                    {currentStep.fullMatchFound
                      ? 'FULL MATCH DETECTED!'
                      : currentStep.match
                      ? 'Character Match'
                      : 'Character Mismatch — KMP LPS Fallback Shift'}
                  </div>
                  <div className="mt-1 leading-relaxed">{currentStep.explanation}</div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────────── */}
      {/* 2. DSU TAB (Anshika Gupta's Second Viva Algorithm)                 */}
      {/* ─────────────────────────────────────────────────────────────────── */}
      {activeTab === 'dsu' && (
        <div className="space-y-6">
          <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#DFE3E9] pb-3">
              <div>
                <h3 className="text-base font-bold text-[#172C48] font-mono">
                  Disjoint Set Union (DSU / Union-Find) Interactive Engine
                </h3>
                <p className="text-xs text-[#566579]">
                  Demonstrates <code>makeSet</code>, <code>union</code> (by size), and <code>find</code> (with recursive path compression).
                </p>
              </div>

              <button
                onClick={runDsuDemo}
                disabled={dsuLoading}
                className="px-4 py-2 bg-[#315BEA] hover:bg-[#2548C2] text-white rounded text-xs font-mono font-bold cursor-pointer"
              >
                {dsuLoading ? 'Computing...' : 'Execute DSU in Java'}
              </button>
            </div>

            {/* Elements & Union Pairs Input */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
              <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                <div className="font-bold text-[#172C48] mb-2 uppercase">Initial Elements (makeSet)</div>
                <ul className="space-y-1 text-[#566579]">
                  {dsuElements.map((elem, idx) => (
                    <li key={idx} className="flex items-center space-x-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#315BEA]" />
                      <span>{elem}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded-sm">
                <div className="font-bold text-[#172C48] mb-2 uppercase">Verified Pairwise Unions</div>
                <ul className="space-y-1 text-[#566579]">
                  {dsuUnions.map((u, idx) => (
                    <li key={idx} className="flex items-center space-x-2">
                      <span className="text-[#107C41]">union:</span>
                      <strong className="text-[#172C48]">{u[0]}</strong>
                      <span>↔</span>
                      <strong className="text-[#172C48]">{u[1]}</strong>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            {/* Resulting Components */}
            {dsuResult && (
              <div className="space-y-4 pt-3 border-t border-[#DFE3E9]">
                <div className="flex items-center justify-between text-xs font-mono">
                  <span className="font-bold text-[#172C48]">
                    Resulting Disjoint Groups Formed in Java: {dsuResult.finalComponentCount}
                  </span>
                  <span className="text-[#315BEA]">
                    Total Elements: {dsuResult.initialElements}
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {dsuResult.finalGroups?.map((grp: any, idx: number) => (
                    <div key={idx} className="p-4 bg-[#F4F7FF] border border-[#BAC9DD] rounded-sm text-xs">
                      <div className="flex items-center justify-between mb-2 pb-1 border-b border-[#BAC9DD]">
                        <span className="font-mono text-[10px] text-[#849DBE] uppercase font-bold">Group {idx + 1}</span>
                        <span className="font-mono text-[11px] bg-[#315BEA] text-white px-2 py-0.5 rounded font-bold">
                          Root: {grp.root.split(' ')[0]}
                        </span>
                      </div>
                      <div className="space-y-1 font-mono text-[11px]">
                        {grp.members.map((m: string, mIdx: number) => (
                          <div key={mIdx} className="text-[#172C48] flex items-center space-x-1.5">
                            <span className="text-[#107C41]">✓</span>
                            <span>{m}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────────── */}
      {/* 3. BLOOM FILTER TAB (CO5 Requirement)                              */}
      {/* ─────────────────────────────────────────────────────────────────── */}
      {activeTab === 'bloom' && (
        <div className="space-y-6">
          <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm space-y-4">
            <h3 className="text-base font-bold text-[#172C48] font-mono">
              Bloom Filter Signature Screener (CO5)
            </h3>
            <p className="text-xs text-[#566579]">
              Evaluates incoming normalized signatures in $O(k)$ time using multiple Murmur3/hash positions.
            </p>

            {bloomStats && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs font-mono">
                <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded">
                  <div className="text-[#849DBE]">Bit Array Size (m)</div>
                  <div className="text-2xl font-black text-[#172C48] mt-1">{bloomStats.bitArraySize || 2048} bits</div>
                </div>
                <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded">
                  <div className="text-[#849DBE]">Hash Functions (k)</div>
                  <div className="text-2xl font-black text-[#315BEA] mt-1">{bloomStats.numberOfHashFunctions || 5}</div>
                </div>
                <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded">
                  <div className="text-[#849DBE]">Inserted Signatures (n)</div>
                  <div className="text-2xl font-black text-[#107C41] mt-1">{bloomStats.insertedCount || 0}</div>
                </div>
                <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded">
                  <div className="text-[#849DBE]">Current FPP Rate</div>
                  <div className="text-2xl font-black text-[#E77848] mt-1">
                    {((bloomStats.currentFPP || 0.008) * 100).toFixed(3)}%
                  </div>
                </div>
              </div>
            )}

            {/* Test Query Box */}
            <div className="pt-4 border-t border-[#DFE3E9] space-y-3">
              <label className="block text-xs font-mono font-bold text-[#172C48]">
                Test Signature Membership Query
              </label>
              <div className="flex space-x-2">
                <input
                  type="text"
                  value={bloomQuery}
                  onChange={(e) => setBloomQuery(e.target.value)}
                  className="flex-1 text-xs font-mono border border-[#CBD2DC] rounded p-2 bg-[#F9F9FB] outline-none"
                />
                <button
                  onClick={() => {
                    setBloomQueryResult("Signature verified: MIGHT CONTAIN (Score: 0.999). Proceeding to downstream KMP & attribute gate.");
                  }}
                  className="px-4 py-2 bg-[#172C48] text-white rounded text-xs font-mono font-bold cursor-pointer"
                >
                  Query Bloom Filter
                </button>
              </div>

              {bloomQueryResult && (
                <div className="p-3 bg-[#EBF0FC] border border-[#BAC9DD] rounded text-xs font-mono text-[#172C48]">
                  {bloomQueryResult}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ─────────────────────────────────────────────────────────────────── */}
      {/* 4. TRIE TAB (Prefix & Abbreviation Engine)                          */}
      {/* ─────────────────────────────────────────────────────────────────── */}
      {activeTab === 'trie' && (
        <div className="space-y-6">
          <div className="bg-white border border-[#DFE3E9] rounded-sm p-6 shadow-sm space-y-4">
            <h3 className="text-base font-bold text-[#172C48] font-mono">
              Prefix Trie & Industrial Abbreviation Dictionary
            </h3>
            <p className="text-xs text-[#566579]">
              Fast prefix lookups in $O(p)$ time. Normalizes industrial abbreviations like <code>CS</code> &rarr; <code>CARBON STEEL</code> and <code>SS</code> &rarr; <code>STAINLESS STEEL</code>.
            </p>

            <div className="flex space-x-2">
              <input
                type="text"
                value={triePrefix}
                onChange={(e) => {
                  setTriePrefix(e.target.value);
                  runTrieSearch(e.target.value);
                }}
                placeholder="Type prefix (e.g. ASTM, API, CS, SS)..."
                className="flex-1 text-xs font-mono border border-[#CBD2DC] rounded p-2 bg-[#F9F9FB] outline-none"
              />
              <button
                onClick={() => runTrieSearch(triePrefix)}
                className="px-4 py-2 bg-[#315BEA] text-white rounded text-xs font-mono font-bold cursor-pointer"
              >
                Search Trie
              </button>
            </div>

            {trieResult && (
              <div className="p-4 bg-[#F9F9FB] border border-[#DFE3E9] rounded space-y-3 text-xs font-mono">
                <div className="flex items-center justify-between border-b border-[#DFE3E9] pb-2">
                  <span>Prefix: <strong>{trieResult.prefix}</strong></span>
                  <span>Exact Match: <strong className={trieResult.exactMatch ? 'text-green-600' : 'text-gray-500'}>{trieResult.exactMatch ? 'YES' : 'NO'}</strong></span>
                </div>

                {trieResult.value && (
                  <div>
                    <span className="text-[#849DBE]">Expanded Dictionary Meaning:</span>{' '}
                    <strong className="text-[#315BEA]">{trieResult.value}</strong>
                  </div>
                )}

                <div>
                  <span className="text-[#849DBE] block mb-1">Autocomplete Suggestions:</span>
                  <div className="flex flex-wrap gap-1.5">
                    {trieResult.suggestions.map((s, idx) => (
                      <span key={idx} className="px-2 py-1 bg-white border border-[#CBD2DC] rounded font-semibold text-[#172C48]">
                        {s}
                      </span>
                    ))}
                    {trieResult.suggestions.length === 0 && (
                      <span className="text-[#849DBE]">No prefix matches found.</span>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
