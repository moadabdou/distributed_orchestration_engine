export const createsCycle = (
  nodes: { jobId: string }[],
  existingEdges: { sourceJobId: string; targetJobId: string }[],
  newEdge: { sourceJobId: string; targetJobId: string }
): boolean => {
  // Build adjacency list
  const adjList: Record<string, string[]> = {};
  
  nodes.forEach(node => {
    adjList[node.jobId] = [];
  });

  const allEdges = [...existingEdges, newEdge];

  allEdges.forEach(edge => {
    if (!adjList[edge.sourceJobId]) {
      adjList[edge.sourceJobId] = [];
    }
    adjList[edge.sourceJobId].push(edge.targetJobId);
  });

  // Detect cycle using DFS
  const visited = new Set<string>();
  const recursionStack = new Set<string>();

  const isCyclic = (nodeId: string): boolean => {
    if (recursionStack.has(nodeId)) return true;
    if (visited.has(nodeId)) return false;

    visited.add(nodeId);
    recursionStack.add(nodeId);

    const neighbors = adjList[nodeId] || [];
    for (const neighbor of neighbors) {
      if (isCyclic(neighbor)) return true;
    }

    recursionStack.delete(nodeId);
    return false;
  };

  for (const node of nodes) {
    if (!visited.has(node.jobId)) {
      if (isCyclic(node.jobId)) return true;
    }
  }

  return false;
};