package com.nopkg.hellodoc.config;

import com.nopkg.hellodoc.entities.SysUser;
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
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库初始化器
 * 应用启动时自动检查数据库是否存在并初始化 schema.sql，同时根据环境变量创建初始管理员账户
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

    @PostConstruct
    public void init() {
        try {
            String databaseName = extractDatabaseName(datasourceUrl);
            String baseUrl = datasourceUrl.substring(0, datasourceUrl.lastIndexOf('/'));

            logger.info("检查数据库 '{}' 是否存在...", databaseName);

            // 连接 postgres 基础库检查目标数据库是否存在
            String postgresUrl = baseUrl + "/postgres";

            try (Connection conn = DriverManager.getConnection(postgresUrl, username, password)) {
                String checkDbQuery = "SELECT 1 FROM pg_database WHERE datname = ?";
                try (PreparedStatement ps = conn.prepareStatement(checkDbQuery)) {
                    ps.setString(1, databaseName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            String errorMsg = String.format(
                                    "Database '%s' does not exist! Please manually create the database, e.g.: CREATE DATABASE %s;",
                                    databaseName, databaseName);
                            logger.error(errorMsg);
                            throw new RuntimeException(errorMsg);
                        } else {
                            logger.info("数据库 '{}' 已存在", databaseName);
                            if (isPublicSchemaEmpty()) {
                                logger.info("数据库 '{}' 为空，开始执行 schema.sql 初始化表结构与基础数据...", databaseName);
                                initializeSchema();
                            }
                        }
                    }
                }
            }

            // 初始化管理员账户（使用 ADMIN_PASSWORD 环境变量的加密密码）
            createAdminUser();

            // 初始化默认本地存储配置 (防止文件并发上传时缺少 id=1 引起外键约束错误)
            checkAndCreateDefaultStorageConfig();

        } catch (Exception e) {
            logger.error("Database initialization failed", e);
            throw new RuntimeException("Database initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * 检查并补齐默认本地存储配置 (id=1)
     */
    private void checkAndCreateDefaultStorageConfig() {
        String checkQuery = "SELECT COUNT(*) FROM kb_storage_config WHERE id = 1";
        String insertQuery = "INSERT INTO kb_storage_config (id, name, provider, is_default, created_at, updated_at) " +
                "VALUES (1, 'Default Local Storage', 'local', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkQuery)) {

            if (rs.next() && rs.getLong(1) == 0) {
                stmt.executeUpdate(insertQuery);
                logger.info("已完成默认本地存储配置 (id=1) 自动检查与补齐");
            }
        } catch (Exception e) {
            logger.error("检查/补齐默认存储配置失败", e);
        }
    }

    /**
     * 从 JDBC URL 中提取数据库名称
     */
    private String extractDatabaseName(String url) {
        int lastSlash = url.lastIndexOf('/');
        int questionMark = url.indexOf('?', lastSlash);

        if (questionMark > 0) {
            return url.substring(lastSlash + 1, questionMark);
        } else {
            return url.substring(lastSlash + 1);
        }
    }

    /**
     * 初始化数据库表结构与基础数据
     */
    private void initializeSchema() {
        try {
            ClassPathResource schemaResource = new ClassPathResource("schema.sql");

            if (schemaResource.exists()) {
                StringBuilder processedSql = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(schemaResource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmedLine = line.trim();
                        if (!trimmedLine.startsWith("\\") &&
                            !trimmedLine.toLowerCase().startsWith("set transaction_timeout")) {
                            processedSql.append(line).append("\n");
                        }



                    }
                }

                try (Connection bootstrapConn = DriverManager.getConnection(datasourceUrl, username, password)) {
                    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                    byte[] sqlBytes = processedSql.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    populator.addScript(new org.springframework.core.io.ByteArrayResource(sqlBytes));
                    populator.setSeparator(";\n");
                    populator.populate(bootstrapConn);
                }
                logger.info("数据库 schema.sql 导入完成");


            } else {
                logger.warn("未找到 schema.sql 文件，跳过表结构初始化");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize schema", e);
            throw new RuntimeException("Schema initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * 判断业务表结构是否未初始化（检查核心业务表 sys_user 是否存在）
     */
    private boolean isPublicSchemaEmpty() {
        String query = "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = 'sys_user'";

        try (Connection conn = DriverManager.getConnection(datasourceUrl, username, password);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                long count = rs.getLong(1);
                logger.info("检查核心业务表 sys_user 结果 count = {}", count);
                return count == 0;
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to check business table existence", e);
            throw new RuntimeException("Failed to check business table existence: " + e.getMessage(), e);
        }
    }


    /**
     * 创建管理员账户
     * 如果 admin 用户不存在，基于环境变量 ADMIN_PASSWORD 动态加密创建默认管理员账户
     */
    private void createAdminUser() {
        if (userService == null) {
            logger.warn("UserService 未注入，跳过管理员账户创建");
            return;
        }

        try {
            if (userService.getUserByUsername("admin").isPresent()) {
                logger.info("管理员账户 'admin' 已存在，跳过初始创建以保护用户自定义密码");
                return;
            }

            if (!StringUtils.hasText(adminPassword)) {
                throw new IllegalStateException("admin.password is not configured, please explicitly provide initial admin password via ADMIN_PASSWORD environment variable");
            }

            SysUser adminUser = new SysUser();
            adminUser.setNickname("System Administrator");
            adminUser.setRealName("Administrator");
            adminUser.setEmail("admin@hellodoc.local");

            userService.createUser(adminUser, "admin", adminPassword, "admin");
            logger.info("Initial admin account created successfully: username=admin");

        } catch (Exception e) {
            logger.error("Failed to create admin user", e);
            throw new RuntimeException("Failed to create admin user: " + e.getMessage(), e);
        }
    }


}
