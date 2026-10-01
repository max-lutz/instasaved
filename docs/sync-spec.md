# Sync spec

How an Export becomes changes to the app's Posts. Terms are defined in `CONTEXT.md`. The rules are written to be implemented as a **pure function** — `(appState, parsedExport) -> (changes, summary)` — and tested case by case, with Drive, Room and WorkManager kept outside it.

## Pipeline

1. **Find** the newest Export ZIP in Google Drive (read-only access, see ADR-0004).
2. **Skip** if that file (Drive file id) was already processed successfully.
3. **Download** to app cache, unzip, locate the saved-posts and saved-collections JSON.
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
- Collection = the app Collection whose name matches the post's Instagram Collection; **create that Collection if it doesn't exist** (next palette color, empty note). No Instagram Collection → To sort.
- Marked **New**. Marked as *seen in an Export*.

**R2 — Deleted Post.** Shortcode in `E` and is a Deleted Post → ignore silently. Deleted Posts never come back through Sync.

**R3 — Existing Post still saved.** Shortcode in `E` and in the app →
- Never changes Collection, Tags, Post Note, or a hand-edited Title.
- If Description has not been hand-edited and the caption differs → update Description (Title follows if not hand-edited). Count as "caption updated".
- Fill owner fields if empty.
- Mark as *seen in an Export*. If it was **No longer saved**, clear the marker (count as "back on Instagram").
- Not marked New (it was already in the app — this includes Posts added by Share-in).

**R4 — Existing Post missing from the Export.** Shortcode not in `E`, Post in the app →
- If it has been *seen in an Export* before → mark **No longer saved** (if not already).
- Otherwise (e.g. Share-in never saved on Instagram) → nothing.

**R5 — Collections are only created by R1.** An Instagram Collection whose posts are all already in the app (wherever the user put them) creates nothing. A renamed or deleted app Collection is not tracked: if a new post arrives in Instagram Collection "a" and no app Collection is named "a", a fresh "a" is created.

**R6 — Idempotent.** Applying the same Export twice produces no changes the second time.

**R7 — Newest only.** Only the newest Export is applied; older ones in Drive are ignored and never deleted (the app has read-only access).

**S1 — Sanity check (safety net).** Refuse to apply an Export if it parses to zero posts, or if it would mark more than 50% of the Posts *seen in an Export* as No longer saved. Show the error in the sync status instead. Protects against a partial Export, a wrong date range, or a Meta format change silently badging everything.

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
| 11 | A added by Share-in | A in "Recipes" | A matched by shortcode, not duplicated, not New, stays where user put it |
| 12 | A, Description not hand-edited | A with a changed caption | Description updated; Title follows unless hand-edited |
| 13 | A, Description hand-edited | A with a changed caption | Description untouched |
| 14 | A saved as `/reel/X/?igsh=…` | `/p/X/` | same Post |
| 15 | any | same Export applied twice | second run: no changes, no Summary |
| 16 | 10 Posts seen in Exports | Export with 3 of them | refused by S1, nothing applied, error shown |
| 17 | any | Export with 0 posts | refused by S1 |
| 18 | caption `Câ€™est` in JSON | — | Description `C’est` |

## Export file format — to verify

Confirmed so far (2026-10-01): a daily scheduled Export to Google Drive offering saved posts exists ("Enregistrements", "Exporter vers Google Drive · Tous les jours"). Settings to use: **all time**, **JSON**.

Not yet verified — check against the first real Export and commit it (anonymized if needed) as test fixtures under `app/src/test/resources/exports/`:
- Folder and file naming of the ZIP in Drive (needed to find "the newest Export").
- Paths inside the ZIP. Community reports: `your_instagram_activity/saved/saved_posts.json` and `saved_collections.json`.
- JSON shape. Community reports two variants: an older `{"saved_saved_media": [{"title": <owner>, "string_map_data": {"Saved on": {"href", "timestamp"}}}]}`, and a newer one with `timestamp` + `label_values` (URL, Caption, Owner…). Collections list their posts under a "Media" group.
- Whether a post saved in several Instagram Collections appears under each (see PRD open question).
