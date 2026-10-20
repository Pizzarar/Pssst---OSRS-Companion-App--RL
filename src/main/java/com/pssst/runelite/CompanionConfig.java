package com.pssst.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("pssstcompanion")
public interface CompanionConfig extends Config
{
	String IP_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers.";

	/** Settings from older versions (the Local Server, and sending to your own server); unset once on start-up. */
	String[] RETIRED_KEYS = {"enableLocalServer", "localServerPort", "bindLoopbackOnly", "enableByosPush", "serverUrl"};

	@ConfigSection(
		name = "Master Sync",
		description = "Turn Pssst on, and find the Sync Token your phone needs",
		position = 0
	)
	String syncSection = "syncSection";

	@ConfigSection(
		name = "Phone",
		description = "Send your data to the Pssst app on your phone, over your home Wi-Fi",
		position = 10
	)
	String phoneSection = "phoneSection";

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
		description = "The pairing code between this PC and your phone. Type it into the Pssst app.",
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
		description = "The address the Pssst app shows, for example 192.168.1.42, or the name your router gives your phone.",
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
}
