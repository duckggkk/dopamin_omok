import apiClient from './client';
import { ApiResponse, Room, GameInfo, GameMove, PageResponse, ByoyomiOption, GameType, OmokRule, TimeLimit, PhysicalReplay } from '@/types';

export interface CreateRoomOptions {
  gameType: GameType;
  omokRule: OmokRule;
  ranked: boolean;       // true=랭크전(회원 전용), false=캐주얼
  timeLimit: TimeLimit;
  byoyomiOption: ByoyomiOption;
}

/** 대기 중 방 목록 필터. recommended가 true면 내 레이팅대 방만(서버가 내 레이팅 기준 ±밴드로 필터). */
export interface WaitingRoomFilter {
  gameType?: GameType;
  ranked?: boolean;      // true=랭크만, false=캐주얼만, 미지정=전체
  recommended?: boolean;
}

const DEFAULT_ROOM_OPTIONS: CreateRoomOptions = {
  gameType: 'CLASSIC',
  omokRule: 'FREESTYLE',
  ranked: true,
  timeLimit: 'UNLIMITED',
  byoyomiOption: 'NONE',
};

export const gameApi = {
  createRoom: (options: CreateRoomOptions = DEFAULT_ROOM_OPTIONS) =>
    apiClient.post<ApiResponse<Room>>('/rooms', options),

  // 피지컬 오목 AI 연습 — 봇과 즉시 한 판(레이팅 미반영). 생성된 방으로 입장한다.
  startAiPractice: () =>
    apiClient.post<ApiResponse<Room>>('/rooms/ai-practice'),

  // 로컬 개발 전용 — 상대 없이 나 혼자 들어가는 피지컬 아레나(움직임 확인용).
  // 백엔드는 local 프로파일에서만 이 엔드포인트를 등록한다.
  startPhysicalSandbox: () =>
    apiClient.post<ApiResponse<Room>>('/dev/physical/sandbox'),

  joinRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<Room>>(`/rooms/${roomCode}/join`),

  spectateRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<Room>>(`/rooms/${roomCode}/spectate`),

  leaveRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<void>>(`/rooms/${roomCode}/leave`),

  requestRematch: (roomCode: string) =>
    apiClient.post<ApiResponse<Room>>(`/rooms/${roomCode}/rematch`),

  getWaitingRooms: (page = 0, size = 10, filter: WaitingRoomFilter = {}) =>
    apiClient.get<ApiResponse<PageResponse<Room>>>('/rooms', {
      params: {
        page,
        size,
        ...(filter.gameType ? { gameType: filter.gameType } : {}),
        ...(filter.ranked !== undefined ? { ranked: filter.ranked } : {}),
        ...(filter.recommended ? { recommended: true } : {}),
      },
    }),

  getLiveRooms: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PageResponse<Room>>>('/rooms/live', {
      params: { page, size },
    }),

  getRoom: (roomCode: string) =>
    apiClient.get<ApiResponse<Room>>(`/rooms/${roomCode}`),

  getGame: (roomCode: string) =>
    apiClient.get<ApiResponse<GameInfo>>(`/rooms/${roomCode}/game`),

  getGameMoves: (roomCode: string) =>
    apiClient.get<ApiResponse<GameMove[]>>(`/rooms/${roomCode}/game/moves`),

  placeStone: (roomCode: string, row: number, col: number) =>
    apiClient.post<ApiResponse<GameMove>>(`/rooms/${roomCode}/game/moves`, { row, col }),

  surrender: (roomCode: string) =>
    apiClient.post<ApiResponse<GameInfo>>(`/rooms/${roomCode}/game/surrender`),

  getMyGames: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PageResponse<GameInfo>>>('/games/my', {
      params: { page, size },
    }),

  // 기보 보기 — 종료된 내 게임의 착수 목록
  getGameMovesById: (gameId: number) =>
    apiClient.get<ApiResponse<GameMove[]>>(`/games/${gameId}/moves`),

  // 피지컬 리플레이 — 기록 없으면 data=null (일반 오목 등)
  getPhysicalReplay: (gameId: number) =>
    apiClient.get<ApiResponse<PhysicalReplay | null>>(`/games/${gameId}/physical-replay`),

  getUserGames: (userId: string, page = 0, size = 10) =>
    apiClient.get<ApiResponse<PageResponse<GameInfo>>>(`/users/${userId}/games`, {
      params: { page, size },
    }),

  getUserGameMoves: (userId: string, gameId: number) =>
    apiClient.get<ApiResponse<GameMove[]>>(`/users/${userId}/games/${gameId}/moves`),

  getUserPhysicalReplay: (userId: string, gameId: number) =>
    apiClient.get<ApiResponse<PhysicalReplay | null>>(`/users/${userId}/games/${gameId}/physical-replay`),
};
