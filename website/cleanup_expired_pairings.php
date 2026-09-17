<?php
/**
 * cleanup_expired_pairings.php
 * -----------------------------
 * Expired device_pairings rows are already ignored by every query's
 * WHERE clause, but nothing actually DELETES them -- without this,
 * they'd linger in the table indefinitely. Run this periodically via
 * cron, e.g. hourly:
 *   0 * * * * php /path/to/cleanup_expired_pairings.php
 */

require_once __DIR__ . "/db.php";

$stmt = db()->prepare("DELETE FROM device_pairings WHERE created_at <= NOW() - INTERVAL 10 MINUTE");
$stmt->execute();

echo "Deleted " . $stmt->rowCount() . " expired pairing row(s).\n";
