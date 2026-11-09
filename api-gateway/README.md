# API Gateway

Reactive Spring Cloud Gateway Server WebFlux, порт `8080`, discovery маршруты: `/user`, `/user/**`, `/users`, `/booking`, `/booking/**`, `/bookings` → Booking; `/api/hotels/**`, `/api/rooms/**` → Hotel. Валидирует JWT, пропускает `/user/register`, `/user/auth` без JWT, запрещает слоты `/api/rooms/{id}/confirm-availability` и `/release` на шлюзе, добавляет и протоколирует `X-Trace-Id`. Boot 3.5/Cloud 2025 используют `spring.cloud.gateway.server.webflux.routes`.

Запуск: `mvn -pl api-gateway spring-boot:run`. Нужны Eureka и одинаковый `JWT_SECRET` с сервисами. Нет БД.
