import type { FieldMapping, Organization } from '../types';

export interface Account {
  id: string;
  name: string;
  email: string;
  reviewer: boolean;
}

export interface Session {
  user: Account | null;
  csrfToken: string;
}

export interface Material {
  id: number;
  cpse: string;
  code: string;
  description: string;
  uom: string;
}

export interface CatalogueSource {
  material: Material;
  batchId: string;
  row: number;
  columns: Record<string, string>;
  category: string;
  grade: string;
  dimensions: string;
  specification: string;
  materialType: string;
}

export interface Batch {
  id: string;
  fileName: string;
  organizationCode: string;
  uploadDate: string;
  recordCount: number;
  validRows: number;
  name: string;
  period: string;
  reference: string;
  bytes: number;
  warnings: string[];
}

export type ProposalStatus = 'POSSIBLE' | 'INSUFFICIENT' | 'CONFLICT' | 'CONFIRMED' | 'SEPARATED';
export type ReviewAction = 'APPROVE' | 'SEPARATE' | 'CLARIFY';
export interface Proposal {
  id: string;
  recordA: number;
  recordB: number;
  status: ProposalStatus;
  clarified: boolean;
  evidence: {
    attributes: Array<{ name: string; left: string; right: string; state: 'MATCH' | 'CONFLICT' | 'UNKNOWN' }>;
    sharedSpecifications: string[];
    approvable: boolean;
    summary: string;
  };
}

export interface Workspace {
  organizations: Array<Pick<Organization, 'code' | 'name' | 'type' | 'contactEmail' | 'location'>>;
  records: CatalogueSource[];
  batches: Batch[];
  proposals: Proposal[];
  registry: Array<{
    group: { id: string; nationalCode: string; members: Material[] };
    issuedAt: string;
    updatedAt: string;
  }>;
  decisions: Array<{
    id: string;
    proposalId: string;
    recordA: number;
    recordB: number;
    reviewerName: string;
    action: ReviewAction;
    rationale: string;
    timestamp: string;
  }>;
  run: { timestamp: string; records: number; comparisons: number } | null;
}

export interface CatalogueOptions {
  organization: string;
  name: string;
  period: string;
  reference: string;
  mapping: FieldMapping;
}

export const proposalLabels: Record<ProposalStatus, string> = {
  POSSIBLE: 'Possible Match',
  INSUFFICIENT: 'Insufficient Information',
  CONFLICT: 'Known Specification Conflict',
  CONFIRMED: 'Confirmed Match',
  SEPARATED: 'Rejected / Keep Separate',
};

export const isPending = (proposal: Proposal) => proposal.status !== 'SEPARATED' && proposal.status !== 'CONFIRMED';

export interface CatalogueValidation {
  totalRows: number;
  validRows: number;
  warnings: string[];
  preview: CatalogueSource[];
}
