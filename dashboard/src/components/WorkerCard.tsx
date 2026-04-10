import React, { useMemo } from 'react';
import { formatDistanceToNow } from 'date-fns';
import type { Worker } from '../types/api';
import { Box, ServerCrash, Zap } from 'lucide-react';
import { getWorkerTheme } from '../utils/workerColors';

interface WorkerCardProps {
  worker: Worker;
}

const WorkerCard: React.FC<WorkerCardProps> = ({ worker }) => {
  const isOffline = worker.status === 'OFFLINE';
  const theme = getWorkerTheme(worker.id, isOffline);
  const capacity = worker.maxCapacity || 4; // default to 4 if missing
  const activeCount = worker.activeJobCount || 0;
  const capacityPercentage = Math.min((activeCount / capacity) * 100, 100);
  
  // Create a stable random mocked memory based on id for demo aesthetic

  const mockMemory = useMemo(() => {
    let hash = 0;
    for (let i = 0; i < worker.id.length; i++) hash += worker.id.charCodeAt(i);
    return 30 + (hash % 60);
  }, [worker.id]);

  return (
    <div className={`glass-card p-4 relative group flex flex-col justify-between min-h-[140px] cursor-pointer ${theme.glow} ${isOffline ? 'opacity-60 grayscale-[0.5]' : ''}`}>
      <div className="flex justify-between items-start">
        <h3 className={`font-semibold truncate mr-2 ${isOffline ? 'text-slate-500' : 'text-slate-700 dark:text-slate-200'}`} title={worker.hostname}>
          {worker.hostname}
        </h3>
        
        {/* Capacity Circular Indicator */}
        <div className="relative flex items-center justify-center w-10 h-10">
          <svg className="w-10 h-10 transform -rotate-90">
            <circle
              cx="20"
              cy="20"
              r="16"
              stroke="currentColor"
              strokeWidth="4"
              fill="transparent"
              className="text-slate-200 dark:text-slate-700"
            />
            <circle
              cx="20"
              cy="20"
              r="16"
              stroke={theme.stroke}
              strokeWidth="4"
              fill="transparent"
              strokeDasharray={100.53} // 2 * PI * 16
              strokeDashoffset={100.53 - (capacityPercentage / 100) * 100.53}
              className="transition-all duration-500 ease-out"
            />
          </svg>
          <span className="absolute text-[10px] font-bold text-slate-600 dark:text-slate-300">
            {activeCount}/{capacity}
          </span>
        </div>
      </div>
      
      <div className="my-2 space-y-1">
        <div className="text-xs text-slate-500 dark:text-slate-400">
          Memory<br/>
          <span className={`font-medium ${isOffline ? 'text-slate-400' : 'text-slate-700 dark:text-slate-300'}`}>{mockMemory}%</span>
        </div>
      </div>
      
      <div className="flex justify-between items-end mt-auto">
        <div className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-400">
          <div className={`w-2 h-2 rounded-full ${theme.dot} ${!isOffline ? 'animate-pulse' : ''}`} />
          {isOffline ? 'Offline' : 'Online'}
        </div>
        
        {/* Decorative Icon matching capacity or status */}
        <div className={`w-8 h-8 rounded-lg flex items-center justify-center opacity-70 ${theme.bg}`}>
          {isOffline ? (
            <ServerCrash className={`w-5 h-5 ${theme.icon}`} />
          ) : activeCount > 0 ? (
             <Zap className={`w-5 h-5 ${theme.icon}`} />
          ) : (
             <Box className={`w-5 h-5 ${theme.icon}`} />
          )}
        </div>
      </div>
      
      <div className="absolute top-0 left-0 w-full h-full pb-0 pointer-events-none group-hover:flex hidden flex-col items-center justify-end">
        <div className="text-[10px] bg-slate-800/80 text-white px-2 py-1 rounded mb-2 backdrop-blur-md">
          {worker.lastHeartbeat ? formatDistanceToNow(new Date(worker.lastHeartbeat)) + ' ago' : 'Never'}
        </div>
      </div>
    </div>
  );
};

export default WorkerCard;
