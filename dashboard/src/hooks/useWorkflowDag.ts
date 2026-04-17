import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getWorkflowDag } from '../api/workflows';
import type { DagGraph } from '../types/api';

interface UseWorkflowDagResult {
  dag: DagGraph | undefined;
  isLoading: boolean;
  error: unknown;
  refetch: () => void;
}

export const useWorkflowDag = (workflowId: string): UseWorkflowDagResult => {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ['workflowDag', workflowId],
    queryFn: () => getWorkflowDag(workflowId),
    refetchInterval: 3000,
  });

  return {
    dag: query.data,
    isLoading: query.isLoading,
    error: query.error,
    refetch: () => {
      queryClient.invalidateQueries({ queryKey: ['workflowDag', workflowId] });
      query.refetch();
    },
  };
};
