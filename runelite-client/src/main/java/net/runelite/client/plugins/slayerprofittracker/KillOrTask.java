package net.runelite.client.plugins.slayerprofittracker;

import lombok.Getter;

public enum KillOrTask {

    TASK("Task"),
    KILL("Kill");

    private final String name;

    KillOrTask(String name){
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
