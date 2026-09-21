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
 * Deliberately list/delete only, no add/edit form - these aren't meant to
 * be hand-typed. A "Restore settings" pull-mode request from the app still
 * picks from these the same way it always has, unaffected by this page's
 * existence.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

const BACKUP_APP_NAME = "MX3Launcher Settings";

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
        button, .btn {
            font-family: inherit; font-size: 16px; line-height: 1.2; padding: 10px;
            display: inline-block; text-align: center; text-decoration: none; color: inherit;
            background: #eee; border: none; border-radius: 4px; cursor: pointer;
            box-sizing: border-box; appearance: none; -webkit-appearance: none;
            margin: 0 8px 0 0; min-width: 90px; vertical-align: middle;
        }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .hint { color: #666; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        td, th { text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }
    </style>
</head>
<body>
    <h2>Your MX3 Launcher settings backups</h2>
    <p class="hint">
        Made by tapping "Back up settings" on the launcher and confirming the code/QR here.
        "Restore settings" on the launcher picks from these the same way.
    </p>
    <p>
        <a class="btn" href="/manage_credentials.php">Your other saved credentials</a>
        <a class="btn" href="/credential_view.php">Enter a pairing code</a>
    </p>
    <?php if($success): ?><div class="success"><?= htmlspecialchars($success) ?></div><?php endif; ?>

    <table>
        <tr><th>Made</th><th></th></tr>
        <?php foreach($backups as $row): ?>
        <tr>
            <td><?= htmlspecialchars($row["label"]) ?></td>
            <td>
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
</body>
</html>
