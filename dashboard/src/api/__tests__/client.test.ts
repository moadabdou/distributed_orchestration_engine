import { describe, it, expect, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { apiClient } from '../client';
import type { ApiError } from '../../types/api';

describe('API Client Error Interceptor', () => {
  const mock = new MockAdapter(apiClient);

  afterEach(() => {
    mock.reset();
  });

  it('should map network errors to ApiError', async () => {
    mock.onGet('/test').networkError();

    try {
      await apiClient.get('/test');
      expect.fail('Should have thrown an error');
    } catch (e) {
      const error = e as ApiError;
      expect(error.message).toBe('Network Error');
    }
  });

  it('should map server errors to ApiError', async () => {
    const errorData = { error: 'Internal Server Error' };
    mock.onGet('/test').reply(500, errorData);

    try {
      await apiClient.get('/test');
      expect.fail('Should have thrown an error');
    } catch (e) {
      const error = e as ApiError;
      expect(error.message).toBe('Request failed with status code 500');
      expect(error.status).toBe(500);
      expect(error.data).toEqual(errorData);
    }
  });
});
