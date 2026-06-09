# 도파민 오목 (Dopamin Omok)

실시간 웹 오목 게임 서비스. 두 가지 게임 모드를 제공한다.

- **클래식 오목** — 정통 턴제 15×15 오목.
- **피지컬 오목** — 크레이지 아케이드 스타일 **실시간 액션 오목**. 캐릭터를 방향키로 움직여
  Space로 착수하고, Shift로 상대 돌을 부수며, 필드 아이템을 주워 Ctrl로 사용해 먼저 5목을 만든다.
  (설계: [docs/physical-omok.md](docs/physical-omok.md))

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.4.1, Spring Security + JWT, Spring Data JPA, WebSocket(STOMP) |
| Frontend | React 18 + TypeScript, Vite, Zustand, Axios, STOMP.js, CSS Modules |
| Database | MySQL 8.0 |
| Infra | Docker, Docker Compose, Nginx |

---

## 프로젝트 구조

```
dopamin_omok/
├── .devcontainer/
│   ├── backend/devcontainer.json    # VSCode Dev Container - 백엔드
│   └── frontend/devcontainer.json  # VSCode Dev Container - 프론트엔드
├── backend/
│   ├── Dockerfile                   # 프로덕션 빌드 (multi-stage)
│   ├── Dockerfile.dev               # 개발용 (볼륨 마운트 + hot reload)
│   ├── build.gradle.kts
│   └── src/main/resources/
│       ├── application.yml          # 공통 설정
│       ├── application-local.yml    # 로컬 직접 실행
│       ├── application-docker.yml   # Docker 컨테이너 실행
│       └── application-prod.yml     # 운영 배포
├── frontend/
│   ├── Dockerfile                   # 프로덕션 빌드 (nginx)
│   ├── Dockerfile.dev               # 개발용 (Vite dev server)
│   └── nginx.conf                   # SPA + API/WebSocket 프록시
├── mysql/init/
│   └── 01_create_user.sql           # DB 초기 계정 생성
├── docker-compose.yml               # 프로덕션 (3 컨테이너)
├── docker-compose.dev.yml           # 개발 (볼륨 마운트 + 포트 노출)
├── .env.example                     # 환경변수 템플릿
└── .gitignore
```

---

## 실행 방법

### 1. 환경변수 설정

```bash
cp .env.example .env
# .env 파일을 열어 필요한 값 수정 (JWT_SECRET 등)
```

---

### 2-A. 개발 환경 (Docker Compose + 볼륨 마운트)

소스 변경이 컨테이너에 즉시 반영됩니다.

```bash
docker-compose -f docker-compose.dev.yml up --build
```

| 서비스 | 접속 주소 |
|--------|----------|
| Frontend (Vite) | http://localhost:3000 |
| Backend (Spring Boot) | http://localhost:8080 |
| MySQL | localhost:3306 |

---

### 2-B. VSCode Remote Explorer로 컨테이너 내부에서 개발

```bash
# 1. 컨테이너 먼저 실행
docker-compose -f docker-compose.dev.yml up -d

# 2. VSCode에서
#    Remote Explorer → Dev Containers
#    → "Omok Backend" 또는 "Omok Frontend" 선택 후 열기
```

또는 프로젝트 루트에서 `Ctrl+Shift+P` → **Dev Containers: Reopen in Container** 선택.

컨테이너 내부에서 백엔드 재시작:
```bash
./gradlew bootRun --no-daemon
```

---

### 2-C. 프로덕션 실행

```bash
docker-compose up --build -d
```

| 서비스 | 접속 주소 |
|--------|----------|
| 전체 앱 (nginx) | http://localhost:80 |
| Backend (내부) | omok_net:8080 |
| MySQL (내부) | omok_net:3306 |

---

### 2-D. 로컬 직접 실행 (Docker 없이)

```bash
# MySQL은 별도 설치 또는 DB만 Docker로
docker-compose -f docker-compose.dev.yml up -d db

# 백엔드
cd backend
./gradlew bootRun          # local 프로파일 (기본)

# 프론트엔드
cd frontend
npm install && npm run dev
```

---

## 컨테이너 구성

```
┌─────────────────────────────────────────────┐
│              omok_net (bridge)              │
│                                             │
│  ┌──────────┐   /api   ┌──────────────────┐ │
│  │ frontend │ ───────► │    backend       │ │
│  │  :80/3000│   /ws    │    :8080         │ │
│  └──────────┘          └────────┬─────────┘ │
│                                 │ JDBC      │
│                        ┌────────▼─────────┐ │
│                        │   db (MySQL)     │ │
│                        │   :3306          │ │
│                        └──────────────────┘ │
└─────────────────────────────────────────────┘
```

---

## API 명세

### Auth
| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | /api/auth/register | 회원가입 | X |
| POST | /api/auth/login | 로그인 | X |
| POST | /api/auth/refresh | 토큰 갱신 | X |
| POST | /api/auth/logout | 로그아웃 | O |

### User
| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| GET | /api/users/me | 내 프로필 | O |
| PATCH | /api/users/me | 프로필 수정 | O |
| GET | /api/users/{id} | 유저 조회 | X |

### Game
| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | /api/games/rooms | 방 생성 | O |
| POST | /api/games/rooms/{code}/join | 방 참가 | O |
| GET | /api/games/rooms | 대기 방 목록 | X |
| GET | /api/games/{id} | 게임 조회 | X |
| GET | /api/games/{id}/moves | 기보 조회 | X |
| POST | /api/games/{id}/moves | 돌 놓기 | O |
| POST | /api/games/{id}/surrender | 기권 | O |
| GET | /api/games/my | 내 게임 내역 | O |

### WebSocket (STOMP over SockJS)
| 구분 | 주소 | 설명 |
|------|------|------|
| Endpoint | `/ws` | SockJS 연결 |
| Subscribe | `/topic/game/{id}` | 돌 놓기 이벤트 수신 |
| Subscribe | `/topic/game/{id}/status` | 게임 상태 변경 수신 |
| Publish | `/app/game/{id}/move` | 돌 놓기 |
| Publish | `/app/game/{id}/surrender` | 기권 |

피지컬 오목(실시간 모드) 전용:
| 구분 | 주소 | 설명 |
|------|------|------|
| Subscribe | `/topic/room/{roomCode}/physical` | 전체 스냅샷(틱마다/입력 직후) |
| Publish | `/app/physical/{roomCode}/input` | 입력(이동/착수/파괴/아이템) |
| Publish | `/app/physical/{roomCode}/surrender` | 기권 |

---

## 문서

설계·운영 메모는 `docs/`(내부 문서)에 있다.

| 문서 | 내용 |
|------|------|
| [docs/설정-가이드.md](docs/설정-가이드.md) | 운영/밸런스 매뉴얼 — 착수 속도·승리 확정 시간·착수음 폴더·상점 등 설정법 |
| [docs/physical-omok.md](docs/physical-omok.md) | 피지컬 오목 설계(아키텍처·틱 루프·WebSocket) |
| [docs/asset-loading.md](docs/asset-loading.md) | 코스메틱 에셋(스킨/착수음) 로딩 구조 |
