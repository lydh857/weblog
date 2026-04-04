<#
用途：验证 VerifyToken 在并发请求下仅可消费一次（一次性语义）。
输出：docs/verify-token-concurrency-result.json、docs/verify-token-concurrency-report.txt。

说明：
1) 默认先自动获取 VerifyToken（调用 /api/captcha/generate + /api/captcha/verify 多次尝试）。
2) 也可通过 -VerifyToken 直接传入已获取的令牌，跳过自动获取。
#>

param(
  [string]$BaseUrl = "http://127.0.0.1:9091",
  [ValidateRange(2, 5)]
  [int]$Concurrency = 5,
  [int]$RequestTimeoutSec = 15,
  [string]$VerifyToken = "",
  [string]$TokenClientIp = "",
  [int]$AcquireMaxAttempts = 40,
  [int]$AcquireSolveDelayMs = 420,
  [int]$AcquireSliderMin = 70,
  [int]$AcquireSliderMax = 250,
  [string]$ConsumePath = "/api/portal/access/unlock",
  [string]$RequestOrigin = "http://localhost:3000",
  [string]$RequestReferer = "http://localhost:3000/",
  [string]$SummaryJsonPath = "docs/verify-token-concurrency-result.json",
  [string]$ReportTextPath = "docs/verify-token-concurrency-report.txt",
  [switch]$FailOnRateLimit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:StartedAt = [DateTimeOffset]::Now

function Join-Url([string]$base, [string]$pathAndQuery) {
  $trimmedBase = $base.TrimEnd('/')
  if ([string]::IsNullOrWhiteSpace($pathAndQuery)) {
    return $trimmedBase
  }
  if ($pathAndQuery.StartsWith('/')) {
    return "$trimmedBase$pathAndQuery"
  }
  return "$trimmedBase/$pathAndQuery"
}

function Convert-RequestPayload([object]$body) {
  if ($null -eq $body) {
    return $null
  }
  if ($body -is [System.Array]) {
    if ($body.Count -eq 0) {
      return '[]'
    }
    return $body | ConvertTo-Json -Depth 12 -AsArray
  }
  if (($body -is [System.Collections.IList]) -and -not ($body -is [string])) {
    if ($body.Count -eq 0) {
      return '[]'
    }
    return $body | ConvertTo-Json -Depth 12 -AsArray
  }
  return $body | ConvertTo-Json -Depth 12
}

function Get-PropValue([object]$obj, [string]$propName, [object]$defaultValue = $null) {
  if ($null -eq $obj) {
    return $defaultValue
  }
  $prop = $obj.PSObject.Properties[$propName]
  if ($null -eq $prop -or $null -eq $prop.Value) {
    return $defaultValue
  }
  return $prop.Value
}

function To-Int([object]$value, [int]$defaultValue = 0) {
  if ($null -eq $value) {
    return $defaultValue
  }
  try {
    return [int]$value
  } catch {
    return $defaultValue
  }
}

function Invoke-Api(
  [string]$Method,
  [string]$Url,
  [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession,
  [hashtable]$Headers = @{},
  [object]$Body = $null
) {
  $payload = Convert-RequestPayload $Body

  try {
    $resp = Invoke-WebRequest -Method $Method -Uri $Url -WebSession $WebSession -Headers $Headers -Body $payload -ContentType "application/json" -TimeoutSec $RequestTimeoutSec -UseBasicParsing
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
      error = ""
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
        raw = ""
        error = [string]$_.Exception.Message
      }
    }

    $statusCode = 0
    try {
      $statusCode = [int]$webResp.StatusCode
    } catch {
      $statusCode = 0
    }

    $raw = ""
    if ($webResp -is [System.Net.Http.HttpResponseMessage]) {
      try {
        $raw = $webResp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      } catch {
        $raw = ""
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
      error = [string]$_.Exception.Message
    }
  }
}

function Get-ResponseCode([object]$response) {
  $code = Get-PropValue -obj $response.json -propName 'code'
  if ($null -ne $code) {
    return (To-Int $code ([int]$response.statusCode))
  }
  return [int]$response.statusCode
}

function Get-ResponseMessage([object]$response) {
  $message = Get-PropValue -obj $response.json -propName 'message' -defaultValue ""
  if (-not [string]::IsNullOrWhiteSpace([string]$message)) {
    return [string]$message
  }
  if (-not [string]::IsNullOrWhiteSpace($response.error)) {
    return [string]$response.error
  }
  return [string]$response.raw
}

function New-PublicProbeIp() {
  return "203.0.113.$(Get-Random -Minimum 10 -Maximum 250)"
}

function New-HumanLikeTrack([int]$sliderPosition) {
  $base = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

  $x1 = [Math]::Round($sliderPosition * 0.12, 2)
  $x2 = [Math]::Round($sliderPosition * 0.27, 2)
  $x3 = [Math]::Round($sliderPosition * 0.46, 2)
  $x4 = [Math]::Round($sliderPosition * 0.68, 2)
  $x5 = [Math]::Round($sliderPosition * 0.86, 2)

  if ($x5 -ge $sliderPosition) {
    $x5 = $sliderPosition - 2
  }

  return @(
    @{ x = 0.0; y = 0.00; timestamp = $base },
    @{ x = [double]$x1; y = 0.35; timestamp = $base + 55 },
    @{ x = [double]$x2; y = -0.28; timestamp = $base + 125 },
    @{ x = [double]$x3; y = 0.62; timestamp = $base + 220 },
    @{ x = [double]$x4; y = -0.45; timestamp = $base + 320 },
    @{ x = [double]$x5; y = 0.21; timestamp = $base + 410 },
    @{ x = [double]$sliderPosition; y = 0.00; timestamp = $base + 520 }
  )
}

function Try-AcquireVerifyToken() {
  $generateUrl = Join-Url $BaseUrl "/api/captcha/generate"
  $verifyUrl = Join-Url $BaseUrl "/api/captcha/verify"

  $metrics = [ordered]@{
    attempts = 0
    generateSuccess = 0
    verifySuccess = 0
    lastGenerateCode = 0
    lastVerifyCode = 0
    lastMessage = ""
  }

  for ($i = 1; $i -le $AcquireMaxAttempts; $i++) {
    $metrics.attempts = $i
    $probeIp = New-PublicProbeIp
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $headers = @{
      'Accept' = 'application/json'
      'User-Agent' = "verify-token-concurrency/acquire-$i"
      'X-Forwarded-For' = $probeIp
      'Origin' = $RequestOrigin
      'Referer' = $RequestReferer
    }

    $generateResp = Invoke-Api -Method 'GET' -Url $generateUrl -WebSession $session -Headers $headers
    $generateCode = Get-ResponseCode $generateResp
    $metrics.lastGenerateCode = $generateCode
    $metrics.lastMessage = Get-ResponseMessage $generateResp

    $captchaToken = [string](Get-PropValue -obj (Get-PropValue -obj $generateResp.json -propName 'data') -propName 'captchaToken' -defaultValue "")
    if ($generateCode -ne 200 -or [string]::IsNullOrWhiteSpace($captchaToken)) {
      continue
    }
    $metrics.generateSuccess++

    Start-Sleep -Milliseconds $AcquireSolveDelayMs

    $sliderPosition = Get-Random -Minimum $AcquireSliderMin -Maximum ($AcquireSliderMax + 1)
    $verifyBody = [ordered]@{
      captchaToken = $captchaToken
      sliderPosition = $sliderPosition
      slideTrack = (New-HumanLikeTrack -sliderPosition $sliderPosition)
    }

    $verifyResp = Invoke-Api -Method 'POST' -Url $verifyUrl -WebSession $session -Headers $headers -Body $verifyBody
    $verifyCode = Get-ResponseCode $verifyResp
    $metrics.lastVerifyCode = $verifyCode
    $metrics.lastMessage = Get-ResponseMessage $verifyResp

    $verifyData = Get-PropValue -obj $verifyResp.json -propName 'data'
    $successFlag = [bool](Get-PropValue -obj $verifyData -propName 'success' -defaultValue $false)
    $verifyTokenValue = [string](Get-PropValue -obj $verifyData -propName 'verifyToken' -defaultValue "")

    if ($verifyCode -eq 200 -and $successFlag -and -not [string]::IsNullOrWhiteSpace($verifyTokenValue)) {
      $metrics.verifySuccess++
      return [pscustomobject]@{
        acquired = $true
        verifyToken = $verifyTokenValue
        clientIp = $probeIp
        metrics = $metrics
      }
    }
  }

  return [pscustomobject]@{
    acquired = $false
    verifyToken = ""
    clientIp = ""
    metrics = $metrics
  }
}

function Invoke-ConcurrentConsume(
  [string]$token,
  [string]$clientIp
) {
  $url = Join-Url $BaseUrl $ConsumePath
  $handler = [System.Net.Http.HttpClientHandler]::new()
  $handler.UseCookies = $true
  $client = [System.Net.Http.HttpClient]::new($handler)
  $client.Timeout = [TimeSpan]::FromSeconds([Math]::Max($RequestTimeoutSec, 3))

  $tasks = New-Object System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]
  $requests = New-Object System.Collections.Generic.List[System.Net.Http.HttpRequestMessage]

  for ($i = 1; $i -le $Concurrency; $i++) {
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $url)
    $null = $req.Headers.TryAddWithoutValidation('Accept', 'application/json')
    $null = $req.Headers.TryAddWithoutValidation('User-Agent', "verify-token-concurrency/consume-$i")
    $null = $req.Headers.TryAddWithoutValidation('X-Captcha-Token', $token)
    $null = $req.Headers.TryAddWithoutValidation('Origin', $RequestOrigin)
    $null = $req.Headers.TryAddWithoutValidation('Referer', $RequestReferer)
    if (-not [string]::IsNullOrWhiteSpace($clientIp)) {
      $null = $req.Headers.TryAddWithoutValidation('X-Forwarded-For', $clientIp)
    }
    $req.Content = [System.Net.Http.StringContent]::new("{}", [System.Text.Encoding]::UTF8, "application/json")

    $requests.Add($req)
    $tasks.Add($client.SendAsync($req))
  }

  $results = New-Object System.Collections.Generic.List[object]
  for ($i = 0; $i -lt $tasks.Count; $i++) {
    $task = $tasks[$i]
    $idx = $i + 1
    try {
      $resp = $task.GetAwaiter().GetResult()
      $statusCode = To-Int $resp.StatusCode 0
      $raw = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()

      $json = $null
      if (-not [string]::IsNullOrWhiteSpace($raw)) {
        try {
          $json = $raw | ConvertFrom-Json
        } catch {
          $json = $null
        }
      }

      $code = To-Int (Get-PropValue -obj $json -propName 'code' -defaultValue $statusCode) $statusCode
      $message = [string](Get-PropValue -obj $json -propName 'message' -defaultValue "")
      if ([string]::IsNullOrWhiteSpace($message)) {
        $message = $raw
      }

      $results.Add([pscustomobject]@{
        index = $idx
        statusCode = $statusCode
        code = $code
        message = $message
        raw = $raw
        success = ($code -eq 200)
      })

      $resp.Dispose()
    } catch {
      $results.Add([pscustomobject]@{
        index = $idx
        statusCode = 0
        code = 0
        message = [string]$_.Exception.Message
        raw = ""
        success = $false
      })
    }
  }

  foreach ($req in $requests) {
    $req.Dispose()
  }
  $client.Dispose()
  $handler.Dispose()

  return $results.ToArray()
}

if ($AcquireSliderMin -ge $AcquireSliderMax) {
  throw "AcquireSliderMin 必须小于 AcquireSliderMax"
}

$tokenSource = "manual"
$consumeClientIp = $TokenClientIp
$acquireResult = $null

if ([string]::IsNullOrWhiteSpace($VerifyToken)) {
  $tokenSource = "auto-acquire"
  Write-Host "未提供 VerifyToken，开始自动获取（最多尝试 $AcquireMaxAttempts 次）..."
  $acquireResult = Try-AcquireVerifyToken
  if (-not $acquireResult.acquired) {
    $durationMs = [Math]::Round((([DateTimeOffset]::Now - $script:StartedAt).TotalMilliseconds), 2)
    $summaryFail = [ordered]@{
      timestamp = [DateTimeOffset]::Now.ToString('o')
      baseUrl = $BaseUrl
      consumePath = $ConsumePath
      tokenSource = $tokenSource
      acquire = $acquireResult.metrics
      durationMs = $durationMs
      concurrency = $Concurrency
      passed = $false
      failReasons = @("未能在限定次数内获取 VerifyToken")
    }

    $summaryDir = Split-Path -Parent $SummaryJsonPath
    if (-not [string]::IsNullOrWhiteSpace($summaryDir) -and -not (Test-Path $summaryDir)) {
      New-Item -ItemType Directory -Path $summaryDir -Force | Out-Null
    }
    $summaryFail | ConvertTo-Json -Depth 12 | Set-Content -Path $SummaryJsonPath -Encoding UTF8
    Write-Host "Summary JSON written: $SummaryJsonPath"
    Write-Host "[FAIL] 无法自动获取 VerifyToken"
    exit 1
  }

  $VerifyToken = [string]$acquireResult.verifyToken
  $consumeClientIp = [string]$acquireResult.clientIp
  Write-Host "自动获取 VerifyToken 成功，使用 clientIp=$consumeClientIp"
} else {
  Write-Host "使用外部传入 VerifyToken，跳过自动获取"
}

Write-Host "开始并发消费测试: concurrency=$Concurrency, path=$ConsumePath"
$consumeResults = Invoke-ConcurrentConsume -token $VerifyToken -clientIp $consumeClientIp

$successCount = @($consumeResults | Where-Object { $_.success }).Count
$invalidTokenCount = @($consumeResults | Where-Object { $_.message -like '*验证令牌无效或已过期*' }).Count
$rateLimitCount = @($consumeResults | Where-Object {
    $_.statusCode -eq 429 -or $_.code -eq 429 -or $_.message -like '*请求过于频繁*'
  }).Count
$transportErrorCount = @($consumeResults | Where-Object { $_.statusCode -eq 0 -and $_.code -eq 0 }).Count

$durationMs = [Math]::Round((([DateTimeOffset]::Now - $script:StartedAt).TotalMilliseconds), 2)

$failReasons = New-Object System.Collections.Generic.List[string]
if ($successCount -ne 1) {
  $failReasons.Add("并发消费成功次数异常，期望=1，实际=$successCount")
}
if ($transportErrorCount -gt 0) {
  $failReasons.Add("存在网络/传输错误请求: $transportErrorCount")
}
if ($FailOnRateLimit -and $rateLimitCount -gt 0) {
  $failReasons.Add("存在限流响应: $rateLimitCount")
}

$summary = [ordered]@{
  timestamp = [DateTimeOffset]::Now.ToString('o')
  baseUrl = $BaseUrl
  consumePath = $ConsumePath
  tokenSource = $tokenSource
  clientIp = $consumeClientIp
  concurrency = $Concurrency
  requestTimeoutSec = $RequestTimeoutSec
  requestOrigin = $RequestOrigin
  requestReferer = $RequestReferer
  durationMs = $durationMs
  acquire = if ($null -eq $acquireResult) { $null } else { $acquireResult.metrics }
  expectedSuccessCount = 1
  actualSuccessCount = $successCount
  invalidTokenCount = $invalidTokenCount
  rateLimitCount = $rateLimitCount
  transportErrorCount = $transportErrorCount
  results = @($consumeResults)
  passed = ($failReasons.Count -eq 0)
  failReasons = @($failReasons)
}

if (-not [string]::IsNullOrWhiteSpace($SummaryJsonPath)) {
  $summaryDir = Split-Path -Parent $SummaryJsonPath
  if (-not [string]::IsNullOrWhiteSpace($summaryDir) -and -not (Test-Path $summaryDir)) {
    New-Item -ItemType Directory -Path $summaryDir -Force | Out-Null
  }
  $summary | ConvertTo-Json -Depth 12 | Set-Content -Path $SummaryJsonPath -Encoding UTF8
  Write-Host "Summary JSON written: $SummaryJsonPath"
}

if (-not [string]::IsNullOrWhiteSpace($ReportTextPath)) {
  $reportDir = Split-Path -Parent $ReportTextPath
  if (-not [string]::IsNullOrWhiteSpace($reportDir) -and -not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
  }

  $reportLines = @(
    "VerifyToken Concurrency Regression Report",
    "runAt=$([DateTimeOffset]::Now.ToString('o'))",
    "baseUrl=$BaseUrl",
    "consumePath=$ConsumePath",
    "tokenSource=$tokenSource",
    "clientIp=$consumeClientIp",
    "concurrency=$Concurrency",
    "requestOrigin=$RequestOrigin",
    "requestReferer=$RequestReferer",
    "durationMs=$durationMs",
    "expectedSuccessCount=1",
    "actualSuccessCount=$successCount",
    "invalidTokenCount=$invalidTokenCount",
    "rateLimitCount=$rateLimitCount",
    "transportErrorCount=$transportErrorCount",
    "passed=$($failReasons.Count -eq 0)",
    "",
    "failReasons:",
    ($(if ($failReasons.Count -eq 0) { "- none" } else { $failReasons | ForEach-Object { "- $_" } })),
    "",
    "results:",
    ($consumeResults | ForEach-Object { "- #$($_.index): http=$($_.statusCode), code=$($_.code), message=$($_.message)" })
  )

  Set-Content -Path $ReportTextPath -Value $reportLines -Encoding UTF8
  Write-Host "Report text written: $ReportTextPath"
}

if ($failReasons.Count -gt 0) {
  Write-Host "[FAIL] VerifyToken 并发消费回归失败"
  foreach ($reason in $failReasons) {
    Write-Host " - $reason"
  }
  exit 1
}

Write-Host "[PASS] VerifyToken 并发消费回归通过"
