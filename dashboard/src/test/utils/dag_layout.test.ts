import { describe, it, expect } from 'vitest';
import { applyDagLayout } from '../../utils/dagLayout';
import type { DagGraph } from '../../types/api';

describe('dagLayout', () => {
  it('should calculate positions for nodes', () => {
    const graph: DagGraph = {
      workflowId: 'w1',
      workflowName: 'Test',
      workflowStatus: 'DRAFT',
      nodes: [
        { jobId: '1', label: 'Job 1', dagIndex: 0, status: 'PENDING', payload: '', result: null, workerId: null, timeoutMs: 60000, jobLabel: 'Job 1', createdAt: '', updatedAt: '' },
        { jobId: '2', label: 'Job 2', dagIndex: 1, status: 'PENDING', payload: '', result: null, workerId: null, timeoutMs: 60000, jobLabel: 'Job 2', createdAt: '', updatedAt: '' },
      ],
      edges: [
        { sourceJobId: '1', targetJobId: '2' },
      ],
    };

    const laidOutNodes = applyDagLayout(graph);
    
    expect(laidOutNodes).toHaveLength(2);
    expect(laidOutNodes[0].position).toBeDefined();
    expect(laidOutNodes[0].position?.x).toBeTypeOf('number');
    expect(laidOutNodes[0].position?.y).toBeTypeOf('number');
    
    expect(laidOutNodes[1].position).toBeDefined();
    
    // Y of child should be greater than Y of parent (top-to-bottom layout)
    expect(laidOutNodes[1].position!.y).toBeGreaterThan(laidOutNodes[0].position!.y);
  });
});