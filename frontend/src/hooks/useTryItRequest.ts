import { useState, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { Endpoint } from '@/types/openapi';
import { useStore } from '@/store/useStore';
import { useOnlineStatus } from './useOnlineStatus';
import { generateExampleValue, resolveSchema } from '@/utils/openapiParser';

export interface TryItFormState {
  pathParams: Record<string, string>;
  queryParams: Record<string, string>;
  headers: Record<string, string>;
  body: string;
}

export interface TryItResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
  time: number;
  size: number;
}

function buildUrl(
  serverUrl: string,
  path: string,
  pathParams: Record<string, string>,
  queryParams: Record<string, string>
): string {
  let urlPath = path;
  for (const [key, value] of Object.entries(pathParams)) {
    urlPath = urlPath.replace(`{${key}}`, encodeURIComponent(value));
  }
  const url = new URL(urlPath, serverUrl);
  for (const [key, value] of Object.entries(queryParams)) {
    if (value) url.searchParams.set(key, value);
  }
  return url.toString();
}

export function useTryItRequest(endpoint: Endpoint | null) {
  const { serverUrl, authToken, addToQueue, addHistory } = useStore();
  const isOnline = useOnlineStatus();
  const [formState, setFormState] = useState<TryItFormState>({
    pathParams: {},
    queryParams: {},
    headers: {},
    body: '',
  });
  const [error, setError] = useState<string | null>(null);

  const { data: response, isLoading, refetch } = useQuery<TryItResponse | null>({
    queryKey: endpoint
      ? ['api', endpoint.method, endpoint.path, JSON.stringify(formState)]
      : ['api', 'none'],
    queryFn: async () => {
      if (!endpoint) return null;
      setError(null);

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...formState.headers,
      };
      if (authToken) {
        headers['Authorization'] = `Bearer ${authToken}`;
      }

      const url = buildUrl(serverUrl, endpoint.path, formState.pathParams, formState.queryParams);
      const body = ['post', 'put', 'patch'].includes(endpoint.method) && formState.body
        ? formState.body
        : undefined;

      const startTime = performance.now();
      try {
        const res = await fetch(url, {
          method: endpoint.method.toUpperCase(),
          headers,
          body,
        });
        const endTime = performance.now();

        const responseBody = await res.text();
        const responseHeaders: Record<string, string> = {};
        res.headers.forEach((v, k) => { responseHeaders[k] = v; });

        const result: TryItResponse = {
          status: res.status,
          statusText: res.statusText,
          headers: responseHeaders,
          body: responseBody,
          time: Math.round(endTime - startTime),
          size: new Blob([responseBody]).size,
        };

        addHistory({
          id: `${Date.now()}-${Math.random()}`,
          timestamp: Date.now(),
          endpointId: endpoint.id,
          method: endpoint.method,
          path: endpoint.path,
          status: res.status,
          time: result.time,
        });

        return result;
      } catch (err: unknown) {
        const errorMsg = err instanceof Error ? err.message : 'Network error';
        setError(errorMsg);

        if (!isOnline) {
          addToQueue({
            id: `${Date.now()}-${Math.random()}`,
            timestamp: Date.now(),
            endpointId: endpoint.id,
            method: endpoint.method,
            path: endpoint.path,
            serverUrl,
            headers,
            body: body || '',
            pathParams: formState.pathParams,
            queryParams: formState.queryParams,
            status: 'pending',
          });
        }
        throw err;
      }
    },
    enabled: false,
    retry: false,
    staleTime: Infinity,
  });

  const setPathParam = useCallback((name: string, value: string) => {
    setFormState((prev) => ({
      ...prev,
      pathParams: { ...prev.pathParams, [name]: value },
    }));
  }, []);

  const setQueryParam = useCallback((name: string, value: string) => {
    setFormState((prev) => ({
      ...prev,
      queryParams: { ...prev.queryParams, [name]: value },
    }));
  }, []);

  const setHeader = useCallback((name: string, value: string) => {
    setFormState((prev) => ({
      ...prev,
      headers: { ...prev.headers, [name]: value },
    }));
  }, []);

  const setBody = useCallback((body: string) => {
    setFormState((prev) => ({ ...prev, body }));
  }, []);

  const resetForm = useCallback(() => {
    if (!endpoint) return;
    const newState: TryItFormState = {
      pathParams: {},
      queryParams: {},
      headers: {},
      body: '',
    };

    if (endpoint.operation.parameters) {
      for (const param of endpoint.operation.parameters) {
        if (param.in === 'path') {
          newState.pathParams[param.name] = '';
        } else if (param.in === 'query') {
          newState.queryParams[param.name] = '';
        }
      }
    }

    if (endpoint.operation.requestBody?.content?.['application/json']?.schema) {
      const schema = resolveSchema(endpoint.operation.requestBody.content['application/json'].schema);
      if (schema) {
        const example = generateExampleValue(schema);
        newState.body = JSON.stringify(example, null, 2);
      }
    }

    setFormState(newState);
    setError(null);
  }, [endpoint]);

  const sendRequest = useCallback(() => {
    refetch();
  }, [refetch]);

  return {
    formState,
    response,
    isLoading,
    error,
    isOnline,
    setPathParam,
    setQueryParam,
    setHeader,
    setBody,
    resetForm,
    sendRequest,
  };
}
