# 도파민 오목 (Dopamin Omok)

실시간 웹 오목 게임 서비스. 두 가지 게임 모드를 제공한다.

- **클래식 오목** — 정통 턴제 15×15 오목.
- **피지컬 오목** — 크레이지 아케이드 스타일 **실시간 액션 오목**. 캐릭터를 방향키로 움직여
  Space로 착수하고, Shift로 상대 돌을 부수며, 필드 아이템을 주워 Ctrl로 사용해 먼저 오목을 만든다.
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


## 문서

설계·운영 메모는 `docs/`, 개발 회고는 `dev-log/`에 있다.

| 문서 | 내용 |
|------|------|
| [docs/설정-가이드.md](docs/설정-가이드.md) | 운영/밸런스 매뉴얼 — 착수 속도·승리 확정 시간·착수음 폴더·상점 등 설정법 |
| [docs/physical-omok.md](docs/physical-omok.md) | 피지컬 오목 설계(아키텍처·틱 루프·WebSocket) |
| [docs/asset-loading.md](docs/asset-loading.md) | 코스메틱 에셋(스킨/착수음) 로딩 구조 |
| [dev-log/0.README.md](dev-log/0.README.md) | 개발 회고록 작성 이유와 프로젝트 목표 |


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
| MySQL | localhost:3307 (`DB_PORT` 미설정 시) |

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

### 3. 테스트와 로컬 MySQL 벤치마크

일반 자동 테스트는 H2를 사용합니다. `backend/src/test/resources/application.yml`이 테스트 전용 설정이라서
서버용 `application-local.yml`, `application-docker.yml`, `application-prod.yml`에는 영향이 없습니다.

```powershell
cd backend
.\gradlew.bat test
```

친구 상대전적 batch 집계의 로컬 MySQL 벤치마크는 별도 opt-in task로 실행합니다.

```powershell
cd backend
.\gradlew.bat mysqlBenchmarkTest --rerun-tasks --console=plain
```

벤치마크 기본값은 `docker-compose.dev.yml` 기준입니다.

| 항목 | 값 |
|------|----|
| MySQL host port | `3307` (`DB_PORT` 미설정 시) |
| Database | `dopamin_omok_bench` |
| Username | `root` |
| Password | `root1234` |
| 설정 파일 | `backend/src/test/resources/application-mysql-benchmark.yml` |

벤치마크 설정은 test resources 안의 `mysql-benchmark` 프로파일에만 있습니다. `ddl-auto=create-drop`,
`flyway.enabled=false`로 실행되고, 테스트 코드가 JDBC URL에 `dopamin_omok_bench`가 없으면 바로 실패시켜
실제 개발/운영 DB에 잘못 붙지 않도록 막습니다.

포트나 비밀번호가 다르면 짧게 덮어씁니다.

```powershell
.\gradlew.bat mysqlBenchmarkTest --rerun-tasks --console=plain `
  "-Domok.bench.mysql.port=3306" `
  "-Domok.bench.mysql.password=YOUR_PASSWORD"
```

호스트나 DB URL 전체가 다르면 `-Domok.bench.mysql.url=...`로 덮어쓸 수 있지만, URL에는 반드시
`dopamin_omok_bench`가 들어가야 합니다.

결과는 콘솔에서 `[HeadToHeadBenchmark]` 라인을 확인합니다. 콘솔에 안 보이면 XML 리포트에서 찾습니다.

```powershell
Select-String -Path .\build\test-results\mysqlBenchmarkTest\*.xml -Pattern "HeadToHeadBenchmark"
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

## 주요 기능
- 인증: JWT + 이메일 인증(Redis TTL) + 구글 OAuth
- 대국: 방 기반 일반/랭크, 비회원 캐주얼, 싱글 AI
- 실시간: 피지컬 오목(STOMP/SockJS 틱 루프)
- 경제: 상점/뽑기/아이템
- 커뮤니티: 만남의 광장(실시간 아바타), 친구

---
