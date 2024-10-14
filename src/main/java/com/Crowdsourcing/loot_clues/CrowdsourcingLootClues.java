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
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
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

    private static final int PICKPOCKET_DELAY = 60;  // a "pickpocket > npc" click can't cause a message 60 ticks later
    private static String pickpocketTarget = null;
    private static int pickpocketClickTick = -1;

    private static final String HUNTERS_LOOT_SACK_BASIC = "Hunters' loot sack (basic)";
    private static final String HUNTERS_LOOT_SACK_ADEPT = "Hunters' loot sack (adept)";
    private static final String HUNTERS_LOOT_SACK_EXPERT = "Hunters' loot sack (expert)";
    private static final String HUNTERS_LOOT_SACK_MASTER = "Hunters' loot sack (master)";

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

    private void addSkillMetadata(Skill s, LootClueData data)
    {
        String name = s.getName();
        String boostedName = "B" + name;
        int level = client.getRealSkillLevel(s);
        int boostedLevel = client.getBoostedSkillLevel(s);
        data.addMetadata(name, level);
        data.addMetadata(boostedName, boostedLevel);
    }

    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        LootClueData pendingLoot = new LootClueData();

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

        if (eventType == LootRecordType.NPC || name.toUpperCase().startsWith("TZHAAR"))
        {
            pendingLoot.addMetadata("hasRingOfWealth", hasChargedRingOfWealth());
        }

        switch (name)
        {
            case HUNTERS_LOOT_SACK_BASIC:
            case HUNTERS_LOOT_SACK_ADEPT:
            case HUNTERS_LOOT_SACK_EXPERT:
            case HUNTERS_LOOT_SACK_MASTER:
                addSkillMetadata(Skill.HERBLORE, pendingLoot);
                addSkillMetadata(Skill.WOODCUTTING, pendingLoot);
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
                pickpocketTarget = npc.getName();
                pickpocketClickTick = client.getTickCount();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pickpocketClickTick == -1)
        {
            return;
        }

        // forget pickpocket target after 60 ticks and after hopping worlds
        int tick = client.getTickCount();
        if (tick == 0 || tick >= pickpocketClickTick + PICKPOCKET_DELAY)
        {
            pickpocketTarget = null;
            pickpocketClickTick = -1;
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
            LootClueData pendingData = new LootClueData();
            pendingData.setMessage(message);
            if (pickpocketTarget != null)
            {
                pendingData.addMetadata("lastPickpocketTarget", pickpocketTarget);
                pendingData.addMetadata("lastPickpocketClickTick", pickpocketClickTick);
            }
            storeEvent(pendingData);
            return;
        }
        if (ROGUE_MESSAGE.equals(message))
        {
            LootClueData pendingData = new LootClueData();
            pendingData.setMessage(message);
            if (pickpocketTarget != null)
            {
                pendingData.addMetadata("lastPickpocketTarget", pickpocketTarget);
                pendingData.addMetadata("lastPickpocketClickTick", pickpocketClickTick);
            }
            storeEvent(pendingData);
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
//        log.info(String.valueOf(data));
        manager.storeEvent(data);
    }
}