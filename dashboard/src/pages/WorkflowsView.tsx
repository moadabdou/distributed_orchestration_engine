import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getWorkflows } from '../api/workflows';
import { useWorkflowActions } from '../hooks/useWorkflowActions';
import { Loader2, Filter, ChevronLeft, ChevronRight, GitMerge, Play, Pause, RotateCcw, Trash2, Eye } from 'lucide-react';
import type { WorkflowSummary } from '../types/api';

const WorkflowRow: React.FC<{ workflow: WorkflowSummary }> = ({ workflow }) => {
  const navigate = useNavigate();
  const {
    execute,
    pause,
    resume,
    reset,
    remove,
    isExecuting,
    isPausing,
    isResuming,
    isResetting,
    isRemoving,
  } = useWorkflowActions(workflow.id);

  return (
    <div className="relative group pl-8">
      {/* Connector Line */}
      <div className="absolute left-3 top-0 bottom-0 w-0.5 bg-gradient-to-b from-white/40 to-transparent dark:from-white/10 ml-0.5 mt-8 group-last:hidden" />

      <div className="glass-card flex items-center p-3 rounded-2xl border border-white/60 dark:border-white/5 shadow-sm hover:shadow dark:bg-slate-800/50 transition-all gap-4">
        <div className="grid grid-cols-[2fr_1fr_1fr_1fr_auto] items-center w-full gap-4 text-sm pl-10 pr-4">
          {/* Name */}
          <div className="font-bold text-slate-700 dark:text-slate-200 truncate">
            {workflow.name}
          </div>

          {/* Status Badge */}
          <div>
            <span className={`px-2 py-1 rounded text-xs font-bold tracking-wide uppercase shadow-sm border ${workflow.status === 'RUNNING' ? 'bg-purple-100 text-purple-700 border-purple-200 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-800' :
                workflow.status === 'COMPLETED' ? 'bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:border-emerald-800' :
                  workflow.status === 'FAILED' ? 'bg-red-100 text-red-700 border-red-200 dark:bg-red-900/30 dark:text-red-300 dark:border-red-800' :
                    workflow.status === 'PAUSED' ? 'bg-yellow-100 text-yellow-700 border-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-800' :
                      'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
              }`}>
              {workflow.status}
            </span>
          </div>

          {/* Job Count */}
          <div className="font-semibold text-slate-600 dark:text-slate-400">
            {workflow.totalJobs} jobs
          </div>

          {/* Created At */}
          <div className="text-slate-500 dark:text-slate-400 font-mono text-xs">
            {new Date(workflow.createdAt).toLocaleString()}
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2 justify-end">
            <button
              className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition"
              title="View DAG"
              onClick={() => navigate(`/?workflowId=${workflow.id}`)}
            >
              <Eye className="w-4 h-4" />
            </button>
            {(workflow.status === 'DRAFT' || workflow.status === 'PAUSED') && (
              <button
                className="p-1.5 rounded-lg bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100 dark:hover:bg-emerald-900/40 transition disabled:opacity-50"
                title={workflow.status === 'PAUSED' ? "Resume" : "Execute"}
                onClick={() => workflow.status === 'PAUSED' ? resume() : execute()}
                disabled={isExecuting || isResuming}
              >
                {isExecuting || isResuming ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
              </button>
            )}
            {workflow.status === 'RUNNING' && (
              <button
                className="p-1.5 rounded-lg bg-yellow-50 dark:bg-yellow-900/20 text-yellow-600 dark:text-yellow-400 hover:bg-yellow-100 dark:hover:bg-yellow-900/40 transition disabled:opacity-50"
                title="Pause"
                onClick={() => pause()}
                disabled={isPausing}
              >
                {isPausing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Pause className="w-4 h-4" />}
              </button>
            )}
            {workflow.status !== 'RUNNING' && workflow.status !== 'DRAFT' && (
              <button
                className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition disabled:opacity-50"
                title="Reset"
                onClick={() => reset()}
                disabled={isResetting}
              >
                {isResetting ? <Loader2 className="w-4 h-4 animate-spin" /> : <RotateCcw className="w-4 h-4" />}
              </button>
            )}
            {workflow.status !== 'RUNNING' && (
              <button
                className="p-1.5 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/40 transition disabled:opacity-50"
                title="Delete"
                onClick={async () => {
                  if (window.confirm('Are you sure you want to delete this workflow?')) {
                    await remove();
                  }
                }}
                disabled={isRemoving}
              >
                {isRemoving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

const WorkflowsView: React.FC = () => {
  const [page, setPage] = useState(0);

  const [statusFilter, setStatusFilter] = useState<WorkflowSummary['status'] | ''>('');
  const size = 15;

  const { data: workflowsResponse, isLoading, isError } = useQuery({
    queryKey: ['workflowsList', page, size, statusFilter],
    queryFn: () => getWorkflows(page, size, statusFilter === '' ? undefined : statusFilter),
    refetchInterval: 3000,
  });

  return (
    <div className="glass-panel p-6 flex flex-col h-full min-h-0 gap-4 relative">
      {/* Header controls */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-white/30 pb-4">
        <div className="flex items-center gap-4">
          <h2 className="text-2xl font-medium text-slate-700 dark:text-slate-200 tracking-wide flex items-center gap-2">
            WORKFLOWS
            <div className="relative flex h-3 w-3 ml-2" title="Live Polling Active">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
            </div>
          </h2>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Status Filter */}
          <div className="relative flex-1 sm:flex-none">
            <Filter className="w-4 h-4 text-slate-500 dark:text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <select
              className="w-full sm:w-auto pl-9 pr-8 py-2 bg-white/50 dark:bg-white/5 border border-white/60 dark:border-white/10 rounded-xl text-sm font-semibold text-slate-600 dark:text-slate-300 outline-none focus:ring-2 focus:ring-purple-300 appearance-none cursor-pointer hover:bg-white/70 dark:hover:bg-white/10 transition-colors"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as any);
                setPage(0);
              }}
            >
              <option value="" className="dark:bg-slate-800">All Statuses</option>
              <option value="DRAFT" className="dark:bg-slate-800">Draft</option>
              <option value="RUNNING" className="dark:bg-slate-800">Running</option>
              <option value="PAUSED" className="dark:bg-slate-800">Paused</option>
              <option value="COMPLETED" className="dark:bg-slate-800">Completed</option>
              <option value="FAILED" className="dark:bg-slate-800">Failed</option>
            </select>
          </div>

        </div>
      </div>

      {/* Table Header */}
      <div className="grid grid-cols-[2fr_1fr_1fr_1fr_auto] text-xs font-semibold text-slate-500 pb-2 px-8 border-b border-white/40 ml-7 gap-4">
        <div>Name</div>
        <div>Status</div>
        <div>Jobs</div>
        <div>Created</div>
        <div className="text-right">Actions</div>
      </div>

      {/* List content */}
      <div className="flex-1 overflow-y-auto flex flex-col gap-3 custom-scrollbar pb-2 pt-2">
        {isLoading ? (
          <div className="flex-1 flex flex-col items-center justify-center text-slate-400">
            <Loader2 className="w-10 h-10 text-purple-400 animate-spin mb-4" />
            Fetching workflows...
          </div>
        ) : isError ? (
          <div className="p-4 text-center text-red-500 font-medium">Network Error: Failed to fetch workflows.</div>
        ) : workflowsResponse?.content.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-slate-400 bg-white/20 rounded-2xl border border-white/40 border-dashed m-4">
            <GitMerge className="w-8 h-8 mb-2 opacity-50 text-purple-400" />
            <p>No {statusFilter ? statusFilter.toLowerCase() : ''} workflows found.</p>
          </div>
        ) : (
          workflowsResponse?.content.map((workflow) => (
            <WorkflowRow key={workflow.id} workflow={workflow} />
          ))
        )}
      </div>

      {/* Pagination Footer */}
      {(workflowsResponse?.totalPages ? workflowsResponse.totalPages > 1 : false) && (
        <div className="flex items-center justify-between pt-4 border-t border-white/30 text-sm font-semibold text-slate-500">
          <div>
            Showing page {workflowsResponse!.number + 1} of {workflowsResponse!.totalPages}
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
              disabled={page >= workflowsResponse!.totalPages - 1}
              onClick={() => setPage(page + 1)}
              className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-white/50 border border-transparent disabled:opacity-50 disabled:hover:bg-transparent"
            >
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default WorkflowsView;
