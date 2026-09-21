<?php
/**
 * device_backup.php
 * ------------------
 * A paired device (holding a device_tokens token minted via the
 * DEVICE_PAIR_APP_NAME pairing flow in credential_view.php) calls this
 * directly to store a settings backup - no human approval, no code/QR,
 * unlike credential_start.php's flows. See LauncherConfigSync.kt's
 * uploadBackup().
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

function bad_request(string $error, int $code = 400): void
{
    http_response_code($code);
    echo json_encode(["ok" => false, "error" => $error]);
    exit;
}

if($_SERVER["REQUEST_METHOD"] !== "POST")
    bad_request("POST required");

// Kept in sync with manage_credentials.php/settings_backups.php/
// credential_view.php's own copy of this constant, and with
// LauncherConfigSync.kt's APP_NAME.
const BACKUP_APP_NAME = "MX3Launcher Settings";
// Kept in sync with credential_start.php's per-field limit.
const MAX_CONFIG_LENGTH = 32768;

$raw = file_get_contents("php://input", false, null, 0, 65536);
$body = json_decode($raw !== false ? $raw : "", true);
if(!is_array($body))
    bad_request("Invalid JSON body");

$token = trim((string)($body["token"] ?? ""));
$config = (string)($body["config"] ?? "");

if($token === "")
    bad_request("Missing token");
if($config === "" || strlen($config) > MAX_CONFIG_LENGTH)
    bad_request("\"config\" is required (max " . MAX_CONFIG_LENGTH . " chars)");

$userId = user_id_from_device_token($token);
if($userId === null)
    bad_request("Unknown or revoked device token", 401);

$label = "Backup — " . date("M j, Y g:i A");
$stmt = db()->prepare(
    "INSERT INTO saved_credentials (user_id, app_name, label, fields_json, created_at)
     VALUES (?, ?, ?, ?, NOW())"
);
$stmt->execute([$userId, BACKUP_APP_NAME, $label, json_encode(["Config" => $config])]);

echo json_encode(["ok" => true, "label" => $label]);
