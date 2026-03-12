package com.encryptiron;

import javax.inject.Inject;

import com.encryptiron.rest.MessageHeaderData;
import com.encryptiron.rest.NewCollectionLogEntry;
import com.encryptiron.rest.OnBossKilled;
import com.encryptiron.rest.SendCollectionLog;
import com.encryptiron.rest.SendCombatAchievements;
import com.encryptiron.rest.SendItemDrop;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerChanged;
import net.runelite.client.callback.ClientThread;
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
    public SendCombatAchievements sendCombatAchievements;
    
    @Inject
    public SendItemDrop sendItemDrop;

    @Inject
    public NewCollectionLogEntry newClogEntry;

    @Inject
    public OnBossKilled onBossKilled;

    @Inject
    private EventBus eventBus;

    @Inject
    private ClientThread clientThread;

    @Provides
    ValianceConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ValianceConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        eventBus.register(sendCollectionLog);
        eventBus.register(sendCombatAchievements);
        eventBus.register(sendItemDrop);
        eventBus.register(newClogEntry);
        eventBus.register(onBossKilled);

        tryLoadPlayer();
    }

    @Override
    protected void shutDown() throws Exception
    {
        eventBus.unregister(sendCollectionLog);
        eventBus.unregister(sendCombatAchievements);
        eventBus.unregister(sendItemDrop);
        eventBus.unregister(newClogEntry);
        eventBus.unregister(onBossKilled);
        
        MessageHeaderData.resetPlayerName();
        sendCollectionLog.resetNumClogsAccordingToVarp();
    }

    @Subscribe
    public void onPlayerChanged(PlayerChanged playerChanged)
    {
        if (playerChanged.getPlayer().getId() == client.getLocalPlayer().getId())
        {
            tryLoadPlayer();
        }
    }
    
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
            event.getGameState() == GameState.HOPPING)
		{
            MessageHeaderData.resetPlayerName();
		}
    }
    
    private void tryLoadPlayer()
    {
        // Keep trying each tick until the client's name is populated.
        clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
            {
                return false;
            }

            // If the player has been logged in, then we will have missed the varp change events that
            // happen during login. So we will manually scan and update our collection log count here.
            sendCollectionLog.updateNumClogsAccordingToVarp();
            MessageHeaderData.setPlayerName(client.getLocalPlayer().getName());

            return true;
        });
    }
}
