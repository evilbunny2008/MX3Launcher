<?php
/**
 * device_backups_list.php
 * ------------------------
 * A paired device (see device_backup.php) calls this directly to list its
 * account's settings backups, to show as an on-device "Restore settings"
 * picker without any trip through this site. See LauncherConfigSync.kt's
 * listBackups().
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

const BACKUP_APP_NAME = "MX3Launcher Settings";

$token = trim((string)($_GET["token"] ?? ""));
if($token === "")
{
    http_response_code(400);
    echo json_encode(["ok" => false, "error" => "Missing token"]);
    exit;
}

$userId = user_id_from_device_token($token);
if($userId === null)
{
    http_response_code(401);
    echo json_encode(["ok" => false, "error" => "Unknown or revoked device token"]);
    exit;
}

$stmt = db()->prepare(
    "SELECT id, label, created_at FROM saved_credentials
     WHERE user_id = ? AND app_name = ? ORDER BY created_at DESC"
);
$stmt->execute([$userId, BACKUP_APP_NAME]);

$backups = array_map(
    fn($row) => ["id" => (int)$row["id"], "label" => $row["label"], "created_at" => $row["created_at"]],
    $stmt->fetchAll()
);

echo json_encode(["ok" => true, "backups" => $backups]);
