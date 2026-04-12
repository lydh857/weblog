<#
用途：模拟自动化脚本攻击验证码接口，输出机器可读结果和人工报告草稿。
主要输出：docs/captcha-attack-regression-result.json、docs/captcha-attack-regression-report.txt。
#>

param(
  [string]$BaseUrl = "http://127.0.0.1:9091",
  [string]$ClientOrigin = "http://localhost:3000",
  [int]$SessionFailureAttempts = 6,
  [int]$IpFailureAttempts = 4,
  [int]$SessionMaxAttempts = 20,
  [int]$IpMaxAttempts = 20,
  [int]$IntervalMs = 40,
  [string]$SummaryJsonPath = "docs/captcha-attack-regression-result.json",
  [string]$ReportTextPath = "docs/captcha-attack-regression-report.txt",
  [switch]$FailOnUnexpectedSuccess,
  [switch]$RequireBlacklistActivation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:StartedAt = [DateTimeOffset]::Now
$script:RequestErrors = New-Object System.Collections.Generic.List[string]

function Join-Url([string]$base, [string]$pathAndQuery) {
  if ($base.EndsWith('/')) {
    return "$($base.TrimEnd('/'))$pathAndQuery"
  }
  return "$base$pathAndQuery"
}

function Convert-RequestPayload([object]$body) {
  if ($null -eq $body) {
    return $null
  }

  if ($body -is [System.Array]) {
    if ($body.Count -eq 0) {
      return '[]'
    }
    return $body | ConvertTo-Json -Depth 10 -AsArray
  }

  if (($body -is [System.Collections.IList]) -and -not ($body -is [string])) {
    if ($body.Count -eq 0) {
      return '[]'
    }
    return $body | ConvertTo-Json -Depth 10 -AsArray
  }

  return $body | ConvertTo-Json -Depth 10
}

function Invoke-Api(
  [string]$method,
  [string]$url,
  [Microsoft.PowerShell.Commands.WebRequestSession]$webSession,
  [hashtable]$headers = @{},
  [object]$body = $null
) {
  $payload = Convert-RequestPayload $body

  try {
    $resp = Invoke-WebRequest -Method $method -Uri $url -WebSession $webSession -Headers $headers -Body $payload -ContentType "application/json" -TimeoutSec 15 -UseBasicParsing
    $json = $null
    if (-not [string]::IsNullOrWhiteSpace($resp.Content)) {
      try {
        $json = $resp.Content | ConvertFrom-Json
      } catch {
        $json = $null
      }
    }

    return [pscustomobject]@{
      statusCode = [int]$resp.StatusCode
      json = $json
      raw = [string]$resp.Content
    }
  } catch {
    $webResp = $null
    if ($null -ne $_.Exception.PSObject.Properties['Response']) {
      $webResp = $_.Exception.Response
    }
    if ($null -eq $webResp) {
      return [pscustomobject]@{
        statusCode = 0
        json = $null
        raw = [string]$_.Exception.Message
      }
    }

    $statusCode = 0
    try {
      $statusCode = [int]$webResp.StatusCode
    } catch {
      $statusCode = 0
    }

    $raw = ''
    if ($webResp -is [System.Net.Http.HttpResponseMessage]) {
      try {
        $raw = $webResp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      } catch {
        $raw = ''
      }
    } elseif ($null -ne $webResp.PSObject.Methods['GetResponseStream']) {
      $reader = New-Object System.IO.StreamReader($webResp.GetResponseStream())
      $raw = $reader.ReadToEnd()
      $reader.Close()
    }

    $json = $null
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
      try {
        $json = $raw | ConvertFrom-Json
      } catch {
        $json = $null
      }
    }

    return [pscustomobject]@{
      statusCode = $statusCode
      json = $json
      raw = $raw
    }
  }
}

function Get-ResponseCode([object]$response) {
  if ($null -ne $response -and $null -ne $response.json) {
    $codeProp = $response.json.PSObject.Properties['code']
    if ($null -ne $codeProp -and $null -ne $codeProp.Value) {
      try {
        return [int]$codeProp.Value
      } catch {
      }
    }
  }
  return [int]$response.statusCode
}

function Get-ResponseMessage([object]$response) {
  if ($null -ne $response -and $null -ne $response.json) {
    $messageProp = $response.json.PSObject.Properties['message']
    if ($null -ne $messageProp -and $null -ne $messageProp.Value) {
      return [string]$messageProp.Value
    }
  }
  return [string]$response.raw
}

function Is-BlacklistMessage([string]$message) {
  if ([string]::IsNullOrWhiteSpace($message)) {
    return $false
  }
  return $message.Contains("请求过于频繁，请稍后重试")
}

function New-BotTrack([int]$sliderPosition) {
  $base = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  return @(
    @{ x = 0.0; y = 0.0; timestamp = $base },
    @{ x = 8.0; y = 0.2; timestamp = $base + 25 },
    @{ x = 21.0; y = -0.1; timestamp = $base + 55 },
    @{ x = 38.0; y = 0.3; timestamp = $base + 95 },
    @{ x = [double]$sliderPosition; y = 0.0; timestamp = $base + 140 }
  )
}

function New-VerifyPayload([string]$captchaToken) {
  $sliderPosition = 60
  return [ordered]@{
    captchaToken = $captchaToken
    sliderPosition = $sliderPosition
    slideTrack = (New-BotTrack -sliderPosition $sliderPosition)
  }
}

function Test-BlockedFromAttempt([object]$attempt) {
  if ($null -eq $attempt) {
    return $false
  }

  if ($attempt.generateCode -eq 429 -or $attempt.verifyCode -eq 429) {
    return $true
  }

  if (Is-BlacklistMessage ([string]$attempt.generateMessage)) {
    return $true
  }

  if (Is-BlacklistMessage ([string]$attempt.verifyMessage)) {
    return $true
  }

  return $false
}

function Invoke-FailureAttempt(
  [Microsoft.PowerShell.Commands.WebRequestSession]$webSession,
  [string]$label
) {
  $headers = @{
    'User-Agent' = "captcha-regression/$label"
    'Accept' = 'application/json'
    'Origin' = $ClientOrigin
    'Referer' = "$ClientOrigin/"
  }

  $generateUrl = Join-Url $BaseUrl '/api/captcha/generate'
  $generateResp = Invoke-Api -method 'GET' -url $generateUrl -webSession $webSession -headers $headers
  $generateCode = Get-ResponseCode $generateResp
  $generateMessage = Get-ResponseMessage $generateResp

  if ($generateCode -ne 200 -or $null -eq $generateResp.json -or $null -eq $generateResp.json.data -or [string]::IsNullOrWhiteSpace([string]$generateResp.json.data.captchaToken)) {
    $script:RequestErrors.Add("generate failed ($label): code=$generateCode, message=$generateMessage")
    return [pscustomobject]@{
      generated = $false
      verifySuccess = $false
      generateCode = $generateCode
      generateMessage = $generateMessage
      verifyCode = 0
      verifyMessage = ''
    }
  }

  $captchaToken = [string]$generateResp.json.data.captchaToken
  $verifyUrl = Join-Url $BaseUrl '/api/captcha/verify'
  $verifyResp = Invoke-Api -method 'POST' -url $verifyUrl -webSession $webSession -headers $headers -body (New-VerifyPayload -captchaToken $captchaToken)
  $verifyCode = Get-ResponseCode $verifyResp
  $verifyMessage = Get-ResponseMessage $verifyResp
  $verifySuccess = $false

  if ($null -ne $verifyResp.json -and $null -ne $verifyResp.json.data) {
    $successProp = $verifyResp.json.data.PSObject.Properties['success']
    if ($null -ne $successProp -and $null -ne $successProp.Value) {
      $verifySuccess = [bool]$successProp.Value
    }
  }

  Start-Sleep -Milliseconds $IntervalMs

  return [pscustomobject]@{
    generated = $true
    verifySuccess = $verifySuccess
    generateCode = $generateCode
    generateMessage = $generateMessage
    verifyCode = $verifyCode
    verifyMessage = $verifyMessage
  }
}

function Invoke-BlacklistProbe(
  [Microsoft.PowerShell.Commands.WebRequestSession]$webSession,
  [string]$label
) {
  $fakeToken = [Guid]::NewGuid().ToString()
  $headers = @{
    'User-Agent' = "captcha-regression-probe/$label"
    'Accept' = 'application/json'
    'Origin' = $ClientOrigin
    'Referer' = "$ClientOrigin/"
  }
  $verifyUrl = Join-Url $BaseUrl '/api/captcha/verify'
  $resp = Invoke-Api -method 'POST' -url $verifyUrl -webSession $webSession -headers $headers -body (New-VerifyPayload -captchaToken $fakeToken)
  $code = Get-ResponseCode $resp
  $message = Get-ResponseMessage $resp
  return [pscustomobject]@{
    code = $code
    message = $message
    blocked = ($code -eq 429 -or (Is-BlacklistMessage $message))
  }
}

Write-Host "== CAPTCHA attack regression start =="
Write-Host "BaseUrl: $BaseUrl"

$safeSessionBaseline = [Math]::Max($SessionFailureAttempts, 1)
$safeIpBaseline = [Math]::Max($IpFailureAttempts, 1)
$safeSessionMax = [Math]::Max($SessionMaxAttempts, $safeSessionBaseline)
$safeIpMax = [Math]::Max($IpMaxAttempts, $safeIpBaseline)

$sessionMetrics = [ordered]@{
  attempts = 0
  generated = 0
  unexpectedSuccess = 0
  verifyRejected = 0
}

$sameSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$sessionBlockedByAttempt = $false
for ($i = 1; $i -le $safeSessionBaseline; $i++) {
  $attempt = Invoke-FailureAttempt -webSession $sameSession -label "same-session-$i"
  $sessionMetrics.attempts++
  if ($attempt.generated) {
    $sessionMetrics.generated++
  }
  if ($attempt.verifySuccess) {
    $sessionMetrics.unexpectedSuccess++
  }
  if (-not $attempt.verifySuccess) {
    $sessionMetrics.verifyRejected++
  }

  if (Test-BlockedFromAttempt $attempt) {
    $sessionBlockedByAttempt = $true
    break
  }
}

if ($RequireBlacklistActivation -and -not $sessionBlockedByAttempt) {
  for ($i = $sessionMetrics.attempts + 1; $i -le $safeSessionMax; $i++) {
    $attempt = Invoke-FailureAttempt -webSession $sameSession -label "same-session-$i"
    $sessionMetrics.attempts++
    if ($attempt.generated) {
      $sessionMetrics.generated++
    }
    if ($attempt.verifySuccess) {
      $sessionMetrics.unexpectedSuccess++
    }
    if (-not $attempt.verifySuccess) {
      $sessionMetrics.verifyRejected++
    }

    if (Test-BlockedFromAttempt $attempt) {
      $sessionBlockedByAttempt = $true
      break
    }
  }
}

$sessionProbe = Invoke-BlacklistProbe -webSession $sameSession -label 'same-session'

$ipMetrics = [ordered]@{
  attempts = 0
  generated = 0
  unexpectedSuccess = 0
  verifyRejected = 0
}

$ipBlockedByAttempt = $false
for ($i = 1; $i -le $safeIpBaseline; $i++) {
  $rotatedSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
  $attempt = Invoke-FailureAttempt -webSession $rotatedSession -label "ip-rotate-$i"
  $ipMetrics.attempts++
  if ($attempt.generated) {
    $ipMetrics.generated++
  }
  if ($attempt.verifySuccess) {
    $ipMetrics.unexpectedSuccess++
  }
  if (-not $attempt.verifySuccess) {
    $ipMetrics.verifyRejected++
  }

  if (Test-BlockedFromAttempt $attempt) {
    $ipBlockedByAttempt = $true
    break
  }
}

if ($RequireBlacklistActivation -and -not $ipBlockedByAttempt) {
  for ($i = $ipMetrics.attempts + 1; $i -le $safeIpMax; $i++) {
    $rotatedSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $attempt = Invoke-FailureAttempt -webSession $rotatedSession -label "ip-rotate-$i"
    $ipMetrics.attempts++
    if ($attempt.generated) {
      $ipMetrics.generated++
    }
    if ($attempt.verifySuccess) {
      $ipMetrics.unexpectedSuccess++
    }
    if (-not $attempt.verifySuccess) {
      $ipMetrics.verifyRejected++
    }

    if (Test-BlockedFromAttempt $attempt) {
      $ipBlockedByAttempt = $true
      break
    }
  }
}

$ipProbeSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$ipProbe = Invoke-BlacklistProbe -webSession $ipProbeSession -label 'ip-probe'

$totalUnexpected = [int]$sessionMetrics.unexpectedSuccess + [int]$ipMetrics.unexpectedSuccess
$sessionRateLimited = @($script:RequestErrors | Where-Object { $_ -like 'generate failed (same-session-*' -and $_ -like '*code=429*' }).Count -gt 0
$ipRateLimited = @($script:RequestErrors | Where-Object { $_ -like 'generate failed (ip-rotate-*' -and $_ -like '*code=429*' }).Count -gt 0
$sessionBlocked = [bool]$sessionProbe.blocked -or $sessionRateLimited
$ipBlocked = [bool]$ipProbe.blocked -or $ipRateLimited
$durationMs = [Math]::Round((([DateTimeOffset]::Now - $script:StartedAt).TotalMilliseconds), 2)

$failReasons = New-Object System.Collections.Generic.List[string]
if ($FailOnUnexpectedSuccess -and $totalUnexpected -gt 0) {
  $failReasons.Add("unexpected captcha verify success count: $totalUnexpected")
}

if ($RequireBlacklistActivation -and -not $sessionBlocked) {
  $failReasons.Add("session blacklist probe not activated")
}

if ($RequireBlacklistActivation -and -not $ipBlocked) {
  $failReasons.Add("ip blacklist probe not activated")
}

if ($RequireBlacklistActivation -and -not $sessionBlocked -and -not $ipBlocked -and $script:RequestErrors.Count -eq 0) {
  $failReasons.Add("hint: check captcha blacklist thresholds and referer/rate-limit policy")
}

$summary = [ordered]@{
  timestamp = [DateTimeOffset]::Now.ToString('o')
  baseUrl = $BaseUrl
  durationMs = $durationMs
  sessionBaselineAttempts = $safeSessionBaseline
  ipBaselineAttempts = $safeIpBaseline
  sessionMaxAttempts = $safeSessionMax
  ipMaxAttempts = $safeIpMax
  sessionFailureAttempts = $SessionFailureAttempts
  ipFailureAttempts = $IpFailureAttempts
  totalUnexpectedSuccess = $totalUnexpected
  sessionMetrics = $sessionMetrics
  ipMetrics = $ipMetrics
  sessionProbe = $sessionProbe
  ipProbe = $ipProbe
  requestErrorCount = $script:RequestErrors.Count
  requestErrors = @($script:RequestErrors)
  failReasons = @($failReasons)
  passed = ($failReasons.Count -eq 0)
}

if (-not [string]::IsNullOrWhiteSpace($SummaryJsonPath)) {
  $summaryDir = Split-Path -Parent $SummaryJsonPath
  if (-not [string]::IsNullOrWhiteSpace($summaryDir) -and -not (Test-Path $summaryDir)) {
    New-Item -ItemType Directory -Path $summaryDir -Force | Out-Null
  }
  $summary | ConvertTo-Json -Depth 10 | Set-Content -Path $SummaryJsonPath -Encoding UTF8
  Write-Host "Summary JSON written: $SummaryJsonPath"
}

if (-not [string]::IsNullOrWhiteSpace($ReportTextPath)) {
  $reportDir = Split-Path -Parent $ReportTextPath
  if (-not [string]::IsNullOrWhiteSpace($reportDir) -and -not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
  }

  $reportLines = @(
    "CAPTCHA Attack Regression Report",
    "runAt=$([DateTimeOffset]::Now.ToString('o'))",
    "baseUrl=$BaseUrl",
    "durationMs=$durationMs",
    "sessionFailureAttemptsConfigured=$SessionFailureAttempts",
    "ipFailureAttemptsConfigured=$IpFailureAttempts",
    "sessionFailureAttemptsActual=$($sessionMetrics.attempts)",
    "ipFailureAttemptsActual=$($ipMetrics.attempts)",
    "sessionMaxAttempts=$safeSessionMax",
    "ipMaxAttempts=$safeIpMax",
    "sessionProbeBlocked=$sessionBlocked (code=$($sessionProbe.code), message=$($sessionProbe.message))",
    "ipProbeBlocked=$ipBlocked (code=$($ipProbe.code), message=$($ipProbe.message))",
    "totalUnexpectedSuccess=$totalUnexpected",
    "requestErrorCount=$($script:RequestErrors.Count)",
    "",
    "Manual fill items:",
    "- attack tool/strategy:",
    "- success rate target:",
    "- bypass cost estimate:",
    "- conclusion:"
  )
  Set-Content -Path $ReportTextPath -Value $reportLines -Encoding UTF8
  Write-Host "Report text written: $ReportTextPath"
}

if ($failReasons.Count -gt 0) {
  Write-Host "[FAIL] CAPTCHA attack regression failed"
  foreach ($reason in $failReasons) {
    Write-Host " - $reason"
  }
  exit 1
}

Write-Host "[PASS] CAPTCHA attack regression passed"
