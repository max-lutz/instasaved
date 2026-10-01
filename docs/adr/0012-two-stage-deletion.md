# Deleting a Post is two-stage: Recently deleted for 30 days, then a shortcode trace

Deleting a Post shows an Undo snackbar, then moves the Post to **Recently deleted**, keeping it whole (Post Note, Tags, Collection, Title, Description). After 30 days, or when the user taps "Empty now", it is reduced to the shortcode-only trace that keeps Sync from bringing it back. Both stages are Deleted Posts: Sync ignores them alike (R2). This guards against both a slip of the finger and a bulk mistake such as deleting a Collection together with its Posts (ADR-0010).

**Restore** puts the Post back in its Collection. If that Collection was deleted in the same action (the "delete Collection and its Posts" case), it is recreated with the same name and color, so restoring them all rebuilds it; otherwise a missing Collection means To sort. This needs a per-deletion action id stored with each recently-deleted Post.

**Share-in of a Deleted Post** asks "You deleted this before. Add it back?" — sharing is a deliberate act, unlike Sync. Yes restores the full Post if it is still in Recently deleted, or creates a fresh Post in To sort if only the trace remains.

Backups include Recently deleted Posts with their deletion dates, so a restore keeps the 30-day countdown.

Rejected: permanent deletion with only a confirm dialog (notes and Tags lost for good on a mistake), and silently ignoring Share-in of a Deleted Post (looks like the app is broken).
