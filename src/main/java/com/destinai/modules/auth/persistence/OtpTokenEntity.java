package com.destinai.modules.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_tokens")
public class OtpTokenEntity {
	@Id
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(name = "code_hash")
	private String codeHash;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "request_ip_hash")
	private String requestIpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected OtpTokenEntity() {
	}

	public OtpTokenEntity(UUID id, String email, String codeHash, String tokenHash, String requestIpHash,
			Instant expiresAt, Instant consumedAt, int attemptCount, Instant createdAt) {
		this.id = id;
		this.email = email;
		this.codeHash = codeHash;
		this.tokenHash = tokenHash;
		this.requestIpHash = requestIpHash;
		this.expiresAt = expiresAt;
		this.consumedAt = consumedAt;
		this.attemptCount = attemptCount;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public String getRequestIpHash() {
		return requestIpHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setConsumedAt(Instant consumedAt) {
		this.consumedAt = consumedAt;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}
}

