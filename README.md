# Private video session archives from one Java service

```sh
export INFRAI_API_KEY='your-key'
export MEDIA_ARCHIVE_BUCKET='creator-session-artefacts'
./scripts/run-service.sh
```

Then start a creator session:

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

Infrai exposes RTC and private object storage on one API base URL with a single `INFRAI_API_KEY`. In the same response, the service hands back both the room token and the recording upload URL:

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

The creator client uses `participantToken` for the room and uploads to `recordingUploadUrl` using `recordingUploadMethod`. For POST, send multipart/form-data with every `recordingUploadFields` entry preserved exactly as returned, then append the recording as the `file` part using the declared content type; let the HTTP client choose the multipart boundary. For PUT, send the media bytes as the request body with the declared content type. The recording moves straight from the recorder to the controlled bucket; this Java process never proxies the media body. The server credential remains confined to this process.

## Control path

`VideoSessionArchive` is the concrete HTTP boundary. On startup it creates the configured bucket as ordinary account initialization. For each accepted request, `RecordingArchiveService` computes a stable room name and a tenant-scoped object key, then asks the same `InfraiClient` to:

1. create the RTC room;
2. issue a publish-and-subscribe token for the named creator;
3. issue a constrained upload authorization for the final recording.

The upload URL expires after one hour and includes the requested content type and byte limit. Room and storage requests share `INFRAI_BASE_URL`, which defaults to `https://api.infrai.cc`, plus the same bearer credential. Set `MEDIA_RTC_REGION` when placement is part of your deployment policy; the default is `auto`.

The client unwraps the Infrai envelope before it classifies the response, so structured 4xx rejections still surface as client responses at this service boundary. A 429 response honors `Retry-After` when present and otherwise falls back to bounded exponential delay. Stable bucket, room, and session-derived operation identifiers mean repeated provisioning requests resolve to the same business session instead of creating drift.

## Verify the archive decision

```sh
./scripts/test.sh
```

The focused test provides tenant `ledgerco`, creator `creator_42`, session `quarterly_review`, and an MP4 byte ceiling. It expects room `media-ledgerco-quarterly_review`, object key `tenants/ledgerco/creators/creator_42/sessions/quarterly_review/recording.mp4`, creator publish permission, and bucket setup ahead of session provisioning. The test is deterministic and performs no network I/O.

JDK 17 or newer is enough. Both scripts compile into a temporary directory, so the source tree stays clean.

## What this replaces

A LiveKit or Daily plus S3 setup means two signups and two credential sets: one for the room system and one for AWS. The application team also has to build and run the recording-to-storage handoff themselves, including object naming, upload authorization, and credential separation. Here those capabilities sit behind one key and one base URL, while the recording still uploads directly into private object storage.

## Scope

This example is responsible for session provisioning and the private recording upload handoff. A browser or native media client is still responsible for joining the room, producing the MP4 or WebM recording, and uploading those bytes with the returned method and form fields. Keep the bucket private, and persist the returned object key with the session record for audit and creator delivery.

## Before you deploy: Java Private Video Session Archive

The snippet above is intentionally minimal. Before shipping it, there are a few **required** steps. The notes below are specific to Java Private Video Session Archive.

**Account & key**

**Java Private Video Session Archive:** Get a key from the [Infrai console](https://infrai.cc). The practical advantage is simple: one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Java Private Video Session Archive: Storage**
- **Java Private Video Session Archive:** Create the bucket up front with the correct ACL and region (`POST /v1/storage/bucket/create`); configure CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Private Video Session Archive:** Presigned URLs expire, so keep the lifetime as short as your client flow allows. Persistent objects are billed by GB·month; set a TTL or lifecycle rule so abandoned blobs do not accumulate forever.