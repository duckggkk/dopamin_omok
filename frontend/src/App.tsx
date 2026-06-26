import { createBrowserRouter, RouterProvider, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { ToastProvider } from '@/contexts/ToastContext';
import Navbar from '@/components/layout/Navbar';
import Footer from '@/components/layout/Footer';
import PrivateRoute from '@/components/common/PrivateRoute';
import MemberRoute from '@/components/common/MemberRoute';
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
          // 게스트(비회원)도 허용 — 싱글 AI, 게임 방법, 그리고 온라인 캐주얼(로비·대국)
          { path: '/ai', element: <AiGamePage /> },
          { path: '/guide', element: <GameGuidePage /> },
          { path: '/lobby', element: <LobbyPage /> },
          { path: '/game/:gameId', element: <GamePage /> },
          { path: '/plaza', element: <PlazaPage /> }, // 게스트도 입장 가능(꾸미기만 차단)
          // 멤버(정식 회원) 전용 — 게스트는 MemberRoute에서 /ai 로 리다이렉트
          {
            element: <MemberRoute />,
            children: [
              { path: '/', element: <HomePage /> },
              { path: '/welcome', element: <WelcomePage /> },
              { path: '/friends', element: <FriendsPage /> },
              { path: '/ranking', element: <RankingPage /> },
              { path: '/profile', element: <ProfilePage /> },
              { path: '/profile/:userId', element: <ProfilePage /> },
              { path: '/shop', element: <ShopPage /> },
            ],
          },
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
