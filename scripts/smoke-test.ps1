$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8080'

Write-Host '1. Health'
Invoke-RestMethod "$base/health"

Write-Host '2. Login'
$login = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType 'application/json' -Body (@{login='mechanic01'; password='demo12345'} | ConvertTo-Json)
$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }

Write-Host '3. Work orders'
$orders = Invoke-RestMethod -Headers $headers "$base/api/v1/work-orders"
$orders | ConvertTo-Json -Depth 10

$id = $orders.current.id
Write-Host "4. Details for $id"
$details = Invoke-RestMethod -Headers $headers "$base/api/v1/work-orders/$id"
$details | ConvertTo-Json -Depth 10

$op = $details.operations | Where-Object { $_.status -ne 'COMPLETED' } | Select-Object -First 1
if ($null -eq $op) { throw 'No non-completed operation in demo order' }

Write-Host '5. Start operation'
$startKey = [guid]::NewGuid().ToString()
Invoke-RestMethod -Method Post -Headers ($headers + @{'Idempotency-Key'=$startKey}) -Uri "$base/api/v1/work-orders/$id/operations/$($op.id)/start"

Write-Host '6. Repeat same request to verify idempotency'
Invoke-RestMethod -Method Post -Headers ($headers + @{'Idempotency-Key'=$startKey}) -Uri "$base/api/v1/work-orders/$id/operations/$($op.id)/start"

Write-Host '7. Sync status'
Invoke-RestMethod -Headers $headers "$base/api/v1/sync/status" | ConvertTo-Json
