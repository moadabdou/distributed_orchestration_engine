import React, { useState } from 'react';
import type { Job } from '../types/api';
import { cancelJob } from '../api/jobs';
import { useQueryClient } from '@tanstack/react-query';
import { Box, PlaySquare, CheckSquare, XSquare, Clock, ChevronDown, ChevronRight, HardDrive, MoreVertical, XCircle, Trash2 } from 'lucide-react';
import { getWorkerTheme } from '../utils/workerColors';

interface JobRowProps {
  job: Job;
}

const JobRow: React.FC<JobRowProps> = ({ job }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  
  const queryClient = useQueryClient();

  const handleCancelClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsDropdownOpen(false);
    setIsCancelModalOpen(true);
  };

  const handleConfirmCancel = async () => {
    setIsCancelling(true);
    try {
      await cancelJob(job.id);
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: ['jobsList'] });
      queryClient.invalidateQueries({ queryKey: ['systemStats'] });
      queryClient.invalidateQueries({ queryKey: ['activityFeed'] });
    } catch (error) {
      console.error('Failed to cancel job:', error);
    } finally {
      setIsCancelling(false);
      setIsCancelModalOpen(false);
    }
  };

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
    case 'CANCELLED':
      progress = 100;
      statusTheme = { text: 'text-slate-400', bg: 'bg-slate-100 dark:bg-slate-800', bar: 'from-slate-300 to-slate-400', icon: XCircle };
      break;
  }

  const Icon = statusTheme.icon;
  const shortId = job.id.split('-')[0] || job.id;
  const workerHost = job.workerId ? `Worker-${job.workerId.substring(0,8)}` : '—'; // using ID substring as placeholder for worker hostname, since full hostname isn't in Job directly
  const workerTheme = job.workerId ? getWorkerTheme(job.workerId, false) : null;
  const showWorkerBadge = (job.status === 'ASSIGNED' || job.status === 'RUNNING') && workerTheme;
  
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

  const isCancelable = job.status === 'PENDING' || job.status === 'ASSIGNED' || job.status === 'RUNNING';

  return (
    <>
    <div className="flex flex-col flex-shrink-0 bg-white/40 dark:bg-slate-800/50 hover:bg-white/60 dark:hover:bg-slate-700/60 transition-colors border border-white/50 dark:border-white/10 rounded-2xl shadow-[0_2px_8px_0_rgba(31,38,135,0.02)] dark:shadow-none overflow-visible">
      <div 
        className="flex items-center px-4 py-3 cursor-pointer relative"
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
          <div className="text-sm font-medium text-slate-700 dark:text-slate-200 truncate flex items-center pr-2">
            {showWorkerBadge ? (
              <span 
                className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${workerTheme!.bg} ${workerTheme!.text}`}
              >
                <HardDrive className="w-3 h-3 mr-1" />
                {workerHost}
              </span>
            ) : (
              <span className="text-slate-400 font-normal">{workerHost}</span>
            )}
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
          
          {/* Status and Actions */}
          <div className="flex items-center justify-end gap-2 text-xs font-semibold capitalize relative">
            <span className={`hidden sm:block ${statusTheme.text}`}>
              {job.status.toLowerCase()}
            </span>
            <div className={`w-6 h-6 rounded flex items-center justify-center ${statusTheme.bg} ${statusTheme.text}`}>
              <Icon className="w-3.5 h-3.5" />
            </div>
            
            {/* Actions Dropdown */}
            <div 
              className="relative"
              onClick={(e) => { e.stopPropagation(); setIsDropdownOpen(!isDropdownOpen); }}
            >
              <button className="p-1 rounded-md hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-500 transition-colors">
                <MoreVertical className="w-4 h-4" />
              </button>
              
              {isDropdownOpen && (
                <>
                  <div 
                    className="fixed inset-0 z-10" 
                    onClick={(e) => { e.stopPropagation(); setIsDropdownOpen(false); }} 
                  />
                  <div className="absolute right-0 mt-1 w-32 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg shadow-lg overflow-hidden z-20 animate-in fade-in zoom-in-95 duration-100">
                    <button
                      onClick={handleCancelClick}
                      disabled={!isCancelable}
                      className={`w-full px-4 py-2 text-left text-sm flex items-center gap-2 ${
                        isCancelable 
                          ? 'text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 cursor-pointer' 
                          : 'text-slate-400 cursor-not-allowed'
                      }`}
                    >
                      <Trash2 className="w-4 h-4" />
                      Cancel
                    </button>
                  </div>
                </>
              )}
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
                {job.result ? (typeof job.result === 'object' ? JSON.stringify(job.result, null, 2) : job.result) : 'No result yet.'}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>

    {/* Cancel Confirmation Modal */}
    {isCancelModalOpen && (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm transition-all duration-300">
        <div className="glass-panel w-full max-w-sm p-6 relative animate-in fade-in zoom-in duration-300 bg-white/95 dark:bg-slate-800/95 shadow-2xl border border-slate-200 dark:border-slate-700">
          <h3 className="text-lg font-bold text-slate-800 dark:text-slate-100 mb-2">Cancel Job?</h3>
          <p className="text-sm text-slate-600 dark:text-slate-300 mb-6">
            Are you sure you want to cancel job <span className="font-mono bg-slate-100 dark:bg-slate-900 px-1 py-0.5 rounded">{shortId}</span>?
            This action cannot be undone.
          </p>
          <div className="flex gap-3 justify-end">
            <button 
              onClick={() => setIsCancelModalOpen(false)}
              className="px-4 py-2 rounded-lg text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700 transition-colors"
            >
              Nevermind
            </button>
            <button 
              onClick={handleConfirmCancel}
              disabled={isCancelling}
              className="px-4 py-2 rounded-lg text-sm font-semibold text-white bg-red-500 hover:bg-red-600 disabled:opacity-50 transition-colors flex items-center gap-2"
            >
              {isCancelling ? 'Cancelling...' : 'Yes, Cancel'}
            </button>
          </div>
        </div>
      </div>
    )}
    </>
  );
};

export default JobRow;
