# View Implementation Plan: Results

## 1. Overview
The Results view displays exactly five country recommendations generated from the questionnaire, lets the user save any recommendation to Favorites, and handles loading, retry, and error states without persisting questionnaire answers on the server.

## 2. View Routing
Path: `/results`

## 3. Component Structure
- `ResultsPage` (page shell + layout)
- `ResultsHeader` (title + helper text + actions)
- `ResultsContent`
- `ResultsLoadingState`
- `ResultsErrorState`
- `RecommendationsList`
- `RecommendationCard`
- `SaveFavoriteButton`
- `InlineStatusMessage` (per-card feedback)

## 4. Component Details
### ResultsPage
- Component description: Page-level container that initializes recommendation fetching from client-only questionnaire data and renders header + content states.
- Main elements: `main`, page `h1`, content wrapper, hidden JSON payload or data attributes.
- Handled interactions: none directly; delegates to `ResultsContent`.
- Handled validation: ensures questionnaire payload exists before API call; if missing, redirect to `/questionnaire`.
- Types: `RecommendationRequestPayload`, `ResultsState`.
- Props: none (page-level).

### ResultsHeader
- Component description: Displays the view title, summary text, and optional navigation actions (Favorites/New destination).
- Main elements: `header`, `h1`, `p`, optional `nav` links.
- Handled interactions: navigation clicks handled by browser.
- Handled validation: none.
- Types: none.
- Props: `showNavActions: boolean`.

### ResultsContent
- Component description: State-aware wrapper that shows loading, error, or list based on `ResultsState`.
- Main elements: container `section`, conditional `ResultsLoadingState`, `ResultsErrorState`, `RecommendationsList`.
- Handled interactions: handles Retry click via props from `ResultsErrorState`.
- Handled validation: none; relies on `ResultsState`.
- Types: `ResultsState`, `RecommendationViewModel[]`.
- Props: `state: ResultsState`, `onRetry: () => void`.

### ResultsLoadingState
- Component description: Loading UI while the recommendations request is in-flight.
- Main elements: `div` with spinner/placeholder, loading copy.
- Handled interactions: none.
- Handled validation: none.
- Types: none.
- Props: none.

### ResultsErrorState
- Component description: Stable error UI with retry action per PRD.
- Main elements: `div`, error message text, `button` for retry.
- Handled interactions: `onRetry` click.
- Handled validation: none.
- Types: none.
- Props: `message: string`, `onRetry: () => void`.

### RecommendationsList
- Component description: Renders exactly five `RecommendationCard` components.
- Main elements: `ul` or `div` list container.
- Handled interactions: none directly.
- Handled validation: enforces `items.length === 5` before rendering; otherwise show `ResultsErrorState`.
- Types: `RecommendationViewModel[]`.
- Props: `items: RecommendationViewModel[]`, `onSave: (country: string) => void`.

### RecommendationCard
- Component description: Displays one destination recommendation with fields required by PRD and Save action.
- Main elements: `article`, `h2`, `dl` or labeled sections for fields, `ul` lists for activities/pros/cons, Save button, status message.
- Handled interactions: Save click (delegated to `SaveFavoriteButton`).
- Handled validation:
  - Render guards for missing fields (fallback to `—`).
  - Enforce display truncation/line-wrapping for long text items.
- Types: `RecommendationViewModel`.
- Props: `item: RecommendationViewModel`, `onSave: (country: string) => void`.

### SaveFavoriteButton
- Component description: Button that triggers `POST /api/favorites` for the card’s country and shows in-progress/saved state.
- Main elements: `button`, optional `span` for status.
- Handled interactions: `click` -> save action.
- Handled validation:
  - Disable when already saved or while saving.
  - Guard against empty `country`.
- Types: `SaveState`.
- Props: `country: string`, `saveState: SaveState`, `onSave: (country: string) => void`.

### InlineStatusMessage
- Component description: Lightweight feedback under Save button (saved/duplicate/cap reached/error).
- Main elements: `p` or `span` with status text.
- Handled interactions: none.
- Handled validation: none.
- Types: `InlineStatus`.
- Props: `status: InlineStatus | null`.

## 5. Types
### DTOs (from API)
- `RecommendationResponseDto`
  - `schemaVersion: string`
  - `destinations: DestinationDto[]`
- `DestinationDto`
  - `country: string`
  - `region: string`
  - `estimatedDailyBudgetEurRange: string`
  - `bestMonths: string[]`
  - `weatherSummary: string`
  - `accommodationFit: string`
  - `travelStyleFit: string`
  - `topActivities: string[]`
  - `pros: string[]`
  - `cons: string[]`
  - `whyMatch: string`
- `FavoriteDto`
  - `id: string` (UUID)
  - `country: string`
  - `note: string | null`
  - `createdAt: string` (ISO timestamp)

### View Models
- `RecommendationViewModel`
  - `country: string`
  - `region: string`
  - `estimatedDailyBudgetEurRange: string`
  - `bestMonths: string[]`
  - `weatherSummary: string`
  - `accommodationFit: string`
  - `travelStyleFit: string`
  - `topActivities: string[]`
  - `pros: string[]`
  - `cons: string[]`
  - `whyMatch: string`
  - `saveState: SaveState`
  - `status: InlineStatus | null`
- `RecommendationRequestPayload`
  - `who: "solo" | "couple"`
  - `travel_type: "backpacking" | "staying_in_one_place"`
  - `accommodation: "camping" | "hostels" | "hotels"`
  - `activities: string[]` (1+ entries)
  - `budget: "very_low" | "medium" | "luxurious"`
  - `weather: "sunny_dry" | "sunny_humid" | "cool" | "rainy"`
  - `season: "winter" | "spring" | "summer" | "autumn"`
- `ResultsState`
  - `status: "idle" | "loading" | "loaded" | "error"`
  - `items: RecommendationViewModel[]`
  - `errorMessage: string | null`
- `SaveState`
  - `"idle" | "saving" | "saved" | "error"`
- `InlineStatus`
  - `"saved" | "duplicate" | "limit" | "error"`

## 6. State Management
- Use a page-level controller (e.g., `results.ts`) to manage state; no global store required.
- State variables:
  - `resultsState: ResultsState`
  - `requestPayload: RecommendationRequestPayload | null`
  - `inFlightSave: Record<string, boolean>` keyed by country
  - `savedCountries: Set<string>`
- Optional helper functions:
  - `loadRequestPayload()` reads payload from `history.state` or `sessionStorage` (client-only, not persisted server-side).
  - `fetchRecommendations(payload)` updates `resultsState`.
  - `saveFavorite(country)` updates per-card `saveState` and `status`.

## 7. API Integration
- `POST /api/recommendations`
  - Request body: `RecommendationRequestCommand` (see `RecommendationRequestPayload`).
  - Response: `RecommendationResponseDto`.
  - Include CSRF token header (if enabled) and rely on session cookie `destinai_session`.
- `POST /api/favorites`
  - Request body: `{ country: string, note?: string }`.
  - Response: `FavoriteDto` (201).
  - Handle 400 for duplicate/cap reached, 401 for missing session.

## 8. User Interactions
- Page load:
  - If questionnaire payload missing, redirect to `/questionnaire`.
  - Otherwise, show loading state and call `/api/recommendations`.
- Retry:
  - Resubmits the same payload and resets error state.
- Save to Favorites:
  - Click Save -> disable button, show saving state.
  - On success: mark card as saved and show “Saved” status.
  - On duplicate: show “Already saved”.
  - On limit reached: show “Favorites limit reached”.

## 9. Conditions and Validation
- Before API call:
  - All fields present in payload.
  - `activities.length >= 1`.
  - Values match enum wire values (`who`, `travel_type`, etc.).
- Before render:
  - Ensure `destinations.length === 5`; otherwise show error state and retry option.
  - Render only safe text via `textContent` to avoid injection.
- Display constraints:
  - Apply CSS truncation/wrapping for list items and short strings per PRD guidance.

## 10. Error Handling
- Recommendation request failures:
  - Network/timeout/5xx -> show “Service is temporarily unavailable. Please try again later.” + Retry.
  - 401 -> redirect to `/login`.
  - 400 -> show generic error + Retry.
- Save favorites failures:
  - 400 with duplicate -> show “Already saved.”
  - 400 with cap -> show “Favorites limit reached.”
  - 401 -> redirect to `/login`.
  - Network/5xx -> show “Could not save. Try again.”

## 11. Implementation Steps
1. Create Thymeleaf template for `/results` with container elements and placeholders for dynamic content.
2. Add a small JS entry (e.g., `results.ts`) to load questionnaire payload from `history.state` or `sessionStorage`.
3. Implement `fetchRecommendations(payload)` with loading/error handling and schema count check (`destinations.length === 5`).
4. Map `DestinationDto` to `RecommendationViewModel` and render cards into `RecommendationsList`.
5. Implement Save button handler calling `POST /api/favorites`, update per-card `saveState` and inline status.
6. Add Retry handler that reuses the stored payload and resets UI state.
7. Apply CSS rules for readable lists, wrapping, and truncation of long text items.
8. Validate redirect behavior for missing payload or unauthenticated session.

