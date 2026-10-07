# Sections group Collections on the Saved screen

The PRD left Sections out of v1, and ADR-0009 dropped the desktop app's sections on import, both on the idea that an emoji prefix in a Collection's name is enough to group Collections in an alphabetical list. It is not: the prefix is part of the name everywhere the Collection shows, groups cannot be collapsed, and regrouping means renaming every Collection. This ADR reverses that and supersedes ADR-0009's note that the user groups with emoji prefixes instead.

A **Section** is a user-named group of Collections, shown on the Saved screen under a collapsible header:

- **A name only**: no color, no note. Names are unique among Sections, ignoring case; a Section and a Collection may share a name.
- **Flat**: no Section inside a Section. A Collection has at most one Section and may have none.
- **It exists until it is deleted**, even empty. Deleting it asks nothing and deletes nothing else: its Collections lose their Section. Undo brings it back with its Collections in it.
- **Only the Saved screen groups by Section**: the lists that pick a Collection (moving a Post, "Move all Posts to…", moving a selection) stay flat and alphabetical.
- **Sync never reads or changes a Section.** A Collection that Sync creates (ADR-0006) has none.
- **Whether a Section is collapsed** is remembered on the phone, with the Section. It is not in the manual backup file: restored Sections are expanded.
- **A Collection deleted with its Posts remembers its Section** (ADR-0010, ADR-0012). Restoring those Posts recreates the Collection in that Section if it is still there, and with none otherwise. A restore never recreates a Section.

The manual backup file holds the Sections and each Collection's Section; its version goes to 2, and a version 1 file still restores, with no Sections.

The Desktop Import is unchanged: it keeps dropping the desktop app's sections (ADR-0009). Existing Collection names keep their emoji prefixes; the user removes them if they want to.

Rejected: Sections with a color or a note (a header needs neither, and a Collection already has both), nested Sections (two levels are enough for a personal library), and deleting a Section together with its Collections (too much to lose behind a header's menu; deleting a Collection already asks what to do with its Posts).
