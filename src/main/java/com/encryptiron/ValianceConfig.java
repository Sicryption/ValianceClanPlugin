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
		position = 3
	)
	default boolean debug()
	{
		return false;
	}
}
