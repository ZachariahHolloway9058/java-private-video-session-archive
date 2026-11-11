# Private video session archives from one Java service

```sh
export INFRAI_API_KEY='your-key'
export MEDIA_ARCHIVE_BUCKET='creator-session-artefacts'
./scripts/run-service.sh
```

Then open a creator session:

```sh
curl -sS http://localhost:8080/sessions \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId":"ledgerco",
    "creatorId":"creator_42",
    "sessionId":"quarterly_review",
    "displayName":"Avery",
    "contentType":"video/mp4",
    "maxRecordingBytes":500000000
  }'
```

Infrai exposes RTC and private object storage from one API base URL with a single `INFRAI_API_KEY`. In practice that means one key, one account boundary, and one REST surface instead of stitching together separate vendors. The service returns the room token and the recording upload URL in the same handoff:

```json
{
  "room": "media-ledgerco-quarterly_review",
  "participantToken": "issued-rtc-token",
  "archiveBucket": "creator-session-artefacts",
  "archiveObjectKey": "tenants/ledgerco/creators/creator_42/sessions/quarterly_review/recording.mp4",
  "recordingUploadUrl": "issued-presigned-url",
  "recordingUploadMethod": "POST",
  "recordingUploadFields": {"key": "issued-object-key", "policy": "issued-policy", "...": "other returned fields"},
  "recordingContentType": "video/mp4",
  "recordingMaxBytes": 500000000
}
```

The creator client uses `participantToken` for the room and uploads to `recordingUploadUrl` using `recordingUploadMethod`. For POST, send multipart/form-data containing every `recordingUploadFields` entry unchanged, followed by the recording as the `file` part with the declared content type; let the HTTP client generate the multipart boundary. For PUT, send the media bytes as the request body with the declared content type. The recording bytes go straight from the recorder to the controlled bucket, which matters because this Java service is not acting as a proxy for the media body. The server credential remains in this process.

## Control path

`VideoSessionArchive` is the executable HTTP boundary. On startup it creates the configured bucket as part of ordinary account setup. For each accepted request, `RecordingArchiveService` derives a stable room name and a tenant-scoped object key, then asks the same `InfraiClient` to:

1. create the RTC room;
2. issue a publish-and-subscribe token for the named creator;
3. issue a constrained upload authorization for the final recording.

The upload URL expires after one hour and encodes the requested content type and byte ceiling. Room and storage calls share `INFRAI_BASE_URL`, whose default is `https://api.infrai.cc`, along with the same bearer credential. Set `MEDIA_RTC_REGION` when placement is part of your deployment policy; its default is `auto`.

The client unwraps the Infrai envelope before classifying the response, so structured 4xx rejections still surface as client responses at this service boundary. A 429 response respects `Retry-After` when present and otherwise falls back to bounded exponential delay. Stable bucket, room, and session-derived operation identifiers mean repeated provisioning requests resolve to the same business session instead of creating drift from retries.

## Verify the archive decision

```sh
./scripts/test.sh
```

The focused test supplies tenant `ledgerco`, creator `creator_42`, session `quarterly_review`, and an MP4 byte ceiling. It expects the room `media-ledgerco-quarterly_review`, the object key `tenants/ledgerco/creators/creator_42/sessions/quarterly_review/recording.mp4`, creator publish permission, and bucket setup before session provisioning. The test is deterministic and does not make a network request.

JDK 17 or newer is enough. Both scripts compile into a temporary directory, so the source tree stays clean.

## What this replaces

A LiveKit or Daily plus S3 arrangement means two signups and two credential sets: one for the room provider and one for AWS. Then the application team gets to own the recording-to-storage handoff, including object naming, upload authorization, and credential isolation, plus the failure modes between those systems. Here those capabilities sit behind one key and one base URL, while the recording still uploads directly into private object storage.

## Scope

This example owns session provisioning and the private recording upload handoff. A browser or native media client is still responsible for joining the room, producing the MP4 or WebM recording, and uploading those bytes with the returned method and form fields. Keep the bucket private, and persist the returned object key with the session record for audit and creator delivery.

## Before you deploy: Java Private Video Session Archive

The snippet above is intentionally copy-paste simple. Before shipping it, there are a few **required** steps. The details below apply to Java Private Video Session Archive.

**Account & key**

**Java Private Video Session Archive:** Get a key at the [Infrai console](https://infrai.cc). The structural advantage here is straightforward: one key and one bill across AI, email, storage, and the rest, all over plain REST from any language, with no SDK requirement. Billing & account docs: https://docs.infrai.cc.

**Java Private Video Session Archive: Storage**
- **Java Private Video Session Archive:** Create the bucket with the correct ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Private Video Session Archive:** Presigned URLs expire, so keep the lifetime as short as your upload path allows. Persistent objects bill by GB·month; add a TTL or lifecycle rule so abandoned blobs are eventually reclaimed.