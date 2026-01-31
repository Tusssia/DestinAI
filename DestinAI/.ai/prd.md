# Product Requirements Document (PRD) - DestinAI
## 1. Product Overview
### 1.1 Purpose
DestinAI is a web application that helps users quickly discover suitable holiday destinations at the country level. Users answer a fixed set of mandatory, closed questions. The app converts the answers into a structured prompt, sends it to an external free-access LLM API, validates/parses the response (strict JSON), and displays exactly 5 recommended countries. Users can save recommendations to a Favorites list, add short notes, and delete saved items.

### 1.2 Goals
1. Reduce time spent researching destinations by generating 5 relevant country recommendations from a short questionnaire.
2. Provide consistent, structured results using a strict JSON schema and a robust normalization pipeline.
3. Enable basic persistence: passwordless login and Favorites CRUD (create, read, update note, delete).

### 1.3 Target Users (MVP)
1. Solo travelers
2. Backpackers
3. Couples

### 1.4 Platforms, Locales, and Scope
- Platform: Web only (responsive)
- Language: English only
- Screens (thin UI): Login, Questionnaire, Results, Favorites

### 1.5 Core UX Flow Summary
- New destination flow: Login → New Destination → Questionnaire → Loading → Results (5) → Save to Favorites
- Returning user flow: Login → Favorites → Delete favorite / Add or update note → Optional New Destination

## 2. User Problem
### 2.1 Problem Statement
Travelers spend significant time browsing the internet to identify destinations that fit their budget, season, accommodation preferences, and activities. The discovery process is fragmented and often requires comparing multiple sources and options.

### 2.2 Jobs To Be Done
1. As a traveler, I want to answer a small set of clear questions so I can get destination recommendations quickly.
2. As a traveler, I want recommendations to be consistent and comparable so I can decide efficiently.
3. As a traveler, I want to save and annotate options so I can revisit my shortlist later.

### 2.3 Key Pain Points Addressed
1. Decision fatigue from endless browsing and open-ended searching
2. Inconsistent destination information formats across sites
3. Losing track of candidate destinations and reasons they were considered

### 2.4 Assumptions (MVP)
1. Users accept country-level recommendations (no city/region granularity).
2. Safety/danger screening is intentionally skipped in MVP; all countries returned by the LLM are acceptable for MVP.
3. Users do not need history of questionnaire submissions; only Favorites are persisted.
4. A free-access LLM API is available with adequate reliability to support the MVP success threshold.

## 3. Functional Requirements
### 3.1 Questionnaire (Inputs)
FR-001 Fixed questionnaire with mandatory closed inputs only
- The questionnaire must contain the following questions and options:
  - A. Who is traveling: solo, couple
  - B. Type of traveling: backpacking, staying in one place
  - C. Accommodation: camping, hostels, hotels
  - D. Favorite activities (multi-select): hiking, diving, tennis, canoeing, climbing, surfing, local culture, local cuisine
  - E. Budget: very low, medium, luxurious
  - F. Weather: sunny-dry, sunny-humid, cool, rainy
  - G. Season: winter (Nov–Feb), spring (Mar–May), summer (Jun–Aug), autumn (Sep–Oct)

FR-002 Input validation (client and server)
- All questions must be answered before submission.
- Activities must allow 1+ selections (multi-select required; if the product later allows 0, it must be explicitly designed and tested).
- Inputs must be validated server-side (do not trust client validation).

### 3.2 Prompt Composition and LLM Integration
FR-003 Prompt template composition
- The app must compose a prompt by filling user answers into a fixed template.
- The prompt must instruct the LLM to return strict JSON matching the schema in FR-006.
- The prompt must instruct the LLM to return exactly 5 destinations.

FR-004 External LLM API call
- The app must send the prompt to a free-access LLM API endpoint and await a response.
- The app must support a server-side timeout target of approximately 20–30 seconds for the overall request.

FR-005 Retry policy (reliability)
- One quick retry for network/timeouts.
- One repair retry for invalid JSON or schema/formatting issues (see FR-009).
- After retries fail, the app must fail gracefully and display an error state (see FR-016), and log the failure category.

### 3.3 Output Data Model and JSON Schema
FR-006 Strict, versioned JSON schema
- The LLM response must include a top-level `schema_version` (example: "1.0").
- The LLM response must include exactly 5 destination objects.
- Each destination object must include required fields:
  - `country` (string)
  - `region` (string; must map to the fixed region taxonomy in FR-012)
  - `estimated_daily_budget_eur_range` (string or structured object; see FR-020)
  - `best_months` (array of strings or month numbers; see FR-020)
  - `weather_summary` (string)
  - `accommodation_fit` (string or enum; see FR-020)
  - `travel_style_fit` (string or enum; see FR-020)
  - `top_activities` (array of strings)
  - `pros` (array of strings)
  - `cons` (array of strings)
  - `why_match` (string)
- UI readability constraint:
  - Enforce max length guidance of approximately 120 characters per string item (pros/cons/activity items and other short strings) via validation rules or post-processing truncation, without breaking JSON.

FR-007 Country-level granularity enforcement
- All recommendations must be countries, not cities/regions.
- If the LLM returns non-country values, the pipeline must request replacement(s) via repair prompts.

### 3.4 Normalization, Validation, and Post-processing Pipeline
FR-008 Schema validation gate
- The system must validate the LLM response against the JSON schema before rendering.
- If invalid, trigger FR-009 repair; do not render invalid content.

FR-009 Automatic repair prompt
- If the response is invalid JSON, fails schema validation, or does not contain exactly 5 destinations:
  - Send a repair prompt instructing the LLM to return corrected JSON strictly matching the schema and constraints.
  - The repair prompt must include explicit instructions about the exact failure (invalid JSON, schema errors, count mismatch).

FR-010 Enforce exactly 5 destinations
- If fewer or more than 5 destinations are returned:
  - Trigger repair prompt requesting exactly 5.
  - Do not render until exactly 5 valid destinations are available.

FR-011 Dedupe and replacement
- Countries must be unique within the results list.
- If duplicates exist:
  - Request replacements for duplicate entries.
  - Re-validate after replacements.
- Only render when uniqueness and schema rules pass.

FR-012 Diversification rule (region cap)
- After validation and before rendering, enforce:
  - Max 2 countries per broad region, using fixed regions:
    - Europe
    - North Africa
    - Sub-Saharan Africa
    - Middle East
    - South Asia
    - East Asia
    - Oceania
    - North America
    - Latin America/Caribbean
- If the region cap is violated:
  - Request replacements for excess entries in the overrepresented region(s).
  - Re-validate and re-check the cap before rendering.

FR-013 Constraint priority and relaxation tracking
- Constraint priority for ranking/scoring:
  1. Who is traveling
  2. Accommodation type
  3. Season (month-range interpretation)
  4. Budget tier
  - Non-core relaxable in order:
    1. Weather
    2. Activities coverage
    3. Travel type
- The system must track `relaxed_constraints` per destination internally in JSON, but must not display it to users in MVP.

FR-014 Activity matching rule
- For selected activities, each destination must strongly cover at least 2 of the selected activities.
- Remaining selected activities may be optional/secondary.

FR-015 Global season handling
- Interpret season as the travel month ranges:
  - winter: Nov–Feb
  - spring: Mar–May
  - summer: Jun–Aug
  - autumn: Sep–Oct
- The LLM instructions and/or post-processing must avoid “season name” ambiguity, including southern hemisphere inversions, by focusing on month ranges and typical weather during those months in the country.

### 3.5 UI Requirements
FR-016 Loading, error, and empty states
- While waiting for results:
  - Show a clear loading state.
- If the LLM service is slow/unavailable or the pipeline fails:
  - Show a clear error message: “Service is temporarily unavailable. Please try again later.”
  - Provide a Retry button (subject to retry policy limits).
- If Favorites is empty:
  - Show an empty state with a call-to-action: “Start a new destination search.”

FR-017 Results display (5 cards)
- Display exactly 5 results as cards or a list with consistent structure.
- Each card must show a stable subset of fields in a consistent template, for example:
  - Country
  - Estimated daily budget range (EUR)
  - Best months
  - Weather summary
  - Accommodation fit
  - Travel style fit
  - Top activities
  - Pros
  - Cons
  - Why match
- Include a Save to Favorites action per result.

FR-018 Favorites display
- Favorites screen must list saved countries with:
  - Country name
  - Timestamp saved (or “saved on” date)
  - Note field (editable, max 100 chars)
  - Delete action
  - Entry point to “New destination”

### 3.6 Account, Authentication, Authorization
FR-019 Passwordless email-based authentication using one-time codes or links
- Users must be able to request a one-time sign-in method by entering an email.
- Users must be able to sign in using the one-time code or link.
- Sessions must be established securely after verification.
- Unauthorized users must not be able to access Favorites or create/save favorites.

### 3.7 Data Storage and Privacy
FR-020 Data model (minimum)
- Store per user:
  - Favorites list entries (max 50)
    - `country` (unique per user; dedupe on save)
    - `note` (max 100 characters)
    - `created_at` timestamp
    - Optional: internal `id` per favorite entry for stable operations (recommended)
- Do not store:
  - Questionnaire answers
  - Past searches
  - User profile fields beyond email required for authentication

FR-021 Favorites constraints
- Dedupe rule: one saved entry per country per user.
- Cap: maximum 50 favorites per user.
- On attempt to save beyond cap:
  - Show a clear message and prevent saving.

### 3.8 Observability and Metrics Logging
FR-022 Server-side logging and reason codes
- Log LLM and pipeline outcomes with reason codes, including:
  - invalid JSON
  - schema validation failure
  - destinations_count != 5
  - duplicate countries
  - region cap violation
  - UI render failure
  - timeout/network error
- Logs must not contain user questionnaire answers (since answers are not stored; avoid logging sensitive raw inputs).

FR-023 KPI computation
- Compute `LLM_success = valid_JSON AND schema_valid AND destinations_count==5 AND render_success`.
- Store breakdown counts for failure reasons.

### 3.9 Non-functional Requirements (MVP)
FR-024 Performance and timeouts
- Target end-to-end recommendation generation within 20–30 seconds, including one allowed retry.
- If exceeded, fail gracefully with retry option and log timeout.

FR-025 Security baseline
- Use secure session handling (httpOnly cookies or equivalent).
- One-time codes/links must be single-use and time-limited.
- Protect endpoints with authorization checks (Favorites CRUD must require a valid session).
- Rate-limit sign-in requests to reduce abuse.

FR-026 Accessibility and responsiveness
- Responsive design supporting mobile browsers (web-only).
- Basic accessibility: form labels, keyboard navigation for questionnaire, sufficient contrast, clear error states.

## 4. Product Boundaries
### 4.1 In Scope (MVP)
1. Web-only application with 4 screens: Login, Questionnaire, Results, Favorites
2. Fixed closed-question questionnaire, all mandatory
3. External LLM integration using strict JSON output
4. Normalization pipeline: validate → enforce count=5 → dedupe → diversification → re-validate → render
5. Passwordless email-based authentication using one-time codes or links
6. Favorites CRUD with notes and cap

### 4.2 Out of Scope (MVP)
1. Mobile native apps
2. User-configurable model selection or LLM provider switching
3. Custom recommendation algorithm (non-LLM)
4. Integrations with other platforms (booking sites, maps, calendars, etc.)
5. Social/sharing features or sharing between users
6. Safety/danger filtering or travel advisory integration
7. Saving questionnaire history or building user profiles
8. Multi-language support

### 4.3 Open Decisions and TBD Items (tracked for post-MVP or implementation planning)
1. Scoring mechanism details
- Exact operational weights/rules for how constraints determine ranking before requesting the top 5.
- Recommendation: implement a deterministic rule set in the prompt and rely on relaxation order; keep ranking logic in prompt for MVP.

2. Precise schema details
- Enums and exact formats:
  - `accommodation_fit` values (example: "strong", "moderate", "weak")
  - `travel_style_fit` values (example: "strong", "moderate", "weak")
  - `estimated_daily_budget_eur_range` format (string like "€50–€100" vs object like `{ "min": 50, "max": 100 }`)
  - `best_months` format (array of month names like ["May","Jun"] vs month numbers [5,6])
- Recommendation for MVP: keep fields as strings/arrays of strings for UI simplicity, and enforce a strict schema.

3. LLM provider specifics
- Selection of free-access LLM API, rate limits, latency expectations, request size limits, and content constraints.
- Implementation must include configuration via environment variables, but no user-facing configuration in MVP.

4. UI presentation choices
- Card layout details and which fields are visible in MVP (relaxations hidden).
- Exact error copy and empty states.

5. Data/privacy operational requirements
- Whether account/data deletion is required in MVP.
- One-time sign-in deliverability requirements, email service selection, and bounce handling.
- Recommendation: include a minimal “delete account” request flow post-MVP unless required by compliance or beta policy.

## 5. User Stories
### Authentication and Access
- ID: US-001
  Title: Request a one-time sign-in method via email
  Description: As a user, I want to enter my email and receive a one-time code or link so I can sign in without a password.
  Acceptance Criteria:
  - Given I am on the Login screen, when I enter a valid email and submit, then the app shows a confirmation state (sent).
  - The system sends a one-time code or link to the provided email address.
  - The request is rate-limited to prevent abuse (e.g., repeated rapid requests are blocked with a friendly message).

- ID: US-002
  Title: Sign in using a valid one-time code or link
  Description: As a user, I want to use the one-time code or link and be authenticated so I can access the app.
  Acceptance Criteria:
  - Given I open a valid, unexpired, unused one-time code or link, when it is verified, then I am signed in and redirected to the app entry point (Favorites or New Destination).
  - A secure session is created.
  - One-time codes or links cannot be reused after successful sign-in.

- ID: US-003
  Title: Handle expired or invalid one-time code or link
  Description: As a user, I want clear guidance if my one-time code or link is invalid or expired so I can request a new one.
  Acceptance Criteria:
  - Given I use an expired/invalid/used code or link, then I see an error message and a call-to-action to request a new one.
  - No session is created for invalid/expired attempts.

- ID: US-004
  Title: Sign out
  Description: As a user, I want to sign out so that my session ends on shared devices.
  Acceptance Criteria:
  - Given I am signed in, when I click Sign out, then my session is invalidated and I return to the Login screen.
  - After sign out, I cannot access Favorites without signing in again.

- ID: US-005
  Title: Prevent unauthorized access to Favorites and saved data
  Description: As the product, I must ensure only signed-in users can access their favorites.
  Acceptance Criteria:
  - Given I am not signed in, when I attempt to access Favorites pages or endpoints, then I am redirected to Login.
  - API endpoints for Favorites CRUD reject requests without a valid session.

### Questionnaire and Recommendation Generation
- ID: US-006
  Title: Start a new destination search
  Description: As a user, I want to begin a new questionnaire so I can get recommendations.
  Acceptance Criteria:
  - Given I am signed in, when I click New destination, then I see the questionnaire screen.
  - The questionnaire starts in an empty state with all required inputs.

- ID: US-007
  Title: Complete mandatory closed questions
  Description: As a user, I want to answer all required questions using fixed options so I can submit the form.
  Acceptance Criteria:
  - The UI presents only the predefined options for each question.
  - The Submit action is disabled until all required questions are answered.
  - Activity selection supports multi-select and requires at least one selected activity.

- ID: US-008
  Title: Submit questionnaire and see loading state
  Description: As a user, I want to submit my answers and see that the app is working while results are generated.
  Acceptance Criteria:
  - When I submit valid answers, then I see a loading state.
  - The loading state persists until results are ready or a timeout/error occurs.
  - The system sends a request to the backend to generate recommendations.

- ID: US-009
  Title: Generate structured prompt from answers
  Description: As the system, I want to convert answers into a fixed prompt template so the LLM can return consistent JSON.
  Acceptance Criteria:
  - The backend maps each input to a prompt template with explicit instructions.
  - The prompt requests strict JSON with `schema_version` and exactly 5 destinations.
  - The prompt includes the fixed month ranges for the selected season.

- ID: US-010
  Title: Receive and validate LLM response (happy path)
  Description: As the system, I want to validate the LLM output so users only see compliant results.
  Acceptance Criteria:
  - Given the LLM returns valid JSON matching the schema with exactly 5 unique countries, then the system renders results successfully.
  - Each destination includes all required fields.

- ID: US-011
  Title: Handle invalid JSON from LLM using repair retry
  Description: As the system, I want to automatically repair formatting issues so users still get results when possible.
  Acceptance Criteria:
  - Given the LLM returns invalid JSON, when the repair retry is executed, then the system re-requests a corrected response.
  - If the repaired response is valid and renderable, results are displayed.
  - If still invalid after allowed retries, the user sees a graceful error state.

- ID: US-012
  Title: Enforce exactly 5 destinations when count mismatch occurs
  Description: As the system, I want to ensure users always receive exactly 5 recommendations.
  Acceptance Criteria:
  - Given the LLM returns fewer or more than 5 destinations, the system triggers a repair request asking for exactly 5.
  - Results are not shown until the count is exactly 5.

- ID: US-013
  Title: Remove duplicate countries and request replacements
  Description: As the system, I want to ensure the 5 results are unique countries.
  Acceptance Criteria:
  - Given duplicates appear in the list, the system requests replacement destinations for the duplicates.
  - The final rendered list contains 5 unique countries.

- ID: US-014
  Title: Enforce diversification rule (max 2 per region)
  Description: As the system, I want varied results across world regions.
  Acceptance Criteria:
  - Given more than 2 countries belong to the same region, the system replaces the excess entries until the max 2 per region rule is met.
  - The final rendered list passes schema validation and includes exactly 5 destinations.

- ID: US-015
  Title: Apply constraint priorities and relaxation tracking internally
  Description: As the system, I want recommendations to prioritize key constraints while allowing controlled relaxations for secondary constraints.
  Acceptance Criteria:
  - The system follows priority: who traveling, accommodation, season, budget as hard constraints.
  - The system relaxes secondary constraints in order: weather → activities coverage → travel type.
  - The output includes a `relaxed_constraints` array per destination internally, but the UI does not display it in MVP.

- ID: US-016
  Title: Display results in a consistent template
  Description: As a user, I want a consistent layout for each recommendation so I can compare options quickly.
  Acceptance Criteria:
  - Each of the 5 results displays the same set of visible fields.
  - The view is readable on common screen sizes (responsive).
  - Long items are truncated or wrapped without breaking layout; string length guidance is enforced.

### Reliability, Errors, and Edge Conditions
- ID: US-017
  Title: Handle LLM timeout or slow response
  Description: As a user, I want clear feedback if results take too long so I can retry or come back later.
  Acceptance Criteria:
  - If the backend does not return results within the timeout target (approximately 20–30 seconds), then the user sees an error state.
  - The error state provides a Retry button.
  - The system logs a timeout reason code.

- ID: US-018
  Title: Handle LLM service unavailable
  Description: As a user, I want a clear “service unavailable” message when the LLM cannot be reached.
  Acceptance Criteria:
  - If the external LLM API returns an error, then the user sees a “service unavailable” message and retry option.
  - The system logs the failure category (network error, provider error).

- ID: US-019
  Title: Retry once for network/timeouts
  Description: As the system, I want to retry once quickly for transient failures.
  Acceptance Criteria:
  - On network failure or timeout, the backend performs one retry automatically.
  - If the retry succeeds, results are shown; if not, the user sees an error state.
  - Retries are bounded and do not loop indefinitely.

- ID: US-020
  Title: Fail gracefully after retry limits
  Description: As a user, I want the app to stop and explain when it cannot generate results.
  Acceptance Criteria:
  - After allowed retries (one quick retry and one repair retry), the UI shows a stable error state.
  - The error state includes a retry action (manual), which initiates a new attempt subject to the same retry rules.
  - The system logs a final failure reason.

- ID: US-021
  Title: Handle client-side connectivity loss during request
  Description: As a user, I want to understand if my network disconnects so I can try again.
  Acceptance Criteria:
  - If the client detects offline state during loading, the UI shows a message indicating connectivity issues.
  - The user can retry after connectivity is restored.

- ID: US-022
  Title: Prevent double-submission of questionnaire
  Description: As the system, I want to avoid duplicate requests when users click submit multiple times.
  Acceptance Criteria:
  - While loading, the submit action is disabled.
  - Only one request is in flight per questionnaire submission.

### Favorites (Persistence) and Notes
- ID: US-023
  Title: Save a recommended country to Favorites
  Description: As a user, I want to save a destination so I can revisit it later.
  Acceptance Criteria:
  - Given I am viewing Results, when I click Save on a destination, then it is added to my Favorites.
  - The favorite includes a saved timestamp.
  - The UI confirms the save (toast/message/state change).

- ID: US-024
  Title: Prevent duplicate favorites for the same country
  Description: As a user, I want Favorites to avoid duplicates so my list stays clean.
  Acceptance Criteria:
  - Given a country is already saved, when I attempt to save it again, then the app does not create a duplicate.
  - The UI indicates it is already saved.

- ID: US-025
  Title: Enforce Favorites cap of 50
  Description: As a user, I want the app to enforce a reasonable limit so the system remains manageable.
  Acceptance Criteria:
  - Given I already have 50 favorites, when I attempt to save another, then saving is blocked and I see a message explaining the limit.
  - No new favorite is created.

- ID: US-026
  Title: View Favorites list
  Description: As a returning user, I want to see my saved countries so I can decide later.
  Acceptance Criteria:
  - Given I am signed in, when I open Favorites, then I see all saved countries (up to 50) in a list.
  - Each entry shows country and saved date (and note, if present).

- ID: US-027
  Title: Add a note to a saved favorite
  Description: As a user, I want to add a short note to remember why I saved a country.
  Acceptance Criteria:
  - Given a favorite entry, when I add/edit a note and save, then the note is stored and displayed.
  - Notes are limited to 100 characters; extra characters are prevented or rejected with a clear message.
  - Notes persist after refresh and after I sign in again.

- ID: US-028
  Title: Delete a favorite
  Description: As a user, I want to remove a destination from Favorites to keep my list current.
  Acceptance Criteria:
  - When I click Delete on a favorite, then it is removed from Favorites.
  - The UI updates immediately after confirmation of success.
  - If deletion fails, an error message is shown and the item remains.

- ID: US-029
  Title: Favorites empty state
  Description: As a user with no saved destinations, I want guidance on what to do next.
  Acceptance Criteria:
  - If Favorites list is empty, then an empty state is displayed with a New destination call-to-action.

### Data Handling, Security, and Observability
- ID: US-030
  Title: Store only minimal user data
  Description: As the product, I want to minimize stored data to reduce privacy risk.
  Acceptance Criteria:
  - The system stores only favorites (country, note, timestamp, optional internal id) per user.
  - The system does not store questionnaire answers or search history.
  - Logs do not contain raw questionnaire answers.

- ID: US-031
  Title: Compute and log LLM_success KPI and failures
  Description: As the product team, I want to measure reliability so we can improve the system.
  Acceptance Criteria:
  - For each recommendation attempt, the system records whether `LLM_success` is true or false.
  - If false, a failure reason code is recorded (invalid JSON, schema fail, count mismatch, timeout/network, render fail, dedupe/region issues).
  - Metrics can be aggregated for reporting (at least server-side logs or a simple analytics store).

- ID: US-032
  Title: Protect Favorites CRUD endpoints
  Description: As the product, I want to ensure Favorites data cannot be accessed or modified by other users.
  Acceptance Criteria:
  - Favorites CRUD requires authentication.
  - Requests are scoped to the signed-in user; attempts to access another user’s data fail.
  - Authorization checks are enforced server-side.

## 6. Success Metrics
### 6.1 Primary Success Criteria (MVP)
1. Reliability: at least 70% of recommendation attempts result in a successful, rendered set of results.
2. Persistence: Favorites CRUD works reliably:
  - Save and dedupe by country
  - Delete
  - Add/update note (max 100 chars)
  - Enforce favorites cap (50)

### 6.2 Key Performance Indicators
1. LLM_success
- Definition: `valid_JSON AND schema_valid AND destinations_count==5 AND render_success`
- Target: 70% or higher over the MVP beta evaluation window

2. LLM_failure_reason breakdown
- Track counts and rates for:
  - invalid JSON
  - schema validation failure
  - destinations_count != 5
  - duplicates detected
  - region cap violation
  - timeout/network error
  - UI render failure

3. Favorites feature health
- Save success rate
- Dedupe rate (attempted saves for already-saved countries)
- Note update success rate
- Delete success rate
- Cap reached rate (users hitting 50 favorites)

### 6.3 Operational Guardrails (MVP)
1. Timeout handling
- Percentage of requests timing out should be tracked; high timeout rates trigger provider/prompt optimization.

2. Error UX stability
- Error states must be deterministic, readable, and provide a clear next step (Retry or try later).

### 6.4 PRD Checklist Review
1. Is each user story testable?
- Yes. Each story includes observable conditions and pass/fail acceptance criteria.

2. Are the acceptance criteria clear and specific?
- Yes. Criteria specify triggering conditions, UI outcomes, and backend behaviors.

3. Do we have enough user stories to build a fully functional application?
- Yes. Stories cover authentication, questionnaire completion, LLM generation pipeline, result rendering, Favorites CRUD, error states, and observability.

4. Have we included authentication and authorization requirements?
- Yes. US-001 through US-005 and US-032 explicitly cover secure access, session handling, and endpoint protection.
