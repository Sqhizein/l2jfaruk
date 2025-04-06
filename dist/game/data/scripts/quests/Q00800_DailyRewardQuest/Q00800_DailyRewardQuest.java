package quests.Q00800_DailyRewardQuest;

import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.quest.Quest;
import org.l2jmobius.gameserver.model.quest.QuestState;
import org.l2jmobius.gameserver.model.quest.State;

public class Q00800_DailyRewardQuest extends Quest
{
    private static final int REWARD_MANAGER = 37000;
    private static final int REWARD_BOX_DAY1 = 52009;
    private static final int REWARD_BOX_DAY2 = 52010;
    private static final int REWARD_BOX_DAY3 = 52011;
    private static final int REWARD_BOX_DAY4 = 52012;
    private static final int REWARD_BOX_DAY5 = 52013;
    private static final int REWARD_BOX_DAY6 = 52014;
    private static final int REWARD_BOX_DAY7 = 52015;
    private static final int MIN_LEVEL = 1;
    
    public Q00800_DailyRewardQuest()
    {
        super(800);
        addStartNpc(REWARD_MANAGER);
        addTalkId(REWARD_MANAGER);
    }
    
    @Override
    public String onEvent(String event, Npc npc, Player player)
    {
        final QuestState qs = getQuestState(player, false);
        if (qs == null)
        {
            return null;
        }
        
        String htmltext = event;
        switch (event)
        {
            case "37000-03.htm":
            {
                qs.startQuest();
                qs.set("TIME", String.valueOf(System.currentTimeMillis()));
                qs.set("DAY", "1"); // Başlangıç günü
                giveItems(player, REWARD_BOX_DAY1, 1);
                break;
            }
            case "reward":
            {
                if (qs.isCond(1))
                {
                    long lastRewardTime = Long.parseLong(qs.get("TIME"));
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - lastRewardTime;
                    
                    if (elapsedTime < 43200000) // 12 saat = 86400000 milisaniye
                    {
                        long remainingTime = 43200000 - elapsedTime;
                        long hours = remainingTime / 3600000;
                        long minutes = (remainingTime % 3600000) / 60000;
                        
                        htmltext = getHtm(player, "37000-05.htm");
                        htmltext = htmltext.replace("%time%", hours + " hours " + minutes + " minutes");
                        return htmltext;
                    }
                    
                    int currentDay = Integer.parseInt(qs.get("DAY"));
                    switch (currentDay)
                    {
                        case 1:
                            giveItems(player, REWARD_BOX_DAY2, 1);
                            qs.set("DAY", "2");
                            break;
                        case 2:
                            giveItems(player, REWARD_BOX_DAY3, 1);
                            qs.set("DAY", "3");
                            break;
                        case 3:
                            giveItems(player, REWARD_BOX_DAY4, 1);
                            qs.set("DAY", "4");
                            break;
                        case 4:
                            giveItems(player, REWARD_BOX_DAY5, 1);
                            qs.set("DAY", "5");
                            break;
                        case 5:
                            giveItems(player, REWARD_BOX_DAY6, 1);
                            qs.set("DAY", "6");
                            break;
                        case 6:
                            giveItems(player, REWARD_BOX_DAY7, 1);
                            qs.exitQuest(false, true);
                            break;
                    }
                    
                    // Zamanı güncelle
                    qs.set("TIME", String.valueOf(System.currentTimeMillis()));
                    htmltext = "37000-06.htm";
                }
                break;
            }
        }
        return htmltext;
    }
    
    @Override
    public String onTalk(Npc npc, Player player)
    {
        final QuestState qs = getQuestState(player, true);
        String htmltext = getNoQuestMsg(player);
        
        if (player.getLevel() < MIN_LEVEL)
        {
            return "37000-02.htm";
        }
        
        switch (qs.getState())
        {
            case State.CREATED:
            {
                htmltext = "37000-01.htm";
                break;
            }
            case State.STARTED:
            {
                if (qs.isCond(1))
                {
                    htmltext = "37000-04.htm";
                }
                break;
            }
            case State.COMPLETED:
            {
                if (!qs.isNowAvailable())
                {
                    htmltext = "37000-07.htm";
                }
                else
                {
                    qs.setState(State.CREATED);
                    htmltext = "37000-01.htm";
                }
                break;
            }
        }
        return htmltext;
    }
}