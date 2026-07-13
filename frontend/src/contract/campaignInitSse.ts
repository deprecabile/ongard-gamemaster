export interface InitCampaignSseCallbacks {
  onStarted: () => void;
  onProgress: (message: string) => void;
  onCompleted: () => void;
  onError: (errorCode: string, description: string) => void;
}
