# The app reads Drive with the restricted `drive.readonly` scope, as an unverified app

Meta writes the Export into the user's Drive. The non-sensitive `drive.file` scope only covers files the app created or the user picked by hand, so it cannot see tomorrow's Export. Reading it automatically needs `drive.readonly`, a **restricted** scope.

Publishing an app with a restricted scope normally requires Google's verification and a security assessment. InstaSaved is distributed to one to a few people via GitHub Releases, so the OAuth consent screen is set to **"In production" without verification**: users click through a one-time "Google hasn't verified this app" warning, and the 100-user cap is irrelevant. "Testing" status was rejected because refresh tokens expire every 7 days, forcing a weekly re-sign-in.

The app never writes to Drive and never deletes old Exports; the user cleans those up.

Rejected: a third-party "Drive → local folder" sync app with InstaSaved watching the folder (one more app to install and trust), and a watched-folder or file-picker fallback (the user chose Drive only).
