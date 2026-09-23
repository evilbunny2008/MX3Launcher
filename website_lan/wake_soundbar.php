<?php
/**
 * wake_soundbar.php
 * ------------------
 * HTTP endpoint for MX3 Launcher to call on app launch. Wakes the
 * soundbar from standby via an IR toggle command sent through the
 * Zigbee IR blaster -- but ONLY if it's actually in standby.
 *
 * This check matters: the IR code is a TOGGLE (it turns the soundbar
 * both on and off), not a dedicated "power on" command. Firing it
 * unconditionally on every app launch would turn the soundbar OFF
 * whenever it's already on -- exactly the opposite of what this is
 * for. The smart socket's power-monitoring (current draw) is used as
 * a proxy for the soundbar's real state: ~0.05A when on, 0A in standby.
 */

require_once("/var/www/mqtt-creds.php");
require_once("/usr/src/MQTTv5Client/MQTThelper.php");

$debug = false;

header('Content-Type: application/json');

$socket_topic = $base_topic . "/Socket_06";

$ir_topic = $base_topic . "/IRBlaster_01/set";

// IR codes for TCL TVs
$ir_payload = array(
	"TCL_0" => "B68Prw/9AbwH4AUDAeIDgAPAF8AP4A8H4AcnwA8HvAf9AbwH/QE=",
	"TCL_1" => "B7EPsQ/4AcQH4AUDAeIDgAPAF8APQAdAE0AH4AMDQBNAA8ATQAsLxAf4AcQH+AHiA/gB",
	"TCL_2" => "B68Prw/4AcIH4AUDAekDgAPAF+ADD8AL4AMH4AsnC8IH+AHpA/gBwgf4AQ==",
	"TCL_3" => "B6wPrA/rAckH4AUDAfYDgAPAF+APD+AJFwEbAkAD4Ac3B44grA+sD+sBwBfABwH2A4AD4BcP4BwnAgPrAQ==",
	"TCL_4" => "B7APsA/1AbkH4AUDAfYDgAPAF0APQAtAB+ALA0AbQAPAG8ALB7kH9QG5B/UB",
	"TCL_5" => "B6YPpg/3AcAH4AUDAesDgAPAF0APQAvgAwdAC8ADQBtAA+ADDwvrA/cBwAf3AesD9wE=",
	"TCL_6" => "B7IPsg/VAdsH4AUDARMEgAPAF0AP4AMLQA/gAwPgBxtADwsTBNUBEwTVAdsH1QE=",
	"TCL_7" => "B6APoA/nAckH4AUDAQQEgAPAFwMEBOcBwAtABwEEBOAFA0ATQAMBBASAA+ADCwMEBOcB",
	"TCL_8" => "B6EPoQ/rAcQH4AUDAQEEgAPgAxcAAaATAAHgCgfAJwAB4AIbC8QH6wHEB+sBxAfrAQ==",
	"TCL_9" => "B7MPsw/ZAdsH4AUDAQkEgAPgAxfgAxPAC8AHwCfAD0AHC9sH2QHbB9kBCQTZAQ==",
	"TCL_ENTER" => "B6MPow/zAc0H4BUDA+cD8wFAI0AH4BsDwCsHzQfzAc0H8wE=",
);

// Philips 6000 soundbar
$ir_payload["P6000_ON"] = "BV0KgQOyAUADQAEHRQFEBUQFsgGAARSBA4EDsgGyAYEDRQH6AYEDsgH6AbKgAYANQAVAAQH//+AFRweyAUQFRAWyAYABB4EDgQOyAbIB4AMFAvoBsmAB4AITAgGyAQ==";

if(!isset($_GET['key']) || $_GET['key'] !== $wake_soundbar_key)
{
    http_response_code(403);
    echo json_encode(["ok" => false, "error" => "Missing or incorrect key"]);
    exit;
}
// ----------------------------------------------------------------------

$_GET['check_current'] = true;

if(isset($_GET['check_current']) && $_GET['check_current'] === true)
{
	$state = mqttget(uniqid(), $socket_topic, $debug);

	if($state === false)
	{
	    http_response_code(500);
	    echo json_encode(["ok" => false, "error" => "Could not read socket state from $socket_topic"]);
	    exit;
	}

	if(!isset($state["current"]))
	{
	    http_response_code(500);
	    echo json_encode(["ok" => false, "error" => "Socket state has no 'current' field", "raw" => $state]);
	    exit;
	}

	$current = floatval($state["current"]);

	if($current > 0)
	{
		// Already on -- do NOT send the toggle, it would turn it off.
		echo json_encode(["ok" => true, "action" => "already_on", "current_was" => $current]);
		exit;
	}
}

$_GET["ir_codes_to_send"] = "P6000_ON";
$codes = explode("|", $_GET["ir_codes_to_send"]);

if(!is_array($codes))
	$codes = array($codes);

foreach($codes as $code)
{
	if(!isset($ir_payload[$code]))
	{
		echo $code . " is invalid...<br>\n";
		continue;
	}

	mqttpublish($ir_topic, array("ir_code_to_send" => $ir_payload[$code]), true, $debug);
}

echo json_encode(["ok" => true, "action" => $_GET["ir_codes_to_send"]]);
