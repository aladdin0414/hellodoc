# Go 身份认证基础实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在隔离环境中用 Go 完成已有账号登录、注册、访问令牌验证、刷新轮换与注销，并给出 Java ↔ Go 的双向验证结果。

**Architecture:** 延用现有 Go HTTP 路由。用 Java 捕获可观察行为，用 PostgreSQL 事务保存账号、会话和日志，用真实签名令牌联通各层。每个端点通过独立协议测试后才进入对照矩阵。

**Tech Stack:** Go 1.25、net/http、database/sql、pgx/v5/stdlib、golang.org/x/crypto/bcrypt、golang-jwt/jwt/v5、Java Spring Security、JUnit、Python unittest。为三个新增模块选择支持仓库 Go 版本的版本并提交 go.sum，新增依赖随首次使用引入。

**Spec:** `docs/superpowers/specs/2026-11-25-go-backend-rewrite-design.md`；上层路线为 `docs/superpowers/plans/2026-11-25-go-backend-rewrite.md`。

## Global Constraints

- 保留前端、桌面、MCP bridge、部署入口、Flyway 历史和 Java 基准。受控运行期间仍由 Java 承担尚未迁移的权限与业务路由。
- 本机启动前读取 `.local/test-environment.md`。本计划创建临时独立 PostgreSQL 容器与随机回环端口、随机测试账号和密钥、独立临时工作目录。仅清理本次创建且身份标记匹配的资源。拒绝外部 DSN、挂载既有卷、已有 UI review 库和正式环境。启动时使用白名单环境，禁止继承 .env 或实际外部服务凭据。
- 本切片使用 Java 初始化的合成库验证既有数据兼容。空库管理员初始化、历史版本逐版升级与恢复仍由数据库切片负责。Go 服务不自动建表。H2 结果只作为 Java 逻辑证据。
- 下列命令从仓库根运行，Go 命令从 hellodoc-go 运行，Gradle 命令从 hellodoc-server 运行。每项先记录失败，再取得通过结果并只提交本项文件。审查门槛包括用例断言、返回值与落库副作用。

## Review Focus

1. 已有 bcrypt 前缀、可选 SHA-256 配置与 UTF-8 字节边界对应任务 2。
2. 过期与错误签名产生不同刷新提示，Bearer 上的实时账号状态和角色对应任务 3、5。
3. 两次并发刷新、事务回滚及 family 隔离对应任务 7。
4. 无身份注销、body 与 Bearer 指向不同账号对应任务 8。
5. 同一令牌跨实现并且真实已有记录续签、客户端错误封装对应任务 9。

## 文件与基准

Java 根为 `hellodoc-server/src/main/java/com/nopkg/hellodoc/`。核对 controllers/AuthController.java、services/{AuthService,CustomUserDetailsService,LoginLogService}.java、security/{Sha256PasswordEncoder,JwtTokenProvider,JwtAuthenticationFilter}.java、config/SecurityConfig.java、repositories/RefreshTokenRepository.java、web/dto/AuthResponse.java、resources 下的 sys_config.yaml、schema.sql 和 db/migration。现有对照入口为 AuthControllerTest、UserControllerTest 及 docs/migration/contracts.tsv。Go 根为 `hellodoc-go/`，以下 internal/ 和 cmd/ 均相对此根。新增 internal/auth/ 文件按以下任务分配，router.go 负责组合，main.go 负责启动依赖。

### Task 1: 建立 Java 基准和受保护的 PostgreSQL 夹具

- [ ] 新建 tests/migration/test_auth_fixture.py 与 scripts/migration/auth_fixture.py。先运行 `python3 -m unittest discover -s tests/migration -p test_auth_fixture.py`，预期缺少模块。定义上下文管理器 `auth_fixture()`，返回两个独立目标各自的连接参数和临时目录；断言无法传入任意目标，清理时标签和容器 ID 均匹配，同名旧资源保持原状。
- [ ] 在两个隔离库用相同版本 Java 的正常启动路径初始化，检查 schema.sql、外键、序列、实际类型、Flyway 历史及后续 Go 启动前后行数。使用合成账号固定业务字段，动态 ID 保存映射。增加独立单元用例，模拟创建第二实例失败，确认只回收已创建资源。
- [ ] 新建 hellodoc-server/src/test/java/com/nopkg/hellodoc/controllers/AuthMigrationContractTest.java。用独立 H2 内存配置固定 en 与 zh-CN 两种 locale，断言四端点成功、错误、缺字段、错误 Content-Type、无效 Bearer 的实际状态、Content-Type、响应字段、null、业务码与消息。运行 `./gradlew test --tests '*AuthMigrationContractTest' --tests '*AuthControllerTest'`，将经验证的预期写入 tests/migration/fixtures/auth_contract.json。禁止保存随机令牌或账号密码。H2 和真实 PostgreSQL 的结论分别记录。
- [ ] 重跑夹具测试和 Java 用例并审查。无法启动临时数据库时标记 PG 未验证，保留后续 SQL 验收门槛。

### Task 2: 兼容 Spring 密码编码

- [ ] 新建 internal/auth/password_test.go，写 TestSpringPasswordVectors，运行 `go test ./internal/auth -run TestSpringPasswordVectors` 见缺少实现。向任务 1 Java 测试增加使用固定合成密码的向量生成断言。覆盖默认 bcrypt cost 10、2a/2b/2y、错误密码/损坏 hash、Unicode、空串、72/73 字节。记录实际 Java 长度边界行为。
- [ ] 新建 internal/auth/password.go，定义 `PasswordCodec{Encoder, Pepper, Output string}`、`(PasswordCodec) Encode(raw string) (string,error)`、`(PasswordCodec) Matches(raw, encoded string) bool`。仅 encoder 等于 sha256（忽略大小写）时写 UTF-8 的 pepper+raw 的 SHA-256，小写 hex 或标准 base64；兼容上述 bcrypt 旧值。其余配置按 Spring bcrypt 校验。用常量时间比对摘要。修改 go.mod/go.sum 引入 bcrypt；确认 Java 可校验 Go 生成的值。缺失密码与空串由 HTTP 层区分。
- [ ] 重跑上述测试；审查 bcrypt 随机盐不做字面比较，SHA 输出逐字节相同。

### Task 3: 签发、验证及区分访问与刷新令牌

- [ ] 新建 internal/auth/token_test.go，写 TestJavaJWTInterop，用 Java 临时生成的测试密钥/令牌和可注入时间，覆盖正确和错误密钥、算法、签名、过期、类型、必需身份字段和 UUID。运行 `go test ./internal/auth -run TestJavaJWTInterop` 见未定义符号。
- [ ] 新建 internal/auth/token.go，定义 `NewTokens(secret []byte, accessTTL, refreshTTL time.Duration, now func() time.Time) (*Tokens,error)`、`(*Tokens) Access(subject string, roles []string) (string,error)`、`(*Tokens) Refresh(subject,jti,family string) (string,error)`、`(*Tokens) Verify(raw string) (Claims,error)`，Claims 保留 sub、roles、iat、exp、type、jti、family。只接受 HS512、UTF-8 原始密钥至少 64 字节，强制有效签名、非空 subject 和到期时间；如 Java 接受缺失字段记为安全差异。访问令牌省略 type，refresh 含 type=refresh、jti、family。使用 NumericDate 秒；配置 JWT_EXPIRATION_MS 默认 86400000、JWT_REFRESH_EXPIRATION_MS 默认 604800000。区分“签名有效但过期”和“无法验证”。更新 go.mod/go.sum。
- [ ] 重跑测试并由 Java JJWT 校验 Go 令牌完整声明；过期分类必须在签名验证之后。双向运行证据及 JWT_SECRET 只使用本次测试材料。

### Task 4: PostgreSQL 账号与角色查询

- [ ] 新建 internal/auth/store_postgres_test.go 和 scripts/migration/test_auth_postgres.py（统一在任务 1 的 auth_fixture 内启动 Go 集成测试，不接收外部库地址）。写 TestLookupPasswordAccount，运行 `python3 scripts/migration/test_auth_postgres.py TestLookupPasswordAccount` 预期缺实现。
- [ ] 新建 internal/auth/store.go，定义 `Store{DB *sql.DB}`、`(*Store) Lookup(ctx context.Context, username string) (Account,error)`；Account 包含 UserID、AuthID、Identifier、Credential、Status、Nickname、Avatar、Roles。按 identifier 与 identity_type=PASSWORD 查 sys_user_auth 和 sys_user，匹配 RoleRepository 的查询与 Spring User 权限顺序、去重和 ROLE_ 前缀。保留 nullable 字段，status 为 null/0 表示启用。配置 pgx 驱动并更新 go.mod/go.sum。
- [ ] 重跑上述命令，加入相同 identifier 的其他 identity_type、无角色、更新角色、禁用、查无记录用例。审查用已初始化真实库且无 DDL 权限的 Go 账号即可读取。

### Task 5: 在系统信息入口接入 Bearer

- [ ] 新建 internal/auth/bearer_test.go，修改 internal/server/router_test.go，写 TestBearerSystemInfo。运行 `go test ./internal/... -run 'TestBearer|Test.*SystemInfo'` 先见正确 access 仍被拒绝。
- [ ] 新建 internal/auth/bearer.go，定义 `(*Service) Bearer(next http.Handler) http.Handler` 和 `Principal(ctx context.Context) (Account,bool)`；internal/auth/service.go 定义 `Service{Store *Store, Tokens *Tokens, Passwords PasswordCodec}`。验证访问令牌后从数据库重载权限，仅在请求 context 保存账号。拒绝 refresh 和非 MCP 路径上的 hdmcp_。按真实 Java 基准处理 Authorization 缺省、大小写、空白、Basic、重复头。过期 401 的 WWW-Authenticate 描述 token_expired、needRefresh=true，其余无效凭据用 invalid_token 和 false，JSON 正文核对 locale。角色变更、禁用或删除后的旧票不得恢复原权限。
- [ ] 修改 internal/server/router.go，保留 NewRouter 现有未配置模式；增加 `NewRouterWithAuth(version string, now func() time.Time, svc *auth.Service) http.Handler`。先走现有 CORS 预检，再走认证和公开处理器。修改 cmd/hellodoc/main.go，显式启用认证时加载 Java 对应数据库、secret、TTL、密码参数，缺失或不可用时启动失败。原独立系统信息模式继续可运行。运行 `go test ./...`，审查两种启动模式和 HEAD/OPTIONS 行为。

### Task 6: 登录和注册分别接入 HTTP

- [ ] 新建 internal/auth/login_test.go 和 login.go。先写 TestLoginContract，运行 `go test ./internal/auth -run TestLoginContract` 见未实现。实现 `(*Service) Login(ctx context.Context, username,password string) (AuthResponse,error)`。用任务 4 读账号，签发访问和独立 family 的刷新令牌，保存 sys_refresh_token 和成功的 sys_login_log（真实 user/auth ID，IP、设备及 UA 按 Java 为 null）。不返回 credential。AuthResponse 精确对应 accessToken、refreshToken、tokenType="Bearer"、nickname、avatar、isInitialPwd=false。错误凭证、禁用和日志写入失败均不返回可用会话；单独记录 Java 登录分步提交的故障窗口。
- [ ] 新建 internal/auth/http.go 与 http_test.go，定义 `(*Service) RegisterRoutes(mux *http.ServeMux)`，从 NewRouterWithAuth 注册 POST /api/auth/login；复用任务 5 过滤器。运行协议测试与 `python3 scripts/migration/test_auth_postgres.py TestLoginPersistence`（在 store_postgres_test.go 添加此测试），确认响应、错误、日志和 refresh 行。审查后独立提交登录。
- [ ] 新建 internal/auth/register_test.go、register.go，在 http.go 增加 POST /api/auth/register。先写 TestRegisterContract 与 TestRegisterAtomic，分别通过 Go 单测和 PG 启动器见红。实现 `(*Service) Register(ctx context.Context, req RegisterRequest) (PublicUser,error)`。按 RegisterRequest.java 及任务 1 捕获的用户 JSON 建模；空白昵称回退 username，realName/email/phone 空白置 null。一个事务内写用户、verified=true 的 PASSWORD 身份并优先分配 user、其次 common、两者均无时不分配。覆盖重复和并发注册、写中失败无半个用户、返回字段白名单。重跑两项，独立审查注册。

### Task 7: 刷新轮换和复用

- [ ] 新建 internal/auth/refresh.go、refresh_test.go；在 store_postgres_test.go 写 TestRefreshRotationPostgres。分别运行 `go test ./internal/auth -run TestRefresh` 与 `python3 scripts/migration/test_auth_postgres.py TestRefreshRotationPostgres` 见红。
- [ ] 实现 `(*Service) Rotate(ctx context.Context, raw string) (AuthResponse,error)`，在 http.go 接入 POST /api/auth/refresh-token。严格验证 refresh 类型、签名、期限、jti、family，锁定现有行 FOR UPDATE，核对 family、owner、行到期时间和当前账号状态。事务内插入新记录、parent_jti 指向旧行、标记旧行 ROTATED、设置 replaced_by_jti/used_at/revoked_at；生成新访问令牌取当前权限。
- [ ] 用两个真实连接同步重刷同一旧票，验证至多一次轮换成功，其他 family 不受影响；注入子行写入失败并验证旧行可重试。Java 在 @Transactional 中调用 revokeFamily 后抛业务异常，必须从独立已提交事务观察后代是否实际撤销。Go 将 REUSE_DETECTED 撤销提交后返回错误，若基准出现回滚差异，记录且阻止标 verified。重跑测试，审查 race 验证检查数据库终态。

### Task 8: 注销权限与副作用

- [ ] 新建 internal/auth/logout.go、logout_test.go，在 store_postgres_test.go 增加 TestLogoutScopePostgres。运行 `python3 scripts/migration/test_auth_postgres.py TestLogoutScopePostgres` 先见红。用两个用户、每人两个 family 验证 anonymous 空 body、仅 body、仅 Bearer、有效和过期/无效 body、Bearer 与 body 不同用户。观察事务后的每个 family、reason、旧 access 的有效期。
- [ ] 实现 `(*Service) Logout(ctx context.Context, principal *Account, refresh *string) error` 与 POST /api/auth/logout 的可选 JSON body。Java 的 /api/auth/** 默认开放，匿名 body 持有者可请求撤销自己的令牌 family；登录用户按账号撤销全部。Java getFamilyIdFromToken 可读取签名正确且已过期票；实现独立的只供注销使用的验签路径，禁止将过期票用于授权或轮换。无效 body 的观察结果与无效 Authorization 头分别比对。合法用户撤销时核实 family 关联当前账号，不允许凭提交他人 family 撤销第三方会话；单列 Java 对不同主体的 body 和 header 分别处理的行为差异。
- [ ] 重跑上述 PG 用例和 `go test ./internal/auth -run TestLogout`。审查刷新行只影响预期范围、返回与 Java 一致的空 data、访问令牌是否仍按 Java 有效至过期。未决定的安全差异保持公开记录，禁止宣布全面兼容。

### Task 9: 双向运行和清单验收

- [ ] 新建 scripts/migration/compare_auth.py 和 tests/migration/test_compare_auth.py。先写 `python3 -m unittest discover -s tests/migration -p test_compare_auth.py`，断言错码、漏 null、错 token 类型、越权、少日志、错 parent_jti 均产生非零结果。实现 `python3 scripts/migration/compare_auth.py` 自建任务 1 的夹具，分别执行注册→登录→公开端点携票→轮换→注销→失效后重试。实现向量包含 H2 捕获与 Java/PG 的单独标签。
- [ ] 为跨实现重启分别使用两条独立跑道：在合成库 A 以 Java 发票后停止 Java，再以 Go 对相同已提交数据验票、续签、注销；反向在另一个 Java 已初始化的合成库 B 运行 Go→Java。只在同一跑道交接测试配置和临时密钥，同时运行的两边使用不同库。比较验证后的 claims、TTL 窗口、实际关联关系、角色及日志，按关系重映射随机 ID。不得遮蔽整个 token、错误消息或 headers。确认 Flyway 历史、现有合成用户和资源均保留。
- [ ] 运行 `go test -race ./...`、两端全矩阵和 `python3 -m unittest discover -s tests/migration`。在 docs/migration/contracts.tsv 仅更新实测行与对应源码符号；列出命令、两种引擎、通过或缺失的证据。客户端自动刷新队列、原生 fetch、桌面及 MCP 只有实际运行了对应客户端才可标 verified。MCP 专用令牌、WebSocket 握手、授权切面及完整 RBAC、密码变更、管理员禁用、系统级初始化与线上切换仍需各自计划。保留任何差异项的 gap，评审确认后再进入下个切片。
