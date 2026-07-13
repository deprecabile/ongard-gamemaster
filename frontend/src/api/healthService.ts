export const healthService = {
  checkRagReady: async (): Promise<boolean> => {
    try {
      const res = await fetch('/api/chat/health');
      if (!res.ok) return false;
      const data = (await res.json()) as { available: boolean };
      return data.available;
    } catch {
      return false;
    }
  },
};
