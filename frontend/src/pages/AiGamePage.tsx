import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Board, StoneColor } from '@/types';
import { createEmptyBoard } from '@/constants/board';
import { checkWin, isBoardFull, placeStone } from '@/utils/omokEngine';
import { AiDifficulty, chooseAiMove } from '@/utils/omokAi';
import { useCosmetics } from '@/hooks/useCosmetics';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import OmokBoard from '@/components/game/OmokBoard';
import { winEffectClass } from '@/components/game/GameResultOverlay';
import styles from './AiGamePage.module.css';
import gameStyles from './GamePage.module.css';

// 사람은 항상 흑(선), AI 는 백(후). 흑이 먼저 두는 표준 진행.
const HUMAN: StoneColor = 'BLACK';
const AI: StoneColor = 'WHITE';

type Result = 'WIN' | 'LOSS' | 'DRAW';

interface DifficultyMeta {
  key: AiDifficulty;
  label: string;
  emoji: string;
  desc: string;
}

const DIFFICULTIES: DifficultyMeta[] = [
  { key: 'EASY', label: '쉬움', emoji: '🌱', desc: '느긋한 연습 상대. 가끔 빈틈을 보여줘요.' },
  { key: 'NORMAL', label: '보통', emoji: '⚔️', desc: '기본기를 갖춘 상대. 위협은 빠짐없이 막아요.' },
  { key: 'HARD', label: '어려움', emoji: '🔥', desc: '한 수 앞을 읽고 반격하는 상대. 방심하면 당해요.' },
];

const AiGamePage = () => {
  const navigate = useNavigate();
  // 내가 장착한 코스메틱(보드/돌 스킨·착수 효과·착수음·승리 이펙트)을 일반전과 동일하게 적용.
  const { boardSkinConfig, stoneSkin, stoneEffect, stoneSoundKey, defeatEffect } = useCosmetics();
  const playStoneSound = useStoneSoundPlayer();

  const [difficulty, setDifficulty] = useState<AiDifficulty | null>(null);
  const [board, setBoard] = useState<Board>(createEmptyBoard());
  const [turn, setTurn] = useState<StoneColor>(HUMAN);
  const [lastMove, setLastMove] = useState<{ row: number; col: number } | null>(null);
  const [result, setResult] = useState<Result | null>(null);
  const [thinking, setThinking] = useState(false);
  const [moveCount, setMoveCount] = useState(0);

  const aiTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const startGame = useCallback((diff: AiDifficulty) => {
    if (aiTimerRef.current) clearTimeout(aiTimerRef.current);
    setDifficulty(diff);
    setBoard(createEmptyBoard());
    setTurn(HUMAN);
    setLastMove(null);
    setResult(null);
    setThinking(false);
    setMoveCount(0);
  }, []);

  // 한 수를 보드에 반영하고, 승리/무승부면 결과를 세팅한다. 게임이 계속되면 true 반환.
  const applyMove = useCallback(
    (row: number, col: number, color: StoneColor): boolean => {
      const next = placeStone(board, row, col, color);
      setBoard(next);
      setLastMove({ row, col });
      setMoveCount((n) => n + 1);
      // 내 수는 내 장착 착수음, AI 수는 기본 폴백음(키 null → 합성음). 일반전과 동일한 통일 규칙.
      playStoneSound(color === HUMAN ? stoneSoundKey : null);

      if (checkWin(next, row, col)) {
        setResult(color === HUMAN ? 'WIN' : 'LOSS');
        return false;
      }
      if (isBoardFull(next)) {
        setResult('DRAW');
        return false;
      }
      return true;
    },
    [board, playStoneSound, stoneSoundKey],
  );

  const handleHumanMove = (row: number, col: number) => {
    if (result || thinking || turn !== HUMAN || board[row][col] !== null) return;
    if (applyMove(row, col, HUMAN)) setTurn(AI);
  };

  // AI 차례: 잠깐 '생각'한 뒤(체감용 지연) 한 수를 둔다. 전부 로컬 계산 — 서버/레이팅과 무관.
  useEffect(() => {
    if (!difficulty || result || turn !== AI) return;

    setThinking(true);
    aiTimerRef.current = setTimeout(() => {
      const move = chooseAiMove(board, AI, difficulty);
      if (!move) {
        setResult('DRAW');
        setThinking(false);
        return;
      }
      if (applyMove(move.row, move.col, AI)) setTurn(HUMAN);
      setThinking(false);
    }, 450 + Math.random() * 350);

    return () => {
      if (aiTimerRef.current) clearTimeout(aiTimerRef.current);
    };
  }, [turn, result, difficulty, board, applyMove]);

  useEffect(() => () => {
    if (aiTimerRef.current) clearTimeout(aiTimerRef.current);
  }, []);

  const currentMeta = DIFFICULTIES.find((d) => d.key === difficulty);

  // ── 난이도 선택 화면 ──
  if (!difficulty) {
    return (
      <div className={styles.page}>
        <div className={styles.setupCard}>
          <span className={styles.practiceBadge}>혼자 연습 · 레이팅 미반영</span>
          <h1 className={styles.setupTitle}>AI와 한 판</h1>
          <p className={styles.setupSub}>
            난이도를 고르면 바로 시작합니다. 당신이 <b>흑(선공)</b>이에요.
          </p>
          <div className={styles.diffGrid}>
            {DIFFICULTIES.map((d) => (
              <button key={d.key} className={styles.diffCard} onClick={() => startGame(d.key)}>
                <span className={styles.diffEmoji}>{d.emoji}</span>
                <span className={styles.diffLabel}>{d.label}</span>
                <span className={styles.diffDesc}>{d.desc}</span>
              </button>
            ))}
          </div>
          <button className={styles.backLink} onClick={() => navigate('/')}>
            ← 홈으로
          </button>
        </div>
      </div>
    );
  }

  // ── 대국 화면 ──
  const statusText = result
    ? result === 'WIN'
      ? '🎉 승리했습니다!'
      : result === 'LOSS'
        ? '😢 패배했습니다.'
        : '무승부입니다.'
    : thinking
      ? 'AI가 생각 중...'
      : turn === HUMAN
        ? '당신 차례입니다 (흑돌)'
        : 'AI 차례입니다 (백돌)';

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <span className={styles.practiceBadge}>
          {currentMeta?.emoji} {currentMeta?.label} · 레이팅 미반영
        </span>
        <span className={styles.moveCount}>수: {moveCount}</span>
        <button className={styles.quitBtn} onClick={() => setDifficulty(null)}>
          그만두기
        </button>
      </div>

      <p className={`${styles.status} ${result ? styles.statusDone : ''}`}>{statusText}</p>

      <div className={styles.boardWrap}>
        <OmokBoard
          board={board}
          currentTurn={turn}
          myColor={HUMAN}
          onPlaceStone={handleHumanMove}
          lastMove={lastMove}
          disabled={!!result || thinking || turn !== HUMAN}
          skinConfig={boardSkinConfig}
          blackStoneSkin={stoneSkin}      // 사람=흑: 내 장착 바둑알 스킨
          blackStoneEffect={stoneEffect}  // 내 장착 착수 효과(예: 'bounce')
        />

        {result && (
          <div className={styles.resultOverlay}>
            {/* 내가 이겼고 승리 이펙트를 장착했다면 일반전과 같은 연출을 깐다(내 코스메틱). */}
            {result === 'WIN' && winEffectClass(defeatEffect) && (
              <div className={`${gameStyles.resultEffect} ${winEffectClass(defeatEffect)}`} aria-hidden="true" />
            )}
            <div className={styles.resultCard}>
              <p
                className={
                  result === 'WIN'
                    ? styles.resultWin
                    : result === 'LOSS'
                      ? styles.resultLoss
                      : styles.resultDraw
                }
              >
                {result === 'WIN' ? '승리!' : result === 'LOSS' ? '패배' : '무승부'}
              </p>
              <p className={styles.resultSub}>
                {currentMeta?.emoji} {currentMeta?.label} 난이도
              </p>
              <div className={styles.resultBtns}>
                <button className={styles.againBtn} onClick={() => startGame(difficulty)}>
                  다시 하기
                </button>
                <button className={styles.ghostBtn} onClick={() => setDifficulty(null)}>
                  난이도 변경
                </button>
                <button className={styles.ghostBtn} onClick={() => navigate('/')}>
                  홈으로
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AiGamePage;
