package com.destinai.unit.favorites;

import com.destinai.api.service.favorites.FavoritesService;
import com.destinai.api.service.model.Favorite;
import com.destinai.api.service.model.FavoritesPage;
import com.destinai.api.service.model.User;
import com.destinai.common.errors.BadRequestException;
import com.destinai.common.errors.NotFoundException;
import com.destinai.modules.favorites.persistence.FavoriteEntity;
import com.destinai.modules.favorites.persistence.FavoriteRepository;
import com.destinai.modules.users.persistence.UserEntity;
import com.destinai.modules.users.persistence.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class FavoritesServiceTest {
	private FavoriteRepository favoriteRepository;
	private UserRepository userRepository;
	private FavoritesService service;
	private User user;

	@BeforeEach
	void setUp() {
		favoriteRepository = Mockito.mock(FavoriteRepository.class);
		userRepository = Mockito.mock(UserRepository.class);
		service = new FavoritesService(favoriteRepository, userRepository);
		user = new User(UUID.randomUUID(), "user@example.com");
	}

	@Test
	void rejectsWhenFavoritesLimitReached() {
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(50L);

		Assertions.assertThrows(BadRequestException.class,
				() -> service.createFavorite(user, "Portugal", "nice"));
	}

	@Test
	void rejectsWhenFavoritesLimitExactlyAtMax() {
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(50L);

		Assertions.assertThrows(BadRequestException.class,
				() -> service.createFavorite(user, "Portugal", "nice"));
	}

	@Test
	void allowsFavoriteWhenBelowLimit() {
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(49L);
		Mockito.when(favoriteRepository.findByUserIdAndCountryIgnoreCase(user.id(), "Portugal"))
				.thenReturn(Optional.empty());
		UserEntity userEntity = new UserEntity(user.id(), user.email(), Instant.now());
		Mockito.when(userRepository.getReferenceById(user.id())).thenReturn(userEntity);
		Mockito.when(favoriteRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Favorite favorite = service.createFavorite(user, "Portugal", "nice");
		Assertions.assertEquals("Portugal", favorite.country());
		Assertions.assertEquals("nice", favorite.note());
	}

	@Test
	void rejectsDuplicateFavorite() {
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(10L);
		FavoriteEntity existing = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"Portugal",
				"existing",
				Instant.now()
		);
		Mockito.when(favoriteRepository.findByUserIdAndCountryIgnoreCase(user.id(), "Portugal"))
				.thenReturn(Optional.of(existing));

		Assertions.assertThrows(BadRequestException.class,
				() -> service.createFavorite(user, "Portugal", "new note"));
	}

	@Test
	void rejectsDuplicateFavoriteCaseInsensitive() {
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(10L);
		FavoriteEntity existing = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"portugal",
				"existing",
				Instant.now()
		);
		Mockito.when(favoriteRepository.findByUserIdAndCountryIgnoreCase(user.id(), "Portugal"))
				.thenReturn(Optional.of(existing));

		Assertions.assertThrows(BadRequestException.class,
				() -> service.createFavorite(user, "Portugal", "new note"));
	}

	@Test
	void createFavoriteSavesWithCorrectUser() {
		Mockito.when(favoriteRepository.countByUserId(user.id())).thenReturn(10L);
		Mockito.when(favoriteRepository.findByUserIdAndCountryIgnoreCase(user.id(), "Portugal"))
				.thenReturn(Optional.empty());
		UserEntity userEntity = new UserEntity(user.id(), user.email(), Instant.now());
		Mockito.when(userRepository.getReferenceById(user.id())).thenReturn(userEntity);
		Mockito.when(favoriteRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.createFavorite(user, "Portugal", "nice place");

		Mockito.verify(favoriteRepository).save(Mockito.argThat(entity ->
				entity.getCountry().equals("Portugal") &&
				entity.getNote().equals("nice place") &&
				entity.getUser().getId().equals(user.id())));
	}

	@Test
	void updateFailsWhenFavoriteMissing() {
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), UUID.randomUUID()))
				.thenReturn(Optional.empty());

		Assertions.assertThrows(NotFoundException.class,
				() -> service.updateFavorite(user, UUID.randomUUID(), "note"));
	}

	@Test
	void updateFailsWhenFavoriteBelongsToDifferentUser() {
		UUID favoriteId = UUID.randomUUID();
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), favoriteId))
				.thenReturn(Optional.empty());

		Assertions.assertThrows(NotFoundException.class,
				() -> service.updateFavorite(user, favoriteId, "note"));
	}

	@Test
	void updatesNoteWhenFound() {
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

		Favorite updated = service.updateFavorite(user, favoriteId, "new");
		Assertions.assertEquals("new", updated.note());
		Mockito.verify(favoriteRepository).save(Mockito.argThat(e -> e.getNote().equals("new")));
	}

	@Test
	void deleteFavoriteRemovesWhenFound() {
		UUID favoriteId = UUID.randomUUID();
		FavoriteEntity entity = new FavoriteEntity(
				favoriteId,
				null,
				"Portugal",
				"note",
				Instant.now()
		);
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), favoriteId))
				.thenReturn(Optional.of(entity));

		service.deleteFavorite(user, favoriteId);

		Mockito.verify(favoriteRepository).delete(entity);
	}

	@Test
	void deleteFavoriteFailsWhenNotFound() {
		UUID favoriteId = UUID.randomUUID();
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), favoriteId))
				.thenReturn(Optional.empty());

		Assertions.assertThrows(NotFoundException.class,
				() -> service.deleteFavorite(user, favoriteId));
	}

	@Test
	void listFavoritesReturnsUserScopedResults() {
		PageRequest pageRequest = PageRequest.of(0, 20);
		FavoriteEntity entity1 = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"Portugal",
				"note1",
				Instant.now()
		);
		FavoriteEntity entity2 = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"Japan",
				"note2",
				Instant.now()
		);
		Page<FavoriteEntity> page = new PageImpl<>(List.of(entity1, entity2), pageRequest, 2);
		Mockito.when(favoriteRepository.findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class)))
				.thenReturn(page);

		FavoritesPage result = service.listFavorites(user, 1, 20, "created_at_desc", null);

		Assertions.assertEquals(2, result.items().size());
		Assertions.assertEquals(1, result.page());
		Assertions.assertEquals(20, result.pageSize());
		Assertions.assertEquals(2, result.total());
		Mockito.verify(favoriteRepository).findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class));
	}

	@Test
	void listFavoritesFiltersByCountry() {
		PageRequest pageRequest = PageRequest.of(0, 20);
		FavoriteEntity entity = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"Portugal",
				"note",
				Instant.now()
		);
		Page<FavoriteEntity> page = new PageImpl<>(List.of(entity), pageRequest, 1);
		Mockito.when(favoriteRepository.findByUserIdAndCountryContainingIgnoreCase(
				Mockito.eq(user.id()), Mockito.eq("port"), Mockito.any(PageRequest.class)))
				.thenReturn(page);

		FavoritesPage result = service.listFavorites(user, 1, 20, "created_at_desc", "port");

		Assertions.assertEquals(1, result.items().size());
		Assertions.assertEquals("Portugal", result.items().get(0).country());
		Mockito.verify(favoriteRepository).findByUserIdAndCountryContainingIgnoreCase(
				Mockito.eq(user.id()), Mockito.eq("port"), Mockito.any(PageRequest.class));
	}

	@Test
	void listFavoritesHandlesEmptyCountryFilter() {
		PageRequest pageRequest = PageRequest.of(0, 20);
		Page<FavoriteEntity> page = new PageImpl<>(List.of(), pageRequest, 0);
		Mockito.when(favoriteRepository.findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class)))
				.thenReturn(page);

		FavoritesPage result = service.listFavorites(user, 1, 20, "created_at_desc", "");

		Assertions.assertEquals(0, result.items().size());
		Mockito.verify(favoriteRepository).findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class));
	}

	@Test
	void listFavoritesHandlesNullCountryFilter() {
		PageRequest pageRequest = PageRequest.of(0, 20);
		Page<FavoriteEntity> page = new PageImpl<>(List.of(), pageRequest, 0);
		Mockito.when(favoriteRepository.findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class)))
				.thenReturn(page);

		FavoritesPage result = service.listFavorites(user, 1, 20, "created_at_desc", null);

		Assertions.assertEquals(0, result.items().size());
		Mockito.verify(favoriteRepository).findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class));
	}

	@Test
	void listFavoritesRespectsPagination() {
		PageRequest pageRequest = PageRequest.of(1, 10);
		Page<FavoriteEntity> page = new PageImpl<>(List.of(), pageRequest, 25);
		Mockito.when(favoriteRepository.findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class)))
				.thenReturn(page);

		FavoritesPage result = service.listFavorites(user, 2, 10, "created_at_desc", null);

		Assertions.assertEquals(2, result.page());
		Assertions.assertEquals(10, result.pageSize());
		Assertions.assertEquals(25, result.total());
	}

	@Test
	void listFavoritesSortsByCreatedAtDesc() {
		PageRequest pageRequest = PageRequest.of(0, 20);
		FavoriteEntity entity1 = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"Portugal",
				"note1",
				Instant.now().minusSeconds(100)
		);
		FavoriteEntity entity2 = new FavoriteEntity(
				UUID.randomUUID(),
				null,
				"Japan",
				"note2",
				Instant.now()
		);
		Page<FavoriteEntity> page = new PageImpl<>(List.of(entity2, entity1), pageRequest, 2);
		Mockito.when(favoriteRepository.findByUserId(Mockito.eq(user.id()), Mockito.any(PageRequest.class)))
				.thenReturn(page);

		FavoritesPage result = service.listFavorites(user, 1, 20, "created_at_desc", null);

		Assertions.assertEquals(2, result.items().size());
	}

	@Test
	void toModelMapsEntityCorrectly() {
		UUID favoriteId = UUID.randomUUID();
		Instant createdAt = Instant.now();
		FavoriteEntity entity = new FavoriteEntity(
				favoriteId,
				null,
				"Portugal",
				"note",
				createdAt
		);
		Mockito.when(favoriteRepository.findByUserIdAndId(user.id(), favoriteId))
				.thenReturn(Optional.of(entity));
		Mockito.when(favoriteRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

		Favorite favorite = service.updateFavorite(user, favoriteId, "updated");

		Assertions.assertEquals(favoriteId, favorite.id());
		Assertions.assertEquals("Portugal", favorite.country());
		Assertions.assertEquals("updated", favorite.note());
		Assertions.assertEquals(createdAt, favorite.createdAt());
	}
}

