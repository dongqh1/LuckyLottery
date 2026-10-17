param([int]$Start,[int]$Size)
$m=@(Get-Content -Raw -Encoding UTF8 .\.upload-files-manifest.json | ConvertFrom-Json)
$m | Select-Object -Skip $Start -First $Size | ConvertTo-Json -Compress -Depth 3
