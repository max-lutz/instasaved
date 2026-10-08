# Sync spec

How an Export becomes changes to the app's Posts. Terms are defined in `CONTEXT.md`. The rules are written to be implemented as a **pure function** — `(appState, parsedExport) -> (changes, summary)` — and tested case by case, with Drive, Room and WorkManager kept outside it.

## Pipeline

1. **Find** the Export folders in Google Drive (read-only access, see ADR-0004) that have not been applied yet, by Drive file id. A folder is an Export by its name (see Export file format), wherever it sits in Drive.

Then, for each of them, oldest first (R7):

2. **Download** its saved-posts JSON, and its saved-collections JSON if it has one (see Export file format).
3. **Parse** into `ExportedPost { shortcode, url, caption?, ownerUsername?, ownerName?, savedAt, instagramCollections: [name] }`.
4. **Diff** against app state with rules R1–R6; apply all changes in one DB transaction, together with the record that this Export (Drive file id) is applied.

And once for the whole Sync:

5. **Record** the sync status (time, date of the newest Export applied) and the Sync Summary, totalled over the Exports applied.
6. **Queue** Thumbnail downloads for newly added Posts (best-effort, outside the transaction).

An Export that fails (download or parse error) is not recorded as applied: the error shows in the sync status, the other Exports are still applied, and the next Sync tries it again. A folder without a saved-posts JSON fails the same way, rather than counting as an Export with no posts: Meta may still be writing it.

Restoring a Backup forgets which Exports were applied, so the next Sync applies them all again: those applied after the Backup was taken bring back their posts, and the rules leave everything else as it is (R2, R3, R6).

## Normalization

- **Identity** is the shortcode extracted from `instagram.com/(p|reel|tv)/<shortcode>`. Query strings (`?igsh=…`, `?img_index=…`) and the path type are ignored for matching. The original URL is stored for opening the post.
- **Text repair**: Meta's JSON stores UTF-8 bytes as Latin-1 code points (`â€™` for `’`). Re-encode each string as Latin-1 bytes and decode as UTF-8 before use.
- **Collection names** match case-insensitively, after trimming.

## Rules

An Export lists only the posts saved since the previous Export; only the first Export of a schedule lists them all (ADR-0013). So the rules only add and update: a Post that an Export does not mention is left alone, and unsaving a post on Instagram is never seen by the app.

Let `E` be the set of shortcodes in the Export.

**R1 — New Post.** Shortcode in `E`, not in the app, not a Deleted Post → create a Post:
- Description = caption, Title auto-derived, owner fields filled, saved-at = Export's saved timestamp.
- Collection = picked by **Placement** (below). No Instagram Collection → To sort.
- Instagram Collection names = all of the post's Instagram Collections, stored on the Post for display only ("also on Instagram in: b, c"); refreshed on every Sync, never used to move a Post.
- Marked **New**. Marked as *seen in an Export*.

**Placement** (used by R1 and R3a). Given the post's Instagram Collections:
1. If one or more match an existing app Collection by name, take the first of those alphabetically.
2. Otherwise take the first Instagram Collection alphabetically and **create an app Collection with that name** (next palette color, empty note).

An Export after the first carries only the Instagram Collections created since the previous Export (see Export file format), so most of its new Posts land in To sort; Placement does its work on a complete Export.

Alphabetical order makes the result independent of how Meta orders the Export. For the same reason, "existing" in step 1 means existing before this Sync: a Collection created earlier in the same Sync is reused by step 2, but does not attract a post away from its alphabetically first Instagram Collection.

**R2 — Deleted Post.** Shortcode in `E` and is a Deleted Post — whether still in Recently deleted or already reduced to its trace → ignore silently. Deleted Posts never come back through Sync (only the user can restore one, see ADR-0012).

**R3 — Existing Post still saved.** Shortcode in `E` and in the app →
- Never changes Collection, Tags, Post Note, or a hand-edited Title.
- If Description has not been hand-edited and the caption differs → update Description (Title follows if not hand-edited). Count as "caption updated".
- Fill owner fields if empty. Refresh the stored Instagram Collection names.
- Mark as *seen in an Export*.
- Not marked New (it was already in the app — this includes Posts added by Share-in).

**R3a — First-sighting placement.** Applies within R3, evaluated *before* marking the Post seen: if the Post has **never been seen in an Export** and is **still in To sort**, and the post has at least one Instagram Collection → set its Collection by Placement (which may create a Collection). It happens once; afterwards the Post is never moved by Sync, including if the user puts it back in To sort. A Post the user already put in a Collection is untouched (ADR-0011).

**R4 — Existing Post missing from the Export.** Shortcode not in `E`, Post in the app → nothing.

**R5 — Collections are only created by Placement** (R1 and R3a). An Instagram Collection whose posts are all already in the app (wherever the user put them) creates nothing. A renamed or deleted app Collection is not tracked: if a new post arrives in Instagram Collection "a" and no app Collection is named "a", a fresh "a" is created.

**R6 — Idempotent.** Applying the same Export twice produces no changes the second time.

**R7 — Every Export, once, oldest first.** Each Export in Drive is applied exactly once: skipping one would lose the posts saved that day. Oldest is by the day in the folder's name, then by when Drive got the folder. Exports are never deleted (the app has read-only access). An Export with no posts changes nothing and is not an error.

## Sync status and Summary

- Status line: "Synced 2 h ago · Export from 1 Oct", "Synced" being the last Sync that went through Drive's Exports. Error state when the last sync failed (Drive access not granted or refused, Drive unreachable, no Export found, an Export that could not be read), with the reason.
- **Stale warning** when the newest Export in Drive is more than 3 days old — the Instagram schedule may have stopped. "Newest in Drive" is as of the last Sync that went through the Exports, and counts an Export that could not be read: its folder still shows the schedule at work.
- **Daily background Sync**: once a day, when the phone has a network. It never opens Google's sign-in: if Google does not hand a token without asking, it does nothing until Drive has been connected by a "Sync now", and after that reports the lost access as an error state. When Drive cannot be reached it tries again sooner than the next day.
- **Sync Summary** (dismissable, only shown when something changed): new · captions updated.
- No system notification.

## Test cases

Case numbers are stable: 10, 16, 17 and 22 tested the "No longer saved" marker and its sanity check, and went away with them (ADR-0013).

| # | Given | Export contains | Then |
|---|---|---|---|
| 1 | empty app | A in "Recipes", B in no collection | A created in new Collection "Recipes", B in To sort, both New |
| 2 | app has Collection "recipes" | A in "Recipes" | A goes into existing "recipes" (case-insensitive); no new Collection |
| 3 | A in app, user moved it to "Travel" | A in "Recipes" | A stays in "Travel"; no "Recipes" created |
| 4 | user deleted Collection "Recipes" (posts kept elsewhere) | only posts already in app, all in "Recipes" | nothing created |
| 5 | user deleted Collection "Recipes" | new post C in "Recipes" | "Recipes" recreated with C only |
| 6 | user renamed "Recipes" → "🍝 Recipes" | new post C in "Recipes" | fresh "Recipes" created with C |
| 7 | A deleted by user | A | ignored; A stays deleted |
| 8 | A in the app | (A missing) | nothing: A is left as it is |
| 9 | any | Export with 0 posts | no changes, no Summary, no error |
| 11a | A added by Share-in, user moved it to "Travel", never in an Export | A in "Recipes" | A matched by shortcode, not duplicated, not New, stays in "Travel" |
| 11b | A added by Share-in, still in To sort, never in an Export | A in "Recipes" | A moved to "Recipes" (created if missing), not New |
| 11c | A seen in an earlier Export with no Instagram Collection, in To sort | A in "Recipes" | A stays in To sort (not its first sighting) |
| 12 | A, Description not hand-edited | A with a changed caption | Description updated; Title follows unless hand-edited |
| 13 | A, Description hand-edited | A with a changed caption | Description untouched |
| 14 | A saved as `/reel/X/?igsh=…` | `/p/X/` | same Post |
| 15 | any | same Export applied twice | second run: no changes, no Summary |
| 18 | caption `Câ€™est` in JSON | — | Description `C’est` |
| 19 | app has Collection "Travel" | new C in "Recipes" and "Travel" | C goes into existing "Travel"; no "Recipes" created; C shows "also on Instagram in: Recipes" |
| 20 | no matching app Collection | new C in "Recipes" and "Desserts" | "Desserts" created with C (first alphabetically) |
| 21 | A in Recently deleted | A | ignored; A stays in Recently deleted |

## Export file format

Confirmed against the Exports of 2026-10-01 and 2026-10-02 (schedule: "Enregistrements", "Exporter vers Google Drive · Tous les jours", **all time**, **JSON**). Anonymized copies are the test fixtures under `app/src/test/resources/exports/`; `ExportParser.kt` reads the format.

**In Drive.** Each Export is a **folder, not a ZIP**: `instagram-<username>-<YYYY-MM-DD>-<random>/`, holding
- `your_instagram_activity/saved/saved_posts.json`
- `your_instagram_activity/saved/saved_collections.json` — absent when the Export has no Instagram Collection.

**`saved_posts.json`** is a list of entries, newest first, one per post. An Export with a single post holds that entry alone, not in a list.

```
{ "timestamp": <saved at, seconds>, "media": [], "fbid": "…", "label_values": [
    { "label": "URL", "value": "https://www.instagram.com/reel/<shortcode>/", "href": "…" },   // or /p/<shortcode>/
    { "label": "Caption", "value": "…" }, { "label": "Title", "value": "" },                // the pair repeats per carousel slide; absent when there is no caption
    { "title": "Hashtags", "dict": [ … ] },
    { "title": "Owner", "dict": [ { "title": "", "dict": [
        { "label": "URL", "value": "<link in bio>" }, { "label": "Name", "value": "…" }, { "label": "Username", "value": "…" } ] } ] },
    { "title": "Brand partner", "dict": [ … ] } ] }
```

**`saved_collections.json`** is a list of Instagram Collections: `label_values` holds `Name`, `Type`, privacy and update time, then one group whose items are the collection's posts, each with the same fields as a saved post. A post in several Instagram Collections appears under each.

What the parser does with it:
- **Labels are in the account's language and vary between Exports** (French on 10-01, English on 10-02): `Caption`/`Légende`, `Owner`/`Propriétaire`, `Name`/`Nom`, `Username`/`Nom de profil`. Both are accepted; an Export whose labels are in neither is a parse error, not an Export of posts without captions.
- Every string, labels included, needs the text repair.
- **Caption** = the first non-blank one. `Title` is always empty and ignored. An empty owner name is no owner name.
- **Instagram Collection names are not unique**: the same name twice, case variants ("books" / "Books"), stray spaces. Names are trimmed and a post lists each exact name once; matching them to app Collections ignores case (Normalization).
- A post listed in an Instagram Collection but not in `saved_posts.json` is ignored.

**Scheduled Exports are incremental, even with "all time"** (confirmed on the Exports of 2026-10-01 to 10-03). Only the first Export of a schedule is a complete snapshot; each later one holds just the posts saved since the previous Export:

| Export | Posts | Saved between | Already in an earlier Export | `saved_collections.json` | Labels |
|---|---|---|---|---|---|
| 10-01 | 871 | 2020 → 10-01 | — | yes | French |
| 10-02 | 1 | 10-02 | 0 | no | English |
| 10-03 | 12 | 10-03 | 0 | no | English |

A second schedule, started on 2026-10-03 with more categories than "Saved" (posts, stories, profile photos, reposts), behaves the same way. Its Exports hold extra files and media folders next to `saved/`, which Sync does not read:

| Export | Posts | Saved between | Already in an earlier Export | `saved_collections.json` | Labels |
|---|---|---|---|---|---|
| 10-03 | 884 | 2020 → 10-03 | — | 67 collections, 846 posts in one or more | French |
| 10-04 | 9 | 10-04 | 0 | no | English |
| 10-05 | 3 | 10-05 | 0 | 2 collections, 1 post each | English |
| 10-06 | 10 | 10-05 → 10-06 | 0 | 1 collection, 1 post (the entry alone, not in a list) | English |

**An incremental Export lists only the Instagram Collections created since the previous Export**, each with the posts saved into it in that window. A post saved into an Instagram Collection that already existed comes without it. What shows this:
- None of the three collections in the 10-05 and 10-06 Exports is in the 10-03 snapshot, and each one's `timestamp` is the save time of its only post.
- A collection's `timestamp` is its creation time, not its last update: in the 10-03 snapshot the newest collection dates from 10-01, yet all 12 posts saved on 10-03 are in a collection.
- The extra categories change nothing: the first schedule's 10-03 Export had no collections file although its posts were saved into (existing) Instagram Collections.

So after the first Export, Placement only ever sees brand-new Instagram Collections; every other new Post lands in To sort.
