package com.encryptiron;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("encryptiron")
public interface ValianceConfig extends Config
{
	@ConfigItem(
		keyName = "debug",
		name = "Enable debug",
		description = "Results in the plugin logging debug output to the users chatbox",
		position = 1
	)
	default boolean debug()
	{
		return false;
	}

	@ConfigItem(
		keyName = "valianceServerUrl",
		name = "Server URL",
		description = "URL to the Valiance Server (don't change)",
		position = 2
	)
	default String valianceServerUrl()
	{
		return "valianceosrs.com";
	}
}
