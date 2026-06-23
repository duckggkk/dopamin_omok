import { createBrowserRouter, RouterProvider, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { ToastProvider } from '@/contexts/ToastContext';
import Navbar from '@/components/layout/Navbar';
import Footer from '@/components/layout/Footer';
import PrivateRoute from '@/components/common/PrivateRoute';
import LoginPage from '@/pages/LoginPage';
import OAuthCallbackPage from '@/pages/OAuthCallbackPage';
import WelcomePage from '@/pages/WelcomePage';
import RegisterPage from '@/pages/RegisterPage';
import VerifyEmailPage from '@/pages/VerifyEmailPage';
import HomePage from '@/pages/HomePage';
import LobbyPage from '@/pages/LobbyPage';
import GamePage from '@/pages/GamePage';
import AiGamePage from '@/pages/AiGamePage';
import ProfilePage from '@/pages/ProfilePage';
import EmailSentPage from '@/pages/EmailSentPage';
import ShopPage from '@/pages/ShopPage';
import RankingPage from '@/pages/RankingPage';
import PlazaPage from '@/pages/PlazaPage';
import FriendsPage from '@/pages/FriendsPage';
import GameGuidePage from '@/pages/GameGuidePage';
import TermsPage from '@/pages/TermsPage';
import PrivacyPolicyPage from '@/pages/PrivacyPolicyPage';

const Layout = () => {
  useAuth();
  const { pathname } = useLocation();
  // 몰입형 실시간 화면(게임 방·광장)에선 푸터를 숨겨 세로 공간을 확보한다.
  const hideFooter = pathname.startsWith('/game/') || pathname.startsWith('/plaza');
  return (
    <>
      <Navbar />
      <Outlet />
      {!hideFooter && <Footer />}
    </>
  );
};

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/oauth/callback', element: <OAuthCallbackPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/terms', element: <TermsPage /> },
      { path: '/privacy', element: <PrivacyPolicyPage /> },
      { path: '/verify-email', element: <VerifyEmailPage /> },
      { path: '/email-sent', element: <EmailSentPage /> },
      {
        element: <PrivateRoute />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/welcome', element: <WelcomePage /> },
          { path: '/ai', element: <AiGamePage /> },
          { path: '/lobby', element: <LobbyPage /> },
          { path: '/plaza', element: <PlazaPage /> },
          { path: '/friends', element: <FriendsPage /> },
          { path: '/ranking', element: <RankingPage /> },
          { path: '/guide', element: <GameGuidePage /> },
          { path: '/game/:gameId', element: <GamePage /> },
          { path: '/profile', element: <ProfilePage /> },
          { path: '/profile/:userId', element: <ProfilePage /> },
          { path: '/shop', element: <ShopPage /> },
        ],
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);

const App = () => (
  <ToastProvider>
    <RouterProvider router={router} />
  </ToastProvider>
);

export default App;
