# Saved posts come from a scheduled, all-time JSON Export in Google Drive

Instagram offers no API to read a personal account's saved posts. The only sanctioned route is "Export your information", which can be scheduled to an external service. The user sets it once: **Export to Google Drive, daily, all time, JSON**, saved posts only. Each Export is therefore a complete snapshot of what is saved on Instagram.

A complete snapshot is what makes "No longer saved" detectable: a post missing from a last-month Export is not necessarily unsaved, but one missing from an all-time Export is. JSON was chosen over HTML because the desktop app's HTML parser depends on French labels and Meta's generated CSS class names.

Rejected: Instagram's private API (account-ban risk); scraping instagram.com/<user>/saved in a logged-in WebView (breaks on markup changes, against ToS); manual exports to the device (Meta can't schedule those, so every sync would cost several taps). Share-in complements the Export for instant capture between syncs.

Consequence: Sync lags Instagram by up to a day plus Meta's export delay (usually hours; Meta says up to 30 days). A stale-Export warning covers the case where the schedule silently stops.
