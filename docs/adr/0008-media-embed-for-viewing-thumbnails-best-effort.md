# Embeds for viewing; Thumbnails downloaded best-effort and kept on the phone

Opening a Post shows Instagram's official Embed in a WebView, which plays videos and swipes carousels — reliable and already proven in the desktop app, at the cost of needing network.

The grid needs Thumbnails, and the Export contains no images. When a Post is added, the app downloads one from `instagram.com/p/<shortcode>/media/?size=m` (worked without login as of 2026-10-01), falling back to the post page's `og:image`. The image bytes are stored on the phone, never the URL: Instagram CDN URLs expire within days. Failures retry slowly; until then the grid shows a placeholder card with the owner's name. Both endpoints are unofficial and may break — this is accepted, since the Embed still works.

Rejected for v1: downloading videos and carousel media for offline viewing (unofficial endpoints, heavy storage). Could return later as a per-Post "keep offline" action.
