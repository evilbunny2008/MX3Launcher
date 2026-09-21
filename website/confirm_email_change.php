<?php
/**
 * confirm_email_change.php
 * -------------------------
 * Handles the link change_email.php sends to the NEW address. Swaps the
 * account's email over once its owner proves receipt by clicking it, then
 * notifies the old address. No login check -- the token itself, not the
 * current session, identifies the account, same as verify_email.php.
 */

require_once __DIR__ . "/auth_helper.php";
require_once __DIR__ . "/mailer.php";

$token = $_GET["token"] ?? "";
$message = "";

if($token === "")
{
    $message = "Missing confirmation token.";
} else {
    $stmt = db()->prepare(
        "SELECT id, email, pending_email FROM users WHERE email_change_token = ? AND email_change_token_expires_at > NOW()"
    );
    $stmt->execute([$token]);
    $user = $stmt->fetch();

    if($user === false)
    {
        $message = "That confirmation link is invalid or has expired.";
    } else {
        $oldEmail = $user["email"];
        $newEmail = $user["pending_email"];

        try {
            $stmt = db()->prepare(
                "UPDATE users SET email = ?, pending_email = NULL, email_change_token = NULL,
                 email_change_token_expires_at = NULL WHERE id = ?"
            );
            $stmt->execute([$newEmail, $user["id"]]);

            send_email(
                $oldEmail,
                "Your email address was changed",
                "<p>This account's email was changed to <strong>" . htmlspecialchars($newEmail) . "</strong>.</p>"
                . "<p>If you didn't make this change, there is currently no self-service way to undo it "
                . "-- contact the site owner.</p>"
            );

            $message = "Email address updated to $newEmail. You can now log in with it.";
        } catch(PDOException $e) {
            // Unique constraint on email -- someone else claimed this
            // exact address between the request and this confirmation.
            $stmt = db()->prepare(
                "UPDATE users SET pending_email = NULL, email_change_token = NULL,
                 email_change_token_expires_at = NULL WHERE id = ?"
            );
            $stmt->execute([$user["id"]]);
            $message = "That email address is now used by another account. Please start the change again with a different address.";
        }
    }
}
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Confirm email change</title>
</head>
<body style="font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px;">
    <h2>Email change</h2>
    <p><?= htmlspecialchars($message) ?></p>
    <p><a href="/login.php">Go to login</a></p>
</body>
</html>
