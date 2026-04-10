export interface Job {
  id: string;
  status: 'PENDING' | 'ASSIGNED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  payload: string;
  result: string | null;
  workerId: string | null;
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
