<?php
/**
 * credential_status.php
 * ----------------------
 * The calling app (whichever side started the share via
 * credential_start.php) polls this using its private token - no auth
 * (the token itself is the credential). Reports "pending" until
 * viewed_at is set (a "push" share gets it the moment credential_view.php
 * does its one-time reveal; a "pull" share gets it the moment an
 * approver attaches a chosen saved_credentials preset there) - at that
 * point, both modes look the same: the row is fully resolved, with the
 * fields to hand back either way. Once collected here, the row is
 * deleted (one-time collection, same as the old pair_poll.php once
 * approved) - so a leaked/replayed token can't read it twice, and a
 * "push" sender that has no real use for its own fields back can just
 * ignore that part of the response and look at "status" alone.
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

$token = $_GET["token"] ?? "";
if($token === "")
{
    http_response_code(400);
    echo json_encode(["ok" => false, "error" => "Missing token"]);
    exit;
}

$stmt = db()->prepare(
    "SELECT viewed_at, payload_json FROM credential_shares
     WHERE token = ? AND created_at > NOW() - INTERVAL 10 MINUTE"
);
$stmt->execute([$token]);
$row = $stmt->fetch();

if($row === false)
{
    echo json_encode(["ok" => true, "status" => "expired"]);
    exit;
}

if($row["viewed_at"] === null)
{
    echo json_encode(["ok" => true, "status" => "pending"]);
    exit;
}

$decoded = json_decode($row["payload_json"] ?? "", true);

$stmt = db()->prepare("DELETE FROM credential_shares WHERE token = ?");
$stmt->execute([$token]);

echo json_encode([
    "ok" => true,
    "status" => "viewed",
    "fields" => is_array($decoded) ? ($decoded["fields"] ?? new stdClass()) : new stdClass(),
]);
