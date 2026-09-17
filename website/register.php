<?php
/**
 * register.php
 * ------------
 * Account signup. Sends a verification email before the account can be
 * used to approve any device pairing.
 */

require_once __DIR__ . "/auth_helper.php";
require_once __DIR__ . "/mailer.php";

auth_start_session();

$error = "";
$success = "";

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $email = trim($_POST["email"] ?? "");
        $password = $_POST["password"] ?? "";

        if(!filter_var($email, FILTER_VALIDATE_EMAIL))
        {
            $error = "Enter a valid email address.";
        } elseif(strlen($password) < 10) {
            $error = "Password must be at least 10 characters.";
        } else {
            $stmt = db()->prepare("SELECT id FROM users WHERE email = ?");
            $stmt->execute([$email]);
            if($stmt->fetch())
            {
                $error = "An account with that email already exists.";
            } else {
                $passwordHash = password_hash($password, PASSWORD_DEFAULT);
                $token = generate_random_token();
                $stmt = db()->prepare(
                    "INSERT INTO users (email, password_hash, verification_token, verification_token_expires_at)
                     VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 24 HOUR))"
                );
                $stmt->execute([$email, $passwordHash, $token]);

                $verifyUrl = "https://" . $_SERVER["HTTP_HOST"] . "/verify_email.php?token=" . urlencode($token);
                send_email(
                    $email,
                    "Verify your email",
                    "<p>Click to verify your account:</p><p><a href=\"$verifyUrl\">$verifyUrl</a></p>"
                    . "<p>This link expires in 24 hours.</p>"
                );

                $success = "Account created. Check your email for a verification link.";
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
    <title>Sign up</title>
    <style>
        body { font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px; }
        input { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; }
        button { font-size: 16px; padding: 10px; width: 100%; }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
    </style>
</head>
<body>
    <h2>Sign up</h2>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?>
        <div class="success"><?= htmlspecialchars($success) ?></div>
    <?php else: ?>
        <form method="post">
            <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
            <input type="email" name="email" placeholder="Email" required autofocus>
            <input type="password" name="password" placeholder="Password (min 10 characters)" required minlength="10">
            <button type="submit">Sign up</button>
        </form>
    <?php endif; ?>
    <p><a href="/login.php">Already have an account? Log in</a></p>
</body>
</html>
