package com.encryptiron;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.slf4j.LoggerFactory;

public class ValianceClanPluginTest
{
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        // Debug logging for this plugin only; --debug would turn it on for every plugin
        ((Logger) LoggerFactory.getLogger(ValianceClanPlugin.class.getPackage().getName())).setLevel(Level.DEBUG);

        ExternalPluginManager.loadBuiltin(ValianceClanPlugin.class);
        RuneLite.main(args);
    }
}
