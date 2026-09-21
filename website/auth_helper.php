<?php
/**
 * auth_helper.php
 * ---------------
 * Shared session/auth/CSRF utilities used by every account-facing page.
 */

require_once __DIR__ . "/db.php";

function auth_start_session(): void
{
    if(session_status() === PHP_SESSION_NONE)
    {
        session_set_cookie_params([
            "lifetime" => 0,
            "path" => "/",
            // This whole site handles passwords and device secrets --
            // it should only ever be served over HTTPS, and this flag
            // makes sure the session cookie itself is never sent over a
            // plain HTTP connection even by accident.
            "secure" => true,
            "httponly" => true,
            "samesite" => "Lax",
        ]);
        session_start();
    }
}

function current_user_id(): ?int
{
    auth_start_session();
    return $_SESSION["user_id"] ?? null;
}

function require_login(): int
{
    $userId = current_user_id();
    if($userId === null)
    {
        header("Location: /login.php");
        exit;
    }
    return $userId;
}

function generate_csrf_token(): string
{
    auth_start_session();
    if(!isset($_SESSION["csrf_token"]))
        $_SESSION["csrf_token"] = bin2hex(random_bytes(32));

    return $_SESSION["csrf_token"];
}

/** hash_equals() specifically, not === -- a plain string comparison
 *  short-circuits on the first differing byte, which leaks timing
 *  information an attacker could use to guess the token byte by byte.
 *  hash_equals() always takes the same time regardless of where the
 *  strings first differ. */
function verify_csrf_token(?string $token): bool
{
    auth_start_session();
    return isset($_SESSION["csrf_token"]) && $token !== null && hash_equals($_SESSION["csrf_token"], $token);
}

function generate_random_token(): string
{
    return bin2hex(random_bytes(32));
}

/** Resolves a device_tokens row (minted once via the DEVICE_PAIR_APP_NAME
 *  pairing flow in credential_view.php) to its owning user id, touching
 *  last_used_at - or null if the token doesn't exist/was revoked. No
 *  session/login involved; the token itself is the credential, the same
 *  trust model as a credential_shares poll token but much longer-lived -
 *  used by device_backup.php/device_backups_list.php/device_backup_get.php
 *  so a paired device can back up/restore without a human approving each
 *  individual call. */
function user_id_from_device_token(string $token): ?int
{
    $stmt = db()->prepare("SELECT user_id FROM device_tokens WHERE token = ?");
    $stmt->execute([$token]);
    $row = $stmt->fetch();
    if($row === false)
        return null;

    $stmt = db()->prepare("UPDATE device_tokens SET last_used_at = NOW() WHERE token = ?");
    $stmt->execute([$token]);

    return (int)$row["user_id"];
}
