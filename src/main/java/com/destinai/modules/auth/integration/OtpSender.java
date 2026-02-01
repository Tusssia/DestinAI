package com.destinai.modules.auth.integration;

public interface OtpSender {
	void sendOtp(String email, String code, String token);
}

