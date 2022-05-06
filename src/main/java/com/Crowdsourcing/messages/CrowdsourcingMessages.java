package com.Crowdsourcing.messages;

import com.Crowdsourcing.CrowdsourcingManager;
import java.util.HashMap;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class CrowdsourcingMessages
{
	@Inject
	private CrowdsourcingManager manager;

	@Inject
	private Client client;

	// Make sure anything here ONLY matches what you want
	private static final String CAIRN_ISLE_SUCCESS = "You manage to keep your balance on the bridge.";
	private static final String CAIRN_ISLE_FAIL = "You fall";

	// Ardy log shortcut, log NW of sinclair mansion, Karamja east log
	private static final String LOG_SUCCESS = "You make it across the log without any problems.";
	private static final String LOG_FAIL = "You lose your footing and fall into the water.";

	// Rock jumps in lumbridge caves
	private static final String STEPPING_STONE_ATTEMPT = "You leap across with a mighty leap!";
	private static final String STEPPING_STONE_FAIL = "You slip over on the slimy stone.";

	// Lava dragon scales, store herblore level and hard diary completion
	private static final int HARD_WILDERNESS_DIARY_VARBIT = 4509;
	private static final Pattern LAVA_DRAGON_SCALE_GRIND = Pattern.compile("You grind the lava dragon scale into \\d shards\\.");

	// Sacred eel -> Zulrah scales, cooking level
	private static final Pattern SACRED_EEL_DISSECTION = Pattern.compile("You dissect the eel carcass and extract \\d scales\\.");

	// Undead twigs, presumably add WC level
	private static final String UNDEAD_TWIGS_SUCCESS = "You cut some undead twigs.";
	private static final String UNDEAD_TWIGS_FAIL = "You almost remove a suitable twig, but you don't quite manage it.";

	// Wire machine, presumably add Thieving level
	private static final String WIRE_MACHINE_SUCCESS = "You grab a piece of wire.";
	private static final String WIRE_MACHINE_FAIL = "You catch your hand in the mechanism.";

	// Vyre distraction
	private static final String VYRE_DISTRACTION_SUCCESS = "You manage to distract the vampyre and sneak away.";
	private static final String VYRE_DISTRACTION_FAIL = "You failed to distract the vampyre.";

	// Zogre coffins (need to do some more checking here, lockpick might complicate things and need to check message uniqueness)
	private static final String ZOGRE_COFFIN_SUCCESS = "You unlock the coffin...";
	private static final String ZOGRE_COFFIN_FAIL = "You fail to pick the lock - your fingers get numb from fumbling with the lock.";
	private static final String ZOGRE_COFFIN_LOCKPICK_SNAPS = "Your lockpick snaps.";

	// Viyeldi Caves rock mining
	private static final String VIYELDI_ROCK_MINING_SUCCESS = "You manage to smash the rock to bits.";
	private static final String VIYELDI_ROCK_MINING_FAIL = "The pick clangs heavily against the rock face and the vibrations rattle your nerves. ";

	// Viyeldi Caves jagged wall
	private static final String VIYELDI_JAGGED_WALL_SUCCESS = "You take a good run up and sail majestically over the wall.";
	private static final String VIYELDI_JAGGED_WALL_FAIL = "You fail to jump the wall properly and clip the wall with your leg.";

	// Stealing entrana candles
	private static final String ENTRANA_CANDLE_SUCCESS = "You steal a candle.";
	private static final String ENTRANA_CANDLE_FAIL = "A higher power smites you.";


	private void addSkillToMap(HashMap<Object, Object> h, Skill s)
	{
		h.put(s.getName(), client.getRealSkillLevel(s));
		h.put("B" + s.getName(), client.getBoostedSkillLevel(s));
	}

	public HashMap<Object, Object> getMetadataForMessage(String message)
	{
		// For each message, check if we need to add metadata. If so, add it to the hashmap to be returned.
		HashMap<Object, Object> h = new HashMap<>();

		// Should these just be a bunch of ImmutableSets and checks on contains?
		if (CAIRN_ISLE_SUCCESS.equals(message) || CAIRN_ISLE_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.AGILITY);
		}

		if (LOG_SUCCESS.equals(message) || LOG_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.AGILITY);
		}

		if (STEPPING_STONE_ATTEMPT.equals(message) || STEPPING_STONE_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.AGILITY);
		}

		if (LAVA_DRAGON_SCALE_GRIND.matcher(message).matches())
		{
			addSkillToMap(h, Skill.HERBLORE);
			h.put("Diarycomplete", client.getVarbitValue(HARD_WILDERNESS_DIARY_VARBIT));
		}

		if (SACRED_EEL_DISSECTION.matcher(message).matches())
		{
			addSkillToMap(h, Skill.COOKING);
		}

		if (UNDEAD_TWIGS_SUCCESS.equals(message) || UNDEAD_TWIGS_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.WOODCUTTING);
		}

		if (WIRE_MACHINE_SUCCESS.equals(message) || WIRE_MACHINE_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.THIEVING);
		}

		if (VYRE_DISTRACTION_SUCCESS.equals(message) || VYRE_DISTRACTION_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.THIEVING);
		}

		if (ZOGRE_COFFIN_SUCCESS.equals(message) || ZOGRE_COFFIN_FAIL.equals(message) || ZOGRE_COFFIN_LOCKPICK_SNAPS.equals(message))
		{
			boolean hasLockpick = false;
			boolean hasHairClip = false;
			ItemContainer equipContainer = client.getItemContainer(InventoryID.INVENTORY);
			if (equipContainer != null)
			{
				final Item[] items = equipContainer.getItems();
				for (Item item : items)
				{
					if (item.getId() == ItemID.LOCKPICK)
						hasLockpick = true;
					else if (item.getId() == ItemID.HAIR_CLIP)
						hasHairClip = true;
				}
			}
			h.put("Lockpick", hasLockpick);
			h.put("Hairclip", hasHairClip);
			addSkillToMap(h, Skill.THIEVING);
		}

		if (VIYELDI_ROCK_MINING_SUCCESS.equals(message) || VIYELDI_ROCK_MINING_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.MINING);
		}

		if (VIYELDI_JAGGED_WALL_SUCCESS.equals(message) || VIYELDI_JAGGED_WALL_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.AGILITY);
		}

		if (ENTRANA_CANDLE_SUCCESS.equals(message) || ENTRANA_CANDLE_FAIL.equals(message))
		{
			addSkillToMap(h, Skill.THIEVING);
		}

		return h;
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = chatMessage.getMessage();
		if (client == null || client.getLocalPlayer() == null)
		{
			return;
		}
		LocalPoint local = LocalPoint.fromWorld(client, client.getLocalPlayer().getWorldLocation());
		if (local == null)
		{
			return;
		}
		WorldPoint location = WorldPoint.fromLocalInstance(client, local);
		boolean isInInstance = client.isInInstancedRegion();
		HashMap<Object, Object> metadata = getMetadataForMessage(message);
		MessagesData data = new MessagesData(message, isInInstance, location, metadata);
		log.debug("" + data);
		manager.storeEvent(data);
	}
}
