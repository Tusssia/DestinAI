# UI Architecture for DestinAI

## 1. UI Structure Overview

DestinAI uses four server-rendered, session-gated views: Login, Questionnaire, Results, and Favorites. The UI is intentionally simple, web-only, and oriented around a single primary journey: authenticate, answer a fixed questionnaire, review five country recommendations, and save favorites for later. A lightweight client-side layer handles form validation and submission feedback, while all state and access control is enforced by server sessions and CSRF-protected requests.

## 2. View List

- View name: Login
  - View path: `/login`
  - Main purpose: Request and verify a one-time code/link to create a session.
  - Key information to display: Email input, code input (or link confirmation), status messaging.
  - Key view components: Email field, “Send code” action, code input, “Verify” action, status message area.
  - UX, accessibility, and security considerations: Clear form labels; keyboard focus order; email validation feedback; avoid exposing whether an email exists; CSRF on verify request; rate-limit messaging kept generic.

- View name: Favorites
  - View path: `/favorites`
  - Main purpose: Show saved destinations and allow note updates and deletion.
  - Key information to display: Saved country name, saved date, note (editable), empty state CTA.
  - Key view components: Favorites list, note input (max 100 chars), save-note action, delete action, “New destination” CTA.
  - UX, accessibility, and security considerations: Inline note length hints; confirmation for delete; empty state guidance; session-gated access; CSRF on note update and delete; avoid exposing other users’ data.

- View name: Questionnaire
  - View path: `/questionnaire`
  - Main purpose: Collect required answers for recommendation generation.
  - Key information to display: All fixed questions and options, required indicators.
  - Key view components: Radio groups and multi-select activities; submit button disabled until valid; validation messages.
  - UX, accessibility, and security considerations: Fieldset/legend for grouped radios; activity multi-select with clear minimum requirement; client-side validation mirrors server rules; session-gated access; CSRF on submit.

- View name: Results
  - View path: `/results`
  - Main purpose: Display exactly five recommendation cards and allow saving to Favorites.
  - Key information to display: Country, budget range, best months, weather summary, accommodation fit, travel style fit, top activities, pros, cons, why match.
  - Key view components: Five cards, Save action per card, simple loading/processing state.
  - UX, accessibility, and security considerations: Consistent card structure; readable lists; avoid overflow with reasonable truncation; session-gated access; CSRF on save; keep error feedback minimal and stable.

## 3. User Journey Map

- Primary flow:
  1. User opens `/login`, submits email to request code.
  2. User verifies code/link; session created.
  3. User is redirected to `/favorites` (default post-login view).
  4. User clicks “New destination” to go to `/questionnaire`.
  5. User completes all required fields (activities requires at least one).
  6. User submits questionnaire and views `/results` with five cards.
  7. User saves one or more destinations to Favorites.
  8. User returns to `/favorites` to view and manage saved items.

- Secondary flows:
  - User with empty Favorites sees empty state and starts a new search.
  - User updates notes or deletes a favorite from `/favorites`.
  - User signs out and returns to `/login`.

## 4. Layout and Navigation Structure

- Top-level navigation:
  - Primary action links: “Favorites” and “New destination.”
  - Optional utility: “Sign out” (visible when authenticated).
- Navigation rules:
  - Unauthenticated users are redirected to `/login`.
  - Authenticated users land on `/favorites` by default.
  - Results view is accessible only after a questionnaire submission.
- Layout consistency:
  - Shared header with app name and primary navigation.
  - Content area showing one view at a time.

## 5. Key Components

- Session Gate: Checks `/api/auth/session` to allow/deny access.
- CSRF Token Handler: Injects token into all state-changing requests.
- Questionnaire Form: Fixed inputs with client validation and submit state.
- Recommendation Card: Standardized display for one destination with Save.
- Favorites List Item: Country, saved date, note field, delete action.
- Empty State Panel: Guidance and CTA when Favorites is empty.

## 6. Requirements and User Story Mapping

- FR-001, FR-002, US-007: Questionnaire view with mandatory inputs and validation.
- FR-003 to FR-010, US-008 to US-014: Results view displays exactly five cards after valid submission.
- FR-016, US-017 to US-020: Simple loading/error messaging on Results.
- FR-018, FR-020, FR-021, US-023 to US-029: Favorites list with save, note, delete, and cap handling.
- FR-019, FR-025, US-001 to US-005, US-032: Login view, session gating, and protected actions.

## 7. Edge Cases and Pain Points Addressed

- Invalid or missing questionnaire input: inline validation and disabled submit.
- Slow or failed recommendations: minimal, stable error state on Results.
- Duplicate favorites or cap reached: inline messaging on save action.
- Unauthorized access: session gate redirects to Login.
- Empty Favorites: clear empty state with “New destination” CTA.

