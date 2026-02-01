package com.destinai.api.service.auth;

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
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private static final int MAX_ATTEMPTS = 5;
	private static final Duration OTP_TTL = Duration.ofMinutes(10);
	private static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
	private static final Duration OTP_LIMIT_WINDOW = Duration.ofHours(1);
	private static final int OTP_MAX_PER_WINDOW = 5;
	private static final Duration OTP_IP_COOLDOWN = Duration.ofSeconds(30);
	private static final Duration OTP_IP_LIMIT_WINDOW = Duration.ofHours(1);
	private static final int OTP_MAX_PER_IP_WINDOW = 15;

	private final SecureRandom secureRandom = new SecureRandom();
	private final OtpTokenRepository otpTokenRepository;
	private final UserRepository userRepository;
	private final SessionRepository sessionRepository;
	private final OtpSender otpSender;

	public AuthService(OtpTokenRepository otpTokenRepository, UserRepository userRepository,
			SessionRepository sessionRepository, OtpSender otpSender) {
		this.otpTokenRepository = otpTokenRepository;
		this.userRepository = userRepository;
		this.sessionRepository = sessionRepository;
		this.otpSender = otpSender;
	}

	@Transactional
	public void requestOtp(String email, String ipAddress) {
		String normalizedEmail = normalizeEmail(email);
		String ipHash = normalizeIpHash(ipAddress);
		enforceOtpRateLimits(normalizedEmail, ipHash);
		String code = generateCode();
		String token = UUID.randomUUID().toString();

		OtpTokenEntity entity = new OtpTokenEntity(
				UUID.randomUUID(),
				normalizedEmail,
				hash(code),
				hash(token),
				ipHash,
				Instant.now().plus(OTP_TTL),
				null,
				0,
				Instant.now()
		);
		otpTokenRepository.save(entity);
		otpSender.sendOtp(normalizedEmail, code, token);
		log.info("Issued OTP for {}", normalizedEmail);
	}

	@Transactional
	public AuthResult verifyOtp(String email, String code, String token) {
		String normalizedEmail = normalizeEmail(email);
		List<OtpTokenEntity> activeTokens = otpTokenRepository.findActiveByEmail(normalizedEmail, Instant.now());
		if (activeTokens.isEmpty()) {
			throw new UnauthorizedException("Invalid or expired OTP.");
		}
		OtpTokenEntity record = activeTokens.getFirst();

		boolean matches = matches(record, code, token);
		if (!matches) {
			int attempts = record.getAttemptCount() + 1;
			record.setAttemptCount(attempts);
			if (attempts >= MAX_ATTEMPTS) {
				record.setConsumedAt(Instant.now());
			}
			otpTokenRepository.save(record);
			throw new UnauthorizedException("Invalid or expired OTP.");
		}

		int updated = otpTokenRepository.consumeIfActive(record.getId(), Instant.now(), Instant.now());
		if (updated == 0) {
			throw new UnauthorizedException("Invalid or expired OTP.");
		}

		UserEntity userEntity = userRepository.findByEmailIgnoreCase(normalizedEmail)
				.orElseGet(() -> userRepository.save(new UserEntity(
						UUID.randomUUID(),
						normalizedEmail,
						Instant.now()
				)));
		User user = new User(userEntity.getId(), userEntity.getEmail());

		String sessionToken = UUID.randomUUID().toString();
		sessionRepository.save(new SessionEntity(
				UUID.randomUUID(),
				userEntity,
				hash(sessionToken),
				Instant.now(),
				Instant.now().plus(Duration.ofDays(30)),
				Instant.now(),
				null,
				null
		));
		return new AuthResult(user, sessionToken);
	}

	public Optional<User> getUserForSession(String sessionToken) {
		if (sessionToken == null || sessionToken.isBlank()) {
			return Optional.empty();
		}
		Optional<SessionEntity> session = sessionRepository.findByTokenHash(hash(sessionToken));
		if (session.isEmpty()) {
			return Optional.empty();
		}
		SessionEntity entity = session.get();
		if (entity.getExpiresAt().isBefore(Instant.now())) {
			sessionRepository.delete(entity);
			return Optional.empty();
		}
		entity.setLastAccessedAt(Instant.now());
		sessionRepository.save(entity);
		UserEntity user = entity.getUser();
		return Optional.of(new User(user.getId(), user.getEmail()));
	}

	public User requireUser(String sessionToken) {
		return getUserForSession(sessionToken)
				.orElseThrow(() -> new UnauthorizedException("Authentication required."));
	}

	public void logout(String sessionToken) {
		if (sessionToken == null || sessionToken.isBlank()) {
			return;
		}
		sessionRepository.deleteByTokenHash(hash(sessionToken));
	}

	private boolean matches(OtpTokenEntity record, String code, String token) {
		if (code != null && !code.isBlank()) {
			return record.getCodeHash() != null && record.getCodeHash().equals(hash(code));
		}
		if (token != null && !token.isBlank()) {
			return record.getTokenHash().equals(hash(token));
		}
		return false;
	}

	private String generateCode() {
		int code = secureRandom.nextInt(1_000_000);
		return String.format("%06d", code);
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}

	private String normalizeIpHash(String ipAddress) {
		if (ipAddress == null || ipAddress.isBlank()) {
			return null;
		}
		return hash(ipAddress.trim());
	}

	private void enforceOtpRateLimits(String email, String ipHash) {
		Instant now = Instant.now();
		List<OtpTokenEntity> latest = otpTokenRepository.findLatestByEmail(email);
		if (!latest.isEmpty() && latest.getFirst().getCreatedAt().isAfter(now.minus(OTP_COOLDOWN))) {
			throw new BadRequestException("OTP recently issued. Please wait before retrying.");
		}
		long issuedInWindow = otpTokenRepository.countByEmailSince(email, now.minus(OTP_LIMIT_WINDOW));
		if (issuedInWindow >= OTP_MAX_PER_WINDOW) {
			throw new BadRequestException("OTP request limit reached. Try again later.");
		}
		if (ipHash != null) {
			List<OtpTokenEntity> latestByIp = otpTokenRepository.findLatestByIpHash(ipHash);
			if (!latestByIp.isEmpty() && latestByIp.getFirst().getCreatedAt().isAfter(now.minus(OTP_IP_COOLDOWN))) {
				throw new BadRequestException("OTP recently issued from this network. Please wait before retrying.");
			}
			long issuedByIp = otpTokenRepository.countByIpHashSince(ipHash, now.minus(OTP_IP_LIMIT_WINDOW));
			if (issuedByIp >= OTP_MAX_PER_IP_WINDOW) {
				throw new BadRequestException("OTP request limit reached for this network. Try again later.");
			}
		}
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
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	public record AuthResult(User user, String sessionToken) {
	}
}

