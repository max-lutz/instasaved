# InstaSaved — product requirements (v1)

## Problem

Instagram's saved posts pile up with no way to annotate them, tag them, or find them again. Instagram's own collections are coarse and its search doesn't cover saves. The Socials Organizer desktop app solved the organizing part, but it lives on a PC, while saving happens on the phone, and syncing meant a manual export + import + review.

## Users

The author, plus possibly a few friends or family. Android only. Installed from GitHub Releases (updates via Obtainium). Not on the Play Store.

## Goals

1. Feel like Instagram, but showing only your saved posts — a thumbnail grid you can browse, open, and watch.
2. Organize: Collections (with notes and colors), Tags, Title, Post Notes.
3. Sync with near-zero clicks after a one-time setup.
4. Private by construction: the app's data never leaves the phone (Android's automatic backup is the one exception — ADR-0001).

## Non-goals (v1)

- iOS.
- Writing anything back to Instagram (moving posts between Instagram Collections, unsaving).
- Offline video / carousel playback (Embeds need network).
- Sections (grouping of Collections) — replaced by emoji prefixes in Collection names, since Collections are listed alphabetically.
- Source (multi-platform) — Instagram only.
- Any compatibility with the desktop app beyond a one-time Desktop Import (ADR-0009).
- Sync methods other than the scheduled Drive Export (no watched folder, no manual ZIP pick, no private API).

## v1 scope

**Browse**
- Instagram-like grid of Thumbnails (placeholder card with owner name when missing). Detailed visual design comes from a mockup step before the UI slice.
- Views: All, To sort, each Collection, No longer saved.
- Collections listed alphabetically.
- Search across Title, Description, Post Note. Sort by saved date, modified date, Title. Group by Tag.

**Post**
- Detail view with the Embed (videos, carousels), Title, Description, Post Note, Collection, Tags, owner, "open in Instagram".
- Title auto-follows Description's first sentence until hand-edited; Description follows the Instagram caption until hand-edited.
- Up to 4 Tags. Delete (remembered as a Deleted Post).
- New marker: cleared on open; "Mark all as seen".

**Collections & Tags**
- Create, rename, recolor (shared 12-color palette carried over from Socials Organizer), Collection Note.
- Deleting a Collection asks: keep its Posts (to To sort) or delete them too (ADR-0010).

**Capture & Sync**
- Share-in: share a post from Instagram to InstaSaved → lands in To sort, optionally tag it on the spot.
- Connect Google Drive once; daily background Sync + "Sync now"; rules in `sync-spec.md`.
- Sync status always visible ("Synced 2 h ago"), error and stale-Export states, dismissable Sync Summary. No system notification.

**Data safety**
- Android automatic backup (database + settings, no Thumbnails).
- Manual backup file: write / restore (restore is wipe-and-replace).
- One-time Desktop Import of a Socials Organizer v6 backup.

## Setup the user does once

See README: Instagram scheduled Export (Drive, daily, all time, JSON), Google sign-in with the unverified-app warning, install via Obtainium.

## Open questions

1. **A post saved in several Instagram Collections** — the app allows one Collection per Post. Default until decided: the first Instagram Collection listed in the Export.
2. **A Share-in post still in To sort, later seen in an Export inside Instagram Collection "a"** — stay in To sort (strict "Sync never moves existing Posts", current rule) or get placed in "a" since the user never sorted it?
3. **Undo a deletion?** Deleted Posts are permanent today. A "Recently deleted" list with restore may be worth adding.
4. **Export file layout** — folder, file names and JSON shape must be confirmed from the first real Export (see `sync-spec.md`).
5. **Does Meta's export schedule expire?** Unknown; the stale-Export warning is the mitigation.
6. **Thumbnail endpoints** are unofficial and may stop working.
7. **UI mockup** — Q6 ideas to explore: 3-column grid, Collections as a circular "highlights" row, To sort as an inbox, Post details in a bottom sheet.
