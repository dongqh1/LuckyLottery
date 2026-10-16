param([int]$Start,[int]$Size)
$records = [System.Collections.Generic.List[object]]::new()
$lines = @(git -c core.quotepath=false diff --name-status --no-renames HEAD 2>$null)
foreach ($line in $lines) {
  if ([string]::IsNullOrWhiteSpace($line)) { continue }
  $parts = $line -split "`t", 2
  if ($parts.Count -lt 2) { continue }
  $status = $parts[0]
  $path = $parts[1]
  if ($status.StartsWith('D')) {
    $records.Add([pscustomobject]@{ path = $path; delete = $true; mode = '100644' })
  } else {
    $bytes = [IO.File]::ReadAllBytes((Join-Path (Get-Location) $path))
    $records.Add([pscustomobject]@{ path = $path; delete = $false; content = [Text.Encoding]::UTF8.GetString($bytes); mode = '100644' })
  }
}
$existing = @($records | ForEach-Object { $_.path })
foreach ($path in @(git ls-files --others --exclude-standard 2>$null)) {
  if ([string]::IsNullOrWhiteSpace($path) -or $existing -contains $path) { continue }
  $bytes = [IO.File]::ReadAllBytes((Join-Path (Get-Location) $path))
  $records.Add([pscustomobject]@{ path = $path; delete = $false; content = [Text.Encoding]::UTF8.GetString($bytes); mode = '100644' })
}
$records | Select-Object -Skip $Start -First $Size | ConvertTo-Json -Compress -Depth 3
