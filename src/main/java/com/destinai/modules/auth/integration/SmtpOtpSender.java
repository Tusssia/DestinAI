package com.destinai.modules.auth.integration;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpOtpSender implements OtpSender {
	private final JavaMailSender mailSender;
	private final OtpMailProperties properties;

	public SmtpOtpSender(JavaMailSender mailSender, OtpMailProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void sendOtp(String email, String code, String token) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email);
		if (properties.from() != null && !properties.from().isBlank()) {
			message.setFrom(properties.from());
		}
		message.setSubject(properties.subject() == null || properties.subject().isBlank()
				? "Your DestinAI sign-in code"
				: properties.subject());
		message.setText(buildBody(code, token));
		mailSender.send(message);
	}

	private String buildBody(String code, String token) {
		return """
				Hello,

				Your DestinAI sign-in code is:
				%s

				This code expires in 10 minutes and can be used only once.

				If you prefer, you can also sign in using this one-time token:
				%s

				If you did not request this, you can ignore this email.
				""".formatted(code, token);
	}
}

