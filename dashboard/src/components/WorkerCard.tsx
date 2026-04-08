import React, { useMemo } from 'react';
import { formatDistanceToNow } from 'date-fns';
import type { Worker } from '../types/api';
import { Box, ServerCrash, Zap } from 'lucide-react';

interface WorkerCardProps {
  worker: Worker;
}

const WorkerCard: React.FC<WorkerCardProps> = ({ worker }) => {
  const statusColors = {
    IDLE: { text: 'text-mint-700', bg: 'bg-mint-100', dot: 'bg-mint-400', icon: 'text-mint-500', glow: 'shadow-[inset_0_0_20px_rgba(110,231,183,0.2)]' },
    BUSY: { text: 'text-purple-700', bg: 'bg-purple-100', dot: 'bg-purple-400', icon: 'text-purple-500', glow: 'shadow-[inset_0_0_20px_rgba(192,132,252,0.2)]' },
    OFFLINE: { text: 'text-red-700', bg: 'bg-red-100', dot: 'bg-red-400', icon: 'text-red-400', glow: 'shadow-[inset_0_0_20px_rgba(248,113,113,0.2)]' }
  };

  const theme = statusColors[worker.status] || statusColors.OFFLINE;
  
  // Create a stable random mocked memory based on id for demo aesthetic
  const mockMemory = useMemo(() => {
    let hash = 0;
    for (let i = 0; i < worker.id.length; i++) hash += worker.id.charCodeAt(i);
    return 30 + (hash % 60);
  }, [worker.id]);

  return (
    <div className={`glass-card p-4 relative group flex flex-col justify-between min-h-[140px] cursor-pointer ${theme.glow}`}>
      <div className="flex justify-between items-start">
        <h3 className="font-semibold text-slate-700 dark:text-slate-200 truncate mr-2" title={worker.hostname}>
          {worker.hostname}
        </h3>
        <span className={`text-[10px] font-bold px-2 py-1 rounded-full whitespace-nowrap capitalize ${theme.bg} ${theme.text}`}>
          {worker.status.toLowerCase()}
        </span>
      </div>
      
      <div className="my-2 space-y-1">
        <div className="text-xs text-slate-500 dark:text-slate-400">
          Memory<br/>
          <span className="font-medium text-slate-700 dark:text-slate-300">{mockMemory}%</span>
        </div>
      </div>
      
      <div className="flex justify-between items-end mt-auto">
        <div className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-400">
          <div className={`w-2 h-2 rounded-full ${theme.dot} ${worker.status !== 'OFFLINE' ? 'animate-pulse' : ''}`} />
          {worker.status === 'OFFLINE' ? 'Offline' : 'Online'}
        </div>
        
        {/* Decorative Icon */}
        <div className={`w-8 h-8 rounded-lg flex items-center justify-center opacity-70 ${theme.bg}`}>
          {worker.status === 'IDLE' && <Box className={`w-5 h-5 ${theme.icon}`} />}
          {worker.status === 'BUSY' && <Zap className={`w-5 h-5 ${theme.icon}`} />}
          {worker.status === 'OFFLINE' && <ServerCrash className={`w-5 h-5 ${theme.icon}`} />}
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
