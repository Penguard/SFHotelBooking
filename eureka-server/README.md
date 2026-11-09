# Eureka Server

Реестр регистрации Spring Cloud Netflix. Порт `8761`, UI: http://localhost:8761. Не регистрирует себя; сервисы Booking, Hotel, Gateway используют переменную `EUREKA_URI` (по умолчанию `http://localhost:8761/eureka/`). Запуск из корня: `mvn -pl eureka-server spring-boot:run`. Локальная БД не требуется.
