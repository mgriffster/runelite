package net.runelite.client.plugins.slayer;

import java.io.Serializable;

public class ProfitEntry implements Serializable {
    private int totalGold;
    private int taskCount;
    private int killCount;

    ProfitEntry(int totalGold, int taskCount, int killCount){
        this.totalGold = totalGold;
        this.taskCount = taskCount;
        this.killCount = killCount;
    }

    ProfitEntry(){
        this.totalGold = 0;
        this.taskCount = 0;
        this.killCount = 0;
    }

    public int getTotalGold() {
        return totalGold;
    }

    public void setTotalGold(int totalGold) {
        this.totalGold = totalGold;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public int getKillCount() {
        return killCount;
    }

    public void setKillCount(int killCount) {
        this.killCount = killCount;
    }
}
