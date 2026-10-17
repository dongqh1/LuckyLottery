param([int]$Start,[int]$Size)
$regex = '(?i)(dailyLimit|reservationRetention|DrawQuota|QuotaReservation|DRAW_QUOTA|TEN_DRAW|RequirePermission|PermissionCodes|PermissionLookup|PermissionInterceptor|MarketingPermission|RolePermission|UserPermission|ROLE_ADMIN|ROLE_USER)'
$explicit = @(
'hm-security/src/main/java/com/dongqh/hmsecurity/context/MissingTrustedUserException.java',
'lottery-service/src/main/java/com/dongqh/lotteryservice/lock/DrawLockKeys.java',
'lottery-service/src/main/resources/db/migration/V2__remove_daily_limit.sql',
'marketing-service/src/main/java/com/dongqh/luckyhub/domain/lock/lottery/DrawLockKeys.java',
'marketing-service/src/main/java/com/dongqh/luckyhub/service/scope/DataScopeService.java',
'marketing-service/src/main/java/com/dongqh/luckyhub/service/scope/UserDataScope.java',
'marketing-service/src/main/java/com/dongqh/luckyhub/service/scope/impl/DataScopeServiceImpl.java',
'marketing-service/src/main/resources/db/migration/V29__remove_daily_limit_and_rbac.sql',
'mysql/migrations/V015__remove_user_role.sql',
'nginx/html/hmall-portal/lottery.html',
'nginx/html/hmall-admin/marketing.html',
'nginx/html/hmall-admin/users.html'
)
$records = [System.Collections.Generic.List[object]]::new()
$seen = [Collections.Generic.HashSet[string]]::new()
foreach ($path in @(git -c core.quotepath=false diff --name-only --no-renames HEAD^ 2>$null)) {
  if ([string]::IsNullOrWhiteSpace($path) -or $path -eq '.upload-chunk.ps1' -or $path -eq '.upload-focused.ps1') { continue }
  $patch = git -c core.quotepath=false diff HEAD^ -- $path 2>$null | Out-String
  $keep = ($explicit -contains $path) -or ($patch -match $regex)
  if ($path -match '(?i)drawchance' -and $patch -notmatch '(?i)(RequirePermission|Permission|dailyLimit|DrawQuota|QuotaReservation|DRAW_QUOTA|TEN_DRAW)') { $keep = $false }
  if (-not $keep) { continue }
  if (-not $seen.Add($path)) { continue }
  $isDelete = -not (Test-Path -LiteralPath $path -PathType Leaf)
  if ($isDelete) {
    $records.Add([pscustomobject]@{ path = $path; delete = $true; mode = '100644' })
  } else {
    $bytes = [IO.File]::ReadAllBytes((Join-Path (Get-Location) $path))
    $records.Add([pscustomobject]@{ path = $path; delete = $false; content = [Text.Encoding]::UTF8.GetString($bytes); mode = '100644' })
  }
}
foreach ($path in $explicit) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or $seen.Contains($path)) { continue }
  $bytes = [IO.File]::ReadAllBytes((Join-Path (Get-Location) $path))
  $records.Add([pscustomobject]@{ path = $path; delete = $false; content = [Text.Encoding]::UTF8.GetString($bytes); mode = '100644' })
}
$records | Select-Object -Skip $Start -First $Size | ConvertTo-Json -Compress -Depth 3
