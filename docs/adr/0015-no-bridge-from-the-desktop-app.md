# No bridge from Socials Organizer: the Desktop Import is removed

ADR-0009 kept one bridge from the desktop app (Socials Organizer): a one-time Desktop Import of a v6 backup file, so that the notes, Tags and Collections made there would not be lost. It was built, and will never be used: InstaSaved replaces Socials Organizer outright, and nothing is carried over from it.

So the Desktop Import is removed, with its row on the More screen. This ADR supersedes ADR-0009:

- **InstaSaved reads nothing from Socials Organizer**: no backup file, no shared format, no sync. The only file the app restores is its own manual backup file.
- **What ADR-0009 said beyond the import still holds**: the app's Backup format is its own and free to evolve.
- **Posts already in the database keep the flags they have.** ADR-0009 marked imported Posts as Description hand-edited; nothing is recomputed. The database schema does not change.
- **What the two apps happen to share stays**: the 12-color palette, and names compared ignoring case.

ADR-0014's line that the Desktop Import keeps dropping the desktop app's sections is moot.

Rejected: keeping the import in case it is needed one day (dead code to carry through every schema change, behind a button nobody should press), and hiding the button while keeping the code (the same cost, without the button).
