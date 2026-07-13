export interface ExtraProperty {
  chiave: string;
  valore: string;
}

export interface Oggetto {
  nome: string;
  descrizione: string;
  quantita: number;
  peso: number;
  extra?: ExtraProperty[];
}

export interface Contenitore {
  nome: string;
  oggetti: Oggetto[];
}

export interface Mount {
  nome: string;
  tipo: string;
  stato: string;
  extra?: ExtraProperty[];
}

export interface Inventory {
  money: number;
  mount: Mount[];
  contenitori: Contenitore[];
}
