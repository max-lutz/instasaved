# A Post still in To sort is placed by the first Export that mentions it

ADR-0005 says Sync never moves a Post already in the app. That left a gap: the user saves a post into Instagram Collection "Recipes", shares it to InstaSaved right away (it lands in To sort), and the next Export — the first that knows about it — can't place it. The user would sort it twice.

So there is one narrow exception: the **first time** an Export contains a Post that has never been *seen in an Export*, and the Post is **still in To sort**, it is placed exactly as a new Post would be (Placement in `sync-spec.md`, which may create a Collection). It is not marked New. Afterwards the normal rule holds: a Post the user put in a Collection is never touched, and a Post the user later moves back to To sort stays there.

The one misfire — the user moved the Post out of To sort and back before the first Export arrived — is rare and costs one move to undo.

Rejected: strict "never move" (every Share-in also saved into an Instagram Collection ends up unsorted), and placing any To sort Post on every Sync (would undo a deliberate "leave it unsorted").
