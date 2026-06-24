import { useEffect, useRef, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { friendApi } from '@/api/friend';
import { FriendRequest } from '@/types';
import { useToast } from '@/contexts/ToastContext';
import styles from './NotificationBell.module.css';

// 친구 요청을 주기적으로 확인해 종 배지/드롭다운으로 알린다(서버 푸시 없이 가벼운 폴링).
const POLL_MS = 45000;

const BellIcon = () => (
  <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="2"
    strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
    <path d="M13.7 21a2 2 0 0 1-3.4 0" />
  </svg>
);

const NotificationBell = () => {
  const navigate = useNavigate();
  const showToast = useToast();
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [open, setOpen] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const knownCountRef = useRef<number | null>(null); // null = 아직 첫 로드 전(첫 로드는 토스트 안 띄움)

  const refresh = useCallback((notify: boolean) => {
    friendApi.getRequests()
      .then((r) => {
        const list = r.data.data ?? [];
        const prev = knownCountRef.current;
        // 첫 로드가 아니고 개수가 늘었으면 = 새 요청 도착 → 토스트로 띄운다.
        if (notify && prev !== null && list.length > prev) {
          showToast(`👋 새 친구 요청이 ${list.length - prev}건 도착했어요!`, 'info');
        }
        knownCountRef.current = list.length;
        setRequests(list);
      })
      .catch(() => { /* 무시 — 다음 주기에 자동 재시도 */ });
  }, [showToast]);

  // 첫 로드(토스트 없음) + 주기 폴링(토스트 켬)
  useEffect(() => {
    refresh(false);
    const id = window.setInterval(() => refresh(true), POLL_MS);
    return () => window.clearInterval(id);
  }, [refresh]);

  // 드롭다운 바깥 클릭 시 닫기
  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next) refresh(false); // 열 때 최신화
  };

  const handleAccept = async (publicId: string) => {
    setBusyId(publicId);
    try {
      await friendApi.accept(publicId);
      refresh(false);
    } catch { /* 무시 */ } finally { setBusyId(null); }
  };

  const handleReject = async (publicId: string) => {
    setBusyId(publicId);
    try {
      await friendApi.remove(publicId);
      refresh(false);
    } catch { /* 무시 */ } finally { setBusyId(null); }
  };

  const count = requests.length;

  return (
    <div className={styles.wrap} ref={ref}>
      <button
        className={styles.bellBtn}
        onClick={toggle}
        aria-label={count ? `알림 ${count}건` : '알림'}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <BellIcon />
        {count > 0 && <span className={styles.badge}>{count > 9 ? '9+' : count}</span>}
      </button>

      {open && (
        <div className={styles.dropdown} role="menu">
          <div className={styles.dropdownHead}>알림</div>
          {count === 0 ? (
            <p className={styles.empty}>새 알림이 없어요.</p>
          ) : (
            <ul className={styles.list}>
              {requests.map((r) => (
                <li key={r.publicId} className={styles.item}>
                  <button
                    className={styles.who}
                    onClick={() => { setOpen(false); navigate(`/profile/${r.publicId}`); }}
                  >
                    <span className={styles.avatar}>
                      {r.profileImageUrl
                        ? <img src={r.profileImageUrl} alt="" />
                        : (r.nickname[0]?.toUpperCase() ?? '?')}
                    </span>
                    <span className={styles.text}>
                      <b>{r.nickname}</b>님이 친구 요청을 보냈어요.
                    </span>
                  </button>
                  <div className={styles.actions}>
                    <button
                      className={styles.accept}
                      disabled={busyId === r.publicId}
                      onClick={() => handleAccept(r.publicId)}
                    >
                      수락
                    </button>
                    <button
                      className={styles.reject}
                      disabled={busyId === r.publicId}
                      onClick={() => handleReject(r.publicId)}
                    >
                      거절
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <button className={styles.viewAll} onClick={() => { setOpen(false); navigate('/friends'); }}>
            친구 페이지로 →
          </button>
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
