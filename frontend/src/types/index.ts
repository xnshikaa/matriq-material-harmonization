export interface MaterialRecord {
  id: string;
  companyName: string;
  originalMaterialCode: string;
  originalDescription: string;
  normalizedDescription?: string;
  materialCategory?: string;
  materialType?: string;
  dimensions?: string;
  unit?: string;
  grade?: string;
  technicalSpecification?: string;
  manufacturer?: string;
  importBatchId?: string;
  nationalMaterialCode?: string;
  groupId?: string;
  status: 'IMPORTED' | 'NORMALIZED' | 'CANDIDATE' | 'GROUPED' | 'APPROVED' | 'REJECTED';
  importedAt?: string;
  extraAttributes?: Record<string, string>;
}

export interface AttributeVerification {
  categoryMatch: boolean;
  typeMatch: boolean;
  gradeMatch: boolean;
  dimensionMatch: boolean;
  specificationMatch: boolean;
  overallPass: boolean;
  failureReason?: string;
}

export interface MaterialMatch {
  id: string;
  recordAId: string;
  recordBId: string;
  recordA?: MaterialRecord;
  recordB?: MaterialRecord;
  matchedSpecifications: string[];
  attributeVerification: AttributeVerification;
  matchReason?: string;
  confidenceScore: number;
  status: 'PENDING' | 'AUTO_APPROVED' | 'APPROVED' | 'REJECTED';
  reviewedBy?: string;
  reviewNote?: string;
  proposedAt?: string;
  reviewedAt?: string;
}

export interface MaterialGroup {
  id: string;
  memberIds: string[];
  nationalCode?: string;
  description?: string;
  status: 'FORMED' | 'APPROVED' | 'REJECTED';
  approvedAt?: string;
}

export interface CompanyCodeMapping {
  companyName: string;
  originalMaterialCode: string;
  originalDescription: string;
}

export interface NationalMaterialCode {
  code: string;
  groupId: string;
  canonicalDescription?: string;
  materialCategory?: string;
  materialType?: string;
  grade?: string;
  technicalSpecification?: string;
  companyMappings: CompanyCodeMapping[];
  issuedAt?: string;
}

export interface ImportBatch {
  id: string;
  companyName: string;
  filename: string;
  totalRows: number;
  importedCount: number;
  duplicateCount: number;
  invalidCount: number;
  importedAt: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  validationWarnings?: string[];
}

export interface HarmonizationResult {
  totalRecords: number;
  normalizedRecords: number;
  candidateMatches: number;
  autoApprovedMatches: number;
  groupsFormed: number;
  executionDurationMs: number;
}

export interface KMPResult {
  pattern: string;
  match: boolean;
  positions: number[];
  comparisons: number;
  lps: number[];
  executionTimeNanos?: number;
}

export interface KMPStep {
  stepNumber: number;
  textIndex: number;
  patternIndex: number;
  textChar: string;
  patternChar: string;
  match: boolean;
  lpsFallback: boolean;
  newPatternIndex: number;
  explanation: string;
  fullMatchFound: boolean;
}

export interface KMPTraceResponse {
  text: string;
  pattern: string;
  lps: number[];
  matchPositions: number[];
  totalComparisons: number;
  totalSteps: number;
  steps: KMPStep[];
}

export interface BloomStats {
  bitArraySize: number;
  numberOfHashFunctions: number;
  insertedCount: number;
  setBitCount: number;
  currentFPP: number;
  falsePositivesTracked: number;
  message?: string;
}

export interface BenchmarkResult {
  id: string;
  algorithmName: string;
  description: string;
  datasetSize: number;
  executionTimeNanos: number;
  naiveExecutionTimeNanos?: number;
  comparisons?: number;
  operations?: number;
  executedAt: string;
  details?: Record<string, any>;
}
