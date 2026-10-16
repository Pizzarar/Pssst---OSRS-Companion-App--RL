package net.runelite.client.plugins.companion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("companion")
public interface CompanionConfig extends Config
{
	@ConfigItem(
		keyName = "apiEndpoint",
		name = "API Endpoint",
		description = "Cloudflare Worker URL endpoint",
		position = 1
	)
	default String apiEndpoint()
	{
		return "";
	}

	@ConfigItem(
		keyName = "authToken",
		name = "Auth Token",
		description = "User-generated UUID string for database authentication",
		secret = true,
		position = 2
	)
	default String authToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "enableSync",
		name = "Enable Sync",
		description = "Enables state synchronization with the remote service",
		warning = "Enabling this transmits your IP and game state to a third-party server.",
		position = 3
	)
	@Warning("Enabling this transmits your IP and game state to a third-party server.")
	default boolean enableSync()
	{
		return false;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@interface Warning
	{
		String value();
	}
}
