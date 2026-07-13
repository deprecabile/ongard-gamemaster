import { handleSseHttpError } from '@/api/sseUtils';
import { useServiceReadyStore } from '@/store/useServiceReadyStore';

describe('handleSseHttpError', () => {
  beforeEach(() => {
    useServiceReadyStore.setState({ serviceUnavailable: false });
  });

  it('marks store unavailable on 503', () => {
    handleSseHttpError(503);
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(true);
  });

  it('does not mark unavailable on 500', () => {
    handleSseHttpError(500);
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
  });

  it('does not mark unavailable on 404', () => {
    handleSseHttpError(404);
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
  });
});
