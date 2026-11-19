# VELTRIX MESSENGER — Native Android

This is the native Android client track, built with Kotlin and Jetpack Compose. It is separate from the existing Cloudflare Pages web client, which remains intact.

## Current phase
- Native Android project scaffold
- RTL Persian login UI
- Supabase Kotlin modules added (Auth, PostgREST, Realtime, Storage)
- GitHub Actions workflow to build and upload a debug APK

## Build
From this directory, run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Security
Only the Supabase publishable key is used in the client. Never add a Supabase service-role or secret key to the Android app. Authentication, RLS and Storage policies remain enforced by Supabase.

## Roadmap
1. Wire native login, signup, session persistence and logout to the existing Supabase project.
2. Implement conversations, memberships and realtime message streams.
3. Add image, audio and video upload/download.
4. Add native WebRTC calling and call notifications.
5. Add tests, release signing and a signed release APK.
