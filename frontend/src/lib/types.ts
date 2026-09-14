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

export type RiskLevel = "HIGH" | "MEDIUM" | "LOW" | "NONE" | "UNKNOWN";

export type ReportPeriodType = "ALL" | "LAST_DAYS" | "SINCE_TAG" | "BETWEEN_TAGS";

export interface ReportPeriod {
  type: ReportPeriodType;
  days?: number | null;
  fromTag?: string | null;
  toTag?: string | null;
}

export interface ReportPeriodInfo extends ReportPeriod {
  label: string;
}

export interface GitTag {
  name: string;
  commitHash: string;
  committedAt: string;
}

export interface PullRequestRef {
  number: number;
  title: string;
  url: string | null;
  issueTitles: string[];
}

export type CommitInsightStatus =
  | "ANALYZED"
  | "TEMPLATE"
  | "UNSTRUCTURED"
  | "FAILED";

export type ChangeCategory =
  | "FEATURE"
  | "FIX"
  | "REFACTOR"
  | "PERFORMANCE"
  | "SECURITY"
  | "DATA"
  | "API"
  | "TESTS"
  | "BUILD"
  | "DOCS"
  | "CHORE"
  | "MERGE";

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
  repositoryUrl: string;
  branch: string;
  period: ReportPeriodInfo;
  audience: ReportAudience;
  status: ReportStatus;
  totalCommits: number;
  processedCommits: number;
  content: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommitInsightDetails {
  keyChanges: string[];
  behaviorImpact: string;
  risks: string[];
  securityNotes: string[];
  testNotes: string;
  checks: string[];
  pullRequest: PullRequestRef | null;
}

export interface CommitInsight {
  commitId: number;
  position: number;
  commitHash: string;
  message: string | null;
  authorName: string | null;
  committedAt: string;
  addedLines: number;
  deletedLines: number;
  changedFiles: number;
  status: CommitInsightStatus;
  category: ChangeCategory;
  categoryName: string;
  riskLevel: RiskLevel;
  summary: string;
  details: CommitInsightDetails;
}

export interface RepositoryFacts {
  analysisTaskId: number;
  repositoryUrl: string;
  repositoryName: string;
  branch: string;
  firstCommitAt: string | null;
  lastCommitAt: string | null;
  periodDays: number;
  activeDays: number;
  totalCommits: number;
  analyzedCommits: number;
  failedCommits: number;
  mergeCommits: number;
  addedLines: number;
  deletedLines: number;
  touchedFiles: number;
  authorCount: number;
  authors: {
    name: string;
    commits: number;
    addedLines: number;
    deletedLines: number;
    share: number;
  }[];
  activityGranularity: "DAY" | "WEEK" | "MONTH";
  activity: { period: string; commits: number }[];
  hotspots: {
    path: string;
    commits: number;
    addedLines: number;
    deletedLines: number;
  }[];
  fileTypes: { type: string; files: number; changes: number }[];
  largestCommits: {
    commitHash: string;
    message: string;
    addedLines: number;
    deletedLines: number;
    changedFiles: number;
  }[];
  risks: {
    high: number;
    medium: number;
    low: number;
    none: number;
    unknown: number;
  };
  categories: {
    category: ChangeCategory;
    displayName: string;
    commits: number;
  }[];
  insightCommits: number;
}

export interface ReportEvent {
  type: string;
  reportId: number;
  status: ReportStatus | null;
  processedCommits: number;
  totalCommits: number;
  message: string;
  commits: CommitInsight[];
  timestamp: string;
}
