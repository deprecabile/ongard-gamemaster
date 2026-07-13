# --- Build stage ---
FROM node:24-slim AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run release

# --- Runtime stage ---
FROM nginx:alpine

RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/ondgard-fe.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
