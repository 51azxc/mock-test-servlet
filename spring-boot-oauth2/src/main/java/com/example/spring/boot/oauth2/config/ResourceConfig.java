package com.example.spring.boot.oauth2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.TokenStore;

@Configuration
@EnableResourceServer   //配置资源服务器
public class ResourceConfig extends ResourceServerConfigurerAdapter {

    private final TokenStore tokenStore;

    public ResourceConfig(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources.resourceId("resource_id").tokenStore(tokenStore);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers(HttpMethod.GET, "/users/*").access("#oauth2.hasScope('read')")
                .and()
                .headers().addHeaderWriter((request, response) -> {
                    response.addHeader("Access-Control-Allow-Origin", "*");
                    if (request.getMethod().equals("OPTIONS")) {
                        response.setHeader("Access-Control-Allow-Methods",
                                request.getHeader("Access-Control-Request-Method"));
                        response.setHeader("Access-Control-Allow-Headers",
                                request.getHeader("Access-Control-Request-Headers"));
                    }
        });
    }
}
