# Instagram Collections map to app Collections by name, created only when a new Post needs one

When Sync adds a new Post, it lands in the app Collection whose name matches its Instagram Collection (case-insensitive), and that Collection is created if it doesn't exist. Collections are never created for any other reason, and no link between an app Collection and an Instagram Collection is stored.

So renaming an app Collection detaches it: the next new post from that Instagram Collection creates a fresh Collection with the old name. That is the user's explicit choice — they would rather keep names aligned by renaming on Instagram than have the app track identities. Deleting an app Collection is not remembered either: it comes back only if Instagram brings a post the app has never seen in that Instagram Collection. If all its posts already sit in other Collections, nothing is recreated.

Rejected: storing an Instagram-Collection id/name on each app Collection so renames are followed (more state, and not what the user wanted), and remembering deleted Collections like Deleted Posts (would strand genuinely new posts in To sort).
