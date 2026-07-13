export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  REGISTER: '/register',
  CONFIRM: '/confirm',
  FORGOT_PASSWORD: '/forgot-password',
  RESET_PASSWORD: '/reset-password',
  DASHBOARD: '/dashboard',
  INIT_CAMPAIGN: '/initCampaign',
  LOAD_CAMPAIGN: '/load-campaign',
  CAMPAIGN: '/campaign/:characterHash',
  ACCOUNT: '/account',
} as const;
