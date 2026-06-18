import { useId, useState, useRef, useEffect, KeyboardEvent as ReactKeyboardEvent } from 'react';
import { ItemConfig, StoneStyle, SkinColors, SkinFilter, CharacterStyle, StoneColor } from '@/types';
import { useProtectedAsset } from '@/hooks/useProtectedAsset';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { playSfx } from '@/utils/sfx';
import styles from './PreviewBoard.module.css';

const DEFAULT_COLORS: SkinColors = { bg: '#dcb95b', lines: '#8b6914', dots: '#8b6914' };
const DEFAULT_FILTER: SkinFilter = { type: 'fractalNoise', freqX: 0.65, freqY: 0.06, octaves: 4, seed: 3, blend: 'overlay' };
const BLACK_STONE: StoneStyle = { fill: '#1a1a1a', stroke: '#000000', shine: '#777777' };
const WHITE_STONE: StoneStyle = { fill: '#f5f5f0', stroke: '#bbbbbb', shine: '#ffffff' };
const FACE_EMOJI: Record<string, string> = { robot: '🤖', rabbit: '🐰', ghost: '👻', cat: '🐱', fox: '🦊', bear: '🐻' };
const clamp = (v: number, min: number, max: number) => (v < min ? min : v > max ? max : v);

// 승패 이펙트 키 → 바둑판 위 연출 클래스 (게임 결과 화면과 동일한 4종)
const WIN_FX_CLASS: Record<string, string> = {
  flame: styles.fxWinFlame,
  shatter: styles.fxWinShatter,
  storm: styles.fxWinStorm,
  tears: styles.fxWinTears,
};
const LOSS_FX_CLASS: Record<string, string> = {
  flame: styles.fxFlame,
  shatter: styles.fxShatter,
  storm: styles.fxStorm,
  tears: styles.fxTears,
};

// 미리보기 9x9 바둑판
const N = 9;
const CELL = 28;
const PAD = 18;
const PX = PAD * 2 + CELL * (N - 1); // 260

interface Placed { r: number; c: number; color: StoneColor; id: number; pop: boolean }

interface Props {
  boardCfg?: ItemConfig | null;
  stoneStyle?: StoneStyle | null;
  effect?: string | null;
  soundKey?: string | null;
  character?: CharacterStyle | null;
  defeatText?: string | null;
  defeatEffect?: string | null;
  resultPreview?: 'win' | 'loss';
  // 'physical'이면 캐릭터를 바둑판에 올려 피지컬 오목 분위기로 보여준다.
  variant?: 'classic' | 'physical';
}

const PreviewBoard = ({
  boardCfg,
  stoneStyle,
  effect,
  soundKey,
  character,
  defeatText,
  defeatEffect,
  resultPreview = 'loss',
  variant = 'classic',
}: Props) => {
  const uid = useId().replace(/:/g, '');
  const isPhysical = variant === 'physical';
  const boardImg = useProtectedAsset('BOARD_SKIN', boardCfg?.assetKey ?? null);
  const playStoneSound = useStoneSoundPlayer();

  const [stones, setStones] = useState<Placed[]>([]);
  const nextId = useRef(0);
  // 피지컬 모드: 캐릭터 위치(방향키로 이동, 스페이스로 그 칸에 착수)
  const CENTER_CELL = (N - 1) / 2;
  const [avatar, setAvatar] = useState({ r: CENTER_CELL, c: CENTER_CELL });
  const holderRef = useRef<HTMLDivElement>(null);

  const colors = boardCfg?.colors ?? DEFAULT_COLORS;
  const filter = boardCfg ? boardCfg.filter ?? null : DEFAULT_FILTER;
  const blackStyle = stoneStyle ?? BLACK_STONE;

  const x = (i: number) => PAD + i * CELL;
  const radius = CELL / 2 - 2;

  const addStone = (r: number, c: number, color: StoneColor) => {
    if (stones.some((s) => s.r === r && s.c === c)) return; // 이미 둔 자리
    setStones((prev) => [...prev, { r, c, color, id: nextId.current++, pop: !!effect }]);
    if (soundKey) playStoneSound(soundKey); // 선택 착수음 우선, 없으면 기본 합성음
    else playSfx('place');
  };

  // 클래식: 클릭으로 흑↔백 번갈아 착수
  const placeByClick = (r: number, c: number) =>
    addStone(r, c, stones.length % 2 === 0 ? 'BLACK' : 'WHITE');

  // 피지컬: 방향키로 캐릭터 이동, 스페이스로 캐릭터 위치에 내 돌(흑/선택 스킨) 착수
  const handlePhysicalKey = (e: ReactKeyboardEvent<HTMLDivElement>) => {
    let dr = 0, dc = 0;
    if (e.key === 'ArrowUp') dr = -1;
    else if (e.key === 'ArrowDown') dr = 1;
    else if (e.key === 'ArrowLeft') dc = -1;
    else if (e.key === 'ArrowRight') dc = 1;
    else if (e.key === ' ' || e.key === 'Spacebar') {
      e.preventDefault();
      addStone(avatar.r, avatar.c, 'BLACK');
      return;
    } else return;
    e.preventDefault();
    setAvatar((p) => ({ r: clamp(p.r + dr, 0, N - 1), c: clamp(p.c + dc, 0, N - 1) }));
  };

  // 모드 전환 시 기존 착수 상태는 섞이지 않도록 초기화한다.
  useEffect(() => {
    setStones([]);
    nextId.current = 0;
    if (isPhysical) {
      setAvatar({ r: CENTER_CELL, c: CENTER_CELL });
      holderRef.current?.focus();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isPhysical]);

  const lastId = stones.length ? stones[stones.length - 1].id : -1;
  const hasResultPreview = !!(defeatText || defeatEffect);
  const isWinPreview = resultPreview === 'win' && !!defeatEffect;
  const resultEffectClass = defeatEffect
    ? isWinPreview ? WIN_FX_CLASS[defeatEffect] : LOSS_FX_CLASS[defeatEffect]
    : null;
  const resultText = isWinPreview ? '승리!' : defeatText ?? '패배';

  return (
    <div className={styles.wrap}>
      <div
        className={`${styles.boardHolder} ${isPhysical ? styles.boardFocusable : ''}`}
        ref={holderRef}
        tabIndex={isPhysical ? 0 : -1}
        onKeyDown={isPhysical ? handlePhysicalKey : undefined}
      >
        <svg width={PX} height={PX} viewBox={`0 0 ${PX} ${PX}`} className={styles.boardSvg}>
          <defs>
            {filter && (
              <filter id={`ptex-${uid}`} x="-2%" y="-2%" width="104%" height="104%" colorInterpolationFilters="sRGB">
                <feTurbulence type={filter.type as 'fractalNoise' | 'turbulence'}
                  baseFrequency={`${filter.freqX} ${filter.freqY}`} numOctaves={filter.octaves} seed={filter.seed} result="noise" />
                <feColorMatrix type="saturate" values="0" in="noise" result="grain" />
                <feBlend in="SourceGraphic" in2="grain" mode={filter.blend as 'overlay'} />
              </filter>
            )}
          </defs>

          {boardImg ? (
            <image href={boardImg} width={PX} height={PX} preserveAspectRatio="xMidYMid slice" />
          ) : (
            <rect width={PX} height={PX} fill={colors.bg} rx={4} filter={filter ? `url(#ptex-${uid})` : undefined} />
          )}

          {Array.from({ length: N }, (_, i) => (
            <g key={i} stroke={colors.lines} strokeWidth={1}>
              <line x1={x(i)} y1={PAD} x2={x(i)} y2={PAD + (N - 1) * CELL} />
              <line x1={PAD} y1={x(i)} x2={PAD + (N - 1) * CELL} y2={x(i)} />
            </g>
          ))}
          <circle cx={x(4)} cy={x(4)} r={2.5} fill={colors.dots} />

          {/* 놓인 돌 — 흑은 선택한 바둑알 스킨, 백은 기본 (대비). 효과 보유 시 등장 모션 */}
          {stones.map((s) => {
            const style = s.color === 'BLACK' ? blackStyle : WHITE_STONE;
            const anim = s.pop ? styles.popStone : styles.appear;
            return (
              <g key={s.id} className={anim}>
                <circle cx={x(s.c)} cy={x(s.r)} r={radius} fill={style.fill} stroke={style.stroke} strokeWidth={1}
                  style={{ filter: 'drop-shadow(0.5px 1.5px 2px rgba(0,0,0,0.45))' }} />
                <circle cx={x(s.c) - radius * 0.32} cy={x(s.r) - radius * 0.34} r={radius * 0.42} fill={style.shine} opacity={0.55} />
                {s.id === lastId && (
                  <circle cx={x(s.c)} cy={x(s.r)} r={4} fill={s.color === 'BLACK' ? '#ff5252' : '#444'} />
                )}
              </g>
            );
          })}

          {/* 피지컬 모드 — 내 캐릭터(CHARACTER_SKIN). 방향키로 움직이는 위치에 렌더 */}
          {isPhysical && (
            <g style={{ pointerEvents: 'none' }}>
              <ellipse cx={x(avatar.c)} cy={x(avatar.r) + 15} rx={15} ry={4} fill="rgba(0,0,0,0.22)" />
              <rect x={x(avatar.c) - 14} y={x(avatar.r) - 18} width={28} height={30} rx={10}
                fill={character?.body ?? '#7fb3d5'} stroke={character?.accent ?? '#2c3e50'} strokeWidth={2.5} />
              <text x={x(avatar.c)} y={x(avatar.r) - 3} textAnchor="middle" dominantBaseline="central" fontSize="18">
                {character ? FACE_EMOJI[character.face] ?? '🙂' : '🙂'}
              </text>
            </g>
          )}

          {/* 착수용 클릭 영역 — 클래식만(피지컬은 방향키+스페이스로 착수) */}
          {!isPhysical && Array.from({ length: N }, (_, r) =>
            Array.from({ length: N }, (_, c) => (
              <rect key={`hit-${r}-${c}`}
                x={x(c) - CELL / 2} y={x(r) - CELL / 2} width={CELL} height={CELL}
                fill="transparent" style={{ cursor: 'pointer' }}
                onClick={() => placeByClick(r, c)} />
            )),
          )}
        </svg>

        {/* 승패 문구·이펙트 — 실제 결과 화면의 승/패 차이를 작은 보드 위에서 분리해 보여준다. */}
        {hasResultPreview && (
          <div className={styles.resultOverlay}>
            {resultEffectClass && (
              <div className={`${styles.resultFx} ${resultEffectClass}`} aria-hidden="true" />
            )}
            <span className={isWinPreview ? styles.resultWinBanner : styles.resultLossBanner}>
              {resultText}
            </span>
            {defeatEffect && (
              <span className={styles.resultModeLabel}>
                {isWinPreview ? '승리 화면' : '패배 화면'}
              </span>
            )}
          </div>
        )}
      </div>

      <div className={styles.toolRow}>
        <span className={styles.hint}>
          {isPhysical ? '⌨️ 방향키로 이동 · 스페이스로 착수' : '바둑판을 눌러 직접 둬보세요'}
        </span>
        <button type="button" className={styles.clearBtn} onClick={() => setStones([])} disabled={stones.length === 0}>
          바둑판 비우기
        </button>
      </div>

    </div>
  );
};

export default PreviewBoard;
