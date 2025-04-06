/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package quests.GlobalMonsterHunt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.Config;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.quest.Quest;
import org.l2jmobius.gameserver.model.quest.QuestState;
import org.l2jmobius.gameserver.network.serverpackets.ExShowScreenMessage;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.Broadcast;

/**
 * Global Monster Hunt Event
 */
public class GlobalMonsterHunt extends Quest
{
    private static final Logger LOGGER = Logger.getLogger(GlobalMonsterHunt.class.getName());
    
    // Constants
    private static final int QUEST_ID = 1900;
    private static final int EVENT_DURATION = 3600000; // 1 saat (milisaniye)
    private static final int EVENT_INTERVAL = 14400000; // 4 saat (milisaniye)
    private static final float EXP_RATE_BONUS = 0.25f; // % exp bonus
    private static final float DROP_RATE_BONUS = 0.25f; // % drop bonus
    private static final int NPC_ID = 37001; // Event NPC ID
    private static final int SPECIAL_ITEM_ID = 6673; // Özel düşecek item ID
    private static final int SPECIAL_ITEM_COUNT = 1; // Düşecek item miktarı
    private static final double SPECIAL_DROP_CHANCE = 25.0; // % özel drop şansı
    private static final String[] GM_COMMANDS = {"start_event", "stop_event", "reset_counts"};
    
    // Variables
    private static int requiredKills = 0;
    private static int currentKills = 0;
    private static boolean isEventActive = false;
    private static final float ORIGINAL_XP_RATE = (float) Config.RATE_XP;
    private static final float ORIGINAL_DROP_RATE = (float) Config.RATE_DEATH_DROP_CHANCE_MULTIPLIER;
    
    public GlobalMonsterHunt()
    {
        super(QUEST_ID);
        
        // Tüm monster ID'lerini al
        int[] monsterIds = getAllMonsterId();
        LOGGER.info("Registering kill IDs: " + Arrays.toString(monsterIds));
        
        // NPC ve Kill eventlerini ekle
        addStartNpc(NPC_ID);
        addFirstTalkId(NPC_ID);
        addTalkId(NPC_ID);
        
        // Her bir monster ID için kill eventi ekle
        if (monsterIds.length > 0)
        {
            for (int id : monsterIds)
            {
                addKillId(id);
                LOGGER.info("Added kill ID: " + id);
            }
        }
        else
        {
            // Eğer hiç monster ID yoksa, tüm monsterları ekle
            addKillId(20001, 20002, 20003);
            LOGGER.info("No specific monster IDs found, using default monsters");
        }
        
        calculateRequiredKills();
        startQuestTimer("CALCULATE_REQUIRED_KILLS", 60000, null, null, true);
        startQuestTimer("START_EVENT_CYCLE", 1000, null, null, false);
        
        LOGGER.info("Global Monster Hunt Event: Initialized with " + requiredKills + " required kills");
    }
    
    @Override
    public String onAdvEvent(String event, Npc npc, Player player)
    {
        if (player != null && Arrays.asList(GM_COMMANDS).contains(event))
        {
            if (!player.isGM())
            {
                player.sendMessage("Only GMs can use these commands.");
                return null;
            }
            
            switch (event)
            {
                case "start_event":
                    if (!isEventActive)
                    {
                        isEventActive = true;
                        currentKills = 0;
                        calculateRequiredKills();
                        
                        // Apply EXP and Drop bonus
                        Config.RATE_XP = ORIGINAL_XP_RATE * (1 + EXP_RATE_BONUS);
                        Config.RATE_DEATH_DROP_CHANCE_MULTIPLIER = ORIGINAL_DROP_RATE * (1 + DROP_RATE_BONUS);
                        
                        // Announce event start
                        String startMessage = "Global Monster Hunt Event has started! EXP and Drop rates increased by 25% for 1 hour!";
                        Broadcast.toAllOnlinePlayers(startMessage);
                        if (player != null)
                        {
                            player.sendMessage(startMessage);
                            player.sendPacket(new ExShowScreenMessage(startMessage, 5000));
                        }
                        
                        // Schedule event end
                        startQuestTimer("STOP_EVENT", EVENT_DURATION, null, null, false);
                        
                        // Update NPC dialog
                        showInfo(player);
                        
                        LOGGER.info("Global Monster Hunt Event started manually by GM: " + player.getName());
                    }
                    else
                    {
                        player.sendMessage("Event is already active!");
                    }
                    break;
                    
                case "stop_event":
                    if (isEventActive)
                    {
                        stopEvent();
                        player.sendMessage("Event manually stopped.");
                        LOGGER.info("Global Monster Hunt Event stopped manually by GM: " + player.getName());
                    }
                    else
                    {
                        player.sendMessage("Event is not active!");
                    }
                    break;
                    
                case "reset_counts":
                    currentKills = 0;
                    calculateRequiredKills();
                    player.sendMessage("Kill counts have been reset. New required kills: " + requiredKills);
                    LOGGER.info("Global Monster Hunt Event counts reset by GM: " + player.getName());
                    break;
            }
            showInfo(player);
            return null;
        }
        
        switch (event)
        {
            case "CALCULATE_REQUIRED_KILLS":
            {
                calculateRequiredKills();
                break;
            }
            case "START_EVENT_CYCLE":
            {
                if (!isEventActive)
                {
                    calculateRequiredKills();
                    announceEventStart();
                }
                break;
            }
            case "STOP_EVENT":
            {
                stopEvent();
                break;
            }
            case "info":
            {
                if (player != null)
                {
                    showInfo(player);
                }
                break;
            }
        }
        return null;
    }
    
    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        showInfo(player);
        return null;
    }
    
    @Override
    public String onTalk(Npc npc, Player player)
    {
        final QuestState qs = getQuestState(player, true);
        if (qs == null)
        {
            return null;
        }
        
        showInfo(player);
        return null;
    }
    
    @Override
    public String onKill(Npc npc, Player killer, boolean isSummon)
    {
        try
        {
            // Debug log
            LOGGER.info("onKill triggered - NPC ID: " + npc.getId() + ", Killer: " + killer.getName());
            
            // Sadece etkinlik aktifken özel eşya düşür
            if (isEventActive)
            {
                // Event aktifken special item drop
                if (Rnd.get(100) < SPECIAL_DROP_CHANCE)
                {
                    // Item drop ve mesaj
                    npc.dropItem(killer, SPECIAL_ITEM_ID, SPECIAL_ITEM_COUNT);
                    killer.sendMessage("You received a special item from the Monster Hunt Event!");
                    LOGGER.info("Special item dropped for " + killer.getName());
                }
            }
            
            // Kill sayımını her durumda yap
            synchronized (this)
            {
                currentKills++;
                LOGGER.info("Kill counted - Current kills: " + currentKills + "/" + requiredKills + " by " + killer.getName());
                
                // Her 100 killde bir duyuru yap
                if (currentKills % 100 == 0)
                {
                    String message = "Monster Hunt Progress: " + currentKills + "/" + requiredKills;
                    Broadcast.toAllOnlinePlayers(message);
                    World.getInstance().getPlayers().forEach(p -> p.sendPacket(new ExShowScreenMessage(message, 3000)));
                }
                
                // Hedef tamamlandığında eventi başlat
                if (currentKills >= requiredKills)
                {
                    LOGGER.info("Required kills reached, starting event...");
                    startEvent();
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.warning("Error in onKill: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    private void showInfo(Player player)
    {
        final NpcHtmlMessage html = new NpcHtmlMessage();
        final StringBuilder sb = new StringBuilder();
        
        sb.append("<html><body>");
        sb.append("<center><font color=\"LEVEL\">Global Monster Hunt Event</font></center><br>");
        
        if (isEventActive)
        {
            sb.append("<center>");
            sb.append("Status: <font color=\"00FF00\">ACTIVE</font><br>");
            sb.append("EXP Bonus: +25%<br>");
            sb.append("Drop Bonus: +25%<br>");
            sb.append("Time Remaining: " + (EVENT_DURATION / 60000) + " minutes<br>");
            sb.append("</center>");
        }
        else
        {
            sb.append("<center>");
            sb.append("Status: <font color=\"FF0000\">INACTIVE</font><br>");
            sb.append("Kills: " + currentKills + "/" + requiredKills + "<br>");
            sb.append("Remaining: " + (requiredKills - currentKills) + "<br>");
            sb.append("</center>");
        }
        
        sb.append("<br><center>");
        sb.append("<font color=\"LEVEL\">Special Drop Info:</font><br>");
        sb.append("All monsters have 25% chance<br>");
        sb.append("to drop special item.<br>");
        sb.append("(Only during active event)<br>");
        sb.append("</center>");
        
        if (player.isGM())
        {
            sb.append("<br><center>");
            sb.append("<table width=270>");
            sb.append("<tr><td>");
            sb.append("<button value=\"Start Event\" action=\"bypass -h Quest " + QUEST_ID + " start_event\" width=95 height=21 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
            sb.append("</td><td>");
            sb.append("<button value=\"Stop Event\" action=\"bypass -h Quest " + QUEST_ID + " stop_event\" width=95 height=21 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
            sb.append("</td></tr><tr><td>");
            sb.append("<button value=\"Reset Counts\" action=\"bypass -h Quest " + QUEST_ID + " reset_counts\" width=95 height=21 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
            sb.append("</td></tr>");
            sb.append("</table>");
            sb.append("</center>");
        }
        
        sb.append("</body></html>");
        
        html.setHtml(sb.toString());
        player.sendPacket(html);
    }
    
    private void calculateRequiredKills()
    {
        try
        {
            int onlinePlayers = Math.max(World.getInstance().getPlayers().size(), 1);
            requiredKills = Math.max(onlinePlayers * 10, 10);
            LOGGER.info("Required kills calculated: " + requiredKills + " (Online Players: " + onlinePlayers + ")");
        }
        catch (Exception e)
        {
            requiredKills = 10;
            LOGGER.warning("Error calculating required kills. Set to default: 10");
        }
    }
    
    private void startEvent()
    {
        if (isEventActive)
        {
            return;
        }
        
        isEventActive = true;
        currentKills = 0;
        
        // Apply EXP and Drop bonus
        Config.RATE_XP = ORIGINAL_XP_RATE * (1 + EXP_RATE_BONUS);
        Config.RATE_DEATH_DROP_CHANCE_MULTIPLIER = ORIGINAL_DROP_RATE * (1 + DROP_RATE_BONUS);
        
        // Announce event start
        String startMessage = "Global Monster Hunt Event has started! EXP and Drop rates increased by 25% for 1 hour!";
        Broadcast.toAllOnlinePlayers(startMessage);
        World.getInstance().getPlayers().forEach(p -> p.sendPacket(new ExShowScreenMessage(startMessage, 5000)));
        
        // Schedule event end
        startQuestTimer("STOP_EVENT", EVENT_DURATION, null, null, false);
        
        LOGGER.info("Global Monster Hunt Event started automatically. Required kills reached.");
    }
    
    private void stopEvent()
    {
        isEventActive = false;
        
        // Reset rates
        Config.RATE_XP = ORIGINAL_XP_RATE;
        Config.RATE_DEATH_DROP_CHANCE_MULTIPLIER = ORIGINAL_DROP_RATE;
        
        // Announce event end
        Broadcast.toAllOnlinePlayers("Global Monster Hunt Event has ended!");
        World.getInstance().getPlayers().forEach(p -> p.sendPacket(new ExShowScreenMessage("Global Monster Hunt Event Ended!", 5000)));
        
        // Schedule next cycle
        startQuestTimer("START_EVENT_CYCLE", EVENT_INTERVAL, null, null, false);
        
        // Reset kills for next cycle
        currentKills = 0;
        calculateRequiredKills();
    }
    
    private void announceEventStart()
    {
        Broadcast.toAllOnlinePlayers("New Monster Hunt Event cycle has started!");
        Broadcast.toAllOnlinePlayers("Required kills: " + requiredKills);
        World.getInstance().getPlayers().forEach(p -> p.sendPacket(new ExShowScreenMessage("Monster Hunt Event Started! Required Kills: " + requiredKills, 5000)));
    }
    
    private int[] getAllMonsterId()
    {
        final List<Integer> monsterIds = new ArrayList<>();
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT monster_id FROM monsters_hunter");
            ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                int monsterId = rs.getInt("monster_id");
                monsterIds.add(monsterId);
                LOGGER.info("Loaded monster ID from DB: " + monsterId);
            }
        }
        catch (Exception e)
        {
            LOGGER.warning("Could not load monster IDs from DB: " + e.getMessage());
            // Varsayılan monster ID'leri
            monsterIds.add(20001);
            monsterIds.add(20002);
            monsterIds.add(20003);
            LOGGER.info("Added default monster IDs");
        }
        
        if (monsterIds.isEmpty())
        {
            // Eğer liste hala boşsa, bazı temel monster ID'leri ekle
            monsterIds.add(20001);
            monsterIds.add(20002);
            monsterIds.add(20003);
            LOGGER.info("Using fallback monster IDs");
        }
        
        LOGGER.info("Total monster IDs loaded: " + monsterIds.size());
        return monsterIds.stream().mapToInt(Integer::intValue).toArray();
    }
    
    public static GlobalMonsterHunt getInstance()
    {
        return SingletonHolder.INSTANCE;
    }
    
    private static class SingletonHolder
    {
        protected static final GlobalMonsterHunt INSTANCE = new GlobalMonsterHunt();
    }
    
    public static void main(String[] args)
    {
        new GlobalMonsterHunt();
    }
}