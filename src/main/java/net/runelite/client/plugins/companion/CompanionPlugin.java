package net.runelite.client.plugins.companion;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Companion",
	description = "Companion plugin for remote state synchronization",
	tags = {"companion", "sync", "augur"}
)
public class CompanionPlugin extends Plugin
{
	private static final int DEBOUNCE_DELAY_SECONDS = 2;
	private static final int HEARTBEAT_TICKS = 1500; // 15 minutes = 1500 ticks @ 600ms/tick

	private static final Set<Integer> TRACKED_VARBITS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		// Miscellania
		Varbits.KINGDOM_APPROVAL,
		Varbits.KINGDOM_COFFER,

		// Daily resets
		Varbits.DAILY_STAVES_COLLECTED,
		Varbits.DAILY_HERB_BOXES_COLLECTED,
		Varbits.QUEST_TEARS_OF_GUTHIX,
		Varbits.DAILY_ESSENCE_COLLECTED,
		Varbits.DAILY_RUNES_COLLECTED,
		Varbits.DAILY_SAND_COLLECTED,
		Varbits.DAILY_FLAX_STATE,
		Varbits.DAILY_ARROWS_STATE,

		// Farming patch states
		Varbits.FARMING_4771,
		Varbits.FARMING_4772,
		Varbits.FARMING_4773,
		Varbits.FARMING_4774,
		Varbits.FARMING_4775,
		Varbits.FARMING_7904,
		Varbits.FARMING_7905,
		Varbits.FARMING_7906,
		Varbits.FARMING_7907,
		Varbits.FARMING_7908,
		Varbits.FARMING_7909,
		Varbits.FARMING_7910,
		Varbits.FARMING_7911,
		Varbits.FARMING_7912,
		Varbits.AUTOWEED,

		// Grapes
		Varbits.GRAPES_4953,
		Varbits.GRAPES_4954,
		Varbits.GRAPES_4955,
		Varbits.GRAPES_4956,
		Varbits.GRAPES_4957,
		Varbits.GRAPES_4958,
		Varbits.GRAPES_4959,
		Varbits.GRAPES_4960,
		Varbits.GRAPES_4961,
		Varbits.GRAPES_4962,
		Varbits.GRAPES_4963,
		Varbits.GRAPES_4964
	)));

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private CompanionConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private NetworkDispatcher networkDispatcher;

	@Inject
	private ScheduledExecutorService executor;

	private List<StateDTO.ItemEntry> currentInventory = new ArrayList<>();
	private List<StateDTO.ItemEntry> currentBank = new ArrayList<>();
	private final Map<Integer, Integer> currentVarbits = new ConcurrentHashMap<>();

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
		if (gson == null)
		{
			gson = new Gson();
		}
		if (okHttpClient == null)
		{
			okHttpClient = new OkHttpClient();
		}
		if (networkDispatcher == null)
		{
			networkDispatcher = new NetworkDispatcher(okHttpClient, gson);
		}
		if (executor == null)
		{
			executor = Executors.newSingleThreadScheduledExecutor();
		}

		log.info("Companion plugin started");
		refreshAllVarbits();
		publishState();
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (debounceTask != null && !debounceTask.isDone())
		{
			debounceTask.cancel(false);
		}
		currentInventory.clear();
		currentBank.clear();
		currentVarbits.clear();
		tickCounter = 0;
		log.info("Companion plugin stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			tickCounter = 0;
			refreshAllVarbits();
			syncState();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCounter++;
		if (tickCounter >= HEARTBEAT_TICKS)
		{
			tickCounter = 0;
			syncState();
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
		if (containerId == 93 || containerId == InventoryID.INVENTORY.getId())
		{
			updateInventory(event.getItemContainer());
			publishState();
		}
		else if (containerId == 95 || containerId == InventoryID.BANK.getId())
		{
			updateBank(event.getItemContainer());
			publishState();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbitId = event.getVarbitId();
		if (varbitId != -1)
		{
			if (TRACKED_VARBITS.contains(varbitId))
			{
				currentVarbits.put(varbitId, event.getValue());
				publishState();
			}
		}
		else if (client != null)
		{
			boolean changed = false;
			for (int trackedId : TRACKED_VARBITS)
			{
				try
				{
					int val = client.getVarbitValue(trackedId);
					Integer prev = currentVarbits.put(trackedId, val);
					if (prev == null || prev != val)
					{
						changed = true;
					}
				}
				catch (Exception ignored)
				{
				}
			}
			if (changed)
			{
				publishState();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("slayer".equals(event.getGroup()) || "runelite.slayer".equals(event.getGroup()))
		{
			publishState();
		}
	}

	private boolean isBankOrInterface(int groupId)
	{
		return groupId == WidgetID.BANK_GROUP_ID
			|| groupId == 12  // Bank main
			|| groupId == 15  // Bank inventory
			|| groupId == 192 // Bank deposit box
			|| groupId == 465 // Grand Exchange
			|| groupId == 631; // Seed vault
	}

	private synchronized void triggerDebouncedSync()
	{
		if (debounceTask != null && !debounceTask.isDone())
		{
			debounceTask.cancel(false);
		}
		if (executor == null)
		{
			executor = Executors.newSingleThreadScheduledExecutor();
		}
		debounceTask = executor.schedule(this::syncState, DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	private void syncState()
	{
		if (config == null || !config.enableSync())
		{
			log.debug("Sync skipped: enableSync is disabled in configuration");
			return;
		}

		String endpoint = config.apiEndpoint();
		if (endpoint == null || endpoint.trim().isEmpty())
		{
			log.warn("Sync skipped: apiEndpoint is not configured");
			return;
		}

		StateDTO state = buildCurrentState();
		if (networkDispatcher != null)
		{
			networkDispatcher.dispatch(endpoint, config.authToken(), state);
		}
	}

	private StateDTO buildCurrentState()
	{
		return StateDTO.builder()
			.inventory(new ArrayList<>(currentInventory))
			.bank(new ArrayList<>(currentBank))
			.varbits(new HashMap<>(currentVarbits))
			.slayer(extractSlayerState())
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
				items.add(new StateDTO.ItemEntry(item.getId(), item.getQuantity()));
			}
		}
		currentInventory = items;
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
	}

	private Map<String, Object> extractSlayerState()
	{
		if (configManager == null)
		{
			return Collections.emptyMap();
		}

		Map<String, Object> slayer = new HashMap<>();
		String taskName = configManager.getConfiguration("slayer", "taskName");
		if (taskName == null)
		{
			taskName = configManager.getConfiguration("runelite.slayer", "taskName");
		}

		String amountStr = configManager.getConfiguration("slayer", "amount");
		if (amountStr == null)
		{
			amountStr = configManager.getConfiguration("runelite.slayer", "amount");
		}

		if (taskName != null && !taskName.isEmpty())
		{
			slayer.put("taskName", taskName);
		}

		if (amountStr != null && !amountStr.isEmpty())
		{
			try
			{
				slayer.put("amount", Integer.parseInt(amountStr));
			}
			catch (NumberFormatException e)
			{
				slayer.put("amount", amountStr);
			}
		}

		return slayer;
	}

	private void publishState()
	{
		StateDTO state = buildCurrentState();
		if (gson == null)
		{
			gson = new Gson();
		}
		String json = gson.toJson(state);
		log.info("StateDTO: {}", json);
	}
}
