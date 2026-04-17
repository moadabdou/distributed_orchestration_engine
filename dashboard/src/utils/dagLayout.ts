import dagre from '@dagrejs/dagre';
import type { DagGraph, DagNode } from '../types/api';

const NODE_WIDTH = 180;
const NODE_HEIGHT = 80;

export const applyDagLayout = (graph: DagGraph): DagNode[] => {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));
  
  // Set layout direction left-to-right
  dagreGraph.setGraph({ 
    rankdir: 'LR',
    nodesep: 60, // Spacing between nodes in same layer
    ranksep: 100, // Spacing between layers
  });

  const sortedNodes = [...graph.nodes].sort((a, b) => a.jobId.localeCompare(b.jobId));
  const sortedEdges = [...graph.edges].sort((a, b) =>
    a.sourceJobId === b.sourceJobId
      ? a.targetJobId.localeCompare(b.targetJobId)
      : a.sourceJobId.localeCompare(b.sourceJobId)
  );

  // Add nodes deterministically
  sortedNodes.forEach((node) => {
    dagreGraph.setNode(node.jobId, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });

  // Add edges deterministically
  sortedEdges.forEach((edge) => {
    dagreGraph.setEdge(edge.sourceJobId, edge.targetJobId);
  });

  // Apply layout
  dagre.layout(dagreGraph);

  // Add positions back to nodes
  return graph.nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.jobId);
    return {
      ...node,
      position: {
        // Adjust for center positioning by dagre (shift to top-left)
        x: nodeWithPosition.x - NODE_WIDTH / 2,
        y: nodeWithPosition.y - NODE_HEIGHT / 2,
      },
    };
  });
};
