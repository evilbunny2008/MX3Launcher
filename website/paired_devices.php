<?php
/**
 * paired_devices.php
 * -------------------
 * Lists this account's device_tokens - long-lived tokens minted via the
 * DEVICE_PAIR_APP_NAME pairing flow in credential_view.php, which let a
 * device back up/restore its MX3 Launcher settings directly (device_backup.php
 * etc.) without a human approving each individual call. This is the one
 * place to see that a device still holds such a token, and to revoke it
 * (e.g. the device was lost/stolen, or you just don't trust it any more) -
 * revoking here takes effect immediately, the device gets no warning.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

$error = "";
$success = "";

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } elseif(($_POST["action"] ?? "") === "revoke") {
        $id = intval($_POST["id"] ?? 0);
        $stmt = db()->prepare("DELETE FROM device_tokens WHERE id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        $success = "Revoked.";
    } elseif(($_POST["action"] ?? "") === "rename") {
        $id = intval($_POST["id"] ?? 0);
        $label = trim((string)($_POST["label"] ?? ""));
        if($label === "" || strlen($label) > 128)
        {
            $error = "Label must be 1-128 characters.";
        } else {
            $stmt = db()->prepare("UPDATE device_tokens SET label = ? WHERE id = ? AND user_id = ?");
            $stmt->execute([$label, $id, $userId]);
            $success = "Renamed.";
        }
    }
}

$stmt = db()->prepare(
    "SELECT id, label, created_at, last_used_at FROM device_tokens WHERE user_id = ? ORDER BY created_at DESC"
);
$stmt->execute([$userId]);
$devices = $stmt->fetchAll();

$csrfToken = generate_csrf_token();
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Your paired devices</title>
    <style>
        body { font-family: sans-serif; max-width: 600px; margin: 40px auto; padding: 0 16px; }
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
        input[type=text] { font-size: 16px; padding: 10px; box-sizing: border-box; font-family: inherit; }
        .rename-form { display: flex; gap: 8px; }
        .rename-form input[type=text] { margin-bottom: 0; }
    </style>
</head>
<body>
    <h2>Your paired devices</h2>
    <p class="hint">
        Each of these can back up and restore its MX3 Launcher settings on its own, without
        approving each individual call here. Revoke one if you no longer trust it - the device
        gets no warning, its next backup/restore attempt will simply fail until re-paired.
    </p>
    <p class="nav-buttons">
        <a class="btn" href="/settings_backups.php">Your settings backups</a>
        <a class="btn" href="/manage_credentials.php">Your other saved credentials</a>
    </p>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?><div class="success"><?= htmlspecialchars($success) ?></div><?php endif; ?>

    <table>
        <tr><th>Device</th><th>Paired</th><th>Last used</th><th></th></tr>
        <?php foreach($devices as $row): ?>
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
            <td><?= htmlspecialchars($row["created_at"]) ?></td>
            <td><?= htmlspecialchars($row["last_used_at"] ?? "never") ?></td>
            <td>
                <form method="post" style="display:inline;">
                    <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                    <input type="hidden" name="action" value="revoke">
                    <input type="hidden" name="id" value="<?= (int)$row["id"] ?>">
                    <button type="submit" onclick="return confirm('Revoke this device? It will stop being able to back up or restore settings until re-paired.');">Revoke</button>
                </form>
            </td>
        </tr>
        <?php endforeach; ?>
        <?php if(empty($devices)): ?>
        <tr><td colspan="4" class="hint">No paired devices yet.</td></tr>
        <?php endif; ?>
    </table>
</body>
</html>
