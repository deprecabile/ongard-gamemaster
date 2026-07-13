export function formatTokens(n: number): string {
  if (n >= 1_000_000) {
    const value = n / 1_000_000;
    return `${value % 1 === 0 ? String(value) : value.toFixed(1)}M`;
  }
  if (n >= 1_000) {
    const value = n / 1_000;
    return `${value % 1 === 0 ? String(value) : value.toFixed(1)}K`;
  }
  return String(n);
}

export function calcUsagePercentage(used: number, limit: number): number {
  if (limit <= 0) return 0;
  return Math.min(100, (used / limit) * 100);
}

export type UsageLevel = 'normal' | 'warning' | 'critical';

export function getUsageLevel(percentage: number): UsageLevel {
  if (percentage >= 100) return 'critical';
  if (percentage >= 85) return 'warning';
  return 'normal';
}
