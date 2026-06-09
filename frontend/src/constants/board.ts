import { Board } from '@/types';

// 오목판 기하/규격 상수 (백엔드 OmokGameEngine.BOARD_SIZE=15 와 일치)
export const BOARD_SIZE = 15;
export const CELL_SIZE = 40;
export const PADDING = 24;
export const BOARD_PX = PADDING * 2 + CELL_SIZE * (BOARD_SIZE - 1);

// 화점 좌표 (15줄 기준)
export const STAR_POINTS = [3, 7, 11];

export const createEmptyBoard = (): Board =>
  Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(null));
