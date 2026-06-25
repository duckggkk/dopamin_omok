import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Board, StoneColor } from '@/types';
import { createEmptyBoard } from '@/constants/board';
import { checkWin, isBoardFull, placeStone } from '@/utils/omokEngine';
import { AI_LEVELS, MAX_AI_LEVEL, chooseAiMove } from '@/utils/omokAi';
import { aiGameApi } from '@/api/aiGame';
import { useAuthStore } from '@/store/authStore';
import { useCosmetics } from '@/hooks/useCosmetics';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { useToast } from '@/contexts/ToastContext';
import OmokBoard from '@/components/game/OmokBoard';
import { winEffectClass } from '@/components/game/GameResultOverlay';
import styles from './AiGamePage.module.css';
import gameStyles from './GamePage.module.css';

type Result = 'WIN' | 'LOSS' | 'DRAW';

const AiGamePage = () => {
  const navigate = useNavigate();
  // 게스트(비회원)에겐 AI 사다리가 곧 홈이라, '홈으로'(=멤버 홈) 버튼은 숨긴다.
  const isGuest = !!useAuthStore((s) => s.user?.guest);
  // 내가 장착한 코스메틱(보드/돌 스킨·착수 효과·착수음·승리 이펙트)을 일반전과 동일하게 적용.
  const { boardSkinConfig, stoneSkin, stoneEffect, stoneSoundKey, defeatEffect } = useCosmetics();
  const playStoneSound = useStoneSoundPlayer();
  const showToast = useToast();

  // 서버(계정)에 저장된 진척: 클리어한 최고 단계. null = 아직 불러오는 중.
  const [clearedLevel, setClearedLevel] = useState<number | null>(null);
  // 현재 대국 중인 단계(1~9). null = 단계 선택 화면.
  const [level, setLevel] = useState<number | null>(null);

  const [board, setBoard] = useState<Board>(createEmptyBoard());
  const [turn, setTurn] = useState<StoneColor>('BLACK'); // 흑 선공 고정
  const [lastMove, setLastMove] = useState<{ row: number; col: number } | null>(null);
  const [result, setResult] = useState<Result | null>(null);
  const [thinking, setThinking] = useState(false);
  const [moveCount, setMoveCount] = useState(0);

  const aiTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reportedRef = useRef(false); // 한 판당 클리어 보고 1회만

  // 현재 단계 메타 + 색 배치. 8~9 단계는 사람이 백(후공) → AI 가 흑(선공)으로 먼저 둔다.
  const meta = level != null ? AI_LEVELS[level - 1] : null;
  const humanColor: StoneColor = meta?.humanPlaysWhite ? 'WHITE' : 'BLACK';
  const aiColor: StoneColor = humanColor === 'BLACK' ? 'WHITE' : 'BLACK';
  const humanStoneLabel = humanColor === 'BLACK' ? '흑돌' : '백돌';
  const aiStoneLabel = aiColor === 'BLACK' ? '흑돌' : '백돌';

  // ── 진척 불러오기(마운트 1회) ──
  useEffect(() => {
    let active = true;
    aiGameApi.getProgress()
      .then((res) => { if (active) setClearedLevel(res.data.data?.clearedLevel ?? 0); })
      .catch(() => {
        if (!active) return;
        setClearedLevel(0);
        showToast('진행 정보를 불러오지 못했어요. 1단계부터 도전할 수 있어요.', 'error');
      });
    return () => { active = false; };
  }, [showToast]);

  const startGame = useCallback((lv: number) => {
    if (aiTimerRef.current) clearTimeout(aiTimerRef.current);
    reportedRef.current = false;
    setLevel(lv);
    setBoard(createEmptyBoard());
    setTurn('BLACK');
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
      playStoneSound(color === humanColor ? stoneSoundKey : null);

      if (checkWin(next, row, col)) {
        setResult(color === humanColor ? 'WIN' : 'LOSS');
        return false;
      }
      if (isBoardFull(next)) {
        setResult('DRAW');
        return false;
      }
      return true;
    },
    [board, playStoneSound, stoneSoundKey, humanColor],
  );

  const handleHumanMove = (row: number, col: number) => {
    if (result || thinking || turn !== humanColor || board[row][col] !== null) return;
    if (applyMove(row, col, humanColor)) setTurn(aiColor);
  };

  // AI 차례: 잠깐 '생각'한 뒤(체감용 지연) 한 수를 둔다. 전부 로컬 계산 — 서버/레이팅과 무관.
  useEffect(() => {
    if (level == null || result || turn !== aiColor) return;

    setThinking(true);
    aiTimerRef.current = setTimeout(() => {
      const move = chooseAiMove(board, aiColor, level);
      if (!move) {
        setResult('DRAW');
        setThinking(false);
        return;
      }
      if (applyMove(move.row, move.col, aiColor)) setTurn(humanColor);
      setThinking(false);
    }, 450 + Math.random() * 350);

    return () => {
      if (aiTimerRef.current) clearTimeout(aiTimerRef.current);
    };
  }, [turn, result, level, board, applyMove, aiColor, humanColor]);

  // 승리 시 클리어를 서버에 보고(계정 저장). 도메인이 '다음 단계만 전진'을 보장하므로 항상 보내도 안전.
  useEffect(() => {
    if (result !== 'WIN' || level == null || reportedRef.current) return;
    reportedRef.current = true;
    const before = clearedLevel ?? 0;
    aiGameApi.reportClear(level)
      .then((res) => {
        const data = res.data.data;
        if (!data) return;
        setClearedLevel(data.clearedLevel);
        if (data.clearedLevel > before) {
          if (data.clearedLevel >= MAX_AI_LEVEL) {
            showToast('🐉 모든 단계를 정복했습니다! 진정한 오목 마스터!', 'success');
          } else {
            showToast(`🎉 클리어! ${data.clearedLevel + 1}단계가 열렸어요.`, 'success');
          }
        }
      })
      .catch(() => {});
  }, [result, level, clearedLevel, showToast]);

  useEffect(() => () => {
    if (aiTimerRef.current) clearTimeout(aiTimerRef.current);
  }, []);

  // ── 단계 선택(사다리) 화면 ──
  if (level == null) {
    const cleared = clearedLevel ?? 0;
    return (
      <div className={styles.page}>
        <div className={styles.setupCard}>
          <span className={styles.practiceBadge}>혼자 연습 · 레이팅 미반영</span>
          <h1 className={styles.setupTitle}>AI 사다리</h1>
          <p className={styles.setupSub}>
            한 단계를 깨면 다음 단계가 열려요. 진행도는 <b>계정에 저장</b>됩니다.
          </p>

          {clearedLevel == null ? (
            <p className={styles.loading}>진행 정보를 불러오는 중...</p>
          ) : (
            <>
              <p className={styles.progressLine}>
                클리어 <b>{cleared}</b> / {MAX_AI_LEVEL}
              </p>
              <ol className={styles.ladder}>
                {AI_LEVELS.map((lv) => {
                  const isCleared = lv.level <= cleared;
                  const isCurrent = lv.level === cleared + 1;
                  const unlocked = isCleared || isCurrent;
                  const status = isCleared ? '✓ 클리어' : isCurrent ? '도전!' : '🔒 잠김';
                  return (
                    <li key={lv.level}>
                      <button
                        type="button"
                        className={[
                          styles.rung,
                          isCleared ? styles.rungCleared : '',
                          isCurrent ? styles.rungCurrent : '',
                          !unlocked ? styles.rungLocked : '',
                        ].join(' ')}
                        disabled={!unlocked}
                        onClick={() => unlocked && startGame(lv.level)}
                      >
                        <span className={styles.rungEmoji}>{unlocked ? lv.emoji : '🔒'}</span>
                        <span className={styles.rungText}>
                          <span className={styles.rungLabel}>{lv.label}</span>
                          <span className={styles.rungDesc}>{lv.desc}</span>
                        </span>
                        <span className={styles.rungStatus}>{status}</span>
                      </button>
                    </li>
                  );
                })}
              </ol>
            </>
          )}

          {!isGuest && (
            <button className={styles.backLink} onClick={() => navigate('/')}>
              ← 홈으로
            </button>
          )}
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
      : turn === humanColor
        ? `당신 차례입니다 (${humanStoneLabel})`
        : `AI 차례입니다 (${aiStoneLabel})`;

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <span className={styles.practiceBadge}>
          {meta?.emoji} {meta?.label} · 레이팅 미반영
        </span>
        <span className={styles.moveCount}>수: {moveCount}</span>
        <button className={styles.quitBtn} onClick={() => setLevel(null)}>
          그만두기
        </button>
      </div>

      {meta?.humanPlaysWhite && (
        <p className={styles.handicapNote}>이 단계부터 당신은 <b>백(후공)</b> — AI가 먼저 둡니다.</p>
      )}

      <p className={`${styles.status} ${result ? styles.statusDone : ''}`}>{statusText}</p>

      <div className={styles.boardWrap}>
        <OmokBoard
          board={board}
          currentTurn={turn}
          myColor={humanColor}
          onPlaceStone={handleHumanMove}
          lastMove={lastMove}
          disabled={!!result || thinking || turn !== humanColor}
          skinConfig={boardSkinConfig}
          // 내 색에만 내 장착 스킨/효과를 적용(8~9단계는 내가 백).
          blackStoneSkin={humanColor === 'BLACK' ? stoneSkin : null}
          whiteStoneSkin={humanColor === 'WHITE' ? stoneSkin : null}
          blackStoneEffect={humanColor === 'BLACK' ? stoneEffect : null}
          whiteStoneEffect={humanColor === 'WHITE' ? stoneEffect : null}
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
                {meta?.emoji} {meta?.label}
              </p>
              <div className={styles.resultBtns}>
                {/* 이겨서 다음 단계가 열렸을 때만 '다음 단계로'(마지막 단계 제외). 가장 먼저 노출. */}
                {result === 'WIN' && level < MAX_AI_LEVEL && (
                  <button className={styles.againBtn} onClick={() => startGame(level + 1)}>
                    다음 단계로 →
                  </button>
                )}
                <button
                  className={result === 'WIN' && level < MAX_AI_LEVEL ? styles.ghostBtn : styles.againBtn}
                  onClick={() => startGame(level)}
                >
                  다시 하기
                </button>
                <button className={styles.ghostBtn} onClick={() => setLevel(null)}>
                  뒤로가기
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
