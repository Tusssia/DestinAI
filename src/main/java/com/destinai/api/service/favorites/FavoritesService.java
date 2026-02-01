package com.destinai.api.service.favorites;

import com.destinai.api.service.model.Favorite;
import com.destinai.api.service.model.FavoritesPage;
import com.destinai.api.service.model.User;
import com.destinai.common.errors.BadRequestException;
import com.destinai.common.errors.NotFoundException;
import com.destinai.modules.favorites.persistence.FavoriteEntity;
import com.destinai.modules.favorites.persistence.FavoriteRepository;
import com.destinai.modules.users.persistence.UserEntity;
import com.destinai.modules.users.persistence.UserRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class FavoritesService {
	private static final int MAX_FAVORITES = 50;

	private final FavoriteRepository favoriteRepository;
	private final UserRepository userRepository;

	public FavoritesService(FavoriteRepository favoriteRepository, UserRepository userRepository) {
		this.favoriteRepository = favoriteRepository;
		this.userRepository = userRepository;
	}

	public FavoritesPage listFavorites(User user, int page, int pageSize, String sort, String countryFilter) {
		Sort sortSpec = Sort.by("createdAt");
		if ("created_at_desc".equals(sort)) {
			sortSpec = sortSpec.descending();
		}
		PageRequest pageRequest = PageRequest.of(page - 1, pageSize, sortSpec);
		Page<FavoriteEntity> pageResult;
		if (countryFilter != null && !countryFilter.isBlank()) {
			pageResult = favoriteRepository.findByUserIdAndCountryContainingIgnoreCase(
					user.id(), countryFilter, pageRequest);
		} else {
			pageResult = favoriteRepository.findByUserId(user.id(), pageRequest);
		}

		List<Favorite> items = pageResult.getContent().stream()
				.map(this::toModel)
				.toList();
		return new FavoritesPage(items, page, pageSize, pageResult.getTotalElements());
	}

	@Transactional
	public Favorite createFavorite(User user, String country, String note) {
		long count = favoriteRepository.countByUserId(user.id());
		if (count >= MAX_FAVORITES) {
			throw new BadRequestException("Favorites limit reached.");
		}
		boolean exists = favoriteRepository.findByUserIdAndCountryIgnoreCase(user.id(), country).isPresent();
		if (exists) {
			throw new BadRequestException("Favorite already exists for this country.");
		}
		UserEntity userEntity = userRepository.getReferenceById(user.id());
		FavoriteEntity entity = new FavoriteEntity(
				UUID.randomUUID(),
				userEntity,
				country,
				note,
				Instant.now()
		);
		return toModel(favoriteRepository.save(entity));
	}

	@Transactional
	public Favorite updateFavorite(User user, UUID favoriteId, String note) {
		FavoriteEntity entity = favoriteRepository.findByUserIdAndId(user.id(), favoriteId)
				.orElseThrow(() -> new NotFoundException("Favorite not found."));
		entity.setNote(note);
		return toModel(favoriteRepository.save(entity));
	}

	@Transactional
	public void deleteFavorite(User user, UUID favoriteId) {
		FavoriteEntity entity = favoriteRepository.findByUserIdAndId(user.id(), favoriteId)
				.orElseThrow(() -> new NotFoundException("Favorite not found."));
		favoriteRepository.delete(entity);
	}

	private Favorite toModel(FavoriteEntity entity) {
		return new Favorite(entity.getId(), entity.getCountry(), entity.getNote(), entity.getCreatedAt());
	}
}

