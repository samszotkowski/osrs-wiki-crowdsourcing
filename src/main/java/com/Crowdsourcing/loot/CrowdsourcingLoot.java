package com.Crowdsourcing.loot;

import com.Crowdsourcing.CrowdsourcingManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
public class CrowdsourcingLoot
{
	@Inject
	Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	CrowdsourcingManager manager;

	// Clues
	private static final Pattern CLUE_WARNING_MESSAGE = Pattern.compile("You have a sneaking suspicion.*");

	// Fishing/mining standard messages
	// "You catch a swordfish.", "You catch some shrimps.", "You catch a shark!", "You catch a scroll box!"
	private static final Pattern FISHING_PATTERN = Pattern.compile("You catch.*");
	private static final Pattern MINING_PATTERN = Pattern.compile("You manage to mine.*");
	private static final Pattern WOODCUTTING_PATTERN = Pattern.compile("You get some.*logs.*");
	private static final String MINING_CLUE_MESSAGE = "You find a scroll box!";
	private static final String MOON_KEY_MESSAGE = "You find a key half!";
	private static final String BONE_SHARD_MESSAGE = "You manage to chip off some bone shards.";
	private static final String ENT_SEED_MESSAGE = "An ent seed falls out of the tree!";

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		List<Map<String, Integer>> drops = new ArrayList<>();
		event.getItems().forEach(item ->
			drops.add(Map.of(
				"id", item.getId(),
				"qty", item.getQuantity()
			))
		);

		clientThread.invokeLater(() ->
			manager.storeEvent(new LootData(
				event.getType(),
				event.getName(),
				drops,
				event.getAmount(),
				"",
				LootMetadata.getMap(client, event.getMetadata())
			))
		);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
		{
			return;
		}

		String message = event.getMessage();
		if (CLUE_WARNING_MESSAGE.matcher(message).matches() ||
			FISHING_PATTERN.matcher(message).matches() ||
			MINING_PATTERN.matcher(message).matches() ||
			WOODCUTTING_PATTERN.matcher(message).matches() ||
			MINING_CLUE_MESSAGE.equals(message) ||
			MOON_KEY_MESSAGE.equals(message) ||
			BONE_SHARD_MESSAGE.equals(message) ||
			ENT_SEED_MESSAGE.equals(message))
		{
			clientThread.invokeLater(() ->
				manager.storeEvent(new LootData(
					LootRecordType.UNKNOWN,
					"MESSAGE",
					Collections.emptyList(),
					-1,
					message,
					LootMetadata.getMap(client, -1)
				))
			);
		}
	}
}