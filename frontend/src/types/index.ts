export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'KAKAO' | 'NAVER';
export type UserRole = 'USER' | 'ADMIN';
export type GameStatus = 'WAITING' | 'IN_PROGRESS' | 'FINISHED' | 'DRAW' | 'ABANDONED';
export type StoneColor = 'BLACK' | 'WHITE';

export interface User {
  id: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  provider: AuthProvider;
  wins: number;
  losses: number;
  draws: number;
  totalGames: number;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface GameRoom {
  id: number;
  roomCode: string;
  status: GameStatus;
  blackPlayer: User | null;
  whitePlayer: User | null;
  winner: User | null;
  currentTurn: StoneColor | null;
  boardSize: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface GameMove {
  id: number;
  gameId: number;
  playerId: number;
  playerNickname: string;
  color: StoneColor;
  row: number;
  col: number;
  moveNumber: number;
  createdAt: string;
}

export type Board = (StoneColor | null)[][];
