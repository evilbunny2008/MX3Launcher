<?php
/**
 * pair_poll.php
 * -------------
 * TV polls this repeatedly using its private token. Response shape is
 * UNCHANGED from the earlier flat-file version (still {ok, status,
 * url, secret} with the same status values) -- the Android app needs
 * no changes for this rewrite.
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

$token = $_GET["token"] ?? "";

if($token === "")
{
    http_response_code(400);
    echo json_encode(["ok" => false, "status" => "error", "error" => "Missing token"]);
    exit;
}

$stmt = db()->prepare(
    "SELECT dp.approved, pu.url, pu.secret
     FROM device_pairings dp
     LEFT JOIN pairing_urls pu ON pu.id = dp.pairing_url_id
     WHERE dp.token = ? AND dp.created_at > NOW() - INTERVAL 10 MINUTE"
);
$stmt->execute([$token]);
$row = $stmt->fetch();

if($row === false)
{
    echo json_encode(["ok" => true, "status" => "expired"]);
    exit;
}

if(!$row["approved"])
{
    echo json_encode(["ok" => true, "status" => "pending"]);
    exit;
}

// Approved -- hand over the real values and delete the pairing row so
// it can't be polled again (one-time use).
$stmt = db()->prepare("DELETE FROM device_pairings WHERE token = ?");
$stmt->execute([$token]);

echo json_encode([
    "ok" => true,
    "status" => "approved",
    "url" => $row["url"],
    "secret" => $row["secret"],
]);
