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
- Source (multi-platform) — Instagram only.
- Any compatibility with the desktop app (ADR-0015).
- Sync methods other than the scheduled Drive Export (no watched folder, no manual ZIP pick, no private API).

## v1 scope

**Browse**
- Instagram-like grid of Thumbnails (placeholder card with owner name when missing). Detailed visual design comes from a mockup step before the UI slice.
- Views: All, To sort, each Collection, Recently deleted.
- Collections listed alphabetically. On the Saved screen they are grouped by Section: first the Collections with no Section, then each Section, alphabetically, under a collapsible header (ADR-0014).
- Search across Title, Description, Post Note. Sort by saved date, modified date, Title. Group by Tag.

**Post**
- Detail view with the Embed (videos, carousels), Title, Description, Post Note, Collection, Tags, owner, "open in Instagram".
- Title auto-follows Description's first sentence until hand-edited; Description follows the Instagram caption until hand-edited.
- Up to 4 Tags. Delete (remembered as a Deleted Post) with an Undo snackbar; Recently deleted view with restore for 30 days and "Empty now" (ADR-0012).
- "Also on Instagram in: …" hint listing the post's other Instagram Collections.
- New marker: cleared on open; "Mark all as seen".

**Collections & Tags**
- Create, rename, recolor (shared 12-color palette carried over from Socials Organizer), Collection Note.
- Deleting a Collection asks: keep its Posts (to To sort) or delete them too (ADR-0010).
- Sections: create, rename, delete (its Collections lose their Section, with Undo). A Collection is put in a Section, or in none, from its editor, or in a Section by dragging its cover onto the Section's header, with Undo (ADR-0014).

**Capture & Sync**
- Share-in: share a post from Instagram to InstaSaved → lands in To sort, optionally tag it on the spot. Sharing a Deleted Post asks "You deleted this before. Add it back?" (ADR-0012).
- Connect Google Drive once; daily background Sync + "Sync now"; rules in `sync-spec.md`.
- Sync status always visible ("Synced 2 h ago"), error and stale-Export states, dismissable Sync Summary. No system notification.

**Data safety**
- Android automatic backup (database + settings, no Thumbnails).
- Manual backup file: write / restore (restore is wipe-and-replace). Backups include Recently deleted Posts with their deletion dates.

## Setup the user does once

See README: Instagram scheduled Export (Drive, daily, all time, JSON), Google sign-in with the unverified-app warning, install via Obtainium.

## Open questions

1. **Export file layout** — folder, file names and JSON shape must be confirmed from the first real Export (see `sync-spec.md`).
2. **Does Meta's export schedule expire?** Unknown; the stale-Export warning is the mitigation.
3. **Thumbnail endpoints** are unofficial and may stop working.
4. **UI mockup** — Q6 ideas to explore: 3-column grid, Collections as a circular "highlights" row, To sort as an inbox, Post details in a bottom sheet.
