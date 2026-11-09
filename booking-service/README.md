# Booking Service

Порт `8082`, Swagger http://localhost:8082/swagger-ui.html. H2 `bookingdb`: `users`, `bookings`. Регистрация, вход, ADMIN CRUD пользователей, USER бронирования/личная история; JWT HS256 (1 час, BCrypt пароли). `POST /booking` требует `X-Request-Id` и поддерживает `autoSelect`.

Сага: отдельный коммит `PENDING`, идемпотентное подтверждение в Hotel через Eureka, локальный `CONFIRMED`, при ошибке `CANCELLED` и `release`. Есть retry/timeout/circuit breaker для confirm, отдельный retry компенсации и периодическая очистка. Запуск из корня: `mvn -pl booking-service spring-boot:run`. Тесты `mvn -pl booking-service test`. Нужны Eureka и Hotel для end-to-end, но не для unit/integration MockMvc тестов.
