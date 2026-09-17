<?php
/**
 * pair_start.php
 * --------------
 * TV calls this first, no auth (nothing to authenticate the TV with
 * yet -- this call itself starts a pairing session). Returns a short
 * code to display on screen, and a private poll token the TV keeps to
 * itself.
 *
 * Response shape is unchanged from the earlier flat-file version, so
 * the Android app needs no changes for this rewrite.
 */

require_once __DIR__ . "/auth_helper.php";

header('Content-Type: application/json');

$alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L -- easy to read off a TV, easy to type on a phone
$code = "";
for($i = 0; $i < 6; $i++)
    $code .= $alphabet[random_int(0, strlen($alphabet) - 1)];

$token = generate_random_token();

$stmt = db()->prepare("INSERT INTO device_pairings (token, code, approved, created_at) VALUES (?, ?, 0, NOW())");
$stmt->execute([$token, $code]);

echo json_encode([
    "ok" => true,
    "code" => $code,
    "token" => $token,
    "expires_in" => 600,
]);
