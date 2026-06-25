import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

/**
 * 멤버(정식 회원) 전용 라우트. PrivateRoute 안쪽에 두어 인증은 이미 보장된 상태에서,
 * 게스트(비회원)는 게스트 허용 영역(싱글 AI)으로 돌려보낸다.
 * 백엔드도 동일 경로를 역할(USER/ADMIN)로 막으므로, 이건 UX용 1차 차단이다.
 */
const MemberRoute = () => {
  const { user } = useAuthStore();
  if (user?.guest) return <Navigate to="/ai" replace />;
  return <Outlet />;
};

export default MemberRoute;
