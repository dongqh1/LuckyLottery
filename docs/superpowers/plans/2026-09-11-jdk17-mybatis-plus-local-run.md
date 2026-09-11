# JDK 17 MyBatis-Plus Local Run Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the five Java 11 hmall services run from IDEA on JDK 17 without MyBatis-Plus `SerializedLambdaMeta` failures and restore the `Jack / 123` login path.

**Architecture:** Keep JDK 17 and MyBatis-Plus 3.4.3 unchanged. Add one JVM module-opening option to the existing IDEA Spring Boot run configurations for the five database-backed hmall services, restart the affected local process, and verify login independently at the service, gateway, and local Nginx boundaries.

**Tech Stack:** IntelliJ IDEA Spring Boot run configurations, JDK 17, MyBatis-Plus 3.4.3, PowerShell 7, local Nginx.

## Global Constraints

- Do not install JDK 11.
- Do not upgrade MyBatis-Plus or other Maven dependencies.
- Do not change `marketing-service`, Nginx configuration, or static frontend files.
- Add exactly `--add-opens=java.base/java.lang.invoke=ALL-UNNAMED` to the five database-backed hmall service run configurations.
- Do not add the option to `HmGatewayApplication`.
- Do not expose the returned JWT in logs or the final report.

---

### Task 1: Persist the JDK 17 compatibility option in IDEA run configurations

**Files:**
- Modify: `.idea/workspace.xml`
- Test: one-off PowerShell XML assertions against `.idea/workspace.xml`

**Interfaces:**
- Consumes: the existing Spring Boot configurations stored under the `RunManager` component.
- Produces: five configurations with `VM_PARAMETERS=--add-opens=java.base/java.lang.invoke=ALL-UNNAMED` and an unchanged gateway configuration.

- [ ] **Step 1: Run the pre-change assertion and confirm it fails**

Run:

```powershell
$xml = [xml](Get-Content -Raw -Encoding UTF8 -LiteralPath '.idea/workspace.xml')
$runManager = $xml.project.component | Where-Object { $_.name -eq 'RunManager' }
$serviceNames = @('ItemServiceApplication','CartServiceApplication','UserServiceApplication','TradeServiceApplication','PayServiceApplication')
$expected = '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED'
$missing = @(foreach ($serviceName in $serviceNames) {
  $configuration = $runManager.configuration | Where-Object { $_.name -eq $serviceName }
  if (-not ($configuration.option | Where-Object { $_.name -eq 'VM_PARAMETERS' -and $_.value -eq $expected })) {
    $serviceName
  }
})
if ($missing.Count -eq 0) { throw 'Expected the compatibility configuration to be absent before implementation' }
```

Expected: the command completes while confirming at least one target configuration lacks the option.

- [ ] **Step 2: Add the exact VM option to the five service configurations**

For each target `<configuration>` element, add this entry immediately before its `<module>` element:

```xml
<option name="VM_PARAMETERS" value="--add-opens=java.base/java.lang.invoke=ALL-UNNAMED" />
```

Targets:

```text
ItemServiceApplication
CartServiceApplication
UserServiceApplication
TradeServiceApplication
PayServiceApplication
```

Leave `HmGatewayApplication` unchanged.

- [ ] **Step 3: Validate the edited XML and exact configuration coverage**

Run:

```powershell
$xml = [xml](Get-Content -Raw -Encoding UTF8 -LiteralPath '.idea/workspace.xml')
$runManager = $xml.project.component | Where-Object { $_.name -eq 'RunManager' }
$serviceNames = @('ItemServiceApplication','CartServiceApplication','UserServiceApplication','TradeServiceApplication','PayServiceApplication')
$expected = '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED'
foreach ($serviceName in $serviceNames) {
  $configuration = $runManager.configuration | Where-Object { $_.name -eq $serviceName }
  if (-not $configuration) { throw "Missing run configuration: $serviceName" }
  $options = @($configuration.option | Where-Object { $_.name -eq 'VM_PARAMETERS' -and $_.value -eq $expected })
  if ($options.Count -ne 1) { throw "$serviceName must contain exactly one compatibility option" }
}
$gateway = $runManager.configuration | Where-Object { $_.name -eq 'HmGatewayApplication' }
if ($gateway.option | Where-Object { $_.name -eq 'VM_PARAMETERS' }) { throw 'Gateway must not receive the compatibility option' }
'IDEA run configuration compatibility options verified'
```

Expected: `IDEA run configuration compatibility options verified`.

---

### Task 2: Restart user-service with the new option

**Files:**
- No repository files modified.
- Verify: Windows process command line and `logs/user-service/spring.log`.

**Interfaces:**
- Consumes: the updated `UserServiceApplication` run configuration.
- Produces: a fresh process listening on `8083` whose JVM command line contains the compatibility option.

- [ ] **Step 1: Stop and restart `UserServiceApplication` from IDEA**

Use IDEA's Run tool window to terminate the current `UserServiceApplication`, then start the same configuration again. Do not restart Gateway or local Nginx.

- [ ] **Step 2: Confirm a fresh process is listening with the expected VM option**

Run:

```powershell
$listener = Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq 8083 } | Select-Object -First 1
if (-not $listener) { throw 'user-service is not listening on port 8083' }
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
if ($process.CommandLine -notmatch '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED') {
  throw 'user-service did not start with the compatibility option'
}
"user-service PID $($process.ProcessId) uses the compatibility option"
```

Expected: a new user-service PID and confirmation that the option is present.

---

### Task 3: Verify the complete local login path

**Files:**
- No repository files modified.
- Verify: HTTP endpoints on ports `8083`, `8080`, and `18080`.

**Interfaces:**
- Consumes: `UserServiceApplication` on `8083`, `HmGatewayApplication` on `8080`, and local `hmall-nginx` on `18080`.
- Produces: three successful login responses for the existing `Jack / 123` account.

- [ ] **Step 1: Verify direct user-service login**

Run:

```powershell
$payload = @{ username = 'Jack'; password = '123' } | ConvertTo-Json -Compress
$response = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/users/login' -Method Post -ContentType 'application/json' -Body $payload -TimeoutSec 10
if (-not $response.token -or $response.username -ne 'Jack') { throw 'Direct user-service login response is invalid' }
'Direct user-service login passed'
```

Expected: `Direct user-service login passed`.

- [ ] **Step 2: Verify Gateway login**

Run:

```powershell
$payload = @{ username = 'Jack'; password = '123' } | ConvertTo-Json -Compress
$response = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/users/login' -Method Post -ContentType 'application/json' -Body $payload -TimeoutSec 10
if (-not $response.token -or $response.username -ne 'Jack') { throw 'Gateway login response is invalid' }
'Gateway login passed'
```

Expected: `Gateway login passed`.

- [ ] **Step 3: Verify local Nginx login**

Run:

```powershell
$payload = @{ username = 'Jack'; password = '123' } | ConvertTo-Json -Compress
$response = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/users/login' -Method Post -ContentType 'application/json' -Body $payload -TimeoutSec 10
if (-not $response.token -or $response.username -ne 'Jack') { throw 'Local Nginx login response is invalid' }
'Local Nginx login passed'
```

Expected: `Local Nginx login passed`.

- [ ] **Step 4: Confirm the original exception no longer recurs**

Record the current end of `logs/user-service/spring.log`, make one final login request, and inspect only newly appended log lines for:

```text
SerializedLambdaMeta
InaccessibleObjectException
NoClassDefFoundError
```

Expected: none of the three patterns occur in the new log segment.

- [ ] **Step 5: Report the local development entry points**

Report:

```text
Frontend: http://localhost:18080
Gateway:  http://localhost:8080
Login:    Jack / 123
```

Do not print the JWT.
