# Go 后台迁移第一阶段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标**　交付可追溯的契约清单、只读 HTTP 对照工具和经过 Java 实例验证的系统信息接口。

**架构**　Java 是行为基准。沿用 Go 的 `cmd/hellodoc`、`server.NewRouter(version string, now func() time.Time) http.Handler` 和 `systeminfo.New(version string, now func() time.Time) http.Handler`。独立工具经 HTTP 检查两个进程。

**技术栈**　Go 1.25 标准库、Java/Spring Boot、Gradle Wrapper、Python 3 标准库。

**设计依据**　`docs/superpowers/specs/2026-11-25-go-backend-rewrite-design.md`。后续业务切片顺序见 `docs/superpowers/plans/2026-11-25-go-backend-rewrite.md`。

## 全局约束

保留客户端、Java 默认部署、数据库、文件卷与 Flyway 历史。服务、数据库或浏览器验证前阅读本机 `.local/test-environment.md`（若存在），核对真实目标与端口归属。测试报告仅记录脱敏结果。Go 在本切片无需连接数据库、Redis 或文件存储。后续写请求对照需要两个独立的数据集和存储目录。

## 审查重点

1. 直接请求、动态路由、原生协议和后台任务均可追溯至源码（任务一）。
2. 无效目标及重定向不能让工具请求未知服务（任务二）。
3. 时间须可严格解析且在请求窗口内，额外字段及值类型变化均须报错（任务二）。
4. 匿名访问、请求方法与内容协商由实际 Java 过滤器链决定（任务三）。
5. 比较使用同一次构建的版本来源，升级版本后也能重建（任务三）。

### Task 1: 建立基准清单

**文件与接口**　新建 `docs/migration/contracts.tsv`、`tests/migration/test_inventory.py`。清单以 UTF-8 制表符分隔，首行为 `id kind contract server_ref caller_ref test_ref status gap` 八列。引用使用仓库相对路径和符号，格式为 `path#symbol`。多项引用用竖线分隔。状态为 `identified`、`baseline`、`verified`，无已知调用或测试填 `-` 并在 gap 说明。HTTP 的唯一标识包含方法和完整路径。

- [ ] 在测试中用 csv.DictReader 检查表头、非空及唯一 id、状态枚举、每个引用的文件存在、verified 必须有测试证据。要求涵盖 http、ws、sse、job、database、integration、client、mcp 八类，并以 GET /api/system/info、/ws/doc/{docId} 和客户端刷新请求作跨类抽样。
- [ ] 执行 `python3 -m unittest discover -s tests/migration -p test_inventory.py`，确认清单缺失时失败。
- [ ] 用 `rg -n 'Mapping|@Scheduled|WebSocket|SseEmitter|ApplicationRunner|CommandLineRunner' hellodoc-server/src/main` 提取入口，再逐个展开类级路径、方法、别名和配置注册。核对 SQL 迁移、初始化、存储、Redis 和外部集成；记录实际使用或尚待核实之处。
- [ ] 用 `rg -n 'fetch\(|axios|WebSocket|EventSource|/api/|/ws/|refresh' hellodoc-client hellodoc-desktop hellodoc-mcp-bridge` 对照调用点，解析封装、模板路径、重试和转发；逐条登记 MCP 工具和传输注册。每个无直接客户端引用的服务入口也保留一行。按来源目录逐文件核销，抽样测试不能代替清点。
- [ ] 重跑上述 unittest；审查者抽查公开接口、一个后台任务及一个 bridge 调用，能够沿引用找到实现。提交 `docs: inventory backend migration contracts`。

### Task 2: 建立只读系统信息对照工具

**文件与接口**　新建 `scripts/migration/compare_system_info.py`、`tests/migration/test_compare_system_info.py`。仓库根执行 `python3 scripts/migration/compare_system_info.py --java-port 18080 --go-port 18081 --version VERSION`。目标固定为 IPv4 回环，参数只接收两个互异的有效端口。退出码 0 表示一致，1 表示契约差异，2 表示配置或连接失败。只访问固定的系统信息路径。

- [ ] 用 unittest 和临时端口的本地 HTTPServer 模拟两个目标。覆盖请求相同、JSON 键顺序不同、两次时间跨秒仍成功、额外字段、错误内容类型、HTTP 状态或版本变化、非法日期、过时时间、超时、重复端口和重定向。重定向测试以第三个本地监听器的请求计数为零作断言。
- [ ] 运行 `python3 -m unittest discover -s tests/migration -p test_compare_system_info.py`，确认缺失工具产生失败。
- [ ] 使用 http.client.HTTPConnection 显式限定主机、路径和 5 秒超时，关闭每次连接。用同步的本地时区分别记录每次请求前后时间，按秒截断后检查闭区间；强制校验格式与真实日期。要求两边 HTTP 200、application/json、恰好四个字符串字段，name 为 HelloDoc Server、status 为 UP、version 等于构建值。解析 JSON 后比较，只有验证通过的 serverTime 允许不同。
- [ ] 重跑测试，检查差异输出包含目标、字段、期望和实得值。连接失败不能输出通过。提交 `test: compare isolated system info responses`。

### Task 3: 验证经过滤器链和打包入口的 Go 行为

**文件与接口**　复用 `hellodoc-server/src/test/java/com/nopkg/hellodoc/controllers/SystemInfoControllerTest.java` 和 `hellodoc-go/internal/systeminfo/handler_test.go`。扩展 `hellodoc-go/internal/server/router_test.go`、任务二的比较工具与测试；按基准差异调整 `hellodoc-go/internal/server/router.go`、`hellodoc-go/internal/systeminfo/handler.go`。新建 `scripts/migration/build-go.sh`，接口为一个必填的输出二进制路径。在 `hellodoc-go/README.md` 记录复现命令，在清单中更新此接口的状态及证据。

- [ ] 执行 `(cd hellodoc-go && go test ./...)`、`(cd hellodoc-server && ./gradlew test --tests '*SystemInfoControllerTest')`。记录既有测试的结果；现有 Go 实现作为待验证基线。Java 的 standaloneSetup 未加载真实过滤器链和 GlobalExceptionHandler，不能由其 POST 断言推定实机返回 405。
- [ ] 依本机隔离说明核实 Java 目标、数据卷和监听者。使用已确认的隔离进程或依说明自行启动，记录进程归属；Go 使用独立临时工作目录、回环地址和相同时区。仅清理自己启动的进程和临时文件，保留已有服务和测试数据。
- [ ] 用无凭据请求在 Java 上采样 GET、HEAD、OPTIONS、POST，以及 GET 的 Accept 为 application/json 或 text/plain 的情形。对 POST 使用空请求体。记录状态、Content-Type、Allow 方法集合、缓存与安全响应头、JSON 正文或空正文；对公开 GET 另测无法解析的测试 Bearer 值。再按 SecurityConfig 中的跨域规则发起允许和拒绝的 Origin 预检（OPTIONS 携带 Origin 和 Access-Control-Request-Method: GET），验证 CORS 头和状态。核对 JwtAuthenticationFilter、SecurityConfig、GlobalExceptionHandler 和媒体协商后，把实际结果写成 router 表驱动用例和双目标断言。按契约比较头，排除传输层自动生成的 Date。错误响应保留 HTTP 状态与业务码两个断言。发现动态值逐字段说明依据与校验规则。
- [ ] 运行 `(cd hellodoc-go && go test ./internal/server -v)` 和任务二的 unittest。记录新增用例暴露的差异，再调整路由或处理器；维持权限边界与既有注入时钟。重复运行至通过。
- [ ] 从仓库根目录执行 `(cd hellodoc-server && ./gradlew -q properties --property version)` 取得项目版本。构建脚本读取唯一的 version 属性、检查非空，再从 Go 模块执行 `go build -ldflags "-X main.version=$build_version" -o "$output" ./cmd/hellodoc`；进入子目录前将输出参数解析为绝对路径。脚本须在 Gradle 失败或版本缺失时以非零码退出。检查 Java processResources 使用的 app.version 与构建值相同。
- [ ] 用临时目录构建并运行 Go 二进制，将实际构建版本传给对照工具。运行任务二 CLI、全部 migration unittest、`(cd hellodoc-go && go test ./... && go vet ./...)`。保留日期、提交、版本、测试命令和各场景结果；清单区分匿名与携带凭据的场景，分别记录证据。过期、刷新、MCP 令牌留待认证切片对照，记录为 gap；凡依赖尚未迁移的认证能力，维持 baseline，不允许凭据异常时降级为匿名成功。仅已通过的具体场景标为 verified。基础设施不可用时单列缺失的实测证据，保留原状态。
- [ ] 审查者从 README 复现构建和比较，确认客户端及默认部署无更改。提交 `feat: match Java system info HTTP behavior`。

第一阶段的验收限于清单、比较工具与系统信息切片。身份、数据库升级、协作、AI、MCP 及部署切换仍按总计划逐项实施。
