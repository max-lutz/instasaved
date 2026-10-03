# Exports are incremental, so Sync only adds: "No longer saved" is dropped

ADR-0003 assumed that a daily, all-time Export is a complete snapshot of what is saved on Instagram, and built "No longer saved" on comparing snapshots. The first real Exports (2026-10-01 to 10-03) showed otherwise: only the first Export of a schedule is complete (871 posts, with Instagram Collections). Each later one holds just the posts saved since the previous Export (1, then 12), and no Instagram Collections, even for posts saved into one.

So a post missing from an Export says nothing about whether it is still saved, and nothing else in an Export does. Sync therefore only adds and updates:

- **"No longer saved" is dropped from v1**, with its view, the "back on Instagram" count, and the sanity check S1 (its > 50% refusal and "Apply anyway" only guarded that marker). A post unsaved on Instagram stays in the app until the user deletes it.
- **Every Export is applied, once, oldest first**, instead of only the newest: skipping one would lose that day's posts. The app remembers the Exports it has applied by Drive file id.
- **Posts from later Exports land in To sort.** Placement (ADR-0006, ADR-0011) only has Instagram Collections to work with on a complete Export.

Rejected: detecting unsaved posts only when a complete Export shows up (the user would have to recreate the schedule to get one, and the app would have to guess which Exports are complete), and asking Meta for a complete Export daily (the schedule offers no such option; "all time" is already selected).
