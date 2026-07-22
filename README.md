# 도파민 오목 (Dopamin Omok)

[![Live Demo](https://img.shields.io/badge/Live%20Demo-dopaminomok.store-2ea44f?style=for-the-badge)](https://dopaminomok.store)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](.github/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

일반 턴제 오목과 서버 주도 실시간 액션 오목을 함께 제공하는 웹 게임 서비스입니다.
게스트도 캐주얼 온라인 대국과 광장을 이용할 수 있고, 회원은 친구·프로필·인벤토리와
코스메틱 기능을 추가로 사용할 수 있습니다.

**👉 [https://dopaminomok.store](https://dopaminomok.store) — 회원가입 없이 게스트 로그인으로 바로 플레이할 수 있습니다.**

> 현재 사용자 화면에서는 캐주얼 대국만 제공합니다. 랭크전은 준비 중입니다.
> 상점의 재화 충전은 실제 결제가 연결되지 않은 데모 기능입니다.

## 화면

<!-- 스크린샷을 docs/screenshots/ 에 넣고 아래 주석을 해제하세요.
     권장: 각 1200px 내외 PNG, 피지컬 오목은 움직임이 보이도록 GIF.

| 클래식 오목 | 피지컬 오목 |
|---|---|
| ![클래식 오목](docs/screenshots/classic.png) | ![피지컬 오목](docs/screenshots/physical.gif) |

| 만남의 광장 | 상점 |
|---|---|
| ![만남의 광장](docs/screenshots/plaza.png) | ![상점](docs/screenshots/shop.png) |
-->

## 주요 기능

### 클래식 오목

- 15×15 턴제 대국과 자유룰·렌주룰
- 방 생성, 코드 참가, 준비, 관전, 채팅, 기권, 재대국
- 플레이어별 제한 시간과 시간 소진 후 매 수 초읽기
- 종료 대국의 전체 수순 다시보기
- 브라우저에서 실행되는 9단계 AI 연습

### 피지컬 오목

캐릭터를 직접 움직여 돌을 놓고 상대 돌을 파괴하는 서버 주도 실시간 액션 모드입니다.

| 입력 | 동작 |
|---|---|
| 방향키 | 캐릭터 이동 |
| `Space` | 현재 위치에 착수 |
| `X` | 현재 위치의 상대 돌 파괴 |
| `C` | 보유 아이템 사용 |

- 기본 14×14 보드
- 완성한 오목을 약 2초 동안 유지하면 1점 획득
- 득점한 줄만 제거한 뒤 같은 보드에서 계속 진행
- 기본 3점 선승
- 이동 부스트, 바둑판 붕괴, 광역 폭탄 아이템
- 서버 틱과 STOMP 스냅샷을 이용한 상태 동기화
- AI 연습과 종료 대국 리플레이

### 계정과 커뮤니티

- 이메일 인증 회원가입, 일반 로그인, 게스트 로그인, Google OAuth
- JWT 액세스 토큰과 Redis 기반 Refresh 토큰
- 실시간 아바타 이동·채팅을 제공하는 만남의 광장
- 친구 요청·수락·삭제와 친구 간 상대 전적
- 프로필, 전적, 공개 범위 설정
- 코스메틱 뽑기, 인벤토리, 장착

게스트는 AI 연습, 캐주얼 온라인 일반·피지컬 대국, 관전, 광장을 이용할 수 있습니다.
친구, 상점, 프로필, 랭킹 등 계정 데이터가 필요한 기능은 회원 전용입니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.4.1, Spring MVC, Spring Security, JWT, Spring Data JPA, QueryDSL |
| Realtime | Spring WebSocket, STOMP, 서버 틱 기반 상태 동기화 |
| Data | MySQL 8, Redis 7, Flyway |
| Frontend | React 18, TypeScript 5, Vite 6, Zustand, Axios, STOMP.js, CSS Modules |
| Test | JUnit 5, Spring Boot Test, H2, MySQL 선택형 벤치마크 |
| Infra | Docker Compose, Nginx, Caddy, GHCR, GitHub Actions |
| Observability | Actuator, Micrometer, Prometheus, Grafana, Loki, Promtail |

## 구조

백엔드는 기능별 패키지 안을 `domain`, `application`, `adapter`로 나눈 포트·어댑터 구조를 사용합니다.

```text
dopamin_omok/
├── backend/
│   └── src/main/java/com/dopamin/omok/
│       ├── auth/       # 인증, 이메일 인증, OAuth
│       ├── game/       # 방, 클래식·피지컬 오목, 리플레이
│       ├── plaza/      # 실시간 광장
│       ├── friend/     # 친구와 상대 전적
│       ├── shop/       # 상점, 인벤토리, 에셋
│       ├── user/       # 프로필, 랭킹, AI 진척
│       └── global/     # 공통 예외, 보안, 로깅, WebSocket
├── frontend/src/
│   ├── api/
│   ├── components/
│   ├── hooks/
│   ├── pages/
│   ├── store/
│   └── utils/
├── deploy/             # Caddy, 관측 스택, 배포·백업 스크립트
├── docker-compose.dev.yml
├── docker-compose.prod.yml
└── .github/workflows/  # CI/CD
```

```text
Browser
  ├─ REST /api ───────────────┐
  └─ STOMP WebSocket /ws ─────┤
                              ▼
                    Spring Boot Backend
                         ├─ MySQL
                         └─ Redis

Prometheus ──> Actuator metrics ──> Grafana
Container logs ──> Promtail ──> Loki ──> Grafana
```

## 로컬 실행

### 요구 사항

- Docker Desktop
- Docker Compose

### 핵심 서비스 실행

```bash
docker compose -f docker-compose.dev.yml up --build db redis backend frontend
```

| 서비스 | 주소 |
|---|---|
| Frontend | http://localhost:3000 |
| Backend | http://localhost:8080 |
| Swagger UI | http://localhost:8080/api/swagger-ui/index.html |
| MySQL | `localhost:3307` |
| Redis | `localhost:6379` |

관측 서비스까지 모두 실행하려면 서비스 이름을 생략합니다.

```bash
docker compose -f docker-compose.dev.yml up --build
```

| 관측 서비스 | 주소 |
|---|---|
| Grafana | http://localhost:3001 |
| Prometheus | http://localhost:9090 |

로컬 기본값은 개발 전용입니다. 공개 배포에서는 `.env`에 DB·JWT·OAuth·메일·관측 도구의
운영용 비밀값을 설정해야 합니다.

## 테스트와 빌드

```bash
# Backend
cd backend
./gradlew test

# Frontend: TypeScript 검사와 프로덕션 번들 생성
cd frontend
npm ci
npm run build
```

Windows PowerShell에서는 백엔드 테스트를 `./gradlew test` 대신 `.\gradlew.bat test`로 실행할 수 있습니다.
일반 백엔드 테스트는 H2를 사용하며, 실제 MySQL 쿼리 성능 비교는 `mysqlBenchmarkTest` 작업으로
별도 실행할 수 있습니다.

## API

- REST API 기본 경로: `/api`
- STOMP WebSocket 연결 경로: `/ws`
- 로컬 Swagger UI: `/api/swagger-ui/index.html`
- OpenAPI JSON: `/api/v3/api-docs`
- 운영 환경에서는 Swagger UI와 OpenAPI 문서 노출을 비활성화합니다.

## 배포와 관측

- GitHub Actions에서 백엔드 테스트와 프론트엔드 타입 검사·빌드를 수행합니다.
- `main` 브랜치 CI가 성공하면 컨테이너 이미지를 GHCR에 게시하고 운영 서버에 배포합니다.
- Caddy가 HTTPS 인증서와 외부 진입점을 담당합니다.
- Prometheus가 Actuator 메트릭을 수집하고 Grafana에서 시각화합니다.
- Promtail이 컨테이너 로그를 Loki로 전달합니다.
- 운영 구성에는 헬스 체크, 점검 페이지, 로그 용량 제한, DB 백업 스크립트가 포함됩니다.

## 현재 범위

- 랭킹 화면과 관련 데이터 기반은 있지만, 랭크 대국 생성 UI는 아직 비활성입니다.
- 상점 재화와 아이템은 포트폴리오용 데모 데이터이며 실제 결제·환전 기능이 없습니다.
- Google OAuth는 운영 환경변수 설정 시 사용할 수 있습니다.
- 서비스는 스피드 베타 단계이므로 데이터 초기화나 기능 변경이 발생할 수 있습니다.

## 개발 기록

구현 과정에서 마주친 문제와 판단 근거를 [dev-log/](dev-log/) 에 정리해 두었습니다.
JWT 검증 구조 변경, Redis 도입에 따른 책임 분리, 쿼리 최적화, `latest` 태그로 인한
배포 장애 분석 등을 다룹니다.

## 라이선스

소스 코드는 [MIT License](LICENSE) 를 따릅니다.

배경음악·이미지·효과음 등 게임 에셋은 MIT 적용 대상이 아닙니다. 배경음악은
Pixabay Content License 로 제공되는 제3자 저작물이며, 출처와 라이선스는
[docs/music-from.md](docs/music-from.md) 에 정리되어 있습니다.
