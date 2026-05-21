import apiClient from './client';
import { ApiResponse, GameRoom, GameMove, PageResponse } from '@/types';

export const gameApi = {
  createRoom: () =>
    apiClient.post<ApiResponse<GameRoom>>('/games/rooms'),

  joinRoom: (roomCode: string) =>
    apiClient.post<ApiResponse<GameRoom>>(`/games/rooms/${roomCode}/join`),

  getWaitingRooms: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PageResponse<GameRoom>>>('/games/rooms', {
      params: { page, size },
    }),

  getGame: (gameId: number) =>
    apiClient.get<ApiResponse<GameRoom>>(`/games/${gameId}`),

  getGameMoves: (gameId: number) =>
    apiClient.get<ApiResponse<GameMove[]>>(`/games/${gameId}/moves`),

  placeStone: (gameId: number, row: number, col: number) =>
    apiClient.post<ApiResponse<GameMove>>(`/games/${gameId}/moves`, { row, col }),

  surrender: (gameId: number) =>
    apiClient.post<ApiResponse<GameRoom>>(`/games/${gameId}/surrender`),

  getMyGames: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PageResponse<GameRoom>>>('/games/my', {
      params: { page, size },
    }),
};
