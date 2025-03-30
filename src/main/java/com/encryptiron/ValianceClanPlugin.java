package com.encryptiron;

import javax.inject.Inject;

import com.encryptiron.rest.MessageHeaderData;
import com.encryptiron.rest.SendCollectionLog;
import com.encryptiron.rest.SendItemDrop;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.PlayerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Valiance",
	description="Valiance clan plugin to help automate events."
)
public class ValianceClanPlugin extends Plugin
{
	@Inject
	private Client client;
	
	@Inject
	public ValianceConfig config;

	@Inject
	public SendCollectionLog sendCollectionLog;
	
	@Inject
	public SendItemDrop sendItemDrop;

	@Inject
	private EventBus eventBus;

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(sendCollectionLog);
		eventBus.register(sendItemDrop);
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(sendCollectionLog);
		eventBus.unregister(sendItemDrop);
	}

	@Provides
	ValianceConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ValianceConfig.class);
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged playerChanged)
	{
		if (playerChanged.getPlayer().getId() == client.getLocalPlayer().getId())
			MessageHeaderData.setPlayerName(client.getLocalPlayer().getName());
	}
}
