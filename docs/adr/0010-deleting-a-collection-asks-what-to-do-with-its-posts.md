# Deleting a Collection asks whether to delete its Posts too

Carried over from Socials Organizer ADR-0001. A Post belongs to at most one Collection. Deleting a Collection asks the user to choose: keep the Posts (they go to To sort) or delete them along with it (they become Deleted Posts — restorable from Recently deleted for 30 days, see ADR-0012 — and Sync won't bring them back).

The database default stays `ON DELETE SET NULL` on the Post → Collection reference, the safe behavior if a Collection is ever removed outside this flow; the "delete the Posts too" path is handled in app code.
