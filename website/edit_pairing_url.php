<?php
/**
 * edit_pairing_url.php
 * ---------------------
 * Edit an existing pairing URL entry, rather than needing to delete and
 * re-add it. Linked from dashboard.php.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

$error = "";

$id = intval($_GET["id"] ?? $_POST["id"] ?? 0);

// user_id in the WHERE clause matters here too -- without it, a
// logged-in user could load (and then save changes to) someone else's
// entry just by changing the id in the URL.
$stmt = db()->prepare("SELECT id, label, url, secret FROM pairing_urls WHERE id = ? AND user_id = ?");
$stmt->execute([$id, $userId]);
$row = $stmt->fetch();

if($row === false)
{
    header("Location: /dashboard.php");
    exit;
}

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $label = trim($_POST["label"] ?? "");
        $url = trim($_POST["url"] ?? "");
        $secret = trim($_POST["secret"] ?? "");

        if($label === "" || $url === "" || $secret === "")
        {
            $error = "All fields are required.";
        } elseif(!filter_var($url, FILTER_VALIDATE_URL) || !str_starts_with($url, "https://")) {
            $error = "URL must be a valid https:// address.";
        } else {
            $stmt = db()->prepare(
                "UPDATE pairing_urls SET label = ?, url = ?, secret = ? WHERE id = ? AND user_id = ?"
            );
            $stmt->execute([$label, $url, $secret, $id, $userId]);
            header("Location: /dashboard.php");
            exit;
        }

        // Validation failed -- keep the row's displayed values as what
        // was just submitted, not the stale pre-edit values, so the
        // error doesn't wipe out what the person was in the middle of
        // typing.
        $row["label"] = $label;
        $row["url"] = $url;
        $row["secret"] = $secret;
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
    <title>Edit pairing URL</title>
    <style>
        body { font-family: sans-serif; max-width: 400px; margin: 40px auto; padding: 0 16px; }
        input { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; }
        button, .btn {
            font-family: inherit; font-size: 16px; line-height: 1.2; padding: 10px;
            width: 100%; box-sizing: border-box; display: block; text-align: center;
            text-decoration: none; color: inherit; background: #eee; border: none;
            border-radius: 4px; cursor: pointer; appearance: none; -webkit-appearance: none;
            margin-top: 8px;
        }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
    </style>
</head>
<body>
    <h2>Edit pairing URL</h2>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <form method="post">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="hidden" name="id" value="<?= (int)$row["id"] ?>">
        <input type="text" name="label" placeholder="Label" value="<?= htmlspecialchars($row["label"]) ?>" required>
        <input type="url" name="url" placeholder="https://your-lan-endpoint.example.com/script.php"
               value="<?= htmlspecialchars($row["url"]) ?>" required>
        <input type="text" name="secret" placeholder="Shared secret" value="<?= htmlspecialchars($row["secret"]) ?>" required>
        <button type="submit">Save</button>
    </form>
    <p><a class="btn" href="/dashboard.php">Cancel</a></p>
</body>
</html>
