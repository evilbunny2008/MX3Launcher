<?php
/**
 * cleanup_expired_credential_shares.php
 * ---------------------------------------
 * Same idea as cleanup_expired_pairings.php, for the credential_shares
 * table - nothing else deletes these once expired (or even once
 * viewed). Run this periodically via cron, e.g. hourly:
 *   0 * * * * php /path/to/cleanup_expired_credential_shares.php
 */

require_once __DIR__ . "/db.php";

$stmt = db()->prepare("DELETE FROM credential_shares WHERE created_at <= NOW() - INTERVAL 10 MINUTE");
$stmt->execute();

echo "Deleted " . $stmt->rowCount() . " expired credential share row(s).\n";
