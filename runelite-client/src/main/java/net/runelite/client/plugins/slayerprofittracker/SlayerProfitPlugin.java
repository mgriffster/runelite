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
    public void onGameStateChanged(GameStateChanged gameState){
        if(gameState.getGameState() == GameState.LOGGED_IN){
            chatCommandManager.registerCommandAsync("!test", this::test1, this::test2);
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath){
        Actor actor = actorDeath.getActor();
        log.debug("ACTOR DEATH: "+ actor.getName());
    }


    @Subscribe
    public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
    {
        final NPC npc = npcLootReceived.getNpc();
        final Collection<ItemStack> items = npcLootReceived.getItems();
        final String name = npc.getName();
        StringBuilder sb = new StringBuilder();
        int totalGold = 0;
        for(ItemStack item : items){
            sb.append(item.getId());
            sb.append("|");
            sb.append(item.getQuantity());
            sb.append(" ");
            totalGold += itemManager.getItemPrice(item.getId());
        }

        log.debug(name + "= " + totalGold + "GP || " +sb.toString());
    }

    private boolean test2(ChatInput chatInput, String s) {
        log.debug("TEST2");
        return true;
    }

    private void test1(ChatMessage chatMessage, String s) {
        log.debug("TEST1");
    }


}
