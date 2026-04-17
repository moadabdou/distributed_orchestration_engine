import React from 'react';
import { Handle, Position } from '@xyflow/react';

interface CustomDagNodeProps {
  data: {
    label: string;
    jobId: string;
    status: 'PENDING' | 'ASSIGNED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
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
  const s = getNodeStyle(data.status);

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
              <div className="flex justify-between items-start mt-2 border-t border-slate-800 pt-2">
                <span className="text-xs text-slate-500">Result</span>
                <span className="text-xs text-slate-300 font-mono line-clamp-3 text-right max-w-[160px] overflow-hidden text-ellipsis leading-relaxed">
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
