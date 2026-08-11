# Onboarding and Session Hardening Implementation Plan

## Goal

Turn the existing raw authentication endpoints into a usable and production-safe session flow. A new user can register from the React application, an existing browser session is validated when the application starts, expired access tokens can be replaced through a rotating server-managed refresh token, and signing out revokes the refresh token instead of only deleting browser storage.

## Product boundaries

- Spring remains the authentication authority. The browser stores only the short-lived access token and public user profile in `sessionStorage`.
- Refresh tokens are opaque random values. Only a SHA-256 digest is stored in PostgreSQL; the raw token is carried in an HttpOnly cookie scoped to `/api/auth`.
- Login and registration issue both tokens. Refresh rotates the stored token under a pessimistic lock. Logout is idempotent, revokes the presented refresh token when present, and expires the cookie.
- `GET /api/auth/me` is protected by Spring Security and validates a restored access token before the authenticated workspace is rendered.
- A protected frontend request that receives 401 performs one shared refresh attempt, updates the local access token, and retries once. If refresh fails, all local authenticated state is cleared and the login screen explains that the session expired.
- Registration collects full name, email, username, and a password of at least eight characters. It returns the same authenticated session contract as login.
- Production profile configuration must receive `JWT_SECRET` and uses secure refresh cookies plus a 15-minute access-token lifetime. Local development retains an explicit local-only secret and non-secure cookie so the documented localhost workflow remains usable.
- Refresh-token cleanup and account recovery are later operational features; they are not required for this MVP boundary.

## Backend implementation

1. Add Flyway migration `V7__add_refresh_tokens.sql` with the user foreign key, unique token digest, issue/expiry/revocation timestamps, audit timestamps, and lookup/cleanup indexes.
2. Add `RefreshToken` persistence model and repository. The token lookup used for rotation takes a pessimistic write lock so one raw token cannot be successfully rotated twice.
3. Add a focused `RefreshTokenService` that generates cryptographically random opaque values, hashes them before persistence, rejects missing/unknown/revoked/expired/inactive-user tokens with 401, rotates tokens transactionally, and revokes idempotently.
4. Extend `AuthController` so login and registration set refresh cookies, `/refresh` rotates and returns a new access token/profile, `/logout` revokes and clears the cookie, and `/me` continues to return the authenticated profile.
5. Narrow anonymous security matchers to login, registration, refresh, and logout. Remove the duplicate anonymous `POST /api/users` account-creation route; onboarding goes through `/api/auth/register` and always produces a usable authenticated session.
6. Strengthen registration validation and add production profile JWT/cookie configuration without breaking local development.

## Frontend implementation

1. Make API requests include credentials and add `register`, `getCurrentUser`, `refreshSession`, and `logout` calls.
2. Add an accessible sign-in/create-account switch in the existing access panel. Preserve form input and show backend validation or duplicate-account errors in place.
3. Validate stored sessions through `/me` before rendering the workspace. If the access token is stale, rotate through `/refresh`; if the session is no longer renewable, clear local state and show a concise expiry notice.
4. Route every authenticated operation through one retry boundary. Concurrent 401 responses share one refresh request and each operation retries at most once with the new access token.
5. Make Account sign-out call the server before clearing local state. Network failure does not trap the user locally: browser state is still cleared and the server-side refresh token will expire naturally.

## Test-first sequence

1. Add failing refresh-token service tests for hashing, issue, rotation, replay rejection, expiry, inactive accounts, and idempotent revocation.
2. Add failing controller/security tests for refresh cookies, refresh/logout contracts, protected `/me`, validation, and removal of anonymous `/api/users` creation.
3. Implement the backend and run focused Java tests, then the non-database Java suite and package gate with Java 21.
4. Add failing API-client and App tests for credentialed requests, registration, restored-session validation, one-time refresh/retry, shared concurrent refresh, expired-session messaging, and server logout.
5. Implement the frontend, run the full frontend suite and production build, then exercise registration, refresh recovery, and logout in a real browser with controlled API responses.
6. Review the branch for authorization, token leakage, cookie scope, request races, error-state honesty, and accessibility. Run `git diff --check`, commit the reviewed changes, and fast-forward merge only with a clean worktree.
