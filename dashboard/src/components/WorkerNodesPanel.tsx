import React, { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getWorkers } from '../api/workers';
import WorkerCard from './WorkerCard';
import { Sparkles, Loader2 } from 'lucide-react';

const WorkerNodesPanel: React.FC = () => {
  const { data: workers, isLoading, isError } = useQuery({
    queryKey: ['workers'],
    queryFn: getWorkers,
    refetchInterval: 2000,
  });

  // Stabilize worker order to prevent visual shuffling during polling
  const sortedWorkers = useMemo(() => {
    return [...(workers || [])].sort((a, b) => a.id.localeCompare(b.id));
  }, [workers]);

  return (
    <div className="glass-panel p-6 flex flex-col h-full gap-4 relative">
      <div className="flex justify-between items-center mb-2">
        <h2 className="text-xl font-medium text-slate-700 tracking-wide">WORKER NODES</h2>
        <div className="flex items-center gap-2 text-sm text-slate-600 font-medium">
          Cluster health <Sparkles className="w-5 h-5 text-purple-400" />
        </div>
      </div>

      {isLoading ? (
        <div className="flex-1 flex items-center justify-center">
          <Loader2 className="w-8 h-8 text-purple-400 animate-spin" />
        </div>
      ) : isError ? (
        <div className="flex-1 flex items-center justify-center text-red-400 p-4 border border-red-200 bg-red-50/50 rounded-xl">
          Failed to load workers
        </div>
      ) : workers?.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center text-slate-400 bg-white/20 rounded-xl border border-white/40 border-dashed">
          <Sparkles className="w-8 h-8 mb-2 opacity-50" />
          <p>No workers connected</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 xl:grid-cols-3 gap-4 auto-rows-max overflow-y-auto pr-2 custom-scrollbar">
          {sortedWorkers?.map(worker => (
            <WorkerCard key={worker.id} worker={worker} />
          ))}
        </div>
      )}
    </div>
  );
};

export default WorkerNodesPanel;
