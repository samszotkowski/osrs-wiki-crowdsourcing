package com.Crowdsourcing.loot;

import com.Crowdsourcing.CrowdsourcingManager;
import javax.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
public class CrowdsourcingLoot {
	@Inject
	Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	CrowdsourcingManager manager;

	private static final Map<Integer, String> VARBITS_CA = new HashMap<>() {
		{
			put(12863, "EASY_CA");
			put(12864, "MEDIUM_CA");
			put(12865, "HARD_CA");
			put(12866, "ELITE_CA");
			put(12867, "MASTER_CA");
			put(12868, "GRANDMASTER_CA");
		}
	};
	private static final int CA_CLAIMED = 2;

	private static final Map<Integer, String> VARBITS_CLUE_WARNINGS = new HashMap<>() {
		{
			put(10693, "BEGINNER_CLUE_DISABLED");
			put(10694, "EASY_CLUE_DISABLED");
			put(10695, "MEDIUM_CLUE_DISABLED");
			put(10723, "HARD_CLUE_DISABLED");
			put(10724, "ELITE_CLUE_DISABLED");
			put(10725, "MASTER_CLUE_DISABLED");
		}
	};
	private static final Pattern CLUE_MESSAGE = Pattern.compile("You have a sneaking suspicion.*");
	private static final int CLUE_WARNING_DISABLED = 1;

	private static final String ROGUE_MESSAGE = "Your rogue clothing allows you to steal twice as much loot!";

	private void addCasClaimedMetadata(LootData data)
	{
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, String> entry : VARBITS_CA.entrySet())
			{
				int varbitId = entry.getKey();
				String caTier = entry.getValue();
				int value = client.getVarbitValue(varbitId);
				boolean caClaimed = value == CA_CLAIMED;
				data.addMetadata(caTier, caClaimed);
			}
		});
	}

	private void addClueWarningSettingsMetadata(LootData data)
	{
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, String> entry : VARBITS_CLUE_WARNINGS.entrySet())
			{
				int varbitId = entry.getKey();
				String clueTier = entry.getValue();
				int value = client.getVarbitValue(varbitId);
				boolean warningDisabled = value == CLUE_WARNING_DISABLED;
				data.addMetadata(clueTier, warningDisabled);
			}
		});
	}

	private void addUniversalMetadata(LootData data)
	{
		data.setTick(client.getTickCount());
		data.setLocation(client.getLocalPlayer().getWorldLocation());
		addCasClaimedMetadata(data);
		addClueWarningSettingsMetadata(data);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		LootData pendingLoot = new LootData();

		String name = event.getName();
		int level = event.getCombatLevel();
		LootRecordType eventType = event.getType();

		pendingLoot.setName(name);
		pendingLoot.setCombatLevel(level);
		pendingLoot.setType(eventType);

		Collection<ItemStack> items = event.getItems();
		for (ItemStack item: items)
		{
			int itemId = item.getId();
			int quantity = item.getQuantity();
			pendingLoot.addDrop(itemId, quantity);
		}

		storeEvent(pendingLoot);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType messageType = event.getType();
		if (messageType != ChatMessageType.GAMEMESSAGE && messageType != ChatMessageType.SPAM)
		{
			return;
		}

		String message = event.getMessage();
		if (CLUE_MESSAGE.matcher(message).matches() || ROGUE_MESSAGE.equals(message))
		{
			LootData pendingData = new LootData();
			pendingData.setMessage(message);
			storeEvent(pendingData);
		}
	}

	private void storeEvent(LootData data)
	{
		addUniversalMetadata(data);
		log.debug(String.valueOf(data));
		manager.storeEvent(data);
	}
}