import { act, render, screen } from '@testing-library/react';

import { useServiceReadyStore } from '@/store/useServiceReadyStore';

vi.mock('@/api/healthService', () => ({
  healthService: {
    checkRagReady: vi.fn(),
  },
}));

import { healthService } from '@/api/healthService';
import ServiceLoadingOverlay from '@/components/service-loading-overlay/ServiceLoadingOverlay';

describe('ServiceLoadingOverlay', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    useServiceReadyStore.setState({ serviceUnavailable: false });
    vi.mocked(healthService.checkRagReady).mockResolvedValue(false);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('renders nothing when service is available', () => {
    const { container } = render(<ServiceLoadingOverlay />);
    expect(container.innerHTML).toBe('');
  });

  it('renders overlay when service is unavailable', () => {
    useServiceReadyStore.setState({ serviceUnavailable: true });
    render(<ServiceLoadingOverlay />);

    expect(screen.getByText('The world of Ondgard is materializing...')).toBeInTheDocument();
    expect(
      screen.getByText('The service is getting ready. Please wait a moment.'),
    ).toBeInTheDocument();
  });

  it('polls health endpoint every 10 seconds', async () => {
    useServiceReadyStore.setState({ serviceUnavailable: true });
    render(<ServiceLoadingOverlay />);

    expect(healthService.checkRagReady).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });
    expect(healthService.checkRagReady).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });
    expect(healthService.checkRagReady).toHaveBeenCalledTimes(2);
  });

  it('dismisses overlay when health returns ready', async () => {
    useServiceReadyStore.setState({ serviceUnavailable: true });
    render(<ServiceLoadingOverlay />);

    expect(screen.getByText('The world of Ondgard is materializing...')).toBeInTheDocument();

    vi.mocked(healthService.checkRagReady).mockResolvedValue(true);

    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });

    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(false);
    expect(screen.queryByText('The world of Ondgard is materializing...')).not.toBeInTheDocument();
  });

  it('keeps polling when health returns not ready', async () => {
    useServiceReadyStore.setState({ serviceUnavailable: true });
    render(<ServiceLoadingOverlay />);

    vi.mocked(healthService.checkRagReady).mockResolvedValue(false);

    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });

    expect(useServiceReadyStore.getState().serviceUnavailable).toBe(true);
    expect(screen.getByText('The world of Ondgard is materializing...')).toBeInTheDocument();
  });

  it('stops polling after service becomes available', async () => {
    useServiceReadyStore.setState({ serviceUnavailable: true });
    render(<ServiceLoadingOverlay />);

    vi.mocked(healthService.checkRagReady).mockResolvedValue(true);

    // First tick: health returns ready → overlay dismissed
    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });

    const callsAfterDismiss = vi.mocked(healthService.checkRagReady).mock.calls.length;

    // After becoming available, further ticks should not increase call count
    await act(async () => {
      vi.advanceTimersByTime(30_000);
    });

    expect(healthService.checkRagReady).toHaveBeenCalledTimes(callsAfterDismiss);
  });
});
