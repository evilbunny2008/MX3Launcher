# MX3 Launcher self-service pairing site

Multi-user accounts, each with one or more named "saved credentials"
presets (e.g. a TV's pairing URL/secret, or any other app's fields).
One generic system backs two flows:

- **"Pull"**: a device with nothing of its own (e.g. an MX3 Launcher TV
  pairing for the first time, or Z2M Dash setting up a new broker) asks
  to *receive* credentials for a named app; the logged-in account holder
  picks which saved preset to send it.
- **"Push"**: an app that already has data of its own hands it over
  directly for a human to read/copy elsewhere - available to any app
  that wants it, though nothing currently uses this mode.

This replaces both the original flat-file pairing system
(`pairing_helper.php`) and the later multi-account `pairing_urls`/
`device_pairings` version (`pair_start.php`/`pair_approve.php`/
`pair_poll.php`/`dashboard.php`/`edit_pairing_url.php`) — all gone now,
folded into the one generic relay.

## Setup

1. **Database**: create a MariaDB database, then run, in order:
   ```
   mysql -u root -p your_database_name < schema.sql
   mysql -u root -p your_database_name < credential_shares.schema.sql
   mysql -u root -p your_database_name < saved_credentials.schema.sql
   ```
   (A fresh install only needs those three. If you're upgrading a
   deployment that already had `credential_shares.schema.sql` applied
   from before pull-mode existed, run
   `credential_shares_pull_mode.migration.sql` instead of re-running
   `credential_shares.schema.sql`.)

2. **`db.php`**: fill in the `TODO_*` constants with your real MariaDB
   host/database name/username/password.

3. **`mailer.php`**: fill in the `TODO_*` from-address/name constants.
   Sends via PHP's built-in `mail()`, which hands off to this server's
   local MTA — no SMTP credentials needed, but it does mean deliverability
   depends on that MTA being properly configured (SPF/DKIM etc.) rather
   than anything this script controls.

4. **HTTPS is required, not optional.** This site handles passwords and
   device secrets — `auth_helper.php`'s session cookie is explicitly
   marked `secure`, meaning it will silently **not** work at all served
   over plain HTTP. Deploy this behind HTTPS only.

5. **Cron cleanup (optional but recommended)**: expired shares are
   already ignored by every query, but nothing deletes them without
   this running periodically:
   ```
   0 * * * * php /path/to/cleanup_expired_credential_shares.php
   ```

## How it works

1. Someone lands on `index.php`, signs up (`register.php`), verifies
   their email (`verify_email.php`), and logs in (`login.php`).
2. On `manage_credentials.php`, they add one or more named presets —
   an app name (e.g. "MX3Launcher" or "Z2M Dash"), a label (e.g. "Living
   room TV"), and its fields as plain "Key: Value" lines. Apps that
   consume these fields automatically via pull mode (rather than a
   human just reading them off a push-mode reveal page) look up
   specific key names, so they need to match exactly - `manage_credentials.php`
   itself has the full, current field list per app (it's the single
   source of truth, so it doesn't drift from what each app's code
   actually reads); in short, **MX3 Launcher** looks for `URL` and
   `Secret` (see its own `SoundbarPairing.kt`), and **Z2M Dash** looks
   for `Hostname` (everything else - `Protocol`, `Port`, `Username`/
   `Password`, `Name`, `BaseTopic`, `WebSocketPath`, `ClientId`,
   `SelfSignedCert`/`SelfSignedCertBase64`, `CleanSession`,
   `KeepAliveSeconds`, `ConnectionTimeoutSeconds`, `AutoConnect`,
   `ShowReconnectionStatus`, `AutoAccept` - is optional, defaulting to
   whatever the broker draft already had). Any other app's fields are
   free-form.
3. **Pull** (e.g. a TV pairing for the first time, or Z2M Dash setting
   up a new broker):
   - The device calls `credential_start.php` with just `{"app": "...",
     "label": "..."}` (no `fields`) — no auth, it has no account of its
     own. Gets back `{code, token, expires_in}` and shows the code/QR
     (linking to `credential_view.php?code=...`).
   - Someone opens that link — requires login. Since this share has no
     data yet, the page shows their own saved presets for that app and
     lets them pick one to send.
   - The device's own poll of `credential_status.php?token=...`
     receives the resolved fields once that happens.
4. **Push** (available to any app that wants it, though nothing
   currently uses this mode):
   - The sending app calls `credential_start.php` with `{"app": "...",
     "label": "...", "fields": {...}}` included up front. Gets back the
     same `{code, token, expires_in}` shape and shows the code/QR.
   - Someone opens `credential_view.php?code=...` — requires login.
     Since this share already has data, confirming the code does a
     one-time reveal of the fields as plain text to copy elsewhere. A
     second visit with the same code shows nothing.
   - The sending app can still poll `credential_status.php?token=...`
     to show "waiting..." / "picked up", using only `status` and
     ignoring the `fields` the response happens to also carry (it
     already has its own copy).
5. Either way, once resolved, `credential_status.php` hands the
   resolved fields to whoever holds the token exactly once, then
   deletes the row.

## Known limitations, disclosed rather than silently glossed over

- **Secrets are stored in plaintext** in the `saved_credentials` and
  `credential_shares` tables, protected only by normal database access
  controls — not encrypted at rest. Encrypting them would need a
  server-side key, which itself needs careful handling (not committed
  to version control, ideally not just sitting in a config file
  readable by the same process that could be compromised). Worth
  revisiting if this ever handles anything more sensitive than a
  home-automation wake signal or a home MQTT broker's password.
- **No rate limiting** on login attempts, registration, or
  credential-share attempts. Fine for a small personal/friends-and-
  family scale service; worth adding if this is ever exposed more
  broadly.
- **No password reset flow** — not asked for, so not built. A locked-out
  user currently has no self-service way back in.
- **Deployment ordering matters**: the old `pair_start.php`/
  `pair_approve.php`/`pair_poll.php` endpoints are gone as of this
  version. Any MX3 Launcher install still running the old pairing code
  will get 404s until it's updated to call `credential_start.php`/
  `credential_status.php` instead. Don't run
  `drop_old_pairing_system.migration.sql` until every install that
  matters has been updated.
