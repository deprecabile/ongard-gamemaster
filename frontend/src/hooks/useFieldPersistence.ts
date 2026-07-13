import { useCallback, useEffect, useRef } from 'react';

import apiClient from '@/api/apiClient';
import { useAuthStore } from '@/store/useAuthStore';

interface FieldPersistenceConfig {
  value: string;
  apiUrl: string;
  delayMs: number;
  flushRegistry: React.RefObject<Map<string, () => void>>;
  registryKey: string;
  enabled: boolean;
}

export const useFieldPersistence = ({
  value,
  apiUrl,
  delayMs,
  flushRegistry,
  registryKey,
  enabled,
}: FieldPersistenceConfig): { onBlur: () => void } => {
  const dirtyRef = useRef(false);
  const valueRef = useRef(value);
  const prevValueRef = useRef(value);

  useEffect(() => {
    valueRef.current = value;
    if (value !== prevValueRef.current) {
      dirtyRef.current = true;
      prevValueRef.current = value;
    }
  }, [value]);

  useEffect(() => {
    if (!enabled || !dirtyRef.current) return;

    const timer = setTimeout(() => {
      if (dirtyRef.current) {
        dirtyRef.current = false;
        void apiClient.put(apiUrl, { value: valueRef.current });
      }
    }, delayMs);

    return () => {
      clearTimeout(timer);
    };
  }, [value, enabled, apiUrl, delayMs]);

  const flush = useCallback(() => {
    if (!dirtyRef.current) return;
    dirtyRef.current = false;

    const { accessToken } = useAuthStore.getState();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    void fetch(`/api${apiUrl}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ value: valueRef.current }),
      keepalive: true,
    });
  }, [apiUrl]);

  useEffect(() => {
    const registry = flushRegistry.current;
    registry.set(registryKey, flush);
    return () => {
      registry.delete(registryKey);
    };
  }, [flushRegistry, registryKey, flush]);

  const onBlur = useCallback(() => {
    if (dirtyRef.current) {
      dirtyRef.current = false;
      void apiClient.put(apiUrl, { value: valueRef.current });
    }
  }, [apiUrl]);

  return { onBlur };
};
