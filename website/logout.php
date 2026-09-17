<?php
/**
 * logout.php
 * ----------
 */

require_once __DIR__ . "/auth_helper.php";

auth_start_session();
$_SESSION = [];
session_destroy();
header("Location: /login.php");
exit;
