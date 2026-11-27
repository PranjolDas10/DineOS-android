# DineOS for Android

The owner's phone app for [DineOS](https://github.com/PranjolDas10/DineOS), the operations platform for a family-run restaurant. Built for the floor: wet hands, one thumb, a kitchen where the Wi-Fi drops.

This first slice does one job well: **tonight's closing checklist, checked off offline.**

## What works

- Sign in with the dashboard account. Tokens are encrypted with an Android Keystore key.
- Today: the day's checklists with progress and due time. The whole row is the tap target.
- Offline-first: a tap is saved on the phone at once and sent when there is a network. The chip in the top bar says where things stand (Synced, 2 to send, Offline).
- Expired sessions refresh themselves, once, however many requests fail together.

## How it is built

| Concern | Choice |
|---|---|
| UI | Jetpack Compose, Material 3, colors generated from the dashboard's `tokens.json`, Geist bundled (SIL OFL, `third_party/geist`) |
| State | MVVM: `ViewModel` + `StateFlow`, Hilt for injection |
| Local data | Room is the source of truth; the screen never reads the network |
| Sync | An op queue in Room, drained by WorkManager (network constraint, backoff) |
| Network | Retrofit + OkHttp + kotlinx.serialization; client **generated** from the backend's OpenAPI spec |
| Auth | Access token in memory, refresh on 401 behind a lock (`TokenRefresher`) |

Three ideas carry the offline behavior:

1. **Ops, not state.** A tap becomes a `PendingCheck` with an id made on the phone (UUIDv7) and the time it happened. The server stores each op once and takes the latest per item by that time, so retries, duplicates and late arrivals are all harmless.
2. **One transaction per tap.** The visible row and the queued op change together (`ChecklistDao.check`).
3. **Unsent taps win locally.** When a refresh lands before the queue is sent, `merge` re-applies the queue on top of the server's list, so nothing flickers back.

## Run it

Needs JDK 17 and the Android SDK (platform 37). The backend runs from the DineOS repo with `docker compose up`.

```
./gradlew installDebug                                   # emulator: talks to http://10.0.2.2:8000/
./gradlew installDebug -Pdineos.apiUrl=http://192.168.1.20:8000/   # a phone on the same Wi-Fi
./gradlew testDebugUnitTest
```

Demo account (after `python -m app.seed` in the backend): `demo@dineos.dev` / `demo-password-123`.

## Contract with the backend

`contract/openapi.json` and `design/tokens.json` are copies from the DineOS repo. `scripts/sync-contract.sh` refreshes them and reruns `design/build-tokens.mjs`, which writes `Tokens.kt` for Compose and `res/values*/colors.xml` for the launch window and the launcher icon. The Kotlin API client is generated from the spec on every build, so an API change that breaks the app breaks the build first.

## Not here yet

Receipt scanning, stock counts, orders, photo proof on checklist items, push alerts, the conflict merge screen for record edits.
