# Run from the project root with all four services already started.
# This script NEVER stops or starts services automatically.
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$HotelHealthUrl = 'http://localhost:8081/actuator/health',
    [int]$RecoveryTimeoutSeconds = 90
)
$ErrorActionPreference = 'Stop'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
}
function Get-StatusCode($ErrorRecord) {
    if ($null -ne $ErrorRecord.Exception.Response) {
        return [int]$ErrorRecord.Exception.Response.StatusCode
    }
    return $null
}
function Get-BookingHistory($Headers) {
    return Invoke-RestMethod -Method Get -Uri "$BaseUrl/bookings?page=0&size=100" -Headers $Headers
}

Write-Host 'STEP 1: Login and select a free room while Hotel Service is running'
$auth = Invoke-RestMethod -Method Post -Uri "$BaseUrl/user/auth" -ContentType 'application/json' -Body (@{username='demo';password='User123!'} | ConvertTo-Json)
Assert-True (-not [string]::IsNullOrWhiteSpace($auth.token)) 'Login did not return JWT'
$headers = @{ Authorization = "Bearer $($auth.token)" }
$start = (Get-Date).Date.AddDays(14).ToString('yyyy-MM-dd')
$end = (Get-Date).Date.AddDays(17).ToString('yyyy-MM-dd')
$roomsResponse = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/rooms?startDate=$start&endDate=$end" -Headers $headers
$rooms = @($roomsResponse | ForEach-Object { $_ })
Assert-True ($rooms.Count -gt 0) 'No available rooms; retry with another date range'
$roomId = $rooms[0].id
Assert-True ($null -ne $roomId) 'Room id not found in response'
$requestId = [guid]::NewGuid().ToString()
$body = @{startDate=$start;endDate=$end;roomId=[long]$roomId;autoSelect=$false} | ConvertTo-Json
Write-Host "Selected roomId=$roomId, requestId=$requestId"

Write-Host ''
Write-Host 'STEP 2: MANUALLY STOP ONLY Hotel Service in IntelliJ or Docker Desktop.' -ForegroundColor Yellow
Write-Host 'Keep Eureka, Booking Service and API Gateway running.'
Read-Host 'After Hotel Service is stopped, press Enter'
try {
    $null = Invoke-RestMethod -Method Get -Uri $HotelHealthUrl -TimeoutSec 3
    throw 'FAIL: Hotel Service is still responding on its health port; stop it before testing'
} catch {
    if ($_.Exception.Message -like 'FAIL:*') { throw }
    $status = Get-StatusCode $_
    if ($null -ne $status) { throw "FAIL: Hotel Service HTTP endpoint still responds (status $status)" }
}
Write-Host 'Hotel Service is unreachable as expected.'

Write-Host 'STEP 3: POST booking while Hotel Service is down (expect 503)'
$postHeaders = $headers.Clone()
$postHeaders['X-Request-Id'] = $requestId
$code = $null
try {
    $null = Invoke-RestMethod -Method Post -Uri "$BaseUrl/booking" -Headers $postHeaders -ContentType 'application/json' -Body $body -TimeoutSec 25
    throw 'FAIL: booking unexpectedly succeeded while Hotel Service was down'
} catch {
    if ($_.Exception.Message -like 'FAIL:*') { throw }
    $code = Get-StatusCode $_
    Assert-True ($code -eq 503) "Expected HTTP 503, got $code. $($_.Exception.Message)"
}
Write-Host 'HTTP 503 verified.' -ForegroundColor Green

Write-Host 'STEP 4: Find CANCELLED booking with releasePending=true'
$history = Get-BookingHistory $headers
$items = @($history.content)
$booking = $items | Where-Object { $_.requestId -eq $requestId } | Select-Object -First 1
Assert-True ($null -ne $booking) "Booking with requestId=$requestId missing from history"
Assert-True ($booking.status -eq 'CANCELLED') "Expected CANCELLED, got $($booking.status)"
Assert-True ([bool]$booking.releasePending) 'Expected releasePending=true while Hotel Service is down'
Write-Host "Cancelled booking id=$($booking.id), releasePending=$($booking.releasePending)" -ForegroundColor Green

Write-Host ''
Write-Host 'STEP 5: MANUALLY RESTART Hotel Service (only it).' -ForegroundColor Yellow
Write-Host 'IMPORTANT: This project uses in-memory H2. Restarting Hotel Service resets its database.' -ForegroundColor Yellow
Read-Host 'After you have restarted Hotel Service, press Enter'
$deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Method Get -Uri $HotelHealthUrl -TimeoutSec 3
        if ($health.status -eq 'UP') { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}
Assert-True $ready "Hotel Service did not become healthy within $RecoveryTimeoutSeconds seconds"
Write-Host 'Hotel Service is healthy again.'

Write-Host 'STEP 6: Wait for scheduled compensation retry (releasePending=false)'
$recovered = $false
$deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    $current = Invoke-RestMethod -Method Get -Uri "$BaseUrl/booking/$($booking.id)" -Headers $headers
    if ($current.status -eq 'CANCELLED' -and -not [bool]$current.releasePending) {
        $recovered = $true
        break
    }
    Start-Sleep -Seconds 3
}
Assert-True $recovered "Compensation was not completed within $RecoveryTimeoutSeconds seconds"
Write-Host "Booking id=$($booking.id): CANCELLED, releasePending=false" -ForegroundColor Green
Write-Host 'FAILOVER TEST PASSED' -ForegroundColor Green
Write-Host 'NOTE: This validates retries in Booking Service, not data durability of Hotel Service.'
