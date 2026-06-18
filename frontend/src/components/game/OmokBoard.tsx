import React, { useCallback } from 'react';
import { Board, ItemConfig, SkinColors, SkinFilter, StoneColor, StoneStyle } from '@/types';
import { useProtectedAsset } from '@/hooks/useProtectedAsset';
import { DEFAULT_BLACK_STONE, DEFAULT_WHITE_STONE } from '@/utils/stoneSkin';
import { BOARD_SIZE, CELL_SIZE, PADDING, BOARD_PX, STAR_POINTS } from '@/constants/board';

interface OmokBoardProps {
  board: Board;
  currentTurn: StoneColor | null;
  myColor: StoneColor | null;
  onPlaceStone: (row: number, col: number) => void;
  lastMove: { row: number; col: number } | null;
  disabled?: boolean;
  skinConfig?: ItemConfig | null;
  // 흑/백 플레이어의 장착 바둑알 스킨 (서버가 user_active_items 에서 읽어 내려준 값, 미장착 null)
  blackStoneSkin?: StoneStyle | null;
  whiteStoneSkin?: StoneStyle | null;
  // 흑/백 플레이어의 착수 효과 키 (서버 권위 값, 예: 'bounce'). 없으면 기본 — 효과 없이 그냥 착수
  blackStoneEffect?: string | null;
  whiteStoneEffect?: string | null;
}

// 기본 스킨 색상/필터 — DB 미보유/미장착 시 폴백 (유일하게 허용된 프론트 하드코딩)
const CLASSIC_COLORS: SkinColors = { bg: '#dcb95b', lines: '#8b6914', dots: '#8b6914' };
const CLASSIC_FILTER: SkinFilter = {
  type: 'fractalNoise', freqX: 0.65, freqY: 0.06, octaves: 4, seed: 3, blend: 'overlay',
};

const OmokBoard: React.FC<OmokBoardProps> = ({
  board,
  currentTurn,
  myColor,
  onPlaceStone,
  lastMove,
  disabled = false,
  skinConfig,
  blackStoneSkin,
  whiteStoneSkin,
  blackStoneEffect,
  whiteStoneEffect,
}) => {
  // 장착 스킨이 colors/filter를 안 주면(예: 이미지 스킨) 기본값으로 보완
  const colors = skinConfig?.colors ?? CLASSIC_COLORS;
  const filter = skinConfig
    ? skinConfig.filter ?? null            // 장착 스킨은 자신의 filter만 사용 (없으면 이미지 기반)
    : CLASSIC_FILTER;                       // 미장착 시 기본 스킨 필터
  const assetKey = skinConfig?.assetKey;
  const canPlace = !disabled && currentTurn === myColor;
  // assetKey가 있는 스킨만 백엔드 보호 이미지를 fetch (없으면 null)
  const skinBlobUrl = useProtectedAsset('BOARD_SKIN', assetKey);

  // 흑/백 바둑알 스타일 — 장착 스킨 우선, 미장착 시 기본값. (스킨은 서버 권위 값이라 신뢰 가능)
  const blackStyle = blackStoneSkin ?? DEFAULT_BLACK_STONE;
  const whiteStyle = whiteStoneSkin ?? DEFAULT_WHITE_STONE;
  const styleOf = (color: StoneColor) => (color === 'BLACK' ? blackStyle : whiteStyle);

  // 착수 효과 — 기본은 효과 없음. 'bounce'(유료) 보유자만 마지막 돌이 "뽀잉".
  const effectOf = (color: StoneColor) =>
    color === 'BLACK' ? blackStoneEffect : whiteStoneEffect;

  const handleClick = useCallback(
    (row: number, col: number) => {
      if (!canPlace || board[row][col] !== null) return;
      onPlaceStone(row, col);
    },
    [canPlace, board, onPlaceStone],
  );

  return (
    <div style={{ overflowX: 'auto', padding: '8px', maxWidth: '100%' }}>
      {/* 실제 바둑판 같은 원목 프레임 (베벨 + 그림자) */}
      <div
        style={{
          padding: 16,
          borderRadius: 16,
          background: 'linear-gradient(135deg, #6b4d2c 0%, #3f2c19 55%, #543b23 100%)',
          border: '1px solid #2a1d11',
          boxShadow:
            'inset 0 2px 4px rgba(255,222,176,0.18), inset 0 -3px 9px rgba(0,0,0,0.5), 0 22px 48px -14px rgba(0,0,0,0.75)',
        }}
      >
        <svg
          width={BOARD_PX}
          height={BOARD_PX}
          style={{ display: 'block', cursor: canPlace ? 'crosshair' : 'default', borderRadius: 6 }}
        >
        <defs>
          {/* 스킨 설정 기반 동적 텍스처 필터 */}
          {filter && (
            <filter id="board-texture" x="-2%" y="-2%" width="104%" height="104%"
                    colorInterpolationFilters="sRGB">
              <feTurbulence
                type={filter.type as 'fractalNoise' | 'turbulence'}
                baseFrequency={`${filter.freqX} ${filter.freqY}`}
                numOctaves={filter.octaves}
                seed={filter.seed}
                result="noise"
              />
              <feColorMatrix type="saturate" values="0" in="noise" result="grain" />
              <feBlend in="SourceGraphic" in2="grain" mode={filter.blend as 'overlay'} />
            </filter>
          )}

          {/* 돌 광택 그라디언트 — 스킨 shine 색을 중심부 하이라이트로 사용 (없으면 기본색) */}
          <radialGradient id="stone-black-shine" cx="35%" cy="30%" r="55%">
            <stop offset="0%" stopColor={blackStyle.shine} stopOpacity="0.6" />
            <stop offset="100%" stopColor={blackStyle.shine} stopOpacity="0" />
          </radialGradient>
          <radialGradient id="stone-white-shine" cx="35%" cy="30%" r="55%">
            <stop offset="0%" stopColor={whiteStyle.shine} stopOpacity="0.75" />
            <stop offset="100%" stopColor={whiteStyle.shine} stopOpacity="0" />
          </radialGradient>

          {/* 마지막 착수 돌 "뽀잉" 등장 애니메이션 (고퀄 스킨 확장 시 Lottie 등으로 대체 가능) */}
          <style>{`
            @keyframes stone-pop {
              0%   { transform: scale(0.2); }
              55%  { transform: scale(1.22); }
              75%  { transform: scale(0.92); }
              100% { transform: scale(1); }
            }
            .stone-pop {
              animation: stone-pop 0.62s cubic-bezier(0.34, 1.56, 0.64, 1) both;
              transform-origin: center;
              transform-box: fill-box;
            }
          `}</style>
        </defs>

        {/* 바둑판 배경
            - skinBlobUrl 있음: 백엔드 인증 텍스처 이미지 (assetKey 스킨)
            - filter 있음: SVG 필터로 즉석 생성 (필터 스킨)
            - 둘 다 없음: 단색 */}
        {skinBlobUrl ? (
          <image
            href={skinBlobUrl}
            width={BOARD_PX}
            height={BOARD_PX}
            preserveAspectRatio="xMidYMid slice"
          />
        ) : (
          <rect
            width={BOARD_PX}
            height={BOARD_PX}
            fill={colors.bg}
            rx={4}
            filter={filter ? 'url(#board-texture)' : undefined}
          />
        )}

        {/* 격자선 */}
        {Array.from({ length: BOARD_SIZE }, (_, i) => (
          <React.Fragment key={`line-${i}`}>
            <line
              x1={PADDING + i * CELL_SIZE} y1={PADDING}
              x2={PADDING + i * CELL_SIZE} y2={PADDING + (BOARD_SIZE - 1) * CELL_SIZE}
              stroke={colors.lines} strokeWidth={1}
            />
            <line
              x1={PADDING} y1={PADDING + i * CELL_SIZE}
              x2={PADDING + (BOARD_SIZE - 1) * CELL_SIZE} y2={PADDING + i * CELL_SIZE}
              stroke={colors.lines} strokeWidth={1}
            />
          </React.Fragment>
        ))}

        {/* 화점 */}
        {STAR_POINTS.flatMap((r) =>
          STAR_POINTS.map((c) => (
            <circle key={`dot-${r}-${c}`}
              cx={PADDING + c * CELL_SIZE} cy={PADDING + r * CELL_SIZE}
              r={3} fill={colors.dots}
            />
          )),
        )}

        {/* 클릭 영역 */}
        {Array.from({ length: BOARD_SIZE }, (_, row) =>
          Array.from({ length: BOARD_SIZE }, (_, col) => (
            <rect key={`cell-${row}-${col}`}
              x={PADDING + col * CELL_SIZE - CELL_SIZE / 2}
              y={PADDING + row * CELL_SIZE - CELL_SIZE / 2}
              width={CELL_SIZE} height={CELL_SIZE}
              fill="transparent"
              onClick={() => handleClick(row, col)}
            />
          )),
        )}

        {/* 돌 — 색별 스킨(fill/stroke/shine) 적용. 마지막 착수 돌은 "뽀잉" 등장 */}
        {board.flatMap((rowArr, row) =>
          rowArr.map((color, col) => {
            if (!color) return null;
            const isLast = lastMove?.row === row && lastMove?.col === col;
            const s = styleOf(color);
            // 기본 돌은 그냥 착수. 마지막 착수 돌이면서 그 색 플레이어가 'bounce' 효과 보유 시에만 "뽀잉".
            const shouldPop = isLast && effectOf(color) === 'bounce';
            const cx = PADDING + col * CELL_SIZE;
            const cy = PADDING + row * CELL_SIZE;
            const r = CELL_SIZE / 2 - 2;
            return (
              <g key={`stone-${row}-${col}`} className={shouldPop ? 'stone-pop' : undefined}>
                <circle cx={cx} cy={cy} r={r}
                  fill={s.fill}
                  stroke={s.stroke}
                  strokeWidth={1}
                  style={{
                    filter: color === 'BLACK'
                      ? 'drop-shadow(1px 2px 3px rgba(0,0,0,0.6))'
                      : 'drop-shadow(1px 2px 3px rgba(0,0,0,0.25))',
                  }}
                />
                <circle cx={cx} cy={cy} r={r}
                  fill={color === 'BLACK' ? 'url(#stone-black-shine)' : 'url(#stone-white-shine)'}
                  style={{ pointerEvents: 'none' }}
                />
                {isLast && (
                  <circle cx={cx} cy={cy} r={5}
                    fill={color === 'BLACK' ? '#ff4444' : '#333'}
                    style={{ pointerEvents: 'none' }}
                  />
                )}
              </g>
            );
          }),
        )}
        </svg>
      </div>
    </div>
  );
};

export default OmokBoard;
