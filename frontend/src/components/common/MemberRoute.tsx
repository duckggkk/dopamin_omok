import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

/**
 * 멤버(정식 회원) 전용 라우트. PrivateRoute 안쪽에 두어 인증은 이미 보장된 상태에서,
 * 게스트(비회원)는 홈(/)으로 돌려보낸다(게스트도 홈 화면은 회원과 동일하게 볼 수 있다).
 * 백엔드도 동일 경로를 역할(USER/ADMIN)로 막으므로, 이건 UX용 1차 차단이다.
 */
const MemberRoute = () => {
  const { user } = useAuthStore();
  if (user?.guest) return <Navigate to="/" replace />;
  return <Outlet />;
};

export default MemberRoute;
