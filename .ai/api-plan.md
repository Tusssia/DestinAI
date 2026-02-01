# REST API Plan

## 1. Resources
- `users` → `users` table (minimal identity, email only)
- `otp_tokens` → `otp_tokens` table (passwordless auth tokens)
- `sessions` → `sessions` table (server-side sessions)
- `favorites` → `favorites` table (saved countries + notes)
- `recommendations` → transient resource (LLM results, not persisted)

## 2. Endpoints

### Auth

#### Request OTP (magic code/link)
- HTTP Method: `POST`
- URL Path: `/api/auth/otp/request`
- Description: Issue a one-time code or magic link to an email.
- Query parameters: none
- JSON request payload:
  ```json
  {
    "email": "user@example.com"
  }
  ```
- JSON response payload:
  ```json
  {
    "status": "sent"
  }
  ```
- Success codes: `202 Accepted` (sent)
- Error codes:
  - `400 Bad Request` (invalid email)
  - `429 Too Many Requests` (rate limit)
  - `503 Service Unavailable` (email/OTP provider issues)

#### Verify OTP (create session)
- HTTP Method: `POST`
- URL Path: `/api/auth/otp/verify`
- Description: Verify OTP and create a server-side session.
- Query parameters: none
- JSON request payload:
  ```json
  {
    "email": "user@example.com",
    "code": "123456"
  }
  ```
  (If magic link is used, the link can call this endpoint with a `token` field instead of `code`.)
- JSON response payload:
  ```json
  {
    "status": "authenticated",
    "user": {
      "id": "uuid",
      "email": "user@example.com"
    }
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `400 Bad Request` (invalid payload)
  - `401 Unauthorized` (invalid/expired/used OTP)
  - `429 Too Many Requests` (attempt limit exceeded)

#### Sign out
- HTTP Method: `POST`
- URL Path: `/api/auth/logout`
- Description: Invalidate current session.
- Query parameters: none
- JSON request payload: none
- JSON response payload:
  ```json
  {
    "status": "signed_out"
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `401 Unauthorized` (no active session)

#### Get current session
- HTTP Method: `GET`
- URL Path: `/api/auth/session`
- Description: Return current session/user for UI bootstrapping.
- Query parameters: none
- JSON response payload:
  ```json
  {
    "authenticated": true,
    "user": {
      "id": "uuid",
      "email": "user@example.com"
    }
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `401 Unauthorized`

### Recommendations (LLM)

#### Generate recommendations
- HTTP Method: `POST`
- URL Path: `/api/recommendations`
- Description: Submit questionnaire answers to LLM pipeline and return 5 countries.
- Query parameters: none
- JSON request payload (fixed questionnaire):
  ```json
  {
    "who": "solo|couple",
    "travel_type": "backpacking|staying_in_one_place",
    "accommodation": "camping|hostels|hotels",
    "activities": ["hiking", "diving"],
    "budget": "very_low|medium|luxurious",
    "weather": "sunny_dry|sunny_humid|cool|rainy",
    "season": "winter|spring|summer|autumn"
  }
  ```
- JSON response payload:
  ```json
  {
    "schema_version": "1.0",
    "destinations": [
      {
        "country": "Portugal",
        "region": "Europe",
        "estimated_daily_budget_eur_range": "€60–€90",
        "best_months": ["May", "Jun", "Sep"],
        "weather_summary": "Mild and sunny",
        "accommodation_fit": "strong",
        "travel_style_fit": "moderate",
        "top_activities": ["surfing", "local cuisine"],
        "pros": ["Great coastal hikes"],
        "cons": ["Crowds in summer"],
        "why_match": "Matches budget and activities"
      }
    ]
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `400 Bad Request` (missing/invalid answers)
  - `502 Bad Gateway` (LLM provider error)
  - `504 Gateway Timeout` (LLM timeout)
  - `422 Unprocessable Entity` (LLM output failed validation after retries)

### Favorites

#### List favorites
- HTTP Method: `GET`
- URL Path: `/api/favorites`
- Description: List current user's favorites.
- Query parameters:
  - `page` (default 1)
  - `page_size` (default 20, max 50)
  - `sort` (default `created_at_desc`, options: `created_at_desc`, `created_at_asc`, `country_asc`)
  - `country` (optional filter, substring match)
- JSON response payload:
  ```json
  {
    "items": [
      {
        "id": "uuid",
        "country": "Portugal",
        "note": "Surfing in autumn",
        "created_at": "2026-02-01T10:15:00Z"
      }
    ],
    "page": 1,
    "page_size": 20,
    "total": 3
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `401 Unauthorized`

#### Create favorite
- HTTP Method: `POST`
- URL Path: `/api/favorites`
- Description: Save a country as favorite.
- Query parameters: none
- JSON request payload:
  ```json
  {
    "country": "Portugal",
    "note": "Surfing in autumn"
  }
  ```
- JSON response payload:
  ```json
  {
    "id": "uuid",
    "country": "Portugal",
    "note": "Surfing in autumn",
    "created_at": "2026-02-01T10:15:00Z"
  }
  ```
- Success codes: `201 Created`
- Error codes:
  - `400 Bad Request` (missing country, note too long)
  - `401 Unauthorized`
  - `409 Conflict` (duplicate country for user)
  - `422 Unprocessable Entity` (favorites cap reached)

#### Update favorite note
- HTTP Method: `PATCH`
- URL Path: `/api/favorites/{favoriteId}`
- Description: Update note for a favorite.
- Query parameters: none
- JSON request payload:
  ```json
  {
    "note": "New note"
  }
  ```
- JSON response payload:
  ```json
  {
    "id": "uuid",
    "country": "Portugal",
    "note": "New note",
    "created_at": "2026-02-01T10:15:00Z"
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `400 Bad Request` (note too long)
  - `401 Unauthorized`
  - `404 Not Found` (not owned by user or missing)

#### Delete favorite
- HTTP Method: `DELETE`
- URL Path: `/api/favorites/{favoriteId}`
- Description: Remove a favorite.
- Query parameters: none
- JSON request payload: none
- JSON response payload:
  ```json
  {
    "status": "deleted"
  }
  ```
- Success codes: `200 OK`
- Error codes:
  - `401 Unauthorized`
  - `404 Not Found` (not owned by user or missing)

## 3. Authentication and Authorization
- Use server-side sessions stored in `sessions` table.
- Session token stored in `HttpOnly`, `Secure`, `SameSite=Lax` cookie.
- Session token value is a random secret; only SHA-256 hash stored in DB (`token_hash`).
- On login, rotate session token and set `expires_at` (default 7 days) and `last_accessed_at`.
- All `/api/favorites*` and `/api/recommendations` require authentication.
- Derive `user_id` from session; never accept `user_id` in request payloads.
- CSRF protection for all state-changing endpoints when using cookies.
- Rate limiting:
  - OTP request: per email and per IP (e.g., max 5/hour).
  - OTP verify: per token with `attempt_count` (max 5), plus per IP.

## 4. Validation and Business Logic

### Users
- `email` required, stored lowercase; unique via `idx_users_email_lower`.
- Created on successful OTP verify if user doesn’t exist.

### OTP Tokens
- Required: `email`, `token_hash`, `expires_at`.
- Single-use enforced by `consumed_at IS NULL` and atomic update on verify.
- `attempt_count` increment on failed verifies; if >= 5, invalidate (set `consumed_at`).
- No FK to `users` (allow pre-registration issuance).

### Sessions
- Required: `user_id`, `token_hash`, `expires_at`.
- Validate on each request: `expires_at > now()` and hash match.
- Optional tracking: `last_accessed_at`, `user_agent`, `ip_address`.

### Favorites
- Required: `user_id`, `country`.
- `note` max 100 characters.
- Unique per user: `(user_id, country)` constraint.
- Cap 50 favorites per user enforced in app layer prior to insert.

### Recommendations Business Logic (LLM pipeline)
- Validate request answers are complete; activities must have 1+ items.
- Compose fixed prompt and request strict JSON.
- Validation and repair flow:
  - Enforce valid JSON and schema.
  - Enforce exactly 5 destinations.
  - Enforce unique countries.
  - Enforce max 2 per region (diversification).
  - Retry once for network/timeouts and once for repair on invalid output.
- Failure responses:
  - `422` when output invalid after retries.
  - `504` on timeout; `502` on provider errors.
- Log reason codes for failures without logging raw questionnaire answers.

### Pagination, Filtering, Sorting
- `GET /api/favorites` supports `page`, `page_size`, `sort`, and optional `country` filter.
- `page_size` max 50 to align with favorites cap.

