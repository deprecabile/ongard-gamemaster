export interface TokenLimits {
  limitMonth: number;
  limitTotal: number;
  version: number;
}

export interface TokenUsageTotals {
  totalTokens: number;
  monthTokens: number;
}

export interface CharacterTokenUsage {
  characterHash: string;
  totalTokens: number;
  monthTokens: number;
}

export interface TokenUsageOverview {
  limits: TokenLimits | null;
  usage: TokenUsageTotals;
  characters: CharacterTokenUsage[];
}

export interface UpdateLimitsRequest {
  limitMonth: number;
  limitTotal: number;
}
