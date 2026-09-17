<?php
/**
 * manage_credentials.php
 * ------------------------
 * Logged-in users manage their saved_credentials presets here -- named,
 * per-app credential sets (e.g. several TVs' pairing URL/secret, or
 * anything else) that a "pull"-mode credential_start.php request can
 * later be resolved against, at credential_view.php approval time. An
 * account can have as many as needed, for as many different apps as
 * needed. Replaces the old dashboard.php/edit_pairing_url.php, which
 * only ever managed a fixed {url, secret} pair.
 *
 * Fields are entered as one "Key: Value" line per field rather than a
 * dynamic add-row form, to avoid needing any client-side JS - for an
 * app that a receiving app parses programmatically (rather than a
 * human just reading credential_view.php's reveal page), the exact key
 * names it looks for need to match what that app expects: MX3 Launcher
 * looks for exactly "URL" and "Secret" (see that project's own
 * SoundbarPairing.kt).
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

const MAX_FIELDS = 20;
const MAX_KEY_LENGTH = 64;
const MAX_VALUE_LENGTH = 512;

/** Parses "Key: Value" lines into an assoc array, or returns an error string. */
function parse_fields_text(string $text)
{
    $fields = [];
    foreach(preg_split("/\r\n|\r|\n/", $text) as $line)
    {
        $line = trim($line);
        if($line === "")
            continue;

        $separatorIndex = strpos($line, ":");
        if($separatorIndex === false)
            return "Each field needs a colon, like \"URL: https://...\" - couldn't read: \"$line\"";

        $key = trim(substr($line, 0, $separatorIndex));
        $value = trim(substr($line, $separatorIndex + 1));
        if($key === "" || strlen($key) > MAX_KEY_LENGTH || strlen($value) > MAX_VALUE_LENGTH)
            return "Each field's key must be 1-" . MAX_KEY_LENGTH . " chars and its value at most " . MAX_VALUE_LENGTH . " chars.";

        $fields[$key] = $value;
    }

    if(count($fields) === 0)
        return "Enter at least one field.";
    if(count($fields) > MAX_FIELDS)
        return "At most " . MAX_FIELDS . " fields.";

    return $fields;
}

function fields_to_text(array $fields): string
{
    $lines = [];
    foreach($fields as $key => $value)
        $lines[] = "$key: $value";
    return implode("\n", $lines);
}

$error = "";
$success = "";
// Prefilled on validation failure (so a mistake doesn't wipe out what
// was being typed) or when editing an existing entry.
$formApp = "";
$formLabel = "";
$formFieldsText = "";
$editingId = 0;

if($_SERVER["REQUEST_METHOD"] === "POST")
{
    if(!verify_csrf_token($_POST["csrf_token"] ?? null))
    {
        $error = "Session expired, please try again.";
    } else {
        $action = $_POST["action"] ?? "";

        if($action === "save")
        {
            $editingId = intval($_POST["id"] ?? 0);
            $formApp = trim($_POST["app"] ?? "");
            $formLabel = trim($_POST["label"] ?? "");
            $formFieldsText = (string)($_POST["fields_text"] ?? "");

            if($formApp === "" || strlen($formApp) > 64)
            {
                $error = "App is required (max 64 chars).";
            } elseif($formLabel === "" || strlen($formLabel) > 128) {
                $error = "Label is required (max 128 chars).";
            } else {
                $parsed = parse_fields_text($formFieldsText);
                if(is_string($parsed))
                {
                    $error = $parsed;
                } else {
                    $fieldsJson = json_encode($parsed);
                    if($editingId > 0)
                    {
                        // user_id in the WHERE clause matters -- without it, a
                        // logged-in user could overwrite someone else's entry
                        // just by changing the id.
                        $stmt = db()->prepare(
                            "UPDATE saved_credentials SET app_name = ?, label = ?, fields_json = ?
                             WHERE id = ? AND user_id = ?"
                        );
                        $stmt->execute([$formApp, $formLabel, $fieldsJson, $editingId, $userId]);
                        $success = "Saved.";
                    } else {
                        $stmt = db()->prepare(
                            "INSERT INTO saved_credentials (user_id, app_name, label, fields_json, created_at)
                             VALUES (?, ?, ?, ?, NOW())"
                        );
                        $stmt->execute([$userId, $formApp, $formLabel, $fieldsJson]);
                        $success = "Added.";
                    }
                    $editingId = 0;
                    $formApp = "";
                    $formLabel = "";
                    $formFieldsText = "";
                }
            }
        } elseif($action === "delete") {
            $id = intval($_POST["id"] ?? 0);
            $stmt = db()->prepare("DELETE FROM saved_credentials WHERE id = ? AND user_id = ?");
            $stmt->execute([$id, $userId]);
            $success = "Deleted.";
        }
    }
}

// ?edit=<id> loads an existing entry into the form - only reachable via
// GET, so no CSRF concern (it doesn't change anything), but still
// scoped to this user's own rows.
if($_SERVER["REQUEST_METHOD"] === "GET" && intval($_GET["edit"] ?? 0) > 0)
{
    $stmt = db()->prepare("SELECT id, app_name, label, fields_json FROM saved_credentials WHERE id = ? AND user_id = ?");
    $stmt->execute([intval($_GET["edit"]), $userId]);
    $row = $stmt->fetch();
    if($row !== false)
    {
        $editingId = (int)$row["id"];
        $formApp = $row["app_name"];
        $formLabel = $row["label"];
        $decoded = json_decode($row["fields_json"], true);
        $formFieldsText = is_array($decoded) ? fields_to_text($decoded) : "";
    }
}

$stmt = db()->prepare("SELECT id, app_name, label, created_at FROM saved_credentials WHERE user_id = ? ORDER BY app_name, label");
$stmt->execute([$userId]);
$savedCredentials = $stmt->fetchAll();

$csrfToken = generate_csrf_token();
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>Your saved credentials</title>
    <style>
        body { font-family: sans-serif; max-width: 600px; margin: 40px auto; padding: 0 16px; }
        input, textarea { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; font-family: inherit; }
        textarea { font-family: monospace; height: 100px; }
        button, .btn {
            font-family: inherit; font-size: 16px; line-height: 1.2; padding: 10px;
            display: inline-block; text-align: center; text-decoration: none; color: inherit;
            background: #eee; border: none; border-radius: 4px; cursor: pointer;
            box-sizing: border-box; appearance: none; -webkit-appearance: none;
            margin: 0 8px 0 0; min-width: 90px; vertical-align: middle;
        }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .hint { color: #666; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        td, th { text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }
    </style>
</head>
<body>
    <h2>Your saved credentials</h2>
    <p><a class="btn" href="/credential_view.php">Enter a pairing code</a> <a class="btn" href="/logout.php">Log out</a></p>
    <?php if($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
    <?php if($success): ?><div class="success"><?= htmlspecialchars($success) ?></div><?php endif; ?>

    <table>
        <tr><th>App</th><th>Label</th><th></th></tr>
        <?php foreach($savedCredentials as $row): ?>
        <tr>
            <td><?= htmlspecialchars($row["app_name"]) ?></td>
            <td><?= htmlspecialchars($row["label"]) ?></td>
            <td>
                <a class="btn" href="/manage_credentials.php?edit=<?= (int)$row["id"] ?>">Edit</a>
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

    <h3><?= $editingId > 0 ? "Edit entry" : "Add a new one" ?></h3>
    <form method="post">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="id" value="<?= $editingId ?>">
        <input type="text" name="app" placeholder="App (e.g. MX3Launcher)" list="known-apps"
               value="<?= htmlspecialchars($formApp) ?>" required>
        <datalist id="known-apps"><option value="MX3Launcher"></datalist>
        <input type="text" name="label" placeholder="Label (e.g. Living room TV)"
               value="<?= htmlspecialchars($formLabel) ?>" required>
        <textarea name="fields_text" placeholder="One per line, Key: Value&#10;URL: https://your-lan-endpoint.example.com/script.php&#10;Secret: your shared secret"
                  required><?= htmlspecialchars($formFieldsText) ?></textarea>
        <p class="hint">
            For MX3Launcher, name the fields exactly <code>URL</code> and <code>Secret</code> -
            that app looks those keys up by name. Any other app can use whatever field names make sense.
        </p>
        <button type="submit"><?= $editingId > 0 ? "Save" : "Add" ?></button>
        <?php if($editingId > 0): ?><a class="btn" href="/manage_credentials.php">Cancel</a><?php endif; ?>
    </form>
</body>
</html>
