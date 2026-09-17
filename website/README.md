# MX3 Launcher self-service pairing site

Multi-user accounts, each with one or more registered pairing URL/secret
entries. Replaces the earlier flat-file, single-fixed-URL pairing system
entirely.

## Setup

1. **Database**: create a MariaDB database, then run `schema.sql` against it:
   ```
   mysql -u root -p your_database_name < schema.sql
   ```

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

5. **Cron cleanup (optional but recommended)**: expired pairing attempts
   are already ignored by every query, but nothing deletes them without
   this running periodically:
   ```
   0 * * * * php /path/to/cleanup_expired_pairings.php
   ```

## Replacing the old pairing system

This entirely replaces the earlier flat-file version
(`pairing_helper.php`, and the old `pair_start.php`/`pair_approve.php`/
`pair_poll.php` that read from a fixed `PAIRING_GRANTED_URL`/
`PAIRING_GRANTED_SECRET` pair). Once this is deployed:

- Delete the old `pairing_helper.php` and the `/tmp/mx3launcher_pairing/`
  session files it used.
- The **Android app needs no changes** — `pair_poll.php`'s JSON response
  shape (`{ok, status, url, secret}` with the same `pending`/`expired`/
  `approved` status values) is deliberately unchanged from the old
  version, and the QR code still encodes `pair_approve.php?code=...`
  exactly as before.

## How it works

1. Someone lands on `index.php` (the site's landing page, explaining
   what this is for), then signs up (`register.php`), verifies their
   email (`verify_email.php`), and logs in (`login.php`).
2. On `dashboard.php`, they register one or more pairing URL/secret
   pairs — whatever their own home server's wake-endpoint URL and
   shared secret are.
3. On the TV, MX3 Launcher calls `pair_start.php` (no auth — the TV
   doesn't have an account of its own) and shows the resulting code/QR.
4. The person scans the QR (or types the code) on their phone, which
   opens `pair_approve.php` — now requiring login. They pick *which* of
   their registered pairing URLs this particular TV should use, and
   approve.
5. The TV's next poll to `pair_poll.php` receives that specific URL and
   secret, saves them locally, and stops polling.

## Known limitations, disclosed rather than silently glossed over

- **Secrets are stored in plaintext** in the `pairing_urls` table,
  protected only by normal database access controls — not encrypted at
  rest. Encrypting them would need a server-side key, which itself needs
  careful handling (not committed to version control, ideally not just
  sitting in a config file readable by the same process that could be
  compromised). Worth revisiting if this ever handles anything more
  sensitive than a home-automation wake signal.
- **No rate limiting** on login attempts, registration, or pairing
  approval attempts. Fine for a small personal/friends-and-family scale
  service; worth adding if this is ever exposed more broadly.
- **No password reset flow** — not asked for, so not built. A locked-out
  user currently has no self-service way back in.
