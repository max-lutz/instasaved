# Embeds for viewing; Thumbnails downloaded best-effort and kept on the phone

Opening a Post shows Instagram's official Embed in a WebView, which plays videos and swipes carousels — reliable and already proven in the desktop app, at the cost of needing network.

A video Embed starts playing when the Post opens (#36): the WebView allows playback without a tap, and a script injected into Instagram's page calls `play()` on its single video once the page has built it. Carousels are left alone. This is best effort like the Thumbnails: if Instagram changes the page, the Embed is simply back to tap-to-play.

The grid needs Thumbnails, and the Export contains no images. When a Post is added, the app downloads one from `instagram.com/p/<shortcode>/media/?size=m` (worked without login as of 2026-10-01), falling back to the post page's `og:image`. The image bytes are stored on the phone, never the URL: Instagram CDN URLs expire within days. Failures are retried, a minute later at first and then ever more slowly, up to once a week; until then the grid shows a placeholder card with the owner's name. Both endpoints are unofficial and may break — this is accepted, since the Embed still works.

Rejected for v1: downloading videos and carousel media for offline viewing (unofficial endpoints, heavy storage). Could return later as a per-Post "keep offline" action.
