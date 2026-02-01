# View Implementation Plan: Questionnaire

## 1. Overview
The Questionnaire view collects all required travel preferences using fixed options, validates inputs client-side, and submits the payload to generate recommendations, then routes to `/results`.

## 2. View Routing
Path: `/questionnaire`

## 3. Component Structure
- `QuestionnairePage` (page shell + layout)
- `QuestionnaireHeader`
- `QuestionnaireForm`
- `RadioGroupField`
- `ActivitiesMultiSelect`
- `FormActions`
- `FormStatusMessage`

## 4. Component Details
### QuestionnairePage
- Component description: Page-level container that renders the form and handles submit flow.
- Main elements: `main`, `section`, form wrapper.
- Handled interactions: form submit via child component.
- Handled validation: redirects to `/login` on 401 response.
- Types: `QuestionnaireState`, `RecommendationRequestPayload`.
- Props: none.

### QuestionnaireHeader
- Component description: Displays title and brief instruction text.
- Main elements: `header`, `h1`, `p`.
- Handled interactions: none.
- Handled validation: none.
- Types: none.
- Props: none.

### QuestionnaireForm
- Component description: Collects all required inputs and manages local validation and submit state.
- Main elements: `form`, `fieldset` groups, `input[type="radio"]`, `input[type="checkbox"]`, `button`.
- Handled interactions: input change, submit.
- Handled validation:
  - All fields required.
  - Activities requires at least one selection.
  - Submit disabled until valid.
- Types: `QuestionnaireFormData`, `FormStatus`, `ValidationErrors`.
- Props: `onSubmit: (payload: RecommendationRequestPayload) => void`, `status: FormStatus`.

### RadioGroupField
- Component description: Reusable radio group for single-choice fields.
- Main elements: `fieldset`, `legend`, `input[type="radio"]`, `label`.
- Handled interactions: change -> update form state.
- Handled validation: required selection.
- Types: `RadioOption`.
- Props: `name`, `label`, `options`, `value`, `onChange`, `error`.

### ActivitiesMultiSelect
- Component description: Multi-select field for activities with 1+ requirement.
- Main elements: `fieldset`, `legend`, list of `input[type="checkbox"]`.
- Handled interactions: toggle activity.
- Handled validation: at least one selected.
- Types: `ActivityOption`.
- Props: `values`, `onChange`, `error`.

### FormActions
- Component description: Submit button and optional helper text.
- Main elements: `button[type="submit"]`, optional `span`.
- Handled interactions: submit.
- Handled validation: disable when invalid or loading.
- Types: none.
- Props: `disabled: boolean`, `status: FormStatus`.

### FormStatusMessage
- Component description: Shows submission errors or loading state.
- Main elements: `p` or `div` with role `status`/`alert`.
- Handled interactions: none.
- Handled validation: none.
- Types: `FormStatus`.
- Props: `status: FormStatus`, `message: string | null`.

## 5. Types
### DTOs (request)
- `RecommendationRequestCommand` (server expects):
  - `who: "solo" | "couple"`
  - `travel_type: "backpacking" | "staying_in_one_place"`
  - `accommodation: "camping" | "hostels" | "hotels"`
  - `activities: string[]` (1+)
  - `budget: "very_low" | "medium" | "luxurious"`
  - `weather: "sunny_dry" | "sunny_humid" | "cool" | "rainy"`
  - `season: "winter" | "spring" | "summer" | "autumn"`

### View Models
- `QuestionnaireFormData`
  - `who: string`
  - `travelType: string`
  - `accommodation: string`
  - `activities: string[]`
  - `budget: string`
  - `weather: string`
  - `season: string`
- `RecommendationRequestPayload`
  - same fields as `RecommendationRequestCommand`, with `travel_type` in snake case.
- `ValidationErrors`
  - per-field error messages: `{ who?: string, travelType?: string, ... }`
- `FormStatus`
  - `"idle" | "submitting" | "error"`
- `RadioOption`
  - `value: string`
  - `label: string`
- `ActivityOption`
  - `value: string`
  - `label: string`

## 6. State Management
- Local state in `QuestionnaireForm` only.
- State variables:
  - `formData: QuestionnaireFormData`
  - `errors: ValidationErrors`
  - `status: FormStatus`
- Helper functions:
  - `validate(formData)` -> `ValidationErrors`
  - `toPayload(formData)` -> `RecommendationRequestPayload`

## 7. API Integration
- `POST /api/recommendations`
  - Request: `RecommendationRequestPayload`
  - Response: `RecommendationResponseDto`
  - On success: store payload in `history.state` or `sessionStorage` and navigate to `/results`.
- Include CSRF token header and rely on `destinai_session` cookie.

## 8. User Interactions
- User selects options for all questions.
- Submit enabled only when all required fields are valid.
- On submit, show loading state and prevent double submissions.
- On success, route to `/results`.

## 9. Conditions and Validation
- All fields are mandatory.
- Activities must have at least one selection.
- Values must match allowed enums.
- Client-side validation mirrors server constraints and prevents invalid submission.

## 10. Error Handling
- 400: show field-level or generic form error.
- 401: redirect to `/login`.
- Network/5xx: show “Service is temporarily unavailable. Please try again later.”

## 11. Implementation Steps
1. Create `/questionnaire` template with all fields and labels.
2. Implement local form state management and validation.
3. Map UI values to API payload wire values.
4. Integrate `POST /api/recommendations` with loading and error handling.
5. On success, persist payload for Results view and navigate to `/results`.
6. Add accessibility details: `fieldset/legend`, `aria-invalid`, and error messages.

