# syntax=docker/dockerfile:1
# React build -> static files -> unprivileged nginx (no Vite dev server in prod).
# The API URL is baked at build time via VITE_API_BASE_URL (see compose args);
# the browser then calls the backend directly (CORS allows the frontend origin).
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
