# 배포 가이드 (도파민 오목)

실서버 배포 + CI/CD + 점검 공지 방식 업데이트의 전체 절차입니다.
코드/설정은 이미 레포에 포함돼 있고, 아래는 **사람이 한 번 해야 하는 준비**와 **운영 절차**입니다.

---

## 0. 구성 개요

```
                 인터넷
                   │  80/443 (HTTPS 자동발급)
              ┌────▼────┐
              │  Caddy  │  ← 점검 모드 토글 + TLS 종단
              └────┬────┘
                   │ (내부망)
              ┌────▼────────┐
              │ frontend    │  Nginx: SPA + /api·/ws 프록시
              │ (nginx)     │
              └────┬────────┘
                   │
              ┌────▼────┐        ┌──────────┐
              │ backend │───────▶│  MySQL   │  (포트 외부 미노출)
              │ (Spring)│        │  + 볼륨  │
              └─────────┘        └──────────┘
```

- 외부에 열리는 포트는 **Caddy의 80/443뿐**. DB/백엔드는 내부망에서만 통신.
- 이미지는 서버에서 빌드하지 않고 **GHCR에서 pull** → 나중에 AWS/k8s로 옮겨도 같은 이미지 재사용.
- 게임 상태: 일반 오목은 **DB 기반**(재접속 복구됨), 피지컬 오목은 **메모리 기반**(재시작 시 진행 매치 종료 → 점검 공지로 안내).

### 무료 호스팅 추천
- **백엔드 + DB**: Oracle Cloud **Always Free** ARM(Ampere A1, 최대 4 vCPU/24GB) VM 1대에 본 compose 그대로.
- (선택) 프론트만 **Cloudflare Pages/Vercel** 무료로 분리하면 더 가볍지만, 본 가이드는 **VM 한 대에 전부** 올리는 단순 구성을 기준으로 합니다.

---

## 1. 사전 준비 (1회)

### 1-1. 도메인 & DNS
- 도메인의 **A 레코드**를 VM 공인 IP로 지정 (예: `omok.example.com → 1.2.3.4`).
- Caddy가 이 도메인으로 Let's Encrypt 인증서를 자동 발급하므로 **DNS 전파가 끝난 뒤** 첫 기동해야 합니다.

### 1-2. 서버(VM) 기본 세팅
```bash
# Docker + compose 플러그인 설치 (Ubuntu 기준)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER      # 재로그인 후 sudo 없이 docker 사용

# 방화벽: 80, 443, (SSH)22 만 개방
sudo ufw allow 22 && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```
> ⚠️ **Oracle Cloud는 두 군데를 모두 열어야** 합니다: ① 인스턴스 **보안 목록/NSG**에서 80·443 인그레스 허용, ② OS 방화벽(ufw/iptables). 한쪽만 열면 접속이 안 됩니다.

### 1-3. 레포 클론 & 환경파일
```bash
sudo mkdir -p /opt/dopamin-omok && sudo chown $USER /opt/dopamin-omok
git clone <레포 URL> /opt/dopamin-omok
cd /opt/dopamin-omok

cp .env.prod.example .env.prod
chmod 600 .env.prod
nano .env.prod        # DOMAIN, 비밀번호, JWT_SECRET, 메일, IMAGE_* 채우기
```
- `JWT_SECRET` 생성: `openssl rand -base64 48`
- `IMAGE_BACKEND/IMAGE_FRONTEND`의 `owner/repo`를 **본인 GitHub 저장소(소문자)** 로 수정.

### 1-4. GHCR 이미지 접근
CD가 이미지를 GHCR에 push합니다. 서버가 pull하려면 둘 중 하나:
- **(간단) 패키지 public 전환**: GitHub > 프로필 > Packages > `backend`/`frontend` > Package settings > Change visibility → Public. 서버 로그인 불필요.
- **(비공개 유지) 서버에서 read 토큰 로그인**:
  ```bash
  echo <READ_PACKAGES_PAT> | docker login ghcr.io -u <github-id> --password-stdin
  ```

### 1-5. GitHub Secrets (Settings > Secrets and variables > Actions)
| Secret | 값 |
|---|---|
| `PROD_HOST` | VM 공인 IP 또는 도메인 |
| `PROD_USER` | SSH 사용자 (예: `ubuntu`) |
| `PROD_SSH_KEY` | 배포용 **개인키** 전체 내용 |
| `PROD_SSH_PORT` | (선택) 기본 22 |

배포용 SSH 키 만들기:
```bash
ssh-keygen -t ed25519 -f deploy_key -N ""
# deploy_key.pub → 서버 ~/.ssh/authorized_keys 에 추가
# deploy_key(개인키) → PROD_SSH_KEY 시크릿에 붙여넣기
```

### 1-6. OAuth / 메일 운영값
- 카카오·구글 콘솔에 **운영 도메인 redirect URI** 등록.
- Gmail은 **앱 비밀번호**(2단계 인증 후 발급)를 `MAIL_PASSWORD`에 사용.

---

## 2. 첫 배포

이미지가 아직 GHCR에 없다면 먼저 한 번 만들어야 합니다.
- **방법 A (권장)**: GitHub Actions 탭 > **Deploy (prod)** > *Run workflow* (수동 실행) → 이미지 빌드·push 후 서버 배포까지 진행.
- **방법 B (서버에서 직접)**:
  ```bash
  cd /opt/dopamin-omok
  docker compose -f docker-compose.prod.yml --env-file .env.prod pull
  docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
  docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend
  ```

확인:
```bash
curl -fsS https://<도메인>/api/actuator/health     # {"status":"UP"} 기대
```

---

## 3. 평상시 업데이트 (점검 공지 방식)

> 무중단이 아니라 **짧은 점검 창**을 띄우고 반영하는 방식입니다.

`main` 브랜치에 머지/푸시하면 자동으로:
```
push main → CI(테스트) 통과 → 이미지 빌드·GHCR push → 서버 SSH → deploy.sh
```
`deploy.sh`가 하는 일:
1. **점검 모드 ON** → 사용자에게 점검 페이지(503) 노출
2. 최신 이미지 pull
3. **DB 백업**
4. 컨테이너 재기동 (부팅 시 **Flyway 마이그레이션 자동 실행**)
5. 백엔드 **헬스체크(UP)** 확인
6. **점검 모드 OFF** → 정상화

> 헬스체크가 실패하면 **점검 모드를 유지한 채 멈춥니다**(깨진 버전을 노출하지 않음). 로그 확인 후 조치하세요.

**수동 배포**(서버에서):
```bash
cd /opt/dopamin-omok && bash deploy/scripts/deploy.sh
```

**점검 모드 수동 토글**:
```bash
touch deploy/maintenance/maintenance.on   # 점검 ON (즉시 503)
rm -f deploy/maintenance/maintenance.on   # 점검 OFF
```
- 사용자 프론트는 WebSocket 자동 재연결(3초)이 있어, 점검 종료 후 자동 복구됩니다. 일반 오목은 DB에서 판이 복원됩니다.

---

## 4. 롤백

```bash
cd /opt/dopamin-omok
# 1) 직전 정상 커밋의 SHA로 이미지 태그를 바꿔치기
nano .env.prod   # IMAGE_BACKEND/IMAGE_FRONTEND 의 :latest → :<직전-good-sha>
bash deploy/scripts/deploy.sh
```
- DB 스키마가 바뀐 배포였다면, 코드 롤백만으로는 부족할 수 있습니다. `backups/` 의 직전 덤프로 복구를 함께 고려하세요(아래 5-2).

---

## 5. 백업 & 복구

### 5-1. 자동 백업 (cron)
```bash
crontab -e
# 매일 새벽 4시 백업 (14일 초과분 자동 삭제)
0 4 * * *  cd /opt/dopamin-omok && bash deploy/scripts/backup.sh >> backups/backup.log 2>&1
```
> 백업이 VM 한 대에만 있으면 VM 유실 시 함께 사라집니다. 주기적으로 외부(예: Cloudflare R2, 오브젝트 스토리지)로 복사하길 권장합니다.

### 5-2. 복구
```bash
gunzip -c backups/dopamin_omok_YYYYmmdd_HHMMSS.sql.gz | \
  docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T \
    -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" db mysql -uroot
```

---

## 6. 보안 체크리스트 (배포 전 최종 확인)

- [ ] `.env.prod` 권한 `600`, git에 커밋 안 됨(확인: `git status`).
- [ ] `JWT_SECRET`을 운영용 랜덤 값으로 새로 발급(예제/CI 값 재사용 금지).
- [ ] DB·메일·OAuth 비밀번호 모두 운영 전용 값.
- [ ] HTTPS 정상(자물쇠) — Caddy 인증서 발급 확인.
- [ ] DB/백엔드 포트가 외부에 노출되지 않음(Caddy 80/443만 개방).
- [ ] 운영에서 `app.shop.direct-charge-enabled=false` (이미 prod 기본값) — 무한 충전 차단.
- [ ] 운영에서 Hibernate SQL/바인드 파라미터 DEBUG 로깅 비활성(민감정보 노출 방지) — prod 기본값 INFO/WARN.
- [ ] Swagger UI 외부 노출 여부 점검(필요 없으면 운영에서 닫기 권장).
- [ ] OS·도커 정기 업데이트, SSH 키 기반 로그인(비밀번호 로그인 비활성).

---

## 7. 자주 막히는 지점

| 증상 | 원인/해결 |
|---|---|
| HTTPS가 안 잡힘 | DNS A레코드 미전파 / 80·443 미개방(보안목록+ufw 둘 다). 전파 후 `docker compose restart caddy` |
| 백엔드 부팅 실패 | `logs backend` 확인. 대개 `.env.prod` 누락 값 또는 Flyway `validate` 불일치 |
| 메일 인증이 안 옴 | `MAIL_*` 값/Gmail 앱 비밀번호 확인. 스팸함도 확인 |
| 이미지 pull 거부 | GHCR 패키지가 private인데 서버 미로그인 → 1-4 참고 |
| 소형 VM OOM | `.env.prod` 의 `JAVA_TOOL_OPTIONS=-Xmx256m` 로 축소 |
