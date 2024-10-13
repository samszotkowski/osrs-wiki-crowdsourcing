package com.Crowdsourcing.loot_clues;

import com.Crowdsourcing.CrowdsourcingManager;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
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
        LootClueData pendingLoot = new LootClueData();

        pendingLoot.setName(event.getName());
        pendingLoot.setCombatLevel(event.getCombatLevel());
        pendingLoot.setType(event.getType().name());

        Collection<ItemStack> items = event.getItems();
        for (ItemStack item: items)
        {
            int itemId = item.getId();
            int quantity = item.getQuantity();
            pendingLoot.addDrop(itemId, quantity);
        }

        if (event.getType() == LootRecordType.NPC || event.getName().toUpperCase().startsWith("TZHAAR"))
        {
            pendingLoot.addMetadata("hasRingOfWealth", hasChargedRingOfWealth());
        }

        storeEvent(pendingLoot);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getMenuOption().equals("Pickpocket"))
        {
            NPC npc = event.getMenuEntry().getNpc();
            if (npc != null)
            {
                LootClueData pendingLoot = new LootClueData();
                pendingLoot.setType("CLICK_PICKPOCKET");
                pendingLoot.setName(npc.getName());
                storeEvent(pendingLoot);
            }
        }
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
            LootClueData pendingLoot = new LootClueData();
            pendingLoot.setMessage(message);
            storeEvent(pendingLoot);
            return;
        }
        if (ROGUE_MESSAGE.equals(message))
        {
            LootClueData pendingLoot = new LootClueData();
            pendingLoot.setMessage(message);
            storeEvent(pendingLoot);
        }
    }

    public void addUniversalMetadata(LootClueData data)
    {
        data.setTick(client.getTickCount());
        data.setLocation(client.getLocalPlayer().getWorldLocation());
        data.addMetadata("combatAchievements", getCasClaimed());
        data.addMetadata("clueWarningsDisabled", getClueWarningSettings());
    }

    public void storeEvent(LootClueData data)
    {
        addUniversalMetadata(data);
        log.info(String.valueOf(data));
//        manager.storeEvent(data);
    }
}