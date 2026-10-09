FROM php:8.4-cli-alpine AS build
RUN apk add --no-cache postgresql-dev curl-dev && docker-php-ext-install pdo_pgsql curl
FROM php:8.4-cli-alpine
RUN apk add --no-cache libpq libcurl
COPY --from=build /usr/local/lib/php/extensions/ /usr/local/lib/php/extensions/
COPY --from=build /usr/local/etc/php/conf.d/ /usr/local/etc/php/conf.d/
WORKDIR /app
COPY *.php .
USER 65534:65534
EXPOSE 8080
CMD ["php","-S","0.0.0.0:8080","main.php"]
