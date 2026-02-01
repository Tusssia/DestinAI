# View Implementation Plan: Favorites

## 1. Overview
The Favorites view lists saved countries for the authenticated user, supports note updates and deletion, enforces a max of 50 items, and shows an empty state with a New destination CTA.

## 2. View Routing
Path: `/favorites`

## 3. Component Structure
- `FavoritesPage` (page shell + layout)
- `FavoritesHeader`
- `FavoritesToolbar` (search + sorting)
- `FavoritesList`
- `FavoriteListItem`
- `FavoriteNoteEditor`
- `FavoriteDeleteButton`
- `FavoritesEmptyState`
- `FavoritesPagination`
- `InlineStatusMessage`

## 4. Component Details
### FavoritesPage
- Component description: Page-level container that fetches favorites and manages filtering, paging, and actions.
- Main elements: `main`, `section`, list wrapper, empty state container.
- Handled interactions: delegates to child components; handles load and refresh.
- Handled validation: verifies authenticated session via API response; redirects to `/login` on 401.
- Types: `FavoritesState`, `FavoritesQuery`.
- Props: none.

### FavoritesHeader
- Component description: Title and primary actions (New destination, Sign out optional).
- Main elements: `header`, `h1`, `nav` links.
- Handled interactions: navigation links.
- Handled validation: none.
- Types: none.
- Props: none.

### FavoritesToolbar
- Component description: Search and sort controls for favorites.
- Main elements: `form`, `input[type="search"]`, `select` for sort, optional page size selector.
- Handled interactions: submit/changes trigger reload.
- Handled validation:
  - `page_size` between 1 and 50.
  - `sort` in `created_at_desc` | `created_at_asc`.
- Types: `FavoritesQuery`.
- Props: `query: FavoritesQuery`, `onChange: (query: FavoritesQuery) => void`.

### FavoritesList
- Component description: Renders the list of favorites.
- Main elements: `ul` or `div`.
- Handled interactions: none directly.
- Handled validation: if `items.length === 0`, render `FavoritesEmptyState`.
- Types: `FavoriteViewModel[]`.
- Props: `items: FavoriteViewModel[]`, `onUpdateNote: (...) => void`, `onDelete: (...) => void`.

### FavoriteListItem
- Component description: Displays a single favorite with country, saved date, note editor, and delete.
- Main elements: `article`, `h2`, `time`, note editor, delete button.
- Handled interactions: note save, delete.
- Handled validation:
  - Note length max 100.
  - Disable actions while saving.
- Types: `FavoriteViewModel`.
- Props: `item: FavoriteViewModel`, `onUpdateNote`, `onDelete`.

### FavoriteNoteEditor
- Component description: Inline editor for note field.
- Main elements: `textarea` (max 100), `button` Save.
- Handled interactions: input change, save click.
- Handled validation: max length 100, non-blocking empty note allowed.
- Types: `NoteSaveState`.
- Props: `note: string`, `status: NoteSaveState`, `onSave: (note: string) => void`.

### FavoriteDeleteButton
- Component description: Delete action with confirm and status handling.
- Main elements: `button`.
- Handled interactions: click -> confirm -> delete.
- Handled validation: disable during delete.
- Types: `DeleteState`.
- Props: `status: DeleteState`, `onDelete: () => void`.

### FavoritesEmptyState
- Component description: Empty list UI with CTA to start new search.
- Main elements: `div`, message text, link/button to `/questionnaire`.
- Handled interactions: CTA click.
- Handled validation: none.
- Types: none.
- Props: none.

### FavoritesPagination
- Component description: Controls pagination.
- Main elements: `nav`, prev/next buttons, page indicator.
- Handled interactions: page change.
- Handled validation: page >= 1.
- Types: `FavoritesPageInfo`.
- Props: `pageInfo: FavoritesPageInfo`, `onPageChange: (page: number) => void`.

### InlineStatusMessage
- Component description: Per-item feedback for saves/deletes.
- Main elements: `p` or `span`.
- Handled interactions: none.
- Handled validation: none.
- Types: `InlineStatus`.
- Props: `status: InlineStatus | null`.

## 5. Types
### DTOs (from API)
- `FavoritesListResponseDto`
  - `items: FavoriteDto[]`
  - `page: number`
  - `pageSize: number`
  - `total: number`
- `FavoriteDto`
  - `id: string` (UUID)
  - `country: string`
  - `note: string | null`
  - `createdAt: string` (ISO timestamp)
- `FavoriteDeleteResponseDto`
  - `status: "deleted"`

### View Models
- `FavoriteViewModel`
  - `id: string`
  - `country: string`
  - `note: string`
  - `createdAt: string`
  - `noteStatus: NoteSaveState`
  - `deleteStatus: DeleteState`
  - `statusMessage: InlineStatus | null`
- `FavoritesState`
  - `status: "idle" | "loading" | "loaded" | "error"`
  - `items: FavoriteViewModel[]`
  - `pageInfo: FavoritesPageInfo`
  - `errorMessage: string | null`
- `FavoritesQuery`
  - `page: number`
  - `pageSize: number`
  - `sort: "created_at_desc" | "created_at_asc"`
  - `country?: string`
- `FavoritesPageInfo`
  - `page: number`
  - `pageSize: number`
  - `total: number`
  - `totalPages: number`
- `NoteSaveState`
  - `"idle" | "saving" | "saved" | "error"`
- `DeleteState`
  - `"idle" | "deleting" | "error"`
- `InlineStatus`
  - `"note_saved" | "note_error" | "deleted_error"`

## 6. State Management
- Page-level state only.
- State variables:
  - `favoritesState: FavoritesState`
  - `query: FavoritesQuery`
  - `inFlightById: Record<string, boolean>` for note/delete.
- Functions:
  - `loadFavorites(query)` -> GET list.
  - `updateNote(id, note)` -> PATCH.
  - `deleteFavorite(id)` -> DELETE.

## 7. API Integration
- `GET /api/favorites`
  - Query params: `page`, `page_size`, `sort`, `country`.
  - Response: `FavoritesListResponseDto`.
- `PATCH /api/favorites/{favoriteId}`
  - Body: `{ note: string }`.
  - Response: `FavoriteDto`.
- `DELETE /api/favorites/{favoriteId}`
  - Response: `FavoriteDeleteResponseDto`.
- Include CSRF token for PATCH/DELETE and rely on session cookie.

## 8. User Interactions
- On load: fetch first page with default sort.
- Search filter: reload list with `country` query.
- Sort change: reload list.
- Update note: inline save with feedback.
- Delete: confirm and remove from list.
- Empty list: show CTA to `/questionnaire`.

## 9. Conditions and Validation
- Enforce `page >= 1`, `page_size <= 50`, valid `sort`.
- Note length <= 100; prevent extra characters or show inline error.
- Disable actions while saving/deleting to prevent duplicates.

## 10. Error Handling
- 401 from any endpoint: redirect to `/login`.
- GET failures: show page-level error with retry.
- PATCH/DELETE failures: show per-item error status and keep item in list.
- Network/5xx: show generic error and allow retry.

## 11. Implementation Steps
1. Create `/favorites` template with list and empty state placeholders.
2. Add client JS to load favorites and manage paging/sorting/search.
3. Implement `GET /api/favorites` integration and map DTO to view model.
4. Implement note update flow with max length validation and status.
5. Implement delete flow with confirmation and list update.
6. Add pagination controls and total pages calculation.
7. Add empty state CTA and error state UI.

