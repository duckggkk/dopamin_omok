import { useState, useEffect, useCallback, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { friendApi } from '@/api/friend';
import { FriendRequest, FriendSummary, HeadToHead } from '@/types';
import { getApiErrorMessage } from '@/utils/error';
import styles from './FriendsPage.module.css';

const record = (h: HeadToHead) => `${h.wins}승 ${h.losses}패 ${h.draws}무`;

const Avatar = ({ nickname, src }: { nickname: string; src: string | null }) => (
  <span className={styles.avatar}>
    {src ? <img src={src} alt={nickname} /> : nickname[0]?.toUpperCase() ?? '?'}
  </span>
);

const FriendsPage = () => {
  const navigate = useNavigate();
  const [friends, setFriends] = useState<FriendSummary[] | null>(null);
  const [requests, setRequests] = useState<FriendRequest[] | null>(null);
  const [nickname, setNickname] = useState('');
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    friendApi.getFriends().then((r) => setFriends(r.data.data ?? [])).catch(() => setFriends([]));
    friendApi.getRequests().then((r) => setRequests(r.data.data ?? [])).catch(() => setRequests([]));
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleSend = async (e: FormEvent) => {
    e.preventDefault();
    const name = nickname.trim();
    if (!name || busy) return;
    setBusy(true);
    try {
      await friendApi.sendRequest(name);
      setNickname('');
      setMsg({ text: `'${name}'님에게 친구 요청을 보냈습니다.`, ok: true });
    } catch (err) {
      setMsg({ text: getApiErrorMessage(err, '친구 요청에 실패했습니다.'), ok: false });
    } finally {
      setBusy(false);
    }
  };

  const handleAccept = async (publicId: string, name: string) => {
    try {
      await friendApi.accept(publicId);
      setMsg({ text: `'${name}'님과 친구가 되었습니다.`, ok: true });
      load();
    } catch (err) {
      setMsg({ text: getApiErrorMessage(err, '수락에 실패했습니다.'), ok: false });
    }
  };

  const handleReject = async (publicId: string) => {
    try {
      await friendApi.remove(publicId);
      load();
    } catch (err) {
      setMsg({ text: getApiErrorMessage(err, '처리에 실패했습니다.'), ok: false });
    }
  };

  const handleUnfriend = async (publicId: string, name: string) => {
    if (!window.confirm(`'${name}'님을 친구에서 삭제할까요?`)) return;
    try {
      await friendApi.remove(publicId);
      setMsg({ text: `'${name}'님을 친구에서 삭제했습니다.`, ok: true });
      load();
    } catch (err) {
      setMsg({ text: getApiErrorMessage(err, '삭제에 실패했습니다.'), ok: false });
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <h1 className={styles.title}>친구</h1>

        <form onSubmit={handleSend} className={styles.addRow}>
          <input
            className={styles.addInput}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="닉네임으로 친구 요청 보내기"
            maxLength={15}
          />
          <button type="submit" className={styles.addBtn} disabled={busy || !nickname.trim()}>
            요청 보내기
          </button>
        </form>
        {msg && <p className={msg.ok ? styles.msgOk : styles.msgErr}>{msg.text}</p>}
      </div>

      {requests && requests.length > 0 && (
        <div className={styles.card}>
          <h2 className={styles.sectionTitle}>받은 요청 ({requests.length})</h2>
          <div className={styles.list}>
            {requests.map((r) => (
              <div key={r.publicId} className={styles.row}>
                <button className={styles.who} onClick={() => navigate(`/profile/${r.publicId}`)}>
                  <Avatar nickname={r.nickname} src={r.profileImageUrl} />
                  <span className={styles.name}>{r.nickname}</span>
                </button>
                <div className={styles.actions}>
                  <button className={styles.acceptBtn} onClick={() => handleAccept(r.publicId, r.nickname)}>수락</button>
                  <button className={styles.rejectBtn} onClick={() => handleReject(r.publicId)}>거절</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className={styles.card}>
        <h2 className={styles.sectionTitle}>친구 목록 {friends ? `(${friends.length})` : ''}</h2>
        <div className={styles.list}>
          {friends === null ? (
            <p className={styles.empty}>불러오는 중...</p>
          ) : friends.length === 0 ? (
            <p className={styles.empty}>아직 친구가 없습니다. 닉네임으로 친구를 추가해보세요.</p>
          ) : (
            friends.map((f) => (
              <div key={f.publicId} className={styles.row}>
                <button className={styles.who} onClick={() => navigate(`/profile/${f.publicId}`)}>
                  <Avatar nickname={f.nickname} src={f.profileImageUrl} />
                  <span className={styles.friendMain}>
                    <span className={styles.name}>{f.nickname}</span>
                    <span className={styles.sub}>
                      📈 {f.classicRating} · ⚔️ {f.physicalRating} · 상대전적 {record(f.headToHead)}
                    </span>
                  </span>
                </button>
                <div className={styles.actions}>
                  <button className={styles.rejectBtn} onClick={() => handleUnfriend(f.publicId, f.nickname)}>친구 끊기</button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default FriendsPage;
