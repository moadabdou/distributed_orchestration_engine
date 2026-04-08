import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getJobs } from '../api/jobs';
import JobRow from './JobRow';
import { Loader2, ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';

const JobQueuePanel: React.FC = () => {
  const [page, setPage] = useState(0);
  const size = 10;

  const { data: jobsResponse, isLoading, isError } = useQuery({
    queryKey: ['jobs', page, size],
    queryFn: () => getJobs(page, size),
    refetchInterval: 2000,
  });

  return (
    <div className="glass-panel p-6 flex flex-col h-full min-h-0 gap-4 relative">
      <h2 className="text-xl font-medium text-slate-700 tracking-wide mb-2">JOB QUEUE</h2>

      {/* Horizontal Flow Diagram Skeleton representation */}
      <div className="flex items-center justify-center mb-6 py-4 px-2 overflow-x-auto gap-4 custom-scrollbar">
        {/* Mint Cube */}
        <div className="w-10 h-10 rounded shadow-md bg-mint-100 flex-shrink-0 flex items-center justify-center border border-mint-300">
          <div className="w-4 h-4 bg-mint-400 rounded-sm"></div>
        </div>
        <ArrowRight className="w-4 h-4 text-slate-300 flex-shrink-0" />
        {/* Blue Cube */}
        <div className="w-10 h-10 rounded shadow-md bg-blue-100 flex-shrink-0 flex items-center justify-center border border-blue-300">
          <div className="w-4 h-4 bg-blue-400 rounded-sm"></div>
        </div>
        <ArrowRight className="w-4 h-4 text-slate-300 flex-shrink-0" />
        {/* Purple Branch */}
        <div className="flex flex-col gap-2 flex-shrink-0">
          <div className="w-10 h-10 rounded shadow-md bg-purple-100 flex items-center justify-center border border-purple-300">
            <div className="w-4 h-4 bg-purple-400 rounded-sm" style={{ transform: 'rotate(45deg)' }}></div>
          </div>
          <div className="w-10 h-10 rounded shadow-md bg-orange-100 flex items-center justify-center border border-orange-300">
            <div className="w-4 h-4 bg-orange-400 rounded-sm"></div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-[1fr_2fr_1.5fr_1.5fr_1fr] text-xs font-semibold text-slate-500 pb-2 px-4 border-b border-white/40">
        <div>Job ID</div>
        <div>Name</div>
        <div>Type</div>
        <div className="text-center">Progress</div>
        <div className="text-right">Status</div>
      </div>

      <div className="flex-1 overflow-y-auto flex flex-col gap-2 pr-2 custom-scrollbar pb-2">
        {isLoading ? (
          <div className="flex-1 flex items-center justify-center">
            <Loader2 className="w-8 h-8 text-purple-400 animate-spin" />
          </div>
        ) : isError ? (
          <div className="p-4 text-center text-red-500">Error loading jobs</div>
        ) : jobsResponse?.content.length === 0 ? (
          <div className="p-4 text-center text-slate-400 italic">No recent jobs</div>
        ) : (
          jobsResponse?.content.map(job => <JobRow key={job.id} job={job} />)
        )}
      </div>

      {/* Pagination Footer */}
      {(jobsResponse?.totalPages ? jobsResponse.totalPages > 1 : false) && (
        <div className="flex items-center justify-between pt-2 border-t border-white/30 text-xs font-semibold text-slate-500">
          <div>
            Page {jobsResponse!.number + 1} of {jobsResponse!.totalPages}
          </div>
          <div className="flex items-center gap-1">
            <button 
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className="w-6 h-6 flex items-center justify-center rounded hover:bg-white/50 disabled:opacity-50"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button 
              disabled={page >= jobsResponse!.totalPages - 1}
              onClick={() => setPage(page + 1)}
              className="w-6 h-6 flex items-center justify-center rounded hover:bg-white/50 disabled:opacity-50"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default JobQueuePanel;
