package com.pssst.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("pssstcompanion")
public interface CompanionConfig extends Config
{
	String IP_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers.";

	/** Settings removed when the plugin stopped serving data itself (it now only sends); unset once on start-up. */
	String[] RETIRED_KEYS = {"enableLocalServer", "localServerPort", "bindLoopbackOnly"};

	@ConfigSection(
		name = "Master Sync",
		description = "Turn Pssst on, and find the Sync Token your phone needs",
		position = 0
	)
	String syncSection = "syncSection";

	@ConfigSection(
		name = "Phone",
		description = "Send your data to the Pssst app on your phone, over your home Wi-Fi (or Tailscale)",
		position = 10
	)
	String phoneSection = "phoneSection";

	@ConfigSection(
		name = "Advanced: Your Own Server",
		description = "Optional. Send your data to a server you run yourself, such as your own Pssst Worker. Most players do not need this.",
		position = 20,
		closedByDefault = true
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
		description = "The password between this PC and your phone. Copy it into the Pssst app, and keep it private.",
		position = 2,
		section = syncSection
	)
	default String syncToken()
	{
		return "";
	}

	// --- Phone ---

	@ConfigItem(
		keyName = "sendToPhone",
		name = "Send to My Phone",
		description = "Sends your data to the Pssst app on your phone when it changes (at most every 2 minutes while you play).",
		warning = IP_WARNING,
		position = 11,
		section = phoneSection
	)
	default boolean sendToPhone()
	{
		return false;
	}

	@ConfigItem(
		keyName = "phoneAddress",
		name = "Phone Address",
		description = "The address the Pssst app shows, for example 192.168.1.42 (or your phone's Tailscale address, 100.x.x.x).",
		position = 12,
		section = phoneSection
	)
	default String phoneAddress()
	{
		return "";
	}

	@ConfigItem(
		keyName = "includeBank",
		name = "Include Bank",
		description = "Also sends your bank and potion storage. Turn off to keep them on this PC.",
		warning = "This sends the contents of your bank and potion storage to the address you set.",
		position = 13,
		section = phoneSection
	)
	default boolean includeBank()
	{
		return true;
	}

	// --- Your own server (advanced) ---

	@ConfigItem(
		keyName = "enableByosPush",
		name = "Send to My Server",
		description = "Also sends your data to the Server URL below when it changes.",
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
		description = "Address of your own Pssst Worker, for example https://pssst-worker.yourname.workers.dev.",
		position = 22,
		section = byosSection
	)
	default String serverUrl()
	{
		// Empty on purpose: there is no server to assume
		return "";
	}
}
