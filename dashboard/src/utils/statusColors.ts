export const getJobStatusColor = (status: string): string => {
  switch (status) {
    case 'PENDING':
      return 'bg-gray-100 text-gray-800 border-gray-200';
    case 'ASSIGNED':
      return 'bg-blue-100 text-blue-800 border-blue-200';
    case 'RUNNING':
      return 'bg-amber-100 text-amber-800 border-amber-200';
    case 'COMPLETED':
      return 'bg-green-100 text-green-800 border-green-200';
    case 'FAILED':
      return 'bg-red-100 text-red-800 border-red-200';
    case 'CANCELLED':
      return 'bg-orange-100 text-orange-800 border-orange-200';
    case 'SKIPPED':
      return 'bg-indigo-100 text-indigo-800 border-indigo-200';
    default:
      return 'bg-gray-100 text-gray-800 border-gray-200';
  }
};

export const getWorkflowStatusColor = (status: string): string => {
  switch (status) {
    case 'DRAFT':
      return 'bg-gray-100 text-gray-800';
    case 'RUNNING':
      return 'bg-blue-100 text-blue-800';
    case 'PAUSED':
      return 'bg-amber-100 text-amber-800';
    case 'COMPLETED':
      return 'bg-green-100 text-green-800';
    case 'FAILED':
      return 'bg-red-100 text-red-800';
    default:
      return 'bg-gray-100 text-gray-800';
  }
};