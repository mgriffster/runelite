package net.runelite.client.plugins.slayerprofittracker;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.slayer.SlayerConfig;

import javax.inject.Inject;

public class SlayerProfitPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private SlayerProfitConfig config;

    @Inject
    private ItemManager itemManager;

    @Provides
    SlayerProfitConfig provideSlayerConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SlayerProfitConfig.class);
    }

}
