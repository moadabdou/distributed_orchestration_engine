import { describe, it, expect } from 'vitest';
import { createsCycle } from '../../utils/dagCycleCheck';

describe('dagCycleCheck', () => {
  const nodes = [
    { jobId: '1' },
    { jobId: '2' },
    { jobId: '3' },
  ];

  it('should return false for an acyclic graph', () => {
    const existingEdges = [
      { sourceJobId: '1', targetJobId: '2' },
    ];
    const newEdge = { sourceJobId: '2', targetJobId: '3' };

    expect(createsCycle(nodes, existingEdges, newEdge)).toBe(false);
  });

  it('should return true when a cycle is created (simple)', () => {
    const existingEdges = [
      { sourceJobId: '1', targetJobId: '2' },
      { sourceJobId: '2', targetJobId: '3' },
    ];
    const newEdge = { sourceJobId: '3', targetJobId: '1' };

    expect(createsCycle(nodes, existingEdges, newEdge)).toBe(true);
  });

  it('should return true for a self-loop', () => {
    const existingEdges: any[] = [];
    const newEdge = { sourceJobId: '2', targetJobId: '2' };

    expect(createsCycle(nodes, existingEdges, newEdge)).toBe(true);
  });
});