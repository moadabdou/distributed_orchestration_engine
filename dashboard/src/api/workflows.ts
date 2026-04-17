import { apiClient } from './client';
import type { PaginatedResponse, Workflow, WorkflowSummary, DagGraph } from '../types/api';

export interface CreateWorkflowRequest {
  name: string;
}

export interface UpdateWorkflowRequest {
  name?: string;
  // allow specifying jobs/edges depending on backend interface
}

export const getWorkflows = async (
  page = 0,
  size = 20,
  status?: string,
  sort?: string
): Promise<PaginatedResponse<WorkflowSummary>> => {
  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  });

  if (status) params.append('status', status);
  if (sort) params.append('sort', sort);

  const response = await apiClient.get<PaginatedResponse<WorkflowSummary>>('/workflows', { params });
  return response.data;
};

export const getWorkflow = async (id: string): Promise<Workflow> => {
  const response = await apiClient.get<Workflow>(`/workflows/${id}`);
  return response.data;
};

export const createWorkflow = async (request: CreateWorkflowRequest): Promise<Workflow> => {
  const response = await apiClient.post<Workflow>('/workflows', request);
  return response.data;
};

export const updateWorkflow = async (id: string, request: UpdateWorkflowRequest): Promise<Workflow> => {
  const response = await apiClient.put<Workflow>(`/workflows/${id}`, request);
  return response.data;
};

export const deleteWorkflow = async (id: string): Promise<void> => {
  await apiClient.delete(`/workflows/${id}`);
};

export const executeWorkflow = async (id: string): Promise<Workflow> => {
  const response = await apiClient.post<Workflow>(`/workflows/${id}/execute`);
  return response.data;
};

export const pauseWorkflow = async (id: string): Promise<Workflow> => {
  const response = await apiClient.post<Workflow>(`/workflows/${id}/pause`);
  return response.data;
};

export const resumeWorkflow = async (id: string): Promise<Workflow> => {
  const response = await apiClient.post<Workflow>(`/workflows/${id}/resume`);
  return response.data;
};

export const resetWorkflow = async (id: string): Promise<Workflow> => {
  const response = await apiClient.post<Workflow>(`/workflows/${id}/reset`);
  return response.data;
};

export const getWorkflowDag = async (id: string): Promise<DagGraph> => {
  const response = await apiClient.get<DagGraph>(`/workflows/${id}/dag`);
  return response.data;
};
