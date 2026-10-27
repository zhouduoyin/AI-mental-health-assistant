# 前端镜像：Node 构建 -> Nginx 提供静态文件并反代 /api
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --include=dev

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
