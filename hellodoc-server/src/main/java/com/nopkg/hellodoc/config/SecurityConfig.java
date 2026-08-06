package com.nopkg.hellodoc.config;

import jakarta.servlet.DispatcherType;
import com.nopkg.hellodoc.security.JwtAuthenticationFilter;
import com.nopkg.hellodoc.security.JwtTokenProvider;
import com.nopkg.hellodoc.security.Sha256PasswordEncoder;
import com.nopkg.hellodoc.services.CustomUserDetailsService;
import com.nopkg.hellodoc.filters.LanguageInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import com.nopkg.hellodoc.filters.RequestLoggingFilter;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 启用 @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 业务用户信息服务：JWT 认证成功后用于加载用户详情与权限。
     */
    private final CustomUserDetailsService userDetailsService;
    /**
     * JWT 工具：负责令牌校验、解析与认证信息构建。
     */
    private final JwtTokenProvider jwtTokenProvider;
    /**
     * 语言拦截器：在认证前后处理请求语言上下文（如 i18n 场景）。
     */
    private final LanguageInterceptor languageInterceptor;

    /**
     * 是否启用请求日志过滤器，默认启用。
     */
    @Value("${app.logging.request.enabled:true}")
    private boolean requestLoggingEnabled;

    /**
     * 是否启用 CORS，默认启用。
     */
    @Value("${app.cors.enabled:true}")
    private boolean corsEnabled;

    /**
     * 暴露 AuthenticationManager，供登录接口等认证流程注入使用。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Spring Security 主过滤链配置。
     *
     * 配置要点：
     * 1. 关闭 CSRF（当前为无状态 JWT 鉴权，不依赖 Cookie Session）。
     * 2. 关闭默认 cache-control 头写入，避免覆盖静态资源缓存策略。
     * 3. 会话策略设为 STATELESS，所有请求都基于令牌认证。
     * 4. 放行系统配置中的白名单路径，其余请求默认需要认证。
     * 5. 关闭 http basic，避免与自定义 JWT 方案混用。
     * 6. 按顺序挂载请求日志、语言、JWT 过滤器。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] extraPermit = readPermitPaths();

        if (corsEnabled) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        } else {
            http.cors(cors -> cors.disable());
        }

        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.cacheControl(cacheControl -> cacheControl.disable()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(extraPermit).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable());

        if (requestLoggingEnabled) {
            http.addFilterBefore(new RequestLoggingFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        http.addFilterBefore(languageInterceptor, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter(), LanguageInterceptor.class);

        return http.build();
    }

    /**
     * 从 classpath 下的 sys_config.yaml 读取 permit-paths 白名单。
     * 读取失败时返回空数组，确保应用可正常启动。
     */
    private String[] readPermitPaths() {
        try {
            ClassPathResource resource = new ClassPathResource("sys_config.yaml");
            if (!resource.exists()) {
                return new String[0];
            }
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            java.util.Map<String, Object> map = yaml.load(resource.getInputStream());
            if (map == null || !map.containsKey("permit-paths")) {
                return new String[0];
            }
            Object paths = map.get("permit-paths");
            if (paths instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) paths;
                return list.stream()
                        .map(Object::toString)
                        .toArray(String[]::new);
            }
            return new String[0];
        } catch (Exception e) {
            // Log error but don't crash app startup
            System.err.println("Failed to read sys_config.yaml: " + e.getMessage());
            return new String[0];
        }
    }

    /**
     * 允许跨域来源，支持逗号分隔多个域名，默认 "*"。
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * CORS 配置源：
     * - 允许常见 HTTP 方法与所有请求头；
     * - 当非通配符来源时允许携带凭证；
     * - 对所有路径生效。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Split by comma if multiple origins
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // Allow credentials if specific origin is set (not wildcard)
        if (!"*".equals(allowedOrigins)) {
            configuration.setAllowCredentials(true);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * JWT 认证过滤器：从请求中提取并校验令牌，写入安全上下文。
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    /**
     * 密码编码器策略：
     * - auth.password.encoder=sha256 时启用自定义 SHA-256 编码器；
     * - 其余情况默认使用 BCrypt。
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${auth.password.encoder:bcrypt}") String encoder,
            @Value("${auth.password.pepper:}") String pepper,
            @Value("${auth.password.sha256.output:hex}") String output) {
        if ("sha256".equalsIgnoreCase(encoder)) {
            return new Sha256PasswordEncoder(pepper, output);
        }
        return new BCryptPasswordEncoder();
    }
}
