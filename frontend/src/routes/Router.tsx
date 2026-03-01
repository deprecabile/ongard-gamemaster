import { createBrowserRouter } from 'react-router-dom';

import ProtectedRoute from '@/components/auth/ProtectedRoute';
import MainLayout from '@/components/layout/MainLayout';
import PublicLayout from '@/components/layout/PublicLayout';
import Dashboard from '@/pages/dashboard/Dashboard';
import Landing from '@/pages/landing/Landing';
import Login from '@/pages/login/Login';
import Register from '@/pages/register/Register';
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
        ],
      },
    ],
  },
]);

export default router;
