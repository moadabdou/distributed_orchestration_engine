import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSystemStats } from '../hooks/useSystemStats';
import { useQuery } from '@tanstack/react-query';
import { getJobs } from '../api/jobs';
import WorkerNodesPanel from '../components/WorkerNodesPanel';
import JobRow from '../components/JobRow';
import DagPreviewPanel from '../components/DagPreviewPanel';
import { Loader2, Activity, Users, Box, Hexagon, PlaySquare, CheckCircle, XCircle, Slash, GitMerge, Zap, ChevronLeft, ChevronRight } from 'lucide-react';

const RecentActivityFeed: React.FC = () => {
  const [page, setPage] = useState(0);
  const { data: recent, isLoading, isFetching } = useQuery({
    queryKey: ['activityFeed', page],
    queryFn: () => getJobs(page, 10, undefined, 'updatedAt,desc'),
    refetchInterval: 2500,
    placeholderData: (prev) => prev,
  });

  return (
    <div className="glass-panel p-6 flex flex-col h-full min-h-[400px] gap-4 relative">
      <div className="flex items-center gap-2 mb-2">
        <Activity className="w-5 h-5 text-purple-500 dark:text-purple-400" />
        <h2 className="text-xl font-medium text-slate-700 dark:text-slate-200 tracking-wide">ACTIVITY FEED</h2>
      </div>

      <div className={`flex-1 overflow-y-auto flex flex-col gap-2 custom-scrollbar pr-2 pb-2 transition-opacity duration-300 ${isFetching ? 'opacity-70' : 'opacity-100'}`}>
        {isLoading ? (
           <div className="flex-1 flex items-center justify-center"><Loader2 className="w-8 h-8 text-purple-400 animate-spin" /></div>
        ) : recent?.content.length === 0 ? (
           <div className="p-4 text-center text-slate-400 italic">Network is silent.</div>
        ) : (
           recent?.content.map(job => <JobRow key={`act-${job.id}`} job={job} />)
        )}
      </div>

      {/* Pagination Footer */}
      {(recent?.totalPages ? recent.totalPages > 1 : false) && (
        <div className="flex items-center justify-between pt-4 border-t border-white/30 text-sm font-semibold text-slate-500">
          <div>
            Showing page {recent!.number + 1} of {recent!.totalPages}
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
              disabled={page >= recent!.totalPages - 1}
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

const DashboardHome: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const paramId = searchParams.get('workflowId');
  const sessionStoredId = sessionStorage.getItem('selectedWorkflowId');

  // If we have a param in URL, always save it to session storage
  useEffect(() => {
    if (paramId) {
      sessionStorage.setItem('selectedWorkflowId', paramId);
    }
  }, [paramId]);

  // If we return to home and there's no param in URL, but we have one in session, restore it to URL
  useEffect(() => {
    if (!paramId && sessionStoredId) {
       setSearchParams({ workflowId: sessionStoredId }, { replace: true });
    }
  }, [paramId, sessionStoredId, setSearchParams]);

  const selectedWorkflowId = paramId || sessionStoredId || undefined;


  const { data: stats, isLoading } = useSystemStats();

  const mockZero = isLoading ? '-' : '0';
  
  const metricCards = [
    { label: 'Workflows', value: stats?.totalWorkflows ?? mockZero, icon: GitMerge, color: 'text-indigo-500' },
    { label: 'Active Workflows', value: stats?.activeWorkflows ?? mockZero, icon: Zap, color: 'text-yellow-500' },
    { label: 'Total Workers', value: stats?.totalWorkers ?? mockZero, icon: Users, color: stats?.totalWorkers ? 'text-emerald-500' : 'text-red-500' },
    { label: 'Active Workers', value: stats?.activeWorkers ?? mockZero, icon: Hexagon, color: 'text-mint-500' },
    { label: 'Total Jobs', value: stats?.totalJobs ?? mockZero, icon: Box, color: 'text-blue-500' },
    { label: 'Pending Jobs', value: stats?.pendingJobs ?? mockZero, icon: Activity, color: 'text-slate-500' },
    { label: 'Running Jobs', value: stats?.runningJobs ?? mockZero, icon: PlaySquare, color: 'text-purple-500' },
    { label: 'Completed Jobs', value: stats?.completedJobs ?? mockZero, icon: CheckCircle, color: 'text-emerald-500' },
    { label: 'Failed Jobs', value: stats?.failedJobs ?? mockZero, icon: XCircle, color: 'text-red-500' },
    { label: 'Cancelled', value: stats?.cancelledJobs ?? mockZero, icon: Slash, color: 'text-slate-400' },
  ];

  return (
    <div className="flex flex-col gap-6 overflow-y-auto custom-scrollbar pb-6">
      {/* Top Metrics Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        {metricCards.map((card, i) => (
          <div key={i} className="glass-card p-4 flex items-center gap-3 relative overflow-hidden group">
            <div className={`absolute -right-4 -top-4 w-16 h-16 ${card.color} opacity-10 group-hover:opacity-20 transition-opacity rounded-full mix-blend-multiply dark:mix-blend-screen blur-xl`}></div>
            <div className={`w-10 h-10 rounded-xl bg-white/50 dark:bg-white/5 border border-white/40 dark:border-white/10 flex items-center justify-center shadow-sm shrink-0 ${card.color}`}>
              <card.icon className="w-5 h-5" />
            </div>
            <div className="flex flex-col">
              <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide truncate">{card.label}</div>
              <div className="text-xl font-bold tracking-tight text-slate-700 dark:text-slate-100 leading-none mt-1">{card.value}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Workflow DAG Preview — resizable via handle */}
      <DagPreviewPanel workflowId={selectedWorkflowId} />

      {/* Main Split Panels */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 min-h-[400px]">
        <WorkerNodesPanel />
        <RecentActivityFeed />
      </div>
    </div>
  );
};

export default DashboardHome;
