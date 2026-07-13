import { create } from 'zustand';

interface ServiceReadyState {
  serviceUnavailable: boolean;
  markUnavailable: () => void;
  markAvailable: () => void;
}

export const useServiceReadyStore = create<ServiceReadyState>()((set) => ({
  serviceUnavailable: false,
  markUnavailable: () => {
    set({ serviceUnavailable: true });
  },
  markAvailable: () => {
    set({ serviceUnavailable: false });
  },
}));
