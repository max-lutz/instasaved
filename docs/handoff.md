# Handoff — from the Socials Organizer brainstorm (2026-10-01)

This repo was kickstarted from a grilling session held in the Socials Organizer repo (max-lutz/instagram-organizer). Everything decided there is captured in `CONTEXT.md`, `docs/PRD.md`, `docs/sync-spec.md` and `docs/adr/`. Read those first; this note only says where the discussion stopped.

## Where we stopped

All design questions from the brainstorm are resolved (follow-up grilling, 2026-10-01):
- Post in several Instagram Collections → Placement rule in `sync-spec.md` (prefer an existing app Collection, else first alphabetically); all names shown as "also on Instagram in".
- Share-in post still in To sort → placed by its first Export sighting (R3a, ADR-0011).
- Undo a deletion → Undo snackbar + Recently deleted for 30 days; Share-in of a Deleted Post asks "Add it back?" (ADR-0012).
- Confirmed: name **InstaSaved** / `com.maxlutz.instasaved`; shortcode identity (ADR-0007); S1 thresholds, now with "Apply anyway" on the > 50% refusal; stale-Export warning after 3 days; Desktop-imported Posts permanently count as Description hand-edited (ADR-0009, unchanged).

## Next steps

1. User: switch the Instagram scheduled Export to **JSON** and **all time** (it was HTML, last month), then share the first Export ZIP (or its saved-posts files) — the Export parser (backlog #7) and sync-spec's "to verify" section depend on it.
2. UI mockup (Instagram-like grid; ideas in PRD open question 4) — before backlog #15.
3. Turn `docs/backlog.md` into GitHub issues.
4. Start backlog #1 (app skeleton + release pipeline).
