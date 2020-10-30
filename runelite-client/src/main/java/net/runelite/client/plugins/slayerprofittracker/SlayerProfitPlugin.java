package net.runelite.client.plugins.slayerprofittracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.Collection;

@PluginDescriptor(
        name = "Slayer Profit Tracker",
        description = "Show how much money you make on average per task type",
        tags = {"combat", "notifications", "tasks"}
)
@Slf4j
public class SlayerProfitPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private SlayerProfitConfig config;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ChatCommandManager chatCommandManager;

    @Provides
    SlayerProfitConfig provideSlayerConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SlayerProfitConfig.class);
    }

    @Override
    protected void startUp() throws Exception{
        log.debug("PROFIT TRACKER STARTED UP");
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath){
        Actor actor = actorDeath.getActor();
        log.debug("ACTOR DEATH: "+ actor.getName());
    }

}
