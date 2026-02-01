package com.destinai.unit.auth;

import com.destinai.api.service.auth.AuthService;
import com.destinai.modules.auth.integration.OtpSender;
import com.destinai.modules.auth.persistence.OtpTokenEntity;
import com.destinai.modules.auth.persistence.OtpTokenRepository;
import com.destinai.modules.auth.persistence.SessionRepository;
import com.destinai.modules.users.persistence.UserEntity;
import com.destinai.modules.users.persistence.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AuthServiceTest {
	@Test
	void issuesAndVerifiesOtp() {
		OtpTokenRepository otpTokenRepository = Mockito.mock(OtpTokenRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		SessionRepository sessionRepository = Mockito.mock(SessionRepository.class);
		OtpSender otpSender = Mockito.mock(OtpSender.class);

		AtomicReference<String> capturedCode = new AtomicReference<>();
		AtomicReference<String> capturedToken = new AtomicReference<>();
		Mockito.doAnswer(invocation -> {
			capturedCode.set(invocation.getArgument(1));
			capturedToken.set(invocation.getArgument(2));
			return null;
		}).when(otpSender).sendOtp(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

		Mockito.when(otpTokenRepository.findLatestByEmail(Mockito.anyString()))
				.thenReturn(List.of());
		Mockito.when(otpTokenRepository.countByEmailSince(Mockito.anyString(), Mockito.any()))
				.thenReturn(0L);
		Mockito.when(otpTokenRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		AuthService authService = new AuthService(otpTokenRepository, userRepository, sessionRepository, otpSender);
		String email = "test@example.com";
		authService.requestOtp(email, "127.0.0.1");

		ArgumentCaptor<OtpTokenEntity> tokenCaptor = ArgumentCaptor.forClass(OtpTokenEntity.class);
		Mockito.verify(otpTokenRepository).save(tokenCaptor.capture());
		OtpTokenEntity savedToken = tokenCaptor.getValue();

		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.anyString(), Mockito.any()))
				.thenReturn(List.of(savedToken));
		Mockito.when(otpTokenRepository.consumeIfActive(Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(1);
		Mockito.when(userRepository.findByEmailIgnoreCase(Mockito.anyString()))
				.thenReturn(Optional.empty());
		UserEntity userEntity = new UserEntity(UUID.randomUUID(), email.toLowerCase(), Instant.now());
		Mockito.when(userRepository.save(Mockito.any())).thenReturn(userEntity);
		Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		AuthService.AuthResult result = authService.verifyOtp(email, capturedCode.get(), capturedToken.get());
		Assertions.assertEquals(email.toLowerCase(), result.user().email());
	}
}

