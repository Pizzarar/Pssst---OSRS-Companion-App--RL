package com.pssst.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("pssstcompanion")
public interface CompanionConfig extends Config
{
	String IP_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers.";

	@ConfigSection(
		name = "Master Sync",
		description = "Master opt-in switch and authentication token",
		position = 0
	)
	String syncSection = "syncSection";

	@ConfigSection(
		name = "Local Server (LAN)",
		description = "Embedded WebSocket server for direct Wi-Fi and Meshnet sync",
		position = 10
	)
	String localWsSection = "localWsSection";

	@ConfigSection(
		name = "BYOS Server (Remote)",
		description = "Push state to a self-hosted mock server, Nord Meshnet IP, or Tailscale node",
		position = 20
	)
	String byosSection = "byosSection";

	// --- Master Sync ---

	@ConfigItem(
		keyName = "enableSync",
		name = "Enable Sync",
		description = "Master switch to enable companion synchronization. Must be enabled for local or remote sync.",
		warning = IP_WARNING,
		position = 1,
		section = syncSection
	)
	default boolean enableSync()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncToken",
		name = "Sync Token",
		description = "Shared authentication token for pairing with your Pssst Android app",
		position = 2,
		section = syncSection
	)
	default String syncToken()
	{
		return "";
	}

	// --- Local Server (LAN) ---

	@ConfigItem(
		keyName = "enableLocalServer",
		name = "Enable Server",
		description = "Runs an embedded zero-cloud WebSocket server on this PC for direct Wi-Fi and Meshnet sync",
		position = 11,
		section = localWsSection
	)
	default boolean enableLocalServer()
	{
		return true;
	}

	@Range(min = 1024, max = 65535)
	@ConfigItem(
		keyName = "localServerPort",
		name = "Port",
		description = "TCP port used by the embedded WebSocket server",
		position = 12,
		section = localWsSection
	)
	default int localServerPort()
	{
		return 8788;
	}

	@ConfigItem(
		keyName = "bindLoopbackOnly",
		name = "Loopback Only",
		description = "Bind strictly to 127.0.0.1. Uncheck for LAN, Wi-Fi, or Meshnet connections (exposes the server to your network).",
		position = 13,
		section = localWsSection
	)
	default boolean bindLoopbackOnly()
	{
		return true; // safe default: LAN exposure must be an explicit opt-in
	}

	// --- BYOS Server (Remote) ---

	@ConfigItem(
		keyName = "enableByosPush",
		name = "Enable Push",
		description = "Periodically push state to a custom remote HTTP server or mock server endpoint",
		position = 21,
		section = byosSection
	)
	default boolean enableByosPush()
	{
		return true;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "Target server address (e.g., http://127.0.0.1:8787, 100.64.0.1:8787, or https://my-server.com)",
		position = 22,
		section = byosSection
	)
	default String serverUrl()
	{
		return "http://127.0.0.1:8787";
	}
}
