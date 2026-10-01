# Handoff — from the Socials Organizer brainstorm (2026-10-01)

This repo was kickstarted from a grilling session held in the Socials Organizer repo (max-lutz/instagram-organizer). Everything decided there is captured in `CONTEXT.md`, `docs/PRD.md`, `docs/sync-spec.md` and `docs/adr/`. Read those first; this note only says where the discussion stopped.

## Where we stopped

The design tree is settled except for these open questions (also in the PRD). Continue the grilling here, one round, then record the answers in the PRD / sync-spec / an ADR as appropriate:

1. **A post saved in several Instagram Collections** — the app allows one Collection per Post. Current default: the first Instagram Collection listed in the Export.
2. **A Share-in post still in To sort, later seen in an Export inside Instagram Collection "a"** — stay in To sort (strict "Sync never moves existing Posts", current rule in sync-spec R3) or get placed in "a" since the user never sorted it?
3. **Undo a deletion** — Deleted Posts are permanent today. Add a "Recently deleted" list with restore?

Decisions taken by the assistant without an explicit answer from the user — confirm or change:
- Name spelled **InstaSaved**; package `com.maxlutz.instasaved`.
- Post identity is the shortcode (ADR-0007).
- Sync sanity check S1 (refuse an Export with 0 posts or that would mark > 50% of known Posts No longer saved).
- Stale-Export warning after 3 days.
- Desktop-imported Posts count as Description hand-edited (ADR-0009).

## Next steps

1. Resolve the open questions above.
2. User: switch the Instagram scheduled Export to **JSON** and **all time** (it was HTML, last month), then share the first Export ZIP (or its saved-posts files) — the Export parser (backlog #7) and sync-spec's "to verify" section depend on it.
3. UI mockup (Instagram-like grid; ideas in PRD open question 7) — before backlog #15.
4. Turn `docs/backlog.md` into GitHub issues.
5. Start backlog #1 (app skeleton + release pipeline).
