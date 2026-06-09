import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gameApi } from '@/api/game';
import { GameType } from '@/types';
import ClassicGamePage from './ClassicGamePage';
import PhysicalGamePage from './PhysicalGamePage';
import styles from './GamePage.module.css';

/**
 * 게임 진입점 디스패처: 방의 gameType 을 한 번 조회해
 * 피지컬 오목이면 PhysicalGamePage, 그 외(클래식)는 ClassicGamePage 를 마운트한다.
 * 각 페이지가 방/소켓을 자체 로드하므로(클래식 회귀 위험 0) 여기선 타입만 판별한다.
 */
const GamePage = () => {
  const { gameId: roomCode } = useParams<{ gameId: string }>();
  const navigate = useNavigate();
  const [gameType, setGameType] = useState<GameType | null>(null);

  useEffect(() => {
    let active = true;
    gameApi
      .getRoom(roomCode!)
      .then((res) => {
        if (active) setGameType(res.data.data?.gameType ?? 'CLASSIC');
      })
      .catch(() => navigate('/lobby'));
    return () => {
      active = false;
    };
  }, [roomCode, navigate]);

  if (!gameType) {
    return <div className={styles.loading}>게임을 불러오는 중...</div>;
  }

  return gameType === 'PHYSICAL' ? <PhysicalGamePage /> : <ClassicGamePage />;
};

export default GamePage;
