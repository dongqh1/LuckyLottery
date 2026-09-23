# Marketing API and Gateway Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route Marketing APIs through `hm-gateway` without changing public URLs, while retaining the exact legacy login path until its RS256-compatible `user-service` replacement is ready.

**Architecture:** Keep `/api/marketing/**` as the browser-facing contract and preserve Nginx's rewrite to backend `/api/**`; forward Marketing APIs to `hm-gateway` except the exact `/api/marketing/auth/login` compatibility endpoint, which remains on `marketing-service` until `user-service` can issue the compatible RS256 login response. Gateway uses a low-priority Nacos fallback for all other un-migrated Marketing APIs. Add each real `hm-api` client alongside its provider/consumer migration rather than creating unused stubs.

**Tech Stack:** Java 17, Spring Boot 4.0.8, Spring Cloud Gateway 2025.1.0, Spring Cloud Alibaba/Nacos 2025.1.0.0, Docker Compose, Nginx 1.27, JUnit 5, Python `unittest`.

## Global Constraints

- The public URL `/api/marketing/**` remains unchanged and Nginx rewrites it to backend `/api/**`.
- Store the shared exact-login Nginx location in one mounted snippet included by both virtual hosts; do not duplicate the routing block.
- Nginx forwards Marketing APIs and existing HMall APIs to `hm-gateway`; only the exact legacy Marketing login path remains temporarily on `marketing-service`. Nacos service names remain the service-discovery identifiers.
- Gateway validates RS256 JWT, removes untrusted `user-info`, and forwards only a verified user ID; business services continue enforcing permissions and user data scope.
- For Gateway-routed Marketing paths, only the exact `POST /api/shipping/callbacks/logistics` is anonymous; keep the existing HMall login/public-read allow-list unchanged, and require service-level signature, timestamp-window, and replay checks for logistics.
- Do not route `/api/marketing/auth/login` through the RS256-only Gateway until `user-service` supplies a compatible response and RS256 token; do not add a temporary HS256 verifier to Gateway.
- Payment callback paths retain JWT by default; the current callback signature is local-simulation-only and is not authorization to create a JWT bypass or a public Nginx entry.
- Until a domain is migrated and verified, its sole writer remains `marketing-service` behind the compatibility route. New service routes must be more specific and ordered before the fallback.
- Do not change Marketing Redis quota keys/Lua scripts, Redisson locks, login-session keys, or RBAC permission-cache keys in this gateway phase. The lottery extraction migrates the quota/lock protocol and tests; the user-service merge carries session and permission-cache semantics.
- New `hm-api` contracts are added with the matching provider and consumer migration; do not add unused placeholder clients.

---

### Task 0: Make optional AMQP auto-configuration safe for Gateway

**Files:**
- Modify: `mall-backend/hm-common/src/main/java/com/hmall/common/config/MqConfig.java`
- Test: `hm-gateway/src/test/java/com/dongqh/hmgateway/HmGatewayApplicationTests.java`

**Interfaces:**
- Consumes: the optional AMQP dependency declared `provided` by `hm-common` and registered unconditionally through `AutoConfiguration.imports`.
- Produces: Gateway starts without acquiring an AMQP/Rabbit runtime dependency; services that include AMQP continue receiving the existing Jackson message converter.

- [x] **Step 1: Confirm the regression test fails before the fix**

Run: `mvn -pl :hm-gateway -am -Dtest=HmGatewayApplicationTests#contextLoads -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `contextLoads` fails with `NoClassDefFoundError` for `org.springframework.amqp.support.converter.MessageConverter`, because Gateway intentionally does not include AMQP while `hm-common` registers `MqConfig` unconditionally.

- [x] **Step 2: Guard the shared optional configuration**

Conditionally load `MqConfig` only when `org.springframework.amqp.support.converter.MessageConverter` is present. Use a class-name condition so class metadata can be evaluated without loading AMQP types. Preserve the existing converter bean implementation and keep AMQP dependencies optional/provided; do not add Rabbit dependencies to Gateway.

- [x] **Step 3: Verify the targeted regression and Gateway suite**

Run: `mvn -pl :hm-gateway -am -Dtest=HmGatewayApplicationTests#contextLoads -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `contextLoads` passes and the reactor succeeds.

- [x] **Step 4: Commit the prerequisite fix**

```powershell
git add mall-backend/hm-common/src/main/java/com/hmall/common/config/MqConfig.java
git commit -m "fix: guard optional amqp auto configuration"
```

---

### Task 1: Define failing routing and authentication contracts

**Files:**
- Modify: `hm-gateway/src/test/java/com/dongqh/hmgateway/filters/AuthGlobalFilterTest.java`
- Modify: `tests/test_infrastructure_contract.py`

**Interfaces:**
- Consumes: `AuthGlobalFilter`, `AuthProperties`, the current Compose topology, and active `nginx/nginx.conf`.
- Produces: regression tests proving the exact logistics exception, payment callback protection, legacy Gateway routing, the temporary login Nginx exception, and Compose startup ordering.

- [ ] **Step 1: Add filter tests for the exact logistics and payment callback rules**

Add the following methods to `AuthGlobalFilterTest`. Use the existing `properties`, `rejectingJwtTool`, and `capture` helpers. These pin the method/path matching and spoofed-header behavior; the static application-config assertions in Step 2 fail until the matching exact allow-list entries are added.

```java
    @Test
    void onlyExactPostLogisticsCallbackIsAnonymousAndSanitizesIdentity() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                properties("POST /api/shipping/callbacks/logistics"), rejectingJwtTool());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        MockServerWebExchange post = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/shipping/callbacks/logistics")
                        .header("user-info", "999"));
        filter.filter(post, capture(forwarded)).block();
        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("user-info")).isNull();

        forwarded.set(null);
        MockServerWebExchange get = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/shipping/callbacks/logistics"));
        filter.filter(get, capture(forwarded)).block();
        assertThat(forwarded.get()).isNull();
        assertThat(get.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void paymentCallbackIsNotAnonymousWithoutJwt() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                properties("POST /api/shipping/callbacks/logistics"), rejectingJwtTool());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/callbacks/payments"));

        filter.filter(exchange, capture(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
```

- [ ] **Step 2: Update the existing Python infrastructure assertions to the target behavior**

In `NginxRoutingContractTest`, make the two `/api/marketing/` prefix locations use Gateway and assert each virtual host includes `/etc/nginx/snippets/marketing-auth-login.conf`. Read that shared snippet once and verify its single exact `/api/marketing/auth/login` location targets Marketing. In `ComposeContractTest`, verify the snippet is mounted read-only, keep Nginx waiting for both healthy Gateway and Marketing, and add the Marketing dependency to Gateway because its low-priority route uses the legacy service. Add assertions that `hm-gateway/src/main/resources/application.yaml` contains the `/api/**` legacy route after specific routes and the `/api/callbacks/payments/**` compatibility route with `StripPrefix=1`.

```python
    def test_marketing_routes_keep_api_prefix_after_stripping_marketing_segment(self):
        locations = re.findall(
            r"location\s+\^~\s+/api/marketing/\s*\{([^}]+)\}", self.conf, re.S
        )
        self.assertEqual(2, len(locations))
        for body in locations:
            self.assertIn("set $gateway_backend hm-gateway:8080;", body)
            self.assertRegex(body, r"rewrite\s+\^/api/marketing/\(\.\*\)\$\s+/api/\$1\s+break;")
            self.assertIn("proxy_pass http://$gateway_backend;", body)
    def test_exact_legacy_marketing_login_stays_on_marketing_until_identity_cutover(self):
        servers = re.findall(r"(?ms)^    server\s*\{(.*?)^    \}", self.conf)
        self.assertEqual(2, len(servers))
        for server in servers:
            self.assertEqual(
                1,
                server.count("include /etc/nginx/snippets/marketing-auth-login.conf;"),
            )
        snippet = read("nginx/snippets/marketing-auth-login.conf")
        locations = re.findall(
            r"location\s*=\s*/api/marketing/auth/login\s*\{([^}]+)\}",
            snippet,
            re.S,
        )
        self.assertEqual(1, len(locations))
        body = locations[0]
        self.assertIn("set $marketing_backend marketing-service:8080;", body)
        self.assertIn("rewrite ^/api/marketing/auth/login$ /api/auth/login break;", body)
        self.assertIn("proxy_pass http://$marketing_backend;", body)

    def test_gateway_keeps_marketing_compatibility_and_callback_rewrite(self):
        application = read("hm-gateway/src/main/resources/application.yaml")
        self.assertRegex(
            application,
            r"(?ms)^\s+- id: marketing-payment-callback-compat\s+"
            r"uri: lb://marketing-service\s+predicates:\s+"
            r"- Path=/api/callbacks/payments,/api/callbacks/payments/\*\*\s+"
            r"filters:\s+- StripPrefix=1\s*$",
        )
        self.assertIn("- POST /api/shipping/callbacks/logistics", application)
        self.assertNotIn("POST /api/auth/login", application)
        self.assertNotIn("POST /callbacks/payments", application)
        self.assertIn("- id: marketing-payment-callback-native", application)
        self.assertIn("Path=/callbacks/payments,/callbacks/payments/**", application)
        legacy = re.search(r"(?ms)^\s+- id: marketing-legacy\s+.*?(?=^\s+- id:|\Z)", application)
        self.assertIsNotNone(legacy)
        self.assertIn("uri: lb://marketing-service", legacy.group(0))
        self.assertIn("Path=/api/**", legacy.group(0))
        self.assertLess(
            application.index("id: marketing-payment-callback-compat"),
            application.index("id: marketing-legacy"),
        )
```

Keep existing unrelated infrastructure assertions intact. The Python route/config assertions must fail for Marketing prefix paths still going directly to Nginx's old upstream, a missing exact-login exception, missing Gateway fallback/callback compatibility, missing exact logistics exclusion, and missing Compose dependency changes. The Java tests capture method/path matching and header-sanitization behavior for the exact logistics exception and ensure payment callback authentication stays on by default.

Run: `python -m unittest tests.test_infrastructure_contract`

Expected: FAIL only on the new Gateway/Nginx/Compose contract assertions; do not weaken assertions to match the current topology.

- [ ] **Step 3: Commit the failing contracts**

```powershell
git add hm-gateway/src/test/java/com/dongqh/hmgateway/filters/AuthGlobalFilterTest.java tests/test_infrastructure_contract.py
git commit -m "test: define marketing gateway compatibility contract"
```

### Task 2: Add the legacy Marketing route and exact authentication exceptions

**Files:**
- Modify: `hm-gateway/src/main/resources/application.yaml`
- Modify: `hm-gateway/src/test/java/com/dongqh/hmgateway/filters/AuthGlobalFilterTest.java`

**Interfaces:**
- Consumes: current HMall Gateway routes and `AuthGlobalFilter` method/path matching.
- Produces: `marketing-legacy` (`lb://marketing-service`), callback compatibility routing, and the exact Gateway logistics-callback exception. The legacy Marketing login remains outside Gateway until the identity cutover.

- [ ] **Step 1: Add the compatibility routes after all existing HMall routes**

Append these routes to `spring.cloud.gateway.server.webflux.routes`. Keep them after the current `/items`, `/carts`, `/users`, `/orders`, and `/pay-orders` routes. The payment callback compatibility path is specific and appears before the catch-all.

```yaml
            - id: marketing-payment-callback-compat
              uri: lb://marketing-service
              predicates:
                - Path=/api/callbacks/payments,/api/callbacks/payments/**
              filters:
                - StripPrefix=1
            - id: marketing-payment-callback-native
              uri: lb://marketing-service
              predicates:
                - Path=/callbacks/payments,/callbacks/payments/**
            - id: marketing-legacy
              uri: lb://marketing-service
              predicates:
                - Path=/api/**
```

The `marketing-legacy` route is temporary. Future service-specific paths must be listed before it and point to their Nacos service IDs; remove the legacy route only after every Marketing domain has passed data and API cutover.

- [ ] **Step 2: Add only the exact Gateway callback exception**

In the existing `hm.auth.excludePaths` list, add:

```yaml
      - POST /api/shipping/callbacks/logistics
```

Keep `POST /users/login` and the existing public HMall reads. Do not add `/api/auth/login` or either payment callback path to this list. The legacy Marketing login bypasses Gateway only through the exact Nginx location in Task 3; it is not a Gateway authentication exception. `AuthGlobalFilter` already strips caller-supplied `user-info` before both anonymous and authenticated handling; keep that behavior unchanged.

- [ ] **Step 3: Run Gateway unit tests**

Run: `mvn -pl :hm-gateway -am -Dtest=AuthGlobalFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: `AuthGlobalFilterTest` passes, including the POST-only logistics exception, spoofed-header removal, and payment callback rejection without JWT.

- [ ] **Step 4: Commit the Gateway compatibility layer**

```powershell
git add hm-gateway/src/main/resources/application.yaml hm-gateway/src/test/java/com/dongqh/hmgateway/filters/AuthGlobalFilterTest.java
git commit -m "feat: route legacy marketing APIs through gateway"
```

### Task 3: Forward Marketing Nginx traffic through Gateway

**Files:**
- Modify: `nginx/nginx.conf`
- Create: `nginx/snippets/marketing-auth-login.conf`
- Modify: `compose.yaml`
- Modify: `tests/test_infrastructure_contract.py`

**Interfaces:**
- Consumes: Gateway compatibility routes from Task 2.
- Produces: both Portal and Admin proxying Marketing APIs to `hm-gateway:8080`, with only the exact legacy login proxied to `marketing-service:8080`; Compose startup dependencies match both paths.

- [ ] **Step 1: Change the two active Marketing Nginx locations**

In each `location ^~ /api/marketing/` block in `nginx/nginx.conf`, replace only its upstream assignment; retain rewrite and proxy headers:

```nginx
        location ^~ /api/marketing/ {
            set $gateway_backend hm-gateway:8080;
            rewrite ^/api/marketing/(.*)$ /api/$1 break;
            proxy_pass http://$gateway_backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header user-info "";
        }
```

Before each prefix location, include the shared exact-match snippet. Nginx exact matches take precedence over the `/api/marketing/` prefix location:

```nginx
        include /etc/nginx/snippets/marketing-auth-login.conf;
```

Create the shared snippet with the exact `/api/marketing/auth/login` location, rewriting only that request to `/api/auth/login` and proxying to `marketing-service:8080`. Retain the existing proxy headers and clear `user-info`; define the full location in this snippet only once.

Do not add `/callbacks/payments` to Nginx: it is not an existing public route and its local simulation signature is not a production callback credential.

- [ ] **Step 2: Make Compose startup reflect the new routing dependency**

Under `hm-gateway.depends_on`, add:

```yaml
      marketing-service:
        condition: service_healthy
```

Keep both `hm-gateway` and `marketing-service` in `nginx.depends_on`: Nginx calls Gateway for normal APIs and Marketing directly for the exact legacy login path.

Mount the shared snippet read-only into the Nginx container:

```yaml
    volumes:
      - ./nginx/snippets/marketing-auth-login.conf:/etc/nginx/snippets/marketing-auth-login.conf:ro
```

```yaml
      hm-gateway:
        condition: service_healthy
      marketing-service:
        condition: service_healthy
```

Do not remove the `marketing-service` dependency. Gateway also needs the old service as its low-priority fallback.

- [ ] **Step 3: Update Compose/Nginx contract assertions**

Keep `ComposeContractTest.test_nginx_exposes_portal_and_admin` asserting healthy dependencies on both `hm-gateway` and `marketing-service`, and that the shared snippet is mounted read-only. Extend `test_gateway_joins_the_default_stack_and_waits_for_discovery_and_routes` to assert healthy `marketing-service` dependency. Change `NginxRoutingContractTest.test_hmall_api_routes_are_sent_through_the_authenticating_gateway` to expect four Gateway proxy locations; change `test_proxy_upstreams_use_docker_runtime_dns` so the Marketing prefix locations use `$gateway_backend`, the one shared exact-login location uses `$marketing_backend`, and all proxy variables are initialized. Keep `test_runtime_upstream_is_initialized_before_rewrite_breaks` asserting both backend variables are set before their rewrites/proxy calls.

- [ ] **Step 4: Run routing contracts**

Run: `python -m unittest tests.test_infrastructure_contract`

Expected: all infrastructure contract tests pass, including Compose config rendering, Gateway dependency health ordering, and both Nginx server blocks.

- [ ] **Step 5: Commit the runtime cutover**

```powershell
git add nginx/nginx.conf compose.yaml tests/test_infrastructure_contract.py
git commit -m "feat: proxy marketing traffic through gateway"
```

### Task 4: Document the new request path and verify the runtime boundary

**Files:**
- Modify: `README.md`
- Test: `hm-gateway/src/test/java/com/dongqh/hmgateway/filters/AuthGlobalFilterTest.java`
- Test: `tests/test_infrastructure_contract.py`

**Interfaces:**
- Consumes: final route order, Nginx rewrite, and Compose dependencies.
- Produces: accurate operator documentation and executable evidence that existing paths pass through the authenticating Gateway.

- [ ] **Step 1: Update the runtime description in README**

Replace the sentence that says Marketing API is forwarded directly by Nginx with:

```markdown
Nginx rewrites `/api/marketing/**` to the existing backend `/api/**` path and forwards Marketing APIs to `hm-gateway`; the Gateway temporarily routes unmigrated APIs to `marketing-service`. The exact `/api/marketing/auth/login` path remains on Marketing until `user-service` provides the compatible RS256 login response. As each domain is extracted, its explicit Nacos route takes precedence over the Gateway fallback.
```

- [ ] **Step 2: Run the focused Java and infrastructure contracts**

```powershell
mvn -pl :hm-gateway -am test
python -m unittest tests.test_infrastructure_contract
docker compose --env-file .env.example config --quiet
```

Expected: Maven reports `BUILD SUCCESS`, Python reports `OK`, and Compose exits with code 0. Do not claim runtime routing is verified from static tests alone.

- [ ] **Step 3: Verify Nginx configuration and HTTP routing in the local stack**

```powershell
docker compose --env-file .env up -d --build hm-gateway nginx
docker compose exec nginx nginx -t
curl.exe -i http://localhost:18080/api/marketing/lottery/activities
curl.exe -i http://localhost:18080/api/items
```

Expected: Nginx reports syntax OK; the Marketing request reaches Gateway authentication and returns its expected unauthenticated response (401, not a Gateway 404); the HMall request reaches the existing item route. If a local endpoint intentionally permits anonymous access, use its existing API contract and assert the response body/service signature rather than weakening the route.

- [ ] **Step 4: Commit the operator-facing path documentation**

```powershell
git add README.md
git commit -m "docs: document marketing gateway compatibility routing"
```

## Phase Boundary and Follow-On Plans

This plan establishes the entry point only; it does not move database ownership or delete any Marketing implementation. The exact login exception remains until the identity/item plan merges authentication and RBAC into `user-service`, preserves the old response shape and Redis session semantics, and proves that returned RS256 tokens work through Gateway and all migrated services. The following plans cover trade/pay, coupon, points, membership, lottery (including the Redis/Lua/Redisson contract in `docs/superpowers/specs/2026-09-23-marketing-service-decomposition-design.md`), and fulfillment. Each such plan adds its `hm-api` contracts alongside the service APIs and has its own migration and rollback verification before the Gateway route changes owner.

## Self-Review

- Spec coverage: this plan implements the public URL, Gateway auth, Nginx proxy, Compose route dependency, and legacy fallback portions of sections 5, 6, and 8. Domain ownership, `hm-api` business contracts, Redis extraction, database migration, and legacy deletion remain explicitly assigned to the follow-on per-subsystem plans.
- Completeness scan: each implementation step names the file, route, command, or assertion to change; no unspecified implementation step remains.
- Type/path consistency: Nacos IDs are `marketing-service` and `hm-gateway`; Gateway listens on 8080; active Docker Nginx config is `nginx/nginx.conf`; browser URLs and backend rewrite match the approved design.
