import { describe, it, expect } from 'vitest';
import { getJobStatusColor, getWorkflowStatusColor } from '../../utils/statusColors';

describe('statusColors', () => {
  describe('getJobStatusColor', () => {
    it('returns colors for job statuses', () => {
      expect(getJobStatusColor('PENDING')).toContain('bg-gray-100');
      expect(getJobStatusColor('RUNNING')).toContain('bg-amber-100');
      expect(getJobStatusColor('COMPLETED')).toContain('bg-green-100');
      expect(getJobStatusColor('FAILED')).toContain('bg-red-100');
      expect(getJobStatusColor('ASSIGNED')).toContain('bg-blue-100');
      expect(getJobStatusColor('CANCELLED')).toContain('bg-orange-100');
    });

    it('returns default color for unknown status', () => {
      expect(getJobStatusColor('UNKNOWN')).toContain('bg-gray-100');
    });
  });

  describe('getWorkflowStatusColor', () => {
    it('returns colors for workflow statuses', () => {
      expect(getWorkflowStatusColor('DRAFT')).toContain('bg-gray-100');
      expect(getWorkflowStatusColor('RUNNING')).toContain('bg-blue-100');
      expect(getWorkflowStatusColor('PAUSED')).toContain('bg-amber-100');
      expect(getWorkflowStatusColor('COMPLETED')).toContain('bg-green-100');
      expect(getWorkflowStatusColor('FAILED')).toContain('bg-red-100');
    });
    
    it('returns default color for unknown status', () => {
      expect(getWorkflowStatusColor('UNKNOWN')).toContain('bg-gray-100');
    });
  });
});