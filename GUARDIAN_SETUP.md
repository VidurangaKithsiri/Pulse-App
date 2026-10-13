# Guardian flow in this project

1. Create a patient account normally. Create a separate account and select **Create a guardian account** in signup.
2. Sign in to the guardian account using the Patient login form. It opens Incoming patient requests.
3. Sign in to the patient account on another device, open **Guardians** on Home, search the guardian by name or email, and send a request.
4. Sign in as the guardian and accept or reject the request. Acceptance writes `GuardianLinks/{patientUid}/{guardianUid}`.

Existing accounts created before the role field was added are treated as PATIENT. To turn an existing user into a guardian, change `Users/{uid}/role` to `GUARDIAN` in Firebase Realtime Database, then sign out and sign in again.

This revision implements account roles and connection requests. It does **not** deliver medicine reminders or high-risk push notifications. Those need a scheduled server process, Firebase Cloud Messaging, device tokens, and authenticated server-side checks against accepted links. A `HIGH` model classification is only a monitoring flag; it is not a clinical emergency diagnosis.

The Firebase Realtime Database rules must authorize authenticated patients to search guardian profiles and create their own requests, and guardians to read and decide only requests addressed to them. Rules must restrict link creation to the accepted patient/guardian pair; otherwise a modified client can forge or read private connections. The current app has no included database rules, so verify these in the Firebase console before using real health data.

Open the project root in Android Studio, sync Gradle, build the debug variant, then install on two devices or profiles. Verify signup, search, request, Accept, Reject, logout and login. The source archive omits machine-specific caches and build outputs.
