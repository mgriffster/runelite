package net.runelite.client.plugins.slayer;

import java.util.List;

public enum ExcludedItems {

    INCLUDE_ALL(null, "ALL"),
    NO_LOW_BONES(SlayerPlugin.lowBones,"NO LOW BONES"),
    NO_BONES_AT_ALL(SlayerPlugin.allBones, "NO BONES");

    //list of Item IDs that are blocked
    private List<Integer> excludeList;
    private String description;

    ExcludedItems(List<Integer> excludeList, String description){
        this.excludeList = excludeList;
        this.description = description;
    }

    public List<Integer> getExcludeList() {
        return excludeList;
    }

    public String getDescription() {
        return description;
    }
}
