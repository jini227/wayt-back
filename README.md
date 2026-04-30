# Wayt Backend

Java 21 + Spring Boot backend for the Wayt appointment arrival app.

## Local Run

The project includes a local H2 configuration so the API can run without an external database.

Create `.env.local` or set these environment variables before a real login test:

```bash
PORT=19191
WAYT_PUBLIC_BASE_URL=http://localhost:19191
KAKAO_REST_API_KEY=your_kakao_rest_api_key
KAKAO_CLIENT_SECRET=your_kakao_client_secret
KAKAO_SCOPES=
```

The mobile app opens the backend Kakao OAuth URL, then the backend exchanges the authorization code, retrieves the Kakao profile, and redirects back to the app with a Wayt session. Set `KAKAO_SCOPES=profile_nickname,profile_image` only after those consent items are enabled in Kakao Developers.

```powershell
$env:JAVA_HOME='C:\_hyozk\dev\map\map-it-back\.jdks\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootRun
```

Default port: `19191`

Health check:

```bash
curl http://localhost:19191/actuator/health
```

For now, local testing uses H2. When deploying later, create a managed PostgreSQL database and set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` with the `prod` profile.

## Main API

- `POST /api/auth/kakao`
- `GET /api/auth/session`
- `GET /api/me`
- `PATCH /api/me/profile`
- `POST /api/me/avatar`
- `DELETE /api/me/avatar`
- `GET /api/uploads/avatars/{filename}`
- `POST /api/appointments`
- `GET /api/appointments/upcoming`
- `GET /api/appointments/{appointmentId}`
- `POST /api/appointments/{appointmentId}/invite-link`
- `POST /api/appointments/{appointmentId}/invites/by-wayt-id`
- `GET /api/invites/{token}`
- `POST /api/invites/{inviteId}/accept`
- `POST /api/appointments/{appointmentId}/locations`
- `POST /api/appointments/{appointmentId}/manual-arrival`
- `POST /api/appointments/{appointmentId}/status-logs`
- `GET /api/history`
- `POST /api/address-book`
- `GET /api/address-book`
- `DELETE /api/address-book/{entryId}`
- `POST /api/places`
- `GET /api/places`
- `PATCH /api/places/{placeId}`
- `DELETE /api/places/{placeId}`
- `POST /api/push-tokens`

## ETA And Subscription Policy

MVP free policy:

- Call the external ETA/directions API once per participant after location sharing starts and the first valid location is received.
- Allow one extra edge-check call when the local estimate enters the late-boundary window, currently appointment time plus grace minutes within 10 minutes.
- Allow a recalculation when travel mode changes.
- Use local location/radius/time rules for ongoing status changes between external ETA calls.

V3 subscription plan:

- `PLUS`: KRW 2,900/month, automatic ETA refresh every 10 minutes.
- `PRO`: KRW 4,900/month, automatic ETA refresh every 3 minutes.
- Free users keep the one-time ETA plus edge-check policy.

The backend already exposes `subscriptionTier`, `etaRefreshPolicy`, `etaCalculatedAt`, `etaNextEligibleAt`, and `etaApiCallCount` in API responses so the app can show or gate this later.

## WebSocket

- Endpoint: `ws://localhost:19191/ws`
- Send location: `/app/appointments/{appointmentId}/location`
- Send status: `/app/appointments/{appointmentId}/status`
- Subscribe: `/topic/appointments/{appointmentId}/presence`

## Checks

```powershell
$env:JAVA_HOME='C:\_hyozk\dev\map\map-it-back\.jdks\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test
```
