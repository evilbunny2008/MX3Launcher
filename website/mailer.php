<?php
/**
 * mailer.php
 * ----------
 * Sends mail via PHP's built-in mail() function, which hands off to
 * this server's local MTA -- no SMTP host/port/username/password
 * needed, since nothing here connects to a remote SMTP server
 * directly. TODO: set the from-address/name below.
 */

define("MAIL_FROM_EMAIL", "mx3launcher@odiousapps.com");
define("MAIL_FROM_NAME", "MX3 Launcher");

/**
 * Relies on $to already having been validated (e.g. via
 * filter_var($email, FILTER_VALIDATE_EMAIL) at the call site, as
 * register.php already does) -- a validated email address can't
 * structurally contain the newline characters mail header injection
 * depends on. $subject is expected to be a fixed string the caller
 * wrote themselves, not raw user input.
 */
function send_email(string $to, string $subject, string $bodyHtml): bool
{
    $headers = "MIME-Version: 1.0\r\n";
    $headers .= "Content-Type: text/html; charset=UTF-8\r\n";
    $headers .= "From: " . MAIL_FROM_NAME . " <" . MAIL_FROM_EMAIL . ">\r\n";

    $sent = mail($to, $subject, $bodyHtml, $headers);
    if(!$sent)
        error_log("mail() failed sending to $to");

    return $sent;
}
