package com.destinai.modules.auth.persistence;

import com.destinai.modules.users.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionEntity {
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "last_accessed_at")
	private Instant lastAccessedAt;

	@Column(name = "user_agent")
	private String userAgent;

	@Column(name = "ip_address")
	private String ipAddress;

	protected SessionEntity() {
	}

	public SessionEntity(UUID id, UserEntity user, String tokenHash, Instant createdAt, Instant expiresAt,
			Instant lastAccessedAt, String userAgent, String ipAddress) {
		this.id = id;
		this.user = user;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.lastAccessedAt = lastAccessedAt;
		this.userAgent = userAgent;
		this.ipAddress = ipAddress;
	}

	public UUID getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getLastAccessedAt() {
		return lastAccessedAt;
	}

	public void setLastAccessedAt(Instant lastAccessedAt) {
		this.lastAccessedAt = lastAccessedAt;
	}
}

