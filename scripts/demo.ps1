$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$start = (Get-Date).AddDays(7).ToString('yyyy-MM-dd')
$end = (Get-Date).AddDays(10).ToString('yyyy-MM-dd')

Write-Host "1. Login as demo USER"
$auth = Invoke-RestMethod -Method Post -Uri "$base/user/auth" -ContentType "application/json" -Body (@{username="demo";password="User123!"} | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($auth.token)" }
Write-Host "Token issued, userId=$($auth.userId)"

Write-Host "2. Fetch available rooms"
# Invoke-RestMethod in Windows PowerShell may output a JSON array as a single pipeline item.
# Explicitly enumerate the response so that each element is one room.
$roomsResponse = Invoke-RestMethod -Method Get -Uri "$base/api/rooms?startDate=$start&endDate=$end" -Headers $headers
$free = @()
foreach ($room in $roomsResponse) { $free += $room }
if ($free.Count -eq 0) { throw "No rooms returned" }
$roomId = [long]$free[0].id
Write-Host "Selected roomId=$roomId from $($free.Count) available rooms"

Write-Host "3. Create booking"
$requestId = [guid]::NewGuid().ToString()
$h = $headers.Clone()
$h['X-Request-Id'] = $requestId
$body = @{startDate=$start;endDate=$end;roomId=$roomId;autoSelect=$false} | ConvertTo-Json
$book = Invoke-RestMethod -Method Post -Uri "$base/booking" -Headers $h -ContentType "application/json" -Body $body
if ($book.status -ne 'CONFIRMED') { throw "Expected CONFIRMED, got $($book.status)" }
Write-Host "Booking confirmed: id=$($book.id), status=$($book.status)"

Write-Host "4. Re-deliver same requestId: must use same booking id"
$duplicate = Invoke-RestMethod -Method Post -Uri "$base/booking" -Headers $h -ContentType "application/json" -Body $body
if ($duplicate.id -ne $book.id) { throw "Idempotency FAILED: two different bookings" }
Write-Host "Idempotency OK"

Write-Host "5. Concurrent-date conflict with new requestId: must return 409"
$h['X-Request-Id'] = [guid]::NewGuid().ToString()
try {
    Invoke-RestMethod -Method Post -Uri "$base/booking" -Headers $h -ContentType "application/json" -Body $body | Out-Null
    throw "FAIL: expected HTTP 409"
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    if ($code -ne 409) { throw "FAIL: expected HTTP 409, got $code. $($_.Exception.Message)" }
    Write-Host "Conflict 409 OK"
}

Write-Host "6. Cancel first booking and check history"
$cancelled = Invoke-RestMethod -Method Delete -Uri "$base/booking/$($book.id)" -Headers $headers
if ($cancelled.status -ne 'CANCELLED') { throw "Expected CANCELLED" }
$history = Invoke-RestMethod -Method Get -Uri "$base/bookings?page=0&size=20" -Headers $headers
Write-Host "Cancelled id=$($book.id), history rows=$($history.totalElements)"
Write-Host "SMOKE TEST PASSED"
