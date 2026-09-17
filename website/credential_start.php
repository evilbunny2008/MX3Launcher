<?php
/**
 * credential_start.php
 * ---------------------
 * An app calls this (no account, no login - same as the old
 * pair_start.php) to start a credential exchange with another
 * device/app, in one of two modes depending on whether it includes
 * "fields" in its request body:
 *
 * - "push" ("fields" present): the app already has the data (e.g. Z2M
 *   Dash sharing one of its own configured broker's hostname/username/
 *   password) and hands it over up front. credential_view.php does a
 *   one-time reveal of it for a human to read/copy elsewhere.
 * - "pull" ("fields" omitted, "app" still required): the app has
 *   nothing of its own to send - it's asking to RECEIVE credentials
 *   (e.g. an MX3 Launcher TV pairing for the first time). The logged-in
 *   approver picks one of their own saved_credentials presets for that
 *   app at credential_view.php, and this device's own poll of
 *   credential_status.php receives the resolved fields automatically.
 *
 * Either way, this returns a short code to show on screen (as text
 * and/or a QR linking to credential_view.php), and a private poll
 * token the calling app keeps to itself to watch for resolution via
 * credential_status.php.
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

function bad_request(string $error): void
{
    http_response_code(400);
    echo json_encode(["ok" => false, "error" => $error]);
    exit;
}

if($_SERVER["REQUEST_METHOD"] !== "POST")
    bad_request("POST required");

// php.ini's post_max_size already caps what PHP will even parse into
// php://input - this is just a cheap extra sanity check before
// json_decode does real work on it.
$raw = file_get_contents("php://input", false, null, 0, 65536);
$body = json_decode($raw !== false ? $raw : "", true);
if(!is_array($body))
    bad_request("Invalid JSON body");

$app = trim((string)($body["app"] ?? ""));
$label = trim((string)($body["label"] ?? ""));
$fieldsProvided = array_key_exists("fields", $body);
$fields = $body["fields"] ?? null;

if($app === "" || strlen($app) > 64)
    bad_request("\"app\" is required (max 64 chars)");
if(strlen($label) > 128)
    bad_request("\"label\" too long (max 128 chars)");

$payloadJson = null;
$requestedApp = null;

if($fieldsProvided)
{
    // Push mode.
    if(!is_array($fields) || count($fields) === 0 || count($fields) > 20)
        bad_request("\"fields\" must be a non-empty object of at most 20 entries");

    $cleanFields = [];
    foreach($fields as $key => $value)
    {
        $key = trim((string)$key);
        $value = (string)$value;
        if($key === "" || strlen($key) > 64 || strlen($value) > 512)
            bad_request("Each field key must be 1-64 chars and its value at most 512 chars");
        $cleanFields[$key] = $value;
    }
    $payloadJson = json_encode(["app" => $app, "label" => $label, "fields" => $cleanFields]);
} else {
    // Pull mode - nothing to attach yet, an approver picks a preset later.
    $requestedApp = $app;
}

// Same alphabet as the old pair_start.php - no 0/O/1/I/L, easy to read
// off one screen and type (or double-check) on another.
$alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
$code = "";
for($i = 0; $i < 6; $i++)
    $code .= $alphabet[random_int(0, strlen($alphabet) - 1)];

$token = generate_random_token();

$stmt = db()->prepare(
    "INSERT INTO credential_shares (token, code, requested_app, payload_json, created_at)
     VALUES (?, ?, ?, ?, NOW())"
);
$stmt->execute([$token, $code, $requestedApp, $payloadJson]);

echo json_encode([
    "ok" => true,
    "code" => $code,
    "token" => $token,
    "expires_in" => 600,
]);
