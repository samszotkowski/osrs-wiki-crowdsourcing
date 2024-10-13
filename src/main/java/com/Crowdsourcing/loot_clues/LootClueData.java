package com.Crowdsourcing.loot_clues;

import java.util.ArrayList;
import java.util.HashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.api.coords.WorldPoint;
import net.runelite.http.api.loottracker.LootRecordType;

@Data
@AllArgsConstructor
public class LootClueData {
    private String name;
    private int combatLevel;
    private LootRecordType type;
    private WorldPoint location;
    private HashMap<String, Object> metadata;
    private ArrayList<String> messages;
    private ArrayList<HashMap<String, Integer>> drops;

    public LootClueData()
    {
        name = null;
        combatLevel = -1;
        type = null;
        location = null;
        metadata = null;
        messages = null;
        drops = null;
    }

    public void addDrop(String itemName, int quantity)
    {
        if (this.drops == null)
        {
            this.drops = new ArrayList<>();
        }

        HashMap<String, Integer> drop = new HashMap<>() {
            {
                put(itemName, quantity);
            }
        };
        this.drops.add(drop);
    }

    public void addMessage(String message)
    {
        if (this.messages == null)
        {
            this.messages = new ArrayList<>();
        }

        this.messages.add(message);
    }

    public void addMetadata(String key, Object value)
    {
        if (this.metadata == null)
        {
            this.metadata = new HashMap<>();
        }

        this.metadata.put(key, value);
    }
}