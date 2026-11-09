# Схема БД (обе БД: независимые H2, `ddl-auto=create-drop`)

## Booking Service — `jdbc:h2:mem:bookingdb`

- `users`: `id` (PK), `username` (UNIQUE), `password` (**BCrypt hash**), `role` (`USER`/`ADMIN`).
- `bookings`: `id` (PK), `user_id` (FK → users), `room_id` (идентификатор внешнего сервиса), `hotel_id` (опционально), `request_id` (UNIQUE), `auto_select`, `start_date`, `end_date`, `status` (`PENDING`/`CONFIRMED`/`CANCELLED`), `release_pending`, `created_at`, `version` (JPA optimistic version). Индексы по `user_id`, `status`.

## Hotel Service — `jdbc:h2:mem:hoteldb`

- `hotels`: `id` (PK), `name`, `address`.
- `rooms`: `id` (PK), `hotel_id` (FK → hotels), `number`, `available` (операционное состояние), `times_booked`, `capacity`. `UNIQUE (hotel_id, number)` + индекс по `hotel_id`.
- `room_slots`: `id` (PK), `room_id` (FK → rooms), `booking_id` (UNIQUE; внешний ID из Booking Service), `request_id`, `start_date` (nullable для предварительной компенсации), `end_date` (nullable), `status` (`ACTIVE`/`RELEASED`), `created_at`. Индекс `room_id,status,start_date,end_date`.

`ACTIVE` перекрывается с новым бронированием, когда `existing.start_date < new.end_date AND existing.end_date > new.start_date`; `RELEASED` никогда не занимает даты. `times_booked` — **число действующих подтверждённых слотов**, увеличивается на первый confirm и уменьшается на первый release; не счётчик всех когда-либо созданных исторических броней.

Для демонстрации БД будут пересозданы после каждого перезапуска соответствующего сервиса. Это учебная настройка. H2 console выключена из соображений безопасности.
