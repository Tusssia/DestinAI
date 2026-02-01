# View Implementation Plan: Login

## 1. Overview
The Login view handles passwordless authentication by letting users request a one-time code/link via email, verify it, and establish a secure session, then redirect to `/favorites`.

## 2. View Routing
Path: `/login`

## 3. Component Structure
- `LoginPage` (page shell + layout)
- `LoginHeader`
- `OtpRequestForm`
- `OtpVerifyForm`
- `LoginStatusMessage`
- `LoginFooterLinks`

## 4. Component Details
### LoginPage
- Component description: Page-level container that renders the OTP request and verify flows and handles view state switching.
- Main elements: `main`, `section`, form containers, hidden CSRF token input.
- Handled interactions: receives events from child forms, updates local state.
- Handled validation: ensures email is present before OTP request, ensures either code or token is present for verify.
- Types: `OtpRequestPayload`, `OtpVerifyPayload`, `LoginState`.
- Props: none.

### LoginHeader
- Component description: Displays app name and concise sign-in instructions.
- Main elements: `header`, `h1`, `p`.
- Handled interactions: none.
- Handled validation: none.
- Types: none.
- Props: none.

### OtpRequestForm
- Component description: Collects email for OTP request and shows submission state.
- Main elements: `form`, `label`, `input[type="email"]`, `button`.
- Handled interactions: submit -> `POST /api/auth/otp/request`.
- Handled validation:
  - Email is non-empty and valid format.
  - Disable submit while loading.
- Types: `OtpRequestPayload`, `FormStatus`.
- Props: `onSubmit: (payload: OtpRequestPayload) => void`, `status: FormStatus`.

### OtpVerifyForm
- Component description: Collects email + code or token for verification.
- Main elements: `form`, `input[type="email"]`, `input[type="text"]` for code, optional token input (hidden if not used), `button`.
- Handled interactions: submit -> `POST /api/auth/otp/verify`.
- Handled validation:
  - Email required.
  - Either code or token required (one must be present).
  - Disable submit while loading.
- Types: `OtpVerifyPayload`, `FormStatus`.
- Props: `onSubmit: (payload: OtpVerifyPayload) => void`, `status: FormStatus`.

### LoginStatusMessage
- Component description: Shows status or error messages (e.g., sent, invalid code, rate-limited).
- Main elements: `p` or `div` with role `status`/`alert`.
- Handled interactions: none.
- Handled validation: none.
- Types: `LoginStatus`.
- Props: `status: LoginStatus | null`.

### LoginFooterLinks
- Component description: Optional links (e.g., retry request, help).
- Main elements: `nav`, `a`.
- Handled interactions: link clicks.
- Handled validation: none.
- Types: none.
- Props: none.

## 5. Types
### DTOs (from API)
- `OtpRequestResponseDto`
  - `status: "sent"`
- `OtpVerifyResponseDto`
  - `status: "authenticated"`
  - `user: UserDto`
- `SessionResponseDto`
  - `authenticated: boolean`
  - `user: UserDto | null`
- `UserDto`
  - `id: string` (UUID)
  - `email: string`

### View Models
- `OtpRequestPayload`
  - `email: string`
- `OtpVerifyPayload`
  - `email: string`
  - `code?: string`
  - `token?: string`
- `LoginState`
  - `step: "request" | "verify"`
  - `requestStatus: FormStatus`
  - `verifyStatus: FormStatus`
  - `statusMessage: LoginStatus | null`
- `FormStatus`
  - `"idle" | "submitting" | "success" | "error"`
- `LoginStatus`
  - `"sent" | "authenticated" | "invalid" | "expired" | "rate_limited" | "error"`

## 6. State Management
- Page-level state only; no global store required.
- State variables:
  - `step` to toggle between request and verify.
  - `requestStatus`, `verifyStatus`.
  - `statusMessage` for user feedback.
- Optional helper: `redirectIfAuthenticated()` calls `/api/auth/session` and navigates to `/favorites` if authenticated.

## 7. API Integration
- `POST /api/auth/otp/request`
  - Request: `OtpRequestCommand` (`{ email }`)
  - Response: `OtpRequestResponseDto`
  - Set status to `sent` on success.
- `POST /api/auth/otp/verify`
  - Request: `OtpVerifyCommand` (`{ email, code }` or `{ email, token }`)
  - Response: `OtpVerifyResponseDto` + session cookie
  - On success, redirect to `/favorites`.
- `GET /api/auth/session` (optional on page load)
  - If `authenticated: true`, redirect to `/favorites`.
- Include CSRF token on POSTs and rely on `destinai_session` cookie.

## 8. User Interactions
- Submit email to request OTP: shows “sent” state and switches to verify step.
- Submit code/token: creates session and redirects to `/favorites`.
- Retry request: returns to request step and clears status.

## 9. Conditions and Validation
- Email must be non-empty and valid format.
- Verify form requires email and either `code` or `token`.
- Disable buttons during submit to prevent double requests.
- Prevent revealing whether an email exists; use generic status text.

## 10. Error Handling
- 400 validation errors: show “Check your input and try again.”
- 401/403 verification failure: show “Invalid or expired code.” and keep verify form.
- Rate limiting: show “Please wait before requesting another code.”
- Network/5xx: show “Service is temporarily unavailable. Please try again later.”

## 11. Implementation Steps
1. Create `/login` template with sections for request and verify forms.
2. Add client JS to manage `step` and submission state.
3. Implement `POST /api/auth/otp/request` integration with status messaging.
4. Implement `POST /api/auth/otp/verify` integration and redirect.
5. Add optional session check on load to redirect authenticated users.
6. Add validation, disabling, and accessible status messaging.

