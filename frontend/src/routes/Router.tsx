import { createBrowserRouter } from 'react-router-dom';

import ProtectedRoute from '@/components/auth/ProtectedRoute';
import MainLayout from '@/components/layout/MainLayout';
import PublicLayout from '@/components/layout/PublicLayout';
import Account from '@/pages/account/Account';
import Campaign from '@/pages/campaign/Campaign';
import InitCampaign from '@/pages/campaign/InitCampaign';
import Confirm from '@/pages/confirm/Confirm';
import Dashboard from '@/pages/dashboard/Dashboard';
import ForgotPassword from '@/pages/forgot-password/ForgotPassword';
import Landing from '@/pages/landing/Landing';
import LoadCampaign from '@/pages/load-campaign/LoadCampaign';
import Login from '@/pages/login/Login';
import Register from '@/pages/register/Register';
import ResetPassword from '@/pages/reset-password/ResetPassword';
import { ROUTES } from '@/routes/routes';

const router = createBrowserRouter([
  {
    path: ROUTES.HOME,
    element: <PublicLayout />,
    children: [
      {
        index: true,
        element: <Landing />,
      },
      {
        path: ROUTES.LOGIN,
        element: <Login />,
      },
      {
        path: ROUTES.REGISTER,
        element: <Register />,
      },
      {
        path: ROUTES.CONFIRM,
        element: <Confirm />,
      },
      {
        path: ROUTES.FORGOT_PASSWORD,
        element: <ForgotPassword />,
      },
      {
        path: ROUTES.RESET_PASSWORD,
        element: <ResetPassword />,
      },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        path: ROUTES.HOME,
        element: <MainLayout />,
        children: [
          {
            path: ROUTES.DASHBOARD,
            element: <Dashboard />,
          },
          {
            path: ROUTES.INIT_CAMPAIGN,
            element: <InitCampaign />,
          },
          {
            path: ROUTES.LOAD_CAMPAIGN,
            element: <LoadCampaign />,
          },
          {
            path: ROUTES.CAMPAIGN,
            element: <Campaign />,
          },
          {
            path: ROUTES.ACCOUNT,
            element: <Account />,
          },
        ],
      },
    ],
  },
]);

export default router;
