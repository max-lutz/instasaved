# No data compatibility with Socials Organizer, except a one-time Desktop Import

The desktop app (Socials Organizer, Node + SQLite) is paused, not deleted. InstaSaved does not keep a shared backup format with it and does not sync with it; its own Backup format is free to evolve.

The one bridge is a one-time **Desktop Import** of a desktop backup file (`schema_version: 6`), so existing notes, Tags and Collections aren't lost. Mapping:
- Collections, Tags, Posts, Post Notes and Collection Notes carry over. Shortcodes are extracted from links.
- Sections are dropped (Collections keep their names; the user groups with emoji prefixes instead). `source` and `reimport_dismissed` are dropped.
- `title_manual` carries over. Desktop has no Description-edited flag, so imported Posts are marked **Description hand-edited** — protecting edits made on desktop at the cost of not refreshing those captions.
- Posts whose `provenance` says they came from an Instagram import are marked *seen in an Export*, so they can become No longer saved; manually added ones are not.
- `deleted_posts` become Deleted Posts.
