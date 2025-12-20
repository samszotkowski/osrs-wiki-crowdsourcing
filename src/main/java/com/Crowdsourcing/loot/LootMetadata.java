package com.Crowdsourcing.loot;

import com.Crowdsourcing.util.BoatLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

public class LootMetadata
{
	private static final Map<Integer, String> VARBITS_CA = Map.of(
		VarbitID.CA_TIER_STATUS_EASY, "easy",
		VarbitID.CA_TIER_STATUS_MEDIUM, "medium",
		VarbitID.CA_TIER_STATUS_HARD, "hard",
		VarbitID.CA_TIER_STATUS_ELITE, "elite",
		VarbitID.CA_TIER_STATUS_MASTER, "master",
		VarbitID.CA_TIER_STATUS_GRANDMASTER, "grandmaster"
	);
	private static final int CA_CLAIMED = 2;

	private static final Map<Integer, String> VARBITS_CLUE_WARNINGS = Map.of(
		VarbitID.OPTION_TRAIL_REMINDER_BEGINNER, "beginner",
		VarbitID.OPTION_TRAIL_REMINDER_EASY, "easy",
		VarbitID.OPTION_TRAIL_REMINDER_MEDIUM, "medium",
		VarbitID.OPTION_TRAIL_REMINDER_HARD, "hard",
		VarbitID.OPTION_TRAIL_REMINDER_ELITE, "elite",
		VarbitID.OPTION_TRAIL_REMINDER_MASTER, "master"
	);
	private static final int CLUE_WARNING_ENABLED = 0;

	private static final List<Integer> EQUIPMENT_WHITELIST = List.of(
		ItemID.RING_OF_WEALTH,
		ItemID.RING_OF_WEALTH_I,
		ItemID.RING_OF_WEALTH_1,
		ItemID.RING_OF_WEALTH_2,
		ItemID.RING_OF_WEALTH_3,
		ItemID.RING_OF_WEALTH_4,
		ItemID.RING_OF_WEALTH_5,
		ItemID.RING_OF_WEALTH_I1,
		ItemID.RING_OF_WEALTH_I2,
		ItemID.RING_OF_WEALTH_I3,
		ItemID.RING_OF_WEALTH_I4,
		ItemID.RING_OF_WEALTH_I5
	);

	private static final int SLAYER_BOSS_TASK_ID = 98;

	private static WorldPoint getLocation(Client client)
	{
		LocalPoint local = LocalPoint.fromWorld(client, client.getLocalPlayer().getWorldLocation());
		return (local != null) ? BoatLocation.fromLocal(client, local) : null;
	}

	private static int getTick(Client client)
	{
		return client.getTickCount();
	}

	private static Map<String, Boolean> getCombatAchievements(Client client)
	{
		Map<String, Boolean> combatAchievements = new HashMap<>();
		VARBITS_CA.forEach((varbitId, caTier) ->
			combatAchievements.put(caTier, client.getVarbitValue(varbitId) == CA_CLAIMED)
		);
		return combatAchievements;
	}

	private static Map<String, Boolean> getClueWarnings(Client client)
	{
		Map<String, Boolean> clueWarnings = new HashMap<>();
		VARBITS_CLUE_WARNINGS.forEach((varbitId, clueTier) ->
			clueWarnings.put(clueTier, client.getVarbitValue(varbitId) == CLUE_WARNING_ENABLED)
		);
		return clueWarnings;
	}

	private static List<Integer> getWornItems(Client client)
	{
		List<Integer> wornItems = new ArrayList<>();

		ItemContainer equipmentContainer = client.getItemContainer(InventoryID.WORN);
		if (equipmentContainer != null)
		{
			for (int itemId : EQUIPMENT_WHITELIST)
			{
				if (equipmentContainer.contains(itemId))
				{
					wornItems.add(itemId);
				}
			}
		}
		return wornItems;
	}

	// dbRow column on https://abextm.github.io/cache2/#/viewer/dbtable/113 uniquely identifies task
	private static int getSlayerTaskDBRowID(Client client)
	{
		int taskTableDBRowID = -1;

		// Player currently has task assigned iff slayer_count > 0.
		// It is not sufficient to check slayer master and/or target, as they can be set without having a task.
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0)
		{
			int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
			if (taskId == SLAYER_BOSS_TASK_ID)
			{
				var bossRows = client.getDBRowsByValue(
					DBTableID.SlayerTaskSublist.ID,
					DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
					0,
					client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));

				if (!bossRows.isEmpty())
				{
					int bossTableDBRowID = bossRows.get(0);
					taskTableDBRowID = (int) client.getDBTableField(bossTableDBRowID, DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
				}
			}
			else
			{
				var taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
				if (!taskRows.isEmpty())
				{
					taskTableDBRowID = taskRows.get(0);
				}
			}
		}

		return taskTableDBRowID;
	}

	// see: https://oldschool.runescape.wiki/w/RuneScape:Varbit/4067
	private static int getSlayerMasterID(Client client)
	{
		int slayerMasterID = -1;

		// Player currently has task assigned iff slayer_count > 0.
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0)
		{
			slayerMasterID = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		}
		return slayerMasterID;
	}

	private static List<String> getWorldTypes(Client client)
	{
		List<String> worldTypes = new ArrayList<>();
		for (WorldType wt : client.getWorldType())
		{
			worldTypes.add(wt.toString());
		}
		if (!worldTypes.contains("MEMBERS"))
		{
			worldTypes.add("FREE");
		}
		return worldTypes;
	}

	private static int getWorldNumber(Client client)
	{
		return client.getWorld();
	}

	public static HashMap<String, Object> getMap(Client client)
	{
		return new HashMap<>() {{
			put("location", getLocation(client));
			put("tick", getTick(client));
			put("combatAchievements", getCombatAchievements(client));
			put("clueWarnings", getClueWarnings(client));
			put("wornItems", getWornItems(client));
			put("slayerTask", getSlayerTaskDBRowID(client));
			put("slayerMaster", getSlayerMasterID(client));
			put("worldTypes", getWorldTypes(client));
			put("worldNumber", getWorldNumber(client));
		}};
	}
}