# No data compatibility with Socials Organizer, except a one-time Desktop Import

**Superseded by ADR-0015**: the Desktop Import is removed, and InstaSaved has no bridge from Socials Organizer at all.

The desktop app (Socials Organizer, Node + SQLite) is paused, not deleted. InstaSaved does not keep a shared backup format with it and does not sync with it; its own Backup format is free to evolve.

The one bridge is a one-time **Desktop Import** of a desktop backup file (`schema_version: 6`), so existing notes, Tags and Collections aren't lost. Mapping:
- Collections, Tags, Posts, Post Notes and Collection Notes carry over. Shortcodes are extracted from links.
- Sections are dropped (Collections keep their names; the user groups with emoji prefixes instead — superseded by ADR-0014: the app has its own Sections, which the import still does not fill). `source` and `reimport_dismissed` are dropped.
- `title_manual` carries over. Desktop has no Description-edited flag, so imported Posts are marked **Description hand-edited** — protecting edits made on desktop at the cost of not refreshing those captions.
- Posts whose `provenance` says they came from an Instagram import are marked *seen in an Export*, so they can become No longer saved; manually added ones are not.
- `deleted_posts` become Deleted Posts.

The import **adds to** what the app holds; it never replaces anything, so it can run after Share-ins or a first Sync, and running it twice changes nothing:
- A Collection or Tag whose name is already taken (ignoring case) is the app's one; a Collection without a Collection Note takes the desktop one.
- A Post the app already has only gets what it lacks: a Collection if it is in To sort, Tags if it has none, a Post Note and owner if empty, and the desktop Title and Description unless hand-edited in the app.
- A Deleted Post of the app stays deleted, and a desktop `deleted_posts` entry never deletes a Post the app holds.
- A desktop Post whose link has no shortcode, or whose shortcode another desktop Post already has, is skipped.

Imported Posts keep the desktop's creation date as their saved date: the desktop backup does not hold the date they were saved on Instagram.
