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
}
