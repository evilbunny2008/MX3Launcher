<?php
/**
 * index.php
 * ---------
 * Landing page. Sends logged-in users straight to their dashboard;
 * everyone else sees a description of what this site is for.
 */

require_once __DIR__ . "/auth_helper.php";

auth_start_session();

if(current_user_id() !== null)
{
    header("Location: /manage_credentials.php");
    exit;
}
?>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <title>MX3 Launcher pairing</title>
    <style>
        body { font-family: sans-serif; max-width: 560px; margin: 40px auto; padding: 0 16px; line-height: 1.5; }
        h1 { font-size: 28px; }
        .buttons { margin-top: 24px; }
        .buttons a {
            display: inline-block; padding: 12px 20px; margin-right: 12px;
            border-radius: 6px; text-decoration: none; font-size: 16px;
        }
        .primary { background: #2B2F77; color: #fff; }
        .secondary { background: #eee; color: #222; }
        ol { padding-left: 20px; }
        li { margin-bottom: 8px; }
    </style>
</head>
<body>
    <h1>MX3 Launcher pairing</h1>
    <p>
        MX3 Launcher is a minimal Home-screen replacement for Android TV /
        Google TV. One of its features can trigger something on your own
        home network when you launch an app — commonly used to wake a
        soundbar that's gone into standby, but really it can call
        whatever URL you point it at.
    </p>
    <p>
        Since that URL and its shared secret shouldn't be typed on a TV
        remote, this site exists to hold them for you instead. Sign up,
        register the URL(s) you want your TV to be able to trigger, and
        pair your TV to your account by scanning a QR code it shows on
        screen — no typing required on the TV itself.
    </p>

    <h3>How it works</h3>
    <ol>
        <li>Sign up and verify your email.</li>
        <li>Add one or more pairing URLs on your dashboard — whatever
            endpoint on your own home network you want triggered, and a
            shared secret to authenticate it.</li>
        <li>On your TV, open MX3 Launcher's settings and start pairing.
            It'll show a QR code and a short code.</li>
        <li>Scan the QR code with your phone (or type the code), choose
            which of your pairing URLs this TV should use, and approve.</li>
        <li>Your TV picks this up automatically within a few seconds —
            nothing else to do.</li>
    </ol>

    <div class="buttons">
        <a class="primary" href="/register.php">Sign up</a>
        <a class="secondary" href="/login.php">Log in</a>
    </div>
</body>
</html>
