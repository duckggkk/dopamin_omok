import React, { useCallback } from 'react';
import { Board, StoneColor } from '@/types';

interface OmokBoardProps {
  board: Board;
  currentTurn: StoneColor | null;
  myColor: StoneColor | null;
  onPlaceStone: (row: number, col: number) => void;
  lastMove: { row: number; col: number } | null;
  disabled?: boolean;
}

const BOARD_SIZE = 15;
const CELL_SIZE = 40;
const PADDING = 24;
const BOARD_PX = PADDING * 2 + CELL_SIZE * (BOARD_SIZE - 1);

const OmokBoard: React.FC<OmokBoardProps> = ({
  board,
  currentTurn,
  myColor,
  onPlaceStone,
  lastMove,
  disabled = false,
}) => {
  const canPlace = !disabled && currentTurn === myColor;

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
        {/* 바둑판 배경 */}
        <rect width={BOARD_PX} height={BOARD_PX} fill="#dcb95b" rx={4} />

        {/* 격자선 */}
        {Array.from({ length: BOARD_SIZE }, (_, i) => (
          <React.Fragment key={`line-${i}`}>
            <line
              x1={PADDING + i * CELL_SIZE}
              y1={PADDING}
              x2={PADDING + i * CELL_SIZE}
              y2={PADDING + (BOARD_SIZE - 1) * CELL_SIZE}
              stroke="#8b6914"
              strokeWidth={1}
            />
            <line
              x1={PADDING}
              y1={PADDING + i * CELL_SIZE}
              x2={PADDING + (BOARD_SIZE - 1) * CELL_SIZE}
              y2={PADDING + i * CELL_SIZE}
              stroke="#8b6914"
              strokeWidth={1}
            />
          </React.Fragment>
        ))}

        {/* 화점 */}
        {[3, 7, 11].flatMap((r) =>
          [3, 7, 11].map((c) => (
            <circle
              key={`dot-${r}-${c}`}
              cx={PADDING + c * CELL_SIZE}
              cy={PADDING + r * CELL_SIZE}
              r={3}
              fill="#8b6914"
            />
          )),
        )}

        {/* 클릭 영역 */}
        {Array.from({ length: BOARD_SIZE }, (_, row) =>
          Array.from({ length: BOARD_SIZE }, (_, col) => (
            <rect
              key={`cell-${row}-${col}`}
              x={PADDING + col * CELL_SIZE - CELL_SIZE / 2}
              y={PADDING + row * CELL_SIZE - CELL_SIZE / 2}
              width={CELL_SIZE}
              height={CELL_SIZE}
              fill="transparent"
              onClick={() => handleClick(row, col)}
            />
          )),
        )}

        {/* 돌 */}
        {board.flatMap((rowArr, row) =>
          rowArr.map((color, col) => {
            if (!color) return null;
            const isLast = lastMove?.row === row && lastMove?.col === col;
            return (
              <g key={`stone-${row}-${col}`}>
                <circle
                  cx={PADDING + col * CELL_SIZE}
                  cy={PADDING + row * CELL_SIZE}
                  r={CELL_SIZE / 2 - 2}
                  fill={getStoneColor(color)}
                  stroke={color === 'BLACK' ? '#000' : '#ccc'}
                  strokeWidth={1}
                  style={{
                    filter:
                      color === 'BLACK'
                        ? 'drop-shadow(1px 1px 2px rgba(0,0,0,0.5))'
                        : 'drop-shadow(1px 1px 2px rgba(0,0,0,0.3))',
                  }}
                />
                {isLast && (
                  <circle
                    cx={PADDING + col * CELL_SIZE}
                    cy={PADDING + row * CELL_SIZE}
                    r={5}
                    fill={color === 'BLACK' ? '#ff4444' : '#333'}
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
