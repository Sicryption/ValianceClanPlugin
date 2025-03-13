package com.encryptiron;

import javax.inject.Inject;

import com.encryptiron.rest.MessageHeaderData;
import com.encryptiron.rest.SendCollectionLog;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;

@Slf4j
@PluginDescriptor(
	name = "Valiance"
)
public class ValianceClanPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ValianceConfig config;

	SendCollectionLog sendCollectionLog;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Valiance Clan Plugin started!");

		sendCollectionLog = new SendCollectionLog(client);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Valiance Clan Plugin stopped!");
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged playerChanged)
	{
		if (playerChanged.getPlayer().getId() == client.getLocalPlayer().getId())
		{
			System.out.println("Logged in as " + client.getLocalPlayer().getName());
			MessageHeaderData.setPlayerName(client.getLocalPlayer().getName());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				break;
		}
	}
	
	@Subscribe
	public void onScriptPreFired(ScriptPreFired preFired) 
	{
		sendCollectionLog.onScriptPreFired(preFired);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) 
	{
		sendCollectionLog.onGameTick(gameTick);
	}
    
	@Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) 
	{
		sendCollectionLog.onWidgetLoaded(widgetLoaded);
    }

	@Provides
	ValianceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ValianceConfig.class);
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
		DropMessage message = new DropMessage(client.getAccountHash(), config.clanUsername(), config.clanEventKeyword(), event.getItems());

		// messenger.send(message);
	}
}
