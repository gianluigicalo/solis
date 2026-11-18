# syntax=docker/dockerfile:1

# ---- Build: compila l'app Angular ----
# stessa versione di node.version in pom.xml
FROM node:22.23.4-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# ---- Runtime: nginx serve i file statici e inoltra /api/* ai servizi ----
FROM nginx:1.27-alpine
# I template in /etc/nginx/templates vengono elaborati con envsubst all'avvio
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html
ENV INVERTER_URL=http://inverter:8080 \
    OPENMETEO_URL=http://openmeteo:8080 \
    ADVISOR_URL=http://advisor:8080 \
    NGINX_ENVSUBST_FILTER="^(INVERTER|OPENMETEO|ADVISOR)_URL$"
EXPOSE 80
