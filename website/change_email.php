<?php
/**
 * change_email.php
 * -----------------
 * Lets a logged-in user change their account email. Requires re-entering
 * their current password, and mirrors register.php's verification-token
 * pattern: the new address isn't live until its confirmation link
 * (confirm_email_change.php) is clicked, so typing an address you don't
 * own can't hijack anything. The old address is notified either way.
 */

require_once __DIR__ . "/auth_helper.php";
require_once __DIR__ . "/mailer.php";

$userId = require_login();

$error = "";
$success = "";

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $action = $_POST["action"] ?? "";

        if($action === "cancel")
        {
            $stmt = db()->prepare(
                "UPDATE users SET pending_email = NULL, email_change_token = NULL, email_change_token_expires_at = NULL WHERE id = ?"
            );
            $stmt->execute([$userId]);
            $success = "Pending email change cancelled.";
        } elseif($action === "start") {
            $newEmail = trim($_POST["new_email"] ?? "");
            $currentPassword = $_POST["current_password"] ?? "";

            $stmt = db()->prepare("SELECT email, password_hash FROM users WHERE id = ?");
            $stmt->execute([$userId]);
            $user = $stmt->fetch();

            if(!password_verify($currentPassword, $user["password_hash"]))
            {
                $error = "Incorrect password.";
            } elseif(!filter_var($newEmail, FILTER_VALIDATE_EMAIL) || strlen($newEmail) > 255) {
                $error = "Enter a valid email address.";
            } elseif(strcasecmp($newEmail, $user["email"]) === 0) {
                $error = "That's already your current email address.";
            } else {
                // Same "does this email already exist" check as register.php.
                $stmt = db()->prepare("SELECT id FROM users WHERE email = ?");
                $stmt->execute([$newEmail]);
                if($stmt->fetch())
                {
                    $error = "An account with that email already exists.";
                } else {
                    $token = generate_random_token();
                    $stmt = db()->prepare(
                        "UPDATE users SET pending_email = ?, email_change_token = ?,
                         email_change_token_expires_at = DATE_ADD(NOW(), INTERVAL 24 HOUR) WHERE id = ?"
                    );
                    $stmt->execute([$newEmail, $token, $userId]);

                    $confirmUrl = "https://" . $_SERVER["HTTP_HOST"] . "/confirm_email_change.php?token=" . urlencode($token);
                    send_email(
                        $newEmail,
                        "Confirm your new email address",
                        "<p>Click to confirm this is your new MX3 Launcher pairing account email:</p>"
                        . "<p><a href=\"$confirmUrl\">$confirmUrl</a></p>"
                        . "<p>This link expires in 24 hours. Your email won't change until you click it.</p>"
                    );
                    send_email(
                        $user["email"],
                        "Email change requested",
                        "<p>Someone requested to change this account's email to <strong>"
                        . htmlspecialchars($newEmail) . "</strong>.</p>"
                        . "<p>If this was you, check that address's inbox for a confirmation link. If it "
                        . "wasn't you, log in and cancel it from the \"Change email\" page — your email "
                        . "hasn't changed yet.</p>"
                    );

                    $success = "Confirmation link sent to " . htmlspecialchars($newEmail) . ". Your email won't change until you click it.";
                }
            }
        }
    }
}

$stmt = db()->prepare("SELECT email, pending_email, email_change_token_expires_at FROM users WHERE id = ?");
$stmt->execute([$userId]);
$user = $stmt->fetch();
$hasPendingChange = $user["pending_email"] !== null;

$csrfToken = generate_csrf_token();
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Change email</title>
    <style>
        body { font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px; }
        input { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; }
        button, .btn {
            font-family: inherit; font-size: 16px; padding: 10px; width: 100%; text-align: center;
            text-decoration: none; color: inherit; background: #eee; border: none; border-radius: 4px;
            cursor: pointer; box-sizing: border-box; display: inline-block;
        }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .pending { background: #ffe; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
    </style>
</head>
<body>
    <h2>Change email</h2>
    <p><a href="/manage_credentials.php">&larr; Back</a></p>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?><div class="success"><?= htmlspecialchars($success) ?></div><?php endif; ?>

    <p>Current email: <strong><?= htmlspecialchars($user["email"]) ?></strong></p>

    <?php if($hasPendingChange): ?>
        <div class="pending">
            Pending change to <strong><?= htmlspecialchars($user["pending_email"]) ?></strong>.
            Waiting for confirmation (expires <?= htmlspecialchars($user["email_change_token_expires_at"]) ?>).
        </div>
        <form method="post">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
            <input type="hidden" name="action" value="cancel">
            <button type="submit">Cancel pending change</button>
        </form>
    <?php endif; ?>

    <h3><?= $hasPendingChange ? "Start a different change" : "Change to a new email" ?></h3>
    <form method="post">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="hidden" name="action" value="start">
        <input type="email" name="new_email" placeholder="New email" required autofocus>
        <input type="password" name="current_password" placeholder="Current password" required>
        <button type="submit">Send confirmation link</button>
    </form>
</body>
</html>
