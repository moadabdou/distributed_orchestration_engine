import React from 'react';
import { useSystemStats } from '../hooks/useSystemStats';
import { useQuery } from '@tanstack/react-query';
import { getJobs } from '../api/jobs';
import WorkerNodesPanel from '../components/WorkerNodesPanel';
import JobRow from '../components/JobRow';
import { Loader2, Activity, Users, Box, Hexagon, PlaySquare, Award } from 'lucide-react';

const RecentActivityFeed: React.FC = () => {
  const { data: recent, isLoading } = useQuery({
    queryKey: ['activityFeed'],
    queryFn: () => getJobs(0, 10, undefined, 'updatedAt,desc'),
    refetchInterval: 2500,
  });

  return (
    <div className="glass-panel p-6 flex flex-col h-full min-h-0 gap-4 relative">
      <div className="flex items-center gap-2 mb-2">
        <Activity className="w-5 h-5 text-purple-500 dark:text-purple-400" />
        <h2 className="text-xl font-medium text-slate-700 dark:text-slate-200 tracking-wide">ACTIVITY FEED</h2>
      </div>

      <div className="flex-1 overflow-y-auto flex flex-col gap-2 custom-scrollbar pr-2 pb-2">
        {isLoading ? (
           <div className="flex-1 flex items-center justify-center"><Loader2 className="w-8 h-8 text-purple-400 animate-spin" /></div>
        ) : recent?.content.length === 0 ? (
           <div className="p-4 text-center text-slate-400 italic">Network is silent.</div>
        ) : (
           recent?.content.map(job => <JobRow key={`act-${job.id}`} job={job} />)
        )}
      </div>
    </div>
  );
};

const DashboardHome: React.FC = () => {
  const { data: stats, isLoading } = useSystemStats();

  const mockZero = isLoading ? '-' : '0';
  
  const metricCards = [
    { label: 'Total Workers', value: stats?.totalWorkers ?? mockZero, icon: Users, color: stats?.totalWorkers ? 'text-emerald-500' : 'text-red-500' },
    { label: 'Active Workers', value: stats?.activeWorkers ?? mockZero, icon: Hexagon, color: 'text-mint-500' },
    { label: 'Total Jobs', value: stats?.totalJobs ?? mockZero, icon: Box, color: 'text-blue-500' },
    { label: 'Pending Jobs', value: stats?.pendingJobs ?? mockZero, icon: Activity, color: 'text-slate-500' },
    { label: 'Running Jobs', value: stats?.runningJobs ?? mockZero, icon: PlaySquare, color: 'text-purple-500' },
    { label: 'Success Rate', value: `${stats?.completionRate ?? mockZero}%`, icon: Award, color: 'text-emerald-500' },
  ];

  return (
    <div className="flex flex-col h-full min-h-0 gap-6">
      {/* Top Metrics Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 flex-shrink-0">
        {metricCards.map((card, i) => (
          <div key={i} className="glass-card p-4 flex flex-col justify-between h-28 relative overflow-hidden group">
            <div className={`absolute -right-4 -top-4 w-16 h-16 ${card.color} opacity-10 group-hover:opacity-20 transition-opacity rounded-full mix-blend-multiply dark:mix-blend-screen blur-xl`}></div>
            <div className={`w-8 h-8 rounded-xl bg-white/50 dark:bg-white/5 border border-white/40 dark:border-white/10 flex items-center justify-center shadow-sm ${card.color}`}>
              <card.icon className="w-4 h-4" />
            </div>
            <div>
              <div className="text-2xl font-bold tracking-tight text-slate-700 dark:text-slate-100">{card.value}</div>
              <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 mt-0.5 uppercase tracking-wide">{card.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Main Split Panels */}
      <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 gap-6">
        <WorkerNodesPanel />
        <RecentActivityFeed />
      </div>
    </div>
  );
};

export default DashboardHome;
