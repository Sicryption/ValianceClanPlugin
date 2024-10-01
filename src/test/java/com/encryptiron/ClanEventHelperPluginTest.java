package com.encryptiron;

import com.encryptiron.ClanEventHelperPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanEventHelperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanEventHelperPlugin.class);
		RuneLite.main(args);
	}
}