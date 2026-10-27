package com.example.rag.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前后端分离/反向代理场景下的跨域配置。
 *
 * <p>本地默认允许 Vite 开发端口和 preview 端口；生产环境通过
 * {@code APP_CORS_ALLOWED_ORIGINS}（对应 app.cors.allowed-origins）覆盖为实际域名。
 * 例如：</p>
 *
 * <pre>
 * APP_CORS_ALLOWED_ORIGINS=https://mental.example.com
 * </pre>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
