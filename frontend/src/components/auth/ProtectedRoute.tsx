import { Navigate, Outlet } from 'react-router-dom';

import { ROUTES } from '@/routes/routes';
import { useAuthStore } from '@/store/useAuthStore';

const ProtectedRoute = () => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.LOGIN} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
