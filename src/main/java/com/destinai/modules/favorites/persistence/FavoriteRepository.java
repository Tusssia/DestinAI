package com.destinai.modules.favorites.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, UUID> {
	Page<FavoriteEntity> findByUserId(UUID userId, Pageable pageable);

	Page<FavoriteEntity> findByUserIdAndCountryContainingIgnoreCase(UUID userId, String country, Pageable pageable);

	Optional<FavoriteEntity> findByUserIdAndCountryIgnoreCase(UUID userId, String country);

	Optional<FavoriteEntity> findByUserIdAndId(UUID userId, UUID id);

	long countByUserId(UUID userId);
}

