package com.Crowdsourcing.loot_clues;

import com.Crowdsourcing.CrowdsourcingManager;
import javax.inject.Inject;
import java.util.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
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
    ClientThread clientThread;

    @Inject
    CrowdsourcingManager manager;

    @Inject
    ItemManager itemManager;

    private static final Map<Integer, String> VARBITS_CA = new HashMap<>() {
        {
            put(12863, "EASY");
            put(12864, "MEDIUM");
            put(12865, "HARD");
            put(12866, "ELITE");
            put(12867, "MASTER");
            put(12868, "GRANDMASTER");
        }
    };
    private static final int CA_CLAIMED = 2;

    private static final Map<Integer, String> VARBITS_CLUE_WARNINGS = new HashMap<>() {
        {
            put(10693, "BEGINNER");
            put(10694, "EASY");
            put(10695, "MEDIUM");
            put(10723, "HARD");
            put(10724, "ELITE");
            put(10725, "MASTER");
        }
    };
    private static final Pattern CLUE_MESSAGE = Pattern.compile("You have a sneaking suspicion.*");
    private static final int CLUE_WARNING_DISABLED = 1;

    private static final String ROGUE_MESSAGE = "Your rogue clothing allows you to steal twice as much loot!";

    // state
    private String pickpocketTarget = null;
    private LootClueData pendingLoot = new LootClueData();
    private boolean lootReceived = false;

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

    private List<String> getCasClaimed()
    {
        List<String> casClaimed = new ArrayList<>();
        clientThread.invoke(() -> {
            for (Map.Entry<Integer, String> entry : VARBITS_CA.entrySet())
            {
                int varbitId = entry.getKey();
                String caTier = entry.getValue();
                int value = client.getVarbitValue(varbitId);
                boolean caClaimed = value == CA_CLAIMED;
                if (caClaimed) {
                    casClaimed.add(caTier);
                }
            }
        });
        return casClaimed;
    }

    private List<String> getClueWarningSettings()
    {
        List<String> disabledClueWarnings = new ArrayList<>();
        clientThread.invoke(() -> {
            for (Map.Entry<Integer, String> entry : VARBITS_CLUE_WARNINGS.entrySet())
            {
                int varbitId = entry.getKey();
                String clueTier = entry.getValue();
                int value = client.getVarbitValue(varbitId);
                boolean warningDisabled = value == CLUE_WARNING_DISABLED;
                if (warningDisabled) {
                    disabledClueWarnings.add(clueTier);
                }
            }
        });
        return disabledClueWarnings;
    }

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        String name = event.getName();
        int combatLevel = event.getCombatLevel();
        LootRecordType type = event.getType();

        if (type == LootRecordType.NPC || name.toUpperCase().startsWith("TZHAAR"))
        {
            pendingLoot.addMetadata("hasRingOfWealth", hasChargedRingOfWealth());
        }

        ArrayList<HashMap<String, Integer>> drops = new ArrayList<>();
        Collection<ItemStack> items = event.getItems();
        for (ItemStack item: items)
        {
            int itemId = item.getId();
            String itemName = itemManager.getItemComposition(itemId).getName();
            int quantity = item.getQuantity();
            pendingLoot.addDrop(itemName, quantity);
        }

        pendingLoot.setName(name);
        pendingLoot.setCombatLevel(combatLevel);
        pendingLoot.setType(type);
        lootReceived = true;
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
        if (CLUE_MESSAGE.matcher(message).matches())
        {
            pendingLoot.addMessage(message);
            lootReceived = true;
        }
        if (ROGUE_MESSAGE.equals(message))
        {
            pendingLoot.addMessage(ROGUE_MESSAGE);
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
                pickpocketTarget = npc.getName();
                return;
            }
        }
        pickpocketTarget = null;
    }

    private void reset()
    {
        pendingLoot = new LootClueData();
        lootReceived = false;
    }

    public void addUniversalMetadata()
    {
        pendingLoot.setLocation(client.getLocalPlayer().getWorldLocation());
        pendingLoot.addMetadata("combatAchievements", getCasClaimed());
        pendingLoot.addMetadata("clueWarningsDisabled", getClueWarningSettings());
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!lootReceived)
        {
            return;
        }

        if (pickpocketTarget != null)
        {
            pendingLoot.setName(pickpocketTarget);
            pendingLoot.setCombatLevel(-1);
            pendingLoot.setType(LootRecordType.PICKPOCKET);
            // do not nullify pickpocketTarget here in case of auto-pickpocket wealthy citizens
        }

        addUniversalMetadata();
        log.info(String.valueOf(pendingLoot));
//        manager.storeEvent(pendingLoot);
        reset();
    }
}