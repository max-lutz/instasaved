# A Post is identified by its Instagram shortcode, behind a surrogate id

Socials Organizer identified a Post by its exact link string. In InstaSaved, Posts arrive both through the Export (`https://www.instagram.com/p/X/`) and through Share-in (often `https://www.instagram.com/reel/X/?igsh=…`). Matching on the raw link would duplicate them, so identity is the shortcode `X`, stored as a unique column; the original URL is kept for opening the post.

The primary key stays a surrogate integer id, as in Socials Organizer ADR-0002: Tags, Thumbnails and Deleted Posts reference something that doesn't depend on parsing a URL.
