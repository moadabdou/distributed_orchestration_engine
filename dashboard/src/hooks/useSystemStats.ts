import { useQuery } from '@tanstack/react-query';
import { getJobs } from '../api/jobs';
import { getWorkers } from '../api/workers';
import { getWorkflows } from '../api/workflows';

export const useSystemStats = () => {
  return useQuery({
    queryKey: ['systemStats'],
    queryFn: async () => {
      // Concurrently fetch counts via size=1 limit trick to reduce payload
      const [workers, totalJobs, pendingJobs, runningJobs, completedJobs, failedJobs, cancelledJobs, totalWorkflows, activeWorkflows] = await Promise.all([
        getWorkers(),
        getJobs(0, 1),
        getJobs(0, 1, 'PENDING'),
        getJobs(0, 1, 'RUNNING'),
        getJobs(0, 1, 'COMPLETED'),
        getJobs(0, 1, 'FAILED'),
        getJobs(0, 1, 'CANCELLED'),
        getWorkflows(0, 1),
        getWorkflows(0, 1, 'RUNNING')
      ]);

      const activeWorkers = workers.filter(w => w.status === 'ONLINE');
      
      const completedCount = completedJobs.totalElements;
      const failedCount = failedJobs.totalElements;
      const cancelledCount = cancelledJobs.totalElements;
      const completionRate = (completedCount + failedCount + cancelledCount) === 0 
        ? 100 // if no jobs have finished/failed/cancelled, they haven't failed essentially.
        : Math.round((completedCount / (completedCount + failedCount + cancelledCount)) * 100);

      return {
        totalWorkers: workers.length,
        activeWorkers: activeWorkers.length,
        totalJobs: totalJobs.totalElements,
        pendingJobs: pendingJobs.totalElements,
        runningJobs: runningJobs.totalElements,
        completedJobs: completedCount,
        failedJobs: failedCount,
        cancelledJobs: cancelledCount,
        totalWorkflows: totalWorkflows.totalElements,
        activeWorkflows: activeWorkflows.totalElements,
        completionRate
      };
    },
    refetchInterval: 3000, // Update dashboard metrics every 3 seconds
  });
};
