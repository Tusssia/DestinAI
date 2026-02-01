# API Endpoint Implementation Plan: Auth, Recommendations, Favorites

## 1. Endpoints Overview
This plan covers all REST endpoints described in `@.ai/api-plan.md` and aligns them to existing command/DTO types under `com.destinai.api`. The endpoints provide passwordless auth (OTP), LLM-backed recommendations, and favorites CRUD.

## 2. Request Details
- **Auth**
  - `POST /api/auth/otp/request`
    - Required body: `email`
    - Optional body: none
    - Command model: `OtpRequestCommand`
  - `POST /api/auth/otp/verify`
    - Required body: `email` + (`code` or `token`)
    - Optional body: `code` or `token` (one required)
    - Command model: `OtpVerifyCommand`
  - `POST /api/auth/logout`
    - No body
  - `GET /api/auth/session`
    - No body
- **Recommendations**
  - `POST /api/recommendations`
    - Required body: questionnaire fields (`who`, `travel_type`, `accommodation`, `activities`, `budget`, `weather`, `season`)
    - Optional body: none
    - Command model: `RecommendationRequestCommand`
- **Favorites**
  - `GET /api/favorites`
    - Query params:
      - Optional: `page` (default 1), `page_size` (default 20, max 50), `sort` (default `created_at_desc`), `country`
  - `POST /api/favorites`
    - Required body: `country`
    - Optional body: `note`
    - Command model: `FavoriteCreateCommand`
  - `PATCH /api/favorites/{favoriteId}`
    - Required path: `favoriteId`
    - Required body: `note`
    - Command model: `FavoriteUpdateCommand`
  - `DELETE /api/favorites/{favoriteId}`
    - Required path: `favoriteId`

### Used Types
- Commands: `OtpRequestCommand`, `OtpVerifyCommand`, `RecommendationRequestCommand`, `FavoriteCreateCommand`, `FavoriteUpdateCommand`
- DTOs: `OtpRequestResponseDto`, `OtpVerifyResponseDto`, `SessionResponseDto`, `LogoutResponseDto`, `RecommendationResponseDto`, `DestinationDto`, `FavoritesListResponseDto`, `FavoriteDto`, `FavoriteDeleteResponseDto`, `UserDto`

## 3. Response Details
- **Auth**
  - OTP request: `OtpRequestResponseDto`, status `200`
  - OTP verify: `OtpVerifyResponseDto` + session cookie set, status `200`
  - Logout: `LogoutResponseDto` + session cookie cleared, status `200`
  - Session: `SessionResponseDto`, status `200`
- **Recommendations**
  - `RecommendationResponseDto` (schema version + 5 destinations), status `200`
- **Favorites**
  - List: `FavoritesListResponseDto`, status `200`
  - Create: `FavoriteDto`, status `201`
  - Update: `FavoriteDto`, status `200`
  - Delete: `FavoriteDeleteResponseDto`, status `200`

## 4. Data Flow
- **Auth: request OTP**
  1. API controller validates `OtpRequestCommand` with Bean Validation.
  2. `AuthService` delegates to `OtpService` to enforce rate limits, generate OTP/token, hash, and persist to `otp_tokens`.
  3. `MailService` sends code/link.
  4. Return `OtpRequestResponseDto(status="sent")`.
- **Auth: verify OTP**
  1. Controller validates `OtpVerifyCommand` and ensures `code` or `token` is present.
  2. `OtpService` performs atomic consume of OTP record; invalid attempts increment `attempt_count`.
  3. `UserService` finds/creates `users` entry (normalized email).
  4. `SessionService` creates new session, stores SHA-256 hash in `sessions`, sets cookie.
  5. Return `OtpVerifyResponseDto(status="authenticated", user=UserDto)`.
- **Auth: logout/session**
  1. `SessionService` validates cookie, invalidates session record on logout.
  2. Session lookup returns `SessionResponseDto` with authenticated false if no session.
- **Recommendations**
  1. Controller validates `RecommendationRequestCommand`.
  2. `RecommendationService` maps to LLM prompt, calls integration client, validates JSON schema, retries on failure, and normalizes to 5 destinations.
  3. Return `RecommendationResponseDto(schemaVersion="1.0", destinations=...)`.
- **Favorites**
  1. Auth filter resolves `user_id` from session.
  2. `FavoritesService` enforces max 50, uniqueness by `(user_id, country)`, and ownership checks.
  3. Persistence layer reads/writes `favorites` table.
  4. Mapper converts entities to `FavoriteDto` and `FavoritesListResponseDto`.

## 5. Security Considerations
- Enforce authentication for `/api/recommendations` and all `/api/favorites*`.
- Use HttpOnly, Secure, SameSite=Lax cookies and rotate session tokens on login.
- CSRF protection for state-changing endpoints (POST/PATCH/DELETE/logout).
- Prevent OTP brute force with rate limits and `attempt_count` max.
- Normalize email to lowercase before storage; never accept `user_id` from client.
- Avoid logging raw questionnaire answers; log only reason codes and timing.

## 6. Error Handling
- **400**: validation failures (invalid email, missing code/token, invalid enums, note too long, bad pagination values).
- **401**: no active session for protected endpoints or OTP verification failure.
- **404**: favorite not found or not owned by user.
- **500**: unexpected server or integration failures.
- Use `@ControllerAdvice` to map exceptions to the standard error DTO and log via SLF4J.
- No error table is defined in `@.ai/db-plan.md`; log structured errors and add a persistence hook only if a table is introduced later.

## 7. Performance
- Favor indexed lookups on `sessions.token_hash`, `otp_tokens.email`, `favorites.user_id`.
- Keep OTP/token verification atomic to avoid extra queries.
- Limit recommendation retries and set strict timeouts on the LLM client to prevent thread exhaustion.
- Pagination defaults reduce load; enforce `page_size <= 50`.

## 8. Implementation Steps
1. **Controllers**: Add REST controllers under `com.destinai.api.web` (or similar) for auth, recommendations, favorites, using `@Valid` with command records.
2. **Services**: Implement or extend module services (`auth`, `recommendations`, `favorites`) with business rules (rate limits, uniqueness, caps, ownership).
3. **Persistence**: Add repository queries for OTP token atomic consume, favorites pagination/sorting, session lookup by hash.
4. **Mappers**: Create explicit mappers for commands → service models and entities → DTOs.
5. **Security**: Configure session cookie attributes and CSRF for REST endpoints; ensure auth filter exposes current user to services.
6. **Error Handling**: Add/extend `@ControllerAdvice` with business exceptions and error DTO mapping for 400/401/404/500.
7. **Integration**: Implement LLM client with strict JSON validation and retry strategy; instrument logs with reason codes.
8. **Tests**: Add unit tests for services (OTP consume, favorites cap, recommendation validation) and integration tests for REST endpoints.

