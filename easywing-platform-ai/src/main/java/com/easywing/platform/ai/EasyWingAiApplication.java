package com.easywing.platform.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.easywing.platform.ai.config.AiProperties;

@SpringBootApplication(scanBasePackages = "com.easywing.platform.ai")
@EnableConfigurationProperties(AiProperties.class)
public class EasyWingAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyWingAiApplication.class, args);
    }
}