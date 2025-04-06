package quests.Q00801_MonsterHunterSpecialization;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.l2jmobius.Config;
import org.l2jmobius.gameserver.instancemanager.QuestManager;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.holders.ItemHolder;
import org.l2jmobius.gameserver.model.quest.Quest;
import org.l2jmobius.gameserver.model.quest.QuestState;
import org.l2jmobius.commons.threads.ThreadPool;
import java.util.Date;

public class Q00801_MonsterHunterSpecialization extends Quest
{
    
    // Bunun yerine şunu ekle:
    private static Q00801_MonsterHunterSpecialization instance;
    
    public static synchronized Q00801_MonsterHunterSpecialization getInstance()
    {
        if (instance == null)
        {
            instance = new Q00801_MonsterHunterSpecialization();
        }
        return instance;
    }
    
    public BonusInfo getMonsterHunterBonuses(Player player, int monsterId)
    {
        MonsterInfo monster = MONSTER_INFO.get(monsterId);
        if (monster != null)
        {
            int currentKills = getMonsterKills(player, monsterId);
            return monster.getActiveBonuses(currentKills);
        }
        return new BonusInfo();
    }
    
    // NPC
    private static final int QUEST_NPC = 37001;
    
    private static final Logger LOGGER = Logger.getLogger(Q00801_MonsterHunterSpecialization.class.getName());
    private static final int RESET_HOUR = 20; // 8 PM - Bunu buraya ekleyin
    // Cache için static map
    private static final Map<String, Integer> KILL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REWARD_CACHE = new ConcurrentHashMap<>();
    // Mevcut quest değişkenlerine ek olarak
    private static final long WEEKLY_RESET = 7 * 24 * 60 * 60 * 1000L; // 7 gün
    private static final long DAILY_RESET = 24 * 60 * 60 * 1000L; // 24 saat
    
    private int calculateMonsterScore(int monsterId, int currentKills, Player player)
    {
        MonsterInfo monster = MONSTER_INFO.get(monsterId);
        if (monster != null)
        {
            // Level kontrolü
            try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT monster_lvl FROM monsters_hunter WHERE monster_id = ?"))
            {
                ps.setInt(1, monsterId);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        int monsterLevel = rs.getInt("monster_lvl");
                        int levelDiff = Math.abs(player.getLevel() - monsterLevel);
                        
                        if (levelDiff > 12)
                        {
                            LOGGER.info("Score cancelled due to level difference - Player Level: " + player.getLevel() + ", Monster Level: " + monsterLevel);
                            return 0; // Level farkı 10'dan büyükse score verme
                        }
                    }
                }
            }
            catch (SQLException e)
            {
                LOGGER.warning("Error checking monster level: " + e.getMessage());
            }
            
            int baseScore = monster.getBaseScore();
            int stageBonus = monster.getStageBonus(currentKills);
            int totalScore = baseScore + stageBonus;
            
            return totalScore;
        }
        
        // Eğer MonsterInfo bulunamazsa, eski hesaplama yöntemini kullan
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT base_score, monster_lvl, " + "monster_need_to_kill_for_reward1, monster_need_to_kill_for_reward2, " + "monster_need_to_kill_for_reward3, monster_need_to_kill_for_reward4, " + "monster_need_to_kill_for_reward5, monster_need_to_kill_for_reward6, " + "monster_need_to_kill_for_reward7, monster_need_to_kill_for_reward8, " + "monster_need_to_kill_for_reward9, monster_need_to_kill_for_reward10 " + "FROM monsters_hunter WHERE monster_id = ?"))
        {
            ps.setInt(1, monsterId);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    // Level kontrolü
                    int monsterLevel = rs.getInt("monster_lvl");
                    int levelDiff = Math.abs(player.getLevel() - monsterLevel);
                    
                    if (levelDiff > 12)
                    {
                        LOGGER.info("Score cancelled due to level difference - Player Level: " + player.getLevel() + ", Monster Level: " + monsterLevel);
                        return 0;
                    }
                    
                    int baseScore = rs.getInt("base_score");
                    double bonus = 1.0;
                    
                    // 10 seviyeye kadar bonus kontrolü
                    for (int i = 1; i <= 10; i++)
                    {
                        int killRequirement = rs.getInt("monster_need_to_kill_for_reward" + i);
                        if (killRequirement > 0 && currentKills >= killRequirement)
                        {
                            bonus += 0.05; // Her seviye için %5 bonus
                        }
                    }
                    
                    int totalScore = (int) (baseScore * bonus);
                    LOGGER.info("Using DB calculation - Base: " + baseScore + ", Bonus Multiplier: " + bonus + ", Total: " + totalScore);
                    return totalScore;
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error calculating monster score: " + e.getMessage());
        }
        return 0;
    }
    
    private void updatePlayerScores(Player player, int monsterId, int score) {
        LOGGER.info("Updating scores for player " + player.getName() + " - Score: " + score);
        
        try (Connection con = DatabaseFactory.getConnection()) {
            // Önce mevcut skorları kontrol et
            String updateSql = 
                "UPDATE monster_hunter_rankings SET " +
                "weekly_score = weekly_score + ?, " +  // Weekly score her zaman artacak
                "daily_score = CASE " +
                "    WHEN daily_reward_claimed = 0 OR daily_score = 0 THEN daily_score + ? " +  // daily_reward_claimed = 0 VEYA daily_score = 0 ise güncelle
                "    ELSE daily_score " +
                "END " +
                "WHERE char_id = ?";
                
            try (PreparedStatement updatePs = con.prepareStatement(updateSql)) {
                updatePs.setInt(1, score);
                updatePs.setInt(2, score);
                updatePs.setInt(3, player.getObjectId());
                int updated = updatePs.executeUpdate();
                
                if (updated > 0) {
                    LOGGER.info("Updated scores for " + player.getName() + 
                        " - Added score: " + score);
                    
                    // Güncel skorları log'la
                    try (PreparedStatement checkPs = con.prepareStatement(
                        "SELECT daily_score, weekly_score FROM monster_hunter_rankings WHERE char_id = ?")) 
                    {
                        checkPs.setInt(1, player.getObjectId());
                        try (ResultSet rs = checkPs.executeQuery()) {
                            if (rs.next()) {
                            }
                        }
                    }
                } else {
                    // Kayıt yoksa yeni kayıt oluştur
                    try (PreparedStatement insertPs = con.prepareStatement(
                        "INSERT INTO monster_hunter_rankings (char_id, weekly_score, daily_score, weekly_kills, daily_kills, daily_reward_claimed, rank_reward_claimed) " +
                        "VALUES (?, ?, ?, 0, 0, 0, 0)")) 
                    {
                        insertPs.setInt(1, player.getObjectId());
                        insertPs.setInt(2, score);
                        insertPs.setInt(3, score);
                        insertPs.executeUpdate();
                        LOGGER.info("Created new ranking entry for " + player.getName() + " with score: " + score);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Error updating scores for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String formatTimeRemaining(long millisRemaining)
    {
        if (millisRemaining < 0)
        {
            return "0s";
        }
        
        long seconds = millisRemaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        seconds %= 60;
        minutes %= 60;
        hours %= 24;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0)
        {
            sb.append(days).append("d ");
        }
        if (hours > 0)
        {
            sb.append(hours).append("h ");
        }
        if (minutes > 0)
        {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0)
        {
            sb.append(seconds).append("s");
        }
        
        return sb.toString();
    }
    
    private boolean shouldPerformReset(String resetType) {
        try (Connection con = DatabaseFactory.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT last_reset FROM monster_hunter_reset_times WHERE reset_type = ?")) 
        {
            ps.setString(1, resetType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long lastReset = rs.getLong("last_reset");
                    long currentTime = System.currentTimeMillis();
                    
                    Calendar lastResetCal = Calendar.getInstance();
                    lastResetCal.setTimeInMillis(lastReset);
                    Calendar currentCal = Calendar.getInstance();
                    currentCal.setTimeInMillis(currentTime);
                    
                    // Eğer şu anki saat 20:00 veya 20:01 ise ve son reset bugün değilse
                    if ((currentCal.get(Calendar.HOUR_OF_DAY) == RESET_HOUR && 
                         currentCal.get(Calendar.MINUTE) <= 1)) { // Sadece 1 dakika tolerans
                        
                        if (resetType.equals("DAILY")) {
                            if (lastResetCal.get(Calendar.DAY_OF_YEAR) < currentCal.get(Calendar.DAY_OF_YEAR)) {
                                LOGGER.info("Daily reset should be performed now.");
                                return true;
                            }
                        }
                        else if (resetType.equals("WEEKLY") && 
                                 currentCal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                            if (lastResetCal.get(Calendar.WEEK_OF_YEAR) < currentCal.get(Calendar.WEEK_OF_YEAR)) {
                                LOGGER.info("Weekly reset should be performed now.");
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        } catch (SQLException e) {
            LOGGER.warning("Error checking reset time: " + e.getMessage());
            return false;
        }
    }
    
    
    private void updateResetTime(String resetType)
    {
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO monster_hunter_reset_times (reset_type, last_reset) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_reset = ?"))
        {
            long currentTime = System.currentTimeMillis();
            ps.setString(1, resetType);
            ps.setLong(2, currentTime);
            ps.setLong(3, currentTime);
            ps.executeUpdate();
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error updating reset time: " + e.getMessage());
        }
    }
    
    private void dailyReset()
    {
        if (!shouldPerformReset("DAILY"))
        {
            LOGGER.info("Daily reset skipped - Not time yet");
            return;
        }
        
        try (Connection con = DatabaseFactory.getConnection())
        {
            LOGGER.info("=== DAILY RESET STARTING === " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
            
            checkAndGiveDailyRankRewards();
            
            try (PreparedStatement ps = con.prepareStatement("UPDATE monster_hunter_rankings SET daily_kills = 0, daily_score = 0, daily_reward_claimed = 0"))
            {
                ps.executeUpdate();
            }
            
            updateResetTime("DAILY");
            LOGGER.info("=== DAILY RESET COMPLETED === Next reset in 24 hours");
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error while resetting daily rankings: " + e.getMessage());
        }
    }
    
    private void weeklyReset()
    {
        if (!shouldPerformReset("WEEKLY"))
        {
            LOGGER.info("Weekly reset skipped - Not time yet");
            return;
        }
        
        try (Connection con = DatabaseFactory.getConnection())
        {
            LOGGER.info("=== WEEKLY RESET STARTING === " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
            
            checkAndGiveWeeklyRankRewards();
            
            try (PreparedStatement ps = con.prepareStatement("UPDATE monster_hunter_rankings SET weekly_kills = 0, weekly_score = 0, rank_reward_claimed = 0"))
            {
                ps.executeUpdate();
            }
            
            updateResetTime("WEEKLY");
            LOGGER.info("=== WEEKLY RESET COMPLETED === Next reset in " + formatTimeRemaining(WEEKLY_RESET));
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error while resetting weekly rankings: " + e.getMessage());
        }
    }
    
    private void startDailyResetTask() {
        Calendar nextReset = Calendar.getInstance();
        nextReset.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
        nextReset.set(Calendar.MINUTE, 0);
        nextReset.set(Calendar.SECOND, 0);
        nextReset.set(Calendar.MILLISECOND, 0);
        
        if (nextReset.getTimeInMillis() <= System.currentTimeMillis()) {
            nextReset.add(Calendar.DAY_OF_YEAR, 1);
        }
        
        long delay = nextReset.getTimeInMillis() - System.currentTimeMillis();
        
        ThreadPool.scheduleAtFixedRate(() -> {
            try {
                Calendar now = Calendar.getInstance();
                if (now.get(Calendar.HOUR_OF_DAY) == RESET_HOUR && 
                    now.get(Calendar.MINUTE) <= 1) {
                    dailyReset();
                }
            } catch (Exception e) {
                LOGGER.severe("Error in daily reset task: " + e.getMessage());
            }
        }, delay, DAILY_RESET);
    }
    
    public void startWeeklyResetTask()
    {
        long delay = getNextWeeklyResetTime(); // getNextResetTime() yerine getNextWeeklyResetTime() kullan
        if (delay < 0)
        {
            LOGGER.warning("Invalid weekly reset delay: " + delay + ". Adjusting to next week.");
            delay = WEEKLY_RESET;
        }
        
        ThreadPool.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    weeklyReset();
                }
                catch (Exception e)
                {
                    LOGGER.severe("Error in weekly reset task: " + e.getMessage());
                }
            }
        }, delay, WEEKLY_RESET);
        
        LOGGER.info("Weekly reset scheduled. Next reset in " + formatTimeRemaining(delay));
    }
    
    private void checkAndGiveDailyRankRewards() {
        LOGGER.info("=== STARTING DAILY RANK REWARDS DISTRIBUTION ===");
        LOGGER.info("Current Server Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
    
        try (Connection con = DatabaseFactory.getConnection()) {
            // Pre-distribution stats
            try (PreparedStatement statsPs = con.prepareStatement(
                "SELECT COUNT(*) as total_players, " +
                "SUM(CASE WHEN daily_score > 0 THEN 1 ELSE 0 END) as active_players, " +
                "MAX(daily_score) as highest_score, " +
                "SUM(daily_score) as total_score " +
                "FROM monster_hunter_rankings")) 
            {
                ResultSet statsRs = statsPs.executeQuery();
                if (statsRs.next()) {
                    LOGGER.info("Pre-distribution Statistics:");
                    LOGGER.info("Total Players: " + statsRs.getInt("total_players"));
                    LOGGER.info("Active Players: " + statsRs.getInt("active_players"));
                    LOGGER.info("Highest Score: " + statsRs.getInt("highest_score"));
                    LOGGER.info("Total Score: " + statsRs.getInt("total_score"));
                }
            }
    
            // Top 10 players before distribution
            LOGGER.info("=== TOP 10 PLAYERS BEFORE DISTRIBUTION ===");
            try (PreparedStatement topPs = con.prepareStatement(
                "SELECT c.char_name, r.daily_score, r.daily_kills, " +
                "(SELECT COUNT(*) + 1 FROM monster_hunter_rankings r2 WHERE r2.daily_score > r.daily_score) as rank " +
                "FROM monster_hunter_rankings r " +
                "JOIN characters c ON r.char_id = c.charId " +
                "WHERE r.daily_score > 0 " +
                "ORDER BY r.daily_score DESC LIMIT 10")) 
            {
                ResultSet topRs = topPs.executeQuery();
                while (topRs.next()) {
                    LOGGER.info(String.format("Rank %d: %s - Score: %d, Kills: %d",
                        topRs.getInt("rank"),
                        topRs.getString("char_name"),
                        topRs.getInt("daily_score"),
                        topRs.getInt("daily_kills")));
                }
            }
    
            // Reward distribution
            LOGGER.info("=== STARTING REWARD DISTRIBUTION ===");
            String rankingSql = 
    "WITH RankedPlayers AS (" +
    "    SELECT r.char_id, r.daily_kills, r.daily_score, " +
    "    c.char_name, c.online, " +
    "    DENSE_RANK() OVER (ORDER BY r.daily_score DESC) as rank_position " +
    "    FROM monster_hunter_rankings r " +
    "    JOIN characters c ON r.char_id = c.charId " +
    "    WHERE r.daily_score > 0 AND r.daily_reward_claimed = 0" +
    ") " +
    "SELECT rp.*, rw.reward_id, rw.reward_count " +
    "FROM RankedPlayers rp " +
    "JOIN monster_hunter_rewards rw ON rp.rank_position = rw.rank_position " +
    "WHERE rw.reward_type = 'DAILY' " +
    "AND rp.rank_position <= 100 " +
    "ORDER BY rp.daily_score DESC, rp.daily_kills DESC";
    
            int rewardedPlayers = 0;
            int failedRewards = 0;
            try (PreparedStatement ps = con.prepareStatement(rankingSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int charId = rs.getInt("char_id");
                        int rewardId = rs.getInt("reward_id");
                        int rewardCount = rs.getInt("reward_count");
                        int rank = rs.getInt("rank_position");
                        String charName = rs.getString("char_name");
                        int isOnline = rs.getInt("online");
                        int score = rs.getInt("daily_score");
                        
                        LOGGER.info(String.format("Processing Rank %d: %s (Score: %d) - Reward: %d x%d",
                            rank, charName, score, rewardId, rewardCount));
                        
                        try {
                            if (isOnline == 1) {
                                Player player = World.getInstance().getPlayer(charId);
                                if (player != null && player.isOnline()) {
                                    player.addItem("Daily Rank Reward", rewardId, rewardCount, player, true);
                                    player.sendMessage("Congratulations! You received daily ranking reward for rank " + rank + "!");
                                    LOGGER.info("Successfully gave reward to online player: " + charName);
                                } else {
                                    addOfflineReward(charId, rewardId, rewardCount, con);
                                    LOGGER.info("Added offline reward for player: " + charName);
                                }
                            } else {
                                addOfflineReward(charId, rewardId, rewardCount, con);
                                LOGGER.info("Added offline reward for player: " + charName);
                            }
                            rewardedPlayers++;
                        } catch (Exception e) {
                            failedRewards++;
                            LOGGER.warning("Failed to give reward to " + charName + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
    
            // Update claimed status
            try (PreparedStatement claimPs = con.prepareStatement(
                "UPDATE monster_hunter_rankings SET daily_reward_claimed = 1 " +
                "WHERE daily_score > 0")) 
            {
                int updatedClaims = claimPs.executeUpdate();
                LOGGER.info("Updated reward claimed status for " + updatedClaims + " players");
            }
    
            // Reset scores
            try (PreparedStatement resetPs = con.prepareStatement(
                "UPDATE monster_hunter_rankings SET daily_kills = 0, daily_score = 0")) 
            {
                int resetCount = resetPs.executeUpdate();
                LOGGER.info("Reset daily scores for " + resetCount + " players");
            }
    
            LOGGER.info("=== DAILY REWARDS DISTRIBUTION COMPLETED ===");
            LOGGER.info("Successfully Rewarded Players: " + rewardedPlayers);
            LOGGER.info("Failed Reward Attempts: " + failedRewards);
    
        } catch (SQLException e) {
            LOGGER.severe("Critical error in daily rewards distribution: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void checkAndGiveWeeklyRankRewards() {
        LOGGER.info("=== STARTING WEEKLY RANK REWARDS DISTRIBUTION ===");
        LOGGER.info("Current Server Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
    
        try (Connection con = DatabaseFactory.getConnection()) {
            // Pre-distribution stats
            try (PreparedStatement statsPs = con.prepareStatement(
                "SELECT COUNT(*) as total_players, " +
                "SUM(CASE WHEN weekly_score > 0 THEN 1 ELSE 0 END) as active_players, " +
                "MAX(weekly_score) as highest_score, " +
                "SUM(weekly_score) as total_score " +
                "FROM monster_hunter_rankings")) 
            {
                ResultSet statsRs = statsPs.executeQuery();
                if (statsRs.next()) {
                    LOGGER.info("Pre-distribution Weekly Statistics:");
                    LOGGER.info("Total Players: " + statsRs.getInt("total_players"));
                    LOGGER.info("Active Players: " + statsRs.getInt("active_players"));
                    LOGGER.info("Highest Score: " + statsRs.getInt("highest_score"));
                    LOGGER.info("Total Score: " + statsRs.getInt("total_score"));
                }
            }
    
            // Top 10 weekly players
            LOGGER.info("=== TOP 10 WEEKLY PLAYERS ===");
            try (PreparedStatement topPs = con.prepareStatement(
                "SELECT c.char_name, r.weekly_score, r.weekly_kills, " +
                "(SELECT COUNT(*) + 1 FROM monster_hunter_rankings r2 WHERE r2.weekly_score > r.weekly_score) as rank " +
                "FROM monster_hunter_rankings r " +
                "JOIN characters c ON r.char_id = c.charId " +
                "WHERE r.weekly_score > 0 " +
                "ORDER BY r.weekly_score DESC LIMIT 10")) 
            {
                ResultSet topRs = topPs.executeQuery();
                while (topRs.next()) {
                    LOGGER.info(String.format("Rank %d: %s - Score: %d, Kills: %d",
                        topRs.getInt("rank"),
                        topRs.getString("char_name"),
                        topRs.getInt("weekly_score"),
                        topRs.getInt("weekly_kills")));
                }
            }
    
            // Reward distribution
            String rankingSql = 
                "SELECT r.char_id, r.weekly_kills, r.weekly_score, " +
                "c.char_name, c.online, rw.reward_id, rw.reward_count, " +
                "(SELECT COUNT(*) + 1 FROM monster_hunter_rankings r2 WHERE r2.weekly_score > r.weekly_score) as rank_position " +
                "FROM monster_hunter_rankings r " +
                "JOIN characters c ON r.char_id = c.charId " +
                "JOIN monster_hunter_rewards rw ON " +
                "(SELECT COUNT(*) + 1 FROM monster_hunter_rankings r2 WHERE r2.weekly_score > r.weekly_score) = rw.rank_position " +
                "WHERE rw.reward_type = 'WEEKLY' " +
                "AND r.weekly_score > 0 " +
                "AND r.rank_reward_claimed = 0 " +
                "AND rw.rank_position <= 100 " +
                "ORDER BY r.weekly_score DESC, r.weekly_kills DESC";
    
            int rewardedPlayers = 0;
            int failedRewards = 0;
            try (PreparedStatement ps = con.prepareStatement(rankingSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int charId = rs.getInt("char_id");
                        int rewardId = rs.getInt("reward_id");
                        int rewardCount = rs.getInt("reward_count");
                        int rank = rs.getInt("rank_position");
                        String charName = rs.getString("char_name");
                        int isOnline = rs.getInt("online");
                        int score = rs.getInt("weekly_score");
                        
                        LOGGER.info(String.format("Processing Weekly Rank %d: %s (Score: %d) - Reward: %d x%d",
                            rank, charName, score, rewardId, rewardCount));
                        
                        try {
                            if (isOnline == 1) {
                                Player player = World.getInstance().getPlayer(charId);
                                if (player != null && player.isOnline()) {
                                    player.addItem("Weekly Rank Reward", rewardId, rewardCount, player, true);
                                    player.sendMessage("Congratulations! You received weekly ranking reward for rank " + rank + "!");
                                    LOGGER.info("Successfully gave weekly reward to online player: " + charName);
                                } else {
                                    addOfflineReward(charId, rewardId, rewardCount, con);
                                    LOGGER.info("Added weekly offline reward for player: " + charName);
                                }
                            } else {
                                addOfflineReward(charId, rewardId, rewardCount, con);
                                LOGGER.info("Added weekly offline reward for player: " + charName);
                            }
                            rewardedPlayers++;
                        } catch (Exception e) {
                            failedRewards++;
                            LOGGER.warning("Failed to give weekly reward to " + charName + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
    
            // Update claimed status
            try (PreparedStatement claimPs = con.prepareStatement(
                "UPDATE monster_hunter_rankings SET rank_reward_claimed = 1 " +
                "WHERE weekly_score > 0")) 
            {
                int updatedClaims = claimPs.executeUpdate();
                LOGGER.info("Updated weekly reward claimed status for " + updatedClaims + " players");
            }
    
            // Reset scores
            try (PreparedStatement resetPs = con.prepareStatement(
                "UPDATE monster_hunter_rankings SET weekly_kills = 0, weekly_score = 0")) 
            {
                int resetCount = resetPs.executeUpdate();
                LOGGER.info("Reset weekly scores for " + resetCount + " players");
            }
    
            LOGGER.info("=== WEEKLY REWARDS DISTRIBUTION COMPLETED ===");
            LOGGER.info("Successfully Rewarded Players: " + rewardedPlayers);
            LOGGER.info("Failed Reward Attempts: " + failedRewards);
    
        } catch (SQLException e) {
            LOGGER.severe("Critical error in weekly rewards distribution: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void addOfflineReward(int charId, int itemId, int count, Connection con) throws SQLException {
        String sql = "INSERT INTO items (owner_id, object_id, item_id, count, enchant_level, loc, loc_data, " + 
            "time_of_use, custom_type1, custom_type2, mana_left, time) " + 
            "VALUES (?, ?, ?, ?, 0, 'INVENTORY', 0, 0, 0, 0, -1, 0)";
        
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            // Her item için unique bir object_id al
            int objectId = getNextObjectId(con);
            
            ps.setInt(1, charId); // owner_id
            ps.setInt(2, objectId); // object_id
            ps.setInt(3, itemId); // item_id
            ps.setInt(4, count); // count
            ps.executeUpdate();
            
            LOGGER.info("Added offline reward to charId: " + charId + 
                " - ItemID: " + itemId + " Count: " + count + " ObjectID: " + objectId);
        } catch (SQLException e) {
            LOGGER.warning("Failed to add offline reward - CharID: " + charId + 
                " ItemID: " + itemId + " Error: " + e.getMessage());
            throw e;
        }
    }
    
    private int getNextObjectId(Connection con) throws SQLException
    {
        try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(object_id), 268500000) FROM items"))
        {
            try (ResultSet rs = ps.executeQuery())
            {
                rs.next();
                return rs.getInt(1) + 1000 + (int) (Math.random() * 1000);
            }
        }
    }
    
    private static class PlayerRankInfo
    {
        String charName;
        int totalKills;
        int completedMonsters;
        int charId;
        int weeklyScore; // Yeni
        int dailyScore; // Yeni
        
        // Weekly/Daily için constructor
        public PlayerRankInfo(String charName, int totalKills, int completedMonsters, int weeklyKills, int dailyKills, int charId)
        {
            this.charName = charName;
            this.totalKills = totalKills;
            this.completedMonsters = completedMonsters;
            this.charId = charId;
            this.weeklyScore = 0;
            this.dailyScore = 0;
        }
        
        // Score için yeni constructor
        public PlayerRankInfo(String charName, int totalKills, int completedMonsters, int weeklyKills, int dailyKills, int charId, int weeklyScore, int dailyScore)
        {
            this.charName = charName;
            this.totalKills = totalKills;
            this.completedMonsters = completedMonsters;
            this.charId = charId;
            this.weeklyScore = weeklyScore;
            this.dailyScore = dailyScore;
        }
    }
    
    // Q00801_MonsterHunterSpecialization sınıfına eklenecek public metodlar
    public Map<Integer, MonsterInfo> getMonsterInfoMap()
    {
        return MONSTER_INFO;
    }
    
    public int getMonsterKillCount(Player player, int monsterId)
    {
        return getMonsterKills(player, monsterId);
    }
    
    public MonsterInfo getMonsterInfo(int monsterId)
    {
        return MONSTER_INFO.get(monsterId);
    }
    
    // Monster bilgilerini tutacak class
    public static class MonsterInfo
    {
        // Getter metodları
        public int[] getRequiredKills()
        {
            return requiredKills;
        }
        
        public Map<Integer, List<Integer>> getRewards()
        {
            return rewards;
        }
        
        public Map<Integer, List<Integer>> getQuantities()
        {
            return quantities;
        }
        
        int monsterId;
        String monsterName;
        int baseScore = 0;
        
        // Çoklu ödüller için Map'ler
        Map<Integer, List<Integer>> rewards = new HashMap<>();
        Map<Integer, List<Integer>> quantities = new HashMap<>();
        int[] requiredKills = new int[10];
        
        // Tüm bonus dizileri
        public double[] expBonuses = new double[10];
        public double[] dropBonuses = new double[10];
        public double[] dropAmountBonuses = new double[10];
        public double[] spoilBonuses = new double[10];
        public double[] spoilAmountBonuses = new double[10];
        
        public MonsterInfo(int id, String name, String race, int level, String reward1, String reward2, String reward3, String reward4, String reward5, String reward6, String reward7, String reward8, String reward9, String reward10, String quantity1, String quantity2, String quantity3, String quantity4, String quantity5, String quantity6, String quantity7, String quantity8, String quantity9, String quantity10, int kills1, int kills2, int kills3, int kills4, int kills5, int kills6, int kills7, int kills8, int kills9, int kills10)
        {
            monsterId = id;
            monsterName = name;
            
            // Ödülleri parse et
            parseAndSetRewards(1, reward1, quantity1);
            parseAndSetRewards(2, reward2, quantity2);
            parseAndSetRewards(3, reward3, quantity3);
            parseAndSetRewards(4, reward4, quantity4);
            parseAndSetRewards(5, reward5, quantity5);
            parseAndSetRewards(6, reward6, quantity6);
            parseAndSetRewards(7, reward7, quantity7);
            parseAndSetRewards(8, reward8, quantity8);
            parseAndSetRewards(9, reward9, quantity9);
            parseAndSetRewards(10, reward10, quantity10);
            
            // Kill gereksinimleri
            requiredKills[0] = kills1;
            requiredKills[1] = kills2;
            requiredKills[2] = kills3;
            requiredKills[3] = kills4;
            requiredKills[4] = kills5;
            requiredKills[5] = kills6;
            requiredKills[6] = kills7;
            requiredKills[7] = kills8;
            requiredKills[8] = kills9;
            requiredKills[9] = kills10;
            
            // Varsayılan bonus değerleri
            initializeDefaultBonuses();
        }
        
        // Varsayılan bonus değerlerini başlatan yeni metod
        private void initializeDefaultBonuses()
        {
            for (int i = 0; i < 10; i++)
            {
                expBonuses[i] = 0.10 * (i + 1);
                dropBonuses[i] = 0.10 * (i + 1);
                dropAmountBonuses[i] = 0.10 * (i + 1);
                spoilBonuses[i] = 0.10 * (i + 1);
                spoilAmountBonuses[i] = 0.10 * (i + 1);
            }
        }
        
        // DB'den bonus değerlerini yükleyen yeni metod
        public void loadBonusesFromDB(ResultSet rs) throws SQLException
        {
            for (int i = 1; i <= 10; i++)
            {
                expBonuses[i - 1] = rs.getDouble("exp_bonus" + i);
                dropBonuses[i - 1] = rs.getDouble("drop_bonus" + i);
                dropAmountBonuses[i - 1] = rs.getDouble("drop_amount_bonus" + i);
                spoilBonuses[i - 1] = rs.getDouble("spoil_bonus" + i);
                spoilAmountBonuses[i - 1] = rs.getDouble("spoil_amount_bonus" + i);
            }
        }
        
        // Aktif bonusları hesaplayan yeni metod
        public BonusInfo getActiveBonuses(int currentKills)
        {
            BonusInfo bonuses = new BonusInfo();
            int currentStage = getCurrentStage(currentKills);
            
            if (currentStage >= 1)
            {
                int index = currentStage - 1;
                bonuses.expBonus = expBonuses[index];
                bonuses.dropBonus = dropBonuses[index];
                bonuses.dropAmountBonus = dropAmountBonuses[index];
                bonuses.spoilBonus = spoilBonuses[index];
                bonuses.spoilAmountBonus = spoilAmountBonuses[index];
            }
            
            return bonuses;
        }
        
        // Mevcut metodlar...
        private void parseAndSetRewards(int stage, String rewardStr, String quantityStr)
        {
            // Mevcut implementasyon...
        }
        
        public void setBaseScore(int score)
        {
            this.baseScore = score;
        }
        
        public int getBaseScore()
        {
            return this.baseScore;
        }
        
        public int getStageBonus(int currentKills)
        {
            int currentStage = getCurrentStage(currentKills);
            if (currentStage >= 1)
            {
                return (int) (baseScore * 0.20 * currentStage);
            }
            return 0;
        }
        
        public int getCurrentStage(int currentKills)
        {
            for (int i = 9; i >= 0; i--)
            {
                if (currentKills >= requiredKills[i])
                {
                    return i + 1;
                }
            }
            return 1;
        }
    }
    
    // Bonus bilgilerini tutacak yeni yardımcı class
    public static class BonusInfo
    {
        public double expBonus = 0;
        public double dropBonus = 0;
        public double dropAmountBonus = 0;
        public double spoilBonus = 0;
        public double spoilAmountBonus = 0;
    }
    
    private Map<Integer, MonsterInfo> loadMonstersFromDB()
    {
        Map<Integer, MonsterInfo> monsters = new HashMap<>();
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM monsters_hunter"))
        {
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    int monsterId = rs.getInt("monster_id");
                    
                    // Quantity'leri String olarak al
                    String quantity1 = rs.getString("monster_reward_quantity1");
                    String quantity2 = rs.getString("monster_reward_quantity2");
                    String quantity3 = rs.getString("monster_reward_quantity3");
                    String quantity4 = rs.getString("monster_reward_quantity4");
                    String quantity5 = rs.getString("monster_reward_quantity5");
                    String quantity6 = rs.getString("monster_reward_quantity6");
                    String quantity7 = rs.getString("monster_reward_quantity7");
                    String quantity8 = rs.getString("monster_reward_quantity8");
                    String quantity9 = rs.getString("monster_reward_quantity9");
                    String quantity10 = rs.getString("monster_reward_quantity10");
                    
                    MonsterInfo info = new MonsterInfo(monsterId, rs.getString("monster_name"), rs.getString("monster_race"), rs.getInt("monster_lvl"), rs.getString("monster_reward1"), rs.getString("monster_reward2"), rs.getString("monster_reward3"), rs.getString("monster_reward4"), rs.getString("monster_reward5"), rs.getString("monster_reward6"), rs.getString("monster_reward7"), rs.getString("monster_reward8"), rs.getString("monster_reward9"), rs.getString("monster_reward10"), quantity1, quantity2, quantity3, quantity4, quantity5, quantity6, quantity7, quantity8, quantity9, quantity10, rs.getInt("monster_need_to_kill_for_reward1"), rs.getInt("monster_need_to_kill_for_reward2"), rs.getInt("monster_need_to_kill_for_reward3"), rs.getInt("monster_need_to_kill_for_reward4"), rs.getInt("monster_need_to_kill_for_reward5"), rs.getInt("monster_need_to_kill_for_reward6"), rs.getInt("monster_need_to_kill_for_reward7"), rs.getInt("monster_need_to_kill_for_reward8"), rs.getInt("monster_need_to_kill_for_reward9"), rs.getInt("monster_need_to_kill_for_reward10"));
                    
                    // Tüm bonusları set et
                    for (int i = 1; i <= 10; i++)
                    {
                        // Exp bonusları
                        info.expBonuses[i - 1] = rs.getDouble("exp_bonus" + i);
                        
                        // Drop bonusları
                        info.dropBonuses[i - 1] = rs.getDouble("drop_bonus" + i);
                        
                        // Drop Amount bonusları
                        info.dropAmountBonuses[i - 1] = rs.getDouble("drop_amount_bonus" + i);
                        
                        // Spoil bonusları
                        info.spoilBonuses[i - 1] = rs.getDouble("spoil_bonus" + i);
                        
                        // Spoil Amount bonusları
                        info.spoilAmountBonuses[i - 1] = rs.getDouble("spoil_amount_bonus" + i);
                    }
                    
                    // Base score'u set et
                    info.setBaseScore(rs.getInt("base_score"));
                    
                    monsters.put(info.monsterId, info);
                    
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error loading monster hunter data: " + e.getMessage(), e);
        }
        LOGGER.info("Loaded total monsters: " + monsters.size());
        return monsters;
    }
    
    // Quest başlangıcında monster bilgilerini yükle
    private static final Map<Integer, MonsterInfo> MONSTER_INFO = new HashMap<>();
    
    public Q00801_MonsterHunterSpecialization()
    {
        super(801);
        
        LOGGER.info("-------------------------------------------------=[ Monster Hunter Quest ]");
        Map<Integer, MonsterInfo> loadedMonsters = loadMonstersFromDB();
        MONSTER_INFO.putAll(loadedMonsters);
        
        // Kaçırılan reset'leri kontrol et
        checkMissedResets();
        
        addStartNpc(QUEST_NPC);
        addTalkId(QUEST_NPC);
        addFirstTalkId(QUEST_NPC);
        
        // Monster ID'lerini killId olarak ekle
        for (int monsterId : MONSTER_INFO.keySet())
        {
            addKillId(monsterId);
        }
        
        startWeeklyResetTask();
        startDailyResetTask();
    }
    
    private long getNextDailyResetTime()
    {
        Calendar nextReset = getNextResetTime("DAILY");
        return nextReset.getTimeInMillis() - System.currentTimeMillis();
    }
    
    private long getNextWeeklyResetTime()
    {
        Calendar nextReset = getNextResetTime("WEEKLY");
        return nextReset.getTimeInMillis() - System.currentTimeMillis();
    }
    
    private Calendar getNextResetTime(String resetType)
    {
        Calendar cal = Calendar.getInstance();
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT last_reset FROM monster_hunter_reset_times WHERE reset_type = ?"))
        {
            ps.setString(1, resetType);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    long lastReset = rs.getLong("last_reset");
                    cal.setTimeInMillis(lastReset);
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error getting last reset time: " + e.getMessage());
        }
        
        // Temel reset zamanı ayarları
        cal.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        // Eğer şu anki zaman, son reset zamanını geçmişse
        if (cal.getTimeInMillis() <= System.currentTimeMillis())
        {
            if (resetType.equals("DAILY"))
            {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            else
            {
                // Weekly reset için Pazartesi'ye ayarla
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                if (cal.getTimeInMillis() <= System.currentTimeMillis())
                {
                    cal.add(Calendar.WEEK_OF_YEAR, 1);
                }
            }
        }
        
        return cal;
    }
    
    private void updateLastResetTime(String resetType)
    {
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO monster_hunter_reset_times (reset_type, last_reset) VALUES (?, ?) " + "ON DUPLICATE KEY UPDATE last_reset = ?"))
        {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            long resetTime = cal.getTimeInMillis();
            
            ps.setString(1, resetType);
            ps.setLong(2, resetTime);
            ps.setLong(3, resetTime);
            ps.executeUpdate();
            
            LOGGER.info(resetType + " reset time updated to: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime()));
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error updating reset time: " + e.getMessage());
        }
    }
    
    public String showDailyRankings(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<center>Daily Monster Hunter Rankings</center><br>");
        
        try (Connection con = DatabaseFactory.getConnection())
        {
            try (PreparedStatement ps = con.prepareStatement("SELECT r.char_id, r.daily_kills, c.char_name " + "FROM monster_hunter_rankings r " + "JOIN characters c ON r.char_id = c.charId " + "WHERE r.daily_kills > 0 " + "ORDER BY r.daily_kills DESC LIMIT 10"))
            {
                try (ResultSet rs = ps.executeQuery())
                {
                    int rank = 1;
                    boolean foundPlayer = false;
                    int playerRank = 0;
                    int playerKills = 0;
                    
                    while (rs.next())
                    {
                        String charName = rs.getString("char_name");
                        int kills = rs.getInt("daily_kills");
                        
                        if (rank <= 10)
                        {
                            sb.append(rank + ". " + charName + " - " + kills + " kills<br>");
                        }
                        
                        if (rs.getInt("char_id") == player.getObjectId())
                        {
                            foundPlayer = true;
                            playerRank = rank;
                            playerKills = kills;
                        }
                        
                        rank++;
                    }
                    
                    sb.append("<br>");
                    if (foundPlayer)
                    {
                        sb.append("Your Rank: " + playerRank + "<br>");
                        sb.append("Your Kills: " + playerKills + "<br>");
                    }
                    else
                    {
                        sb.append("You haven't killed any monsters today.<br>");
                    }
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error while showing daily rankings: " + e.getMessage());
            return "Error loading rankings.";
        }
        
        sb.append("</body></html>");
        return sb.toString();
    }
    
    private void checkMissedResets() {
        LOGGER.info("=== STARTING MISSED RESETS CHECK ===");
        LOGGER.info("Current Server Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        
        Calendar currentTime = Calendar.getInstance();
        
        try (Connection con = DatabaseFactory.getConnection()) {
            // İlk kontrol: Bugünkü reset ve ödül durumu
            LOGGER.info("Checking today's reset and reward status...");
            try (PreparedStatement ps = con.prepareStatement(
                "SELECT last_reset FROM monster_hunter_reset_times WHERE reset_type = 'DAILY'")) 
            {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Calendar lastReset = Calendar.getInstance();
                    lastReset.setTimeInMillis(rs.getLong("last_reset"));
                    
                    LOGGER.info("Last Daily Reset Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastReset.getTime()));
                    
                    // Reset zamanı kontrolü
                    if (lastReset.get(Calendar.DAY_OF_YEAR) == currentTime.get(Calendar.DAY_OF_YEAR) &&
                        lastReset.get(Calendar.HOUR_OF_DAY) == RESET_HOUR)
                    {
                        LOGGER.info("Today's reset time check passed. Checking for unclaimed rewards...");
                        
                        // Önce rank'leri kontrol et
                        try (PreparedStatement rankPs = con.prepareStatement(
                            "SELECT c.char_name, r.daily_score, " +
                            "DENSE_RANK() OVER (ORDER BY r.daily_score DESC) as rank " +
                            "FROM monster_hunter_rankings r " +
                            "JOIN characters c ON r.char_id = c.charId " +
                            "WHERE r.daily_score > 0 " +
                            "ORDER BY r.daily_score DESC LIMIT 5")) 
                        {
                            ResultSet rankRs = rankPs.executeQuery();
                            LOGGER.info("Current Rankings:");
                            while (rankRs.next()) {
                                LOGGER.info(String.format("Rank %d: %s - Score: %d",
                                    rankRs.getInt("rank"),
                                    rankRs.getString("char_name"),
                                    rankRs.getInt("daily_score")));
                            }
                        }
                        
                        // Ödül kontrolü
                        try (PreparedStatement checkPs = con.prepareStatement(
                            "SELECT COUNT(*) as count FROM monster_hunter_rankings " + 
                            "WHERE daily_score > 0 AND daily_reward_claimed = 0")) 
                        {
                            ResultSet checkRs = checkPs.executeQuery();
                            if (checkRs.next() && checkRs.getInt("count") > 0) {
                                LOGGER.info("Found " + checkRs.getInt("count") + " players waiting for rewards");
                                checkAndGiveDailyRankRewards();
                            } else {
                                LOGGER.info("No unclaimed rewards found.");
                            }
                        }
                    }
                }
            }
            
            // Daily Reset Kontrolü
            LOGGER.info("Checking for missed daily reset...");
            Calendar lastDailyReset = getNextResetTime("DAILY");
            lastDailyReset.add(Calendar.DAY_OF_YEAR, -1);
            
            LOGGER.info("Last Daily Reset: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastDailyReset.getTime()));
            LOGGER.info("Next Expected Reset: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastDailyReset.getTimeInMillis() + DAILY_RESET));
            
            if (currentTime.getTimeInMillis() >= lastDailyReset.getTimeInMillis() + DAILY_RESET)
            {
                LOGGER.info("=== PERFORMING MISSED DAILY RESET ===");
                
                // Ödül dağıtımı öncesi durum
                try (PreparedStatement checkPs = con.prepareStatement(
                    "SELECT COUNT(*) as count, SUM(daily_score) as total_score FROM monster_hunter_rankings WHERE daily_score > 0")) 
                {
                    ResultSet checkRs = checkPs.executeQuery();
                    if (checkRs.next()) {
                        LOGGER.info("Pre-reset stats - Active Players: " + checkRs.getInt("count") + 
                                  ", Total Score: " + checkRs.getInt("total_score"));
                    }
                }
                
                // Ödülleri dağıt
                checkAndGiveDailyRankRewards();
                
                // Stats sıfırlama
                try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE monster_hunter_rankings SET daily_kills = 0, daily_score = 0, daily_reward_claimed = 0"))
                {
                    int updated = ps.executeUpdate();
                    LOGGER.info("Reset daily stats for " + updated + " players");
                }
                
                // Yeni reset zamanı
                Calendar newResetTime = Calendar.getInstance();
                newResetTime.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
                newResetTime.set(Calendar.MINUTE, 0);
                newResetTime.set(Calendar.SECOND, 0);
                newResetTime.set(Calendar.MILLISECOND, 0);
                
                if (newResetTime.getTimeInMillis() <= System.currentTimeMillis())
                {
                    newResetTime.add(Calendar.DAY_OF_YEAR, 1);
                }
                
                // Reset zamanı güncelleme
                try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE monster_hunter_reset_times SET last_reset = ? WHERE reset_type = 'DAILY'"))
                {
                    ps.setLong(1, newResetTime.getTimeInMillis());
                    int updated = ps.executeUpdate();
                    LOGGER.info("Updated daily reset time to: " + 
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(newResetTime.getTime()) +
                        " (Updated " + updated + " records)");
                }
            }
            
            // Weekly Reset Kontrolü
            LOGGER.info("Checking for missed weekly reset...");
            Calendar lastWeeklyReset = getNextResetTime("WEEKLY");
            lastWeeklyReset.add(Calendar.WEEK_OF_YEAR, -1);
            
            LOGGER.info("Last Weekly Reset: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastWeeklyReset.getTime()));
            LOGGER.info("Next Expected Weekly Reset: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastWeeklyReset.getTimeInMillis() + WEEKLY_RESET));
            
            if (currentTime.getTimeInMillis() >= lastWeeklyReset.getTimeInMillis() + WEEKLY_RESET)
            {
                LOGGER.info("=== PERFORMING MISSED WEEKLY RESET ===");
                
                // Ödül dağıtımı öncesi durum
                try (PreparedStatement checkPs = con.prepareStatement(
                    "SELECT COUNT(*) as count, SUM(weekly_score) as total_score FROM monster_hunter_rankings WHERE weekly_score > 0")) 
                {
                    ResultSet checkRs = checkPs.executeQuery();
                    if (checkRs.next()) {
                        LOGGER.info("Pre-reset stats - Active Players: " + checkRs.getInt("count") + 
                                  ", Total Score: " + checkRs.getInt("total_score"));
                    }
                }
                
                // Ödülleri dağıt
                checkAndGiveWeeklyRankRewards();
                
                // Stats sıfırlama
                try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE monster_hunter_rankings SET weekly_kills = 0, weekly_score = 0, rank_reward_claimed = 0"))
                {
                    int updated = ps.executeUpdate();
                    LOGGER.info("Reset weekly stats for " + updated + " players");
                }
                
                // Yeni reset zamanı
                Calendar newResetTime = Calendar.getInstance();
                newResetTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                newResetTime.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
                newResetTime.set(Calendar.MINUTE, 0);
                newResetTime.set(Calendar.SECOND, 0);
                newResetTime.set(Calendar.MILLISECOND, 0);
                
                if (newResetTime.getTimeInMillis() <= System.currentTimeMillis())
                {
                    newResetTime.add(Calendar.WEEK_OF_YEAR, 1);
                }
                
                // Reset zamanı güncelleme
                try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE monster_hunter_reset_times SET last_reset = ? WHERE reset_type = 'WEEKLY'"))
                {
                    ps.setLong(1, newResetTime.getTimeInMillis());
                    int updated = ps.executeUpdate();
                    LOGGER.info("Updated weekly reset time to: " + 
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(newResetTime.getTime()) +
                        " (Updated " + updated + " records)");
                }
            }
            
            // Reset zamanları kontrolü ve oluşturma
            LOGGER.info("Checking and initializing reset times...");
            try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO monster_hunter_reset_times (reset_type, last_reset) VALUES (?, ?)"))
            {
                Calendar initTime = Calendar.getInstance();
                initTime.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
                initTime.set(Calendar.MINUTE, 0);
                initTime.set(Calendar.SECOND, 0);
                initTime.set(Calendar.MILLISECOND, 0);
                
                // Daily reset
                ps.setString(1, "DAILY");
                ps.setLong(2, initTime.getTimeInMillis());
                int dailyInserted = ps.executeUpdate();
                
                // Weekly reset
                ps.setString(1, "WEEKLY");
                ps.setLong(2, initTime.getTimeInMillis());
                int weeklyInserted = ps.executeUpdate();
                
                if (dailyInserted > 0 || weeklyInserted > 0) {
                    LOGGER.info("Initialized missing reset times - Daily: " + dailyInserted + ", Weekly: " + weeklyInserted);
                }
            }
            
            LOGGER.info("=== MISSED RESETS CHECK COMPLETED ===");
            
        } catch (SQLException e) {
            LOGGER.warning("Error checking missed resets: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Cache key oluşturucu
    private String getCacheKey(int charId, int monsterId)
    {
        return charId + "_" + monsterId;
    }
    
    // Kill sayısını kontrol eden method
    private int getMonsterKills(Player player, int monsterId)
    {
        String cacheKey = getCacheKey(player.getObjectId(), monsterId);
        
        Integer cachedKills = KILL_CACHE.get(cacheKey);
        if (cachedKills != null)
        {
            return cachedKills;
        }
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT kills FROM monster_kill_tracker WHERE char_id=? AND quest_id=? AND monster_id=?"))
        {
            ps.setInt(1, player.getObjectId());
            ps.setInt(2, 801);
            ps.setInt(3, monsterId);
            
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    int kills = rs.getInt("kills");
                    KILL_CACHE.put(cacheKey, kills);
                    return kills;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return 0;
    }
    
    // Kill sayısını güncelleyen method
    private void updateMonsterKills(Player player, int monsterId, int kills)
    {
        String cacheKey = getCacheKey(player.getObjectId(), monsterId);
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO monster_kill_tracker (char_id, quest_id, monster_id, kills) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE kills=?"))
        {
            ps.setInt(1, player.getObjectId());
            ps.setInt(2, 801);
            ps.setInt(3, monsterId);
            ps.setInt(4, kills);
            ps.setInt(5, kills);
            ps.executeUpdate();
            
            KILL_CACHE.put(cacheKey, kills);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    // Ödül durumunu kontrol eden method
    private boolean isRewardClaimed(Player player, int monsterId, int rewardLevel)
    {
        String cacheKey = getCacheKey(player.getObjectId(), monsterId) + "_reward" + rewardLevel;
        
        Boolean cachedReward = REWARD_CACHE.get(cacheKey);
        if (cachedReward != null)
        {
            return cachedReward;
        }
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT reward" + rewardLevel + "_claimed FROM monster_kill_tracker WHERE char_id=? AND quest_id=? AND monster_id=?"))
        {
            ps.setInt(1, player.getObjectId());
            ps.setInt(2, 801);
            ps.setInt(3, monsterId);
            
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    boolean claimed = rs.getInt("reward" + rewardLevel + "_claimed") == 1;
                    REWARD_CACHE.put(cacheKey, claimed);
                    return claimed;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }
    
    // Ödül alındı olarak işaretleyen method
    private void setRewardClaimed(Player player, int monsterId, int rewardLevel)
    {
        String cacheKey = getCacheKey(player.getObjectId(), monsterId) + "_reward" + rewardLevel;
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE monster_kill_tracker SET reward" + rewardLevel + "_claimed=1 WHERE char_id=? AND quest_id=? AND monster_id=?"))
        {
            ps.setInt(1, player.getObjectId());
            ps.setInt(2, 801);
            ps.setInt(3, monsterId);
            ps.executeUpdate();
            
            REWARD_CACHE.put(cacheKey, true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    public static void newCharacterCreated(Player player)
    {
        if (player == null)
        {
            return;
        }
        
        // Quest instance'ını al
        final Quest quest = QuestManager.getInstance().getQuest("Q00801_MonsterHunterSpecialization");
        if (quest != null)
        {
            final QuestState qs = quest.newQuestState(player);
            if (qs != null)
            {
                quest.notifyEvent("AUTO_START", null, player);
            }
        }
    }
    
    @Override
    public String onEvent(String event, Npc npc, Player player)
    {
        // Player variables ayarı
        player.getVariables().set("MONSTER_HUNTER_DIALOG_SHOWN", "0");
        player.getVariables().storeMe();
        
        if (event == null || event.isEmpty())
        {
            return null;
        }
        
        try
        {
            LOGGER.warning("Monster Hunter Event triggered: " + event);
            
            // Quest kabul etme eventi
            if (event.equals("acceptQuest"))
            {
                QuestState qs = getQuestState(player, true);
                if (qs == null)
                {
                    qs = newQuestState(player);
                }
                if (!qs.isStarted())
                {
                    qs.startQuest();
                    player.sendMessage("You have accepted the Monster Hunter quest!");
                }
                return showMainPage(player);
            }
            
            // Ana sayfa ve arama işlemleri
            if (event.equals("AUTO_START"))
            {
                QuestState qs = getQuestState(player, true);
                if (qs == null)
                {
                    qs = newQuestState(player);
                }
                if (!qs.isStarted())
                {
                    qs.startQuest();
                }
                return null;
            }
            
            if (event.equals("showMain"))
            {
                return showMainPage(player);
            }
            
            if (event.startsWith("searchMain_"))
            {
                String searchTerm = event.substring("searchMain_".length()).trim();
                return showMainSearch(player, searchTerm);
            }
            
            // Temel sayfalar
            if (event.equals("showRecords"))
            {
                return showRecords(player);
            }
            
            if (event.equals("showHelp"))
            {
                return showHelp();
            }
            
            // Sayfalı listeler
            if (event.startsWith("showAll_"))
            {
                int page = Integer.parseInt(event.substring("showAll_".length()));
                return showAllMonsters(player, page, "");
            }
            
            if (event.startsWith("showCompleted_"))
            {
                int page = Integer.parseInt(event.split("_")[1]);
                return showCompletedMonsters(player, page, null);
            }
            
            if (event.startsWith("showInProgress_"))
            {
                int page = Integer.parseInt(event.split("_")[1]);
                return showInProgress(player, page, null);
            }
            
            if (event.startsWith("showRankings_"))
            {
                String[] parts = event.split("_");
                int page = Integer.parseInt(parts[1]);
                String sortBy = parts.length > 2 ? parts[2] : "kills";
                return showRankings(player, page, sortBy);
            }
            
            // Arama işlemleri
            if (event.startsWith("searchAll_"))
            {
                String[] parts = event.substring("searchAll_".length()).split("_");
                String searchTerm = "";
                int page = 1;
                
                if (parts.length > 0)
                {
                    searchTerm = parts[0];
                    if (parts.length > 1)
                    {
                        try
                        {
                            page = Integer.parseInt(parts[1]);
                        }
                        catch (NumberFormatException e)
                        {
                            page = 1;
                        }
                    }
                }
                return showAllMonsters(player, page, searchTerm);
            }
            
            if (event.startsWith("searchCompleted_"))
            {
                String[] parts = event.substring("searchCompleted_".length()).split("_");
                String searchTerm = "";
                int page = 1;
                
                if (parts.length > 0)
                {
                    searchTerm = parts[0];
                    if (parts.length > 1)
                    {
                        try
                        {
                            page = Integer.parseInt(parts[1]);
                        }
                        catch (NumberFormatException e)
                        {
                            page = 1;
                        }
                    }
                }
                return showCompletedMonsters(player, page, searchTerm);
            }
            
            if (event.startsWith("searchInProgress_"))
            {
                String[] parts = event.substring("searchInProgress_".length()).split("_");
                String searchTerm = "";
                int page = 1;
                
                if (parts.length > 0)
                {
                    searchTerm = parts[0];
                    if (parts.length > 1)
                    {
                        try
                        {
                            page = Integer.parseInt(parts[1]);
                        }
                        catch (NumberFormatException e)
                        {
                            page = 1;
                        }
                    }
                }
                return showInProgress(player, page, searchTerm);
            }
            
            // Varsayılan
            return getHtml(player, event);
        }
        catch (Exception e)
        {
            LOGGER.warning("Error in Monster Hunter Specialization quest: " + e.getMessage());
            e.printStackTrace();
            return "An error occurred.";
        }
    }
    
    private void initPlayer(Player player)
    {
        try (Connection con = DatabaseFactory.getConnection())
        {
            // Önce monster_hunter_rankings tablosunda kayıt var mı kontrol et
            try (PreparedStatement ps = con.prepareStatement("SELECT char_id FROM monster_hunter_rankings WHERE char_id = ?"))
            {
                ps.setInt(1, player.getObjectId());
                try (ResultSet rs = ps.executeQuery())
                {
                    if (!rs.next())
                    {
                        // Kayıt yoksa yeni kayıt oluştur
                        try (PreparedStatement insertPs = con.prepareStatement("INSERT INTO monster_hunter_rankings (char_id, weekly_kills, daily_kills, weekly_score, daily_score) VALUES (?, 0, 0, 0, 0)"))
                        {
                            insertPs.setInt(1, player.getObjectId());
                            insertPs.executeUpdate();
                        }
                    }
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Could not initialize player " + player.getName() + ": " + e.getMessage());
        }
    }
    
    public static void printQuestMethods()
    {
        Method[] methods = Quest.class.getDeclaredMethods();
        for (Method method : methods)
        {
            System.out.println("Method: " + method.getName() + " " + Arrays.toString(method.getParameterTypes()));
            
        }
    }
    
    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        QuestState qs = getQuestState(player, true);
        initPlayer(player);
        
        // Quest durumunu kontrol et
        if (qs == null || !qs.isStarted())
        {
            // Quest alınmamış, quest alma sayfasını göster
            return showQuestStartPage(player);
        }
        
        // Quest alınmış, ana sayfayı göster
        return showMainPage(player);
    }
    
    // Yeni metod: Quest başlangıç sayfası
    private String showQuestStartPage(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=270 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=35>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Monster Hunter System</font>");
        sb.append("</td></tr></table><br>");
        
        // Quest açıklaması
        sb.append("<table width=270>");
        sb.append("<tr><td align=center>Welcome to the Monster Hunter System!</td></tr>");
        sb.append("<tr><td align=center>&nbsp;</td></tr>");
        sb.append("<tr><td align=center>Track your monster hunting progress,</td></tr>");
        sb.append("<tr><td align=center>earn rewards and compete with others!</td></tr>");
        sb.append("<tr><td align=center>&nbsp;</td></tr>");
        sb.append("<tr><td align=center>Would you like to start your journey?</td></tr>");
        sb.append("</table><br>");
        
        // Quest başlatma butonu
        sb.append("<table width=270>");
        sb.append("<tr><td align=center>");
        sb.append("<button value=\"Accept Quest\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization acceptQuest\" width=120 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</td></tr>");
        sb.append("</table>");
        
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    private String showMainSearch(Player player, String search)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=270 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=35>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Search Results</font>");
        sb.append("</td></tr></table>");
        
        // Monster listesi
        List<MonsterInfo> monsters = new ArrayList<>(MONSTER_INFO.values());
        boolean found = false;
        int monsterCount = 0; // Sayaç ekledik
        
        for (MonsterInfo monster : monsters)
        {
            if (monster.monsterName.toLowerCase().contains(search.toLowerCase()))
            {
                found = true;
                monsterCount++; // Her monster için sayacı artır
                
                // 12'den fazla monster varsa döngüyü bitir
                if (monsterCount > 8)
                {
                    break;
                }
                
                int kills = getMonsterKills(player, monster.monsterId);
                
                // Monster container
                sb.append("<table width=270 bgcolor=\"333333\">");
                sb.append("<tr><td>");
                
                // Monster ismi ve Score
                sb.append("<table width=270><tr>");
                String monsterName = monster.monsterName;
                if (monsterName.length() > 30)
                {
                    monsterName = monsterName.substring(0, 17) + "...";
                }
                sb.append("<td width=200 style=\"overflow:hidden;white-space:nowrap;\">&nbsp;<font color=\"LEVEL\" size=-2>").append(monsterName).append("</font></td>");
                sb.append("<td width=70 align=right><font color=\"LEVEL\" size=-2>Poins: ").append(monster.getBaseScore()).append("</font>&nbsp;&nbsp;</td>");
                sb.append("</tr></table>");
                
                // Stage durumları
                for (int stage = 1; stage <= 10; stage++)
                {
                    int requiredKills = monster.requiredKills[stage - 1];
                    boolean isCompleted = kills >= requiredKills;
                    
                    String statusColor;
                    if (isCompleted)
                    {
                        switch (stage)
                        {
                            case 1:
                                statusColor = "FFA500"; // Açık sarı
                                break;
                            case 2:
                                statusColor = "FFA500"; // Sarı "FFA500"; // Açık sarı
                                break;
                            case 3:
                                statusColor = "DAA520"; // Koyu sarı "DAA520"; // Koyu sarı
                                break;
                            case 4:
                                statusColor = "DAA520"; // Açık yeşil "DAA520"; // Koyu sarı
                                break;
                            case 5:
                                statusColor = "FFD700"; // Açık sarı "FFD700"; // Sarı
                                break;
                            case 6:
                                statusColor = "FFD700"; // Açık sarı "FFD700"; // Sarı
                                break;
                            case 7:
                                statusColor = "98FB98"; // Koyu sarı "98FB98"; // Açık yeşil
                                break;
                            case 8:
                                statusColor = "98FB98"; // Koyu sarı "98FB98"; // Açık yeşil
                                break;
                            case 9:
                                statusColor = "32CD32"; // Yeşil "32CD32"; // Yeşil
                                break;
                            case 10:
                                statusColor = "32CD32"; // Yeşil "32CD32"; // Yeşil
                                break;
                            default:
                                statusColor = "FFFFFF";
                        }
                        
                        sb.append("<table width=270><tr>");
                        sb.append("<td width=80 align=left>&nbsp;<font size=-2>Stage ").append(stage).append("</font></td>");
                        sb.append("<td width=190 align=right><font color=\"").append(statusColor).append("\" size=-2>Completed</font>&nbsp;&nbsp;</td>");
                        sb.append("</tr></table>");
                    }
                    else
                    {
                        sb.append("<table width=270><tr>");
                        sb.append("<td width=80 align=left>&nbsp;<font size=-2>Stage ").append(stage).append("</font></td>");
                        sb.append("<td width=190 align=right><font size=-2>").append(kills).append("/").append(requiredKills).append("</font>&nbsp;&nbsp;</td>");
                        sb.append("</tr></table>");
                    }
                }
                
                sb.append("</td></tr></table>");
                
                // Monster arası boşluk
                sb.append("<table width=270 height=3><tr><td></td></tr></table>");
            }
        }
        
        if (!found)
        {
            sb.append("<table width=270 height=40><tr><td align=center>");
            sb.append("<font color=\"FF0000\">No monsters found matching your search.</font>");
            sb.append("</td></tr></table>");
        }
        
        // Geri dönüş butonu
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    private String formatNumber(int number)
    {
        DecimalFormat formatter = new DecimalFormat("#,###");
        return formatter.format(number).replace(",", ".");
        
    }
    
    private String showMainPage(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Ana başlık
        sb.append("<table width=270 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=35>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Monster Hunter System</font>");
        sb.append("</td></tr></table>");
        
        // Quick Access Buttons - 3'lü grup
        sb.append("<table width=270 height=35 bgcolor=\"333333\">");
        sb.append("<tr>");
        sb.append("<td align=center><button value=\"All\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showAll_1\" width=85 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("<td align=center><button value=\"Completed\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showCompleted_1\" width=85 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("<td align=center><button value=\"In Progress\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showInProgress_1\" width=85 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr></table>");
        
        // Arama kutusu - Daha kompakt
        sb.append("<table width=270 height=35>");
        sb.append("<tr><td align=center>");
        sb.append("<table width=250><tr>");
        sb.append("<td width=180><edit var=\"search\" width=175 height=15></td>");
        sb.append("<td width=70><button value=\"Search\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchMain_ $search\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr></table>");
        sb.append("</td></tr></table>");
        
        // İstatistikler
        final QuestState st = getQuestState(player, false);
        if (st != null && st.isStarted())
        {
            // İstatistik hesaplamaları
            int totalMonsters = MONSTER_INFO.size();
            int completedMonsters = 0;
            int inProgressMonsters = 0;
            int notStartedMonsters = 0;
            int totalKills = 0;
            int totalRewards = 0;
            int highestKills = 0;
            String mostKilledMonster = "";
            
            // Weekly ranking istatistikleri
            int weeklyScore = 0;
            int weeklyRank = 0;
            int dailyScore = 0;
            int dailyRank = 0;
            
            try (Connection con = DatabaseFactory.getConnection())
            {
                // Weekly ve Daily scores
                try (PreparedStatement ps = con.prepareStatement("SELECT weekly_score, daily_score FROM monster_hunter_rankings WHERE char_id = ?"))
                {
                    ps.setInt(1, player.getObjectId());
                    try (ResultSet rs = ps.executeQuery())
                    {
                        if (rs.next())
                        {
                            weeklyScore = rs.getInt("weekly_score");
                            dailyScore = rs.getInt("daily_score");
                        }
                    }
                }
                
                // Weekly rank
                try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) + 1 as rank FROM monster_hunter_rankings WHERE weekly_score > " + "(SELECT COALESCE(weekly_score, 0) FROM monster_hunter_rankings WHERE char_id = ?)"))
                {
                    ps.setInt(1, player.getObjectId());
                    try (ResultSet rs = ps.executeQuery())
                    {
                        if (rs.next())
                        {
                            weeklyRank = rs.getInt("rank");
                        }
                    }
                }
                
                // Daily rank
                try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) + 1 as rank FROM monster_hunter_rankings WHERE daily_score > " + "(SELECT COALESCE(daily_score, 0) FROM monster_hunter_rankings WHERE char_id = ?)"))
                {
                    ps.setInt(1, player.getObjectId());
                    try (ResultSet rs = ps.executeQuery())
                    {
                        if (rs.next())
                        {
                            dailyRank = rs.getInt("rank");
                        }
                    }
                }
            }
            catch (SQLException e)
            {
                LOGGER.warning("Error while getting ranking stats: " + e.getMessage());
            }
            
            for (MonsterInfo monster : MONSTER_INFO.values())
            {
                int currentKills = getMonsterKills(player, monster.monsterId);
                totalKills += currentKills;
                
                // En çok öldürülen monster
                if (currentKills > highestKills)
                {
                    highestKills = currentKills;
                    mostKilledMonster = monster.monsterName;
                }
                
                int maxRequired = Arrays.stream(monster.requiredKills).max().getAsInt();
                if (currentKills >= maxRequired)
                {
                    completedMonsters++;
                }
                else if (currentKills > 0)
                {
                    inProgressMonsters++;
                }
                else
                {
                    notStartedMonsters++;
                }
                
                // Toplam alınan ödüller
                for (int i = 0; i < 5; i++)
                {
                    if (isRewardClaimed(player, monster.monsterId, i + 1))
                    {
                        totalRewards++;
                    }
                }
            }
            
            // İstatistik Tabloları
            // Genel İstatistikler
            sb.append("<table width=270>");
            sb.append("<tr><td><font color=\"LEVEL\">General Statistics</font></td></tr>");
            sb.append("<tr><td>");
            
            // İç tablo - İstatistikler
            sb.append("<table width=260>");
            sb.append("<tr><td>Total Kills:</td><td align=right><font color=\"LEVEL\">").append(formatNumber(totalKills)).append("</font></td></tr>");
            sb.append("<tr><td>Most Killed:</td><td align=right><font color=\"00FF00\">").append(mostKilledMonster).append(" (").append(formatNumber(highestKills)).append(")</font></td></tr>");
            sb.append("<tr><td>Total Rewards:</td><td align=right><font color=\"LEVEL\">").append(formatNumber(totalRewards)).append("</font></td></tr>");
            sb.append("<tr><td>Weekly Score:</td><td align=right><font color=\"LEVEL\">").append(formatNumber(weeklyScore)).append("</font></td></tr>");
            if (weeklyRank > 0)
            {
                sb.append("<tr><td>Weekly Rank:</td><td align=right><font color=\"00FF00\">#").append(weeklyRank).append("</font></td></tr>");
            }
            sb.append("<tr><td>Daily Score:</td><td align=right><font color=\"LEVEL\">").append(formatNumber(dailyScore)).append("</font></td></tr>");
            if (dailyRank > 0)
            {
                sb.append("<tr><td>Daily Rank:</td><td align=right><font color=\"00FF00\">#").append(dailyRank).append("</font></td></tr>");
            }
            sb.append("</table>");
            
            sb.append("</td></tr></table>");
            
            // Progress Status
            sb.append("<table width=270>");
            sb.append("<tr><td><font color=\"LEVEL\">Progress Status</font></td></tr>");
            sb.append("<tr><td>");
            
            // İç tablo - Progress
            sb.append("<table width=260>");
            sb.append("<tr><td>Completed:</td><td align=right><font color=\"00FF00\">").append(completedMonsters).append("/").append(totalMonsters).append("</font></td></tr>");
            sb.append("<tr><td>In Progress:</td><td align=right><font color=\"LEVEL\">").append(inProgressMonsters).append("</font></td></tr>");
            sb.append("<tr><td>Not Started:</td><td align=right><font color=\"FF0000\">").append(notStartedMonsters).append("</font></td></tr>");
            double completionPercentage = (completedMonsters * 100.0) / totalMonsters;
            sb.append("<tr><td>Completion Rate:</td><td align=right><font color=\"00FF00\">").append(String.format("%.1f", completionPercentage)).append("%</font></td></tr>");
            sb.append("</table>");
            
            sb.append("</td></tr></table>");
        }
        
        // Alt Menü Butonları
        sb.append("<table width=270>");
        sb.append("<tr>");
        sb.append("<td align=center width=90><button value=\"Rankings\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_1\" width=85 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("<td align=center width=90><button value=\"Records\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRecords\" width=85 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("<td align=center width=90><button value=\"Help\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showHelp\" width=85 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr>");
        sb.append("</table>");
        
        // Süre Bilgileri Tablosu
        sb.append("<table width=270 bgcolor=\"222222\">");
        sb.append("<tr><td align=center height=25><font color=\"LEVEL\">Time Information</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270 bgcolor=\"333333\">");
        
        // Reset zamanları için Calendar objeleri
        Calendar weeklyReset = Calendar.getInstance();
        Calendar dailyReset = Calendar.getInstance();
        
        // Günlük reset için
        dailyReset.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
        dailyReset.set(Calendar.MINUTE, 0);
        dailyReset.set(Calendar.SECOND, 0);
        dailyReset.set(Calendar.MILLISECOND, 0);
        
        // Eğer bugünkü reset zamanı geçtiyse, yarına ayarla
        if (dailyReset.getTimeInMillis() <= System.currentTimeMillis())
        {
            dailyReset.add(Calendar.DAY_OF_YEAR, 1);
        }
        
        // Haftalık reset için
        weeklyReset.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        weeklyReset.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
        weeklyReset.set(Calendar.MINUTE, 0);
        weeklyReset.set(Calendar.SECOND, 0);
        weeklyReset.set(Calendar.MILLISECOND, 0);
        
        // Eğer bu haftaki reset zamanı geçtiyse, gelecek haftaya ayarla
        if (weeklyReset.getTimeInMillis() <= System.currentTimeMillis())
        {
            weeklyReset.add(Calendar.WEEK_OF_YEAR, 1);
        }
        
        // Reset zamanları için Calendar objeleri
        long weeklyTimeLeft = getNextWeeklyResetTime();
        long dailyTimeLeft = getNextDailyResetTime();
        
        // Weekly süre hesaplamaları (millisaniyeden dönüştür)
        long weeklyDays = weeklyTimeLeft / (24 * 3600000);
        long weeklyHours = (weeklyTimeLeft % (24 * 3600000)) / 3600000;
        long weeklyMinutes = (weeklyTimeLeft % 3600000) / 60000;
        
        // Daily süre hesaplamaları (millisaniyeden dönüştür)
        long dailyHours = dailyTimeLeft / 3600000;
        long dailyMinutes = (dailyTimeLeft % 3600000) / 60000;
        
        // Süre bilgilerini tabloda göster
        sb.append("<tr><td align=left>&nbsp;<font color=\"AAAAAA\">Weekly Reset:</font> ");
        sb.append("<font color=\"FFFFFF\">").append(weeklyDays).append("d ").append(weeklyHours).append("h ").append(weeklyMinutes).append("m</font></td></tr>");
        
        sb.append("<tr><td align=left>&nbsp;<font color=\"AAAAAA\">Daily Reset:</font> ");
        sb.append("<font color=\"FFFFFF\">").append(dailyHours).append("h ").append(dailyMinutes).append("m</font></td></tr>");
        
        // Next Weekly Reset Date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        sb.append("<tr><td align=left>&nbsp;<font color=\"AAAAAA\">Next Weekly Reset:</font> ");
        sb.append("<font color=\"FFFFFF\">").append(sdf.format(weeklyReset.getTime())).append("</font></td></tr>");
        
        // Next Daily Reset Date
        sb.append("<tr><td align=left>&nbsp;<font color=\"AAAAAA\">Next Daily Reset:</font> ");
        sb.append("<font color=\"FFFFFF\">").append(sdf.format(dailyReset.getTime())).append("</font></td></tr>");
        
        sb.append("</table>");
        
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    // Her sayfanın arama kısmını bu şekilde düzenleyelim
    private List<MonsterInfo> getFilteredMonsters(Player player, String search, String type)
    {
        List<MonsterInfo> filteredList = new ArrayList<>();
        
        // Search terimini trim ediyoruz (başındaki ve sonundaki boşlukları siliyoruz)
        if (search != null)
        {
            search = search.trim();
        }
        
        System.out.println("=== FILTER DEBUG ===");
        System.out.println("Search term after trim: '" + search + "'");
        
        for (MonsterInfo monster : MONSTER_INFO.values())
        {
            int kills = getMonsterKills(player, monster.monsterId);
            int maxRequired = Arrays.stream(monster.requiredKills).max().getAsInt();
            
            boolean matchesSearch = (search == null || search.isEmpty() || monster.monsterName.toLowerCase().contains(search.toLowerCase().trim()));
            
            if (!matchesSearch)
            {
                continue;
            }
            
            switch (type)
            {
                case "completed":
                    if (kills >= maxRequired)
                    {
                        filteredList.add(monster);
                    }
                    break;
                case "inProgress":
                    if (kills > 0 && kills < maxRequired)
                    {
                        filteredList.add(monster);
                    }
                    break;
                case "all":
                    filteredList.add(monster);
                    break;
            }
        }
        
        return filteredList;
    }
    
    // Completed Monsters sayfası için yeni metod
    private String showCompletedMonsters(Player player, int page, String search)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=300 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=40>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Completed Monsters</font>");
        sb.append("</td></tr></table>");
        
        // Arama kutusu
        sb.append("<table width=300 height=45 bgcolor=\"333333\">");
        sb.append("<tr><td align=center>");
        sb.append("<table width=280><tr>");
        sb.append("<td width=210><edit var=\"search\" width=205 height=15 align=left></td>");
        sb.append("<td width=70><button value=\"Search\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchCompleted_ $search\" width=70 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr></table>");
        sb.append("</td></tr></table>");
        
        // Liste başlığı
        sb.append("<table width=300 bgcolor=\"222222\">");
        sb.append("<tr>");
        sb.append("<td width=160 align=center><font color=\"LEVEL\">Monster</font></td>");
        sb.append("<td width=70 align=center><font color=\"LEVEL\">Level</font></td>");
        sb.append("<td width=70 align=center><font color=\"LEVEL\">Kills</font></td>");
        sb.append("</tr></table>");
        
        // Tamamlanmış monster'ları listele
        List<MonsterInfo> filteredMonsters = getFilteredMonsters(player, search, "completed");
        
        // Liste boşsa mesaj göster
        if (filteredMonsters.isEmpty())
        {
            sb.append("<table width=300 height=40><tr><td align=center>");
            if (search != null && !search.isEmpty())
            {
                sb.append("<font color=\"FF0000\">No completed monsters found matching your search.</font>");
            }
            else
            {
                sb.append("<font color=\"FF0000\">You haven't completed any monsters yet.</font>");
            }
            sb.append("</td></tr></table>");
        }
        else
        {
            // Sayfalama
            int monstersPerPage = 10;
            int totalPages = (filteredMonsters.size() + monstersPerPage - 1) / monstersPerPage;
            page = Math.min(Math.max(1, page), totalPages);
            int startIndex = (page - 1) * monstersPerPage;
            int endIndex = Math.min(startIndex + monstersPerPage, filteredMonsters.size());
            
            // Monster listesi
            for (int i = startIndex; i < endIndex; i++)
            {
                MonsterInfo monster = filteredMonsters.get(i);
                int currentKills = getMonsterKills(player, monster.monsterId);
                int currentLevel = getCurrentLevel(currentKills, monster.requiredKills);
                
                String bgColor = (i % 2 == 0) ? "333333" : "292929";
                sb.append("<table width=300 bgcolor=\"").append(bgColor).append("\">");
                sb.append("<tr>");
                sb.append("<td width=160 align=center>").append(monster.monsterName).append("</td>");
                sb.append("<td width=70 align=center>").append(currentLevel).append("</td>");
                sb.append("<td width=70 align=center>").append(currentKills).append("</td>");
                sb.append("</tr></table>");
            }
            
            // Sayfalama butonları
            sb.append("<table width=300 height=40><tr>");
            if (page > 1)
            {
                if (!search.isEmpty())
                {
                    sb.append("<td width=100 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchCompleted_").append(search).append("_").append(page - 1).append("\" width=70 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=100 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showCompleted_").append(page - 1).append("\" width=70 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
            }
            else
            {
                sb.append("<td width=100></td>");
            }
            
            sb.append("<td width=100 align=center><font color=\"LEVEL\">Page ").append(page).append("/").append(totalPages).append("</font></td>");
            
            if (page < totalPages)
            {
                if (!search.isEmpty())
                {
                    sb.append("<td width=100 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchCompleted_").append(search).append("_").append(page + 1).append("\" width=70 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=100 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showCompleted_").append(page + 1).append("\" width=70 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
            }
            else
            {
                sb.append("<td width=100></td>");
            }
            sb.append("</tr></table>");
        }
        
        // Geri dönüş butonu
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    private String showRankings(Player player, int page, String sortBy)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=300 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=40>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Monster Hunter Rankings</font>");
        sb.append("</td></tr></table><br>");
        
        // Sıralama seçenekleri
        sb.append("<table width=300>");
        sb.append("<tr>");
        sb.append("<td align=center><button value=\"Weekly Score\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_1_weekly\" width=100 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("<td align=center><button value=\"Daily Score\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_1_daily\" width=100 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td align=center><button value=\"Total Kills\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_1_kills\" width=100 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("<td align=center><button value=\"Completed\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_1_completed\" width=100 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr></table><br>");
        
        // Liste başlığı
        sb.append("<table width=300 bgcolor=\"222222\">");
        sb.append("<tr>");
        sb.append("<td width=40 align=center><font color=\"LEVEL\">#</font></td>");
        sb.append("<td width=130 align=center><font color=\"LEVEL\">Character</font></td>");
        
        // Sıralama tipine göre başlık
        if (sortBy.equals("weekly"))
        {
            sb.append("<td width=130 align=center><font color=\"LEVEL\">Weekly Score</font></td>");
        }
        else if (sortBy.equals("daily"))
        {
            sb.append("<td width=130 align=center><font color=\"LEVEL\">Daily Score</font></td>");
        }
        else if (sortBy.equals("kills"))
        {
            sb.append("<td width=130 align=center><font color=\"LEVEL\">Total Kills</font></td>");
        }
        else
        {
            sb.append("<td width=130 align=center><font color=\"LEVEL\">Completed</font></td>");
        }
        sb.append("</tr></table>");
        
        // Sıralama listesini al
        List<PlayerRankInfo> rankings = new ArrayList<>();
        String sql;
        
        if (sortBy.equals("weekly"))
        {
            sql = "SELECT c.char_name, c.charId, " + "COALESCE(SUM(mkt.kills), 0) as total_kills, " + "SUM(CASE WHEN mkt.reward5_claimed = 1 THEN 1 ELSE 0 END) as completed_monsters, " + "COALESCE(mhr.weekly_kills, 0) as weekly_kills, " + "COALESCE(mhr.weekly_score, 0) as weekly_score, " + "COALESCE(mhr.daily_kills, 0) as daily_kills " + "FROM characters c " + "LEFT JOIN monster_kill_tracker mkt ON c.charId = mkt.char_id AND mkt.quest_id = 801 " + "LEFT JOIN monster_hunter_rankings mhr ON c.charId = mhr.char_id " + "GROUP BY c.charId, c.char_name " + "ORDER BY weekly_score DESC, weekly_kills DESC";
        }
        else if (sortBy.equals("daily"))
        {
            sql = "SELECT c.char_name, c.charId, " + "COALESCE(SUM(mkt.kills), 0) as total_kills, " + "SUM(CASE WHEN mkt.reward5_claimed = 1 THEN 1 ELSE 0 END) as completed_monsters, " + "COALESCE(mhr.weekly_kills, 0) as weekly_kills, " + "COALESCE(mhr.daily_kills, 0) as daily_kills, " + "COALESCE(mhr.daily_score, 0) as daily_score " + "FROM characters c " + "LEFT JOIN monster_kill_tracker mkt ON c.charId = mkt.char_id AND mkt.quest_id = 801 " + "LEFT JOIN monster_hunter_rankings mhr ON c.charId = mhr.char_id " + "GROUP BY c.charId, c.char_name " + "ORDER BY daily_score DESC, daily_kills DESC";
        }
        else
        {
            sql = "SELECT c.char_name, c.charId, " + "COALESCE(SUM(mkt.kills), 0) as total_kills, " + "SUM(CASE WHEN mkt.reward5_claimed = 1 THEN 1 ELSE 0 END) as completed_monsters, " + "COALESCE(mhr.weekly_kills, 0) as weekly_kills, " + "COALESCE(mhr.daily_kills, 0) as daily_kills " + "FROM characters c " + "LEFT JOIN monster_kill_tracker mkt ON c.charId = mkt.char_id AND mkt.quest_id = 801 " + "LEFT JOIN monster_hunter_rankings mhr ON c.charId = mhr.char_id " + "GROUP BY c.charId, c.char_name " + "ORDER BY " + (sortBy.equals("kills") ? "total_kills" : "completed_monsters") + " DESC";
        }
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement(sql))
        {
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    String charName = rs.getString("char_name");
                    int charId = rs.getInt("charId");
                    int totalKills = rs.getInt("total_kills");
                    int completedMonsters = rs.getInt("completed_monsters");
                    int weeklyKills = rs.getInt("weekly_kills");
                    int dailyKills = rs.getInt("daily_kills");
                    
                    if (sortBy.equals("weekly"))
                    {
                        int weeklyScore = rs.getInt("weekly_score");
                        rankings.add(new PlayerRankInfo(charName, totalKills, completedMonsters, weeklyKills, dailyKills, charId, weeklyScore, 0));
                    }
                    else if (sortBy.equals("daily"))
                    {
                        int dailyScore = rs.getInt("daily_score");
                        rankings.add(new PlayerRankInfo(charName, totalKills, completedMonsters, weeklyKills, dailyKills, charId, 0, dailyScore));
                    }
                    else
                    {
                        rankings.add(new PlayerRankInfo(charName, totalKills, completedMonsters, weeklyKills, dailyKills, charId));
                    }
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error while getting ranking data: " + e.getMessage());
        }
        
        // Liste gösterimi
        if (rankings.isEmpty())
        {
            sb.append("<table width=300 height=40><tr><td align=center>");
            sb.append("<font color=\"FF0000\">No rankings available yet.</font>");
            sb.append("</td></tr></table>");
        }
        else
        {
            // Sayfalama
            int playersPerPage = 100;
            int totalPages = (rankings.size() + playersPerPage - 1) / playersPerPage;
            page = Math.min(Math.max(1, page), totalPages);
            int startIndex = (page - 1) * playersPerPage;
            int endIndex = Math.min(startIndex + playersPerPage, rankings.size());
            
            // Sıralama listesi
            for (int i = startIndex; i < endIndex; i++)
            {
                PlayerRankInfo rankInfo = rankings.get(i);
                String bgColor = (i % 2 == 0) ? "333333" : "292929";
                String textColor = (rankInfo.charId == player.getObjectId()) ? "00FF00" : "FFFFFF";
                
                sb.append("<table width=300 bgcolor=\"").append(bgColor).append("\">");
                sb.append("<tr>");
                sb.append("<td width=40 align=center><font color=\"").append(textColor).append("\">").append(i + 1).append("</font></td>");
                sb.append("<td width=130 align=center><font color=\"").append(textColor).append("\">").append(rankInfo.charName).append("</font></td>");
                
                if (sortBy.equals("weekly"))
                {
                    sb.append("<td width=130 align=center><font color=\"").append(textColor).append("\">").append(formatNumber(rankInfo.weeklyScore)).append("</font></td>");
                }
                else if (sortBy.equals("daily"))
                {
                    sb.append("<td width=130 align=center><font color=\"").append(textColor).append("\">").append(formatNumber(rankInfo.dailyScore)).append("</font></td>");
                }
                else if (sortBy.equals("kills"))
                {
                    sb.append("<td width=130 align=center><font color=\"").append(textColor).append("\">").append(formatNumber(rankInfo.totalKills)).append("</font></td>");
                }
                else
                {
                    sb.append("<td width=130 align=center><font color=\"").append(textColor).append("\">").append(formatNumber(rankInfo.completedMonsters)).append("</font></td>");
                }
                sb.append("</tr></table>");
            }
            
            // Sayfalama butonları
            if (totalPages > 1)
            {
                sb.append("<table width=270 height=40><tr>");
                if (page > 1)
                {
                    sb.append("<td width=90 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_").append(page - 1).append("_").append(sortBy).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=90></td>");
                }
                
                sb.append("<td width=90 align=center><font color=\"LEVEL\">Page ").append(page).append("/").append(totalPages).append("</font></td>");
                
                if (page < totalPages)
                {
                    sb.append("<td width=90 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showRankings_").append(page + 1).append("_").append(sortBy).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=90></td>");
                }
                sb.append("</tr></table>");
            }
        }
        
        // Geri dönüş butonu
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    // Güvenli HTML alma metodu
    private String getHtml(Player player, String fileName)
    {
        String content = getHtm(player, "data/scripts/quests/Q00801_MonsterHunterSpecialization/" + fileName);
        if (content == null)
        {
            LOGGER.warning("Missing HTML page: " + fileName);
            return "File not found: " + fileName;
        }
        return content;
    }
    
    private String showAllMonsters(Player player, int page, String search)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=270 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=35>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">All Monsters</font>");
        sb.append("</td></tr></table>");
        
        // Arama kutusu
        sb.append("<table width=270 height=35 bgcolor=\"333333\">");
        sb.append("<tr><td align=center>");
        sb.append("<table width=250><tr>");
        sb.append("<td width=180><edit var=\"search\" width=175 height=15 align=left></td>");
        sb.append("<td width=70><button value=\"Search\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchAll_ $search\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr></table>");
        sb.append("</td></tr></table>");
        
        // Liste başlığı
        sb.append("<table width=270 bgcolor=\"222222\">");
        sb.append("<tr>");
        sb.append("<td width=180 align=left><font color=\"LEVEL\" size=-2>&nbsp;Monster</font></td>");
        sb.append("<td width=45 align=center><font color=\"LEVEL\" size=-2>Score</font></td>");
        sb.append("<td width=45 align=center><font color=\"LEVEL\" size=-2>Kills</font></td>");
        sb.append("</tr></table>");
        
        // Monster listesi
        List<MonsterInfo> filteredMonsters = getFilteredMonsters(player, search, "all");
        
        // Liste boşsa mesaj göster
        if (filteredMonsters.isEmpty())
        {
            sb.append("<table width=270 height=40><tr><td align=center>");
            sb.append("<font color=\"FF0000\">No monsters found matching your search.</font>");
            sb.append("</td></tr></table>");
        }
        else
        {
            // Sayfalama
            int monstersPerPage = 10;
            int totalPages = (filteredMonsters.size() + monstersPerPage - 1) / monstersPerPage;
            page = Math.min(Math.max(1, page), totalPages);
            int startIndex = (page - 1) * monstersPerPage;
            int endIndex = Math.min(startIndex + monstersPerPage, filteredMonsters.size());
            
            // Monster listesi
            for (int i = startIndex; i < endIndex; i++)
            {
                MonsterInfo monster = filteredMonsters.get(i);
                int currentKills = getMonsterKills(player, monster.monsterId);
                
                // İsim düzenlemesi
                String monsterName = monster.monsterName;
                if (monsterName.length() > 25) // Daha uzun isimler gösterebiliriz artık
                {
                    monsterName = monsterName.substring(0, 22) + "...";
                }
                
                String bgColor = (i % 2 == 0) ? "333333" : "292929";
                sb.append("<table width=270 bgcolor=\"").append(bgColor).append("\">");
                sb.append("<tr>");
                sb.append("<td width=180 style=\"overflow:hidden;white-space:nowrap;\"><font size=-2>&nbsp;").append(monsterName).append("</font></td>");
                sb.append("<td width=45 align=center><font size=-2>").append(monster.getBaseScore()).append("</font></td>");
                sb.append("<td width=45 align=center><font size=-2>").append(currentKills).append("</font></td>");
                sb.append("</tr></table>");
            }
            
            // Sayfalama butonları
            sb.append("<table width=270 height=40><tr>");
            if (page > 1)
            {
                if (search != null && !search.isEmpty())
                {
                    sb.append("<td width=90 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchAll_").append(search).append("_").append(page - 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=90 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showAll_").append(page - 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
            }
            else
            {
                sb.append("<td width=90></td>");
            }
            
            sb.append("<td width=90 align=center><font color=\"LEVEL\">Page ").append(page).append("/").append(totalPages).append("</font></td>");
            
            if (page < totalPages)
            {
                if (search != null && !search.isEmpty())
                {
                    sb.append("<td width=90 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchAll_").append(search).append("_").append(page + 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=90 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showAll_").append(page + 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
            }
            else
            {
                sb.append("<td width=90></td>");
            }
            sb.append("</tr></table>");
        }
        
        // Geri dönüş butonu
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    private String showRecords(Player player)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=300 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=40>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Your Monster Hunter Records</font>");
        sb.append("</td></tr></table><br>");
        
        // İstatistikler
        int totalKills = 0;
        int completedMonsters = 0;
        int inProgressMonsters = 0;
        MonsterInfo highestKillMonster = null;
        int highestKills = 0;
        
        // İstatistikleri hesapla
        for (MonsterInfo monster : MONSTER_INFO.values())
        {
            int kills = getMonsterKills(player, monster.monsterId);
            totalKills += kills;
            
            if (kills >= Arrays.stream(monster.requiredKills).max().getAsInt())
            {
                completedMonsters++;
            }
            else if (kills > 0)
            {
                inProgressMonsters++;
            }
            
            if (kills > highestKills)
            {
                highestKills = kills;
                highestKillMonster = monster;
            }
        }
        
        // Genel İstatistikler
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">General Statistics</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td width=130>Total Kills:</td><td width=140 align=right><font color=\"LEVEL\">").append(totalKills).append("</font></td></tr>");
        sb.append("<tr><td>Completed Monsters:</td><td align=right><font color=\"00FF00\">").append(completedMonsters).append("</font></td></tr>");
        sb.append("<tr><td>In Progress:</td><td align=right><font color=\"LEVEL\">").append(inProgressMonsters).append("</font></td></tr>");
        sb.append("<tr><td>Completion Rate:</td><td align=right><font color=\"00FF00\">").append(String.format("%.1f", (completedMonsters * 100.0 / MONSTER_INFO.size()))).append("%</font></td></tr>");
        sb.append("</table>");
        
        // En Yüksek Kill Sayısı
        if (highestKillMonster != null)
        {
            sb.append("<table width=270 bgcolor=\"333333\">");
            sb.append("<tr><td><font color=\"LEVEL\">Highest Kills</font></td></tr>");
            sb.append("</table>");
            
            sb.append("<table width=270>");
            sb.append("<tr><td width=130>Monster:</td><td width=140 align=right><font color=\"00FF00\">").append(highestKillMonster.monsterName).append("</font></td></tr>");
            sb.append("<tr><td>Kills:</td><td align=right><font color=\"LEVEL\">").append(highestKills).append("</font></td></tr>");
            sb.append("</table>");
        }
        
        // Son Tamamlanan Canavarlar (son 5)
        List<MonsterInfo> recentlyCompleted = new ArrayList<>();
        for (MonsterInfo monster : MONSTER_INFO.values())
        {
            int kills = getMonsterKills(player, monster.monsterId);
            if (kills >= Arrays.stream(monster.requiredKills).max().getAsInt())
            {
                recentlyCompleted.add(monster);
            }
        }
        
        if (!recentlyCompleted.isEmpty())
        {
            sb.append("<table width=270 bgcolor=\"333333\">");
            sb.append("<tr><td><font color=\"LEVEL\">Recently Completed</font></td></tr>");
            sb.append("</table>");
            
            sb.append("<table width=270>");
            int count = Math.min(5, recentlyCompleted.size());
            for (int i = 0; i < count; i++)
            {
                MonsterInfo monster = recentlyCompleted.get(i);
                int kills = getMonsterKills(player, monster.monsterId);
                sb.append("<tr><td width=130>").append(monster.monsterName).append(":</td>");
                sb.append("<td width=140 align=right><font color=\"00FF00\">").append(kills).append(" kills</font></td></tr>");
            }
            sb.append("</table>");
        }
        
        // Geri dönüş butonu
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    private String showHelp()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Title
        sb.append("<table width=270 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=35>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">Monster Hunter System Guide</font>");
        sb.append("</td></tr></table>");
        
        // Stage System
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">Stage System</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td>Each monster has 5 stages of mastery. Required kills for each stage vary based on monster level.</td></tr>");
        sb.append("</table>");
        
        // Bonuses
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">Monster-Specific Stage Bonuses</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td>For each mastered monster, you receive:</td></tr>");
        sb.append("<tr><td>• <font color=\"00FF00\">+10% Drop Rate</font> bonus from that specific monster per stage</td></tr>");
        sb.append("<tr><td>• <font color=\"00FF00\">+10% EXP Bonus</font> when killing that specific monster per stage</td></tr>");
        sb.append("<tr><td>• <font color=\"00FF00\">+20% Score Bonus</font> for that specific monster's points per stage</td></tr>");
        sb.append("<tr><td>• Unique rewards at each stage completion</td></tr>");
        sb.append("</table>");
        
        // Example Table
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">Example</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td>If you reach Stage 3 with Doom Knight:</td></tr>");
        sb.append("<tr><td>• <font color=\"00FF00\">+30% Drop Rate</font> from Doom Knight only</td></tr>");
        sb.append("<tr><td>• <font color=\"00FF00\">+30% EXP</font> from Doom Knight only</td></tr>");
        sb.append("<tr><td>• <font color=\"00FF00\">+60% Score</font> from Doom Knight only</td></tr>");
        sb.append("</table>");
        
        // Ranking System
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">Ranking Rewards</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td><font color=\"LEVEL\">Daily Rewards (Top 100)</font></td></tr>");
        sb.append("<tr><td>1st Place: <font color=\"00FF00\">50 Coins</font></td></tr>");
        sb.append("<tr><td>2nd Place: <font color=\"00FF00\">49 Coins</font></td></tr>");
        sb.append("<tr><td>3rd Place: <font color=\"00FF00\">48 Coins</font></td></tr>");
        sb.append("<tr><td>4rd Place: <font color=\"00FF00\">47 Coins</font></td></tr>");
        sb.append("<tr><td>5rd Place: <font color=\"00FF00\">46 Coins</font></td></tr>");
        sb.append("<tr><td>6th-100th: Decreasing rewards</td></tr>");
        sb.append("</table><br>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td><font color=\"LEVEL\">Weekly Rewards (Top 20)</font></td></tr>");
        sb.append("<tr><td>1st Place: <font color=\"00FF00\">200 Coins</font></td></tr>");
        sb.append("<tr><td>2st Place: <font color=\"00FF00\">195 Coins</font></td></tr>");
        sb.append("<tr><td>3st Place: <font color=\"00FF00\">190 Coins</font></td></tr>");
        sb.append("<tr><td>4st Place: <font color=\"00FF00\">185 Coins</font></td></tr>");
        sb.append("<tr><td>5st Place: <font color=\"00FF00\">180 Coins</font></td></tr>");
        sb.append("<tr><td>6th-100th: Decreasing rewards</td></tr>");
        sb.append("</table>");
        
        // Important Notes
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">Important Information</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td>• Daily reset: <font color=\"00FF00\">06:00 AM</font> server time</td></tr>");
        sb.append("<tr><td>• Weekly reset: <font color=\"00FF00\">Monday 06:00 AM</font> server time</td></tr>");
        sb.append("<tr><td>• Rewards are automatically distributed</td></tr>");
        sb.append("<tr><td>• Offline players receive rewards in inventory</td></tr>");
        sb.append("</table>");
        
        // Tips
        sb.append("<table width=270 bgcolor=\"333333\">");
        sb.append("<tr><td><font color=\"LEVEL\">Important Tips</font></td></tr>");
        sb.append("</table>");
        
        sb.append("<table width=270>");
        sb.append("<tr><td>• Focus on monsters matching your level</td></tr>");
        sb.append("<tr><td>• Check rankings daily for your position</td></tr>");
        sb.append("<tr><td>• Higher stages mean better rewards</td></tr>");
        sb.append("<tr><td>• Progress is saved permanently</td></tr>");
        sb.append("</table>");
        
        // Back button
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    private String showInProgress(Player player, int page, String search)
    {
        // Search null kontrolü
        if (search == null)
        {
            search = "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><center>");
        
        // Başlık
        sb.append("<table width=270 bgcolor=\"000000\">");
        sb.append("<tr><td align=center height=35>");
        sb.append("<font color=\"LEVEL\" name=\"hs12\">In Progress Monsters</font>");
        sb.append("</td></tr></table>");
        
        // Arama kutusu
        sb.append("<table width=270 height=35 bgcolor=\"333333\">");
        sb.append("<tr><td align=center>");
        sb.append("<table width=250><tr>");
        sb.append("<td width=180><edit var=\"search\" width=175 height=15 align=left></td>");
        sb.append("<td width=70><button value=\"Search\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchInProgress_ $search\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
        sb.append("</tr></table>");
        sb.append("</td></tr></table>");
        
        // Liste başlığı
        sb.append("<table width=270 bgcolor=\"222222\">");
        sb.append("<tr>");
        sb.append("<td width=130 align=left>&nbsp;<font color=\"LEVEL\">Monster</font></td>");
        sb.append("<td width=70 align=center><font color=\"LEVEL\">Progress</font></td>");
        sb.append("<td width=70 align=center><font color=\"LEVEL\">Kills</font></td>");
        sb.append("</tr></table>");
        
        List<MonsterInfo> filteredMonsters = getFilteredMonsters(player, search.trim(), "inProgress");
        
        // Liste boşsa mesaj göster
        if (filteredMonsters.isEmpty())
        {
            sb.append("<table width=270 height=40><tr><td align=center>");
            if (!search.trim().isEmpty())
            {
                sb.append("<font color=\"FF0000\">No in-progress monsters found matching your search.</font>");
            }
            else
            {
                sb.append("<font color=\"FF0000\">You don't have any monsters in progress.</font>");
            }
            sb.append("</td></tr></table>");
        }
        else
        {
            // Sayfalama
            int monstersPerPage = 10;
            int totalPages = (filteredMonsters.size() + monstersPerPage - 1) / monstersPerPage;
            page = Math.min(Math.max(1, page), totalPages);
            int startIndex = (page - 1) * monstersPerPage;
            int endIndex = Math.min(startIndex + monstersPerPage, filteredMonsters.size());
            
            // Monster listesi
            for (int i = startIndex; i < endIndex; i++)
            {
                MonsterInfo monster = filteredMonsters.get(i);
                int kills = getMonsterKills(player, monster.monsterId);
                int nextLevel = getCurrentLevel(kills, monster.requiredKills) + 1;
                int nextRequired = getNextRequired(kills, monster.requiredKills);
                
                String bgColor = (i % 2 == 0) ? "333333" : "292929";
                sb.append("<table width=270 bgcolor=\"").append(bgColor).append("\">");
                sb.append("<tr>");
                sb.append("<td width=130 align=left>&nbsp;").append(monster.monsterName).append("</td>");
                sb.append("<td width=70 align=center>Stage ").append(nextLevel).append("</td>");
                sb.append("<td width=70 align=center>").append(kills).append("/").append(nextRequired).append("</td>");
                sb.append("</tr></table>");
            }
            
            // Sayfalama butonları
            sb.append("<table width=270 height=40><tr>");
            if (page > 1)
            {
                if (!search.trim().isEmpty())
                {
                    sb.append("<td width=90 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchInProgress_").append(search).append("_").append(page - 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=90 align=right><button value=\"Previous\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showInProgress_").append(page - 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
            }
            else
            {
                sb.append("<td width=90></td>");
            }
            
            sb.append("<td width=90 align=center><font color=\"LEVEL\">Page ").append(page).append("/").append(totalPages).append("</font></td>");
            
            if (page < totalPages)
            {
                if (!search.trim().isEmpty())
                {
                    sb.append("<td width=90 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization searchInProgress_").append(search).append("_").append(page + 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
                else
                {
                    sb.append("<td width=90 align=left><button value=\"Next\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showInProgress_").append(page + 1).append("\" width=65 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
                }
            }
            else
            {
                sb.append("<td width=90></td>");
            }
            sb.append("</tr></table>");
        }
        
        // Geri dönüş butonu
        sb.append("<br><button value=\"Back\" action=\"bypass -h Quest Q00801_MonsterHunterSpecialization showMain\" width=70 height=22 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
        sb.append("</center></body></html>");
        return sb.toString();
    }
    
    // Yardımcı metod: Bir sonraki seviye için gereken kill sayısını hesaplar
    private int getNextRequired(int currentKills, int[] requiredKills)
    {
        for (int required : requiredKills)
        {
            if (currentKills < required)
            {
                return required;
            }
        }
        return requiredKills[requiredKills.length - 1];
    }
    
    private int getCurrentLevel(int kills, int[] requiredKills)
    {
        for (int level = requiredKills.length - 1; level >= 0; level--)
        {
            if (kills >= requiredKills[level])
            {
                return level + 1;
            }
        }
        return 0;
    }
    
    @Override
    public String onKill(Npc npc, Player killer, boolean isSummon) {
        if (killer == null) {
            return super.onKill(npc, killer, isSummon);
        }
    
        final QuestState qs = killer.getQuestState(getName());
        if ((qs != null) && qs.isStarted()) {
            int monsterId = npc.getId();
            MonsterInfo monster = MONSTER_INFO.get(monsterId);
            
            if (monster != null) {
                // Score hesaplama
                int baseScore = monster.getBaseScore();
                int currentKills = getMonsterKills(killer, monsterId);
                int stageBonus = monster.getStageBonus(currentKills);
                int totalScore = baseScore + stageBonus;
                
                LOGGER.info("Monster killed - ID: " + monsterId + 
                    ", Base Score: " + baseScore + 
                    ", Stage Bonus: " + stageBonus + 
                    ", Total Score: " + totalScore);
    
                // Score güncelleme
                updatePlayerScores(killer, monsterId, totalScore);
                
                // Kill sayısını güncelle
                processKill(npc, killer);
            } else {
                LOGGER.warning("No monster info found for ID: " + monsterId);
            }
        }
        return super.onKill(npc, killer, isSummon);
    }
    
    private void processKill(Npc npc, Player player)
    {
        int monsterId = npc.getId();
        
        try (Connection con = DatabaseFactory.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM monsters_hunter WHERE monster_id = ?"))
        {
            ps.setInt(1, monsterId);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    String monsterName = rs.getString("monster_name");
                    int currentKills = getMonsterKills(player, monsterId);
                    int monsterLevel = rs.getInt("monster_lvl");
                    
                    // Level farkı kontrolü
                    int levelDiff = Math.abs(player.getLevel() - monsterLevel);
                    if (levelDiff > 12)
                    {
                        if (currentKills % 5 == 0)
                        {
                            player.sendMessage("No score gained - Level difference is too high! (Your level: " + player.getLevel() + ", Monster level: " + monsterLevel + ")");
                        }
                        return;
                    }
                    
                    // Score hesaplama ve güncelleme
                    MonsterInfo monster = MONSTER_INFO.get(monsterId);
                    if (monster != null)
                    {
                        int baseScore = monster.getBaseScore();
                        int stageBonus = monster.getStageBonus(currentKills);
                        int totalScore = baseScore + stageBonus;
                        
                        updatePlayerScores(player, monsterId, totalScore);
                    }
                    
                    if (currentKills == 0)
                    {
                        player.sendMessage("You have started hunting " + monsterName + "!");
                    }
                    
                    currentKills++;
                    updateMonsterKills(player, monsterId, currentKills);
                    
                    // Bonus hesaplama
                    double expBonus = 0;
                    double dropBonus = 0;
                    double dropAmountBonus = 0;
                    double spoilBonus = 0;
                    double spoilAmountBonus = 0;
                    
                    // Her stage için bonus kontrolü
                    for (int i = 1; i <= 10; i++)
                    {
                        // Önce reward claimed durumunu kontrol et
                        if (isRewardClaimed(player, monsterId, i))
                        {
                            // Her bonus türü için tablodaki ilgili kolondan değeri al
                            expBonus = Math.max(expBonus, rs.getDouble("exp_bonus" + i));
                            dropBonus = Math.max(dropBonus, rs.getDouble("drop_bonus" + i));
                            dropAmountBonus = Math.max(dropAmountBonus, rs.getDouble("drop_amount_bonus" + i));
                            spoilBonus = Math.max(spoilBonus, rs.getDouble("spoil_bonus" + i));
                            spoilAmountBonus = Math.max(spoilAmountBonus, rs.getDouble("spoil_amount_bonus" + i));
                            
                        }
                    }
                    
                    // Bonusları uygula
                    if (expBonus > 0 || dropBonus > 0 || dropAmountBonus > 0 || spoilBonus > 0 || spoilAmountBonus > 0)
                    {
                        // Instance script mantığını kullan
                        npc.getTemplate().setInstanceRates(1.0 + dropBonus, // dropChance
                            1.0 + dropAmountBonus, // dropAmount
                            1.0 + spoilBonus, // spoilChance
                            1.0 + spoilAmountBonus, // spoilAmount
                            null // özel itemler için null
                        );
                        
                        // Exp bonusu için
                        npc.getTemplate().setMonsterHunterBonuses(dropBonus, expBonus, dropAmountBonus, spoilBonus, spoilAmountBonus);
                        
                    }
                    
                    // Her 5 kill'de bir progress mesajı
                    if (currentKills % 5 == 0)
                    {
                        StringBuilder progressMsg = new StringBuilder();
                        progressMsg.append("----Monster Hunter Record----\n");
                        progressMsg.append("Monster: ").append(monsterName).append("\n");
                        progressMsg.append("Level: ").append(monsterLevel).append("\n");
                        progressMsg.append("Total Kills: ").append(currentKills).append("\n");
                        progressMsg.append("---------------------------\n");
                        progressMsg.append("Active Bonuses:\n");
                        
                        boolean hasAnyBonus = false;
                        if (expBonus > 0)
                        {
                            progressMsg.append("• EXP: +").append(String.format("%.1f", expBonus * 100)).append("%\n");
                            hasAnyBonus = true;
                        }
                        if (dropBonus > 0)
                        {
                            progressMsg.append("• Drop Rate: +").append(String.format("%.1f", dropBonus * 100)).append("%\n");
                            hasAnyBonus = true;
                        }
                        if (dropAmountBonus > 0)
                        {
                            progressMsg.append("• Drop Amount: +").append(String.format("%.1f", dropAmountBonus * 100)).append("%\n");
                            hasAnyBonus = true;
                        }
                        if (spoilBonus > 0)
                        {
                            progressMsg.append("• Spoil Rate: +").append(String.format("%.1f", spoilBonus * 100)).append("%\n");
                            hasAnyBonus = true;
                        }
                        if (spoilAmountBonus > 0)
                        {
                            progressMsg.append("• Spoil Amount: +").append(String.format("%.1f", spoilAmountBonus * 100)).append("%\n");
                            hasAnyBonus = true;
                        }
                        
                        if (!hasAnyBonus)
                        {
                            progressMsg.append("• There is no Active bonuses right now\n");
                        }
                        
                        progressMsg.append("---------------------------");
                        player.sendMessage(progressMsg.toString());
                    }
                    
                    // Ödül kontrolleri
                    for (int i = 1; i <= 10; i++)
                    {
                        checkAndGiveReward(player, monsterId, currentKills, rs, i);
                    }
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error in processKill: " + e.getMessage());
        }
    }
    
    private boolean canGainScore(Player player)
    {
        if (Config.DUALBOX_CHECK_MAX_PLAYERS_PER_IP == 0)
        {
            return true;
        }
        
        final String addr = player.getClient().getIp();
        if (addr == null)
        {
            return true;
        }
        
        int dual = 0;
        final int addrHash = addr.hashCode();
        
        // Whitelist kontrolü
        Integer limit = Config.DUALBOX_CHECK_WHITELIST.get(addrHash);
        if (limit != null && limit < 0)
        {
            return true;
        }
        
        // Online oyuncular arasında aynı IP'ye sahip aktif score kazanan oyuncuları say
        for (Player onlinePlayer : World.getInstance().getPlayers())
        {
            if (onlinePlayer == null || !onlinePlayer.isOnline() || onlinePlayer == player)
            {
                continue;
            }
            
            if (onlinePlayer.getClient() == null)
            {
                continue;
            }
            
            if (onlinePlayer.getClient().getIp().hashCode() == addrHash && hasRecentScore(onlinePlayer))
            {
                dual++;
                if (limit != null)
                {
                    if (dual >= limit)
                    {
                        return false;
                    }
                }
                else if (dual >= Config.DUALBOX_CHECK_MAX_PLAYERS_PER_IP)
                {
                    return false;
                }
            }
        }
        return true;
    }
    
    // Son X dakika içinde score kazanılmış mı kontrolü
    private boolean hasRecentScore(Player player)
    {
        // Son score kazanma zamanını kontrol et (örnek: son 5 dakika)
        long lastScoreTime = player.getVariables().getLong("LastMonsterHunterScore", 0);
        return (System.currentTimeMillis() - lastScoreTime) < 300000; // 5 dakika
    }
    
    // Score kazanıldığında çağrılacak method
    private void updateLastScoreTime(Player player)
    {
        player.getVariables().set("LastMonsterHunterScore", System.currentTimeMillis());
    }
    
    public void addScore(Player player, int score)
    {
        if (!canGainScore(player))
        {
            player.sendMessage("Only one character per IP can gain Monster Hunter score.");
            return;
        }
        
        // Score ekleme işlemleri...
        updateLastScoreTime(player);
        
        // Son score zamanı bilgisi
        player.sendMessage("Last score time: " + new SimpleDateFormat("HH:mm:ss").format(new Date(player.getVariables().getLong("LastMonsterHunterScore", 0))));
    }
    
    private void checkAndGiveReward(Player player, int monsterId, int currentKills, ResultSet rs, int rewardLevel) throws SQLException
    {
        int requiredKills = rs.getInt("monster_need_to_kill_for_reward" + rewardLevel);
        String rewardIds = rs.getString("monster_reward" + rewardLevel);
        String rewardQuantities = rs.getString("monster_reward_quantity" + rewardLevel);
        
        if (rewardIds != null && !rewardIds.isEmpty() && requiredKills > 0)
        {
            if (currentKills >= requiredKills && !isRewardClaimed(player, monsterId, rewardLevel))
            {
                String[] rewards = rewardIds.split(",");
                String[] quantities = rewardQuantities.split(",");
                
                int minLength = Math.min(rewards.length, quantities.length);
                for (int i = 0; i < minLength; i++)
                {
                    try
                    {
                        int itemId = Integer.parseInt(rewards[i].trim());
                        int quantity = Integer.parseInt(quantities[i].trim());
                        giveItems(player, itemId, quantity);
                    }
                    catch (NumberFormatException e)
                    {
                        LOGGER.warning("Invalid reward/quantity format: " + rewards[i] + "/" + quantities[i]);
                    }
                }
                setRewardClaimed(player, monsterId, rewardLevel);
                player.sendMessage("You have received rewards for killing " + requiredKills + " monsters!!!");
            }
        }
    }
}