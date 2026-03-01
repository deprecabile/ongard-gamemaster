export interface RaceAttributes {
  minHeight: number;
  maxHeight: number;
  favoriteBiomes: string[];
}

export interface GameRace {
  code: string;
  name: string;
  description: string;
  attributes: RaceAttributes;
}
