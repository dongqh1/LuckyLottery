[CmdletBinding()]
param(
    [string]$PortalBaseUrl = "http://127.0.0.1:18080",
    [string]$AdminBaseUrl = "http://127.0.0.1:18081",
    [string]$Username = "Jack",
    [string]$Password = "123",
    [ValidateRange(1, 60)]
    [int]$MaxAttempts = 10,
    [ValidateRange(0, 30)]
    [int]$RetryDelaySeconds = 2
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$portal = $PortalBaseUrl.TrimEnd("/")
$admin = $AdminBaseUrl.TrimEnd("/")

function Invoke-SmokeRequest {
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$Uri,
        [ValidateSet("Get", "Post")]
        [string]$Method = "Get",
        [hashtable]$Headers,
        [hashtable]$Body
    )

    $request = @{
        Uri = $Uri
        Method = $Method
        ErrorAction = "Stop"
    }
    if ($null -ne $Headers) {
        $request.Headers = $Headers
    }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Compress
    }

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $response = Invoke-RestMethod @request
            Write-Host "PASS $Name"
            return $response
        } catch {
            if ($attempt -eq $MaxAttempts) {
                throw
            }
            Write-Host "WAIT $Name (attempt $attempt/$MaxAttempts)"
            Start-Sleep -Seconds $RetryDelaySeconds
        }
    }
}

$null = Invoke-SmokeRequest -Name "Portal static page" -Uri "${portal}/"
$null = Invoke-SmokeRequest -Name "Admin static page" -Uri "${admin}/items.html"

$login = Invoke-SmokeRequest `
    -Name "User login" `
    -Uri "${portal}/api/users/login" `
    -Method Post `
    -Body @{ username = $Username; password = $Password }

if ([string]::IsNullOrWhiteSpace($login.token)) {
    throw "User login returned no authorization token"
}
$headers = @{ authorization = $login.token }

$search = Invoke-SmokeRequest -Name "Item search" -Uri "${portal}/api/search/list?pageNo=1&pageSize=1"
$items = @($search.list)
if ($items.Count -eq 0 -or $null -eq $items[0].id) {
    throw "Item search returned no item id"
}
$itemId = $items[0].id

$null = Invoke-SmokeRequest -Name "Item detail" -Uri "${portal}/api/items/${itemId}"
$null = Invoke-SmokeRequest -Name "Cart query" -Uri "${portal}/api/carts" -Headers $headers
$null = Invoke-SmokeRequest -Name "Address query" -Uri "${portal}/api/addresses" -Headers $headers
$null = Invoke-SmokeRequest -Name "Order query" -Uri "${portal}/api/orders/me" -Headers $headers
$null = Invoke-SmokeRequest `
    -Name "Marketing membership query" `
    -Uri "${portal}/api/marketing/memberships/me" `
    -Headers $headers
