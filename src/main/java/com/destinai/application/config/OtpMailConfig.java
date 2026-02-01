package com.destinai.application.config;

import com.destinai.modules.auth.integration.OtpMailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OtpMailProperties.class)
public class OtpMailConfig {
}

