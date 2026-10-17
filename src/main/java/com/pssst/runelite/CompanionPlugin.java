package com.pssst.runelite;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pssst.runelite.birdhouse.BirdhouseTracker;
import com.pssst.runelite.farming.FarmingTracker;
import com.pssst.runelite.slayer.SlayerReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Pssst Companion",
	description = "Syncs player state to the Pssst Android app",
	tags = {"pssst", "companion", "sync"},
	hidden = false
)
public class CompanionPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "pssstcompanion";
	private static final int DEBOUNCE_DELAY_SECONDS = 2;
	private static final int HEARTBEAT_TICKS = 1500; // 15 minutes = 1500 ticks @ 600ms/tick

	// Potion Storage Script & Widget IDs
	private static final int SCRIPT_POTIONSTORE_BUILD = 6080;
	private static final int SCRIPT_POTIONSTORE_UPDATE = 6555;
	private static final int WIDGET_POTIONSTORE_ITEMS = 786490; // (12 << 16) | 58
	private static final int ENUM_POTIONSTORE_POTIONS = 4826;
	private static final int ENUM_POTIONSTORE_UNFINISHED = 4829;

	static final Set<Integer> TRACKED_VARBITS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		// Miscellania
		VarbitID.MISC_APPROVAL,
		VarbitID.MISC_COFFERS,

		// Daily resets & weekly activities
		VarbitID.ZAFF_LAST_CLAIMED,
		VarbitID.NZONE_HERBBOXES_PURCHASED,
		VarbitID.ARDOUGNE_FREE_ESSENCE,
		VarbitID.LUNDAIL_LAST_CLAIMED,
		VarbitID.YANILLE_SAND_CLAIMED,
		VarbitID.SEERS_FREE_FLAX,
		VarbitID.WESTERN_RANTZ_ARROWS,
		VarbitID.MORYTANIA_SLIME_CLAIMED,
		VarbitID.KOUREND_FREE_DYNAMITE,
		VarbitID.TOG_COUNTDOWN,
		VarbitID.TOG_QP_BEFORE_RETURN,

		// What RuneLite's Daily Tasks plugin also reads to decide who is eligible for which daily
		VarbitID.IRONMAN,
		VarbitID.HANDSAND_QUEST,

		// Achievement Diaries (unlock tier counts for daily rewards)
		VarbitID.VARROCK_DIARY_EASY_COMPLETE,
		VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
		VarbitID.VARROCK_DIARY_HARD_COMPLETE,
		VarbitID.VARROCK_DIARY_ELITE_COMPLETE,
		VarbitID.MORYTANIA_DIARY_EASY_COMPLETE,
		VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
		VarbitID.MORYTANIA_DIARY_HARD_COMPLETE,
		VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE,
		VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE,
		VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
		VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE,
		VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE,
		VarbitID.KANDARIN_DIARY_EASY_COMPLETE,
		VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
		VarbitID.KANDARIN_DIARY_HARD_COMPLETE,
		VarbitID.KANDARIN_DIARY_ELITE_COMPLETE,
		VarbitID.WESTERN_DIARY_EASY_COMPLETE,
		VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
		VarbitID.WESTERN_DIARY_HARD_COMPLETE,
		VarbitID.WESTERN_DIARY_ELITE_COMPLETE,
		VarbitID.WILDERNESS_DIARY_EASY_COMPLETE,
		VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
		VarbitID.WILDERNESS_DIARY_HARD_COMPLETE,
		VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE,
		VarbitID.KOUREND_DIARY_EASY_COMPLETE,
		VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
		VarbitID.KOUREND_DIARY_HARD_COMPLETE,
		VarbitID.KOUREND_DIARY_ELITE_COMPLETE,

		VarbitID.FARMING_BLOCKWEEDS
	)));

	static final Set<Integer> TRACKED_VARPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		VarPlayerID.NZONE_REWARDPOINTS
	)));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private CompanionConfig config;

	@Inject
	private Gson gson;

	@Inject
	private NetworkDispatcher networkDispatcher;

	@Inject
	private LocalStateServer localStateServer;

	@Inject
	private ScheduledExecutorService executor;

	// volatile: written on the client thread, read by syncState() on the executor thread.
	// Lists are replaced wholesale, never mutated in place, so a volatile reference is sufficient.
	private volatile List<StateDTO.ItemEntry> currentInventory = new ArrayList<>();
	private volatile List<StateDTO.ItemEntry> currentBank = new ArrayList<>();
	private volatile List<StateDTO.ItemEntry> currentPotionStorage = new ArrayList<>();
	private volatile List<StateDTO.SkillEntry> currentSkills = new ArrayList<>();
	private volatile List<StateDTO.GeOfferEntry> currentGeOffers = new ArrayList<>();
	private final Map<Integer, Integer> currentVarbits = new ConcurrentHashMap<>();
	// Varps and varbits have separate id spaces; keeping them apart avoids one overwriting the other
	private final Map<Integer, Integer> currentVarps = new ConcurrentHashMap<>();
	// Replaced wholesale on the client thread, read by syncState() on the executor thread
	private volatile StateDTO.SlayerEntry currentSlayer;
	private volatile Map<String, String> currentQuests = new HashMap<>();

	private PriceBook priceBook;
	private FarmingTracker farmingTracker;
	private BirdhouseTracker birdhouseTracker;

	private ScheduledFuture<?> debounceTask;
	private int tickCounter = 0;

	@Provides
	CompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CompanionConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		if (config != null && (config.syncToken() == null || config.syncToken().trim().isEmpty()))
		{
			// 128 bits from SecureRandom. The token is the only credential protecting the state (and the Worker row), so
			// it is long, and deliberately never logged.
			byte[] bytes = new byte[16];
			new SecureRandom().nextBytes(bytes);
			StringBuilder token = new StringBuilder();
			for (byte b : bytes)
			{
				token.append(String.format("%02x", b));
			}
			configManager.setConfiguration("pssstcompanion", "syncToken", token.toString());
			log.info("Generated a new sync token (shown in the plugin settings)");
		}

		priceBook = new PriceBook(itemManager::getItemPrice);

		ObservationStore observationStore = new ObservationStore()
		{
			@Override
			public String profileKey()
			{
				return configManager.getRSProfileKey();
			}

			@Override
			public String get(String key)
			{
				return configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				configManager.setRSProfileConfiguration(CONFIG_GROUP, key, value);
			}
		};
		farmingTracker = new FarmingTracker(client::getVarbitValue, observationStore, System::currentTimeMillis);
		birdhouseTracker = new BirdhouseTracker(client::getVarpValue, observationStore, System::currentTimeMillis);

		reconcileLocalServer();

		log.info("Pssst Companion plugin started!");
		clientThread.invokeLater(this::refreshAllClientState);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (debounceTask != null && !debounceTask.isDone())
		{
			debounceTask.cancel(false);
		}
		if (localStateServer != null)
		{
			localStateServer.stop();
		}
		currentInventory = new ArrayList<>();
		currentBank = new ArrayList<>();
		currentPotionStorage = new ArrayList<>();
		currentSkills = new ArrayList<>();
		currentGeOffers = new ArrayList<>();
		currentVarbits.clear();
		currentVarps.clear();
		if (priceBook != null)
		{
			priceBook.clear();
		}
		currentSlayer = null;
		currentQuests = new HashMap<>();
		if (farmingTracker != null)
		{
			farmingTracker.clear();
		}
		if (birdhouseTracker != null)
		{
			birdhouseTracker.clear();
		}
		tickCounter = 0;
		log.info("Companion plugin stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			tickCounter = 0;
			clientThread.invokeLater(this::refreshAllClientState);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		observeLocationBoundState();

		tickCounter++;
		if (tickCounter >= HEARTBEAT_TICKS)
		{
			tickCounter = 0;
			// Prices drift; refresh them on the client thread (where ItemManager may be asked) before syncing
			priceBook.record(heldIds());
			refreshQuests();
			syncState();
		}
	}

	/**
	 * Farming patch varbits and birdhouse varps only describe what is near the player (the region's patches, or
	 * Fossil Island), so they are read per tick, with the player's location, instead of from the global var map.
	 */
	private void observeLocationBoundState()
	{
		Player player = client.getLocalPlayer();
		if (farmingTracker == null || birdhouseTracker == null
			|| client.getGameState() != GameState.LOGGED_IN || player == null)
		{
			return;
		}
		WorldPoint location = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		boolean farmingChanged = farmingTracker.update(location.getX(), location.getY(), location.getPlane());
		boolean birdhousesChanged = birdhouseTracker.update(location.getX(), location.getY(), location.getPlane());
		if (farmingChanged || birdhousesChanged)
		{
			publishState();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		int groupId = event.getGroupId();
		if (isBankOrInterface(groupId))
		{
			triggerDebouncedSync();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.INV)
		{
			updateInventory(event.getItemContainer());
			publishState();
		}
		else if (containerId == InventoryID.BANK)
		{
			updateBank(event.getItemContainer());
			publishState();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		refreshAllSkills();
		publishState();
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		refreshAllGeOffers();
		publishState();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		int scriptId = event.getScriptId();
		if (scriptId == SCRIPT_POTIONSTORE_BUILD || scriptId == SCRIPT_POTIONSTORE_UPDATE)
		{
			updatePotionStorage();
			publishState();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbitId = event.getVarbitId();
		int varpId = event.getVarpId();
		if (varbitId != -1)
		{
			if (TRACKED_VARBITS.contains(varbitId))
			{
				currentVarbits.put(varbitId, event.getValue());
				publishState();
			}
		}
		else if (varpId != -1 && TRACKED_VARPS.contains(varpId))
		{
			currentVarps.put(varpId, event.getValue());
			publishState();
		}
		if (isSlayerVar(varpId, varbitId))
		{
			// RuneLite's Slayer plugin defers the DB lookups a tick for the same reason: the varps are mid-update
			clientThread.invokeLater(() ->
			{
				refreshSlayer();
				publishState();
			});
		}
		// No else-branch rescan: the client fires a separate VarbitChanged for every varbit
		// affected by a varp write, so the tracked-varbit branch above already sees them.
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("pssstcompanion".equals(event.getGroup()))
		{
			reconcileLocalServer();
			if ("enableSync".equals(event.getKey()))
			{
				if (config != null && config.enableSync())
				{
					clientThread.invokeLater(this::refreshAllClientState);
				}
			}
			else if ("enableLocalServer".equals(event.getKey()) || "enableByosPush".equals(event.getKey()))
			{
				triggerDebouncedSync();
			}
		}
	}

	private void reconcileLocalServer()
	{
		if (localStateServer == null)
		{
			return;
		}

		if (config != null && config.enableSync() && config.enableLocalServer())
		{
			localStateServer.start(config.localServerPort(), config.bindLoopbackOnly(), config.syncToken());
		}
		else
		{
			localStateServer.stop();
		}
	}

	private boolean isBankOrInterface(int groupId)
	{
		return groupId == InterfaceID.BANKMAIN
			|| groupId == InterfaceID.BANKSIDE // Bank inventory
			|| groupId == InterfaceID.BANK_DEPOSITBOX
			|| groupId == InterfaceID.GE_OFFERS // Grand Exchange
			|| groupId == InterfaceID.SEED_VAULT;
	}

	private synchronized void triggerDebouncedSync()
	{
		if (debounceTask != null && !debounceTask.isDone())
		{
			debounceTask.cancel(false);
		}
		// RuneLite's shared injected executor; never create our own threads
		debounceTask = executor.schedule(this::syncState, DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	private void syncState()
	{
		if (config == null || !config.enableSync())
		{
			log.debug("Sync skipped: enableSync is disabled in configuration");
			return;
		}

		try
		{
			StateDTO state = buildCurrentState();
			log.info("Sync state collected: inv={} bank={} potions={} skills={} ge={} varbits={}",
				state.getInventory() != null ? state.getInventory().size() : 0,
				state.getBank() != null ? state.getBank().size() : 0,
				state.getPotionStorage() != null ? state.getPotionStorage().size() : 0,
				state.getSkills() != null ? state.getSkills().size() : 0,
				state.getGeOffers() != null ? state.getGeOffers().size() : 0,
				state.getVarbits() != null ? state.getVarbits().size() : 0);

			// 1. Publish to the local server, wrapped in the same envelope the Worker returns
			if (config.enableLocalServer() && localStateServer != null && localStateServer.isRunning())
			{
				Map<String, Object> envelope = new HashMap<>();
				envelope.put("data", state);
				envelope.put("updated_at", System.currentTimeMillis());
				String envelopeJson = gson.toJson(envelope);
				localStateServer.publish(envelopeJson);
			}

			// 2. Dispatch to BYOS outbound server if enabled
			if (config.enableByosPush() && networkDispatcher != null)
			{
				networkDispatcher.dispatch(config.serverUrl(), config.syncToken(), true, state);
			}
		}
		catch (Exception e)
		{
			log.warn("Sync failed", e);
		}
	}

	private StateDTO buildCurrentState()
	{
		return StateDTO.builder()
			.inventory(new ArrayList<>(currentInventory))
			.bank(new ArrayList<>(currentBank))
			.potionStorage(new ArrayList<>(currentPotionStorage))
			.skills(new ArrayList<>(currentSkills))
			.geOffers(new ArrayList<>(currentGeOffers))
			.varbits(new HashMap<>(currentVarbits))
			.varps(new HashMap<>(currentVarps))
			.prices(priceBook.forHeld(heldIds()))
			.slayer(currentSlayer)
			.quests(currentQuests)
			.farming(farmingTracker != null ? farmingTracker.snapshot() : new ArrayList<>())
			.birdhouses(birdhouseTracker != null ? birdhouseTracker.snapshot() : new ArrayList<>())
			.build();
	}

	private void updateInventory(ItemContainer container)
	{
		if (container == null)
		{
			currentInventory = new ArrayList<>();
			return;
		}

		List<StateDTO.ItemEntry> items = new ArrayList<>();
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				ItemComposition definition = client.getItemDefinition(item.getId());
				Integer baseId = ItemIds.notedBaseId(definition.getNote(), definition.getLinkedNoteId());
				items.add(new StateDTO.ItemEntry(item.getId(), item.getQuantity(), baseId));
			}
		}
		currentInventory = items;
		recordPrices(items);
	}

	private void updateBank(ItemContainer container)
	{
		if (container == null)
		{
			currentBank = new ArrayList<>();
			return;
		}

		List<StateDTO.ItemEntry> items = new ArrayList<>();
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				items.add(new StateDTO.ItemEntry(item.getId(), item.getQuantity()));
			}
		}
		currentBank = items;
		recordPrices(items);
	}

	private void refreshAllSkills()
	{
		if (client == null)
		{
			return;
		}

		List<StateDTO.SkillEntry> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if ("Overall".equals(skill.getName()))
			{
				continue;
			}
			try
			{
				int level = client.getRealSkillLevel(skill);
				int boosted = client.getBoostedSkillLevel(skill);
				long xp = client.getSkillExperience(skill);
				skills.add(new StateDTO.SkillEntry(skill.getName(), level, boosted, xp));
			}
			catch (Exception ignored)
			{
			}
		}
		currentSkills = skills;
	}

	private void refreshAllGeOffers()
	{
		if (client == null)
		{
			return;
		}

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}

		List<StateDTO.GeOfferEntry> geList = new ArrayList<>();
		for (int i = 0; i < offers.length; i++)
		{
			GrandExchangeOffer offer = offers[i];
			if (offer != null && offer.getItemId() > 0 && offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				geList.add(new StateDTO.GeOfferEntry(
					i + 1,
					offer.getItemId(),
					offer.getQuantitySold(),
					offer.getTotalQuantity(),
					offer.getPrice(),
					offer.getSpent(),
					offer.getState().name()
				));
			}
		}
		currentGeOffers = geList;
		priceBook.record(heldIds());
	}

	private int resolveCanonicalPotionId(int itemId)
	{
		if (client == null || itemId <= 0)
		{
			return itemId;
		}

		try
		{
			EnumComposition finishedList = client.getEnum(ENUM_POTIONSTORE_POTIONS);
			if (finishedList != null)
			{
				for (int potionEnumId : finishedList.getIntVals())
				{
					EnumComposition potionEnum = client.getEnum(potionEnumId);
					if (potionEnum != null)
					{
						for (int dose = 1; dose <= 4; dose++)
						{
							if (potionEnum.getIntValue(dose) == itemId)
							{
								int fourDoseId = potionEnum.getIntValue(4);
								return fourDoseId > 0 ? fourDoseId : itemId;
							}
						}
					}
				}
			}

			EnumComposition unfinishedList = client.getEnum(ENUM_POTIONSTORE_UNFINISHED);
			if (unfinishedList != null)
			{
				for (int unfinEnumId : unfinishedList.getIntVals())
				{
					EnumComposition unfinEnum = client.getEnum(unfinEnumId);
					if (unfinEnum != null && unfinEnum.getIntValue(1) == itemId)
					{
						return itemId;
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}

		return itemId;
	}

	private void updatePotionStorage()
	{
		if (client == null)
		{
			return;
		}

		Widget widget = client.getWidget(WIDGET_POTIONSTORE_ITEMS);
		if (widget == null)
		{
			return;
		}

		Widget[] children = widget.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		List<StateDTO.ItemEntry> potions = new ArrayList<>();
		for (int i = 0; i + 4 < children.length; i += 5)
		{
			Widget itemWidget = children[i + 1];
			Widget textWidget = children[i + 3];
			if (itemWidget == null || textWidget == null)
			{
				continue;
			}
			int rawItemId = itemWidget.getItemId();
			if (rawItemId <= 0)
			{
				continue;
			}
			String text = textWidget.getText();
			if (text == null || text.trim().isEmpty())
			{
				continue;
			}
			try
			{
				String[] parts = text.split(":");
				if (parts.length >= 2)
				{
					int doses = Integer.parseInt(parts[1].replace(",", "").trim());
					if (doses > 0)
					{
						int canonicalId = resolveCanonicalPotionId(rawItemId);
						potions.add(new StateDTO.ItemEntry(canonicalId, doses));
					}
				}
			}
			catch (Exception ignored)
			{
			}
		}
		if (!potions.isEmpty())
		{
			currentPotionStorage = potions;
			recordPrices(potions);
		}
	}

	private void refreshAllClientState()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		updateInventory(client.getItemContainer(InventoryID.INV));
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			updateBank(bank);
		}
		refreshAllSkills();
		refreshAllGeOffers();
		updatePotionStorage();
		refreshAllVarbits();
		refreshSlayer();
		refreshQuests();
		publishState();
	}

	private static boolean isSlayerVar(int varpId, int varbitId)
	{
		for (int id : SlayerReader.WATCHED_VARPS)
		{
			if (id == varpId)
			{
				return true;
			}
		}
		for (int id : SlayerReader.WATCHED_VARBITS)
		{
			if (id == varbitId)
			{
				return true;
			}
		}
		return false;
	}

	/** Must run on the client thread (varp reads and DB table lookups). Keeps the previous value if names are not resolvable yet. */
	private void refreshSlayer()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		StateDTO.SlayerEntry entry = SlayerReader.read(new SlayerReader.Game()
		{
			@Override
			public int varp(int id)
			{
				return client.getVarpValue(id);
			}

			@Override
			public int varbit(int id)
			{
				return client.getVarbitValue(id);
			}

			@Override
			public List<Integer> dbRowsByValue(int table, int column, Object value)
			{
				return client.getDBRowsByValue(table, column, 0, value);
			}

			@Override
			public Object dbField(int row, int column)
			{
				return client.getDBTableField(row, column, 0)[0];
			}
		});
		if (entry != null)
		{
			currentSlayer = entry;
		}
	}

	/** Quest states come from the client's own quest scripts, so this runs on the client thread only. */
	private void refreshQuests()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		currentQuests = QuestSnapshot.of(Arrays.asList(Quest.values()), quest -> quest.getState(client));
	}

	/** Remembers the GE price of each item (client thread only). */
	private void recordPrices(List<StateDTO.ItemEntry> entries)
	{
		Set<Integer> ids = new HashSet<>();
		for (StateDTO.ItemEntry entry : entries)
		{
			ids.add(ItemIds.pricingId(entry));
		}
		priceBook.record(ids);
	}

	/** Every item id currently held or on offer, as the id that carries its name and price. */
	private Set<Integer> heldIds()
	{
		Set<Integer> ids = new HashSet<>();
		for (List<StateDTO.ItemEntry> entries : Arrays.asList(currentInventory, currentBank, currentPotionStorage))
		{
			for (StateDTO.ItemEntry entry : entries)
			{
				ids.add(ItemIds.pricingId(entry));
			}
		}
		for (StateDTO.GeOfferEntry offer : currentGeOffers)
		{
			if (offer.getItemId() > 0)
			{
				ids.add(offer.getItemId());
			}
		}
		return ids;
	}

	private void refreshAllVarbits()
	{
		if (client == null)
		{
			return;
		}

		for (int varbitId : TRACKED_VARBITS)
		{
			try
			{
				int val = client.getVarbitValue(varbitId);
				currentVarbits.put(varbitId, val);
			}
			catch (Exception ignored)
			{
			}
		}

		for (int varpId : TRACKED_VARPS)
		{
			try
			{
				int val = client.getVarpValue(varpId);
				currentVarps.put(varpId, val);
			}
			catch (Exception ignored)
			{
			}
		}
	}

	private void publishState()
	{
		// Serialization happens once in syncState(); serializing here ran on every container/varbit event
		triggerDebouncedSync();
	}
}
