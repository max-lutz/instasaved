# The app's own data never leaves the phone

InstaSaved has no server, no account system and no cloud database. Posts, Collections, Tags, notes and Deleted Posts live in a local SQLite database (Room) on the phone. The app makes no network calls except to *download*: the Export from Google Drive, and Thumbnails and Embeds from Instagram. No analytics, no crash reporting.

The one deliberate exception is **Android's automatic backup** to the user's Google account (end-to-end encrypted when the device has a screen lock). Losing every note and tag with a lost phone was judged worse than this exception. It covers the database and app settings only; Thumbnails live in a directory excluded from backup, because they are re-downloadable and would blow the 25 MB per-app backup quota. A manual backup file (written wherever the user picks) stays available as a second safety net.

Considered and rejected: keeping the Socials Organizer Node server as the source of truth with the phone as a client (needs the PC reachable from anywhere, e.g. Tailscale), and phone + cloud sync (real conflict-handling work, and data leaves the phone).
