<?php
/**
 * pair_approve.php
 * -----------------
 * Open this on your PHONE to approve a pairing request. Now requires
 * being logged in (redirects to login.php otherwise), since the
 * pairing needs to be linked to one of YOUR registered pairing URLs.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

$error = "";
$success = "";
$prefilledCode = strtoupper(trim($_GET["code"] ?? ""));

$stmt = db()->prepare("SELECT id, label FROM pairing_urls WHERE user_id = ? ORDER BY label");
$stmt->execute([$userId]);
$myUrls = $stmt->fetchAll();

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $code = strtoupper(trim($_POST["code"] ?? ""));
        $pairingUrlId = intval($_POST["pairing_url_id"] ?? 0);

        if($code === "")
        {
            $error = "Enter the code shown on the TV.";
        } elseif($pairingUrlId === 0) {
            $error = "Choose which pairing URL this device should use.";
        } else {
            // Confirm the chosen pairing_url actually belongs to THIS
            // user -- without this check, a logged-in user could link a
            // TV to someone else's URL just by submitting a different ID.
            $stmt = db()->prepare("SELECT id FROM pairing_urls WHERE id = ? AND user_id = ?");
            $stmt->execute([$pairingUrlId, $userId]);
            if($stmt->fetch() === false)
            {
                $error = "That pairing URL doesn't belong to your account.";
            } else {
                $stmt = db()->prepare(
                    "UPDATE device_pairings SET approved = 1, user_id = ?, pairing_url_id = ?
                     WHERE code = ? AND approved = 0 AND created_at > NOW() - INTERVAL 10 MINUTE"
                );
                $stmt->execute([$userId, $pairingUrlId, $code]);

                if($stmt->rowCount() === 0)
                {
                    $error = "That code wasn't found, or it's expired -- check the TV screen for a fresh one.";
                } else {
                    $success = "Approved. The TV should pick this up within a few seconds.";
                }
            }
        }
    }
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
    <title>Pair device</title>
    <style>
        body { font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px; }
        input[type=text], select { font-size: 20px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; }
        input[type=text] { text-align: center; text-transform: uppercase; letter-spacing: 4px; }
        button { font-size: 18px; padding: 12px; width: 100%; }
        .message { margin-top: 16px; padding: 12px; border-radius: 4px; }
        .error { background: #fdd; }
        .success { background: #dfd; }
    </style>
</head>
<body>
    <h2>Pair device</h2>
    <?php if(empty($myUrls)): ?>
        <p>You don't have any pairing URLs yet. <a href="/dashboard.php">Add one first</a>.</p>
    <?php else: ?>
        <p>Enter the code shown on the TV, and choose which pairing URL it should use.</p>
        <form method="post">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
            <input type="text" name="code" maxlength="6" autofocus autocomplete="off" placeholder="ABCDEF"
                   value="<?= htmlspecialchars($prefilledCode) ?>">
            <select name="pairing_url_id">
                <?php foreach($myUrls as $row): ?>
                    <option value="<?= (int)$row["id"] ?>"><?= htmlspecialchars($row["label"]) ?></option>
                <?php endforeach; ?>
            </select>
            <button type="submit">Approve</button>
        </form>
    <?php endif; ?>
    <?php if($error): ?><div class="message error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?><div class="message success"><?= htmlspecialchars($success) ?></div><?php endif; ?>
    <p><a href="/dashboard.php">Manage your pairing URLs</a></p>
</body>
</html>
