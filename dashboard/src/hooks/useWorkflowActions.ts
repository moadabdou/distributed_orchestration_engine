import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useToast } from '../components/ToastProvider';
import {
  executeWorkflow,
  pauseWorkflow,
  resumeWorkflow,
  resetWorkflow,
  deleteWorkflow,
} from '../api/workflows';

export const useWorkflowActions = (workflowId: string) => {
  const queryClient = useQueryClient();
  const { showToast, updateToast } = useToast();

  const invalidateQueries = () => {
    queryClient.invalidateQueries({ queryKey: ['workflowsList'] });
    queryClient.invalidateQueries({ queryKey: ['workflowDag', workflowId] });
    queryClient.invalidateQueries({ queryKey: ['workflow', workflowId] });
    queryClient.invalidateQueries({ queryKey: ['systemStats'] });
    queryClient.invalidateQueries({ queryKey: ['activityFeed'] });
  };

  const handleAction = async <T,>(
    actionFn: () => Promise<T>,
    actionName: string
  ) => {
    const toastId = showToast(`${actionName} workflow...`, 'loading');
    try {
      const result = await actionFn();
      invalidateQueries();
      updateToast(toastId, `Workflow ${actionName.toLowerCase()}d successfully`, 'success');
      return result;
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message || 'Unknown error';
      updateToast(toastId, `Failed to ${actionName.toLowerCase()}: ${msg}`, 'error', 5000);
      throw error;
    }
  };

  const executeMutation = useMutation({
    mutationFn: () => handleAction(() => executeWorkflow(workflowId), 'Execute'),
  });

  const pauseMutation = useMutation({
    mutationFn: () => handleAction(() => pauseWorkflow(workflowId), 'Pause'),
  });

  const resumeMutation = useMutation({
    mutationFn: () => handleAction(() => resumeWorkflow(workflowId), 'Resume'),
  });

  const resetMutation = useMutation({
    mutationFn: () => handleAction(() => resetWorkflow(workflowId), 'Reset'),
  });

  const removeMutation = useMutation({
    mutationFn: () => handleAction(() => deleteWorkflow(workflowId), 'Delete'),
  });

  return {
    execute: executeMutation.mutateAsync,
    pause: pauseMutation.mutateAsync,
    resume: resumeMutation.mutateAsync,
    reset: resetMutation.mutateAsync,
    remove: removeMutation.mutateAsync,
    
    isExecuting: executeMutation.isPending,
    isPausing: pauseMutation.isPending,
    isResuming: resumeMutation.isPending,
    isResetting: resetMutation.isPending,
    isRemoving: removeMutation.isPending,
    
    executeError: executeMutation.error,
    pauseError: pauseMutation.error,
    resumeError: resumeMutation.error,
    resetError: resetMutation.error,
    removeError: removeMutation.error,
  };
};