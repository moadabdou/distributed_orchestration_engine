import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getJobs } from '../api/jobs';
import type { Job } from '../types/api';
import JobRow from '../components/JobRow';
import SubmitJobModal from '../components/SubmitJobModal';
import { Loader2, Plus, Filter, ChevronLeft, ChevronRight } from 'lucide-react';

const JobsView: React.FC = () => {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<Job['status'] | ''>('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const size = 15;

  const { data: jobsResponse, isLoading, isError } = useQuery({
    queryKey: ['jobsList', page, size, statusFilter],
    queryFn: () => getJobs(page, size, statusFilter === '' ? undefined : statusFilter),
    refetchInterval: 2000,
  });

  return (
    <div className="glass-panel p-6 flex flex-col h-full min-h-0 gap-4 relative">
      {/* Header controls */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-white/30 pb-4">
        <div className="flex items-center gap-4">
          <h2 className="text-2xl font-medium text-slate-700 tracking-wide flex items-center gap-2">
            JOBS
            <div className="relative flex h-3 w-3 ml-2" title="Live Polling Active">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
            </div>
          </h2>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Status Filter */}
          <div className="relative flex-1 sm:flex-none">
            <Filter className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <select
              className="w-full sm:w-auto pl-9 pr-8 py-2 bg-white/50 border border-white/60 rounded-xl text-sm font-semibold text-slate-600 outline-none focus:ring-2 focus:ring-purple-300 appearance-none cursor-pointer hover:bg-white/70 transition-colors"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as any);
                setPage(0); // reset page on filter change
              }}
            >
              <option value="">All Statuses</option>
              <option value="PENDING">Pending</option>
              <option value="ASSIGNED">Assigned</option>
              <option value="RUNNING">Running</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
            </select>
          </div>

          <button 
            onClick={() => setIsModalOpen(true)}
            className="flex-shrink-0 flex items-center justify-center w-10 h-10 sm:w-auto sm:px-4 sm:py-2 gap-2 bg-white/50 hover:bg-white border border-white/60 rounded-xl text-purple-600 font-bold transition-all shadow-sm"
          >
            <Plus className="w-5 h-5" />
            <span className="hidden sm:block">Submit Job</span>
          </button>
        </div>
      </div>

      {/* Table Header */}
      <div className="grid grid-cols-[1fr_2fr_1.5fr_1.5fr_1fr] text-xs font-semibold text-slate-500 pb-2 px-8 border-b border-white/40 ml-7">
        <div>Job ID</div>
        <div>Worker</div>
        <div>Progress</div>
        <div className="text-center">Created</div>
        <div className="text-right">Status</div>
      </div>

      {/* List content */}
      <div className="flex-1 overflow-y-auto flex flex-col gap-2 custom-scrollbar pb-2">
        {isLoading ? (
          <div className="flex-1 flex flex-col items-center justify-center text-slate-400">
            <Loader2 className="w-10 h-10 text-purple-400 animate-spin mb-4" />
            Scanning orchestrator network...
          </div>
        ) : isError ? (
          <div className="p-4 text-center text-red-500 font-medium">Network Error: Failed to fetch jobs.</div>
        ) : jobsResponse?.content.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-slate-400 bg-white/20 rounded-2xl border border-white/40 border-dashed m-4">
            <Filter className="w-8 h-8 mb-2 opacity-50 text-purple-400" />
            <p>No {statusFilter ? statusFilter.toLowerCase() : ''} jobs found.</p>
          </div>
        ) : (
          jobsResponse?.content.map(job => <JobRow key={job.id} job={job} />)
        )}
      </div>

      {/* Pagination Footer */}
      {(jobsResponse?.totalPages ? jobsResponse.totalPages > 1 : false) && (
        <div className="flex items-center justify-between pt-4 border-t border-white/30 text-sm font-semibold text-slate-500">
          <div>
            Showing page {jobsResponse!.number + 1} of {jobsResponse!.totalPages}
          </div>
          <div className="flex items-center gap-2">
            <button 
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-white/50 border border-transparent disabled:opacity-50 disabled:hover:bg-transparent"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>
            <button 
              disabled={page >= jobsResponse!.totalPages - 1}
              onClick={() => setPage(page + 1)}
              className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-white/50 border border-transparent disabled:opacity-50 disabled:hover:bg-transparent"
            >
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}

      {isModalOpen && <SubmitJobModal onClose={() => setIsModalOpen(false)} />}
    </div>
  );
};

export default JobsView;
