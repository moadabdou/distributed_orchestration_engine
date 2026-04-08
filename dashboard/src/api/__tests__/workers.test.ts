import { describe, it, expect, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { apiClient } from '../client';
import { getWorkers } from '../workers';

describe('Workers API', () => {
  const mock = new MockAdapter(apiClient);

  afterEach(() => {
    mock.reset();
  });

  it('should fetch workers successfully', async () => {
    const mockWorkers = [
      { id: '1', hostname: 'host1', ipAddress: '10.0.0.1', status: 'IDLE', lastHeartbeat: '' },
      { id: '2', hostname: 'host2', ipAddress: '10.0.0.2', status: 'BUSY', lastHeartbeat: '' },
    ];

    mock.onGet('/system/workers').reply(200, mockWorkers);

    const result = await getWorkers();

    expect(result).toEqual(mockWorkers);
  });
});
