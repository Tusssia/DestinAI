package com.destinai.unit.favorites;

import com.destinai.api.service.favorites.FavoritesService;
import com.destinai.api.service.model.User;
import com.destinai.common.errors.BadRequestException;
import com.destinai.common.errors.NotFoundException;
import com.destinai.modules.favorites.persistence.FavoriteEntity;
import com.destinai.modules.favorites.persistence.FavoriteRepository;
import com.destinai.modules.users.persistence.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FavoritesServiceTest {
	@Test
	void rejectsWhenFavoritesLimitReached() {
		FavoriteRepository favoriteRepository = Mockito.mock(FavoriteRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		FavoritesService service = new FavoritesService(favoriteRepository, userRepository);

		User user = new User(UUID.randomUUID(), "user@example.com");
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(50L);

		Assertions.assertThrows(BadRequestException.class,
				() -> service.createFavorite(user, "Portugal", "nice"));
	}

	@Test
	void updateFailsWhenFavoriteMissing() {
		FavoriteRepository favoriteRepository = Mockito.mock(FavoriteRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		FavoritesService service = new FavoritesService(favoriteRepository, userRepository);

		User user = new User(UUID.randomUUID(), "user@example.com");
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), UUID.randomUUID()))
				.thenReturn(Optional.empty());

		Assertions.assertThrows(NotFoundException.class,
				() -> service.updateFavorite(user, UUID.randomUUID(), "note"));
	}

	@Test
	void updatesNoteWhenFound() {
		FavoriteRepository favoriteRepository = Mockito.mock(FavoriteRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		FavoritesService service = new FavoritesService(favoriteRepository, userRepository);

		User user = new User(UUID.randomUUID(), "user@example.com");
		UUID favoriteId = UUID.randomUUID();
		FavoriteEntity entity = new FavoriteEntity(
				favoriteId,
				null,
				"Portugal",
				"old",
				Instant.now()
		);
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), favoriteId))
				.thenReturn(Optional.of(entity));
		Mockito.when(favoriteRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Assertions.assertEquals("new", service.updateFavorite(user, favoriteId, "new").note());
	}
}

