# Telemetry sync contract

Android client ↔ FastAPI backend. The app uses HTTPS with bearer authentication; the current
build authenticates users locally and sends no token, but the upload path already supports one.

## Upload records

`POST /api/v1/telemetry/records/batch`

Headers:

- `Content-Type: application/json`
- `Authorization: Bearer <jwt>` (when the backend login flow is enabled)

Body:

```json
{
  "records": [
    {
      "id": "9f1c2a7e-6b4d-4d0e-9a51-2f6f1b0c8e11",
      "vin": "93YBSR7RHEJ298116",
      "collected_at": "2026-11-02T14:03:11.482Z",
      "user": "admin",
      "readings": [
        { "metric": "rpm", "unit": "rpm", "available": true, "value": 1065.0 },
        { "metric": "speed", "unit": "km/h", "available": true, "value": 42.0 },
        { "metric": "coolant_temperature", "unit": "°C", "available": true, "value": 91.0 },
        { "metric": "engine_load", "unit": "%", "available": true, "value": 23.5 },
        { "metric": "throttle_position", "unit": "%", "available": true, "value": 12.0 },
        { "metric": "intake_manifold_pressure", "unit": "kPa", "available": true, "value": 35.0 },
        { "metric": "maf", "unit": "g/s", "available": true, "value": 4.2 },
        { "metric": "fuel_level", "unit": "%", "available": false, "value": null }
      ]
    }
  ]
}
```

Rules:

- At most 100 records per request.
- `id` is a client-generated UUID and the deduplication key.
- `vin` and `rpm` are nullable. An unavailable parameter is `available: false` and `value: null`;
  it is never sent as zero.
- `collected_at` is ISO-8601 in UTC.
- `user` is an untrusted hint until JWT authentication exists. Authorization must not depend on it.

Response (`200 OK` when the batch was processed):

```json
{
  "accepted": ["9f1c2a7e-6b4d-4d0e-9a51-2f6f1b0c8e11"],
  "duplicates": ["a1b2c3d4-0000-0000-0000-000000000000"],
  "rejected": [
    { "id": "c3d4e5f6-0000-0000-0000-000000000000", "code": "invalid_rpm", "message": "rpm must be >= 0" }
  ]
}
```

- `accepted` and `duplicates` both mean the record is safely stored; the app marks both as uploaded.
- Per-record validation failures are returned in `rejected`; the app keeps the record locally and
  marks it rejected instead of retrying it forever.
- Server should insert with `ON CONFLICT (id) DO NOTHING` and return already-known ids as
  `duplicates`.

Status codes:

| Status | App behavior |
| --- | --- |
| 200 | Apply `accepted` / `duplicates` / `rejected` |
| 401, 403 | Keep records pending, stop the run, surface an auth error |
| 400, 404, 405, 413, 415, 422 | Permanent failure; keep records pending, surface a server error |
| 408, 429, 5xx | Keep records pending and retry with exponential backoff |
| Network timeout / no response | Keep records pending and retry with the same ids |

Delivery is at-least-once. The app never marks a record uploaded before a confirmed response.

## Retention

After each sync attempt the app deletes `UPLOADED` records (and their readings) whose
`uploaded_at` is older than **3 days**. Pending and rejected records are never pruned.

## Configuring the endpoint

The base URL is a build config field:

```bash
./gradlew assembleDebug -Pdrivedata.debugApiBaseUrl=http://192.168.0.50:8000/
./gradlew assembleRelease -Pdrivedata.apiBaseUrl=https://api.example.com/
```

Debug builds allow cleartext HTTP for local development only. Release builds are HTTPS-only and
never disable certificate validation.
