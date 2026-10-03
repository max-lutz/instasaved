# Sync spec

How an Export becomes changes to the app's Posts. Terms are defined in `CONTEXT.md`. The rules are written to be implemented as a **pure function** — `(appState, parsedExport) -> (changes, summary)` — and tested case by case, with Drive, Room and WorkManager kept outside it.

## Pipeline

1. **Find** the newest Export folder in Google Drive (read-only access, see ADR-0004).
2. **Skip** if that folder (Drive file id) was already processed successfully.
3. **Download** its saved-posts and saved-collections JSON (see Export file format).
4. **Parse** into `ExportedPost { shortcode, url, caption?, ownerUsername?, ownerName?, savedAt, instagramCollections: [name] }`.
5. **Sanity-check** (rule S1). On failure: apply nothing, record the error in the sync status.
6. **Diff** against app state with rules R1–R7; apply all changes in one DB transaction.
7. **Record** the sync status (time, Export date, Drive file id) and the Sync Summary.
8. **Queue** Thumbnail downloads for newly added Posts (best-effort, outside the transaction).

## Normalization

- **Identity** is the shortcode extracted from `instagram.com/(p|reel|tv)/<shortcode>`. Query strings (`?igsh=…`, `?img_index=…`) and the path type are ignored for matching. The original URL is stored for opening the post.
- **Text repair**: Meta's JSON stores UTF-8 bytes as Latin-1 code points (`â€™` for `’`). Re-encode each string as Latin-1 bytes and decode as UTF-8 before use.
- **Collection names** match case-insensitively, after trimming.

## Rules

Let `E` be the set of shortcodes in the Export.

**R1 — New Post.** Shortcode in `E`, not in the app, not a Deleted Post → create a Post:
- Description = caption, Title auto-derived, owner fields filled, saved-at = Export's saved timestamp.
- Collection = picked by **Placement** (below). No Instagram Collection → To sort.
- Instagram Collection names = all of the post's Instagram Collections, stored on the Post for display only ("also on Instagram in: b, c"); refreshed on every Sync, never used to move a Post.
- Marked **New**. Marked as *seen in an Export*.

**Placement** (used by R1 and R3a). Given the post's Instagram Collections:
1. If one or more match an existing app Collection by name, take the first of those alphabetically.
2. Otherwise take the first Instagram Collection alphabetically and **create an app Collection with that name** (next palette color, empty note).

Alphabetical order makes the result independent of how Meta orders the Export. For the same reason, "existing" in step 1 means existing before this Sync: a Collection created earlier in the same Sync is reused by step 2, but does not attract a post away from its alphabetically first Instagram Collection.

**R2 — Deleted Post.** Shortcode in `E` and is a Deleted Post — whether still in Recently deleted or already reduced to its trace → ignore silently. Deleted Posts never come back through Sync (only the user can restore one, see ADR-0012).

**R3 — Existing Post still saved.** Shortcode in `E` and in the app →
- Never changes Collection, Tags, Post Note, or a hand-edited Title.
- If Description has not been hand-edited and the caption differs → update Description (Title follows if not hand-edited). Count as "caption updated".
- Fill owner fields if empty. Refresh the stored Instagram Collection names.
- Mark as *seen in an Export*. If it was **No longer saved**, clear the marker (count as "back on Instagram").
- Not marked New (it was already in the app — this includes Posts added by Share-in).

**R3a — First-sighting placement.** Applies within R3, evaluated *before* marking the Post seen: if the Post has **never been seen in an Export** and is **still in To sort**, and the post has at least one Instagram Collection → set its Collection by Placement (which may create a Collection). It happens once; afterwards the Post is never moved by Sync, including if the user puts it back in To sort. A Post the user already put in a Collection is untouched (ADR-0011).

**R4 — Existing Post missing from the Export.** Shortcode not in `E`, Post in the app →
- If it has been *seen in an Export* before → mark **No longer saved** (if not already).
- Otherwise (e.g. Share-in never saved on Instagram) → nothing.

**R5 — Collections are only created by Placement** (R1 and R3a). An Instagram Collection whose posts are all already in the app (wherever the user put them) creates nothing. A renamed or deleted app Collection is not tracked: if a new post arrives in Instagram Collection "a" and no app Collection is named "a", a fresh "a" is created.

**R6 — Idempotent.** Applying the same Export twice produces no changes the second time.

**R7 — Newest only.** Only the newest Export is applied; older ones in Drive are ignored and never deleted (the app has read-only access).

**S1 — Sanity check (safety net).** Refuse to apply an Export if it parses to zero posts, or if it would mark more than 50% of the Posts *seen in an Export* as No longer saved. Show the error in the sync status instead. Protects against a partial Export, a wrong date range, or a Meta format change silently badging everything.
- The **> 50% refusal** offers **"Apply anyway"**: the user confirms that one Export (by Drive file id) and it is applied in full. Without this, a genuine mass unsave would block every later Sync.
- The **0-posts refusal** has no override.

## Sync status and Summary

- Status line: "Synced 2 h ago · Export from 1 Oct". Error state when the last sync failed (Drive sign-in expired, no Export found, S1 refused, parse error), with the reason.
- **Stale warning** when the newest Export in Drive is more than 3 days old — the Instagram schedule may have stopped.
- **Sync Summary** (dismissable, only shown when something changed): new · no longer saved · back on Instagram · captions updated.
- No system notification.

## Test cases

| # | Given | Export contains | Then |
|---|---|---|---|
| 1 | empty app | A in "Recipes", B in no collection | A created in new Collection "Recipes", B in To sort, both New |
| 2 | app has Collection "recipes" | A in "Recipes" | A goes into existing "recipes" (case-insensitive); no new Collection |
| 3 | A in app, user moved it to "Travel" | A in "Recipes" | A stays in "Travel"; no "Recipes" created |
| 4 | user deleted Collection "Recipes" (posts kept elsewhere) | only posts already in app, all in "Recipes" | nothing created |
| 5 | user deleted Collection "Recipes" | new post C in "Recipes" | "Recipes" recreated with C only |
| 6 | user renamed "Recipes" → "🍝 Recipes" | new post C in "Recipes" | fresh "Recipes" created with C |
| 7 | A deleted by user | A | ignored; A stays deleted |
| 8 | A seen in an earlier Export | (A missing) | A marked No longer saved, kept |
| 9 | A No longer saved | A | marker cleared; counted "back on Instagram" |
| 10 | A added by Share-in, never in an Export | (A missing) | nothing |
| 11a | A added by Share-in, user moved it to "Travel", never in an Export | A in "Recipes" | A matched by shortcode, not duplicated, not New, stays in "Travel" |
| 11b | A added by Share-in, still in To sort, never in an Export | A in "Recipes" | A moved to "Recipes" (created if missing), not New |
| 11c | A seen in an earlier Export with no Instagram Collection, in To sort | A in "Recipes" | A stays in To sort (not its first sighting) |
| 12 | A, Description not hand-edited | A with a changed caption | Description updated; Title follows unless hand-edited |
| 13 | A, Description hand-edited | A with a changed caption | Description untouched |
| 14 | A saved as `/reel/X/?igsh=…` | `/p/X/` | same Post |
| 15 | any | same Export applied twice | second run: no changes, no Summary |
| 16 | 10 Posts seen in Exports | Export with 3 of them | refused by S1, nothing applied, error shown |
| 17 | any | Export with 0 posts | refused by S1 |
| 18 | caption `Câ€™est` in JSON | — | Description `C’est` |
| 19 | app has Collection "Travel" | new C in "Recipes" and "Travel" | C goes into existing "Travel"; no "Recipes" created; C shows "also on Instagram in: Recipes" |
| 20 | no matching app Collection | new C in "Recipes" and "Desserts" | "Desserts" created with C (first alphabetically) |
| 21 | A in Recently deleted | A | ignored; A stays in Recently deleted |
| 22 | 10 Posts seen in Exports, Export with 3 refused by S1 | user taps "Apply anyway" | applied: 7 marked No longer saved |

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

**Open — the 10-02 Export held only the one post saved since the 10-01 Export**, and no collections file. If scheduled Exports are incremental even with "all time", an Export is not a complete snapshot and R4 and S1 cannot work as written (ADR-0003). To settle with the Exports that follow before the Sync rules engine is built.
