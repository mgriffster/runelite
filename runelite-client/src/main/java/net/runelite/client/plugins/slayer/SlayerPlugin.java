/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.slayer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import static java.lang.Integer.max;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import joptsimple.internal.Strings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import com.google.common.collect.HashMultiset;

import static net.runelite.api.Skill.SLAYER;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.vars.SlayerUnlock;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.chat.ChatClient;

@PluginDescriptor(
	name = "Slayer",
	description = "Show additional slayer task related information",
	tags = {"combat", "notifications", "overlay", "tasks"}
)
@Slf4j
public class SlayerPlugin extends Plugin
{

	//Excluded item lists for profit tracker
	static final List<Integer> lowBones = Arrays.asList(526, 527,280,281,282,283,529,530,531,532,533,534,535,2530,2531,
			2859,2860,3123,3124,3125,3126,3127,3128,3129,3179,3180,3181,3182,3183,3184,3185,3186,3187,4812,4813,15589,12839
			,24655,19271,592,593);

	static final List<Integer> allBones = Arrays.asList(526, 527,280,281,282,283,529,530,531,532,533,534,535,2530,2531,
			2859,2860,3123,3124,3125,3126,3127,3128,3129,3179,3180,3181,3182,3183,3184,3185,3186,3187,4812,4813,15589,12839
			,24655,22780,22781,22782,536,11943,22124,537,11944,22125,19272,19273,19271,4830,4832,4834,4831,4833,4835,6729,6730,
			22786,22787,22783,22784,592,593);

	//Chat messages
	private static final Pattern CHAT_GEM_PROGRESS_MESSAGE = Pattern.compile("^(?:You're assigned to kill|You have received a new Slayer assignment from .*:) (?:[Tt]he )?(?<name>.+?)(?: (?:in|on|south of) (?:the )?(?<location>[^;]+))?(?:; only | \\()(?<amount>\\d+)(?: more to go\\.|\\))$");
	private static final String CHAT_GEM_COMPLETE_MESSAGE = "You need something new to hunt.";
	private static final Pattern CHAT_COMPLETE_MESSAGE = Pattern.compile("(?:\\d+,)*\\d+");
	private static final String CHAT_CANCEL_MESSAGE = "Your task has been cancelled.";
	private static final String CHAT_CANCEL_MESSAGE_JAD = "You no longer have a slayer task as you left the fight cave.";
	private static final String CHAT_CANCEL_MESSAGE_ZUK = "You no longer have a slayer task as you left the Inferno.";
	private static final String CHAT_SUPERIOR_MESSAGE = "A superior foe has appeared...";
	private static final String CHAT_BRACELET_SLAUGHTER = "Your bracelet of slaughter prevents your slayer";
	private static final Pattern CHAT_BRACELET_SLAUGHTER_REGEX = Pattern.compile("Your bracelet of slaughter prevents your slayer count from decreasing. It has (\\d{1,2}) charges? left\\.");
	private static final String CHAT_BRACELET_EXPEDITIOUS = "Your expeditious bracelet helps you progress your";
	private static final Pattern CHAT_BRACELET_EXPEDITIOUS_REGEX = Pattern.compile("Your expeditious bracelet helps you progress your slayer (?:task )?faster. It has (\\d{1,2}) charges? left\\.");
	private static final String CHAT_BRACELET_SLAUGHTER_CHARGE = "Your bracelet of slaughter has ";
	private static final Pattern CHAT_BRACELET_SLAUGHTER_CHARGE_REGEX = Pattern.compile("Your bracelet of slaughter has (\\d{1,2}) charges? left\\.");
	private static final String CHAT_BRACELET_EXPEDITIOUS_CHARGE = "Your expeditious bracelet has ";
	private static final Pattern CHAT_BRACELET_EXPEDITIOUS_CHARGE_REGEX = Pattern.compile("Your expeditious bracelet has (\\d{1,2}) charges? left\\.");
	private static final Pattern COMBAT_BRACELET_TASK_UPDATE_MESSAGE = Pattern.compile("^You still need to kill (\\d+) monsters to complete your current Slayer assignment");

	//NPC messages
	private static final Pattern NPC_ASSIGN_MESSAGE = Pattern.compile(".*(?:Your new task is to kill|You are to bring balance to)\\s*(?<amount>\\d+) (?<name>.+?)(?: (?:in|on|south of) (?:the )?(?<location>.+))?\\.");
	private static final Pattern NPC_ASSIGN_BOSS_MESSAGE = Pattern.compile("^(?:Excellent\\. )?You're now assigned to (?:kill|bring balance to) (?:the )?(.*) (\\d+) times.*Your reward point tally is (.*)\\.$");
	private static final Pattern NPC_ASSIGN_FIRST_MESSAGE = Pattern.compile("^We'll start you off (?:hunting|bringing balance to) (.*), you'll need to kill (\\d*) of them\\.$");
	private static final Pattern NPC_CURRENT_MESSAGE = Pattern.compile("^You're (?:still(?: meant to be)?|currently assigned to) (?:hunting|bringing balance to|kill|bring balance to|slaying) (?<name>.+?)(?: (?:in|on|south of) (?:the )?(?<location>.+))?(?:, with|; (?:you have|only)) (?<amount>\\d+)(?: more)? to go\\..*");

	//Reward UI
	private static final Pattern REWARD_POINTS = Pattern.compile("Reward points: ((?:\\d+,)*\\d+)");

	private static final int GROTESQUE_GUARDIANS_REGION = 6727;

	private static final int EXPEDITIOUS_CHARGE = 30;
	private static final int SLAUGHTER_CHARGE = 30;

	// Chat Command
	private static final String TASK_COMMAND_STRING = "!task";
	private static final Pattern TASK_STRING_VALIDATION = Pattern.compile("[^a-zA-Z0-9' -]");
	private static final int TASK_STRING_MAX_LENGTH = 50;

	@Inject
	private Client client;

	@Inject
	private SlayerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SlayerOverlay overlay;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TargetClickboxOverlay targetClickboxOverlay;

	@Inject
	private TargetWeaknessOverlay targetWeaknessOverlay;

	@Inject
	private TargetMinimapOverlay targetMinimapOverlay;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ChatClient chatClient;

	@Getter(AccessLevel.PACKAGE)
	private List<NPC> highlightedTargets = new ArrayList<>();

	private final Set<NPC> taggedNpcs = new HashSet<>();
	private int taggedNpcsDiedPrevTick;
	private int taggedNpcsDiedThisTick;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int amount;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int initialAmount;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String taskLocation;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int expeditiousChargeCount;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int slaughterChargeCount;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private String taskName;

	private TaskCounter counter;
	private TaskCounter profitbox;
	private int cachedXp = -1;
	private Instant infoTimer;
	private boolean loginFlag;
	private boolean saveFlag = false;
	private List<String> targetNames = new ArrayList<>();

	//Profit tracker
	private Map<String, ProfitEntry> taskToGoldMap = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		overlayManager.add(targetClickboxOverlay);
		overlayManager.add(targetWeaknessOverlay);
		overlayManager.add(targetMinimapOverlay);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			cachedXp = client.getSkillExperience(SLAYER);

			if (config.amount() != -1
				&& !config.taskName().isEmpty())
			{
				setExpeditiousChargeCount(config.expeditious());
				setSlaughterChargeCount(config.slaughter());
				clientThread.invoke(() -> setTask(config.taskName(), config.amount(), config.initialAmount(), config.taskLocation(), false));
			}
		}
		fetchProfitFromFile();
		chatCommandManager.registerCommandAsync(TASK_COMMAND_STRING, this::taskLookup, this::taskSubmit);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		overlayManager.remove(targetClickboxOverlay);
		overlayManager.remove(targetWeaknessOverlay);
		overlayManager.remove(targetMinimapOverlay);
		removeCounter();
		removeProfitBox();
		highlightedTargets.clear();
		taggedNpcs.clear();
		cachedXp = -1;
		dumpProfitToFile();
		chatCommandManager.unregisterCommand(TASK_COMMAND_STRING);
	}

	@Provides
	SlayerConfig provideSlayerConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case HOPPING:
			case LOGGING_IN:
				cachedXp = -1;
				taskName = "";
				amount = 0;
				loginFlag = true;
				saveFlag = true;
				highlightedTargets.clear();
				taggedNpcs.clear();
				break;
			case LOGGED_IN:
				if (config.amount() != -1
					&& !config.taskName().isEmpty()
					&& loginFlag)
				{
					setExpeditiousChargeCount(config.expeditious());
					setSlaughterChargeCount(config.slaughter());
					setTask(config.taskName(), config.amount(), config.initialAmount(), config.taskLocation(), false);
					loginFlag = false;
				}
				break;
			case LOGIN_SCREEN:
				if(taskToGoldMap != null && saveFlag){
					dumpProfitToFile();
					saveFlag = false;
				}
		}
	}

	private void save()
	{
		config.amount(amount);
		config.initialAmount(initialAmount);
		config.taskName(taskName);
		config.taskLocation(taskLocation);
		config.expeditious(expeditiousChargeCount);
		config.slaughter(slaughterChargeCount);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		if (isTarget(npc))
		{
			highlightedTargets.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		taggedNpcs.remove(npc);
		highlightedTargets.remove(npc);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Widget npcDialog = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
		if (npcDialog != null)
		{
			String npcText = Text.sanitizeMultilineText(npcDialog.getText()); //remove color and linebreaks
			final Matcher mAssign = NPC_ASSIGN_MESSAGE.matcher(npcText); // amount, name, (location)
			final Matcher mAssignFirst = NPC_ASSIGN_FIRST_MESSAGE.matcher(npcText); // name, number
			final Matcher mAssignBoss = NPC_ASSIGN_BOSS_MESSAGE.matcher(npcText); // name, number, points
			final Matcher mCurrent = NPC_CURRENT_MESSAGE.matcher(npcText); // name, (location), amount

			if (mAssign.find())
			{
				String name = mAssign.group("name");
				int amount = Integer.parseInt(mAssign.group("amount"));
				String location = mAssign.group("location");
				setTask(name, amount, amount, location);
			}
			else if (mAssignFirst.find())
			{
				int amount = Integer.parseInt(mAssignFirst.group(2));
				setTask(mAssignFirst.group(1), amount, amount);
			}
			else if (mAssignBoss.find())
			{
				int amount = Integer.parseInt(mAssignBoss.group(2));
				setTask(mAssignBoss.group(1), amount, amount);
				int points = Integer.parseInt(mAssignBoss.group(3).replaceAll(",", ""));
				config.points(points);
			}
			else if (mCurrent.find())
			{
				String name = mCurrent.group("name");
				int amount = Integer.parseInt(mCurrent.group("amount"));
				String location = mCurrent.group("location");
				setTask(name, amount, initialAmount, location);
			}
		}

		Widget braceletBreakWidget = client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT);
		if (braceletBreakWidget != null)
		{
			String braceletText = Text.removeTags(braceletBreakWidget.getText()); //remove color and linebreaks
			if (braceletText.contains("bracelet of slaughter"))
			{
				slaughterChargeCount = SLAUGHTER_CHARGE;
				config.slaughter(slaughterChargeCount);
			}
			else if (braceletText.contains("expeditious bracelet"))
			{
				expeditiousChargeCount = EXPEDITIOUS_CHARGE;
				config.expeditious(expeditiousChargeCount);
			}
		}

		Widget rewardsBarWidget = client.getWidget(WidgetInfo.SLAYER_REWARDS_TOPBAR);
		if (rewardsBarWidget != null)
		{
			for (Widget w : rewardsBarWidget.getDynamicChildren())
			{
				Matcher mPoints = REWARD_POINTS.matcher(w.getText());
				if (mPoints.find())
				{
					final int prevPoints = config.points();
					int points = Integer.parseInt(mPoints.group(1).replaceAll(",", ""));

					if (prevPoints != points)
					{
						config.points(points);
						removeCounter();
						addCounter();
					}

					break;
				}
			}
		}

		if (infoTimer != null && config.statTimeout() != 0)
		{
			Duration timeSinceInfobox = Duration.between(infoTimer, Instant.now());
			Duration statTimeout = Duration.ofMinutes(config.statTimeout());

			if (timeSinceInfobox.compareTo(statTimeout) >= 0)
			{
				removeProfitBox();
				removeCounter();
			}
		}

		taggedNpcsDiedPrevTick = taggedNpcsDiedThisTick;
		taggedNpcsDiedThisTick = 0;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String chatMsg = Text.removeTags(event.getMessage()); //remove color and linebreaks

		if (chatMsg.startsWith(CHAT_BRACELET_SLAUGHTER))
		{
			Matcher mSlaughter = CHAT_BRACELET_SLAUGHTER_REGEX.matcher(chatMsg);

			amount++;
			slaughterChargeCount = mSlaughter.find() ? Integer.parseInt(mSlaughter.group(1)) : SLAUGHTER_CHARGE;
			config.slaughter(slaughterChargeCount);
		}

		if (chatMsg.startsWith(CHAT_BRACELET_EXPEDITIOUS))
		{
			Matcher mExpeditious = CHAT_BRACELET_EXPEDITIOUS_REGEX.matcher(chatMsg);

			amount--;
			expeditiousChargeCount = mExpeditious.find() ? Integer.parseInt(mExpeditious.group(1)) : EXPEDITIOUS_CHARGE;
			config.expeditious(expeditiousChargeCount);
		}

		if (chatMsg.startsWith(CHAT_BRACELET_EXPEDITIOUS_CHARGE))
		{
			Matcher mExpeditious = CHAT_BRACELET_EXPEDITIOUS_CHARGE_REGEX.matcher(chatMsg);

			if (!mExpeditious.find())
			{
				return;
			}

			expeditiousChargeCount = Integer.parseInt(mExpeditious.group(1));
			config.expeditious(expeditiousChargeCount);
		}
		if (chatMsg.startsWith(CHAT_BRACELET_SLAUGHTER_CHARGE))
		{
			Matcher mSlaughter = CHAT_BRACELET_SLAUGHTER_CHARGE_REGEX.matcher(chatMsg);
			if (!mSlaughter.find())
			{
				return;
			}

			slaughterChargeCount = Integer.parseInt(mSlaughter.group(1));
			config.slaughter(slaughterChargeCount);
		}

		if (chatMsg.startsWith("You've completed") && (chatMsg.contains("Slayer master") || chatMsg.contains("Slayer Master")))
		{
			Matcher mComplete = CHAT_COMPLETE_MESSAGE.matcher(chatMsg);

			List<String> matches = new ArrayList<>();
			while (mComplete.find())
			{
				matches.add(mComplete.group(0).replaceAll(",", ""));
			}

			int streak = -1, points = -1;
			switch (matches.size())
			{
				case 0:
					streak = 1;
					break;
				case 1:
					streak = Integer.parseInt(matches.get(0));
					break;
				case 3:
					streak = Integer.parseInt(matches.get(0));
					points = Integer.parseInt(matches.get(2));
					break;
				default:
					log.warn("Unreachable default case for message ending in '; return to Slayer master'");
			}
			if (streak != -1)
			{
				config.streak(streak);
			}
			if (points != -1)
			{
				config.points(points);
			}
			saveTaskGoldToMap(config.taskName());
			incrementProfitTask(config.taskName());
			setTask("", 0, 0);
			return;
		}

		if (chatMsg.equals(CHAT_GEM_COMPLETE_MESSAGE) || chatMsg.equals(CHAT_CANCEL_MESSAGE) || chatMsg.equals(CHAT_CANCEL_MESSAGE_JAD) || chatMsg.equals(CHAT_CANCEL_MESSAGE_ZUK))
		{
			saveTaskGoldToMap(config.taskName());
			setTask("", 0, 0);
			return;
		}

		if (config.showSuperiorNotification() && chatMsg.equals(CHAT_SUPERIOR_MESSAGE))
		{
			notifier.notify(CHAT_SUPERIOR_MESSAGE);
			return;
		}

		Matcher mProgress = CHAT_GEM_PROGRESS_MESSAGE.matcher(chatMsg);

		if (mProgress.find())
		{
			String name = mProgress.group("name");
			int gemAmount = Integer.parseInt(mProgress.group("amount"));
			String location = mProgress.group("location");
			setTask(name, gemAmount, initialAmount, location);
			return;
		}

		final Matcher bracerProgress = COMBAT_BRACELET_TASK_UPDATE_MESSAGE.matcher(chatMsg);

		if (bracerProgress.find())
		{
			final int taskAmount = Integer.parseInt(bracerProgress.group(1));
			setTask(taskName, taskAmount, initialAmount);

			// Avoid race condition (combat brace message goes through first before XP drop)
			amount++;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		if (statChanged.getSkill() != SLAYER)
		{
			return;
		}

		int slayerExp = statChanged.getXp();

		if (slayerExp <= cachedXp)
		{
			return;
		}

		if (cachedXp == -1)
		{
			// this is the initial xp sent on login
			cachedXp = slayerExp;
			return;
		}

		final int delta = slayerExp - cachedXp;
		cachedXp = slayerExp;

		log.debug("Slayer xp change delta: {}, killed npcs: {}", delta, taggedNpcsDiedPrevTick);

		final Task task = Task.getTask(taskName);
		if (task != null && task.getExpectedKillExp() > 0)
		{
			// Only decrement a kill if the xp drop matches the expected drop. This is just for Tzhaar tasks.
			if (task.getExpectedKillExp() == delta)
			{
				killed(1);
			}
		}
		else
		{
			// This is at least one kill, but if we observe multiple tagged NPCs dieing on the previous tick, count them
			// instead.
			killed(max(taggedNpcsDiedPrevTick, 1));
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		Actor actor = hitsplatApplied.getActor();
		Hitsplat hitsplat = hitsplatApplied.getHitsplat();
		if (hitsplat.getHitsplatType() == Hitsplat.HitsplatType.DAMAGE_ME && highlightedTargets.contains(actor))
		{
			// If the actor is in highlightedTargets it must be an NPC and also a task assignment
			taggedNpcs.add((NPC) actor);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		Actor actor = actorDeath.getActor();
		if (taggedNpcs.contains(actor))
		{
			log.debug("Tagged NPC {} has died", actor.getName());
			++taggedNpcsDiedThisTick;
		}
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
	{
		final NPC npc = npcLootReceived.getNpc();

		if(isTarget(npc)){
			ExcludedItems excludedItems = config.excludedItems();
            final Collection<ItemStack> items = npcLootReceived.getItems();
            final String name = npc.getName();
            for(ItemStack item : items){
            	//Check if the item should be excluded from consideration for task loot value
            	if(excludedItems.getExcludeList() != null
						&& excludedItems.getExcludeList().contains(item.getId())){
            		continue;
				}
            	int price = itemManager.getItemPrice(item.getId()) * item.getQuantity();
            	if(config.valueCutoff() > price){
            		continue;
				}
            	config.currentGold(config.currentGold() + price);
            	addProfitBox();
				profitbox.setCount(config.currentGold());
				int existingTaskGold = taskToGoldMap.containsKey(taskName) ? taskToGoldMap.get(taskName).getTotalGold() : 0;

				String goldToolTip = ColorUtil.wrapWithColorTag("%s", new Color(255, 119, 0)) + "</br>";

				goldToolTip += ColorUtil.wrapWithColorTag("This Task:", Color.YELLOW)
						+ " %s</br>"
						+ ColorUtil.wrapWithColorTag("Lifetime:", Color.YELLOW)
						+ " %s";
				profitbox.setTooltip(String.format(goldToolTip, capsString(taskName), config.currentGold(), existingTaskGold + config.currentGold()));
            }

        }
	}

	private void addTaskToProfitMap(String taskName){
		if(!taskToGoldMap.containsKey(taskName)){
			taskToGoldMap.put(taskName, new ProfitEntry());
		}
	}

	private void saveTaskGoldToMap(String taskName){
		if(taskName != null && !taskName.isEmpty()){
			if(taskToGoldMap.containsKey(taskName)){
				ProfitEntry oldInfo = taskToGoldMap.get(taskName);
				oldInfo.setTotalGold(oldInfo.getTotalGold() + config.currentGold());
			}
			else{
				taskToGoldMap.put(taskName, new ProfitEntry(config.currentGold(), 0 ,0));
			}
			config.currentGold(0);
		}

	}

	private void incrementProfitTask(String taskName){
		if(taskName != null && taskToGoldMap.containsKey(taskName)){
			ProfitEntry taskEntry = taskToGoldMap.get(taskName);
			taskEntry.setTaskCount(taskEntry.getTaskCount()+1);
		}
		else{
			log.debug("Attempt to increment task for "+ taskName+" was unsuccessful");
		}
	}

	private void incrementProfitKill(String taskName, int amt){
		if(taskName != null && taskToGoldMap.containsKey(taskName)){
			ProfitEntry taskEntry = taskToGoldMap.get(taskName);
			taskEntry.setKillCount(taskEntry.getKillCount()+amt);
		}else{
			log.debug("Attempt to increment kill for "+ taskName+ " | " + amt +" was unsuccessful");
		}
	}

	private void dumpProfitToFile(){
		ObjectMapper mapper = new ObjectMapper();
		try{
			log.debug("PROFIT MAP SAVE: " + taskToGoldMap.toString());
			mapper.writeValue(new File("SlayerProfit.json"), taskToGoldMap);
		}
		catch(Exception e){
			log.error("Error while saving profits to JSON file: " + e.toString());
		}

	}

	private void fetchProfitFromFile(){
		ObjectMapper mapper = new ObjectMapper();
		try{
			File json = new File("SlayerProfit.json");
			if(json.exists()){
				taskToGoldMap = mapper.readValue(json, new TypeReference<Map<String, ProfitEntry>>(){});
				log.debug("PROFIT MAP LOAD: " + taskToGoldMap.toString());
			}
			else{
				taskToGoldMap = new HashMap<>();
			}
		}
		catch(Exception e){
			log.error("Error while fetching profits from JSON file: " + e.toString());
		}

	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("slayer") || !event.getKey().equals("infobox") || !event.getKey().equals("profitBox"))
		{
			return;
		}

		if (config.showInfobox())
		{
			clientThread.invoke(this::addCounter);
		}
		else
		{
			removeCounter();
		}

		if(config.showProfitBox()){
			clientThread.invoke(this::addProfitBox);
		}
		else{
			removeProfitBox();
		}
	}

	@VisibleForTesting
void killed(int amt)
	{
		if (amount == 0)
		{
			return;
		}

		amount -= amt;
		if (doubleTroubleExtraKill())
		{
			assert amt == 1;
			amount--;
		}

		config.amount(amount); // save changed value

		if (!config.showInfobox())
		{
			return;
		}

		// add and update counter, set timer
		incrementProfitKill(taskName, amt);
		addCounter();
		counter.setCount(amount);
		infoTimer = Instant.now();
	}

	private boolean doubleTroubleExtraKill()
	{
		return WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID() == GROTESQUE_GUARDIANS_REGION &&
			SlayerUnlock.GROTESQUE_GUARDIAN_DOUBLE_COUNT.isEnabled(client);
	}

	private boolean isTarget(NPC npc)
	{
		if (targetNames.isEmpty())
		{
			return false;
		}

		String name = npc.getName();
		if (name == null)
		{
			return false;
		}

		name = name.toLowerCase();

		for (String target : targetNames)
		{
			if (name.contains(target))
			{
				NPCComposition composition = npc.getTransformedComposition();

				if (composition != null)
				{
					List<String> actions = Arrays.asList(composition.getActions());
					if (actions.contains("Attack") || actions.contains("Pick")) //Pick action is for zygomite-fungi
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	private void rebuildTargetNames(Task task)
	{
		targetNames.clear();

		if (task != null)
		{
			Arrays.stream(task.getTargetNames())
				.map(String::toLowerCase)
				.forEach(targetNames::add);

			targetNames.add(taskName.toLowerCase().replaceAll("s$", ""));
		}
	}

	private void rebuildTargetList()
	{
		highlightedTargets.clear();

		for (NPC npc : client.getNpcs())
		{
			if (isTarget(npc))
			{
				highlightedTargets.add(npc);
			}
		}
	}

	private void setTask(String name, int amt, int initAmt)
	{
		setTask(name, amt, initAmt, null);
	}

	private void setTask(String name, int amt, int initAmt, String location)
	{
		setTask(name, amt, initAmt, location, true);
	}

	private void setTask(String name, int amt, int initAmt, String location, boolean addCounter)
	{
		taskName = name;
		amount = amt;
		initialAmount = Math.max(amt, initAmt);
		taskLocation = location;
		save();
		removeCounter();
		removeProfitBox();
		if (addCounter)
		{
			infoTimer = Instant.now();
			addCounter();
			addProfitBox();
		}

		Task task = Task.getTask(name);
		addTaskToProfitMap(taskName);
		rebuildTargetNames(task);
		rebuildTargetList();
	}

	private void addCounter()
	{
		if (!config.showInfobox() || counter != null || Strings.isNullOrEmpty(taskName))
		{
			return;
		}

		Task task = Task.getTask(taskName);
		int itemSpriteId = ItemID.ENCHANTED_GEM;
		if (task != null)
		{
			itemSpriteId = task.getItemSpriteId();
		}

		BufferedImage taskImg = itemManager.getImage(itemSpriteId);
		String taskTooltip = ColorUtil.wrapWithColorTag("%s", new Color(255, 119, 0)) + "</br>";

		if (taskLocation != null && !taskLocation.isEmpty())
		{
			taskTooltip += taskLocation + "</br>";
		}

		taskTooltip += ColorUtil.wrapWithColorTag("Pts:", Color.YELLOW)
			+ " %s</br>"
			+ ColorUtil.wrapWithColorTag("Streak:", Color.YELLOW)
			+ " %s";

		if (initialAmount > 0)
		{
			taskTooltip += "</br>"
				+ ColorUtil.wrapWithColorTag("Start:", Color.YELLOW)
				+ " " + initialAmount;
		}

		counter = new TaskCounter(taskImg, this, amount);
		counter.setTooltip(String.format(taskTooltip, capsString(taskName), config.points(), config.streak()));

		infoBoxManager.addInfoBox(counter);
	}

	private void removeCounter()
	{
		if (counter == null)
		{
			return;
		}

		infoBoxManager.removeInfoBox(counter);
		counter = null;
	}

	private void addProfitBox()
	{
		if (!config.showProfitBox() || profitbox != null || Strings.isNullOrEmpty(taskName))
		{
			return;
		}
		if(!taskName.isEmpty()){
			BufferedImage goldImg = itemManager.getImage(1004);
			String goldToolTip = ColorUtil.wrapWithColorTag("%s", new Color(255, 119, 0)) + "</br>";

			goldToolTip += ColorUtil.wrapWithColorTag("This session:", Color.YELLOW)
					+ " %s</br>"
					+ ColorUtil.wrapWithColorTag("Lifetime:", Color.YELLOW)
					+ " %s";


			profitbox = new TaskCounter(goldImg, this, config.currentGold());
			int existingTaskGold = taskToGoldMap.containsKey(taskName) ? taskToGoldMap.get(taskName).getTotalGold() : 0;
			profitbox.setTooltip(String.format(goldToolTip, capsString(taskName), config.currentGold(), existingTaskGold + config.currentGold()));

			infoBoxManager.addInfoBox(profitbox);
		}

	}

	private void removeProfitBox(){
		if(profitbox == null){
			return;
		}
		infoBoxManager.removeInfoBox(profitbox);
		profitbox = null;
	}

	void taskLookup(ChatMessage chatMessage, String message)
	{
		if (!config.taskCommand())
		{
			return;
		}

		ChatMessageType type = chatMessage.getType();

		final String player;
		if (type.equals(ChatMessageType.PRIVATECHATOUT))
		{
			player = client.getLocalPlayer().getName();
		}
		else
		{
			player = Text.removeTags(chatMessage.getName())
				.replace('\u00A0', ' ');
		}

		net.runelite.http.api.chat.Task task;
		try
		{
			task = chatClient.getTask(player);
		}
		catch (IOException ex)
		{
			log.debug("unable to lookup slayer task", ex);
			return;
		}

		if (TASK_STRING_VALIDATION.matcher(task.getTask()).find() || task.getTask().length() > TASK_STRING_MAX_LENGTH ||
			TASK_STRING_VALIDATION.matcher(task.getLocation()).find() || task.getLocation().length() > TASK_STRING_MAX_LENGTH ||
			Task.getTask(task.getTask()) == null || !Task.LOCATIONS.contains(task.getLocation()))
		{
			log.debug("Validation failed for task name or location: {}", task);
			return;
		}

		int killed = task.getInitialAmount() - task.getAmount();

		StringBuilder sb = new StringBuilder();
		sb.append(task.getTask());
		if (!Strings.isNullOrEmpty(task.getLocation()))
		{
			sb.append(" (").append(task.getLocation()).append(")");
		}
		sb.append(": ");
		if (killed < 0)
		{
			sb.append(task.getAmount()).append(" left");
		}
		else
		{
			sb.append(killed).append('/').append(task.getInitialAmount()).append(" killed");
		}

		String response = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append("Slayer Task: ")
			.append(ChatColorType.HIGHLIGHT)
			.append(sb.toString())
			.build();

		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(response);
		chatMessageManager.update(messageNode);
		client.refreshChat();
	}

	private boolean taskSubmit(ChatInput chatInput, String value)
	{
		if (Strings.isNullOrEmpty(taskName))
		{
			return false;
		}

		final String playerName = client.getLocalPlayer().getName();

		executor.execute(() ->
		{
			try
			{
				chatClient.submitTask(playerName, capsString(taskName), amount, initialAmount, taskLocation);
			}
			catch (Exception ex)
			{
				log.warn("unable to submit slayer task", ex);
			}
			finally
			{
				chatInput.resume();
			}
		});

		return true;
	}

	//Utils
	private String capsString(String str)
	{
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
