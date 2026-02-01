package com.destinai.api.web;

import com.destinai.api.command.favorites.FavoriteCreateCommand;
import com.destinai.api.command.favorites.FavoriteUpdateCommand;
import com.destinai.api.dto.favorites.FavoriteDeleteResponseDto;
import com.destinai.api.dto.favorites.FavoriteDto;
import com.destinai.api.dto.favorites.FavoritesListResponseDto;
import com.destinai.api.service.auth.AuthService;
import com.destinai.api.service.favorites.FavoritesService;
import com.destinai.api.service.model.Favorite;
import com.destinai.api.service.model.FavoritesPage;
import com.destinai.api.service.model.User;
import com.destinai.common.errors.BadRequestException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
public class FavoritesApiController {
	private static final String SESSION_COOKIE_NAME = "destinai_session";

	private final AuthService authService;
	private final FavoritesService favoritesService;

	public FavoritesApiController(AuthService authService, FavoritesService favoritesService) {
		this.authService = authService;
		this.favoritesService = favoritesService;
	}

	@GetMapping
	public FavoritesListResponseDto listFavorites(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken,
			@RequestParam(name = "page", defaultValue = "1") int page,
			@RequestParam(name = "page_size", defaultValue = "20") int pageSize,
			@RequestParam(name = "sort", defaultValue = "created_at_desc") String sort,
			@RequestParam(name = "country", required = false) String country
	) {
		validatePagination(page, pageSize, sort);
		User user = authService.requireUser(sessionToken);
		FavoritesPage result = favoritesService.listFavorites(user, page, pageSize, sort, country);
		List<FavoriteDto> items = result.items().stream().map(this::toDto).toList();
		return new FavoritesListResponseDto(items, result.page(), result.pageSize(), result.total());
	}

	@PostMapping
	public ResponseEntity<FavoriteDto> createFavorite(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken,
			@Valid @RequestBody FavoriteCreateCommand command
	) {
		User user = authService.requireUser(sessionToken);
		Favorite favorite = favoritesService.createFavorite(user, command.country(), command.note());
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(favorite));
	}

	@PatchMapping("/{favoriteId}")
	public FavoriteDto updateFavorite(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken,
			@PathVariable UUID favoriteId,
			@Valid @RequestBody FavoriteUpdateCommand command
	) {
		User user = authService.requireUser(sessionToken);
		Favorite favorite = favoritesService.updateFavorite(user, favoriteId, command.note());
		return toDto(favorite);
	}

	@DeleteMapping("/{favoriteId}")
	public FavoriteDeleteResponseDto deleteFavorite(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken,
			@PathVariable UUID favoriteId
	) {
		User user = authService.requireUser(sessionToken);
		favoritesService.deleteFavorite(user, favoriteId);
		return new FavoriteDeleteResponseDto("deleted");
	}

	private void validatePagination(int page, int pageSize, String sort) {
		if (page < 1) {
			throw new BadRequestException("Page must be >= 1.");
		}
		if (pageSize < 1 || pageSize > 50) {
			throw new BadRequestException("Page size must be between 1 and 50.");
		}
		if (!"created_at_desc".equals(sort) && !"created_at_asc".equals(sort)) {
			throw new BadRequestException("Unsupported sort value.");
		}
	}

	private FavoriteDto toDto(Favorite favorite) {
		return new FavoriteDto(
				favorite.id(),
				favorite.country(),
				favorite.note(),
				favorite.createdAt()
		);
	}
}

