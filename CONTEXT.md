# InstaSaved

An Android app for organizing saved Instagram posts — a private, Instagram-looking grid of only your saves, where you add notes and tags and sort posts into your own Collections. Instagram is the read-only source of what you saved; all organizing happens in the app, and the app's data never leaves the phone.

## Language

**Post**:
A saved Instagram post or reel that the app keeps track of. Identified by its Instagram shortcode (the `ABC123` in `instagram.com/p/ABC123/` or `/reel/ABC123/`), so the same post reached through different link shapes or tracking parameters is one Post.
_Avoid_: Item, card, clip, save

**Title**:
A short, user-facing label for a Post. Defaults to Description's first sentence and keeps auto-following it as Description changes, until the user hand-edits Title — after which it keeps exactly what they typed.
_Avoid_: Name, headline

**Description**:
A Post's caption text, mirrored from Instagram's caption and kept up to date by Sync — until the user hand-edits it, after which Sync never overwrites it.
_Avoid_: Caption (use it only for Instagram's own field), note

**Post Note**:
Freeform personal text on a single Post — why it was saved, or anything worth remembering about it. Never touched by Sync.
_Avoid_: Note (ambiguous — use Post Note or Collection Note), annotation

**Collection**:
A user-named group of Posts in the app, with a Collection Note and a color. A Post belongs to at most one Collection. Collections are listed alphabetically by name.
_Avoid_: Category, group, folder, album

**Section**:
A user-named group of Collections, shown on the Saved screen under a collapsible header. A name only: no color, no note. A Collection belongs to at most one Section; there is no Section inside a Section. A Section may be empty and exists until the user deletes it, which leaves its Collections with no Section.
_Avoid_: Folder, group, category, using it for a part of a screen

**Instagram Collection**:
A collection as it exists on Instagram, read from the Export. Used to pick the Collection a Post lands in the first time an Export mentions it (the Collection with the same name — see Placement in `docs/sync-spec.md`), and shown on the Post as "also on Instagram in". Never written to, and never moves a Post the user has sorted or that an earlier Export already placed.
_Avoid_: Using bare "Collection" for this — "Collection" always means the app's.

**Collection Note**:
Freeform personal text on a Collection as a whole.
_Avoid_: Note (ambiguous), description

**To sort**:
A Post that has no Collection.
_Avoid_: Unsorted, inbox, uncategorized

**Tag**:
A reusable, user-defined label (a name plus a color from the shared palette). A Post can carry up to 4 Tags, independent of its Collection.
_Avoid_: Category, label, hashtag

**Export**:
A folder that Instagram's "Export your information" writes to the user's Google Drive on a daily schedule, as JSON. The first Export of a schedule contains all saved posts and Instagram Collections; each later one contains only the posts saved since the previous Export, without Instagram Collections. The only automatic source of saved posts.
_Avoid_: Download, dump, backup (Backup is the app's own data)

**Sync**:
Reading the Exports in Google Drive that the app has not applied yet and applying them to the app's Posts according to the sync rules (`docs/sync-spec.md`). One direction only: Instagram → app. It only adds and updates: unsaving a post on Instagram changes nothing in the app.
_Avoid_: Import, refresh

**Share-in**:
Adding a Post by sharing it from Instagram to the app via Android's share sheet. Lands in To sort immediately, without waiting for the next Export.
_Avoid_: Quick add, capture

**New**:
A marker on a Post that Sync added and the user hasn't opened yet. Cleared by opening the Post or by "Mark all as seen".
_Avoid_: Unread, fresh

**Deleted Post**:
A Post the user deleted in the app. Sync never brings it back. It first sits in Recently deleted, then is reduced to a trace keyed by shortcode.
_Avoid_: Tombstone (implementation term), trash

**Recently deleted**:
The first 30 days of a Deleted Post: the full Post (notes, Tags, Collection) is kept and can be restored. After 30 days, or on "Empty now", only the shortcode trace remains.
_Avoid_: Trash, bin

**Thumbnail**:
A small image of a Post, downloaded once and stored on the phone for the grid. Best-effort: a Post without one shows a placeholder card.
_Avoid_: Preview, cover

**Embed**:
Instagram's official embedded player for a Post, shown inside the app to watch videos and swipe carousels. Needs network.
_Avoid_: Player, viewer

**Sync Summary**:
The dismissable in-app recap of the last Sync that changed something ("12 new · 2 captions updated"), shown alongside the sync status ("Synced 2 h ago").
_Avoid_: Notification, report

**Backup**:
A copy of the app's own data (Posts, Collections, Sections, Tags, notes, Deleted Posts) — via Android's automatic backup, or a manual backup file. Never includes Thumbnails.
_Avoid_: Export (that's Instagram's)
