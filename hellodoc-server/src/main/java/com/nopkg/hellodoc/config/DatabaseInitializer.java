package com.nopkg.hellodoc.config;

import com.nopkg.hellodoc.entities.SysRole;
import com.nopkg.hellodoc.entities.SysRolePermission;
import com.nopkg.hellodoc.entities.SysRolePermissionId;
import com.nopkg.hellodoc.entities.SysUser;
import com.nopkg.hellodoc.entities.SysUserRole;
import com.nopkg.hellodoc.entities.SysUserRoleId;
import com.nopkg.hellodoc.repositories.RolePermissionRepository;
import com.nopkg.hellodoc.repositories.RoleRepository;
import com.nopkg.hellodoc.repositories.UserRoleRepository;
import com.nopkg.hellodoc.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库初始化器
 * 在应用启动时检查数据库是否存在,如果不存在则自动创建
 */
@Configuration
@ConditionalOnProperty(name = "app.database.initializer.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${admin.password}")
    private String adminPassword;

    @Autowired(required = false)
    private UserService userService;

    @Autowired(required = false)
    private RoleRepository roleRepository;

    @Autowired(required = false)
    private UserRoleRepository userRoleRepository;

    @Autowired(required = false)
    private RolePermissionRepository rolePermissionRepository;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @PostConstruct
    public void init() {
        try {
            // 从 JDBC URL 中提取数据库名称
            // 例如: jdbc:postgresql://localhost:5432/hellodoc
            String databaseName = extractDatabaseName(datasourceUrl);
            String baseUrl = datasourceUrl.substring(0, datasourceUrl.lastIndexOf('/'));

            logger.info("检查数据库 '{}' 是否存在...", databaseName);

            // 连接到 postgres 默认数据库来检查目标数据库是否存在
            String postgresUrl = baseUrl + "/postgres";

            try (Connection conn = DriverManager.getConnection(postgresUrl, username, password)) {

                // 检查数据库是否存在
                String checkDbQuery = "SELECT 1 FROM pg_database WHERE datname = ?";
                try (PreparedStatement ps = conn.prepareStatement(checkDbQuery)) {
                    ps.setString(1, databaseName);
                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            // 数据库不存在,抛出异常提示用户手动创建
                            String errorMsg = String.format(
                                    "数据库 '%s' 不存在!请先手动创建数据库,例如执行: CREATE DATABASE %s;",
                                    databaseName, databaseName);
                            logger.error(errorMsg);
                            throw new RuntimeException(errorMsg);
                        } else {
                            logger.info("数据库 '{}' 已存在", databaseName);
                            // 数据库已存在但为空时,自动初始化表结构
                            if (isPublicSchemaEmpty()) {
                                logger.info("数据库 '{}' 为空,开始初始化表结构...", databaseName);
                                initializeSchema();
                            }
                            ensureUserLanguageModeColumn();
                            ensureNotificationI18nColumns();
                            ensureDictI18nColumn();
                            ensureSysConfigI18nColumns();
                            ensureRefreshTokenTable();
                            ensureDocumentExtraMetaColumn();
                        }
                    }
                }
            }

            // 数据库初始化完成后,先初始化角色,再创建管理员账户
            initializeRoles();
            initializeConfigs();
            createAdminUser();

        } catch (Exception e) {
            logger.error("数据库初始化失败", e);
            throw new RuntimeException("数据库初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JDBC URL 中提取数据库名称
     */
    private String extractDatabaseName(String url) {
        // jdbc:postgresql://host:port/database?params
        int lastSlash = url.lastIndexOf('/');
        int questionMark = url.indexOf('?', lastSlash);

        if (questionMark > 0) {
            return url.substring(lastSlash + 1, questionMark);
        } else {
            return url.substring(lastSlash + 1);
        }
    }

    /**
     * 初始化数据库表结构
     * 如果你有 SQL 初始化脚本,可以在这里执行
     */
    private void initializeSchema() {
        try {
            logger.info("开始初始化数据库表结构...");

            // 检查是否存在初始化脚本
            ClassPathResource schemaResource = new ClassPathResource("schema.sql");

            if (schemaResource.exists()) {
                // 读取并预处理脚本，过滤掉 psql 特有的 '\' 指令（如 \restrict）
                StringBuilder processedSql = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(schemaResource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmedLine = line.trim();
                        // 过滤以 \ 开头的 psql 命令行
                        // 同时过滤 PostgreSQL 14 不支持的 transaction_timeout 设置 (pg_dump 18 生成的)
                        if (!trimmedLine.startsWith("\\") && !trimmedLine.toLowerCase().startsWith("set transaction_timeout")) {
                            processedSql.append(line).append("\n");
                        } else {
                            logger.debug("过滤不支持的指令: {}", line);
                        }
                    }
                }

                try (Connection bootstrapConn = DriverManager.getConnection(datasourceUrl, username, password)) {
                    // 使用 ResourceDatabasePopulator 来处理脚本执行，方便设置分隔符
                    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                    byte[] sqlBytes = processedSql.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    populator.addScript(new org.springframework.core.io.ByteArrayResource(sqlBytes));
                    // 设置分隔符为 EOF，确保函数体不被拆分
                    populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
                    populator.populate(bootstrapConn);
                }
                logger.info("数据库表结构初始化完成");
            } else {
                logger.info("未找到 schema.sql 文件,跳过表结构初始化");
                logger.info("如果需要自动初始化表结构,请在 src/main/resources 目录下创建 schema.sql 文件");
            }

        } catch (Exception e) {
            logger.error("表结构初始化失败", e);
            throw new RuntimeException("表结构初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断 public schema 是否为空（没有业务表）
     */
    private boolean isPublicSchemaEmpty() {
        String query = "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'";

        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                long tableCount = rs.getLong(1);
                logger.info("当前数据库 public schema 表数量: {}", tableCount);
                return tableCount == 0;
            }

            return true;
        } catch (Exception e) {
            logger.error("检查数据库是否为空失败", e);
            throw new RuntimeException("检查数据库是否为空失败: " + e.getMessage(), e);
        }
    }

    private void ensureUserLanguageModeColumn() {
        String alterSql = "ALTER TABLE public.sys_user ADD COLUMN IF NOT EXISTS language_mode varchar(16) DEFAULT 'AUTO'";
        String backfillSql = "UPDATE public.sys_user SET language_mode = 'AUTO' WHERE language_mode IS NULL OR trim(language_mode) = ''";
        String commentSql = "COMMENT ON COLUMN public.sys_user.language_mode IS '语言模式（AUTO/zh-CN/en-US）'";
        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
            stmt.executeUpdate(backfillSql);
            stmt.execute(commentSql);
            logger.info("已完成 sys_user.language_mode 字段检查与补齐");
        } catch (Exception e) {
            logger.error("补齐 sys_user.language_mode 字段失败", e);
            throw new RuntimeException("补齐 sys_user.language_mode 字段失败: " + e.getMessage(), e);
        }
    }

    private void ensureNotificationI18nColumns() {
        String[] sqlList = new String[] {
                "ALTER TABLE public.kb_doc_notification ADD COLUMN IF NOT EXISTS title_key varchar(128)",
                "ALTER TABLE public.kb_doc_notification ADD COLUMN IF NOT EXISTS content_key varchar(128)",
                "ALTER TABLE public.kb_doc_notification ADD COLUMN IF NOT EXISTS template_params text",
                "ALTER TABLE public.kb_doc_notification ADD COLUMN IF NOT EXISTS channel varchar(16) DEFAULT 'IN_APP'",
                "COMMENT ON COLUMN public.kb_doc_notification.title_key IS '通知标题模板键'",
                "COMMENT ON COLUMN public.kb_doc_notification.content_key IS '通知正文模板键'",
                "COMMENT ON COLUMN public.kb_doc_notification.template_params IS '通知模板参数JSON'",
                "COMMENT ON COLUMN public.kb_doc_notification.channel IS '通知通道：IN_APP/EMAIL'" };
        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement()) {
            for (String sql : sqlList) {
                stmt.execute(sql);
            }
            logger.info("已完成 kb_doc_notification 国际化字段检查与补齐");
        } catch (Exception e) {
            logger.error("补齐 kb_doc_notification 国际化字段失败", e);
            throw new RuntimeException("补齐 kb_doc_notification 国际化字段失败: " + e.getMessage(), e);
        }
    }

    private void ensureDictI18nColumn() {
        String alterSql = "ALTER TABLE public.sys_dict_data ADD COLUMN IF NOT EXISTS label_i18n text";
        String commentSql = "COMMENT ON COLUMN public.sys_dict_data.label_i18n IS '多语言标签JSON'";
        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
            stmt.execute(commentSql);
            logger.info("已完成 sys_dict_data.label_i18n 字段检查与补齐");
        } catch (Exception e) {
            logger.error("补齐 sys_dict_data.label_i18n 字段失败", e);
            throw new RuntimeException("补齐 sys_dict_data.label_i18n 字段失败: " + e.getMessage(), e);
        }
    }

    private void ensureSysConfigI18nColumns() {
        String[] sqlList = new String[] {
                "ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS config_name_i18n text",
                "ALTER TABLE public.sys_config ADD COLUMN IF NOT EXISTS description_i18n text"
        };
        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement()) {
            for (String sql : sqlList) {
                stmt.execute(sql);
            }
            logger.info("已完成 sys_config 国际化字段检查与补齐");
        } catch (Exception e) {
            logger.error("补齐 sys_config 国际化字段失败", e);
            throw new RuntimeException("补齐 sys_config 国际化字段失败: " + e.getMessage(), e);
        }
    }

    private void ensureRefreshTokenTable() {
        String[] sqlList = new String[] {
                "CREATE TABLE IF NOT EXISTS public.sys_refresh_token (" +
                        "id BIGSERIAL PRIMARY KEY," +
                        "user_id BIGINT NOT NULL," +
                        "jti VARCHAR(64) NOT NULL," +
                        "family_id VARCHAR(64) NOT NULL," +
                        "parent_jti VARCHAR(64)," +
                        "issued_at TIMESTAMP WITHOUT TIME ZONE NOT NULL," +
                        "expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL," +
                        "used_at TIMESTAMP WITHOUT TIME ZONE," +
                        "revoked_at TIMESTAMP WITHOUT TIME ZONE," +
                        "replaced_by_jti VARCHAR(64)," +
                        "revoked_reason VARCHAR(64)," +
                        "create_time TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP," +
                        "CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id) ON DELETE CASCADE" +
                        ")",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_token_jti ON public.sys_refresh_token(jti)",
                "CREATE INDEX IF NOT EXISTS idx_refresh_token_family ON public.sys_refresh_token(family_id)",
                "CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON public.sys_refresh_token(user_id)",
                "CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON public.sys_refresh_token(expires_at)"
        };
        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement()) {
            for (String sql : sqlList) {
                stmt.execute(sql);
            }
            logger.info("已完成 sys_refresh_token 表检查与补齐");
        } catch (Exception e) {
            logger.error("补齐 sys_refresh_token 表失败", e);
            throw new RuntimeException("补齐 sys_refresh_token 表失败: " + e.getMessage(), e);
        }
    }

    private void ensureDocumentExtraMetaColumn() {
        String alterSql = "ALTER TABLE public.kb_document ADD COLUMN IF NOT EXISTS extra_meta jsonb";
        String commentSql = "COMMENT ON COLUMN public.kb_document.extra_meta IS '文档扩展元数据JSON'";
        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
            stmt.execute(commentSql);
            logger.info("已完成 kb_document.extra_meta 字段检查与补齐");
        } catch (Exception e) {
            logger.error("补齐 kb_document.extra_meta 字段失败", e);
            throw new RuntimeException("补齐 kb_document.extra_meta 字段失败: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化角色数据
     * 创建 admin 和 user 两个基础角色
     */
    private void initializeRoles() {
        if (roleRepository == null) {
            logger.warn("RoleRepository 未注入,跳过角色初始化");
            return;
        }
        if (transactionManager == null) {
            throw new RuntimeException("PlatformTransactionManager 未注入,无法初始化角色");
        }

        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                // 检查并创建 admin 角色
                if (!roleRepository.findByRoleCode("admin").isPresent()) {
                    SysRole adminRole = new SysRole();
                    adminRole.setRoleCode("admin");
                    adminRole.setRoleName("管理员");
                    adminRole.setStatus((short) 0);
                    adminRole.setCreateTime(Instant.now());
                    roleRepository.save(adminRole);
                    logger.info("角色 'admin' 创建成功");
                } else {
                    logger.info("角色 'admin' 已存在,跳过创建");
                }

                // 检查并创建 user 角色
                if (!roleRepository.findByRoleCode("user").isPresent()) {
                    SysRole userRole = new SysRole();
                    userRole.setRoleCode("user");
                    userRole.setRoleName("普通用户");
                    userRole.setStatus((short) 0);
                    userRole.setCreateTime(Instant.now());
                    roleRepository.save(userRole);
                    logger.info("角色 'user' 创建成功");
                } else {
                    logger.info("角色 'user' 已存在,跳过创建");
                }

                SysRole superAdminRole = roleRepository.findByRoleCode("SUPER_ADMIN")
                        .or(() -> roleRepository.findByRoleCode("super_admin"))
                        .orElse(null);

                if (superAdminRole != null) {
                    SysRole adminRole = roleRepository.findByRoleCode("admin").orElse(null);
                    if (adminRole == null) {
                        superAdminRole.setRoleCode("admin");
                        superAdminRole.setRoleName("管理员");
                        roleRepository.save(superAdminRole);
                        logger.info("角色 'SUPER_ADMIN' 已迁移为 'admin'");
                    } else if (userRoleRepository == null || rolePermissionRepository == null) {
                        logger.warn("UserRoleRepository 或 RolePermissionRepository 未注入,无法清理 'SUPER_ADMIN' 角色");
                    } else {
                        List<SysUserRole> userRoles = userRoleRepository.findByIdRoleId(superAdminRole.getId());
                        for (SysUserRole userRole : userRoles) {
                            SysUserRoleId newId = new SysUserRoleId();
                            newId.setUserId(userRole.getId().getUserId());
                            newId.setRoleId(adminRole.getId());
                            if (!userRoleRepository.existsById(newId)) {
                                SysUserRole newUserRole = new SysUserRole();
                                newUserRole.setId(newId);
                                newUserRole.setUser(userRole.getUser());
                                newUserRole.setRole(adminRole);
                                userRoleRepository.save(newUserRole);
                            }
                            userRoleRepository.deleteById(userRole.getId());
                        }

                        List<SysRolePermission> rolePerms = rolePermissionRepository.findByIdRoleId(superAdminRole.getId());
                        for (SysRolePermission rolePerm : rolePerms) {
                            SysRolePermissionId newId = new SysRolePermissionId();
                            newId.setRoleId(adminRole.getId());
                            newId.setPermId(rolePerm.getId().getPermId());
                            if (!rolePermissionRepository.existsById(newId)) {
                                SysRolePermission newRolePerm = new SysRolePermission();
                                newRolePerm.setId(newId);
                                newRolePerm.setRole(adminRole);
                                newRolePerm.setPerm(rolePerm.getPerm());
                                rolePermissionRepository.save(newRolePerm);
                            }
                            rolePermissionRepository.deleteById(rolePerm.getId());
                        }

                        roleRepository.deleteById(superAdminRole.getId());
                        logger.info("角色 'SUPER_ADMIN' 已清理完成");
                    }
                }
            });

        } catch (Exception e) {
            logger.error("初始化角色失败", e);
            throw new RuntimeException("初始化角色失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建管理员账户
     * 如果 admin 用户不存在,则创建一个默认管理员账户
     */
    private void createAdminUser() {
        if (userService == null) {
            logger.warn("UserService 未注入,跳过管理员账户创建");
            return;
        }

        try {
            // 检查 admin 用户是否已存在
            if (userService.getUserByUsername("admin").isPresent()) {
                logger.info("管理员账户 'admin' 已存在,跳过创建");
                return;
            }

            if (!StringUtils.hasText(adminPassword)) {
                throw new IllegalStateException("admin.password 未配置，请通过 ADMIN_PASSWORD 环境变量显式提供初始管理员密码");
            }

            // 创建管理员用户
            SysUser adminUser = new SysUser();
            adminUser.setNickname("系统管理员");
            adminUser.setRealName("Administrator");
            adminUser.setEmail("admin@hellodoc.local");

            userService.createUser(adminUser, "admin", adminPassword, "admin");
            logger.info("管理员账户创建成功: username=admin");

        } catch (Exception e) {
            logger.error("创建管理员账户失败", e);
            throw new RuntimeException("创建管理员账户失败: " + e.getMessage(), e);
        }
    }
    /**
     * 初始化系统配置项
     */
    private void initializeConfigs() {
        if (configService == null) {
            logger.warn("ConfigService 未注入,跳过配置初始化");
            return;
        }

        try {
            // 初始化留言功能开关
            String guestbookKey = "app.enable_guestbook";
            if (!configRepository.existsByConfigKey(guestbookKey)) {
                com.nopkg.hellodoc.entities.SysConfig config = new com.nopkg.hellodoc.entities.SysConfig();
                config.setConfigKey(guestbookKey);
                config.setConfigName("开启留言功能");
                config.setConfigNameI18n(java.util.Map.of("zh-CN", "开启留言功能", "en-US", "Enable Guestbook"));
                config.setConfigValue("true");
                config.setValueType("boolean");
                config.setDescription("是否开启系统留言板功能");
                config.setDescriptionI18n(java.util.Map.of("zh-CN", "是否开启系统留言板功能", "en-US", "Whether to enable system guestbook"));
                config.setConfigGroup("app");
                config.setIsSystem(true);
                config.setIsFrontend(true);
                config.setStatus((short) 0);
                configService.createConfig(config);
                logger.info("配置项 '{}' 初始化成功", guestbookKey);
            }

            String aiAgentKey = "ai.openai.agent";
            if (!configRepository.existsByConfigKey(aiAgentKey)) {
                com.nopkg.hellodoc.entities.SysConfig config = new com.nopkg.hellodoc.entities.SysConfig();
                config.setConfigKey(aiAgentKey);
                config.setConfigName("AI Agent 提示词");
                config.setConfigNameI18n(java.util.Map.of("zh-CN", "AI Agent 提示词", "en-US", "AI Agent Prompt"));
                config.setConfigValue("");
                config.setValueType("string");
                config.setDescription("AI 助手的系统提示词，留空时默认使用 YAML 配置值");
                config.setDescriptionI18n(java.util.Map.of("zh-CN", "AI 助手的系统提示词，留空时默认使用 YAML 配置值",
                        "en-US", "System prompt for the AI assistant; when empty, it falls back to the YAML value"));
                config.setConfigGroup("ai");
                config.setIsSystem(true);
                config.setIsFrontend(false);
                config.setStatus((short) 0);
                configService.createConfig(config);
                logger.info("配置项 '{}' 初始化成功", aiAgentKey);
            }
        } catch (Exception e) {
            logger.error("初始化系统配置失败", e);
        }
    }

    @Autowired(required = false)
    private com.nopkg.hellodoc.services.ConfigService configService;

    @Autowired(required = false)
    private com.nopkg.hellodoc.repositories.SysConfigRepository configRepository;
}
