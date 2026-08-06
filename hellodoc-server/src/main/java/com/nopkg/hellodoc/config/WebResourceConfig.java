package com.nopkg.hellodoc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Web 资源相关配置：
 * 1. 兼容 swagger-ui 入口重定向；
 * 2. 配置静态资源缓存策略；
 * 3. 支持视频/大文件分片响应（Range 请求）。
 */
@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    /**
     * 兼容 swagger-ui 入口访问路径。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html");
    }

    /**
     * 配置静态资源处理器：
     * 1. /assets/** 映射到 classpath:/static/assets/，公共缓存 30 天；
     * 2. /index.html 映射到 classpath:/static/，禁用缓存。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }

    /**
     * 支持大文件/视频分片响应（Range 请求），提升在线播放与断点续传体验。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ResourceRegionHttpMessageConverter converter = new ResourceRegionHttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.parseMediaType("video/mp4")
        ));
        converters.add(0, converter);
    }
}
