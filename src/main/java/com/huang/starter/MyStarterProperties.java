package com.huang.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "my-starter")
public class MyStarterProperties {
    private String name = "koshunho";

    private String projectName = "my-starter";
}
