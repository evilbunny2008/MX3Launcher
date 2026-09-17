<?php
/**
 * db.php
 * ------
 * PDO/MariaDB connection helper. TODO: fill in your real credentials.
 */

define("DB_HOST", "localhost");
define("DB_NAME", "database");
define("DB_USER", "username");
define("DB_PASS", "password");

function db(): PDO
{
    static $pdo = null;
    if($pdo === null)
    {
        $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4";
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            // Real prepared statements rather than emulated ones --
            // properly parameterized regardless of driver quirks, which
            // matters here since every query in this system takes user
            // input (email, password, labels, URLs, secrets, codes).
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    }
    return $pdo;
}
