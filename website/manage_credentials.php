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
 * Fields are entered as dynamic key/value row pairs (add a row, remove
 * a row, or load every field a known app might use via the template
 * picker) - the exact key names an app looks up programmatically
 * (rather than a human just reading credential_view.php's reveal page)
 * need to match what that app expects exactly, so $KNOWN_APP_FIELDS
 * below is the single source of truth this page renders both the
 * reference list and the template picker's data from, to keep the two
 * from drifting apart from each other (though it can still drift from
 * what each app's own code actually reads, being a separate repo -
 * MX3 Launcher's SoundbarPairing.kt and Z2M Dash's
 * AddEditBrokerScreen.kt/applyImportedFields are the real ground truth).
 *
 * Excludes MX3 Launcher's settings-backup presets (app_name
 * BACKUP_APP_NAME, from LauncherConfigSync.kt) - those aren't hand-managed
 * credentials, they accumulate one new entry per backup, and would swamp
 * this list over time. See settings_backups.php for those instead.
 */

require_once __DIR__ . "/auth_helper.php";

$userId = require_login();

const BACKUP_APP_NAME = "MX3Launcher Settings";

const MAX_FIELDS = 20;
const MAX_KEY_LENGTH = 64;
// Kept in sync with credential_start.php's own per-field limit - see the
// comment there for why 32768 (not just "a few KB" of broker settings).
const MAX_VALUE_LENGTH = 32768;

// Every field a known app looks up by name, used to render both the
// human-readable reference below the form and the template picker's
// pre-fill data (as JSON, further down). "required" only affects the
// reference text - every field is still optional to actually submit,
// since credential_start.php's own field-presence check is what really
// enforces requiredness for e.g. Z2M Dash's "Hostname".
$KNOWN_APP_FIELDS = [
    "MX3Launcher" => [
        ["key" => "URL", "required" => true],
        ["key" => "Secret", "required" => true],
    ],
    "Z2M Dash" => [
        ["key" => "Hostname", "required" => true],
        ["key" => "Protocol", "required" => false, "hint" => "one of MQTT, MQTTS, WS, WSS"],
        ["key" => "Port", "required" => false, "hint" => "a number, default 1883"],
        ["key" => "Username", "required" => false, "hint" => "default blank, no auth"],
        ["key" => "Password", "required" => false, "hint" => "default blank, no auth"],
        ["key" => "Name", "required" => false, "hint" => "the broker's display name in the app"],
        ["key" => "BaseTopic", "required" => false, "hint" => "default zigbee2mqtt"],
        ["key" => "WebSocketPath", "required" => false, "hint" => "only relevant for WS/WSS, default /mqtt"],
        ["key" => "ClientId", "required" => false, "hint" => "default a randomly generated one"],
        ["key" => "SelfSignedCert", "required" => false, "hint" => "true/false, default false"],
        ["key" => "SelfSignedCertBase64", "required" => false, "hint" => "the certificate's raw bytes, base64-encoded"],
        ["key" => "CleanSession", "required" => false, "hint" => "true/false, default false"],
        ["key" => "KeepAliveSeconds", "required" => false, "hint" => "a number, default 60"],
        ["key" => "ConnectionTimeoutSeconds", "required" => false, "hint" => "a number, default 30"],
        ["key" => "AutoConnect", "required" => false, "hint" => "true/false, default true"],
        ["key" => "ShowReconnectionStatus", "required" => false, "hint" => "true/false, default true"],
        [
            "key" => "AutoAccept", "required" => false,
            "hint" => "true/false, default false - auto-adds newly-seen devices instead of prompting"
        ],
    ],
];

/** Builds an assoc array from parallel field_keys[]/field_values[] arrays, or returns an error string. */
function parse_fields_arrays(array $keys, array $values)
{
    $fields = [];
    $count = min(count($keys), count($values));
    for($i = 0; $i < $count; $i++)
    {
        // A row with no key is just skipped rather than treated as an
        // error - lets a template's unused optional rows be left alone
        // rather than needing to individually delete each one.
        $key = trim((string)$keys[$i]);
        if($key === "")
            continue;

        $value = trim((string)$values[$i]);
        if(strlen($key) > MAX_KEY_LENGTH || strlen($value) > MAX_VALUE_LENGTH)
            return "\"$key\": key must be 1-" . MAX_KEY_LENGTH . " chars, value at most " . MAX_VALUE_LENGTH . " chars.";

        $fields[$key] = $value;
    }

    if(count($fields) === 0)
        return "Enter at least one field.";
    if(count($fields) > MAX_FIELDS)
        return "At most " . MAX_FIELDS . " fields.";

    return $fields;
}

$error = "";
$success = "";
// Prefilled on validation failure (so a mistake doesn't wipe out what
// was being typed) or when editing an existing entry. $formFields is a
// list of [key, value] pairs (not an assoc array) so a row with a
// blank or duplicate key submitted by mistake still redisplays exactly
// as typed, rather than silently collapsing.
$formApp = "";
$formLabel = "";
$formFields = [];
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
            $postedKeys = is_array($_POST["field_keys"] ?? null) ? $_POST["field_keys"] : [];
            $postedValues = is_array($_POST["field_values"] ?? null) ? $_POST["field_values"] : [];
            foreach($postedKeys as $i => $k)
                $formFields[] = [(string)$k, (string)($postedValues[$i] ?? "")];

            if($formApp === "" || strlen($formApp) > 64)
            {
                $error = "App is required (max 64 chars).";
            } elseif($formLabel === "" || strlen($formLabel) > 128) {
                $error = "Label is required (max 128 chars).";
            } else {
                $parsed = parse_fields_arrays($postedKeys, $postedValues);
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
                    $formFields = [];
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
        $formFields = is_array($decoded) ? array_map(null, array_keys($decoded), array_values($decoded)) : [];
    }
}

$stmt = db()->prepare(
    "SELECT id, app_name, label, created_at FROM saved_credentials
     WHERE user_id = ? AND app_name != ? ORDER BY app_name, label"
);
$stmt->execute([$userId, BACKUP_APP_NAME]);
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
        /* 700, not 600: the 4 nav buttons up top (Enter a pairing code,
           MX3 Launcher settings backups, Change email, Log out) need at
           least one more button-width of room than 600px gives them
           before they wrap awkwardly. */
        body { font-family: sans-serif; max-width: 700px; margin: 40px auto; padding: 0 16px; }
        input, select { font-size: 16px; width: 100%; padding: 10px; margin-bottom: 12px; box-sizing: border-box; font-family: inherit; }
        button, .btn {
            font-family: inherit; font-size: 16px; line-height: 1.2; padding: 10px;
            display: inline-block; text-align: center; text-decoration: none; color: inherit;
            background: #eee; border: none; border-radius: 4px; cursor: pointer;
            box-sizing: border-box; appearance: none; -webkit-appearance: none;
            margin: 0 8px 0 0; min-width: 90px; vertical-align: middle;
        }
        .nav-buttons { text-align: center; }
        .nav-buttons .btn:last-child { margin-right: 0; }
        .error { background: #fdd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .success { background: #dfd; padding: 12px; border-radius: 4px; margin-bottom: 12px; }
        .hint { color: #666; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        td, th { text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }
        details { margin-bottom: 12px; }
        details ul { margin: 8px 0 0; padding-left: 20px; }
        details li { margin-bottom: 4px; }
        #fields-container { margin-bottom: 12px; }
        .field-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
        .field-row input { margin-bottom: 0; }
        .field-row input[name="field_keys[]"] { flex: 0 0 40%; }
        .field-row input[name="field_values[]"] { flex: 1; min-width: 0; }
        .remove-row {
            flex-shrink: 0; width: 40px; height: 40px; min-width: 0; margin: 0; padding: 0;
            background: #fdd; color: #c00; font-size: 20px; line-height: 1;
        }
        #add-field-btn { margin-bottom: 12px; }
    </style>
</head>
<body>
    <h2>Your saved credentials</h2>
    <p class="nav-buttons">
        <a class="btn" href="/credential_view.php">Enter a pairing code</a>
        <a class="btn" href="/settings_backups.php">MX3 Launcher settings backups</a>
        <a class="btn" href="/change_email.php">Change email</a>
        <a class="btn" href="/logout.php">Log out</a>
    </p>
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
    <form method="post" id="credential-form">
        <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($csrfToken) ?>">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="id" value="<?= $editingId ?>">

        <select id="template-select">
            <option value="">Load a template for...</option>
            <?php foreach(array_keys($KNOWN_APP_FIELDS) as $appName): ?>
                <option value="<?= htmlspecialchars($appName) ?>"><?= htmlspecialchars($appName) ?></option>
            <?php endforeach; ?>
        </select>

        <input type="text" name="app" id="app-input" placeholder="App (e.g. MX3Launcher)" list="known-apps"
               value="<?= htmlspecialchars($formApp) ?>" required>
        <datalist id="known-apps">
            <?php foreach(array_keys($KNOWN_APP_FIELDS) as $appName): ?>
                <option value="<?= htmlspecialchars($appName) ?>">
            <?php endforeach; ?>
        </datalist>
        <input type="text" name="label" placeholder="Label (e.g. Living room TV)"
               value="<?= htmlspecialchars($formLabel) ?>" required>

        <div id="fields-container"></div>
        <button type="button" id="add-field-btn">+ Add field</button>

        <p class="hint">
            Apps that consume these fields automatically (rather than a human just reading them off
            credential_view.php) look up specific key names, so they need to match exactly - use the
            template picker above, or see below for the apps that currently do this. Any other app can
            use whatever field names make sense.
        </p>
        <?php foreach($KNOWN_APP_FIELDS as $appName => $appFields): ?>
            <details>
                <summary><?= htmlspecialchars($appName) ?> field names</summary>
                <ul class="hint">
                    <?php foreach($appFields as $field): ?>
                        <li>
                            <code><?= htmlspecialchars($field["key"]) ?></code>
                            <?= $field["required"] ? "- required" : "" ?>
                            <?= isset($field["hint"]) ? "- " . htmlspecialchars($field["hint"]) : "" ?>
                        </li>
                    <?php endforeach; ?>
                </ul>
            </details>
        <?php endforeach; ?>

        <button type="submit"><?= $editingId > 0 ? "Save" : "Add" ?></button>
        <?php if($editingId > 0): ?><a class="btn" href="/manage_credentials.php">Cancel</a><?php endif; ?>
    </form>

    <script>
        // Pre-fill data for the template picker, and this entry's existing
        // fields (edit mode) or a submitted-but-invalid attempt (so a
        // validation error doesn't wipe out what was being typed) - both
        // rendered server-side above into $KNOWN_APP_FIELDS/$formFields.
        var KNOWN_APP_FIELDS = <?= json_encode($KNOWN_APP_FIELDS, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT) ?>;
        var INITIAL_FIELDS = <?= json_encode($formFields, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT) ?>;

        function addFieldRow(key, value) {
            var row = document.createElement("div");
            row.className = "field-row";

            var keyInput = document.createElement("input");
            keyInput.type = "text";
            keyInput.name = "field_keys[]";
            keyInput.placeholder = "Key";
            keyInput.value = key || "";

            var valueInput = document.createElement("input");
            valueInput.type = "text";
            valueInput.name = "field_values[]";
            valueInput.placeholder = "Value";
            valueInput.value = value || "";

            var removeBtn = document.createElement("button");
            removeBtn.type = "button";
            removeBtn.className = "remove-row";
            removeBtn.setAttribute("aria-label", "Remove field");
            removeBtn.textContent = "×";
            removeBtn.addEventListener("click", function () { row.remove(); });

            row.appendChild(keyInput);
            row.appendChild(valueInput);
            row.appendChild(removeBtn);
            document.getElementById("fields-container").appendChild(row);
        }

        document.getElementById("add-field-btn").addEventListener("click", function () {
            addFieldRow("", "");
        });

        document.getElementById("template-select").addEventListener("change", function () {
            var appName = this.value;
            this.value = ""; // reset so picking the same template again still re-fires "change"
            if (!appName || !KNOWN_APP_FIELDS[appName]) return;

            document.getElementById("app-input").value = appName;
            document.getElementById("fields-container").innerHTML = "";
            KNOWN_APP_FIELDS[appName].forEach(function (field) {
                addFieldRow(field.key, "");
            });
        });

        if (INITIAL_FIELDS.length > 0) {
            INITIAL_FIELDS.forEach(function (pair) { addFieldRow(pair[0], pair[1]); });
        } else {
            addFieldRow("", "");
        }
    </script>
</body>
</html>
