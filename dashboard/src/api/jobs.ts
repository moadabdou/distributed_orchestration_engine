import { apiClient } from './client';
import type { Job, PaginatedResponse, CreateJobRequest } from '../types/api';

export const getJobs = async (
  page: number = 0,
  size: number = 20,
  status?: Job['status'],
  sort: string = 'createdAt,desc'
): Promise<PaginatedResponse<Job>> => {
  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
    sort: sort,
  });
  
  if (status) {
    params.append('status', status);
  }

  const response = await apiClient.get<PaginatedResponse<Job>>(`/jobs`, { params });
  return response.data;
};

export const getJob = async (id: string): Promise<Job> => {
  const response = await apiClient.get<Job>(`/jobs/${id}`);
  return response.data;
};

export const createJob = async (payload: CreateJobRequest): Promise<Job> => {
  const response = await apiClient.post<Job>('/jobs', payload);
  return response.data;
};

export const cancelJob = async (id: string): Promise<Job> => {
  const response = await apiClient.post<Job>(`/jobs/${id}/cancel`);
  return response.data;
};

export const getJobLogsUrl = (id: string): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1';
  return `${baseUrl}/logs/jobs/${id}`;
};
