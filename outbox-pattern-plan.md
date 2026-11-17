
# Outbox Pattern Implementation Plan

## Goal

Implement reliable offline-first telemetry synchronization. Records must be stored locally before being sent to the backend and synchronized automatically when internet connectivity becomes available.

## 1. Record Collection

Whenever telemetry is collected:

1. Generate a unique UUID.
2. Create the telemetry record.
3. Persist it locally as `PENDING`.
4. Request synchronization.

Collection must never depend on internet availability.

## 2. Synchronization Flow

Implement a background synchronization process using WorkManager.

1. Check internet connectivity.
2. Retrieve pending records.
3. Ensure a valid access token is available.
4. Refresh the token if necessary.
5. Send pending records in batches.
6. Mark successfully synchronized records as UPLOADED.

Use unique WorkManager jobs to prevent concurrent synchronization processes.

## 3. Backend Integration

**Endpoint:** `POST /api/v1/telemetry/batches`

**Header:**
`Authorization: Bearer <access_token>`

**Payload:**

```json
{
  "records": [
    {
      "id": "uuid",
      "vin": "string",
      "collected_at": "2026-11-17T20:29:47.947Z",
      "readings": [
        {
          "metric": "string",
          "unit": "string",
          "available": true,
          "value": 0
        }
      ]
    }
  ]
}
```

Send multiple pending records in a single request.

Only mark records as synchronized after successful backend confirmation.

The backend must handle duplicate record IDs idempotently to support safe retries.

## 4. Retry and Error Handling

- **No internet:** Keep records pending.
- **401 Unauthorized:** Refresh the token and retry once.
- **Expired/revoked refresh token:** Require reauthentication.
- **Server errors (5xx):** Keep records pending and retry later.
- **Client errors (4xx):** Log the failure and avoid endless retries.
- **Successful synchronization:** Mark records as synchronized.

Never delete pending records because of network or authentication failures.

## 5. Synchronization Triggers

Trigger synchronization:

- After new telemetry records are collected.
- When internet connectivity becomes available.
- When the application starts, if pending records exist.

## 6. Implementation Order

1. Implement local persistence of pending records.
2. Implement the batch synchronization request.
3. Implement the background synchronization process.
4. Integrate authentication and token refresh.
5. Implement retry and error handling.
6. Test offline collection, application restarts, token expiration, and successful synchronization.

## Expected Result

- Telemetry collection works offline.
- Records survive application restarts.
- Synchronization happens automatically when connectivity returns.
- Failed requests never cause local data loss.
- Successfully synchronized records are not unnecessarily resent.
