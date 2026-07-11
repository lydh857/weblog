# 用途：统一执行 weblog-api 全量测试（本地与 CI 共用）
# 输入参数：无
# 输出：控制台测试日志；命令失败时返回非 0 退出码

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Write-Host "[run-weblog-api-tests] start"
mvn -f "weblog-backend/pom.xml" -pl weblog-api -am test
if ($LASTEXITCODE -ne 0) {
  Write-Error "[run-weblog-api-tests] failed with exit code $LASTEXITCODE"
  exit $LASTEXITCODE
}

Write-Host "[run-weblog-api-tests] success"
