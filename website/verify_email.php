<?php
/**
 * verify_email.php
 * ----------------
 * Handles the link sent by register.php. Marks the account verified if
 * the token is valid and not expired.
 */

require_once __DIR__ . "/auth_helper.php";

$token = $_GET["token"] ?? "";
$message = "";

if($token === "")
{
    $message = "Missing verification token.";
} else {
    $stmt = db()->prepare(
        "SELECT id FROM users WHERE verification_token = ? AND verification_token_expires_at > NOW() AND email_verified = 0"
    );
    $stmt->execute([$token]);
    $user = $stmt->fetch();

    if($user === false)
    {
        $message = "That verification link is invalid or has expired.";
    } else {
        $stmt = db()->prepare(
            "UPDATE users SET email_verified = 1, verification_token = NULL, verification_token_expires_at = NULL WHERE id = ?"
        );
        $stmt->execute([$user["id"]]);
        $message = "Email verified! You can now log in.";
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
    <title>Verify email</title>
</head>
<body style="font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px;">
    <h2>Email verification</h2>
    <p><?= htmlspecialchars($message) ?></p>
    <p><a href="/login.php">Go to login</a></p>
</body>
</html>
