package com.huang.starter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MyStarterProperties.class)
@ConditionalOnClass(MyStarterService.class)
public class MyStarterAutoConfiguration {

    @Autowired
    private MyStarterProperties properties;

    @Bean
    public MyStarterService myStarterService(){
        MyStarterService service = new MyStarterService();

        service.setName(properties.getName());

        service.setProjectName(properties.getProjectName());

        return service;
    }
}
