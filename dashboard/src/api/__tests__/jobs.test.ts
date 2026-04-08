import { describe, it, expect, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { apiClient } from '../client';
import { getJobs, getJob, createJob } from '../jobs';

describe('Jobs API', () => {
  const mock = new MockAdapter(apiClient);

  afterEach(() => {
    mock.reset();
  });

  it('should fetch paginated jobs and correctly append query params', async () => {
    const mockResponse = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 1,
      size: 10,
    };

    mock.onGet('/jobs', { params: new URLSearchParams({ page: '1', size: '10', status: 'PENDING' }) })
        .reply(200, mockResponse);

    const result = await getJobs(1, 10, 'PENDING');
    
    expect(result).toEqual(mockResponse);
  });

  it('should fetch a single job by id', async () => {
    const mockJob = { id: '123', status: 'PENDING', payload: {}, result: null, workerId: null, createdAt: '', updatedAt: '' };
    
    mock.onGet('/jobs/123').reply(200, mockJob);

    const result = await getJob('123');
    
    expect(result).toEqual(mockJob);
  });

  it('should create a new job via POST request', async () => {
    const requestPayload = { payload: { type: 'test' } };
    const mockJobResponse = { id: '456', status: 'PENDING', payload: { type: 'test' }, result: null, workerId: null, createdAt: '2026-04-06T00:00:00', updatedAt: '2026-04-06T00:00:00' };

    mock.onPost('/jobs').reply(200, mockJobResponse);

    const result = await createJob(requestPayload);
    
    expect(result).toEqual(mockJobResponse);
    expect(mock.history.post[0].data).toBe(JSON.stringify(requestPayload));
  });
});
