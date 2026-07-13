import { useServiceReadyStore } from '@/store/useServiceReadyStore';

describe('useServiceReadyStore', () => {
  beforeEach(() => {
    useServiceReadyStore.setState({ serviceUnavailable: false });
  });

  it('starts with serviceUnavailable = false', () => {
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
  });

  it('markUnavailable sets serviceUnavailable to true', () => {
    useServiceReadyStore.getState().markUnavailable();
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(true);
  });

  it('markAvailable sets serviceUnavailable back to false', () => {
    useServiceReadyStore.getState().markUnavailable();
    useServiceReadyStore.getState().markAvailable();
    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
  });
});
