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

// ── Integrated harmonizer pipeline (root Spring Boot app) ────────────────────
// These types mirror the Java records in harmonizer.matching / harmonizer.harmonization,
// which are proxied under /api/dataset and /api/harmonize by vite.config.ts.

export type AttributeState = 'MATCH' | 'CONFLICT' | 'UNKNOWN';

export interface HarmonizerRecord {
  id: number;
  cpse: string;
  code: string;
  description: string;
  uom: string;
}

export interface MaterialAttributes {
  itemType: string | null;
  form: string | null;
  metal: string | null;
  /** Jackson serialises the NaN "unknown size" marker as the string "NaN". */
  sizeMillimetres: number | string | null;
  specification: string | null;
  grade: string | null;
}

export interface SpecEvidence {
  specification: string;
  positionInA: number;
  positionInB: number;
  comparisonsA: number;
  comparisonsB: number;
}

export interface AttributeStates {
  type: AttributeState;
  form: AttributeState;
  metal: AttributeState;
  grade: AttributeState;
  dimensions: AttributeState;
  specification: AttributeState;
}

export interface AttributeChecks {
  type: boolean;
  form: boolean;
  metal: boolean;
  grade: boolean;
  dimensions: boolean;
  specification: boolean;
}

export interface HarmonizerEvidence {
  recordA: number;
  recordB: number;
  sharedSpecifications: SpecEvidence[];
  checks: AttributeChecks;
  attributeStates: AttributeStates;
  attributesA: MaterialAttributes;
  attributesB: MaterialAttributes;
  rejectionReason: string;
}

export interface HarmonizerEdge {
  recordA: number;
  recordB: number;
  evidence: HarmonizerEvidence;
}

export interface HarmonizerGroup {
  id: string;
  materialFingerprint: string;
  nationalCode: string | null;
  members: HarmonizerRecord[];
}

export interface HarmonizerResult {
  totalRecords: number;
  candidatePairs: number;
  verifiedPairs: number;
  rejectedPairs: number;
  suppressedPairs: number;
  splitComponents: number;
  kmpComparisons: number;
  groupCount: number;
  groupedRecords: number;
  verifiedEdges: HarmonizerEdge[];
  rejectedCandidates: HarmonizerEvidence[];
  groups: HarmonizerGroup[];
  attributesByRecord: Record<string, MaterialAttributes>;
  elapsedMillis: number;
}

export interface DatasetSummary {
  recordsLoaded: number;
  recordsPerCpse: Record<string, number>;
  rowsSkipped: number;
  warnings: string[];
}

// ── Enterprise Platform Extensions ──────────────────────────────────────────

export interface Organization {
  id: string;
  name: string;
  code: string;
  type: string;
  recordCount: number;
  lastUploadDate: string;
  status: 'ACTIVE' | 'INACTIVE';
  contactEmail?: string;
  location?: string;
}

export interface CatalogueDetails {
  organizationId: string;
  catalogueName: string;
  reportingPeriod: string;
  sourceReference?: string;
}

export interface FieldMapping {
  itemCode: string;
  description: string;
  category: string;
  materialType: string;
  grade: string;
  dimensions: string;
  unit: string;
  specification: string;
}

export interface FileValidationSummary {
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateCodes: number;
  missingFields: number;
  unrecognizedValues: number;
  warnings: string[];
  fieldMapping: Record<string, string>;
}

export interface AuditEvent {
  id: string;
  timestamp: string;
  action: 'CATALOGUE_IMPORT' | 'HARMONIZATION_RUN' | 'MATCH_APPROVED' | 'MATCH_REJECTED' | 'SEPARATION_ENFORCED' | 'REGISTRY_ASSIGNED' | 'IDENTITY_UPDATED' | 'ORGANIZATION_REGISTERED';
  actor: string;
  actorRole: string;
  affectedItems: string;
  details: string;
  rationale?: string;
}

export interface ReviewDecision {
  matchId: string;
  decision: 'APPROVE' | 'KEEP_SEPARATE' | 'REQUEST_CLARIFICATION';
  reviewer: string;
  rationale: string;
  timestamp: string;
  overriddenConflict?: boolean;
}
