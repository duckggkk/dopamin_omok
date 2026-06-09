import apiClient from './client';
import { ApiResponse, Room, GameInfo, GameMove, PageResponse, ByoyomiOption, GameType, TimeLimit } from '@/types';

export interface CreateRoomOptions {
  gameType: GameType;
  timeLimit: TimeLimit;
  byoyomiOption: ByoyomiOption;
}

const DEFAULT_ROOM_OPTIONS: CreateRoomOptions = {
  gameType: 'CLASSIC',
  timeLimit: 'UNLIMITED',
  byoyomiOption: 'NONE',
};

export const gameApi = {
  createRoom: (options: CreateRoomOptions = DEFAULT_ROOM_OPTIONS) =>
    apiClient.post<ApiResponse<Room>>('/rooms', options),

  joinRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<Room>>(`/rooms/${roomCode}/join`),

  spectateRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<Room>>(`/rooms/${roomCode}/spectate`),

  leaveRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<void>>(`/rooms/${roomCode}/leave`),

  requestRematch: (roomCode: string) =>
    apiClient.post<ApiResponse<Room>>(`/rooms/${roomCode}/rematch`),

  getWaitingRooms: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PageResponse<Room>>>('/rooms', {
      params: { page, size },
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
};
