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
export type GameStatus = 'IN_PROGRESS' | 'FINISHED' | 'DRAW' | 'ABANDONED';
export type StoneColor = 'BLACK' | 'WHITE';
export type RoomStatus = 'WAITING' | 'IN_PROGRESS' | 'CLOSED';
export type GameType = 'CLASSIC' | 'CARD';
export type PlayerRole = 'HOST' | 'PLAYER' | 'SPECTATOR';
export type TimeLimit = 'UNLIMITED' | 'ONE_MIN' | 'THREE_MIN' | 'FIVE_MIN' | 'TEN_MIN';
export type ByoyomiOption = 'NONE' | 'TEN_SEC' | 'FIFTEEN_SEC' | 'THIRTY_SEC';

export interface User {
  id: string; // UUID (publicId)
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  provider: AuthProvider;
  wins: number;
  losses: number;
  draws: number;
  totalGames: number;
  currency: number;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface GamePlayer {
  userId: string; // UUID (publicId)
  nickname: string;
  profileImageUrl: string | null;
  role: PlayerRole;
  color: StoneColor | null;
  remainingSeconds: number | null;
  inByoyomi: boolean;
  ready: boolean;
}

export interface GameInfo {
  id: number;
  gameNumber: number;
  status: GameStatus;
  blackPlayer: User | null;
  whitePlayer: User | null;
  winner: User | null;
  currentTurn: StoneColor | null;
  startedAt: string | null;
  finishedAt: string | null;
  lastMoveAt: string | null;
  winnerDefeatMessage: string | null;
}

export interface Room {
  id: number;
  roomCode: string;
  host: User;
  status: RoomStatus;
  gameType: GameType;
  timeLimit: TimeLimit;
  byoyomiOption: ByoyomiOption;
  maxSpectators: number;
  currentGameNumber: number;
  players: GamePlayer[];
  currentGame: GameInfo | null;
  createdAt: string;
}

export interface GameMove {
  id: number;
  roomCode: string;
  playerId: number;
  playerNickname: string;
  color: StoneColor;
  row: number;
  col: number;
  moveNumber: number;
  createdAt: string;
}

export interface ChatMessage {
  senderNickname: string;
  senderColor: StoneColor | null;
  spectator: boolean;
  content: string;
  sentAt: string;
}

export type Board = (StoneColor | null)[][];

// Shop
export type ItemType = 'DEFEAT_MESSAGE' | 'BOARD_SKIN' | 'STONE_SOUND';

// 스킨 렌더링용 색상 (BOARD_SKIN 전용)
export interface SkinColors {
  bg: string;
  lines: string;
  dots: string;
}

// 스킨 SVG 필터 파라미터 (BOARD_SKIN 전용)
export interface SkinFilter {
  type: string;
  freqX: number;
  freqY: number;
  octaves: number;
  seed: number;
  blend: string;
}

// 코스메틱 아이템 메타데이터 (백엔드 items.item_config 와 동일 스키마, 스킨/착수음 공통)
export interface ItemConfig {
  displayName: string;
  assetKey?: string | null;
  colors?: SkinColors | null;
  filter?: SkinFilter | null;
}

export interface ShopItem {
  id: number;
  name: string;
  displayName: string;
  itemType: ItemType;
  description: string;
  itemConfig?: ItemConfig | null;
}

export interface CurrencyPackage {
  id: string;
  currency: number;
  priceKrw: number;
  label: string;
}

export interface GachaBox {
  type: string;
  name: string;
  price: number;
  itemType: ItemType;
  possibleItems: string[];
}

export interface ShopInfo {
  packages: CurrencyPackage[];
  boxes: GachaBox[];
}

export interface GachaResult {
  item: ShopItem;
  remainingCurrency: number;
  isDuplicate: boolean;
}

export interface Inventory {
  currency: number;
  items: ShopItem[];
  activeItems: Record<string, ShopItem>;
}
