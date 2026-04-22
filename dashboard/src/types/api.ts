export interface Job {
  id: string;
  status: 'PENDING' | 'ASSIGNED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'SKIPPED';
  payload: string;
  result: string | null;
  workerId: string | null;
  workflowId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Worker {
  id: string;
  hostname: string;
  ipAddress: string;
  status: 'ONLINE' | 'OFFLINE';
  lastHeartbeat: string;
  maxCapacity: number;
  activeJobCount: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateJobRequest {
  payload: string;
}

export interface ApiError {
  message: string;
  status?: number;
  code?: string;
  data?: unknown;
}

export interface Workflow {
  id: string;
  name: string;
  status: 'DRAFT' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED';
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  pendingJobs: number;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowSummary {
  id: string;
  name: string;
  status: Workflow['status'];
  totalJobs: number;
  createdAt: string;
}

export interface DagNode {
  jobId: string;
  label: string;
  dagIndex: number;
  status: Job['status'];
  payload: string;
  result: string | null;
  workerId: string | null;
  timeoutMs: number;
  jobLabel: string | null;
  createdAt: string;
  updatedAt: string;
  position?: { x: number; y: number };
}

export interface DagEdge {
  sourceJobId: string;
  targetJobId: string;
}

export interface DagGraph {
  workflowId: string;
  workflowName: string;
  workflowStatus: Workflow['status'];
  nodes: DagNode[];
  edges: DagEdge[];
  dataEdges: DagEdge[];
}
