# Hotel Service

Порт `8081`, Swagger http://localhost:8081/swagger-ui.html. Независимая H2 `hoteldb`: `hotels`, `rooms`, `room_slots`. REST CRUD отелей/комнат, GET свободных на период, GET рекомендаций по `times_booked ASC,id ASC`, статистика занятости за период. INTERNAL `confirm-availability`/`release` принимают только сервисный JWT и выполняют `@Transactional` с пессимистической блокировкой строки комнаты, чтобы не возникли двойные брони на пересекающиеся даты.

Запуск из корня: `mvn -pl hotel-service spring-boot:run`. Тесты: `mvn -pl hotel-service test`. Подробные маршруты в [общем README](../README.md).
