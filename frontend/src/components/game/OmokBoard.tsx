import React, { useCallback } from 'react';
import { Board, ItemConfig, SkinColors, SkinFilter, StoneColor } from '@/types';
import { useProtectedAsset } from '@/hooks/useProtectedAsset';

interface OmokBoardProps {
  board: Board;
  currentTurn: StoneColor | null;
  myColor: StoneColor | null;
  onPlaceStone: (row: number, col: number) => void;
  lastMove: { row: number; col: number } | null;
  disabled?: boolean;
  skinConfig?: ItemConfig | null;
}

const BOARD_SIZE = 15;
const CELL_SIZE = 40;
const PADDING = 24;
const BOARD_PX = PADDING * 2 + CELL_SIZE * (BOARD_SIZE - 1);

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

  const handleClick = useCallback(
    (row: number, col: number) => {
      if (!canPlace || board[row][col] !== null) return;
      onPlaceStone(row, col);
    },
    [canPlace, board, onPlaceStone],
  );

  const getStoneColor = (color: StoneColor) =>
    color === 'BLACK' ? '#1a1a1a' : '#f5f5f0';

  return (
    <div style={{ overflowX: 'auto', padding: '8px' }}>
      <svg
        width={BOARD_PX}
        height={BOARD_PX}
        style={{ display: 'block', cursor: canPlace ? 'crosshair' : 'default' }}
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

          {/* 돌 광택 그라디언트 */}
          <radialGradient id="stone-black-shine" cx="35%" cy="30%" r="55%">
            <stop offset="0%" stopColor="#666" stopOpacity="0.6" />
            <stop offset="100%" stopColor="#000" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="stone-white-shine" cx="35%" cy="30%" r="55%">
            <stop offset="0%" stopColor="#fff" stopOpacity="0.7" />
            <stop offset="100%" stopColor="#ccc" stopOpacity="0" />
          </radialGradient>
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
        {[3, 7, 11].flatMap((r) =>
          [3, 7, 11].map((c) => (
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

        {/* 돌 — 광택 그라디언트 적용 */}
        {board.flatMap((rowArr, row) =>
          rowArr.map((color, col) => {
            if (!color) return null;
            const isLast = lastMove?.row === row && lastMove?.col === col;
            const cx = PADDING + col * CELL_SIZE;
            const cy = PADDING + row * CELL_SIZE;
            const r = CELL_SIZE / 2 - 2;
            return (
              <g key={`stone-${row}-${col}`}>
                <circle cx={cx} cy={cy} r={r}
                  fill={getStoneColor(color)}
                  stroke={color === 'BLACK' ? '#000' : '#bbb'}
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
  );
};

export default OmokBoard;
