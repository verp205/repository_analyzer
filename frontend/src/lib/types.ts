export type AnalysisTaskStatus =
  | "NEW"
  | "CLONING"
  | "DISCOVERING_COMMITS"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type ReportAudience =
  | "DEVELOPER"
  | "ANALYST"
  | "SECURITY"
  | "TEAM_LEAD"
  | "DIRECTOR";

export type ReportStatus =
  | "NEW"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export interface AnalysisTask {
  id: number;
  repositoryUrl: string;
  branch: string;
  status: AnalysisTaskStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisProgress {
  analysisTaskId: number;
  status: AnalysisTaskStatus;
  totalCommits: number;
  newCommits: number;
  processingCommits: number;
  completedCommits: number;
  failedCommits: number;
}

export interface CommitGraphNode {
  id: number;
  commitHash: string;
  message: string | null;
  authorName: string | null;
  committedAt: string;
  processingStatus: "NEW" | "PROCESSING" | "COMPLETED" | "FAILED";
}

export interface CommitGraphEdge {
  parentHash: string;
  childHash: string;
  parentPosition: number;
}

export interface CommitGraph {
  analysisTaskId: number;
  totalCommits: number;
  rootCount: number;
  tipCount: number;
  mergeCommitCount: number;
  maxDepth: number;
  missingParentCount: number;
  nodes: CommitGraphNode[];
  edges: CommitGraphEdge[];
}

export interface ClusteredCommit {
  id: number;
  commitHash: string;
  message: string | null;
  changedFiles: number;
  addedLines: number;
  deletedLines: number;
}

export interface ChangeCluster {
  type: string;
  displayName: string;
  commits: ClusteredCommit[];
}

export interface CommitClusters {
  analysisTaskId: number;
  clusteredCommits: number;
  clusters: ChangeCluster[];
}

export interface AnalysisReport {
  id: number;
  analysisTaskId: number;
  audience: ReportAudience;
  status: ReportStatus;
  totalCommits: number;
  processedCommits: number;
  content: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReportEvent {
  type: string;
  reportId: number;
  processedCommits: number;
  totalCommits: number;
  message: string;
  contentChunk: string | null;
  timestamp: string;
}
