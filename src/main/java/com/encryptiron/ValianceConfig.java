package com.encryptiron;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("encryptiron")
public interface ValianceConfig extends Config
{
	@ConfigItem(
		keyName = "clanUsername",
		name = "Clan name to submit to",
		description = "Name within the clan to submit drops to",
		position = 1
	)
	default String clanUsername()
	{
		return "YOUR_USERNAME";
	}

	@ConfigItem(
		keyName = "clanEventKeyword",
		name = "Clan Event Keyword",
		description = "Keyword for the active clan event",
		position = 2
	)
	default String clanEventKeyword()
	{
		return "KEYWORD";
	}

	@ConfigItem(
		keyName = "debug",
		name = "Enable debug",
		description = "Results in the plugin logging output to the users chatbox",
		position = 3
	)
	default boolean debug()
	{
		return true;
	}
}
