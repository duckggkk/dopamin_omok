# 운영 로그 보는 법 (Observability Runbook)

도파민 오목 운영 서버에서 문제가 생겼을 때 **로그로 원인을 추적하는 방법**을 정리한다.
초보자도 그대로 따라 할 수 있게 명령어 위주로 적었다.

---

## 0. 한눈에 보기 (로그가 흐르는 길)

```
앱 코드(log.info ...)
   │  ← logback-spring.xml 이 형식 결정: [traceId] 붙이고, 이메일/토큰 마스킹
   ▼
컨테이너 stdout
   │  ← docker json-file 드라이버가 파일로 저장(10MB×3개 로테이션)
   ▼
Promtail (수집 에이전트)
   │  ← 컨테이너마다 로그를 따라가며 service/container 라벨을 붙임
   ▼
Loki (로그 저장소, 14일 보관)
   │
   ▼
Grafana (https://<도메인>/grafana → Explore 에서 검색)
```

핵심 도구는 두 가지다.
- **빠르게 한 컨테이너만** 볼 땐 → `docker logs` (서버에 SSH 접속)
- **여러 컨테이너를 가로질러 검색·기간 조회**할 땐 → **Grafana + Loki**

---

## 1. 가장 빠른 방법 — `docker logs` (SSH)

서버에 접속한 뒤:

```bash
# 백엔드 실시간 로그 따라가기 (가장 자주 씀)
docker logs -f omok_backend

# 최근 200줄만
docker logs --tail 200 omok_backend

# 특정 시간 이후만
docker logs --since 30m omok_backend          # 최근 30분
docker logs --since 2026-06-20T09:00:00 omok_backend

# 키워드로 거르기 (ERROR 만)
docker logs omok_backend 2>&1 | grep ERROR

# 특정 추적 ID(traceId)로 한 요청 전체 모아보기
docker logs omok_backend 2>&1 | grep 'a1b2c3d4'
```

컨테이너 이름: `omok_backend`, `omok_frontend`, `omok_db`, `omok_caddy`,
`omok_prometheus`, `omok_grafana`, `omok_loki`, `omok_promtail`.

> 주의: `docker logs` 는 그 컨테이너가 **살아있는 동안의 최근 로그(10MB×3)** 만 본다.
> 재배포로 컨테이너가 교체되면 과거 로그는 사라진다. 그래서 Loki 가 필요하다(아래).

---

## 2. 제대로 된 방법 — Grafana + Loki

브라우저에서 **`https://<도메인>/grafana`** 접속 → 로그인(`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`).

왼쪽 메뉴 **Explore** → 데이터소스 **Loki** 선택 → 아래 LogQL 로 검색한다.

### 자주 쓰는 검색 (LogQL)

```logql
# 백엔드 전체 로그
{service="backend"}

# 백엔드에서 ERROR 만
{service="backend"} |= "ERROR"

# 특정 traceId 로 한 요청 전체 추적 (요청별 디버깅의 핵심)
{service="backend"} |= "a1b2c3d4"

# 이메일 발송 관련만
{service="backend"} |= "이메일"

# 여러 서비스 한 번에
{service=~"backend|frontend"} |= "ERROR"

# 5xx 응답만 (접근 로그 패턴 활용: "-> 500")
{service="backend"} |= "-> 5"
```

- 오른쪽 위 **시간 범위**로 기간을 좁힌다(예: Last 1 hour, 또는 사건 발생 시각 전후).
- `|=` 포함 / `!=` 제외 / `|~` 정규식 / `!~` 정규식 제외.

### Live 모드
Explore 오른쪽 위 **Live** 버튼 → 실시간 스트리밍(= `docker logs -f` 의 웹 버전, 여러 서비스 동시).

---

## 3. traceId 로 요청 추적하기 (제일 중요)

요청마다 8자리 추적 ID 가 붙는다. 사용법:

1. 사용자가 오류를 겪으면, 응답 헤더 **`X-Request-Id`** 값(예: `a1b2c3d4`)을 알아낸다.
   (브라우저 개발자도구 Network 탭 → 해당 요청 → Response Headers)
2. 그 값으로 검색하면 그 한 번의 요청이 거친 **컨트롤러·서비스·비동기 메일 발송 로그까지 전부** 한 줄기로 모인다.
   ```logql
   {service="backend"} |= "a1b2c3d4"
   ```

로그 한 줄 예시(운영 형식):
```
2026-06-20 09:12:33.444 INFO  [a1b2c3d4] [http-nio-8080-exec-3] c.d.o.g.l.TraceIdFilter - POST /api/auth/login -> 200 (45ms)
```
`[a1b2c3d4]` 가 traceId, `-> 200 (45ms)` 가 응답 상태와 소요시간이다.

---

## 4. 개인정보 마스킹 — 무엇이 가려지나

로그로 새어 나갈 수 있는 민감정보는 **출력 직전에 자동으로 가려진다**
(`PiiMasker` + `MaskingMessageConverter`, 모든 로그에 적용).

| 원본 | 로그에 찍히는 모습 |
|------|----------------------|
| `hong@gmail.com` | `h***@gmail.com` (도메인만 남김) |
| JWT `eyJhbGci...` | `***JWT***` |
| `Bearer eyJ...` | `Bearer ***` |

- 개발자가 호출부마다 마스킹을 신경 쓸 필요 없다(전역 안전망).
- 새 민감정보 유형(예: 전화번호)이 생기면 `PiiMasker` 에 정규식 한 줄만 추가하면 된다.
- 한계: 예외 **스택트레이스 본문**과 구조화 로그로 바꿀 경우엔 별도 처리가 필요하다(현재 코드 경로엔 PII 미포함).

---

## 5. 로그 레벨 조정

평소 운영(prod)은 **INFO**, 로컬은 **DEBUG** 다. (`application-prod.yml` / `application.yml` 의 `logging.level`)

문제 추적을 위해 잠깐 더 자세히 보고 싶을 때:
- `application-prod.yml` 의 `logging.level.com.dopamin.omok: INFO` 를 `DEBUG` 로 바꾸고 백엔드만 재시작.
- ⚠️ Hibernate SQL/바인드 파라미터 DEBUG 는 민감정보를 노출할 수 있어 **켜지 말 것**
  (불가피하면 짧게 켰다가 즉시 되돌리기). 자세한 주의는 `application.yml` 주석 참고.

---

## 6. 디스크/보관 정책

- 컨테이너별 로컬 로그: **10MB × 3개**(json-file 로테이션) — `docker-compose.prod.yml` 의 `x-logging`.
- Loki 보관: **14일** 후 자동 삭제 — `deploy/loki/loki-config.yml` 의 `retention_period`.
- Prometheus(지표)는 15일 — 참고용.

용량이 부담되면 위 두 값을 줄이면 된다.

---

## 7. 자주 겪는 문제

- **Grafana 에 Loki 가 안 보임** → `omok_loki` / `omok_promtail` 컨테이너가 떠 있는지 확인
  (`docker ps`), Promtail 로그 확인(`docker logs omok_promtail`).
- **로그가 Loki 에 안 쌓임** → Promtail 이 `docker.sock` 을 못 읽는 경우. compose 의
  `/var/run/docker.sock:/var/run/docker.sock:ro` 마운트 확인.
- **traceId 가 빈칸 `[]`** → 요청 컨텍스트 밖(서버 시작 로그, 틱 루프 등)이라 정상.
  HTTP 요청 처리 로그에는 항상 채워진다.
