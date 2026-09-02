param(
    [string] $ConfirmLocalDemoData
)

$ErrorActionPreference = 'Stop'

if ($ConfirmLocalDemoData -cne 'YES') {
    throw '必须显式传入 -ConfirmLocalDemoData YES，才允许初始化本地演示数据。'
}

$blockedOverrides = [System.Collections.Generic.List[string]]::new()
Get-ChildItem Env: | ForEach-Object {
    $normalizedName = $_.Name.ToUpperInvariant()
    $isBlocked = $normalizedName.StartsWith('SPRING_DATASOURCE_') -or
        $normalizedName.StartsWith('SPRING_FLYWAY_') -or
        $normalizedName.StartsWith('SPRING_CONFIG_') -or
        $normalizedName -in @(
            'SPRING_APPLICATION_JSON', 'SPRING_PROFILES_ACTIVE',
            'MAVEN_ARGS', 'MAVEN_OPTS', 'MAVEN_PROJECTBASEDIR',
            'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'
        )
    if ($isBlocked -and -not [string]::IsNullOrWhiteSpace($_.Value)) {
        $blockedOverrides.Add($_.Name)
    }
}
if ($blockedOverrides.Count -gt 0) {
    throw "检测到可能覆盖数据源的环境变量，拒绝启动 Maven：$($blockedOverrides -join ', ')"
}

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$mavenConfigurationDirectory = Join-Path $repositoryRoot '.mvn'
foreach ($configurationName in @('maven.config', 'jvm.config')) {
    $configurationPath = Join-Path $mavenConfigurationDirectory $configurationName
    if ((Test-Path -LiteralPath $configurationPath -PathType Leaf) -and
        -not [string]::IsNullOrWhiteSpace((Get-Content -Raw -LiteralPath $configurationPath))) {
        throw "检测到非空 Maven 项目配置 .mvn/$configurationName，拒绝启动 Maven。"
    }
}

$envFile = Join-Path $repositoryRoot '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw '缺少仓库根目录 .env，无法确认本地数据库目标。'
}

$settings = @{}
Get-Content -Encoding UTF8 -LiteralPath $envFile |
    Where-Object { $_ -match '^[A-Z0-9_]+=' } |
    ForEach-Object {
        $key, $value = $_ -split '=', 2
        $settings[$key] = $value.Trim()
    }

# 当前进程环境变量优先，便于安全测试验证远程主机一定会被拒绝。
$mysqlHost = if (Test-Path Env:MYSQL_HOST) { $env:MYSQL_HOST } else { $settings['MYSQL_HOST'] }
$mysqlDatabase = if (Test-Path Env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { $settings['MYSQL_DATABASE'] }
$mysqlPort = if (Test-Path Env:MYSQL_PORT) { $env:MYSQL_PORT } else { $settings['MYSQL_PORT'] }

$allowedLocalHosts = @('localhost', '127.0.0.1', '::1')
if ([string]::IsNullOrWhiteSpace($mysqlHost) -or $allowedLocalHosts -notcontains $mysqlHost.Trim().ToLowerInvariant()) {
    throw "检测到非本机 MySQL 主机 '$mysqlHost'，拒绝写入演示数据。"
}
if ([string]::IsNullOrWhiteSpace($mysqlDatabase) -or
    $mysqlDatabase -notmatch '^luckyhub(?:_[a-zA-Z0-9_]+)?$') {
    throw "数据库名称 '$mysqlDatabase' 不在允许的 LuckyHub 本地命名范围内，拒绝写入。"
}
if ($mysqlPort -notmatch '^[0-9]{1,5}$' -or [int]$mysqlPort -lt 1 -or [int]$mysqlPort -gt 65535) {
    throw "MySQL 端口 '$mysqlPort' 不合法，拒绝写入。"
}

$jdbcHost = if ($mysqlHost.Trim() -eq '::1') { '[::1]' } else { $mysqlHost.Trim().ToLowerInvariant() }
$verifiedJdbcUrl = "jdbc:mysql://${jdbcHost}:$mysqlPort/$mysqlDatabase"
$env:SPRING_DATASOURCE_URL = $verifiedJdbcUrl
$env:SPRING_FLYWAY_ENABLED = 'false'

$mavenWrapper = Join-Path $repositoryRoot 'scripts\Invoke-Maven.ps1'
Write-Host "已确认本地数据库：$mysqlHost / $mysqlDatabase"
Write-Host '正在执行幂等演示数据初始化与安全快照测试……'

Push-Location -LiteralPath $repositoryRoot
try {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $mavenWrapper `
        '-Dfrontend.demo.seed=true' `
        "-Dfrontend.demo.expected-database=$mysqlDatabase" `
        '-Dspring.flyway.enabled=false' `
        '-Dtest=FrontendDemoDataSeederTests,FrontendDemoDataSafetyTests' `
        test

    if ($LASTEXITCODE -ne 0) {
        throw "演示数据初始化或安全验证失败，Maven 退出码：$LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host '演示数据初始化完成；重复执行不会增加重复的 DEMO- 业务数据。'
