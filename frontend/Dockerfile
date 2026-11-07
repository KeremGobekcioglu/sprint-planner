# ---------- Frontend + HTTPS reverse proxy (Caddy) ----------
# Build context must be the REPO ROOT-relative "frontend" dir (see docker-compose.prod.yml).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# VITE_* values are baked into the static bundle at build time (they are public by nature)
ARG VITE_TRELLO_API_KEY=""
ENV VITE_USE_REAL_API=true \
    VITE_API_BASE_URL="" \
    VITE_TRELLO_API_KEY=$VITE_TRELLO_API_KEY
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /app/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
