import React, { useState, useRef, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ReactFlow, Controls, Background, BackgroundVariant, useNodesState, useEdgesState } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useWorkflowDag } from '../hooks/useWorkflowDag';
import { useWorkflowActions } from '../hooks/useWorkflowActions';
import type { DagNode, DagEdge } from '../types/api';
import { applyDagLayout } from '../utils/dagLayout';
import CustomDagNode from './CustomDagNode';
import { 
  GripHorizontal, GitMerge, Play, Pause, RotateCcw, Trash2, Loader2,
  CheckCircle2, XCircle, Clock, PlayCircle, SkipForward, Ban, List
} from 'lucide-react';

const nodeTypes = {
  customNode: CustomDagNode,
};

interface DagPreviewPanelProps {
  workflowId?: string;
}

const MIN_HEIGHT = 280;
const MAX_HEIGHT = 900;
const DEFAULT_HEIGHT = 400;

const DagPreviewPanel: React.FC<DagPreviewPanelProps> = ({ workflowId }) => {
  const navigate = useNavigate();
  const { dag, isLoading, error } = useWorkflowDag(workflowId || '');
  const [height, setHeight] = useState(DEFAULT_HEIGHT);
  const isDragging = useRef(false);
  const startY = useRef(0);
  const startH = useRef(0);

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
  } = useWorkflowActions(workflowId || '');

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    isDragging.current = true;
    startY.current = e.clientY;
    startH.current = height;
    const onMove = (ev: MouseEvent) => {
      if (!isDragging.current) return;
      const delta = ev.clientY - startY.current;
      setHeight(Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, startH.current + delta)));
    };
    const onUp = () => {
      isDragging.current = false;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [height]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const lastLaidOutJobIds = useRef<string>('');

  useEffect(() => {
    lastLaidOutJobIds.current = '';
  }, [workflowId]);

  useEffect(() => {
    if (!dag) return;

    const currentJobIds = dag.nodes.map(n => n.jobId).sort().join(',');

    if (currentJobIds !== lastLaidOutJobIds.current || lastLaidOutJobIds.current === '') {
      // Re-layout fully
      const layoutedNodes = applyDagLayout(dag);
      setNodes(layoutedNodes.map((n: DagNode) => ({
        id: n.jobId,
        type: 'customNode',
        position: n.position || { x: 0, y: 0 },
        data: {
          ...n,
          label: n.label || n.jobId,
          status: n.status || 'PENDING',
          workflowId: dag.workflowId,
        },
      })));
      lastLaidOutJobIds.current = currentJobIds;
    } else {
      // Only update node data
      setNodes((nds) =>
        nds.map((nd) => {
          const newData = dag.nodes.find((n) => n.jobId === nd.id);
          if (newData) {
            return {
              ...nd,
              data: {
                ...newData,
                label: newData.label || newData.jobId,
                status: newData.status || 'PENDING',
                result: newData.result || null,
                workerId: newData.workerId || null,
                workflowId: dag.workflowId,
              },
            };
          }
          return nd;
        })
      );
    }

    // Refresh edges
    const nodeStatusMap = new Map<string, string>();
    dag.nodes.forEach(n => nodeStatusMap.set(n.jobId, n.status));

    const newEdges = dag.edges.map((e: DagEdge) => {
      const sourceStatus = nodeStatusMap.get(e.sourceJobId);
      const targetStatus = nodeStatusMap.get(e.targetJobId);

      const aboutToRun = sourceStatus === 'COMPLETED' && targetStatus === 'PENDING';
      const isFlowing = sourceStatus === 'COMPLETED' && ['ASSIGNED', 'RUNNING'].includes(targetStatus || '');
      const isComplete = sourceStatus === 'COMPLETED' && targetStatus === 'COMPLETED';
      const isActive = sourceStatus === 'RUNNING';

      let strokeColor = 'rgba(148,163,184,0.25)';
      let animated = false;
      let strokeWidth = 1.5;
      let className = '';

      if (isComplete) {
        strokeColor = 'rgba(52,211,153,0.5)';
        strokeWidth = 1.5;
      } else if (aboutToRun) {
        strokeColor = '#a78bfa';
        animated = true;
        strokeWidth = 2.5;
        className = 'dag-edge-glow-violet';
      } else if (isFlowing) {
        strokeColor = '#818cf8';
        animated = true;
        strokeWidth = 2;
        className = 'dag-edge-glow-indigo';
      } else if (isActive) {
        strokeColor = '#fbbf24';
        animated = true;
        strokeWidth = 2;
      }

      return {
        id: `${e.sourceJobId}-${e.targetJobId}`,
        source: e.sourceJobId,
        target: e.targetJobId,
        type: 'smoothstep',
        animated,
        className,
        style: { stroke: strokeColor, strokeWidth },
      };
    });

    setEdges(newEdges);
  }, [dag, setNodes, setEdges]);

  // Handle 404 Not Found - clear memory and navigate home
  useEffect(() => {
    if (error && (error as any).status === 404) {
      sessionStorage.removeItem('selectedWorkflowId');
      navigate('/', { replace: true });
    }
  }, [error, navigate]);

  if (!workflowId) {
    return (
      <div className="glass-panel p-6 flex flex-col items-center justify-center text-center" style={{ height }}>
        <GitMerge className="w-10 h-10 text-slate-600 dark:text-slate-500 mb-3" />
        <p className="text-slate-500 dark:text-slate-400 text-sm">Select a workflow to preview its DAG</p>
      </div>
    );
  }

  if (isLoading) {
    return <div className="glass-panel animate-pulse" style={{ height }} />;
  }

  if (error) {
    return (
      <div className="glass-panel flex items-center justify-center text-red-400 text-sm" style={{ height }}>
        Error loading DAG
      </div>
    );
  }

  const metrics = dag ? {
    total: dag.nodes.length,
    completed: dag.nodes.filter(n => n.status === 'COMPLETED').length,
    failed: dag.nodes.filter(n => n.status === 'FAILED').length,
    running: dag.nodes.filter(n => n.status === 'RUNNING').length,
    pending: dag.nodes.filter(n => n.status === 'PENDING' || n.status === 'ASSIGNED').length,
    skipped: dag.nodes.filter(n => n.status === 'SKIPPED').length,
    cancelled: dag.nodes.filter(n => n.status === 'CANCELLED').length,
  } : { total: 0, completed: 0, failed: 0, running: 0, pending: 0, skipped: 0, cancelled: 0 };

  return (
    <div className="flex flex-col gap-0">
      {/* DAG Canvas */}
      <div
        className="glass-panel overflow-hidden dag-panel"
        style={{ height }}
      >
        {/* Header */}
        <div className="flex items-center gap-2 px-5 py-3 border-b border-white/10 flex-shrink-0 bg-slate-900/40">
          <GitMerge className="w-4 h-4 text-violet-400" />
          <h2 className="text-xs font-bold text-slate-500 dark:text-slate-400 tracking-[0.15em] uppercase">
            {dag?.workflowName ?? 'Workflow'} — DAG Preview
          </h2>
          {dag?.workflowStatus && (
            <div className="ml-auto flex items-center gap-3">
              {/* DAG Action Controls */}
              <div className="flex items-center gap-1 border-r border-white/10 pr-3">
                {(dag.workflowStatus === 'DRAFT' || dag.workflowStatus === 'PAUSED') && (
                  <button
                    className="p-1 rounded-md bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500/20 transition-colors disabled:opacity-50"
                    title={dag.workflowStatus === 'PAUSED' ? 'Resume' : 'Execute'}
                    onClick={() => dag.workflowStatus === 'PAUSED' ? resume() : execute()}
                    disabled={isExecuting || isResuming}
                  >
                    {isExecuting || isResuming ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
                  </button>
                )}
                {dag.workflowStatus === 'RUNNING' && (
                  <button
                    className="p-1 rounded-md bg-amber-500/10 text-amber-500 hover:bg-amber-500/20 transition-colors disabled:opacity-50"
                    title="Pause Workflow"
                    onClick={() => pause()}
                    disabled={isPausing}
                  >
                    {isPausing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Pause className="w-3.5 h-3.5" />}
                  </button>
                )}
                {dag.workflowStatus !== 'RUNNING' && dag.workflowStatus !== 'DRAFT' && (
                  <button
                    className="p-1 rounded-md bg-slate-500/10 text-slate-400 hover:bg-slate-500/20 hover:text-slate-300 transition-colors disabled:opacity-50"
                    title="Reset Workflow"
                    onClick={() => reset()}
                    disabled={isResetting}
                  >
                    {isResetting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RotateCcw className="w-3.5 h-3.5" />}
                  </button>
                )}
                {dag.workflowStatus !== 'RUNNING' && (
                  <button
                    className="p-1 rounded-md bg-rose-500/10 text-rose-500 hover:bg-rose-500/20 transition-colors disabled:opacity-50"
                    title="Delete Workflow"
                    onClick={async () => {
                      if (window.confirm('Are you sure you want to delete this workflow?')) {
                        await remove();
                        navigate('/');
                      }
                    }}
                    disabled={isRemoving}
                  >
                    {isRemoving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
                  </button>
                )}
              </div>
              <span className={`text-[10px] font-bold uppercase tracking-widest px-2.5 py-0.5 rounded-full border ${getStatusChipStyle(dag.workflowStatus)}`}>
                {dag.workflowStatus}
              </span>
            </div>
          )}
        </div>

        {/* Workflow Specific Metrics Bar */}
        <div className="flex items-center gap-4 px-5 py-2.5 bg-slate-900/60 border-b border-white/5 overflow-x-auto no-scrollbar">
          <div className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-slate-800/40 border border-slate-700/50">
            <List className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-tight">Total</span>
            <span className="text-xs font-black text-slate-200 ml-1 font-mono">{metrics.total}</span>
          </div>
          
          <div className="h-4 w-px bg-white/10" />

          <MetricBadge 
            icon={<CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />} 
            label="Complete" 
            count={metrics.completed} 
            color="emerald" 
          />
          <MetricBadge 
            icon={<PlayCircle className="w-3.5 h-3.5 text-amber-400" />} 
            label="Running" 
            count={metrics.running} 
            color="amber" 
          />
          <MetricBadge 
            icon={<Clock className="w-3.5 h-3.5 text-blue-400" />} 
            label="Pending" 
            count={metrics.pending} 
            color="blue" 
          />
          <MetricBadge 
            icon={<XCircle className="w-3.5 h-3.5 text-rose-400" />} 
            label="Failed" 
            count={metrics.failed} 
            color="rose" 
          />
          <MetricBadge 
            icon={<Ban className="w-3.5 h-3.5 text-slate-400" />} 
            label="Cancelled" 
            count={metrics.cancelled} 
            color="slate" 
          />
          <MetricBadge 
            icon={<SkipForward className="w-3.5 h-3.5 text-indigo-400" />} 
            label="Skipped" 
            count={metrics.skipped} 
            color="indigo" 
          />
        </div>

        {/* Flow */}
        <div style={{ height: 'calc(100% - 84px)' }}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            nodeTypes={nodeTypes}
            fitView
            colorMode="dark"
            proOptions={{ hideAttribution: true }}
          >
            <Background
              variant={BackgroundVariant.Dots}
              color="rgba(148,163,184,0.1)"
              gap={20}
              size={1}
            />
            <Controls
              showInteractive={false}
              className="dag-controls"
            />
          </ReactFlow>
        </div>
      </div>

      {/* Resize Handle */}
      <div
        onMouseDown={onMouseDown}
        className="flex items-center justify-center h-4 cursor-row-resize group select-none"
        title="Drag to resize"
      >
        <GripHorizontal className="w-5 h-5 text-slate-400 dark:text-slate-600 group-hover:text-violet-400 transition-colors" />
      </div>
    </div>
  );
};

function getStatusChipStyle(status: string): string {
  switch (status) {
    case 'RUNNING':  return 'border-amber-500/40 text-amber-400 bg-amber-900/20';
    case 'COMPLETED': return 'border-emerald-500/40 text-emerald-400 bg-emerald-900/20';
    case 'FAILED':   return 'border-rose-500/40 text-rose-400 bg-rose-900/20';
    case 'PAUSED':   return 'border-slate-500/40 text-slate-400 bg-slate-800/20';
    default:          return 'border-slate-600/40 text-slate-400 bg-slate-800/20';
  }
}

export default DagPreviewPanel;

interface MetricBadgeProps {
  icon: React.ReactNode;
  label: string;
  count: number;
  color: 'emerald' | 'rose' | 'amber' | 'blue' | 'indigo' | 'slate';
}

const MetricBadge: React.FC<MetricBadgeProps> = ({ icon, label, count, color }) => {
  const themes = {
    emerald: 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400',
    rose: 'bg-rose-500/10 border-rose-500/20 text-rose-400',
    amber: 'bg-amber-500/10 border-amber-500/20 text-amber-400',
    blue: 'bg-blue-500/10 border-blue-500/20 text-blue-400',
    indigo: 'bg-indigo-500/10 border-indigo-500/20 text-indigo-400',
    slate: 'bg-slate-500/10 border-slate-500/20 text-slate-400',
  };

  return (
    <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border backdrop-blur-sm transition-all duration-300 ${themes[color]}`}>
      {icon}
      <span className="text-[10px] font-bold uppercase tracking-tight opacity-70 group-hover:opacity-100">{label}</span>
      <span className="text-xs font-black ml-0.5 font-mono">{count}</span>
    </div>
  );
};
