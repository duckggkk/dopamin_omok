import { createBrowserRouter, RouterProvider, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import Navbar from '@/components/layout/Navbar';
import PrivateRoute from '@/components/common/PrivateRoute';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import VerifyEmailPage from '@/pages/VerifyEmailPage';
import LobbyPage from '@/pages/LobbyPage';
import GamePage from '@/pages/GamePage';
import ProfilePage from '@/pages/ProfilePage';
import EmailSentPage from '@/pages/EmailSentPage';
import ShopPage from '@/pages/ShopPage';

const Layout = () => {
  useAuth();
  return (
    <>
      <Navbar />
      <Outlet />
    </>
  );
};

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <Navigate to="/lobby" replace /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/verify-email', element: <VerifyEmailPage /> },
      { path: '/email-sent', element: <EmailSentPage /> },
      {
        element: <PrivateRoute />,
        children: [
          { path: '/lobby', element: <LobbyPage /> },
          { path: '/game/:gameId', element: <GamePage /> },
          { path: '/profile', element: <ProfilePage /> },
          { path: '/shop', element: <ShopPage /> },
        ],
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);

const App = () => <RouterProvider router={router} />;

export default App;
