# План тестирования

## Автоматизированные тесты (JUnit 5 + MockMvc + Mockito + настоящая H2)

- `HotelServiceTest.duplicateConfirmAndReleaseAreIdempotent`: две одинаковые подтверждающие операции и две компенсации, счётчик не меняется повторно, `RELEASED` tombstone.
- `HotelServiceTest.preemptiveReleasePreventsLateConfirm`: компенсация раньше запоздавшего confirm.
- `HotelServiceTest.twoParallelBookingsOfSameDatesHaveOnlyOneWinner`: две поточные операции одного номера, только один подтверждённый слот.
- `HotelServiceTest.recommendationsAreOrderedByTimesBookedThenId`: сортировка и агрегат.
- `HotelServiceTest.permissionsAndValidationReturnCorrectStatuses`: 401/403/400/201, INTERNAL защищён.
- `BookingServiceTest.successfulBookingAndRepeatedDeliveryUseOneRow`: `PENDING→CONFIRMED`, повторная доставка, история, отмена.
- `BookingServiceTest.serviceConflictCancelsAndCompensates`: 409, `CANCELLED`, release.
- `BookingServiceTest.timeoutCancelsAndKeepsPendingCompensationWhenReleaseAlsoFails`: тайм-аут, сохранение маркера.
- `BookingServiceTest.retryTaskCompletesCompensationWhenHotelRecovers`: повтор release на следующем цикле.
- `BookingServiceTest.autoSelectPicksLeastUsedRoomAndCancellationRetrySucceeds`: сортировка `(times_booked,id)` с игнорированием переданного roomId.
- `BookingServiceTest.onlyOwnerMayReadOtherUsersBooking`: USER чужую бронь не получает.
- `BookingServiceTest.idempotencyKeyCannotBeReusedWithDifferentPayload`: 409 и только одна строка.
- `BookingServiceTest.validationAndSecurity`: 400/401/403.
- `JwtAuthIntegrationTest.registrationIssuesRealJwtThatResourceServerAccepts`: действительная выдача JWT, приём Resource Server, запрет ADMIN для USER, плохой JWT и неверный пароль.

Команда: `mvn clean verify` из корня (Eureka отключён в профилях тестов).

## Ручная сквозная проверка (smoke test)

1. `docker compose up --build -d`, дождаться регистрации сервисов в Eureka.
2. `powershell -ExecutionPolicy Bypass -File .\scripts\demo.ps1`.
3. Проверить Swagger на портах 8081/8082 и Eureka на 8761.
4. Проверить неавторизованный GET `/bookings` → 401; USER POST `/api/hotels` → 403.
5. Смоделировать сбой Hotel Service по инструкции README, проверить `CANCELLED`, `COMPENSATION_PENDING`, затем `COMPENSATED`.

Область покрытия: автоматические сервисные тесты не запускают все четыре приложения одновременно. Межсервисное взаимодействие через Gateway и Eureka проверяется отдельным smoke-тестом. Положительный результат `mvn clean verify` сам по себе не гарантирует успешного запуска всей распределённой системы.
