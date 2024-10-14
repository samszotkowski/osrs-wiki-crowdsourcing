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
    private ArrayList<HashMap<String, Integer>> drops;
    private String message;
    private WorldPoint location;
    private HashMap<String, Object> metadata;
    private int tick;

    public LootClueData()
    {
        name = null;
        combatLevel = -1;
        type = null;
        location = null;
        metadata = null;
        message = null;
        drops = null;
    }

    public void addDrop(int itemId, int quantity)
    {
        if (this.drops == null)
        {
            this.drops = new ArrayList<>();
        }

        HashMap<String, Integer> drop = new HashMap<>() {
            {
                put("id", itemId);
                put("qty", quantity);
            }
        };
        this.drops.add(drop);
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