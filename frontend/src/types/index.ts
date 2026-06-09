export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T | null;
}

// WebSocket 공지/방 종료 등 단순 알림 페이로드
export interface NoticePayload {
  message?: string;
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
export type GameType = 'CLASSIC' | 'CARD' | 'PHYSICAL';
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
  stoneSkin: StoneStyle | null; // 서버가 user_active_items 에서 읽어 내려준 장착 바둑알 스킨(미장착 null)
  stoneEffect: string | null;   // 서버가 해석한 착수 효과 키(예: 'bounce'). STONE_EFFECT 장착 또는 고급 스킨 번들. 없으면 null
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
  playerId: string; // UUID (publicId) — 다른 식별자와 일관
  playerNickname: string;
  color: StoneColor;
  row: number;
  col: number;
  moveNumber: number;
  soundAssetKey: string | null; // 이 수를 둔 사람의 장착 착수음(없으면 null)
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
export type ItemType = 'DEFEAT_MESSAGE' | 'BOARD_SKIN' | 'STONE_SOUND' | 'STONE_SKIN' | 'STONE_EFFECT' | 'CHARACTER_SKIN';

// 스킨 렌더링용 색상 (BOARD_SKIN 전용)
export interface SkinColors {
  bg: string;
  lines: string;
  dots: string;
}

// 바둑알 스킨 스타일 (STONE_SKIN 전용, 절차적 — 색상만). 백엔드 ItemConfig.stone 과 동일 스키마.
export interface StoneStyle {
  fill: string;   // 돌 채움색
  stroke: string; // 테두리색
  shine: string;  // 광택 하이라이트색
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
  stone?: StoneStyle | null;  // STONE_SKIN 전용
  effect?: string | null;     // STONE_EFFECT 전용(고급 STONE_SKIN이 번들할 수도 있음)
  character?: CharacterStyle | null; // CHARACTER_SKIN 전용(피지컬 캐릭터 외형)
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

// ===== 피지컬 오목 (실시간 액션 모드) =====
export type Direction = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';
export type PhysicalItemType = 'SPEED_BOOST' | 'CRATER' | 'BOMB' | 'REMOVE_STONE';
export type PhysicalInputType = 'MOVE_START' | 'MOVE_STOP' | 'PLACE' | 'DESTROY' | 'USE_ITEM';

// 피지컬 캐릭터 스킨(절차적) — body/accent는 hex, face는 이모지 매핑 키워드. 백엔드 ItemConfig.character 와 동일.
export interface CharacterStyle {
  body: string;
  accent: string;
  face: string;
}

// 한 플레이어의 가시 상태(색으로 식별 — 클라는 자기 색과 매칭해 '나'를 찾는다)
export interface PhysicalPlayerView {
  color: StoneColor;
  nickname: string;
  x: number;
  y: number;
  heldItem: PhysicalItemType | null;
  destroyReadyAt: number; // 파괴 쿨다운 해제 시각(epoch ms)
  speedBoosted: boolean;
  skin: StoneStyle | null;          // 서버가 해석한 바둑알 스킨 색(미장착 null)
  character: CharacterStyle | null; // 서버가 해석한 캐릭터 스킨(미장착 null)
  soundAssetKey: string | null;     // 장착 착수음 assetKey(미장착 null)
}

export interface PhysicalItemDrop {
  x: number;
  y: number;
  type: PhysicalItemType;
}

// 서버가 틱마다 /topic/room/{code}/physical 로 보내는 전체 스냅샷(서버 권위)
export interface PhysicalSnapshot {
  roomCode: string;
  status: GameStatus;
  boardSize: number;
  winCount: number;
  cells: number[][]; // [y][x]: 0=빈칸,1=흑,2=백,3=분화구
  players: PhysicalPlayerView[];
  items: PhysicalItemDrop[];
  winnerColor: StoneColor | null;
  pendingWinColor: StoneColor | null; // 5목 확정 대기 중인 색(이 동안 끊으면 무효), 없으면 null
  pendingWinLine: number[][] | null; // 확정 대기 중인 5목을 이루는 칸 좌표들([[x,y],...]) — 강조용, 없으면 null
  serverTime: number; // epoch ms — 쿨다운 계산 기준
}

// 랭킹
export interface RankingEntry {
  rank: number;
  userId: string; // UUID
  nickname: string;
  profileImageUrl: string | null;
  wins: number;
  losses: number;
  draws: number;
  totalGames: number;
  winRate: number;
}

