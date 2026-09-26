package com.scheduling.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册拦截器
 *
 * RoleInterceptor 以"方法级 @RequireRole 注解"为准做授权校验,
 * 因此统一拦截所有路径即可(未标注注解的方法会被拦截器直接放行)。
 */
@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor).addPathPatterns("/**");
    }
}
