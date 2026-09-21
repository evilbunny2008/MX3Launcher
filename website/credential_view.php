<?php
/**
 * credential_view.php
 * --------------------
 * Open this (scan the QR, or type the code) to act on a credential
 * share started via credential_start.php. Requires being logged in,
 * same as the old pair_approve.php - ties this to an account rather
 * than just "whoever has the QR/code", since this can hand over real
 * passwords. Branches on which mode that share is in:
 *
 * - "push" (its payload_json is already set): show a one-time reveal
 *   of the fields as plain text to copy elsewhere. A second visit with
 *   the same code shows nothing.
 * - "pull" (payload_json is still NULL - a device asked to receive
 *   credentials for requested_app, but has none of its own): let the
 *   approver pick one of their own saved_credentials presets **for that
 *   app** to attach - the picker only ever offers matching-app presets,
 *   so there's no dropdown default to pick wrong. If a default_credentials
 *   row already remembers a choice for this app, the picker is skipped
 *   entirely in favour of a single "Approve" button (see
 *   default_credentials in schema.sql). The requesting device's own poll
 *   of credential_status.php then receives it automatically - this page
 *   never displays the values in that case, since this browser is the
 *   approver, not the device actually receiving them.
 *
 * Either way, once acted on, viewed_at gets set and
 * credential_status.php reports it resolved.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

// Kept in sync with manage_credentials.php/settings_backups.php's own copy
// of this constant, and with LauncherConfigSync.kt's APP_NAME.
const BACKUP_APP_NAME = "MX3Launcher Settings";

$error = "";
$revealed = null; // ["app"=>, "label"=>, "fields"=>[...]] - push mode, once shown
$savedAsPreset = false; // true once a push-mode reveal's data has also been saved below
$attached = false; // true once a preset has been attached - pull mode
$prefilledCode = strtoupper(trim($_GET["code"] ?? ""));
// ?choose=1 bypasses a remembered default for just this one confirmation,
// without changing what's remembered - see the picker's "remember" checkbox.
$forceChoose = isset($_GET["choose"]);

function find_pending_share(string $code)
{
    $stmt = db()->prepare(
        "SELECT payload_json, requested_app FROM credential_shares
         WHERE code = ? AND viewed_at IS NULL AND created_at > NOW() - INTERVAL 10 MINUTE"
    );
    $stmt->execute([$code]);
    return $stmt->fetch();
}

/** The saved_credentials row a default_credentials entry for (userId, app)
 *  points at, or false if there's no remembered default or it no longer
 *  exists (e.g. deleted since). */
function find_default_preset(int $userId, string $app)
{
    $stmt = db()->prepare(
        "SELECT sc.id, sc.app_name, sc.label, sc.fields_json
         FROM default_credentials dc
         JOIN saved_credentials sc ON sc.id = dc.saved_credential_id
         WHERE dc.user_id = ? AND dc.app_name = ? AND sc.user_id = ?"
    );
    $stmt->execute([$userId, $app, $userId]);
    return $stmt->fetch();
}

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $code = strtoupper(trim($_POST["code"] ?? ""));
        $prefilledCode = $code;
        $presetId = intval($_POST["preset_id"] ?? 0);
        $useDefault = ($_POST["action"] ?? "") === "approve_default";
        $remember = isset($_POST["remember"]);

        if($code === "")
        {
            $error = "Enter the code shown on the other device.";
        } else {
            $share = find_pending_share($code);
            if($share === false)
            {
                $error = "That code wasn't found, was already used, or has expired - check the other device for a fresh one.";
            } elseif($share["payload_json"] !== null) {
                // Push mode - reveal now, regardless of any preset_id
                // that might have been posted (there's nothing to pick
                // here). Also saved as a reusable saved_credentials preset
                // in the same step (not a separate opt-in action), so a
                // sending device that polls credential_status.php and sees
                // "viewed" can rely on that meaning it was actually kept,
                // not just glanced at - e.g. MX3 Launcher's settings-backup
                // flow (LauncherConfigSync.kt) needs that guarantee, and
                // any human-readable use of the reveal below still works
                // exactly as before.
                $stmt = db()->prepare(
                    "UPDATE credential_shares SET viewed_at = NOW(), viewed_by_user_id = ? WHERE code = ?"
                );
                $stmt->execute([$userId, $code]);

                $decoded = json_decode($share["payload_json"], true);
                if(is_array($decoded))
                {
                    $revealed = $decoded;
                    $stmt = db()->prepare(
                        "INSERT INTO saved_credentials (user_id, app_name, label, fields_json, created_at)
                         VALUES (?, ?, ?, ?, NOW())"
                    );
                    $stmt->execute([
                        $userId,
                        (string)($decoded["app"] ?? ""),
                        (string)($decoded["label"] ?? ""),
                        json_encode($decoded["fields"] ?? new stdClass()),
                    ]);
                    $savedAsPreset = true;
                } else {
                    $error = "That entry's data was malformed.";
                }
            } else {
                // Pull mode - attach a preset, owned by this user: either
                // the remembered default for this app, or one explicitly
                // picked from the (matching-app-only) dropdown.
                $preset = false;
                if($useDefault)
                {
                    $preset = find_default_preset($userId, (string)($share["requested_app"] ?? ""));
                    if($preset === false)
                        $error = "Your remembered choice for this is no longer available - pick one below instead.";
                } elseif($presetId === 0) {
                    $error = "Choose which saved credentials to send.";
                } else {
                    $stmt = db()->prepare(
                        "SELECT id, app_name, label, fields_json FROM saved_credentials WHERE id = ? AND user_id = ?"
                    );
                    $stmt->execute([$presetId, $userId]);
                    $preset = $stmt->fetch();
                    if($preset === false)
                        $error = "That saved credential entry doesn't belong to your account.";
                }

                if($preset !== false)
                {
                    $payloadJson = json_encode([
                        "app" => $preset["app_name"],
                        "label" => $preset["label"],
                        "fields" => json_decode($preset["fields_json"], true) ?: new stdClass(),
                    ]);
                    // payload_json IS NULL in the WHERE guards against a
                    // double-submit race attaching two different presets.
                    $stmt = db()->prepare(
                        "UPDATE credential_shares SET payload_json = ?, viewed_at = NOW(), viewed_by_user_id = ?
                         WHERE code = ? AND payload_json IS NULL"
                    );
                    $stmt->execute([$payloadJson, $userId, $code]);

                    if($stmt->rowCount() === 0)
                    {
                        $error = "That code was already resolved - check the other device for a fresh one.";
                    } else {
                        $attached = true;
                        if($remember)
                        {
                            $stmt = db()->prepare(
                                "INSERT INTO default_credentials (user_id, app_name, saved_credential_id)
                                 VALUES (?, ?, ?)
                                 ON DUPLICATE KEY UPDATE saved_credential_id = VALUES(saved_credential_id)"
                            );
                            $stmt->execute([$userId, $preset["app_name"], $preset["id"]]);
                        }
                    }
                }
            }
        }
    }
}

// For a GET (or a POST that hasn't resolved anything yet), look up the
// share fresh so the pull-mode picker/approval screen knows which app's
// presets to offer.
$pendingShare = ($revealed === null && !$attached && $prefilledCode !== "") ? find_pending_share($prefilledCode) : false;
$isPullMode = $pendingShare !== false && $pendingShare["payload_json"] === null;
$requestedApp = $isPullMode ? $pendingShare["requested_app"] : null;

$defaultPreset = false;
if($isPullMode && !$forceChoose)
    $defaultPreset = find_default_preset($userId, (string)$requestedApp);

$presets = [];
if($isPullMode && $defaultPreset === false)
{
    $stmt = db()->prepare(
        "SELECT id, app_name, label FROM saved_credentials WHERE user_id = ? AND app_name = ? ORDER BY label"
    );
    $stmt->execute([$userId, $requestedApp ?? ""]);
    $presets = $stmt->fetchAll();
}

$csrfToken = generate_csrf_token();
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Receive credentials</title>
    <style>
        body { font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px; }
        input[type=text], select {
            font-size: 20px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box;
        }
        input[type=text] { text-align: center; text-transform: uppercase; letter-spacing: 4px; }
        button { font-size: 18px; padding: 12px; width: 100%; }
        .message { margin-top: 16px; padding: 12px; border-radius: 4px; }
        .error { background: #fdd; }
        .notice { background: #ffd; }
        table { width: 100%; border-collapse: collapse; margin-top: 16px; }
        td { padding: 8px; border-bottom: 1px solid #ddd; word-break: break-all; }
        td:first-child { font-weight: bold; white-space: nowrap; padding-right: 12px; }
        label.remember { display: block; margin: -4px 0 12px; }
        label.remember input { width: auto; margin-right: 6px; vertical-align: middle; }
        .secondary { text-align: center; margin-top: 8px; }
    </style>
</head>
<body>
    <h2>Receive credentials</h2>
    <?php if($revealed !== null): ?>
        <p>
            <?= htmlspecialchars($revealed["app"] ?? "") ?><?php if(!empty($revealed["label"])): ?>
                &mdash; <?= htmlspecialchars($revealed["label"]) ?>
            <?php endif; ?>
        </p>
        <table>
            <?php foreach(($revealed["fields"] ?? []) as $key => $value): ?>
                <tr><td><?= htmlspecialchars($key) ?></td><td><?= htmlspecialchars($value) ?></td></tr>
            <?php endforeach; ?>
        </table>
        <p class="message notice">
            Copy these into whatever needs them now if you want to - this page won't show them again.
            <?php if($savedAsPreset && ($revealed["app"] ?? "") === BACKUP_APP_NAME): ?>
                It's also been saved to <a href="/settings_backups.php">your settings backups</a> for later.
            <?php elseif($savedAsPreset): ?>
                They've also been saved to <a href="/manage_credentials.php">your saved credentials</a> for later.
            <?php endif; ?>
        </p>
    <?php elseif($attached): ?>
        <p class="message notice">Sent. The requesting device should pick this up within a few seconds.</p>
    <?php elseif($isPullMode && $defaultPreset !== false): ?>
        <p>
            <?= htmlspecialchars($requestedApp ?: "A device") ?> is asking to receive credentials.
        </p>
        <form method="post">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
            <input type="hidden" name="code" value="<?= htmlspecialchars($prefilledCode) ?>">
            <input type="hidden" name="action" value="approve_default">
            <button type="submit">Approve sending &ldquo;<?= htmlspecialchars($defaultPreset["label"]) ?>&rdquo;</button>
        </form>
        <p class="secondary">
            <a href="/credential_view.php?code=<?= urlencode($prefilledCode) ?>&choose=1">Choose a different one instead</a>
        </p>
    <?php elseif($isPullMode): ?>
        <p>
            <?= htmlspecialchars($requestedApp ?: "A device") ?> is asking to receive credentials.
            Choose which of your saved entries to send it.
        </p>
        <?php if(empty($presets) && $requestedApp === BACKUP_APP_NAME): ?>
            <p class="message error">
                You don't have any settings backups yet.
                Use "Back up settings" on the launcher first, then come back to this code.
            </p>
        <?php elseif(empty($presets)): ?>
            <p class="message error">
                You don't have any saved credentials for <?= htmlspecialchars($requestedApp ?: "this app") ?> yet.
                <a href="/manage_credentials.php">Add one first</a>, then come back to this code.
            </p>
        <?php else: ?>
            <form method="post">
                <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                <input type="hidden" name="code" value="<?= htmlspecialchars($prefilledCode) ?>">
                <select name="preset_id">
                    <?php foreach($presets as $preset): ?>
                        <option value="<?= (int)$preset["id"] ?>">
                            <?= htmlspecialchars($preset["label"]) ?>
                        </option>
                    <?php endforeach; ?>
                </select>
                <label class="remember">
                    <input type="checkbox" name="remember" value="1">
                    Remember this choice for <?= htmlspecialchars($requestedApp ?: "this app") ?> -
                    future requests skip straight to an Approve button
                </label>
                <button type="submit">Send</button>
            </form>
        <?php endif; ?>
    <?php else: ?>
        <p>Confirm the code shown on the other device, then continue.</p>
        <form method="post">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
            <input type="text" name="code" maxlength="6" autofocus autocomplete="off" placeholder="ABCDEF"
                   value="<?= htmlspecialchars($prefilledCode) ?>">
            <button type="submit">Continue</button>
        </form>
    <?php endif; ?>
    <?php if($error): ?><div class="message error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <p><a href="/manage_credentials.php">Manage your saved credentials</a></p>
</body>
</html>
