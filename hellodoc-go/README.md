# Go 后台迁移（开发中）

本切片提供 `/api/system/info`，默认监听 `127.0.0.1:18081`。匿名 GET 返回 Java 项目版本和本地时间。携带 Authorization 的请求返回 401。有效、过期、刷新及 MCP 令牌的处理仍待迁移。

## 构建与回归

在仓库根目录运行。要求已安装 Go、项目所需 JDK 和 Python 3。构建脚本读取 Gradle 项目版本，Java 的 processResources 使用同一属性填充 app.version。

```sh
(cd hellodoc-server && ./gradlew test --tests '*SystemInfoControllerTest' bootJar)
(cd hellodoc-go && go test ./... && go vet ./...)
python3 -B -m unittest discover -s tests/migration
root=$PWD
work=$(mktemp -d)
scripts/migration/build-go.sh "$work/hellodoc"
version=$(cd hellodoc-server && ./gradlew -q properties --property version | awk '/^version: / {print $2}')
```

## 复现真实 HTTP 对照

下面在同一个 shell 中启动两个临时进程。先阅读本机的 `.local/test-environment.md`（如存在），确认两个示例端口空闲。Java 使用测试资源中配置的内存 H2 数据库，测试前端、测试 PostgreSQL 和正式服务均无需启动。Gradle 测试会下载 H2 驱动。

```sh
java_port=38080
go_port=38081
lsof -nP -iTCP:"$java_port" -iTCP:"$go_port" -sTCP:LISTEN
# 仅当上面两个端口均空闲时继续。
h2=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.h2database/h2" -name 'h2-2.3.232.jar' | head -1)
test -f "$h2" || exit 1
cp "$root/hellodoc-server/src/test/resources/application.properties" "$work/oracle.properties"
cd "$work"
env -i HOME="$work" PATH="$PATH" TZ=Asia/Shanghai OPENAI_API_KEY=unused \
  OPENAI_BASE_URL=http://127.0.0.1:9 OPENAI_MODEL=unused AI_AGENT=unused \
  java -Dloader.path="$h2" -cp "$root/hellodoc-server/build/libs/hellodoc.jar" \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.config.additional-location=file:./oracle.properties --spring.flyway.enabled=false \
  --app.cors.allowed-origins=http://localhost:13000 --server.address=127.0.0.1 \
  --server.port="$java_port" >java.log 2>&1 &
java_pid=$!
env TZ=Asia/Shanghai APP_CORS_ALLOWED_ORIGINS=http://localhost:13000 \
  HELLODOC_GO_ADDR="127.0.0.1:$go_port" ./hellodoc >go.log 2>&1 &
go_pid=$!
```

等到 java.log 出现 `Started HelloDocApplication`，确认监听端口的 PID 与上述变量一致，再运行对照并停止本次进程。

```sh
lsof -nP -a -p "$java_pid,$go_pid" -iTCP -sTCP:LISTEN
TZ=Asia/Shanghai python3 -B "$root/scripts/migration/compare_system_info.py" \
  --java-port "$java_port" --go-port "$go_port" --version "$version" --http-matrix
kill "$go_pid" "$java_pid"
wait "$go_pid"
wait "$java_pid"
cd "$root"
```

矩阵验证默认中文环境下的 GET、HEAD、普通 OPTIONS、空 POST、JSON 与纯文本 Accept、无法解析的 Bearer，同源 GET 和 POST、拒绝的跨域 POST，以及针对显式允许列表的两种 GET 预检。路由对其余方法保留 Go 的 405 回退。实测 Java 对 TRACE 和自定义 OTHER 方法均返回 400，这两种方法仍有差异。默认 CORS 配置为通配符，复现实验显式配置一个允许来源以覆盖拒绝场景。POST 的 HTTP 状态为 200，JSON 业务码为 9999。时间按每个目标的请求起止秒分别校验，比较时使用相同时区。传输层生成的 Date、长度与分块编码不参与比较。其他媒体类型、语言及认证流程尚未完成对照。
