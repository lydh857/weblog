<#
Usage:

1) strict mode (recommended)
powershell -ExecutionPolicy Bypass -File "docs/p0-api-regression.ps1" -BaseUrl "http://127.0.0.1:9091"

2) legacy replies paging compatibility
powershell -ExecutionPolicy Bypass -File "docs/p0-api-regression.ps1" -BaseUrl "http://127.0.0.1:9091" -AllowLegacyRepliesPaging

3) authenticated checks (my/comment batch/favorite batch)
powershell -ExecutionPolicy Bypass -File "docs/p0-api-regression.ps1" -BaseUrl "http://127.0.0.1:9091" -AuthToken "<Satoken>"

4) authenticated checks with forbidden comment assertion
powershell -ExecutionPolicy Bypass -File "docs/p0-api-regression.ps1" -BaseUrl "http://127.0.0.1:9091" -AuthToken "<Satoken>" -ForbiddenCommentId 123

5) write machine-readable summary json
powershell -ExecutionPolicy Bypass -File "docs/p0-api-regression.ps1" -BaseUrl "http://127.0.0.1:9091" -SummaryJsonPath "docs/p0-api-regression-result.json"

6) malformed comment like cache tolerance check
powershell -ExecutionPolicy Bypass -File "docs/p0-api-regression.ps1" -BaseUrl "http://127.0.0.1:9091" -CheckMalformedCommentLike -MalformedCheckPostId 1 -MalformedCheckCommentId 1 -MalformedExpectedLikeCount 12
#>

param(
  [string]$BaseUrl = "http://localhost:9091",
  [string]$AuthToken = "",
  [switch]$AllowLegacyRepliesPaging,
  [long]$ForbiddenCommentId = 0,
  [string]$SummaryJsonPath = "",
  [switch]$CheckMalformedCommentLike,
  [long]$MalformedCheckPostId = 1,
  [long]$MalformedCheckCommentId = 1,
  [long]$MalformedExpectedLikeCount = 12
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:TotalChecks = 0
$script:PassedChecks = 0
$script:FailedChecks = 0
$script:WarnChecks = 0
$script:FailureMessages = New-Object System.Collections.Generic.List[string]

function Join-Url([string]$base, [string]$pathAndQuery) {
  if ($base.EndsWith('/')) {
    return "$($base.TrimEnd('/'))$pathAndQuery"
  }
  return "$base$pathAndQuery"
}

function Invoke-Api([string]$method, [string]$url, [hashtable]$headers = @{}, [object]$body = $null) {
  $payload = $null
  if ($null -ne $body) {
    $payload = $body | ConvertTo-Json -Depth 10
  }

  try {
    $resp = Invoke-WebRequest -Method $method -Uri $url -Headers $headers -Body $payload -ContentType "application/json" -UseBasicParsing
    $json = $resp.Content | ConvertFrom-Json
    return [pscustomobject]@{
      StatusCode = [int]$resp.StatusCode
      Json = $json
      Raw = $resp.Content
    }
  } catch {
    $webResp = $_.Exception.Response
    if ($null -eq $webResp) {
      throw
    }

    $statusCode = 0
    try {
      $statusCode = [int]$webResp.StatusCode
    } catch {}

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
    try {
      $json = $raw | ConvertFrom-Json
    } catch {
      $json = [pscustomobject]@{
        code = $statusCode
        message = $raw
      }
    }

    return [pscustomobject]@{
      StatusCode = $statusCode
      Json = $json
      Raw = $raw
    }
  }
}

function Assert-ResultCode([int]$actual, [int]$expected, [string]$context) {
  if ($actual -ne $expected) {
    throw "${context}: expected code=$expected, actual code=$actual"
  }
}

function Assert-Contains([string]$actual, [string]$expectedSubstring, [string]$context) {
  if ([string]::IsNullOrEmpty($actual) -or -not $actual.Contains($expectedSubstring)) {
    throw "${context}: expected message contains '$expectedSubstring', actual '$actual'"
  }
}

function Get-ResponseCode([object]$response) {
  if ($null -ne $response -and $null -ne $response.Json) {
    $codeProp = $response.Json.PSObject.Properties['code']
    if ($null -ne $codeProp -and $null -ne $codeProp.Value) {
      try {
        return [int]$codeProp.Value
      } catch {
      }
    }
  }
  return [int]$response.StatusCode
}

function Get-ResponseMessage([object]$response) {
  if ($null -ne $response -and $null -ne $response.Json) {
    $messageProp = $response.Json.PSObject.Properties['message']
    if ($null -ne $messageProp -and $null -ne $messageProp.Value) {
      return [string]$messageProp.Value
    }

    $errorProp = $response.Json.PSObject.Properties['error']
    if ($null -ne $errorProp -and $null -ne $errorProp.Value) {
      return [string]$errorProp.Value
    }
  }

  if (-not [string]::IsNullOrWhiteSpace($response.Raw)) {
    return [string]$response.Raw
  }
  return ''
}

function Extract-QueryParam([string]$url, [string]$name) {
  $uri = [System.Uri]$url
  $raw = $uri.Query.TrimStart('?')
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return $null
  }

  foreach ($pair in $raw.Split('&', [System.StringSplitOptions]::RemoveEmptyEntries)) {
    $kv = $pair.Split('=', 2)
    if ($kv.Length -ne 2) {
      continue
    }
    if ($kv[0] -eq $name) {
      return [System.Uri]::UnescapeDataString($kv[1])
    }
  }
  return $null
}

function Get-ListCount([object]$value) {
  if ($null -eq $value) {
    return 0
  }
  if ($value -is [System.Array]) {
    return $value.Count
  }
  if ($value -is [System.Collections.IEnumerable] -and -not ($value -is [string])) {
    return @($value).Count
  }
  return 1
}

function Find-CommentLikeCount([object]$records, [long]$targetCommentId) {
  if ($null -eq $records) {
    return $null
  }

  foreach ($record in $records) {
    if ($null -ne $record.id -and [long]$record.id -eq $targetCommentId) {
      return [long]$record.likeCount
    }

    if ($null -ne $record.replies) {
      foreach ($reply in $record.replies) {
        if ($null -ne $reply.id -and [long]$reply.id -eq $targetCommentId) {
          return [long]$reply.likeCount
        }
      }
    }
  }

  return $null
}

function Run-Check([string]$name, [scriptblock]$check) {
  $script:TotalChecks++
  try {
    & $check
    $script:PassedChecks++
    Write-Host "[PASS] $name"
  } catch {
    $script:FailedChecks++
    $msg = "$name -> $($_.Exception.Message)"
    $script:FailureMessages.Add($msg)
    Write-Host "[FAIL] $msg"
  }
}

function Add-Warn([string]$name, [string]$message) {
  $script:WarnChecks++
  Write-Host "[WARN] $name -> $message"
}

Write-Host "== P0 API regression start =="
Write-Host "BaseUrl: $BaseUrl"

$authHeaders = @{}
if (-not [string]::IsNullOrWhiteSpace($AuthToken)) {
  $authHeaders = @{ Cookie = "Satoken=$AuthToken" }
  Write-Host "[INFO] authenticated checks enabled"
} else {
  Write-Host "[INFO] authenticated checks skipped (AuthToken is empty)"
}

Run-Check 'smoke check' {
  $healthUrl = Join-Url $BaseUrl '/api/search?keyword=test&pageNum=1&pageSize=1'
  $healthResp = Invoke-Api 'GET' $healthUrl
  Assert-ResultCode (Get-ResponseCode $healthResp) 200 'smoke check'
}

Run-Check 'search pageNum lower bound' {
  $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/search?keyword=test&pageNum=0&pageSize=10')
  Assert-ResultCode (Get-ResponseCode $resp) 400 'search pageNum lower bound'
}

Run-Check 'search pageSize lower bound' {
  $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/search?keyword=test&pageNum=1&pageSize=0')
  Assert-ResultCode (Get-ResponseCode $resp) 400 'search pageSize lower bound'
}

Run-Check 'search empty keyword returns empty result' {
  $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/search?keyword=%20%20%20&pageNum=1&pageSize=10')
  Assert-ResultCode (Get-ResponseCode $resp) 200 'search empty keyword code'
  $total = [long]$resp.Json.data.total
  if ($total -ne 0) {
    throw "search empty keyword total expected 0, actual $total"
  }
  $hitCount = Get-ListCount $resp.Json.data.hits
  if ($hitCount -ne 0) {
    throw "search empty keyword hits expected 0, actual $hitCount"
  }
}

Run-Check 'comment replies parentId lower bound' {
  $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/comment/replies/0?pageNum=1&pageSize=10')
  Assert-ResultCode (Get-ResponseCode $resp) 400 'comment replies parentId lower bound'
}

$repliesPageNumBad = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/comment/replies/1?pageNum=0&pageSize=10')
$repliesPageSizeBad = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/comment/replies/1?pageNum=1&pageSize=0')
$repliesPageNumBadCode = Get-ResponseCode $repliesPageNumBad
$repliesPageSizeBadCode = Get-ResponseCode $repliesPageSizeBad
if ($AllowLegacyRepliesPaging) {
  if ($repliesPageNumBadCode -eq 400) {
    Write-Host '[PASS] comment replies pageNum lower bound'
  } else {
    Add-Warn 'comment replies pageNum lower bound' "expected code=400, actual code=$repliesPageNumBadCode (legacy mode enabled)"
  }
  if ($repliesPageSizeBadCode -eq 400) {
    Write-Host '[PASS] comment replies pageSize lower bound'
  } else {
    Add-Warn 'comment replies pageSize lower bound' "expected code=400, actual code=$repliesPageSizeBadCode (legacy mode enabled)"
  }
} else {
  Run-Check 'comment replies pageNum lower bound' {
    Assert-ResultCode $repliesPageNumBadCode 400 'comment replies pageNum lower bound'
  }
  Run-Check 'comment replies pageSize lower bound' {
    Assert-ResultCode $repliesPageSizeBadCode 400 'comment replies pageSize lower bound'
  }
}

$missingPostId = 999999999
Run-Check 'comment post existence' {
  $resp = Invoke-Api 'GET' (Join-Url $BaseUrl "/api/portal/comment/post/${missingPostId}?pageNum=1&pageSize=10")
  Assert-ResultCode (Get-ResponseCode $resp) 404 'comment post existence'
}

Run-Check 'interaction post existence' {
  $resp = Invoke-Api 'GET' (Join-Url $BaseUrl "/api/portal/interaction/like/${missingPostId}")
  Assert-ResultCode (Get-ResponseCode $resp) 404 'interaction post existence'
}

if ($CheckMalformedCommentLike) {
  Run-Check 'malformed comment like cache fallback' {
    if ($MalformedCheckPostId -le 0) {
      throw 'MalformedCheckPostId must be greater than 0'
    }
    if ($MalformedCheckCommentId -le 0) {
      throw 'MalformedCheckCommentId must be greater than 0'
    }

    $listUrl = Join-Url $BaseUrl "/api/portal/comment/post/${MalformedCheckPostId}?pageNum=1&pageSize=100"
    $resp = Invoke-Api 'GET' $listUrl
    Assert-ResultCode (Get-ResponseCode $resp) 200 'malformed comment like cache fallback code'

    $records = $resp.Json.data.records
    $actualLikeCount = Find-CommentLikeCount $records $MalformedCheckCommentId
    if ($null -eq $actualLikeCount) {
      throw "target comment not found in comment list response: postId=$MalformedCheckPostId, commentId=$MalformedCheckCommentId"
    }

    if ([long]$actualLikeCount -ne $MalformedExpectedLikeCount) {
      throw "malformed comment like cache fallback expected likeCount=$MalformedExpectedLikeCount, actual=$actualLikeCount"
    }
  }
} else {
  Write-Host '[INFO] malformed comment like cache fallback check skipped (CheckMalformedCommentLike not enabled)'
}

if ($authHeaders.Count -gt 0) {
  $oversizedIds = 1..101

  Run-Check 'comment my pageNum lower bound' {
    $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/comment/my?pageNum=0&pageSize=10') $authHeaders
    Assert-ResultCode (Get-ResponseCode $resp) 400 'comment my pageNum lower bound'
  }
  Run-Check 'comment my pageSize lower bound' {
    $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/comment/my?pageNum=1&pageSize=0') $authHeaders
    Assert-ResultCode (Get-ResponseCode $resp) 400 'comment my pageSize lower bound'
  }
  Run-Check 'interaction likes pageNum lower bound' {
    $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/interaction/my/likes?pageNum=0&pageSize=10') $authHeaders
    Assert-ResultCode (Get-ResponseCode $resp) 400 'interaction likes pageNum lower bound'
  }
  Run-Check 'interaction likes pageSize lower bound' {
    $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/interaction/my/likes?pageNum=1&pageSize=0') $authHeaders
    Assert-ResultCode (Get-ResponseCode $resp) 400 'interaction likes pageSize lower bound'
  }
  Run-Check 'interaction favorites pageNum lower bound' {
    $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/interaction/my/favorites?pageNum=0&pageSize=10') $authHeaders
    Assert-ResultCode (Get-ResponseCode $resp) 400 'interaction favorites pageNum lower bound'
  }
  Run-Check 'interaction favorites pageSize lower bound' {
    $resp = Invoke-Api 'GET' (Join-Url $BaseUrl '/api/portal/interaction/my/favorites?pageNum=1&pageSize=0') $authHeaders
    Assert-ResultCode (Get-ResponseCode $resp) 400 'interaction favorites pageSize lower bound'
  }

  Run-Check 'comment batch delete invalid id' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/comment/batch') $authHeaders @(0)
    Assert-ResultCode (Get-ResponseCode $resp) 400 'comment batch delete invalid id'
  }

  Run-Check 'comment batch delete empty list' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/comment/batch') $authHeaders @()
    Assert-ResultCode (Get-ResponseCode $resp) 200 'comment batch delete empty list'
  }

  Run-Check 'comment batch delete not found' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/comment/batch') $authHeaders @(999999999)
    Assert-ResultCode (Get-ResponseCode $resp) 404 'comment batch delete not found'
  }

  Run-Check 'comment batch delete max count' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/comment/batch') $authHeaders $oversizedIds
    Assert-ResultCode (Get-ResponseCode $resp) 400 'comment batch delete max count'
  }

  if ($ForbiddenCommentId -gt 0) {
    Run-Check 'comment batch delete forbidden' {
      $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/comment/batch') $authHeaders @($ForbiddenCommentId)
      Assert-ResultCode (Get-ResponseCode $resp) 403 'comment batch delete forbidden'
    }
  } else {
    Write-Host '[INFO] comment batch delete forbidden check skipped (ForbiddenCommentId not provided)'
  }

  Run-Check 'interaction batch unfavorite invalid duplicate id' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/interaction/favorite/batch') $authHeaders @(0, 0)
    Assert-ResultCode (Get-ResponseCode $resp) 400 'interaction batch unfavorite invalid duplicate id'
  }

  Run-Check 'interaction batch unfavorite empty list' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/interaction/favorite/batch') $authHeaders @()
    Assert-ResultCode (Get-ResponseCode $resp) 200 'interaction batch unfavorite empty list'
  }

  Run-Check 'interaction batch unfavorite not found duplicate id' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/interaction/favorite/batch') $authHeaders @(999999999, 999999999)
    Assert-ResultCode (Get-ResponseCode $resp) 404 'interaction batch unfavorite not found duplicate id'
  }

  Run-Check 'interaction batch unfavorite max count' {
    $resp = Invoke-Api 'DELETE' (Join-Url $BaseUrl '/api/portal/interaction/favorite/batch') $authHeaders $oversizedIds
    Assert-ResultCode (Get-ResponseCode $resp) 400 'interaction batch unfavorite max count'
  }
}

$redirectUri = [System.Uri]::EscapeDataString('http://localhost:3000/oauth/callback')
Run-Check 'oauth authorize' {
  $authorizeResp = Invoke-Api 'GET' (Join-Url $BaseUrl "/api/portal/oauth/github/authorize?redirectUri=$redirectUri")
  $authorizeCode = Get-ResponseCode $authorizeResp
  Assert-ResultCode $authorizeCode 200 'oauth authorize'

  $authUrl = ''
  if ($authorizeResp.Json -is [string]) {
    $authUrl = [string]$authorizeResp.Json
  } elseif ($null -ne $authorizeResp.Json) {
    $dataProp = $authorizeResp.Json.PSObject.Properties['data']
    if ($null -ne $dataProp -and $null -ne $dataProp.Value) {
      $authUrl = [string]$dataProp.Value
    }
  }
  if ([string]::IsNullOrWhiteSpace($authUrl) -and -not [string]::IsNullOrWhiteSpace($authorizeResp.Raw)) {
    $rawText = [string]$authorizeResp.Raw
    if ($rawText.StartsWith('http')) {
      $authUrl = $rawText
    }
  }

  if ([string]::IsNullOrWhiteSpace($authUrl)) {
    throw 'oauth authorize did not return auth url'
  }
  $state = Extract-QueryParam $authUrl 'state'
  if ([string]::IsNullOrWhiteSpace($state)) {
    throw 'oauth authorize did not return state'
  }

  $stateCookie = "weblog_github_oauth_state=$state"
  $callbackUrl = Join-Url $BaseUrl "/api/portal/oauth/github/callback?code=fake-code&state=$state"
  $originHeaders = @{ Cookie = $stateCookie; Origin = 'http://localhost:3000'; Referer = 'http://localhost:3000/' }

  $callbackFirst = Invoke-Api 'POST' $callbackUrl $originHeaders
  $callbackFirstCode = Get-ResponseCode $callbackFirst
  $callbackFirstMessage = Get-ResponseMessage $callbackFirst
  Write-Host "[INFO] oauth callback first -> code=$callbackFirstCode, message=$callbackFirstMessage"

  $callbackSecond = Invoke-Api 'POST' $callbackUrl $originHeaders
  $callbackSecondCode = Get-ResponseCode $callbackSecond
  $callbackSecondMessage = Get-ResponseMessage $callbackSecond
  Assert-ResultCode $callbackSecondCode 400 'oauth state reused'
  Assert-Contains $callbackSecondMessage 'state' 'oauth state reused message'
}

Write-Host ''
Write-Host '== P0 API regression summary =='
Write-Host "Total checks: $script:TotalChecks"
Write-Host "Passed: $script:PassedChecks"
Write-Host "Failed: $script:FailedChecks"
Write-Host "Warnings: $script:WarnChecks"

if ($script:FailedChecks -gt 0) {
  Write-Host 'Failed list:'
  foreach ($item in $script:FailureMessages) {
    Write-Host "- $item"
  }

  if (-not [string]::IsNullOrWhiteSpace($SummaryJsonPath)) {
    $summaryObj = [pscustomobject]@{
      timestamp = [DateTimeOffset]::Now.ToString("o")
      baseUrl = $BaseUrl
      total = $script:TotalChecks
      passed = $script:PassedChecks
      failed = $script:FailedChecks
      warnings = $script:WarnChecks
      failedList = @($script:FailureMessages)
      authChecksEnabled = ($authHeaders.Count -gt 0)
      legacyRepliesPaging = [bool]$AllowLegacyRepliesPaging
    }
    $summaryObj | ConvertTo-Json -Depth 10 | Set-Content -Path $SummaryJsonPath -Encoding UTF8
    Write-Host "Summary JSON written: $SummaryJsonPath"
  }

  throw "P0 API regression failed with $script:FailedChecks failed checks"
}

if (-not [string]::IsNullOrWhiteSpace($SummaryJsonPath)) {
  $summaryObj = [pscustomobject]@{
    timestamp = [DateTimeOffset]::Now.ToString("o")
    baseUrl = $BaseUrl
    total = $script:TotalChecks
    passed = $script:PassedChecks
    failed = $script:FailedChecks
    warnings = $script:WarnChecks
    failedList = @()
    authChecksEnabled = ($authHeaders.Count -gt 0)
    legacyRepliesPaging = [bool]$AllowLegacyRepliesPaging
  }
  $summaryObj | ConvertTo-Json -Depth 10 | Set-Content -Path $SummaryJsonPath -Encoding UTF8
  Write-Host "Summary JSON written: $SummaryJsonPath"
}

Write-Host '== P0 API regression passed =='
