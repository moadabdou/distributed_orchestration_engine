import React, { useState } from 'react';
import type { Job } from '../types/api';
import { Box, PlaySquare, CheckSquare, XSquare, Clock, ChevronDown, ChevronRight } from 'lucide-react';

interface JobRowProps {
  job: Job;
}

const JobRow: React.FC<JobRowProps> = ({ job }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  // Derive a pseudo progress value based on status
  let progress = 0;
  let statusTheme = { text: 'text-slate-500', bg: 'bg-slate-100', bar: 'from-slate-200 to-slate-300', icon: Clock };
  
  switch (job.status) {
    case 'PENDING':
      progress = 0;
      statusTheme = { text: 'text-slate-500', bg: 'bg-slate-100', bar: 'from-slate-200 to-slate-300', icon: Clock };
      break;
    case 'ASSIGNED':
      progress = 20;
      statusTheme = { text: 'text-blue-500', bg: 'bg-blue-100', bar: 'from-blue-300 to-indigo-300', icon: Box };
      break;
    case 'RUNNING':
      progress = 60;
      statusTheme = { text: 'text-purple-500', bg: 'bg-purple-100', bar: 'from-purple-300 to-fuchsia-300', icon: PlaySquare };
      break;
    case 'COMPLETED':
      progress = 100;
      statusTheme = { text: 'text-emerald-500', bg: 'bg-emerald-100', bar: 'from-emerald-300 to-teal-300', icon: CheckSquare };
      break;
    case 'FAILED':
      progress = 100;
      statusTheme = { text: 'text-red-500', bg: 'bg-red-100', bar: 'from-red-400 to-red-500', icon: XSquare };
      break;
  }

  const Icon = statusTheme.icon;
  const shortId = job.id.split('-')[0] || job.id;
  const workerHost = job.workerId ? `Worker-${job.workerId.substring(0,8)}` : '—'; // using ID substring as placeholder for worker hostname, since full hostname isn't in Job directly

  let jobType = 'Task';
  let prettyPayload = job.payload;
  try {
    const parsedPayload = typeof job.payload === 'string' ? JSON.parse(job.payload) : job.payload;
    if (parsedPayload?.type) {
      jobType = parsedPayload.type;
    }
    prettyPayload = JSON.stringify(parsedPayload, null, 2);
  } catch (e) {
    // ignore parsing errors, fallback to raw string
  }

  return (
    <div className="flex flex-col flex-shrink-0 bg-white/40 dark:bg-slate-800/50 hover:bg-white/60 dark:hover:bg-slate-700/60 transition-colors border border-white/50 dark:border-white/10 rounded-2xl shadow-[0_2px_8px_0_rgba(31,38,135,0.02)] dark:shadow-none overflow-hidden">
      <div 
        className="flex items-center px-4 py-3 cursor-pointer"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="mr-3 text-slate-400 flex-shrink-0">
          {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </div>
        
        <div className="grid grid-cols-[1fr_2fr_1.5fr_1.5fr_1fr] items-center w-full gap-2">
          {/* Job ID */}
          <div className="text-xs text-slate-500 dark:text-slate-400 font-mono" title={job.id}>
            {shortId}
          </div>
          
          {/* Worker assigned */}
          <div className="text-sm font-medium text-slate-700 dark:text-slate-200 truncate">
            {workerHost}
          </div>
          
          {/* Type */}
          <div className="text-xs text-slate-600 dark:text-slate-400 truncate">
            {jobType}
          </div>
          
          {/* Progress */}
          <div className="px-2">
            <div className="w-full h-2.5 bg-slate-100/50 rounded-full overflow-hidden border border-white/50">
              <div 
                className={`h-full rounded-full bg-gradient-to-r ${statusTheme.bar} transition-all duration-1000 ease-in-out`}
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
          
          {/* Time (Creation) */}
          <div className="text-xs text-slate-600 dark:text-slate-400 truncate text-center hidden xl:block">
             {new Date(job.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit'})}
          </div>
          
          {/* Status */}
          <div className="flex items-center justify-end gap-2 text-xs font-semibold capitalize">
            <span className={`hidden sm:block ${statusTheme.text}`}>
              {job.status.toLowerCase()}
            </span>
            <div className={`w-6 h-6 rounded flex items-center justify-center ${statusTheme.bg} ${statusTheme.text}`}>
              <Icon className="w-3.5 h-3.5" />
            </div>
          </div>
        </div>
      </div>

      {isExpanded && (
        <div className="px-10 pb-4 pt-1 bg-white/20 dark:bg-slate-900/40 border-t border-white/30 dark:border-white/10 text-xs">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <div className="font-semibold text-slate-600 dark:text-slate-400 mb-1">Payload</div>
              <pre className="bg-slate-800/80 backdrop-blur text-mint-200 p-3 rounded-xl overflow-x-auto custom-scrollbar font-mono shadow-inner border border-slate-700/50">
                {prettyPayload}
              </pre>
            </div>
            <div>
              <div className="font-semibold text-slate-600 dark:text-slate-400 mb-1">Result</div>
              <pre className="bg-slate-800/80 backdrop-blur text-purple-200 p-3 rounded-xl overflow-x-auto custom-scrollbar font-mono shadow-inner border border-slate-700/50">
                {job.result ? job.result : 'No result yet.'}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default JobRow;
