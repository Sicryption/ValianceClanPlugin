package com.encryptiron;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;

@Slf4j
@PluginDescriptor(
	name = "Clan Event Helper"
)
public class ClanEventHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClanEventHelperConfig config;

	private Messenger messenger;

	@Override
	protected void startUp() throws Exception
	{
		log.info("ClanEventHelperPlugin started!");

		messenger = new Messenger(config, client);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("ClanEventHelperPlugin stopped!");

		messenger = null;
	}

	@Provides
	ClanEventHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanEventHelperConfig.class);
	}

	@Subscribe
	public void onLootReceived(final LootReceived event)
	{
		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);

		// Only support main game drops
		if (profileType != RuneScapeProfileType.STANDARD)
		{
			return;
		}

		// Create message
		DropMessage message = new DropMessage(client.getAccountHash(), config.clanUsername(), event.getItems());

		messenger.send(message);
	}
}
