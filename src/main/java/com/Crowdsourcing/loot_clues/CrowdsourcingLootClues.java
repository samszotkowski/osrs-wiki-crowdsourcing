package com.Crowdsourcing.loot_clues;

import com.Crowdsourcing.CrowdsourcingManager;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
public class CrowdsourcingLootClues {
    @Inject
    Client client;

    @Inject
    CrowdsourcingManager manager;

    @Inject
    ItemManager itemManager;

    private static final List<Integer> VARBITS_CA = new ArrayList<>() {
        {
            add(12863);  // easy
            add(12864);  // medium
            add(12865);  // hard
            add(12866);  // elite
            add(12867);  // master
            add(12868);  // grandmaster
        }
    };
    private static final List<String> CA_TIERS = new ArrayList<>() {
        {
            add("EASY");
            add("MEDIUM");
            add("HARD");
            add("ELITE");
            add("MASTER");
            add("GRANDMASTER");
        }
    };
    private static final int CA_CLAIMED = 2;

    private static final List<Integer> VARBITS_CLUE_WARNINGS = new ArrayList<>() {
        {
            add(10693);  // beginner
            add(10694);  // easy
            add(10695);  // medium
            add(10723);  // hard
            add(10724);  // elite
            add(10725);  // master
        }
    };
    private static final List<String> CLUE_TIERS = new ArrayList<>() {
        {
            add("BEGINNER");
            add("EASY");
            add("MEDIUM");
            add("HARD");
            add("ELITE");
            add("MASTER");
        }
    };
    private static final Pattern CLUE_MESSAGE = Pattern.compile("You have a sneaking suspicion.*");

    private int highestCaIndexClaimed = -1;
    private final HashMap<String, Boolean> enabledClueWarnings = new HashMap<>() {
        {
            put("BEGINNER", true);
            put("EASY", true);
            put("MEDIUM", true);
            put("HARD", true);
            put("ELITE", true);
            put("MASTER", true);
        }
    };

    private LootClueData pendingLoot = null;
    private ArrayList<String> pendingMessages = new ArrayList<>();
    private String prevLooted = "";

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        int varbitId = varbitChanged.getVarbitId();
        if (VARBITS_CA.contains(varbitId))
        {
            int index = VARBITS_CA.indexOf(varbitId);
            int newValue = varbitChanged.getValue();
            if (newValue == CA_CLAIMED && index > highestCaIndexClaimed)
            {
                highestCaIndexClaimed = index;
//                log.info("Highest CA: " + CA_TIERS.get(highestCaIndexClaimed));
            }
        }
        if (VARBITS_CLUE_WARNINGS.contains(varbitId))
        {
            int index = VARBITS_CLUE_WARNINGS.indexOf(varbitId);
            String clueTier = CLUE_TIERS.get(index);
            int newValue = varbitChanged.getValue();
            Boolean warningEnabled = newValue == 0;
            enabledClueWarnings.put(clueTier, warningEnabled);
//            log.info("Clue warnings enabled: " + enabledClueWarnings);
        }
    }

    private boolean hasChargedRingOfWealth()
    {
        ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipmentContainer == null) {
            return false;
        }
        return equipmentContainer.contains(ItemID.RING_OF_WEALTH_1) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_2) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_3) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_4) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_5) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_I1) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_I2) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_I3) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_I4) ||
            equipmentContainer.contains(ItemID.RING_OF_WEALTH_I5);
    }

    public HashMap<String, Object> createMetadata()
    {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("highestCombatAchievement", CA_TIERS.get(highestCaIndexClaimed));
        metadata.put("clueWarningsEnabled", enabledClueWarnings);
        metadata.put("hasRingOfWealth", hasChargedRingOfWealth());
        return metadata;
    }

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        String name = event.getName();
        prevLooted = name;

        int combatLevel = event.getCombatLevel();
        LootRecordType type = event.getType();

        ArrayList<HashMap<String, Integer>> drops = new ArrayList<>();
        Collection<ItemStack> items = event.getItems();
        for (ItemStack item: items)
        {
            int itemId = item.getId();
            String itemName = itemManager.getItemComposition(itemId).getName();
            int quantity = item.getQuantity();
            HashMap<String, Integer> drop = new HashMap<>() {
                {
                    put(itemName, quantity);
                }
            };
            drops.add(drop);
        }

        pendingLoot = new LootClueData(name, combatLevel, type, null, null, null, drops);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {

        if (event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        String message = event.getMessage();
        if (CLUE_MESSAGE.matcher(message).matches())
        {
            pendingMessages.add(message);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getMenuOption().equals("Pickpocket"))
        {
            NPC npc = event.getMenuEntry().getNpc();
            if (npc != null)
            {
                prevLooted = npc.getName();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pendingLoot == null && pendingMessages.isEmpty())
        {
            return;
        }
        if (pendingLoot != null && !pendingMessages.isEmpty())
        {
            pendingLoot.setMessages(pendingMessages);
        }
        if (pendingLoot == null)
        {
            pendingLoot = new LootClueData(
                prevLooted, -1, LootRecordType.PICKPOCKET, null, null, pendingMessages, null
            );
        }

        pendingLoot.setLocation(client.getLocalPlayer().getWorldLocation());
        pendingLoot.setMetadata(createMetadata());

//        log.info(String.valueOf(pendingLoot));
        manager.storeEvent(pendingLoot);
        pendingLoot = null;
        pendingMessages = new ArrayList<>();
    }
}