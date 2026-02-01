package com.destinai.unit.auth;

import com.destinai.api.service.auth.AuthService;
import com.destinai.api.service.model.User;
import com.destinai.common.errors.BadRequestException;
import com.destinai.common.errors.UnauthorizedException;
import com.destinai.modules.auth.integration.OtpSender;
import com.destinai.modules.auth.persistence.OtpTokenEntity;
import com.destinai.modules.auth.persistence.OtpTokenRepository;
import com.destinai.modules.auth.persistence.SessionEntity;
import com.destinai.modules.auth.persistence.SessionRepository;
import com.destinai.modules.users.persistence.UserEntity;
import com.destinai.modules.users.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AuthServiceTest {
	private OtpTokenRepository otpTokenRepository;
	private UserRepository userRepository;
	private SessionRepository sessionRepository;
	private OtpSender otpSender;
	private AuthService authService;

	@BeforeEach
	void setUp() {
		otpTokenRepository = Mockito.mock(OtpTokenRepository.class);
		userRepository = Mockito.mock(UserRepository.class);
		sessionRepository = Mockito.mock(SessionRepository.class);
		otpSender = Mockito.mock(OtpSender.class);
		authService = new AuthService(otpTokenRepository, userRepository, sessionRepository, otpSender);
	}

	@Test
	void issuesAndVerifiesOtp() {
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

	@Test
	void verifyOtpRejectsExpiredTokens() {
		String email = "test@example.com";
		String code = "123456";

		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(List.of());

		Assertions.assertThrows(UnauthorizedException.class,
				() -> authService.verifyOtp(email, code, null));
	}

	@Test
	void verifyOtpConsumesTokenOnlyOnce() {
		String email = "test@example.com";
		String code = "123456";
		OtpTokenEntity token = createOtpToken(email, code, Instant.now().plus(Duration.ofMinutes(10)));

		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(List.of(token));
		Mockito.when(otpTokenRepository.consumeIfActive(Mockito.eq(token.getId()), Mockito.any(), Mockito.any()))
				.thenReturn(1)
				.thenReturn(0); // Second call returns 0 (already consumed)

		Mockito.when(userRepository.findByEmailIgnoreCase(Mockito.anyString()))
				.thenReturn(Optional.of(new UserEntity(UUID.randomUUID(), email.toLowerCase(), Instant.now())));
		Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		// First verification succeeds
		authService.verifyOtp(email, code, null);

		// Second verification fails (token already consumed)
		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(List.of());
		Assertions.assertThrows(UnauthorizedException.class,
				() -> authService.verifyOtp(email, code, null));
	}

	@Test
	void verifyOtpRejectsWrongCode() {
		String email = "test@example.com";
		String correctCode = "123456";
		String wrongCode = "999999";
		OtpTokenEntity token = createOtpToken(email, correctCode, Instant.now().plus(Duration.ofMinutes(10)));

		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(List.of(token));
		Mockito.when(otpTokenRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Assertions.assertThrows(UnauthorizedException.class,
				() -> authService.verifyOtp(email, wrongCode, null));
		Mockito.verify(otpTokenRepository).save(Mockito.argThat(entity ->
				entity.getAttemptCount() == 1));
	}

	@Test
	void verifyOtpInvalidatesAfterMaxAttempts() {
		String email = "test@example.com";
		String wrongCode = "999999";
		OtpTokenEntity token = createOtpToken(email, "123456", Instant.now().plus(Duration.ofMinutes(10)));
		token.setAttemptCount(4); // One attempt away from max

		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(List.of(token));
		Mockito.when(otpTokenRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Assertions.assertThrows(UnauthorizedException.class,
				() -> authService.verifyOtp(email, wrongCode, null));

		ArgumentCaptor<OtpTokenEntity> captor = ArgumentCaptor.forClass(OtpTokenEntity.class);
		Mockito.verify(otpTokenRepository).save(captor.capture());
		OtpTokenEntity saved = captor.getValue();
		Assertions.assertEquals(5, saved.getAttemptCount());
		Assertions.assertNotNull(saved.getConsumedAt());
	}

	@Test
	void requestOtpEnforcesCooldown() {
		String email = "test@example.com";
		Instant recentTime = Instant.now().minus(Duration.ofSeconds(30));
		OtpTokenEntity recentToken = createOtpToken(email, "123456", Instant.now().plus(Duration.ofMinutes(10)));
		recentToken = new OtpTokenEntity(
				recentToken.getId(),
				recentToken.getEmail(),
				recentToken.getCodeHash(),
				recentToken.getTokenHash(),
				recentToken.getRequestIpHash(),
				recentToken.getExpiresAt(),
				recentToken.getConsumedAt(),
				recentToken.getAttemptCount(),
				recentTime
		);

		Mockito.when(otpTokenRepository.findLatestByEmail(Mockito.eq(email.toLowerCase())))
				.thenReturn(List.of(recentToken));

		Assertions.assertThrows(BadRequestException.class,
				() -> authService.requestOtp(email, "127.0.0.1"));
	}

	@Test
	void requestOtpEnforcesEmailRateLimit() {
		String email = "test@example.com";
		Mockito.when(otpTokenRepository.findLatestByEmail(Mockito.eq(email.toLowerCase())))
				.thenReturn(List.of());
		Mockito.when(otpTokenRepository.countByEmailSince(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(5L); // Already at limit

		Assertions.assertThrows(BadRequestException.class,
				() -> authService.requestOtp(email, "127.0.0.1"));
	}

	@Test
	void requestOtpEnforcesIpRateLimit() {
		String email = "test@example.com";
		String ipAddress = "192.168.1.1";
		Mockito.when(otpTokenRepository.findLatestByEmail(Mockito.anyString()))
				.thenReturn(List.of());
		Mockito.when(otpTokenRepository.countByEmailSince(Mockito.anyString(), Mockito.any()))
				.thenReturn(0L);
		Mockito.when(otpTokenRepository.findLatestByIpHash(Mockito.anyString()))
				.thenReturn(List.of());
		Mockito.when(otpTokenRepository.countByIpHashSince(Mockito.anyString(), Mockito.any()))
				.thenReturn(15L); // Already at IP limit

		Assertions.assertThrows(BadRequestException.class,
				() -> authService.requestOtp(email, ipAddress));
	}

	@Test
	void requestOtpEnforcesIpCooldown() {
		String email = "test@example.com";
		String ipAddress = "192.168.1.1";
		Instant recentTime = Instant.now().minus(Duration.ofSeconds(15));
		OtpTokenEntity recentToken = createOtpToken(email, "123456", Instant.now().plus(Duration.ofMinutes(10)));
		recentToken = new OtpTokenEntity(
				recentToken.getId(),
				recentToken.getEmail(),
				recentToken.getCodeHash(),
				recentToken.getTokenHash(),
				hash(ipAddress.trim()),
				recentToken.getExpiresAt(),
				recentToken.getConsumedAt(),
				recentToken.getAttemptCount(),
				recentTime
		);

		Mockito.when(otpTokenRepository.findLatestByEmail(Mockito.anyString()))
				.thenReturn(List.of());
		Mockito.when(otpTokenRepository.countByEmailSince(Mockito.anyString(), Mockito.any()))
				.thenReturn(0L);
		Mockito.when(otpTokenRepository.findLatestByIpHash(Mockito.anyString()))
				.thenReturn(List.of(recentToken));

		Assertions.assertThrows(BadRequestException.class,
				() -> authService.requestOtp(email, ipAddress));
	}

	@Test
	void normalizeEmailConvertsToLowercase() {
		String email = "Test@Example.COM";
		Mockito.when(otpTokenRepository.findLatestByEmail(Mockito.anyString()))
				.thenReturn(List.of());
		Mockito.when(otpTokenRepository.countByEmailSince(Mockito.anyString(), Mockito.any()))
				.thenReturn(0L);
		Mockito.when(otpTokenRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		authService.requestOtp(email, "127.0.0.1");

		ArgumentCaptor<OtpTokenEntity> captor = ArgumentCaptor.forClass(OtpTokenEntity.class);
		Mockito.verify(otpTokenRepository).save(captor.capture());
		Assertions.assertEquals("test@example.com", captor.getValue().getEmail());
	}

	@Test
	void getUserForSessionReturnsEmptyForNullToken() {
		Optional<User> result = authService.getUserForSession(null);
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void getUserForSessionReturnsEmptyForBlankToken() {
		Optional<User> result = authService.getUserForSession("   ");
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void getUserForSessionReturnsEmptyForExpiredSession() {
		String sessionToken = "valid-token";
		UserEntity userEntity = new UserEntity(UUID.randomUUID(), "user@example.com", Instant.now());
		SessionEntity expiredSession = new SessionEntity(
				UUID.randomUUID(),
				userEntity,
				hash(sessionToken),
				Instant.now(),
				Instant.now().minus(Duration.ofDays(1)), // Expired
				Instant.now(),
				null,
				null
		);

		Mockito.when(sessionRepository.findByTokenHash(Mockito.anyString()))
				.thenReturn(Optional.of(expiredSession));

		Optional<User> result = authService.getUserForSession(sessionToken);
		Assertions.assertTrue(result.isEmpty());
		Mockito.verify(sessionRepository).delete(expiredSession);
	}

	@Test
	void getUserForSessionUpdatesLastAccessedAt() {
		String sessionToken = "valid-token";
		UserEntity userEntity = new UserEntity(UUID.randomUUID(), "user@example.com", Instant.now());
		SessionEntity session = new SessionEntity(
				UUID.randomUUID(),
				userEntity,
				hash(sessionToken),
				Instant.now(),
				Instant.now().plus(Duration.ofDays(30)),
				Instant.now().minus(Duration.ofHours(1)),
				null,
				null
		);

		Mockito.when(sessionRepository.findByTokenHash(Mockito.anyString()))
				.thenReturn(Optional.of(session));
		Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<User> result = authService.getUserForSession(sessionToken);
		Assertions.assertTrue(result.isPresent());
		Mockito.verify(sessionRepository).save(Mockito.argThat(entity ->
				entity.getLastAccessedAt() != null &&
				entity.getLastAccessedAt().isAfter(Instant.now().minus(Duration.ofSeconds(1)))));
	}

	@Test
	void requireUserThrowsWhenNoSession() {
		Mockito.when(sessionRepository.findByTokenHash(Mockito.anyString()))
				.thenReturn(Optional.empty());

		Assertions.assertThrows(UnauthorizedException.class,
				() -> authService.requireUser("invalid-token"));
	}

	@Test
	void logoutDeletesSession() {
		String sessionToken = "valid-token";
		authService.logout(sessionToken);
		Mockito.verify(sessionRepository).deleteByTokenHash(hash(sessionToken));
	}

	@Test
	void logoutHandlesNullToken() {
		authService.logout(null);
		Mockito.verify(sessionRepository, Mockito.never()).deleteByTokenHash(Mockito.anyString());
	}

	@Test
	void logoutHandlesBlankToken() {
		authService.logout("   ");
		Mockito.verify(sessionRepository, Mockito.never()).deleteByTokenHash(Mockito.anyString());
	}

	@Test
	void verifyOtpCreatesUserIfNotExists() {
		String email = "newuser@example.com";
		String code = "123456";
		OtpTokenEntity token = createOtpToken(email, code, Instant.now().plus(Duration.ofMinutes(10)));

		Mockito.when(otpTokenRepository.findActiveByEmail(Mockito.eq(email.toLowerCase()), Mockito.any()))
				.thenReturn(List.of(token));
		Mockito.when(otpTokenRepository.consumeIfActive(Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(1);
		Mockito.when(userRepository.findByEmailIgnoreCase(Mockito.eq(email.toLowerCase())))
				.thenReturn(Optional.empty());
		UserEntity newUser = new UserEntity(UUID.randomUUID(), email.toLowerCase(), Instant.now());
		Mockito.when(userRepository.save(Mockito.any())).thenReturn(newUser);
		Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		AuthService.AuthResult result = authService.verifyOtp(email, code, null);
		Assertions.assertNotNull(result.user());
		Mockito.verify(userRepository).save(Mockito.any());
	}

	private OtpTokenEntity createOtpToken(String email, String code, Instant expiresAt) {
		String codeHash = hash(code);
		String tokenHash = hash(UUID.randomUUID().toString());
		return new OtpTokenEntity(
				UUID.randomUUID(),
				email.toLowerCase(),
				codeHash,
				tokenHash,
				null,
				expiresAt,
				null,
				0,
				Instant.now()
		);
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder();
			for (byte b : hashed) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}

