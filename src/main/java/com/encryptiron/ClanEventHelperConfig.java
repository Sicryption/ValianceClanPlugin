package com.encryptiron;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("encryptiron")
public interface ClanEventHelperConfig extends Config
{
	@ConfigItem(
		keyName = "clanUsername",
		name = "Clan name to submit to",
		description = "Name within the clan to submit drops to"
	)
	default String clanUsername()
	{
		return "YOUR_USERNAME";
	}

	@ConfigItem(
		keyName = "debug",
		name = "Enable debug",
		description = "Results in the plugin logging output to the users chatbox"
	)
	default Boolean debug()
	{
		return false;
	}
}
