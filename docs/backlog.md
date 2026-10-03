# Backlog (v1)

Thin, end-to-end slices, in a workable order. Each becomes a GitHub issue. "Blocked by" lists the slices that must land first.

## 1. App skeleton + release pipeline
Kotlin/Compose project `com.maxlutz.instasaved`, min API 29, Room wired up, one empty screen. GitHub Actions builds a signed release APK on tag and attaches it to a GitHub Release (Obtainium-installable). Unit tests run in CI.
- Blocked by: —

## 2. Share-in → To sort grid (tracer bullet)
Share a post from Instagram to the app → a Post (shortcode identity, ADR-0007) appears in a To sort grid as a placeholder card. Sharing the same post twice doesn't duplicate.
- Blocked by: 1

## 3. Post detail
Open a Post: Embed in a WebView, Title / Description / Post Note editing with the hand-edited flags (Title auto-follows Description until hand-edited), open in Instagram, delete (→ Recently deleted, Undo snackbar).
- Blocked by: 2

## 4. Collections
Create / rename / recolor / Collection Note; assign a Post; views per Collection; alphabetical list; delete with the keep-or-delete-Posts prompt (ADR-0010).
- Blocked by: 3

## 5. Tags
Create / rename / recolor; up to 4 per Post; tag on the spot after Share-in.
- Blocked by: 3

## 6. Thumbnails
Download on Post creation (`/media/?size=m`, fallback `og:image`), store bytes in a no-backup directory, slow retry queue, placeholder with owner name.
- Blocked by: 2

## 7. Export parser
Pure Kotlin: an Export's JSON files → `ExportedPost` list. Text repair, shortcode extraction. Tested against anonymized fixtures from the first real Exports.
- Blocked by: 1 · needs a real Export sample

## 8. Sync rules engine
Pure function implementing `sync-spec.md` R1–R6 (incl. Placement and R3a), with every row of its test-case table as a unit test.
- Blocked by: 7

## 9. Drive connection + Sync now
Connect Google Drive (`drive.readonly`), find the Exports not applied yet, and for each, oldest first: download, run 7 + 8, apply in one transaction. "Sync now" button, sync status line, error states.
- Blocked by: 8, 4

## 10. Daily background Sync
WorkManager periodic job (daily, needs network), stale-Export warning (> 3 days).
- Blocked by: 9

## 11. New / Sync Summary
New marker (clears on open, "Mark all as seen"), dismissable Sync Summary.
- Blocked by: 9

## 11b. Recently deleted
Recently deleted view: restore (recreating the Collection if it was deleted in the same action), "Empty now", purge to shortcode trace after 30 days. Share-in of a Deleted Post asks "Add it back?" (ADR-0012).
- Blocked by: 4

## 12. Search, sort, group by Tag
Search Title / Description / Post Note; sort by saved / modified / Title; group by Tag.
- Blocked by: 4, 5

## 13. Backups
Android automatic backup rules (DB + settings, exclude Thumbnails); manual backup file write / restore (wipe-and-replace, transactional).
- Blocked by: 4, 5

## 14. Desktop Import
One-time import of a Socials Organizer v6 backup, per the mapping in ADR-0009.
- Blocked by: 4, 5

## 15. Instagram-like design pass
Apply the UI mockup (to be designed first): grid, Collections row, bottom-sheet details, theming.
- Blocked by: UI mockup, 4, 5, 11
