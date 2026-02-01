package com.destinai.modules.auth.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otp.mail")
public record OtpMailProperties(
		String from,
		String subject
) {
}

