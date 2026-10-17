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
		description = "Turn Pssst on, and find the Sync Token your phone needs",
		position = 0
	)
	String syncSection = "syncSection";

	@ConfigSection(
		name = "Local Server",
		description = "Let the Pssst app on your phone read your data straight from this PC, over your Wi-Fi or a VPN such as Tailscale",
		position = 10
	)
	String localWsSection = "localWsSection";

	@ConfigSection(
		name = "Your Own Server",
		description = "Send your data to a server you run yourself, such as your own Pssst Worker, so the app can reach it from anywhere",
		position = 20
	)
	String byosSection = "byosSection";

	// --- Master Sync ---

	@ConfigItem(
		keyName = "enableSync",
		name = "Enable Sync",
		description = "Turns Pssst on. Nothing leaves RuneLite until this is on.",
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
		description = "The password your phone uses to read your data. Copy it into the Pssst app, and keep it private.",
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
		name = "Enable Local Server",
		description = "Lets the Pssst app read your data from this PC directly, with no cloud in between. Uses the Sync Token.",
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
		description = "Network port for the local server. Only change it if 8788 is already in use.",
		position = 12,
		section = localWsSection
	)
	default int localServerPort()
	{
		return 8788;
	}

	@ConfigItem(
		keyName = "bindLoopbackOnly",
		name = "This PC Only",
		description = "On: only this PC can connect. Turn it off so your phone can connect over your Wi-Fi or a VPN.",
		warning = "Turning this off lets other devices on your network reach this PC on the chosen port. They still need your Sync Token, and the connection is not encrypted. Only do this on a network you trust.",
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
		name = "Send to My Server",
		description = "Sends your data to the Server URL below whenever it changes.",
		warning = IP_WARNING,
		position = 21,
		section = byosSection
	)
	default boolean enableByosPush()
	{
		return false;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "Address of your own Pssst Worker, for example https://pssst-worker.yourname.workers.dev. Leave empty if you only use the Local Server.",
		position = 22,
		section = byosSection
	)
	default String serverUrl()
	{
		// Empty on purpose: there is no server to assume. (127.0.0.1:8787 was the developer's mock server.)
		return "";
	}
}
