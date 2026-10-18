package com.pssst.runelite;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pssst.runelite.birdhouse.BirdhouseTracker;
import com.pssst.runelite.farming.FarmingTracker;
import com.pssst.runelite.slayer.SlayerReader;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
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
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.game.ItemManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.HttpUrl;

@Slf4j
@PluginDescriptor(
	name = "Pssst Companion",
	internalName = "pssst-companion", // required for getPluginDirectory(); the saved snapshot lives there
	description = "Syncs player state to the Pssst Android app",
	tags = {"pssst", "companion", "sync"},
	hidden = false
)
public class CompanionPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "pssstcompanion";
	private static final int DEBOUNCE_DELAY_SECONDS = 2;
	private static final int DELIVERY_TICK_SECONDS = 5;
	private static final String TARGET_PHONE = "phone";
	private static final int HEARTBEAT_TICKS = 1500; // 15 minutes = 1500 ticks @ 600ms/tick

	// Potion storage: the same named enums, scripts and widget RuneLite's PotionStorage reads, not bare numbers
	private static final int SCRIPT_POTIONSTORE_BUILD = ScriptID.POTIONSTORE_BUILD;
	private static final int SCRIPT_POTIONSTORE_UPDATE = ScriptID.POTIONSTORE_DOSE_CHANGE;
	private static final int WIDGET_POTIONSTORE_ITEMS = InterfaceID.Bankmain.POTIONSTORE_ITEMS;
	private static final int ENUM_POTIONSTORE_POTIONS = EnumID.POTIONSTORE_POTIONS;
	private static final int ENUM_POTIONSTORE_UNFINISHED = EnumID.POTIONSTORE_UNFINISHED_POTIONS;

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
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

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

	// When the game last fed the snapshot (event or heartbeat while logged in); see StateDTO.observedAt
	private volatile long observedAtMs;
	// When the bank / potion storage were last read from the open bank, or carried over from the saved snapshot
	private volatile long bankObservedAtMs;
	// True once this RuneLite session has seen the logged-in game. Until then the saved snapshot is what gets sent.
	private volatile boolean liveThisSession;
	// The RS profile the live state belongs to; written on the client thread, read when saving
	private volatile String profileKey;
	// The previous session's snapshot, loaded off-thread at startup; consumed once the profile is known
	private volatile SnapshotStore.Snapshot persisted;
	// What gets delivered to the phone: the live state once logged in, the saved snapshot before that.
	private volatile StateDTO latestState;
	private volatile String latestProfileKey;
	// Why the Phone Address cannot be used, or null
	private volatile String phoneAddressProblem;

	private final DeliveryPolicy phonePolicy = new DeliveryPolicy(System::currentTimeMillis);
	private IconBook iconBook;
	private ScheduledFuture<?> deliveryTask;

	private SnapshotStore snapshotStore;
	private PairingPanel pairingPanel;
	private NavigationButton navigationButton;

	private PriceBook priceBook;
	private FarmingTracker farmingTracker;
	private BirdhouseTracker birdhouseTracker;

	private ScheduledFuture<?> debounceTask;
	private int tickCounter = 0;

	private static final String[] TOKEN_MONSTERS = {
		"goblin", "jad", "zulrah", "vorkath", "elvarg", "cerberus", "kraken", "bandos",
		"saradomin", "zamorak", "armadyl", "guthix", "olm", "verzik", "tumeken", "nex",
		"barrow", "slayer", "tzhaar", "wyvern", "kurask", "nechryael", "gargoyle", "abyssal",
		"dragon", "rune", "gilded", "crystal", "obsidian", "torva", "ancestral", "masori"
	};

	private static final String[] TOKEN_ITEMS = {
		"whip", "scimitar", "shark", "ranarr", "blowpipe", "crossbow", "halberd", "defender",
		"tbow", "scythe", "shadow", "fang", "claws", "godsword", "warhammer", "rapier",
		"pickaxe", "harpoon", "axe", "tinderbox", "spade", "chisel", "cannon", "thrall",
		"prayer", "stamina", "brews", "angler", "karambwan", "supercomb", "lantern", "totem"
	};

	/** Human-friendly OSRS word-pair token (e.g. goblin-whip) for easy pairing across PC and phone. */
	static String newToken()
	{
		SecureRandom random = new SecureRandom();
		String first = TOKEN_MONSTERS[random.nextInt(TOKEN_MONSTERS.length)];
		String second = TOKEN_ITEMS[random.nextInt(TOKEN_ITEMS.length)];
		return first + "-" + second;
	}

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
			configManager.setConfiguration(CONFIG_GROUP, "syncToken", newToken());
			log.info("Generated a new sync token (shown in the plugin settings)");
		}

		priceBook = new PriceBook(itemManager::getItemPrice);
		iconBook = new IconBook(new IconBook.Source()
		{
			@Override
			public void request(int id, Runnable onLoaded)
			{
				itemManager.getImage(id).onLoaded(() ->
				{
					onLoaded.run();
					clientThread.invokeLater(iconBook::process);
				});
			}

			@Override
			public StateDTO.IconEntry encode(int id)
			{
				return IconEncoder.encode(itemManager.getItemComposition(id).getName(), itemManager.getImage(id));
			}
		});

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

		for (String key : CompanionConfig.RETIRED_KEYS)
		{
			// Local Server settings from older versions: the plugin no longer listens on a port
			configManager.unsetConfiguration(CONFIG_GROUP, key);
		}

		snapshotStore = new SnapshotStore(this::getPluginDirectory, gson);
		executor.execute(this::loadPersisted); // disk read: not here, and never on the client thread
		// Cheap check every few seconds; DeliveryPolicy decides whether anything is actually sent
		deliveryTask = executor.scheduleWithFixedDelay(this::deliveryTick, DELIVERY_TICK_SECONDS, DELIVERY_TICK_SECONDS, TimeUnit.SECONDS);

		pairingPanel = new PairingPanel(config, this::phoneStatusHtml,
			(key, value) -> configManager.setConfiguration(CONFIG_GROUP, key, value), CompanionPlugin::newToken,
			this::sendNow);
		navigationButton = NavigationButton.builder()
			.tooltip("Pssst Companion")
			.icon(panelIcon())
			.priority(10)
			.panel(pairingPanel)
			.build();
		clientToolbar.addNavigation(navigationButton);

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
		if (deliveryTask != null)
		{
			deliveryTask.cancel(false);
			deliveryTask = null;
		}
		flushSnapshot();
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		if (pairingPanel != null)
		{
			pairingPanel.dispose();
			pairingPanel = null;
		}
		if (iconBook != null)
		{
			iconBook.clear();
		}
		liveThisSession = false;
		persisted = null;
		profileKey = null;
		latestState = null;
		latestProfileKey = null;
		observedAtMs = 0;
		bankObservedAtMs = 0;
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
		else if (event.getGameState() == GameState.LOGIN_SCREEN && liveThisSession)
		{
			// Logged out: send the final state now instead of waiting for the 2-minute pacing
			phonePolicy.requestFlush();
			executor.schedule(this::deliveryTick, DEBOUNCE_DELAY_SECONDS + 1, TimeUnit.SECONDS);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		resolveProfile();
		observeLocationBoundState();
		iconBook.process();

		tickCounter++;
		if (tickCounter >= HEARTBEAT_TICKS)
		{
			tickCounter = 0;
			// Prices drift; refresh them on the client thread (where ItemManager may be asked) before syncing
			priceBook.record(heldIds());
			refreshQuests();
			// Serializing and saving the whole state is not client-thread work: hand it to the executor
			observedAtMs = System.currentTimeMillis();
			triggerDebouncedSync();
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// RuneLite is closing: the last state goes to disk, and to the phone if it is listening
		SnapshotStore.Snapshot snapshot = currentSnapshot();
		if (snapshot == null)
		{
			return;
		}
		latestState = snapshot.getData();
		latestProfileKey = snapshot.getProfileKey();
		event.waitFor(executor.submit(() -> snapshotStore.write(withMarks(snapshot))));
		phonePolicy.requestFlush();
		for (CompletableFuture<Void> sent : deliverAll())
		{
			event.waitFor(sent);
		}
	}

	/**
	 * Client thread. The RS profile is known a moment after login. The first time it is, the previous session's bank and
	 * potion storage are carried over if they belong to the same profile (the game only shows them while the bank is
	 * open, so otherwise the phone would be told the bank is empty). If a different profile logs in later, the old
	 * profile's bank is dropped so it is never shown as someone else's.
	 */
	private void resolveProfile()
	{
		String key = configManager.getRSProfileKey();
		if (key == null || key.equals(profileKey))
		{
			return;
		}
		boolean switched = profileKey != null;
		profileKey = key;
		SnapshotStore.Snapshot snapshot = persisted;
		persisted = null;
		if (switched)
		{
			currentBank = new ArrayList<>();
			currentPotionStorage = new ArrayList<>();
			bankObservedAtMs = 0;
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			if (bank != null)
			{
				updateBank(bank);
			}
		}
		else
		{
			carryOver(BankCarry.from(snapshot, key));
		}
		publishState();
	}

	private void carryOver(BankCarry carry)
	{
		if (carry == null)
		{
			return;
		}
		if (currentBank.isEmpty() && !carry.bank.isEmpty())
		{
			currentBank = carry.bank;
			recordPrices(currentBank);
			bankObservedAtMs = carry.observedAt;
		}
		if (currentPotionStorage.isEmpty() && !carry.potionStorage.isEmpty())
		{
			currentPotionStorage = carry.potionStorage;
			recordPrices(currentPotionStorage);
			if (bankObservedAtMs == 0)
			{
				bankObservedAtMs = carry.observedAt;
			}
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

	void sendNow()
	{
		phonePolicy.reset();
		phoneAddressProblem = null;
		if (executor != null)
		{
			executor.execute(this::deliveryTick);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("pssstcompanion".equals(event.getGroup()))
		{
			String key = event.getKey();
			if ("syncToken".equals(key) || "sendToPhone".equals(key) || "phoneAddress".equals(key) || "includeBank".equals(key))
			{
				sendNow();
			}
			if (pairingPanel != null)
			{
				pairingPanel.refresh();
			}
			if ("enableSync".equals(event.getKey()))
			{
				if (config != null && config.enableSync())
				{
					clientThread.invokeLater(this::refreshAllClientState);
				}
				else if (snapshotStore != null)
				{
					// Sync off means nothing about the player stays on disk for the app
					executor.execute(snapshotStore::delete);
				}
			}
			else if ("sendToPhone".equals(event.getKey()))
			{
				triggerDebouncedSync();
			}
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
		if (!liveThisSession)
		{
			// Nothing has been seen in game yet this session: an empty state must not replace the saved snapshot
			log.debug("Sync skipped: not logged in yet this session");
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

			// This is what the delivery loop sends (to the phone, and to your own server if set)
			latestState = state;
			if (profileKey != null)
			{
				latestProfileKey = profileKey;
			}

			// Keep the last state on disk for the next RuneLite start, with what each target already has
			if (snapshotStore != null && profileKey != null)
			{
				snapshotStore.write(withMarks(new SnapshotStore.Snapshot(profileKey, state)));
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
			.observedAt(observedAtMs)
			.bankObservedAt(bankObservedAtMs)
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
		bankObservedAtMs = System.currentTimeMillis();
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
			bankObservedAtMs = System.currentTimeMillis();
			recordPrices(potions);
		}
	}

	private void refreshAllClientState()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		liveThisSession = true;
		observedAtMs = System.currentTimeMillis();
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

	/** Executor thread. Reads the previous session's snapshot; until the game is seen live it is what gets delivered. */
	private void loadPersisted()
	{
		SnapshotStore.Snapshot snapshot = snapshotStore == null ? null : snapshotStore.read();
		if (snapshot == null)
		{
			return;
		}
		persisted = snapshot;
		phonePolicy.restoreDelivered(snapshot.deliveredTo(TARGET_PHONE));
		if (!liveThisSession)
		{
			latestState = snapshot.getData();
			latestProfileKey = snapshot.getProfileKey();
		}
	}

	/** Executor thread, every few seconds: hands the latest state to the phone when its policy says it is due. */
	private void deliveryTick()
	{
		try
		{
			if (iconBook != null && iconBook.hasPending() && clientThread != null)
			{
				clientThread.invokeLater(iconBook::process);
			}
			deliverAll();
			if (pairingPanel != null)
			{
				pairingPanel.refresh();
			}
		}
		catch (Exception e)
		{
			log.warn("Delivery check failed", e);
		}
	}

	/** @return one future per send started now, completed when it finishes (used to hold RuneLite open on exit) */
	private List<CompletableFuture<Void>> deliverAll()
	{
		List<CompletableFuture<Void>> sends = new ArrayList<>();
		StateDTO state = latestState;
		if (config == null || !config.enableSync() || state == null)
		{
			return sends;
		}
		String token = config.syncToken();
		if (token == null || token.trim().isEmpty())
		{
			return sends;
		}
		if (config.sendToPhone())
		{
			PhoneAddress.Result address = PhoneAddress.parse(config.phoneAddress());
			if (address.problem != null)
			{
				phoneAddressProblem = address.problem;
			}
			else
			{
				deliver(phonePolicy, address, token, state, sends);
			}
		}
		return sends;
	}

	private void deliver(DeliveryPolicy policy, PhoneAddress.Result address, String token, StateDTO state,
		List<CompletableFuture<Void>> sends)
	{
		long observedAt = state.getObservedAt();
		if (!policy.shouldSend(observedAt, iconBook.hasReady()))
		{
			return;
		}
		// A name (Tailscale MagicDNS, a router name) is looked up here, on the executor, every time it is used
		PhoneAddress.Target target = PhoneAddress.target(address, InetAddress::getAllByName);
		phoneAddressProblem = target.problem;
		if (target.url == null)
		{
			policy.onOutcome(new NetworkDispatcher.Outcome(NetworkDispatcher.Outcome.Kind.UNREACHABLE, 0, 0), observedAt);
			return;
		}
		HttpUrl url = target.url;
		CompletableFuture<Void> done = new CompletableFuture<>();
		sends.add(done);
		Map<Integer, StateDTO.IconEntry> icons = iconBook.takeReady(IconBook.MAX_PER_PAYLOAD);
		StateDTO body = payload(state);
		if (!icons.isEmpty())
		{
			body = body.toBuilder().icons(icons).build();
		}
		networkDispatcher.post(url, token, gson.toJson(body), outcome ->
		{
			policy.onOutcome(outcome, observedAt);
			if (outcome.kind == NetworkDispatcher.Outcome.Kind.DELIVERED)
			{
				iconBook.markSent(icons.keySet());
				iconBook.want(outcome.needIcons);
				if (outcome.needIcons != null && !outcome.needIcons.isEmpty() && clientThread != null)
				{
					clientThread.invokeLater(iconBook::process);
				}
				executor.execute(this::saveDeliveryMarks);
			}
			if (pairingPanel != null)
			{
				pairingPanel.refresh();
			}
			done.complete(null);
		});
	}

	private StateDTO payload(StateDTO state)
	{
		return config.includeBank() ? state : PayloadFilter.withoutBank(state);
	}

	private SnapshotStore.Snapshot withMarks(SnapshotStore.Snapshot snapshot)
	{
		Map<String, Long> marks = new HashMap<>();
		marks.put(TARGET_PHONE, phonePolicy.deliveredObservedAt());
		snapshot.setDelivered(marks);
		return snapshot;
	}

	/** Executor thread: records on disk what was just delivered, so a restart doesn't send it again. */
	private void saveDeliveryMarks()
	{
		StateDTO state = latestState;
		String key = latestProfileKey;
		if (snapshotStore != null && state != null && key != null && config != null && config.enableSync())
		{
			snapshotStore.write(withMarks(new SnapshotStore.Snapshot(key, state)));
		}
	}

	private String phoneStatusHtml()
	{
		String problem = null;
		if (config.sendToPhone())
		{
			problem = PhoneAddress.parse(config.phoneAddress()).problem;
			if (problem == null)
			{
				problem = phoneAddressProblem;
			}
		}
		return PairingPanel.describe(config.enableSync(), config.sendToPhone(), problem, phonePolicy.state(),
			latestState != null, phonePolicy.lastDeliveredAt(), phonePolicy.waitingSince(), System.currentTimeMillis());
	}

	/** The state to save, or null when there is nothing worth saving (sync off, never logged in, profile unknown). */
	private SnapshotStore.Snapshot currentSnapshot()
	{
		if (snapshotStore == null || config == null || !config.enableSync() || !liveThisSession
			|| profileKey == null || priceBook == null)
		{
			return null;
		}
		return new SnapshotStore.Snapshot(profileKey, buildCurrentState());
	}

	/** Saves the state as it is right now, on the executor (plugin shutdown must not wait on the disk). */
	private void flushSnapshot()
	{
		SnapshotStore.Snapshot snapshot = currentSnapshot();
		if (snapshot != null)
		{
			executor.execute(() -> snapshotStore.write(withMarks(snapshot)));
		}
	}

	/** A small gold "P" for the sidebar button, drawn here so the plugin ships no image file. */
	private static BufferedImage panelIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xC8A24A));
		g.fillRoundRect(0, 0, 16, 16, 5, 5);
		g.setColor(new Color(0x2B2118));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.drawString("P", 4, 13);
		g.dispose();
		return image;
	}

	private void publishState()
	{
		// Serialization happens once in syncState(); serializing here ran on every container/varbit event.
		// Every caller is a game event, so this is the moment the snapshot was last true in game.
		observedAtMs = System.currentTimeMillis();
		triggerDebouncedSync();
	}
}
