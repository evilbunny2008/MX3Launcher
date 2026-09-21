<?php
/**
 * settings_backups.php
 * ---------------------
 * Lists this account's MX3 Launcher settings backups - saved_credentials
 * rows with app_name BACKUP_APP_NAME, created automatically by
 * credential_view.php's push-mode handling whenever a "Back up settings"
 * QR/code (see the app's LauncherConfigSync.kt) is confirmed here. Split
 * out from manage_credentials.php because backups accumulate one new
 * entry per backup (never overwritten) rather than staying at a handful of
 * hand-managed entries, and would otherwise swamp that page's single list
 * over time.
 *
 * No add/edit form for these (they're not meant to be hand-typed) - but
 * "Download" and "Upload a backup file" below let a backup leave/enter the
 * account as a plain .json file, e.g. to archive one somewhere else, or to
 * bring one in from a device that can't currently reach the pairing flow.
 * A "Restore settings" pull-mode request from the app picks from whichever
 * of these exist the same way either way, unaffected by how they got here.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

const BACKUP_APP_NAME = "MX3Launcher Settings";
const FIELD_CONFIG = "Config";
// Mirrors LauncherConfigSync.kt's KNOWN_KEYS - an upload is only accepted
// as a real backup if it has at least one of these, same guard that
// protects the app's own restore path from a corrupt/unrelated file.
const KNOWN_SETTINGS_KEYS = ["themeMode", "gradientId", "columns", "hiddenPackages", "appOrder"];
// Kept in sync with credential_start.php's per-field limit.
const MAX_CONFIG_LENGTH = 32768;

function backup_filename(string $label): string
{
    $slug = preg_replace('/[^A-Za-z0-9_-]+/', '_', $label);
    $slug = trim($slug, '_');
    return ($slug !== "" ? $slug : "mx3launcher_settings") . ".json";
}

// Handled before any HTML output - this response is a file, not a page.
if($_SERVER["REQUEST_METHOD"] === "GET" && intval($_GET["download"] ?? 0) > 0)
{
    $stmt = db()->prepare(
        "SELECT label, fields_json FROM saved_credentials WHERE id = ? AND user_id = ? AND app_name = ?"
    );
    $stmt->execute([intval($_GET["download"]), $userId, BACKUP_APP_NAME]);
    $row = $stmt->fetch();
    if($row === false)
    {
        http_response_code(404);
        exit("Not found.");
    }

    $fields = json_decode($row["fields_json"], true);
    $config = is_array($fields) ? (string)($fields[FIELD_CONFIG] ?? "") : "";

    header("Content-Type: application/json");
    header("Content-Disposition: attachment; filename=\"" . backup_filename($row["label"]) . "\"");
    header("Content-Length: " . strlen($config));
    echo $config;
    exit;
}

$error = "";
$success = "";

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } elseif(($_POST["action"] ?? "") === "delete") {
        $id = intval($_POST["id"] ?? 0);
        // app_name in the WHERE guards against this form being used to
        // delete some other kind of saved credential by id.
        $stmt = db()->prepare(
            "DELETE FROM saved_credentials WHERE id = ? AND user_id = ? AND app_name = ?"
        );
        $stmt->execute([$id, $userId, BACKUP_APP_NAME]);
        $success = "Deleted.";
    } elseif(($_POST["action"] ?? "") === "rename") {
        $id = intval($_POST["id"] ?? 0);
        $label = trim((string)($_POST["label"] ?? ""));
        if($label === "" || strlen($label) > 128)
        {
            $error = "Label must be 1-128 characters.";
        } else {
            $stmt = db()->prepare(
                "UPDATE saved_credentials SET label = ? WHERE id = ? AND user_id = ? AND app_name = ?"
            );
            $stmt->execute([$label, $id, $userId, BACKUP_APP_NAME]);
            $success = "Renamed.";
        }
    } elseif(($_POST["action"] ?? "") === "upload") {
        $upload = $_FILES["backup_file"] ?? null;
        if($upload === null || ($upload["error"] ?? UPLOAD_ERR_NO_FILE) === UPLOAD_ERR_NO_FILE)
        {
            $error = "Choose a file to upload.";
        } elseif($upload["error"] !== UPLOAD_ERR_OK) {
            $error = "Upload failed (error code " . (int)$upload["error"] . ").";
        } elseif($upload["size"] > MAX_CONFIG_LENGTH) {
            $error = "That file is too large to be a settings backup (max " . MAX_CONFIG_LENGTH . " bytes).";
        } else {
            $raw = file_get_contents($upload["tmp_name"]);
            $decoded = $raw !== false ? json_decode($raw, true) : null;
            $looksLikeSettings = is_array($decoded)
                && count(array_intersect(KNOWN_SETTINGS_KEYS, array_keys($decoded))) > 0;

            if(!$looksLikeSettings)
            {
                $error = "That file doesn't look like an MX3 Launcher settings backup.";
            } else {
                $label = trim((string)($_POST["label"] ?? ""));
                if($label === "")
                    $label = "Uploaded — " . date("M j, Y g:i A");
                if(strlen($label) > 128)
                    $label = substr($label, 0, 128);

                $fieldsJson = json_encode([FIELD_CONFIG => $raw]);
                $stmt = db()->prepare(
                    "INSERT INTO saved_credentials (user_id, app_name, label, fields_json, created_at)
                     VALUES (?, ?, ?, ?, NOW())"
                );
                $stmt->execute([$userId, BACKUP_APP_NAME, $label, $fieldsJson]);
                $success = "Uploaded.";
            }
        }
    }
}

$stmt = db()->prepare(
    "SELECT id, label, created_at FROM saved_credentials
     WHERE user_id = ? AND app_name = ? ORDER BY created_at DESC"
);
$stmt->execute([$userId, BACKUP_APP_NAME]);
$backups = $stmt->fetchAll();

$csrfToken = generate_csrf_token();
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Your MX3 Launcher settings backups</title>
    <style>
        body { font-family: sans-serif; max-width: 600px; margin: 40px auto; padding: 0 16px; }
        input[type=text], input[type=file] {
            font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; font-family: inherit;
        }
        button, .btn {
            font-family: inherit; font-size: 16px; line-height: 1.2; padding: 10px;
            display: inline-block; text-align: center; text-decoration: none; color: inherit;
            background: #eee; border: none; border-radius: 4px; cursor: pointer;
            box-sizing: border-box; appearance: none; -webkit-appearance: none;
            margin: 0 8px 0 0; min-width: 90px; vertical-align: middle;
        }
        .nav-buttons { text-align: center; }
        .nav-buttons .btn:last-child { margin-right: 0; }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .hint { color: #666; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        td, th { text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }
        .rename-form { display: flex; gap: 8px; }
        .rename-form input[type=text] { margin-bottom: 0; }
    </style>
</head>
<body>
    <h2>Your MX3 Launcher settings backups</h2>
    <p class="hint">
        Made by tapping "Back up settings" on the launcher and confirming the code/QR here, or
        uploaded below. "Restore settings" on the launcher picks from these the same way either way.
    </p>
    <p class="nav-buttons">
        <a class="btn" href="/paired_devices.php">Your paired devices</a>
        <a class="btn" href="/manage_credentials.php">Your other saved credentials</a>
        <a class="btn" href="/credential_view.php">Enter a pairing code</a>
    </p>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?><div class="success"><?= htmlspecialchars($success) ?></div><?php endif; ?>

    <table>
        <tr><th>Label</th><th></th></tr>
        <?php foreach($backups as $row): ?>
        <tr>
            <td>
                <form method="post" class="rename-form">
                    <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                    <input type="hidden" name="action" value="rename">
                    <input type="hidden" name="id" value="<?= (int)$row["id"] ?>">
                    <input type="text" name="label" value="<?= htmlspecialchars($row["label"]) ?>" maxlength="128">
                    <button type="submit">Rename</button>
                </form>
            </td>
            <td>
                <a class="btn" href="/settings_backups.php?download=<?= (int)$row["id"] ?>">Download</a>
                <form method="post" style="display:inline;">
                    <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="id" value="<?= (int)$row["id"] ?>">
                    <button type="submit" onclick="return confirm('Delete this backup?');">Delete</button>
                </form>
            </td>
        </tr>
        <?php endforeach; ?>
        <?php if(empty($backups)): ?>
        <tr><td colspan="2" class="hint">No backups yet.</td></tr>
        <?php endif; ?>
    </table>

    <h3>Upload a backup file</h3>
    <form method="post" enctype="multipart/form-data">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="hidden" name="action" value="upload">
        <input type="text" name="label" placeholder="Label (optional)" maxlength="128">
        <input type="file" name="backup_file" accept="application/json,.json" required>
        <button type="submit">Upload</button>
    </form>
    <p class="hint">
        Accepts a .json file previously downloaded from here, or exported some other way -
        it just needs to be an MX3 Launcher settings backup underneath.
    </p>
</body>
</html>
