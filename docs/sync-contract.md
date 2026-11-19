# Telemetry sync contract

Android client ↔ FastAPI backend. Requests use bearer JWT authentication. Local Room storage is the
outbox; the backend is authoritative for authorization.

## Authentication

`POST /api/v1/auth/token`

```json
{ "name": "admin", "password": "..." }
```

Returns `access_token`, `refresh_token`, `token_type`, `expires_in` (900 s) and
`refresh_expires_in` (2 592 000 s). Send the access token as `Authorization: Bearer <access_token>`.

`POST /api/v1/auth/refresh` accepts the refresh token as `Authorization: Bearer <refresh_token>` and
returns a new pair. Refresh tokens are single-use: reusing a rotated token ends the session and the
user must sign in again. The client persists the token pair so a restarted app can resume syncing.

`POST /api/v1/auth/logout` accepts the refresh token in the same header and ends the session.

## Upload records

`POST /api/v1/telemetry/batches`

Headers:

- `Content-Type: application/json`
- `Authorization: Bearer <access_token>`

Body:

```json
{
  "records": [
    {
      "id": "9f1c2a7e-6b4d-4d0e-9a51-2f6f1b0c8e11",
      "vin": "93YBSR7RHEJ298116",
      "collected_at": "2026-11-02T14:03:11.482Z",
      "readings": [
        { "metric": "rpm", "unit": "rpm", "available": true, "value": 1065.0 },
        { "metric": "fuel_level", "unit": "%", "available": false, "value": null }
      ]
    }
  ]
}
```

Rules:

- 1 to 500 records per request, each with a client-generated UUID `id`.
- `id` is the idempotency key; resending a known record is safe and it is reported as accepted.
- `vin` is required. Authorization is per record: the signed-in user must own or drive that vehicle.
- 1 to 64 readings per record. An unavailable parameter is `available: false` and `value: null`;
  it is never sent as zero.
- `collected_at` is ISO-8601 with a time zone.
- There is no `user` field; the JWT identifies the sender.

Response (`202 Accepted` when the batch was processed):

```json
{
  "batch_id": "1ca1c57c-7d8d-4e1b-8539-cb314c5d873f",
  "received_at": "2026-11-17T20:40:51.522107+00:00",
  "accepted": 1,
  "rejected": [
    {
      "index": 0,
      "id": "c3d4e5f6-0000-0000-0000-000000000000",
      "vin": "93YBSR7RHEJ298117",
      "reason": "vehicle_not_authorized"
    }
  ]
}
```

`accepted` is a count of published records, including duplicates. `rejected` contains the records
that were not published and must not be resent unchanged. The app treats every record not listed in
`rejected` as acknowledged, but only if `accepted` matches that count; otherwise it retries with
the same ids.

Status codes:

| Status | App behavior |
| --- | --- |
| 202 | Apply `accepted` / `rejected`; mark acknowledged records uploaded |
| 401 | Refresh the access token once and retry; if refresh fails, require reauthentication |
| 403 | Keep records pending, surface an auth error, do not retry automatically |
| 400, 404, 405, 413, 415, 422 | Permanent failure; mark the batch rejected locally, retain data, stop retrying |
| 408, 429, 5xx | Keep records pending and retry with exponential backoff |
| Network timeout / no response | Keep records pending and retry with the same ids |

Delivery is at-least-once. The app never marks a record uploaded before a confirmed response and
never deletes pending records because of network or authentication failures.

## Retention

After each sync attempt the app deletes `UPLOADED` records (and their readings) whose
`uploaded_at` is older than **3 days**. Pending and rejected records are never pruned.

## Configuring the endpoint

The base URL is a build config field:

```bash
./gradlew assembleDebug -Pdrivedata.debugApiBaseUrl=http://192.168.0.50:5000/
./gradlew assembleRelease -Pdrivedata.apiBaseUrl=https://api.example.com/
```

Debug builds allow cleartext HTTP for local development only. Release builds are HTTPS-only and
never disable certificate validation. From an Android emulator, the host's `localhost:5000` is
`http://10.0.2.2:5000/`.
