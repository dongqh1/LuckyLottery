param([int]$Start,[int]$Size,[switch]$CountOnly,[switch]$Build)
$manifestPath = Join-Path (Get-Location) '.upload-full-manifest.json'
if ($Build -or -not (Test-Path -LiteralPath $manifestPath)) {
  $exclude = @('.upload-chunk.ps1','.upload-focused.ps1','.upload-full.ps1','.upload-full-manifest.json')
  $basePaths = @(git ls-tree -r --name-only HEAD^ 2>$null)
  $currentPaths = @(git ls-tree -r --name-only HEAD 2>$null)
  $currentPaths += @(git ls-files --others --exclude-standard 2>$null)
  $currentPaths = @($currentPaths | Where-Object { $_ -and ($exclude -notcontains $_) -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Sort-Object -Unique)
  $baseSet = [Collections.Generic.HashSet[string]]::new()
  foreach ($p in $basePaths) { [void]$baseSet.Add($p) }
  $records = [System.Collections.Generic.List[object]]::new()
  foreach ($p in $currentPaths) {
    $bytes = [IO.File]::ReadAllBytes((Join-Path (Get-Location) $p))
    $binary = $false
    foreach ($b in $bytes) { if (($b -lt 32) -and ($b -ne 9) -and ($b -ne 10) -and ($b -ne 13)) { $binary = $true; break } }
    if ($binary) { $records.Add([pscustomobject]@{ path = $p; delete = $false; binary = $true; content = [Convert]::ToBase64String($bytes); mode = '100644' }) }
    else { $records.Add([pscustomobject]@{ path = $p; delete = $false; binary = $false; content = [Text.Encoding]::UTF8.GetString($bytes); mode = '100644' }) }
    [void]$baseSet.Remove($p)
  }
  foreach ($p in ($baseSet | Sort-Object)) { if ($exclude -notcontains $p) { $records.Add([pscustomobject]@{ path = $p; delete = $true; mode = '100644' }) } }
  $records | ConvertTo-Json -Compress -Depth 3 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
}
$records = @(Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestPath | ConvertFrom-Json)
if ($CountOnly) { Write-Output ('COUNT=' + $records.Count); exit }
$records | Select-Object -Skip $Start -First $Size | ConvertTo-Json -Compress -Depth 3
