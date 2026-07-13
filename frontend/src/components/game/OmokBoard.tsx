import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Board, ItemConfig, SkinColors, SkinFilter, StoneColor, StoneStyle } from '@/types';
import { useProtectedAsset } from '@/hooks/useProtectedAsset';
import { useTouchUI } from '@/hooks/useTouchUI';
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

// ---- 줌/팬 상수 ----
const MIN_SCALE = 1;
const MAX_SCALE = 3;
const ZOOM_STEP = 1.4;              // +/− 버튼 한 번당 배율
const DRAG_THRESHOLD_PX = 8;        // 이 이상 움직이면 탭이 아니라 드래그(팬)로 판정
// 자연 크기: 판 + 원목 프레임(16*2) + 바깥 여백(8*2) — 데스크톱에선 기존과 동일한 크기 유지
const NATURAL_WIDTH = BOARD_PX + 48;

// 줌/팬 변환 상태: 화면 좌표 = tx + k * 판 좌표 (SVG <g transform> 과 동일)
interface ViewTransform { k: number; tx: number; ty: number }

/** 배율/이동을 허용 범위로 눌러 담기 — 판 밖의 빈 공간이 보이지 않게 한다 */
const clampView = (k: number, tx: number, ty: number): ViewTransform => {
  const ck = Math.min(Math.max(k, MIN_SCALE), MAX_SCALE);
  const min = BOARD_PX * (1 - ck); // 이동 하한 (음수). 상한은 0
  return {
    k: ck,
    tx: ck === 1 ? 0 : Math.min(Math.max(tx, min), 0),
    ty: ck === 1 ? 0 : Math.min(Math.max(ty, min), 0),
  };
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

  // ==== 모바일 UX: 탭 미리보기 + 줌/팬 ====
  const touchUI = useTouchUI();
  // 미리보기(반투명 돌) 좌표 — 같은 곳을 한 번 더 탭하면 실제 착수
  const [pending, setPending] = useState<{ row: number; col: number } | null>(null);
  const [view, setView] = useState<ViewTransform>({ k: 1, tx: 0, ty: 0 });

  const svgRef = useRef<SVGSVGElement>(null);
  // 화면에 닿아 있는 손가락들 (pointerId → 마지막 클라이언트 좌표)
  const pointersRef = useRef(new Map<number, { x: number; y: number }>());
  // 핀치 시작 스냅샷 (두 손가락 거리/중점과 그 시점의 변환)
  const pinchRef = useRef<{ dist: number; midX: number; midY: number; view: ViewTransform } | null>(null);
  // 한 손가락 팬 시작 스냅샷 (확대 중일 때만)
  const panRef = useRef<{ x: number; y: number; view: ViewTransform } | null>(null);
  // 드래그/핀치 후 따라오는 click 이벤트를 착수로 오인하지 않기 위한 플래그
  const movedRef = useRef(false);

  // 클라이언트 px → SVG viewBox 좌표 변환 배율
  const svgFactor = () => {
    const rect = svgRef.current?.getBoundingClientRect();
    return rect && rect.width > 0 ? BOARD_PX / rect.width : 1;
  };
  const toSvgPoint = (clientX: number, clientY: number) => {
    const rect = svgRef.current!.getBoundingClientRect();
    const f = svgFactor();
    return { x: (clientX - rect.left) * f, y: (clientY - rect.top) * f };
  };

  const handlePointerDown = (e: React.PointerEvent<SVGSVGElement>) => {
    pointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const pts = [...pointersRef.current.values()];
    if (pts.length === 2) {
      // 두 손가락 → 핀치 줌 시작
      const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
      pinchRef.current = {
        dist,
        midX: (pts[0].x + pts[1].x) / 2,
        midY: (pts[0].y + pts[1].y) / 2,
        view,
      };
      panRef.current = null;
      movedRef.current = true; // 핀치 중 발생한 click 무시
    } else if (pts.length === 1) {
      movedRef.current = false;
      // 확대 상태에서만 한 손가락 드래그로 판을 움직인다 (원배율에선 페이지 스크롤 우선)
      if (view.k > 1) panRef.current = { x: e.clientX, y: e.clientY, view };
    }
  };

  // move/up 은 window 에 걸어 손가락이 판 밖으로 나가도 추적한다
  useEffect(() => {
    const onMove = (e: PointerEvent) => {
      if (!pointersRef.current.has(e.pointerId)) return;
      pointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

      const pinch = pinchRef.current;
      if (pinch && pointersRef.current.size >= 2) {
        const pts = [...pointersRef.current.values()];
        const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
        if (dist <= 0 || pinch.dist <= 0) return;
        const k = pinch.view.k * (dist / pinch.dist);
        // 시작 중점 아래에 있던 판 위치가 손가락 중점을 따라오도록 이동값 계산
        const midNow = toSvgPoint(
          (pts[0].x + pts[1].x) / 2,
          (pts[0].y + pts[1].y) / 2,
        );
        const midStart = toSvgPoint(pinch.midX, pinch.midY);
        const cx = (midStart.x - pinch.view.tx) / pinch.view.k;
        const cy = (midStart.y - pinch.view.ty) / pinch.view.k;
        const ck = Math.min(Math.max(k, MIN_SCALE), MAX_SCALE);
        setView(clampView(ck, midNow.x - ck * cx, midNow.y - ck * cy));
        return;
      }

      const pan = panRef.current;
      if (pan && pointersRef.current.size === 1) {
        const dx = e.clientX - pan.x;
        const dy = e.clientY - pan.y;
        if (Math.abs(dx) + Math.abs(dy) > DRAG_THRESHOLD_PX) movedRef.current = true;
        const f = svgFactor();
        setView(clampView(pan.view.k, pan.view.tx + dx * f, pan.view.ty + dy * f));
      }
    };
    const onUp = (e: PointerEvent) => {
      if (!pointersRef.current.delete(e.pointerId)) return;
      if (pointersRef.current.size < 2) pinchRef.current = null;
      if (pointersRef.current.size === 0) panRef.current = null;
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
    };
    // view 를 pinch/pan 시작 시점 스냅샷으로 쓰므로 최신 구독이 필요 없다 — 마운트 시 1회면 충분
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // +/− 버튼: 현재 보이는 영역의 중심을 유지하며 배율 변경
  const zoomBy = (factor: number) => {
    setView((v) => {
      const k = Math.min(Math.max(v.k * factor, MIN_SCALE), MAX_SCALE);
      const center = BOARD_PX / 2;
      const cx = (center - v.tx) / v.k;
      const cy = (center - v.ty) / v.k;
      return clampView(k, center - k * cx, center - k * cy);
    });
  };
  const resetZoom = () => setView({ k: 1, tx: 0, ty: 0 });

  // 수가 놓이면(보드 갱신) 미리보기 초기화 — 상대 수가 와도, 내 착수가 반영돼도 정리된다
  useEffect(() => {
    setPending(null);
  }, [board]);

  const handleClick = useCallback(
    (row: number, col: number) => {
      // 드래그/핀치 끝에 발생한 click 은 착수가 아니다
      if (movedRef.current) {
        movedRef.current = false;
        return;
      }
      if (!canPlace || board[row][col] !== null) return;
      if (touchUI) {
        // 탭 1회 = 미리보기, 같은 곳 탭 1회 더 = 착수 (다른 곳 탭 = 미리보기 이동)
        if (pending && pending.row === row && pending.col === col) {
          setPending(null);
          onPlaceStone(row, col);
        } else {
          setPending({ row, col });
        }
        return;
      }
      onPlaceStone(row, col);
    },
    [canPlace, board, onPlaceStone, touchUI, pending],
  );

  const showPending = touchUI && canPlace && pending && myColor;
  const pendingStyle = myColor ? styleOf(myColor) : null;

  return (
    <div style={{ width: '100%', maxWidth: NATURAL_WIDTH, padding: '8px' }}>
      {/* 실제 바둑판 같은 원목 프레임 (베벨 + 그림자) */}
      <div
        style={{
          padding: 'clamp(8px, 2.2vw, 16px)',
          borderRadius: 16,
          background: 'linear-gradient(135deg, #6b4d2c 0%, #3f2c19 55%, #543b23 100%)',
          border: '1px solid #2a1d11',
          boxShadow:
            'inset 0 2px 4px rgba(255,222,176,0.18), inset 0 -3px 9px rgba(0,0,0,0.5), 0 22px 48px -14px rgba(0,0,0,0.75)',
        }}
      >
        <svg
          ref={svgRef}
          viewBox={`0 0 ${BOARD_PX} ${BOARD_PX}`}
          onPointerDown={handlePointerDown}
          style={{
            display: 'block',
            width: '100%',
            height: 'auto',
            aspectRatio: '1 / 1',
            cursor: canPlace ? 'crosshair' : 'default',
            borderRadius: 6,
            // 확대 중엔 판 위 제스처를 모두 우리가 처리(팬), 원배율에선 세로 스크롤을 브라우저에 양보
            touchAction: view.k > 1 ? 'none' : 'pan-y',
          }}
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
            @keyframes pending-pulse {
              0%, 100% { opacity: 0.85; }
              50% { opacity: 0.35; }
            }
            .pending-ring { animation: pending-pulse 1.1s ease-in-out infinite; }
          `}</style>
        </defs>

        {/* 줌/팬 변환 — 판 전체(배경/격자/돌/클릭영역)에 적용 */}
        <g transform={`translate(${view.tx} ${view.ty}) scale(${view.k})`}>

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

        {/* 터치 미리보기 — 반투명 돌 + 깜빡이는 링. 같은 곳을 한 번 더 탭하면 착수 */}
        {showPending && pendingStyle && (
          <g style={{ pointerEvents: 'none' }}>
            <circle
              cx={PADDING + pending.col * CELL_SIZE}
              cy={PADDING + pending.row * CELL_SIZE}
              r={CELL_SIZE / 2 - 2}
              fill={pendingStyle.fill}
              stroke={pendingStyle.stroke}
              strokeWidth={1}
              opacity={0.55}
            />
            <circle
              className="pending-ring"
              cx={PADDING + pending.col * CELL_SIZE}
              cy={PADDING + pending.row * CELL_SIZE}
              r={CELL_SIZE / 2 + 3}
              fill="none"
              stroke="#ffd86b"
              strokeWidth={2.5}
            />
          </g>
        )}
        </g>
        </svg>
      </div>

      {/* 터치 화면 전용: 미리보기 안내 + 줌 컨트롤 (판을 가리지 않도록 아래에 배치) */}
      {touchUI && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: showPending ? 'space-between' : 'flex-end',
            gap: 8,
            marginTop: 8,
            minHeight: 36,
          }}
        >
          {showPending && (
            <span style={{ color: '#ffd86b', fontSize: '0.82rem', fontWeight: 700 }}>
              한 번 더 탭하면 착수돼요
            </span>
          )}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {view.k > 1 && (
              <button type="button" onClick={resetZoom} style={{ ...zoomBtnStyle, width: 'auto', padding: '0 10px' }}>
                ↺ {Math.round(view.k * 100)}%
              </button>
            )}
            <button type="button" onClick={() => zoomBy(1 / ZOOM_STEP)} style={zoomBtnStyle} aria-label="축소">
              −
            </button>
            <button type="button" onClick={() => zoomBy(ZOOM_STEP)} style={zoomBtnStyle} aria-label="확대">
              ＋
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

// 줌 버튼 공통 스타일 (터치 타깃 36px 확보)
const zoomBtnStyle: React.CSSProperties = {
  width: 36,
  height: 36,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: 'rgba(40, 30, 18, 0.85)',
  color: '#e8ddc8',
  border: '1px solid rgba(160, 130, 80, 0.45)',
  borderRadius: 8,
  fontSize: '1.1rem',
  fontWeight: 700,
  cursor: 'pointer',
};

export default OmokBoard;
