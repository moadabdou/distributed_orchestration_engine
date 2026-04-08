import { useQuery } from '@tanstack/react-query';
import { getJobs } from '../api/jobs';
import { getWorkers } from '../api/workers';

export const useSystemStats = () => {
  return useQuery({
    queryKey: ['systemStats'],
    queryFn: async () => {
      // Concurrently fetch counts via size=1 limit trick to reduce payload
      const [workers, totalJobs, pendingJobs, runningJobs, completedJobs, failedJobs] = await Promise.all([
        getWorkers(),
        getJobs(0, 1),
        getJobs(0, 1, 'PENDING'),
        getJobs(0, 1, 'RUNNING'),
        getJobs(0, 1, 'COMPLETED'),
        getJobs(0, 1, 'FAILED')
      ]);

      const activeWorkers = workers.filter(w => w.status === 'BUSY' || w.status === 'IDLE');
      
      const completedCount = completedJobs.totalElements;
      const failedCount = failedJobs.totalElements;
      const completionRate = (completedCount + failedCount) === 0 
        ? 100 // if no jobs have finished/failed, they haven't failed essentially.
        : Math.round((completedCount / (completedCount + failedCount)) * 100);

      return {
        totalWorkers: workers.length,
        activeWorkers: activeWorkers.length,
        totalJobs: totalJobs.totalElements,
        pendingJobs: pendingJobs.totalElements,
        runningJobs: runningJobs.totalElements,
        completionRate
      };
    },
    refetchInterval: 3000, // Update dashboard metrics every 3 seconds
  });
};
