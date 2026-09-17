<?php
/**
 * login.php
 * ---------
 */

require_once __DIR__ . "/auth_helper.php";

auth_start_session();

if(current_user_id() !== null)
{
    header("Location: /dashboard.php");
    exit;
}

$error = "";

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $email = trim($_POST["email"] ?? "");
        $password = $_POST["password"] ?? "";

        $stmt = db()->prepare("SELECT id, password_hash, email_verified FROM users WHERE email = ?");
        $stmt->execute([$email]);
        $user = $stmt->fetch();

        if($user === false || !password_verify($password, $user["password_hash"]))
        {
            // Deliberately the SAME error for "no such account" and
            // "wrong password" -- distinguishing them lets an attacker
            // enumerate which emails have accounts at all.
            $error = "Incorrect email or password.";
        } elseif(!$user["email_verified"]) {
            $error = "Please verify your email before logging in.";
        } else {
            // Regenerate the session ID on login -- prevents session
            // fixation (an attacker who set a victim's session ID
            // before login gaining access to the now-authenticated
            // session).
            session_regenerate_id(true);
            $_SESSION["user_id"] = $user["id"];
            header("Location: /dashboard.php");
            exit;
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
    <title>Log in</title>
    <style>
        body { font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px; }
        input { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; }
        button { font-size: 16px; padding: 10px; width: 100%; }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
    </style>
</head>
<body>
    <h2>Log in</h2>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <form method="post">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="email" name="email" placeholder="Email" required autofocus>
        <input type="password" name="password" placeholder="Password" required>
        <button type="submit">Log in</button>
    </form>
    <p><a href="/register.php">Need an account? Sign up</a></p>
</body>
</html>
