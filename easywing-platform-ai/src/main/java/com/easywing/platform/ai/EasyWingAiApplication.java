package com.easywing.platform.ai;

import com.easywing.platform.ai.config.AiProperties;
import com.easywing.platform.ai.sandbox.SandboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.easywing.platform.ai")
@EnableConfigurationProperties({AiProperties.class, SandboxProperties.class})
public class EasyWingAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyWingAiApplication.class, args);
    }
}
