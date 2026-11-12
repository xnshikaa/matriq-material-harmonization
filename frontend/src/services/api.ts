import {
  MaterialRecord,
  MaterialMatch,
  MaterialGroup,
  NationalMaterialCode,
  ImportBatch,
  HarmonizationResult,
  KMPResult,
  KMPTraceResponse,
  BloomStats,
  BenchmarkResult
} from '../types';

const API_BASE = '/api';

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const errorText = await res.text();
    let errorMessage = `HTTP ${res.status}: ${res.statusText}`;
    try {
      const parsed = JSON.parse(errorText);
      if (parsed.error || parsed.message) {
        errorMessage = parsed.error || parsed.message;
      }
    } catch {
      if (errorText) errorMessage = errorText;
    }
    throw new Error(errorMessage);
  }
  return res.json();
}

export const api = {
  // ── Materials ───────────────────────────────────────────────────────────────
  async getMaterials(company?: string, status?: string): Promise<MaterialRecord[]> {
    const params = new URLSearchParams();
    if (company) params.append('company', company);
    if (status) params.append('status', status);
    const qs = params.toString() ? `?${params.toString()}` : '';
    const res = await fetch(`${API_BASE}/materials${qs}`);
    return handleResponse<MaterialRecord[]>(res);
  },

  async getMaterialById(id: string): Promise<MaterialRecord> {
    const res = await fetch(`${API_BASE}/materials/${id}`);
    return handleResponse<MaterialRecord>(res);
  },

  async getMaterialStats(): Promise<Record<string, any>> {
    const res = await fetch(`${API_BASE}/materials/stats`);
    return handleResponse<Record<string, any>>(res);
  },

  async uploadCsv(file: File, companyName: string): Promise<ImportBatch> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('companyName', companyName);
    const res = await fetch(`${API_BASE}/materials/upload`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse<ImportBatch>(res);
  },

  async getImportBatches(): Promise<ImportBatch[]> {
    const res = await fetch(`${API_BASE}/materials/imports`);
    return handleResponse<ImportBatch[]>(res);
  },

  // ── Harmonization ───────────────────────────────────────────────────────────
  async runHarmonization(): Promise<HarmonizationResult> {
    const res = await fetch(`${API_BASE}/harmonization/run`, {
      method: 'POST',
    });
    return handleResponse<HarmonizationResult>(res);
  },

  async getHarmonizationResults(): Promise<Record<string, any>> {
    const res = await fetch(`${API_BASE}/harmonization/results`);
    return handleResponse<Record<string, any>>(res);
  },

  async getGroups(): Promise<MaterialGroup[]> {
    const res = await fetch(`${API_BASE}/harmonization/groups`);
    return handleResponse<MaterialGroup[]>(res);
  },

  async getGroupById(id: string): Promise<{ group: MaterialGroup; members: MaterialRecord[] }> {
    const res = await fetch(`${API_BASE}/harmonization/groups/${id}`);
    return handleResponse<{ group: MaterialGroup; members: MaterialRecord[] }>(res);
  },

  async getNetworkData(): Promise<{ nodes: any[]; edges: any[] }> {
    const res = await fetch(`${API_BASE}/harmonization/network`);
    return handleResponse<{ nodes: any[]; edges: any[] }>(res);
  },

  // ── Matching & Review ───────────────────────────────────────────────────────
  async getPendingMatches(): Promise<MaterialMatch[]> {
    const res = await fetch(`${API_BASE}/matching/pending`);
    return handleResponse<MaterialMatch[]>(res);
  },

  async getAllMatches(): Promise<MaterialMatch[]> {
    const res = await fetch(`${API_BASE}/matching`);
    return handleResponse<MaterialMatch[]>(res);
  },

  async approveMatch(id: string, reviewedBy = 'Reviewer', note = ''): Promise<MaterialMatch> {
    const res = await fetch(`${API_BASE}/matching/${id}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reviewedBy, note }),
    });
    return handleResponse<MaterialMatch>(res);
  },

  async rejectMatch(id: string, reviewedBy = 'Reviewer', note = ''): Promise<MaterialMatch> {
    const res = await fetch(`${API_BASE}/matching/${id}/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reviewedBy, note }),
    });
    return handleResponse<MaterialMatch>(res);
  },

  // ── National Codes ──────────────────────────────────────────────────────────
  async assignNationalCodes(): Promise<{ assigned: number; codes: NationalMaterialCode[] }> {
    const res = await fetch(`${API_BASE}/national-codes/assign`, {
      method: 'POST',
    });
    return handleResponse<{ assigned: number; codes: NationalMaterialCode[] }>(res);
  },

  async getNationalCodes(): Promise<NationalMaterialCode[]> {
    const res = await fetch(`${API_BASE}/national-codes`);
    return handleResponse<NationalMaterialCode[]>(res);
  },

  async getNationalCode(code: string): Promise<NationalMaterialCode> {
    const res = await fetch(`${API_BASE}/national-codes/${encodeURIComponent(code)}`);
    return handleResponse<NationalMaterialCode>(res);
  },

  // ── Algorithm Lab ───────────────────────────────────────────────────────────
  async kmpSearch(text: string, pattern: string): Promise<KMPResult> {
    const res = await fetch(`${API_BASE}/algorithms/kmp/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, pattern }),
    });
    return handleResponse<KMPResult>(res);
  },

  async kmpTrace(text: string, pattern: string): Promise<KMPTraceResponse> {
    const res = await fetch(`${API_BASE}/algorithms/kmp/trace`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text, pattern }),
    });
    return handleResponse<KMPTraceResponse>(res);
  },

  async dsuDemo(operations: Array<{ type: string; a?: string; b?: string }>): Promise<any> {
    const res = await fetch(`${API_BASE}/algorithms/dsu/demo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ operations }),
    });
    return handleResponse<any>(res);
  },

  async getBloomStats(): Promise<BloomStats> {
    const res = await fetch(`${API_BASE}/algorithms/bloom/statistics`);
    return handleResponse<BloomStats>(res);
  },

  async trieSearch(prefix: string): Promise<{ prefix: string; exactMatch: boolean; value: string; suggestions: string[] }> {
    const res = await fetch(`${API_BASE}/algorithms/trie/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prefix }),
    });
    return handleResponse<{ prefix: string; exactMatch: boolean; value: string; suggestions: string[] }>(res);
  },

  // ── Benchmarks ──────────────────────────────────────────────────────────────
  async runBenchmarks(): Promise<BenchmarkResult[]> {
    const res = await fetch(`${API_BASE}/benchmarks/run`, {
      method: 'POST',
    });
    return handleResponse<BenchmarkResult[]>(res);
  },

  async getBenchmarkResults(): Promise<BenchmarkResult[]> {
    const res = await fetch(`${API_BASE}/benchmarks/results`);
    return handleResponse<BenchmarkResult[]>(res);
  },
};
