<?php
/**
 * dashboard.php
 * -------------
 * Logged-in users manage their registered pairing URL/secret entries
 * here -- an account can have more than one (different devices/rooms).
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
    } else {
        $action = $_POST["action"] ?? "";

        if($action === "add")
        {
            $label = trim($_POST["label"] ?? "");
            $url = trim($_POST["url"] ?? "");
            $secret = trim($_POST["secret"] ?? "");

            if($label === "" || $url === "" || $secret === "")
            {
                $error = "All fields are required.";
            } elseif(!filter_var($url, FILTER_VALIDATE_URL) || !str_starts_with($url, "https://")) {
                $error = "URL must be a valid https:// address.";
            } else {
                $stmt = db()->prepare("INSERT INTO pairing_urls (user_id, label, url, secret) VALUES (?, ?, ?, ?)");
                $stmt->execute([$userId, $label, $url, $secret]);
                $success = "Added.";
            }
        } elseif($action === "delete") {
            $id = intval($_POST["id"] ?? 0);
            // user_id in the WHERE clause matters -- without it, any
            // logged-in user could delete anyone else's entry just by
            // guessing/iterating IDs.
            $stmt = db()->prepare("DELETE FROM pairing_urls WHERE id = ? AND user_id = ?");
            $stmt->execute([$id, $userId]);
            $success = "Deleted.";
        }
    }
}

$stmt = db()->prepare("SELECT id, label, url, secret, created_at FROM pairing_urls WHERE user_id = ? ORDER BY created_at DESC");
$stmt->execute([$userId]);
$pairingUrls = $stmt->fetchAll();

$csrfToken = generate_csrf_token();
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Dashboard</title>
    <style>
        body { font-family: sans-serif; max-width: 600px; margin: 40px auto; padding: 0 16px; }
        input { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; }
        button, .btn {
            font-family: inherit; font-size: 16px; line-height: 1.2; padding: 10px;
            display: inline-block; text-align: center; text-decoration: none; color: inherit;
            background: #eee; border: none; border-radius: 4px; cursor: pointer;
            box-sizing: border-box; appearance: none; -webkit-appearance: none;
            margin: 0 8px 0 0; min-width: 90px; vertical-align: middle;
        }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        td, th { text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }
    </style>
</head>
<body>
    <h2>Your pairing URLs</h2>
    <p><a class="btn" href="/pair_approve.php">Approve a device</a> <a class="btn" href="/logout.php">Log out</a></p>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?><div class="success"><?= htmlspecialchars($success) ?></div><?php endif; ?>

    <table>
        <tr><th>Label</th><th>URL</th><th></th></tr>
        <?php foreach($pairingUrls as $row): ?>
        <tr>
            <td><?= htmlspecialchars($row["label"]) ?></td>
            <td><?= htmlspecialchars($row["url"]) ?></td>
            <td>
                <a class="btn" href="/edit_pairing_url.php?id=<?= (int)$row["id"] ?>">Edit</a>
                <form method="post" style="display:inline;">
                    <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="id" value="<?= (int)$row["id"] ?>">
                    <button type="submit" onclick="return confirm('Delete this?');">Delete</button>
                </form>
            </td>
        </tr>
        <?php endforeach; ?>
    </table>

    <h3>Add a new one</h3>
    <form method="post">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="hidden" name="action" value="add">
        <input type="text" name="label" placeholder="Label (e.g. Living room soundbar)" required>
        <input type="url" name="url" placeholder="https://your-lan-endpoint.example.com/script.php" required>
        <input type="text" name="secret" placeholder="Shared secret" required>
        <button type="submit">Add</button>
    </form>
</body>
</html>
