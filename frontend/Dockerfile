# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --silent

COPY . .
# 구글 로그인 버튼 노출 여부 — Vite 는 빌드타임에 이 값을 코드에 박는다.
# 기본 false(숨김). 켜려면 빌드 시 --build-arg VITE_GOOGLE_LOGIN_ENABLED=true (CI는 GitHub 변수로 주입).
ARG VITE_GOOGLE_LOGIN_ENABLED=false
ENV VITE_GOOGLE_LOGIN_ENABLED=$VITE_GOOGLE_LOGIN_ENABLED
RUN npm run build

# ── Stage 2: Nginx ───────────────────────────────────────────────────────────
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
