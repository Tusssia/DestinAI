package com.destinai.modules.favorites.persistence;

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
@Table(name = "favorites")
public class FavoriteEntity {
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Column(nullable = false)
	private String country;

	@Column
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FavoriteEntity() {
	}

	public FavoriteEntity(UUID id, UserEntity user, String country, String note, Instant createdAt) {
		this.id = id;
		this.user = user;
		this.country = country;
		this.note = note;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UserEntity getUser() {
		return user;
	}

	public String getCountry() {
		return country;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setNote(String note) {
		this.note = note;
	}
}

