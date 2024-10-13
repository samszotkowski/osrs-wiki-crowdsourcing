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

    // metadata
    private Set<String> casClaimed;
    private Set<String> disabledClueWarnings;

    // state
    private String pickpocketTarget;
    private LootClueData pendingLoot;
    private boolean lootReceived;

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        int varbitId = varbitChanged.getVarbitId();
        if (VARBITS_CA.containsKey(varbitId))
        {
            if (casClaimed == null)
            {
                casClaimed = new HashSet<>();
            }

            String caTier = VARBITS_CA.get(varbitId);
            int newValue = varbitChanged.getValue();
            boolean caClaimed = newValue == CA_CLAIMED;
            if (caClaimed)
            {
                casClaimed.add(caTier);
            }
            else
            {
                casClaimed.remove(caTier);
            }

            if (casClaimed.isEmpty())
            {
                casClaimed = null;
            }
            log.info("Combat achievements: " + casClaimed);
        }
        if (VARBITS_CLUE_WARNINGS.containsKey(varbitId))
        {
            if (disabledClueWarnings == null)
            {
                disabledClueWarnings = new HashSet<>();
            }

            String clueTier = VARBITS_CLUE_WARNINGS.get(varbitId);
            int newValue = varbitChanged.getValue();
            boolean warningDisabled = newValue == CLUE_WARNING_DISABLED;
            if (warningDisabled)
            {
                disabledClueWarnings.add(clueTier);
            }
            else
            {
                disabledClueWarnings.remove(clueTier);
            }

            if (disabledClueWarnings.isEmpty())
            {
                disabledClueWarnings = null;
            }
            log.info("Clue warnings disabled: " + disabledClueWarnings);
        }
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
            pendingLoot.addMetadata("rogueEquipmentDoubled", true);
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
        pickpocketTarget = null;
        pendingLoot = new LootClueData();
        lootReceived = false;
        casClaimed = null;
        disabledClueWarnings = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            reset();
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

    public void addUniversalMetadata()
    {
        pendingLoot.setLocation(client.getLocalPlayer().getWorldLocation());
        pendingLoot.addMetadata("combatAchievements", casClaimed);
        pendingLoot.addMetadata("clueWarningsDisabled", disabledClueWarnings);
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
        }

        addUniversalMetadata();
        log.info(String.valueOf(pendingLoot));
//        manager.storeEvent(pendingLoot);
        pendingLoot = new LootClueData();
        lootReceived = false;
    }
}