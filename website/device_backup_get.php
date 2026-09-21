<?php
/**
 * device_backup_get.php
 * ----------------------
 * A paired device (see device_backup.php) calls this directly to fetch one
 * specific backup by id, picked from device_backups_list.php's response,
 * to actually restore. See LauncherConfigSync.kt's fetchBackup().
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

const BACKUP_APP_NAME = "MX3Launcher Settings";

$token = trim((string)($_GET["token"] ?? ""));
$id = intval($_GET["id"] ?? 0);

if($token === "" || $id === 0)
{
    http_response_code(400);
    echo json_encode(["ok" => false, "error" => "Missing token or id"]);
    exit;
}

$userId = user_id_from_device_token($token);
if($userId === null)
{
    http_response_code(401);
    echo json_encode(["ok" => false, "error" => "Unknown or revoked device token"]);
    exit;
}

// user_id and app_name in the WHERE make sure a paired device can only
// ever fetch its own account's settings backups, never another user's row
// or some other kind of saved credential, regardless of what id it asks for.
$stmt = db()->prepare(
    "SELECT fields_json FROM saved_credentials WHERE id = ? AND user_id = ? AND app_name = ?"
);
$stmt->execute([$id, $userId, BACKUP_APP_NAME]);
$row = $stmt->fetch();
if($row === false)
{
    http_response_code(404);
    echo json_encode(["ok" => false, "error" => "Not found"]);
    exit;
}

$fields = json_decode($row["fields_json"], true);
$config = is_array($fields) ? (string)($fields["Config"] ?? "") : "";

echo json_encode(["ok" => true, "config" => $config]);
