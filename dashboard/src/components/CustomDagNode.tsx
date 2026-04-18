import React, { useState } from 'react';
import { Handle, Position } from '@xyflow/react';
import { ExternalLink, RotateCcw, Trash2, FileText, Loader2 } from 'lucide-react';
import { cancelJob, retryJob, getJobLogsUrl } from '../api/jobs';
import { useQueryClient } from '@tanstack/react-query';

interface CustomDagNodeProps {
  data: {
    label: string;
    jobId: string;
    status: 'PENDING' | 'ASSIGNED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
    workflowId?: string;
    workerId?: string | null;
    createdAt?: string;
    updatedAt?: string;
    result?: string | null;
    payload?: string;
  };
}

const getNodeStyle = (status: string) => {
  switch (status) {
    case 'PENDING':
      return {
        border: 'border-slate-600/50',
        bg: 'bg-slate-800/60',
        dot: 'bg-slate-500',
        glow: '',
      };
    case 'ASSIGNED':
      return {
        border: 'border-blue-500/40',
        bg: 'bg-blue-950/60',
        dot: 'bg-blue-400',
        glow: '',
      };
    case 'RUNNING':
      return {
        border: 'border-amber-400/60',
        bg: 'bg-amber-950/50',
        dot: 'bg-amber-400 animate-pulse',
        glow: 'shadow-[0_0_12px_rgba(251,191,36,0.3)]',
      };
    case 'COMPLETED':
      return {
        border: 'border-emerald-500/40',
        bg: 'bg-emerald-950/40',
        dot: 'bg-emerald-400',
        glow: '',
      };
    case 'FAILED':
      return {
        border: 'border-rose-500/50',
        bg: 'bg-rose-950/50',
        dot: 'bg-rose-400',
        glow: 'shadow-[0_0_10px_rgba(244,63,94,0.25)]',
      };
    case 'CANCELLED':
      return {
        border: 'border-slate-600/30',
        bg: 'bg-slate-900/40',
        dot: 'bg-slate-600',
        glow: '',
      };
    default:
      return {
        border: 'border-slate-600/50',
        bg: 'bg-slate-800/60',
        dot: 'bg-slate-500',
        glow: '',
      };
  }
};

const CustomDagNode: React.FC<CustomDagNodeProps> = ({ data }) => {
  const [isRetrying, setIsRetrying] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const queryClient = useQueryClient();
  const s = getNodeStyle(data.status);

  const invalidateQueries = () => {
    queryClient.invalidateQueries({ queryKey: ['jobs'] });
    queryClient.invalidateQueries({ queryKey: ['jobsList'] });
    queryClient.invalidateQueries({ queryKey: ['systemStats'] });
    queryClient.invalidateQueries({ queryKey: ['activityFeed'] });
    queryClient.invalidateQueries({ queryKey: ['workflows'] });
    queryClient.invalidateQueries({ queryKey: ['workflow', data.workflowId] });
    queryClient.invalidateQueries({ queryKey: ['dag', data.workflowId] });
  };

  const handleRetry = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsRetrying(true);
    try {
      await retryJob(data.jobId);
      invalidateQueries();
    } catch (error) {
      console.error('Failed to retry job:', error);
    } finally {
      setIsRetrying(false);
    }
  };

  const handleCancel = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsCancelling(true);
    try {
      await cancelJob(data.jobId);
      invalidateQueries();
    } catch (error) {
      console.error('Failed to cancel job:', error);
    } finally {
      setIsCancelling(false);
    }
  };

  const isCancelable = data.status === 'PENDING' || data.status === 'ASSIGNED' || data.status === 'RUNNING';
  const isRetriable = data.status === 'FAILED' || data.status === 'CANCELLED';

  return (
    <div
      className={`
        group relative px-5 py-4 rounded-xl border backdrop-blur-md
        min-w-[160px] max-w-[220px] min-h-[56px] flex items-center justify-center cursor-pointer
        transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:z-[1000]
        ${s.bg} ${s.border} ${s.glow}
      `}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!w-2.5 !h-2.5 !bg-slate-600 !border-slate-900 !border-2"
      />

      <div className="flex items-center gap-3">
        {/* Status dot */}
        <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${s.dot}`} />
        {/* Label only — no status badge */}
        <span className="text-sm font-semibold text-slate-200 truncate leading-relaxed">
          {data.label}
        </span>
      </div>

      <Handle
        type="source"
        position={Position.Right}
        className="!w-2.5 !h-2.5 !bg-slate-600 !border-slate-900 !border-2"
      />

      {/* Floating Action Bar (appears on hover) */}
      <div className="absolute -top-3 right-0 flex items-center gap-1.5 p-1 rounded-lg bg-slate-900/90 backdrop-blur-md border border-white/10 shadow-xl opacity-0 translate-y-2 group-hover:opacity-100 group-hover:translate-y-0 transition-all duration-200 z-[1001] pointer-events-auto">
        {isRetriable && (
          <button
            onClick={handleRetry}
            disabled={isRetrying}
            className="p-1.5 rounded-md hover:bg-slate-700 text-indigo-400 transition-colors disabled:opacity-50"
            title="Retry Job"
          >
            {isRetrying ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RotateCcw className="w-3.5 h-3.5" />}
          </button>
        )}
        {isCancelable && (
          <button
            onClick={handleCancel}
            disabled={isCancelling}
            className="p-1.5 rounded-md hover:bg-slate-700 text-rose-400 transition-colors disabled:opacity-50"
            title="Cancel Job"
          >
            {isCancelling ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
          </button>
        )}
        <a
          href={getJobLogsUrl(data.jobId)}
          target="_blank"
          rel="noopener noreferrer"
          className="p-1.5 rounded-md hover:bg-slate-700 text-slate-400 transition-colors"
          title="View Logs"
          onClick={(e) => e.stopPropagation()}
        >
          <FileText className="w-3.5 h-3.5" />
        </a>
      </div>

      {/* Hover Info Card */}
      <div className="absolute top-full left-1/2 -translate-x-1/2 mt-3 p-4 rounded-xl border border-slate-700/60 bg-slate-900/95 backdrop-blur-xl shadow-2xl z-50 w-[280px] pointer-events-none opacity-0 translate-y-2 group-hover:opacity-100 group-hover:translate-y-0 transition-all duration-200">
        <div className="flex flex-col gap-2">
          <div className="flex justify-between items-start">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">Job Details</span>
            <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${s.bg} ${s.border} text-slate-300`}>
              {data.status}
            </span>
          </div>
          
          <div className="space-y-1 mt-1">
            <div className="flex justify-between">
              <span className="text-xs text-slate-500">ID</span>
              <span className="text-xs text-slate-300 font-mono truncate max-w-[120px]">{data.jobId || data.label}</span>
            </div>
            {data.workerId && (
              <div className="flex justify-between">
                <span className="text-xs text-slate-500">Worker</span>
                <span className="text-xs text-slate-300 font-mono truncate max-w-[120px]">{data.workerId}</span>
              </div>
            )}
            {data.result && (
              <div className="flex flex-col gap-2 mt-2 border-t border-slate-800 pt-2 pointer-events-auto">
                <div className="flex justify-between items-center">
                  <span className="text-xs text-slate-500">Result</span>
                  <a 
                    href={getJobLogsUrl(data.jobId)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1 text-[10px] text-violet-400 hover:text-violet-300 hover:underline font-bold"
                  >
                    <ExternalLink className="w-3 h-3" />
                    LOGS
                  </a>
                </div>
                <span className="text-xs text-slate-300 font-mono line-clamp-2 leading-relaxed">
                  {data.result}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default CustomDagNode;
