package quests.Q00802_ticaretyapanPcTrade;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Set;
import java.util.HashSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.enums.PrivateStoreType;
import org.l2jmobius.gameserver.instancemanager.QuestManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.TradeList;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.Armor;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.quest.Quest;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgSell;
import org.l2jmobius.gameserver.enums.Race;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.instancemanager.IdManager;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.enums.ChatType;

// Add these imports near the top of your file with the other imports


/**
 * Player-based Trading System
 */
public class Q00802_ticaretyapanPcTrade extends Quest
{
    private static final Logger LOGGER = Logger.getLogger(Q00802_ticaretyapanPcTrade.class.getName());
    
    // NPCs
    private static final int TRADE_MANAGER = 37003;
    
    // Location boundaries for selling traders
    private static final int SELL_MIN_X = 81138;
    private static final int SELL_MAX_X = 82719;
    private static final int SELL_MIN_Y = 147904;
    private static final int SELL_MAX_Y = 149322;
    private static final int Z_COORD = -3473;
    
    // Location boundaries for buying traders
    private static final int BUY_MIN_X = 81151;
    private static final int BUY_MAX_X = 82707;
    private static final int BUY_MIN_Y = 147682;
    private static final int BUY_MAX_Y = 147857;
    
    // Exclusion zone (statue area)
    private static final int EXCLUSION_MIN_X = 81688;
    private static final int EXCLUSION_MAX_X = 82181;
    private static final int EXCLUSION_MIN_Y = 148347;
    private static final int EXCLUSION_MAX_Y = 148879;
    
    // System variables
    private static final Map<Integer, PlayerTrader> ACTIVE_TRADERS = new ConcurrentHashMap<>();
    // Update this to point directly to the game item directory
    private static final String GAME_ITEMS_PATH = "data/stats/items/";
    private static final String RECIPES_FILE_PATH = "data/Recipes.xml";
    private static final List<ItemData> ITEM_DATABASE = new ArrayList<>();
    private static final List<ItemData> ITEM_BUY_DATABASE = new ArrayList<>();
    private static final List<RecipeData> RECIPE_DATABASE = new ArrayList<>();
    private static ScheduledFuture<?> _traderUpdateTask = null;
    
    // Random names for traders
    private static final String[] MALE_NAMES =
    {
        "Arthur"
    };
    private static final String[] FEMALE_NAMES =
    {
        "Guinevere"
    };
    private static final String[] LAST_NAMES =
    {
        "of Avalon"
    };
    
    // Add this as a class field
    private static final String FAKE_PLAYERS_PATH = "data/tools/fake_players.list";
    private static final List<String> FAKE_PLAYER_NAMES = new ArrayList<>();
    
    // Config constants - enhanced version
    private static final class Config
    {
        // Sell mode configs
        static boolean ALLOW_SELL_NON_TRADABLE = true; // Allow selling normally non-tradable items
        static final int MIN_SELL_ITEM_TYPES = 5; // Minimum number of different items a seller can offer
        static final int MAX_SELL_ITEM_TYPES = 40; // Maximum number of different items a seller can offer
        static final int MIN_SELL_ITEM_COUNT = 1; // Minimum quantity of a specific item
        static final int MAX_SELL_ITEM_COUNT = 100; // Maximum quantity of a specific item
        
        // Buy mode configs
        static final boolean ENABLE_BUY_MODE = true; // Enable traders in buy mode
        static final int MIN_BUY_ITEM_TYPES = 5; // Minimum number of different items a buyer can request
        static final int MAX_BUY_ITEM_TYPES = 40; // Maximum number of different items a buyer can request
        static final int MIN_BUY_ITEM_COUNT = 1; // Minimum quantity of a specific item
        static final int MAX_BUY_ITEM_COUNT = 100; // Maximum quantity of a specific item
        
        // Store title configs
        static final int MAX_STORE_TITLE_LENGTH = 29; // Maximum store title length
        
        // Trade announcements
        static final boolean ENABLE_TRADE_ANNOUNCEMENTS = true; // Enable trade channel announcements
        static final int TRADE_ANNOUNCEMENT_INTERVAL = 60000; // Announcement interval in ms (1 minute)
        static final int MAX_TRADE_ANNOUNCEMENT_LENGTH = 105; // Maximum length for trade announcements
        
        // System configs
        static int TRADER_REFRESH_INTERVAL = 300000; // 5 minutes by default, but now changeable
        static boolean TRADER_AUTO_REFRESH = true; // Whether to automatically refresh traders
        
        // Initial trader counts (separate for buy and sell modes)
        static final int INITIAL_TRADER_COUNT_SELL = 500; // Number of sell traders to spawn initially
        static final int INITIAL_TRADER_COUNT_BUY = 200; // Number of buy traders to spawn initially
        
        // Specialized traders configs - Trader type distribution (percentage)
        static final int WEAPON_TRADER_PERCENTAGE = 25; // Weapon-specialized traders
        static final int ARMOR_TRADER_PERCENTAGE = 25; // Armor-specialized traders
        static final int ETCITEM_TRADER_PERCENTAGE = 25; // Etc item-specialized traders
        static final int CRAFT_TRADER_PERCENTAGE = 25; // Craft item-specialized traders
        
        // For selling/buying crafting items - separate quantities for buy and sell modes
        static final int CRAFT_ITEMS_MIN_QUANTITY_FOR_SELL = 1000;
        static final int CRAFT_ITEMS_MAX_QUANTITY_FOR_SELL = 9999;
        static final int CRAFT_ITEMS_MIN_QUANTITY_FOR_BUY = 500;
        static final int CRAFT_ITEMS_MAX_QUANTITY_FOR_BUY = 5000;
        
        // Original map (kept for backward compatibility)
        static final Map<Integer, int[]> CRAFT_ITEMS = new HashMap<>(); // Map of itemId -> [minPrice, maxPrice]
        static {
            // Add default craft items - format: itemId, minPrice, maxPrice
            CRAFT_ITEMS.put(1872, new int[] {999999, 150000}); // Craft Items
            CRAFT_ITEMS.put(1867, new int[] {999999, 150000}); // Craft Items
            // Add more craft items here
        }
        
        // Separate maps for buy and sell modes
        static final Map<Integer, int[]> CRAFT_ITEMS_BUY = new HashMap<>(); // Map of itemId -> [minPrice, maxPrice]
        static {
            // Add default craft items - format: itemId, minPrice, maxPrice
            CRAFT_ITEMS_BUY.put(1872, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1867, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1878, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1870, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1869, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1785, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(3031, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1880, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1883, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1864, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1866, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1868, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1865, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1871, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1881, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1879, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1884, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1458, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(2130, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1885, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1882, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1886, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1873, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1877, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1891, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1892, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1889, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1894, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1459, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(2131, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5220, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1895, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1890, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1876, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1893, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1874, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1875, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1888, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1887, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4043, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4047, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1460, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4042, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(2132, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4046, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4045, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4048, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4039, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4041, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4040, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(4044, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5553, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1461, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5550, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(2133, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5549, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5551, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5554, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(5552, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(1462, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(2134, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(9629, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(9628, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(9630, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_BUY.put(9631, new int[] {150, 150000}); // Craft Items            
            // Add more craft items here
        }
        
        static final Map<Integer, int[]> CRAFT_ITEMS_SELL = new HashMap<>(); // Map of itemId -> [minPrice, maxPrice]
        static {
            // Add default craft items - format: itemId, minPrice, maxPrice
            CRAFT_ITEMS_SELL.put(1872, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1867, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1878, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1870, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1869, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1785, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(3031, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1880, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1883, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1864, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1866, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1868, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1865, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1871, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1881, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1879, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1884, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1458, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(2130, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1885, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1882, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1886, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1873, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1877, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1891, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1892, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1889, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1894, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1459, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(2131, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5220, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1895, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1890, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1876, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1893, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1874, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1875, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1888, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1887, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4043, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4047, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1460, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4042, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(2132, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4046, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4045, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4048, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4039, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4041, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4040, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(4044, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5553, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1461, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5550, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(2133, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5549, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5551, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5554, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(5552, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(1462, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(2134, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(9629, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(9628, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(9630, new int[] {150, 150000}); // Craft Items
            CRAFT_ITEMS_SELL.put(9631, new int[] {150, 150000}); // Craft Items            
            // Add more craft items here
        }
        
        // Exception items with custom prices
        static final Map<Integer, int[]> EXCEPTION_ITEMS_BUY_PRICE = new HashMap<>();
        static final Map<Integer, int[]> EXCEPTION_ITEMS_SELL_PRICE = new HashMap<>();
        
        // Price multipliers by grade (percentage)
        // Selling price multipliers (item grade specific)
        static final int SELL_PRICE_MULTIPLIER_NO_GRADE = 100; // % of reference price for no-grade items
        static final int SELL_PRICE_MULTIPLIER_D_GRADE = 120; // % of reference price for D-grade items
        static final int SELL_PRICE_MULTIPLIER_C_GRADE = 150; // % of reference price for C-grade items
        static final int SELL_PRICE_MULTIPLIER_B_GRADE = 200; // % of reference price for B-grade items
        static final int SELL_PRICE_MULTIPLIER_A_GRADE = 250; // % of reference price for A-grade items
        static final int SELL_PRICE_MULTIPLIER_S_GRADE = 350; // % of reference price for S-grade items
        static final int SELL_PRICE_MULTIPLIER_S80_GRADE = 400; // % of reference price for S80-grade items
        static final int SELL_PRICE_MULTIPLIER_S84_GRADE = 450; // % of reference price for S84-grade items
        
        // Buying price multipliers (item grade specific)
        static final int BUY_PRICE_MULTIPLIER_NO_GRADE = 80; // % of reference price for no-grade items
        static final int BUY_PRICE_MULTIPLIER_D_GRADE = 70; // % of reference price for D-grade items
        static final int BUY_PRICE_MULTIPLIER_C_GRADE = 60; // % of reference price for C-grade items
        static final int BUY_PRICE_MULTIPLIER_B_GRADE = 50; // % of reference price for B-grade items
        static final int BUY_PRICE_MULTIPLIER_A_GRADE = 45; // % of reference price for A-grade items
        static final int BUY_PRICE_MULTIPLIER_S_GRADE = 40; // % of reference price for S-grade items
        static final int BUY_PRICE_MULTIPLIER_S80_GRADE = 35; // % of reference price for S80-grade items
        static final int BUY_PRICE_MULTIPLIER_S84_GRADE = 30; // % of reference price for S84-grade items
        
        // Legacy multipliers (kept for backward compatibility)
        static final int SELL_PRICE_MULTIPLIER = 100; // 100% = normal price
        static final int BUY_PRICE_MULTIPLIER = 100; // 100% = normal price
        
        // Item type distribution
        static final Map<String, Integer> ITEM_TYPE_DISTRIBUTION = new HashMap<>();
        static {
            ITEM_TYPE_DISTRIBUTION.put("Weapon", 30);
            ITEM_TYPE_DISTRIBUTION.put("Armor", 30);
            ITEM_TYPE_DISTRIBUTION.put("EtcItem", 40);
        }
        
        // Weapon type distribution
        static final Map<String, Integer> WEAPON_TYPE_DISTRIBUTION = new HashMap<>();
        static {
            WEAPON_TYPE_DISTRIBUTION.put("BLUNT", 10);
            WEAPON_TYPE_DISTRIBUTION.put("BOW", 10);
            WEAPON_TYPE_DISTRIBUTION.put("BIGSWORD", 10);
            WEAPON_TYPE_DISTRIBUTION.put("BIGBLUNT", 10);
            WEAPON_TYPE_DISTRIBUTION.put("DUAL", 10);
            WEAPON_TYPE_DISTRIBUTION.put("DUALFIST", 5);
            WEAPON_TYPE_DISTRIBUTION.put("POLE", 10);
            WEAPON_TYPE_DISTRIBUTION.put("SWORD", 15);
            WEAPON_TYPE_DISTRIBUTION.put("DAGGER", 10);
            WEAPON_TYPE_DISTRIBUTION.put("RAPIER", 5);
            WEAPON_TYPE_DISTRIBUTION.put("ANCIENT_SWORD", 5);
            WEAPON_TYPE_DISTRIBUTION.put("CROSSBOW", 5);
            WEAPON_TYPE_DISTRIBUTION.put("DUALDAGGER", 5);
        }
        
        // Armor type distribution
        static final Map<String, Integer> ARMOR_TYPE_DISTRIBUTION = new HashMap<>();
        static {
            ARMOR_TYPE_DISTRIBUTION.put("LIGHT", 30);
            ARMOR_TYPE_DISTRIBUTION.put("HEAVY", 30);
            ARMOR_TYPE_DISTRIBUTION.put("MAGIC", 30);
            ARMOR_TYPE_DISTRIBUTION.put("SIGIL", 10);
        }
        
        // Body part distribution
        static final Map<String, Integer> BODYPART_DISTRIBUTION = new HashMap<>();
        static {
            BODYPART_DISTRIBUTION.put("rhand", 15);
            BODYPART_DISTRIBUTION.put("lhand", 5);
            BODYPART_DISTRIBUTION.put("lrhand", 10);
            BODYPART_DISTRIBUTION.put("chest", 10);
            BODYPART_DISTRIBUTION.put("legs", 10);
            BODYPART_DISTRIBUTION.put("fullarmor", 10);
            BODYPART_DISTRIBUTION.put("head", 10);
            BODYPART_DISTRIBUTION.put("gloves", 5);
            BODYPART_DISTRIBUTION.put("feet", 5);
            BODYPART_DISTRIBUTION.put("back", 5);
            BODYPART_DISTRIBUTION.put("neck", 5);
            BODYPART_DISTRIBUTION.put("rfinger", 5);
            BODYPART_DISTRIBUTION.put("lfinger", 5);
        }
        
        // Crystal type distribution
        static final Map<String, Integer> CRYSTAL_TYPE_DISTRIBUTION = new HashMap<>();
        static {
            CRYSTAL_TYPE_DISTRIBUTION.put("NONE", 20); // No grade
            CRYSTAL_TYPE_DISTRIBUTION.put("D", 20);
            CRYSTAL_TYPE_DISTRIBUTION.put("C", 20);
            CRYSTAL_TYPE_DISTRIBUTION.put("B", 15);
            CRYSTAL_TYPE_DISTRIBUTION.put("A", 15);
            CRYSTAL_TYPE_DISTRIBUTION.put("S", 5);
            CRYSTAL_TYPE_DISTRIBUTION.put("S80", 3);
            CRYSTAL_TYPE_DISTRIBUTION.put("S84", 2);
        }
        
        // Magic weapon percentage
        static final int MAGIC_WEAPON_PERCENTAGE = 30; // 30% of weapons should be magic weapons
        
        // Item flags distribution
        static final boolean SELL_TRADABLE_ONLY = true;
        static final boolean SELL_DROPABLE_ONLY = true;
        static final boolean SELL_SELLABLE_ONLY = true;
        static final boolean SELL_DEPOSITABLE_ONLY = true;
        static final boolean SELL_QUEST_ITEMS = false;
        
        // Specialized crafter configs - Trader type distribution (percentage)
        static final int INITIAL_CRAFTER_TRADER_COUNT = 200; // Number of Craft CRAFTER to spawn initially
        
        static final int CRAFTER_MIN_CRAFT_NON_STACKABLE_ITEM_TYPES = 1; // Minimum number of different items a seller can offer
        static final int CRAFTER_MAX_CRAFT_NON_STACKABLE_ITEM_TYPES = 1; // Maximum number of different items a seller can offer
        static final int CRAFTER_MIN_CRAFT_NON_STACKABLE_ITEM_COUNT = 1; // Minimum quantity of a specific item
        static final int CRAFTER_MAX_CRAFT_NON_STACKABLE_ITEM_COUNT = 20; // Maximum quantity of a specific item
        
        static final int CRAFTER_MIN_CRAFT_STACKABLE_ITEM_COUNT = 1000; // Minimum quantity of a specific item
        static final int CRAFTER_MAX_CRAFT_STACKABLE_ITEM_COUNT = 100000; // Maximum quantity of a specific item
        
        static final int WEAPON_CRAFTER_PERCENTAGE = 25; // Weapon-specialized CRAFTER
        static final int ARMOR_CRAFTER_PERCENTAGE = 25; // Armor-specialized CRAFTER
        static final int ETCITEM_CRAFTER_PERCENTAGE = 25; // Etc item-specialized CRAFTER
        static final int SOUL_AND_SPIRIT_SHOT_CRAFTER_PERCENTAGE = 25; // Craft item-specialized CRAFTER
        
        static final int CRAFTER_PRICE_MULTIPLIER_SOUL_AND_SPIRIT_SHOT = 100; // % of reference price for soul and spirit items
        static final int CRAFTER_PRICE_MULTIPLIER_MATS = 80; // % of reference price for no-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_NO_GRADE = 80; // % of reference price for no-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_D_GRADE = 70; // % of reference price for D-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_C_GRADE = 60; // % of reference price for C-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_B_GRADE = 50; // % of reference price for B-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_A_GRADE = 45; // % of reference price for A-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_S_GRADE = 40; // % of reference price for S-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_S80_GRADE = 35; // % of reference price for S80-grade items
        static final int CRAFTER_PRICE_MULTIPLIER_S84_GRADE = 30; // % of reference price for S84-grade items
        
        // Exception recipes with custom prices
        static final Map<Integer, Integer> EXCEPTION_RECIPE_PRICE = new HashMap<>();
        static {
            // Add recipe ID and custom price multiplier (percentage)
            // Soul shots and spirit shots can be added here with custom price multipliers
        }
        
        // Soul shot and spirit shot recipe IDs
        static final Set<Integer> SOUL_SPIRIT_SHOT_RECIPES = new HashSet<>();
        static {
            // D-grade
            SOUL_SPIRIT_SHOT_RECIPES.add(1804); // Soulshot D
            SOUL_SPIRIT_SHOT_RECIPES.add(3953); // Blessed Spiritshot D
            // C-grade
            SOUL_SPIRIT_SHOT_RECIPES.add(1805); // Soulshot C
            SOUL_SPIRIT_SHOT_RECIPES.add(3954); // Blessed Spiritshot C
            // B-grade
            SOUL_SPIRIT_SHOT_RECIPES.add(1806); // Soulshot B
            SOUL_SPIRIT_SHOT_RECIPES.add(3955); // Blessed Spiritshot B
            // A-grade
            SOUL_SPIRIT_SHOT_RECIPES.add(1807); // Soulshot A
            SOUL_SPIRIT_SHOT_RECIPES.add(3956); // Blessed Spiritshot A
            // S-grade
            SOUL_SPIRIT_SHOT_RECIPES.add(1808); // Soulshot S
            SOUL_SPIRIT_SHOT_RECIPES.add(3957); // Blessed Spiritshot S
        }
    }

    // Scheduled task for trade announcements
    private static ScheduledFuture<?> _tradeAnnouncementTask = null;
    

    /**
     * Class to store item data from the itemlist.ini file
     */
    private static class ItemData
    {
        private final int id;
        private final int price;
        private final String name;
        private int minQuantity = 1;
        private int maxQuantity = 100;
        
        public ItemData(int id, int price, String name)
        {
            this.id = id;
            this.price = price;
            this.name = name;
        }
        
        public int getId()
        {
            return id;
        }
        
        public int getPrice()
        {
            return price;
        }
        
        public String getName()
        {
            return name;
        }
        
    
        public void setMinQuantity(int minQuantity)
        {
            this.minQuantity = minQuantity;
        }
        
        
        public void setMaxQuantity(int maxQuantity)
        {
            this.maxQuantity = maxQuantity;
        }
        
        public int getRandomQuantity()
        {
            return Rnd.get(minQuantity, maxQuantity);
        }
    }
    
    /**
     * Class to store recipe data from the Recipes.xml file
     */
    private static class RecipeData
    {
        private final int recipeId;
        private final int itemId;
        private final String name;
        private final int craftLevel;
        private final String type;
        private final int successRate;
        private final List<int[]> ingredients = new ArrayList<>(); // [itemId, count]
        private int price; // Calculated price for crafting
        private final int itemType; // 0 = weapon, 1 = armor, 2 = etc
        
        public RecipeData(int recipeId, int itemId, String name, int craftLevel, String type, int successRate)
        {
            this.recipeId = recipeId;
            this.itemId = itemId;
            this.name = name;
            this.craftLevel = craftLevel;
            this.type = type;
            this.successRate = successRate;
            this.itemType = determineItemType(itemId);
        }
        
        private int determineItemType(int itemId) {
            ItemTemplate template = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
            if (template == null) {
                return 2; // Default to EtcItem if template not found
            }
            
            if (template instanceof Weapon) {
                return 0; // Weapon
            } else if (template instanceof Armor) {
                return 1; // Armor
            } else {
                return 2; // EtcItem
            }
        }
        
        public void addIngredient(int itemId, int count)
        {
            ingredients.add(new int[] {itemId, count});
        }
        
        public int getRecipeId()
        {
            return recipeId;
        }
        
        public int getItemId()
        {
            return itemId;
        }
        
        public String getName()
        {
            return name;
        }
        
        public int getCraftLevel()
        {
            return craftLevel;
        }
        
        public String getType()
        {
            return type;
        }
        
        public int getSuccessRate()
        {
            return successRate;
        }
        
        public List<int[]> getIngredients()
        {
            return ingredients;
        }
        
        public int getPrice()
        {
            return price;
        }
        
        public void setPrice(int price)
        {
            this.price = price;
        }
        
        public int getItemType()
        {
            return itemType;
        }
        
        public boolean isSoulOrSpiritShot()
        {
            return Config.SOUL_SPIRIT_SHOT_RECIPES.contains(recipeId);
        }
    }
    
    /**
     * Class to represent our player-like traders
     */
    private static class PlayerTrader
    {
        private final int objectId;
        private final String name;
        private final TradeList tradeList;
        private final int classId;
        private final Race race;
        private final boolean isFemale;
        private final Location location;
        private final String title;
        private final boolean buyMode; // Is this trader in buy mode?
        private final boolean craftMode; // Is this trader a crafter?
        private Player fakePc;
        private List<ItemData> selectedItems; // Store selected items
        private List<RecipeData> selectedRecipes; // Store selected recipes
        private long lastAnnouncementTime = 0;
        
        public PlayerTrader(int objectId, String name, TradeList tradeList, int classId, Race race, boolean isFemale, Location location, String title, boolean buyMode, boolean craftMode)
        {
            this.objectId = objectId;
            this.name = name;
            this.tradeList = tradeList;
            this.classId = classId;
            this.race = race;
            this.isFemale = isFemale;
            this.location = location;
            this.title = title;
            this.buyMode = buyMode;
            this.craftMode = craftMode;
            this.fakePc = null;
            this.selectedItems = new ArrayList<>();
            this.selectedRecipes = new ArrayList<>();
        }
        
        // Add this constructor and update existing constructor calls
        public PlayerTrader(int objectId, String name, TradeList tradeList, int classId, Race race, boolean isFemale, Location location, String title, boolean buyMode)
        {
            this(objectId, name, tradeList, classId, race, isFemale, location, title, buyMode, false);
        }
        
        public int getObjectId()
        {
            return objectId;
        }
        
        public String getName()
        {
            return name;
        }
        
        public int getClassId()
        {
            return classId;
        }
        
        public Race getRace()
        {
            return race;
        }
        
        public boolean isFemale()
        {
            return isFemale;
        }
        
        public Location getLocation()
        {
            return location;
        }
        
        public String getTitle()
        {
            return title;
        }
        
        public boolean isBuyMode()
        {
            return buyMode;
        }
        
        public boolean isCraftMode()
        {
            return craftMode;
        }
        
        public Player getFakePc()
        {
            return fakePc;
        }
        
        public void setFakePc(Player fakePc)
        {
            this.fakePc = fakePc;
        }
        
        public List<ItemData> getSelectedItems()
        {
            return selectedItems;
        }
        
        public void setSelectedItems(List<ItemData> selectedItems)
        {
            this.selectedItems = selectedItems;
        }
        
        public List<RecipeData> getSelectedRecipes()
        {
            return selectedRecipes;
        }
        
        public void setSelectedRecipes(List<RecipeData> selectedRecipes)
        {
            this.selectedRecipes = selectedRecipes;
        }
        
        public long getLastAnnouncementTime()
        {
            return lastAnnouncementTime;
        }
        
        public void setLastAnnouncementTime(long time)
        {
            this.lastAnnouncementTime = time;
        }
        
        @SuppressWarnings("unused")
        public TradeList getTradeList()
        {
            return tradeList;
        }
    }
    
    public Q00802_ticaretyapanPcTrade()
    {
        super(802);
        addStartNpc(TRADE_MANAGER);
        addTalkId(TRADE_MANAGER);
        addFirstTalkId(TRADE_MANAGER);
        
        // Load XML-based configuration
        loadTraderConfig();
        
        // Load items directly from game files
        loadItemsFromGameXML();
        
        // Load recipe data from Recipes.xml
        loadRecipesFromXML();
        
        // Load fake player names
        loadFakePlayerNames();
        
        // Reference methods to avoid warnings
        if (LOGGER.isLoggable(Level.FINEST))
        {
            LOGGER.finest("Initialized helper methods: " + (findItemById(1) == null ? "findItemById" : "") + ", " + (ACTIVE_TRADERS.isEmpty() ? "" : ACTIVE_TRADERS.values().iterator().next().getTradeList()));
        }
    }
    
    /**
     * Load all items directly from the game's XML files
     */
    private void loadItemsFromGameXML()
    {
        LOGGER.info("Loading items directly from game XML files...");
        
        // Create a list of items from the game XMLs
        List<GameItemData> gameItems = loadGameItems();
        
        // Filter items and add to the appropriate databases
        int sellItemsAdded = 0;
        int buyItemsAdded = 0;
        
        for (GameItemData item : gameItems)
        {
            // Skip quest items, untradable items, or unsellable items unless configured otherwise
            if ((item.isQuestItem && !Config.SELL_QUEST_ITEMS) || 
                (!item.isTradable && Config.SELL_TRADABLE_ONLY) ||
                (!item.isSellable && Config.SELL_SELLABLE_ONLY) ||
                (!item.isDropable && Config.SELL_DROPABLE_ONLY) ||
                (!item.isDepositable && Config.SELL_DEPOSITABLE_ONLY))
            {
                continue;
            }
            
            // Skip items with missing or zero prices
            if (item.price <= 0)
            {
                continue;
            }
            
            // Add to sell database
            ItemData sellItemData = new ItemData(item.id, item.price, item.name);
            // Set quantity based on stackable status
            if (item.isStackable)
            {
                sellItemData.setMinQuantity(Config.MIN_SELL_ITEM_COUNT);
                sellItemData.setMaxQuantity(Config.MAX_SELL_ITEM_COUNT);
            }
            else
            {
                // Non-stackable items like weapons and armor should be quantity 1
                sellItemData.setMinQuantity(1);
                sellItemData.setMaxQuantity(1);
            }
            
            ITEM_DATABASE.add(sellItemData);
            sellItemsAdded++;
            
            // Add to buy database - less restrictive
            // For buying, prices are often lower than selling prices
            int buyPrice = (int)(item.price * 0.7); // 70% of reference price
            ItemData buyItemData = new ItemData(item.id, buyPrice, item.name);
            // Set quantity based on stackable status
            if (item.isStackable)
            {
                buyItemData.setMinQuantity(Config.MIN_BUY_ITEM_COUNT);
                buyItemData.setMaxQuantity(Config.MAX_BUY_ITEM_COUNT);
            }
            else
            {
                // Non-stackable items like weapons and armor should be quantity 1
                buyItemData.setMinQuantity(1);
                buyItemData.setMaxQuantity(1);
            }
            
            ITEM_BUY_DATABASE.add(buyItemData);
            buyItemsAdded++;
            
            // Limit to a reasonable number of items
            if (sellItemsAdded >= 1000)
            {
                break;
            }
        }
        
        LOGGER.info("Loaded " + sellItemsAdded + " sell items and " + buyItemsAdded + " buy items from game XML files.");
    }
    
    /**
     * Load recipe data from Recipes.xml
     */
    private void loadRecipesFromXML()
    {
        LOGGER.info("Loading recipe data from Recipes.xml...");
        
        try {
            File recipesFile = new File(org.l2jmobius.Config.DATAPACK_ROOT, RECIPES_FILE_PATH);
            if (!recipesFile.exists())
            {
                LOGGER.warning("Recipes.xml file not found: " + recipesFile.getAbsolutePath());
                return;
            }
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(recipesFile);
            
            NodeList recipeNodes = doc.getElementsByTagName("item");
            
            for (int i = 0; i < recipeNodes.getLength(); i++)
            {
                Element recipeElement = (Element) recipeNodes.item(i);
                
                try {
                    int recipeId = Integer.parseInt(recipeElement.getAttribute("recipeId"));
                    String name = recipeElement.getAttribute("name");
                    int craftLevel = Integer.parseInt(recipeElement.getAttribute("craftLevel"));
                    String type = recipeElement.getAttribute("type");
                    int successRate = Integer.parseInt(recipeElement.getAttribute("successRate"));
                    
                    // Find production element to get the item ID
                    NodeList productionNodes = recipeElement.getElementsByTagName("production");
                    if (productionNodes.getLength() == 0)
                    {
                        continue; // Skip recipes without production
                    }
                    
                    Element productionElement = (Element) productionNodes.item(0);
                    int itemId = Integer.parseInt(productionElement.getAttribute("id"));
                    
                    // Create recipe data
                    RecipeData recipeData = new RecipeData(recipeId, itemId, name, craftLevel, type, successRate);
                    
                    // Add ingredients
                    NodeList ingredientNodes = recipeElement.getElementsByTagName("ingredient");
                    for (int j = 0; j < ingredientNodes.getLength(); j++)
                    {
                        Element ingredientElement = (Element) ingredientNodes.item(j);
                        int ingredientId = Integer.parseInt(ingredientElement.getAttribute("id"));
                        int count = Integer.parseInt(ingredientElement.getAttribute("count"));
                        recipeData.addIngredient(ingredientId, count);
                    }
                    
                    // Calculate price based on the produced item's price
                    ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
                    if (itemTemplate != null)
                    {
                        int basePrice = itemTemplate.getReferencePrice();
                        
                        // Calculate price based on success rate
                        int price;
                        if (successRate == 100)
                        {
                            // 100% success rate - half the item price
                            price = basePrice / 2;
                        }
                        else
                        {
                            // Lower success rate - quarter of the item price
                            price = basePrice / 4;
                        }
                        
                        // Apply crystal type multiplier
                        int multiplier = getCrafterPriceMultiplier(itemTemplate.getCrystalType().toString(), recipeData.isSoulOrSpiritShot());
                        price = (int) (price * (multiplier / 100.0));
                        
                        // Check for exception recipes
                        if (Config.EXCEPTION_RECIPE_PRICE.containsKey(recipeId))
                        {
                            int exceptionMultiplier = Config.EXCEPTION_RECIPE_PRICE.get(recipeId);
                            price = (int) (basePrice * (exceptionMultiplier / 100.0));
                        }
                        
                        recipeData.setPrice(Math.max(price, 100)); // Minimum price of 100
                    }
                    
                    RECIPE_DATABASE.add(recipeData);
                }
                catch (Exception e)
                {
                    LOGGER.warning("Error parsing recipe data: " + e.getMessage());
                }
            }
            
            LOGGER.info("Loaded " + RECIPE_DATABASE.size() + " recipes from Recipes.xml");
        }
        catch (Exception e)
        {
            LOGGER.warning("Error loading Recipes.xml: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load fake player names from file
     */
    private void loadFakePlayerNames()
    {
        final File file = new File(org.l2jmobius.Config.DATAPACK_ROOT, FAKE_PLAYERS_PATH);
        if (!file.exists())
        {
            LOGGER.warning("Fake player names file not found: " + file.getAbsolutePath());
            return;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(file)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                if (line.trim().isEmpty() || line.startsWith(";") || line.startsWith("#"))
                {
                    continue;
                }
                FAKE_PLAYER_NAMES.add(line.trim());
            }
            LOGGER.info("Loaded " + FAKE_PLAYER_NAMES.size() + " fake player names.");
        }
        catch (Exception e)
        {
            LOGGER.warning("Error loading fake player names: " + e.getMessage());
        }
    }
    
    @Override
    public String onEvent(String event, Npc npc, Player player)
    {
        // Use proper logging instead of System.out.println
        LOGGER.warning("=== TRADER SYSTEM DEBUG ===");
        LOGGER.warning("onEvent called with event: " + event);
        LOGGER.warning("NPC ID: " + (npc != null ? npc.getId() : "null"));
        LOGGER.warning("Player: " + (player != null ? player.getName() : "null"));
        
        // Safety check - player should never be null, but just in case
        if (player == null)
        {
            LOGGER.warning("Player is null in onEvent!");
            return null;
        }
        
        try {
            // Process the event without requiring a QuestState
            String htmltext;
            
            switch (event)
            {
                case "37003.htm":
                case "37003-01.htm":
                case "37003-02.htm":
                case "37003-03.htm":
                case "37003-04.htm":
                case "37003-05.htm":
                case "37003-06.htm":
                case "37003-07.htm":
                case "37003-08.htm":
                case "37003-09.htm":
                case "37003-10.htm":
                case "37003-11.htm":
                case "37003-12.htm":
                case "37003-13.htm":
                case "37003-14.htm":
                case "37003-15.htm":
                    htmltext = event;
                    break;
                    
                case "start_traders":
                    if (startTraders())
                    {
                        htmltext = "37003-05.htm"; // Changed from "37003-2.htm"
                    }
                    else
                    {
                        htmltext = "37003-06.htm"; // Changed from "37003-4.htm"
                    }
                    break;
                    
                case "stop_traders":
                    if (stopTraders())
                    {
                        htmltext = "37003-07.htm"; // Changed from "37003-3.htm"
                    }
                    else
                    {
                        htmltext = "37003-08.htm"; // Changed from "37003-5.htm"
                    }
                    break;
                    
                case "delete_db_traders":
                    LOGGER.warning("Deleting fake traders from database requested by: " + player.getName());
                    int removed = deleteFakeTradersFromDB();
                    if (removed > 0)
                    {
                        LOGGER.warning("Deleted " + removed + " fake traders from database");
                        htmltext = "37003-10.htm"; // Show success message
                    }
                    else
                    {
                        LOGGER.warning("No fake traders found in database");
                        htmltext = "37003-11.htm"; // Show no traders found message
                    }
                    break;
                
                    case "set_trader_interval":
                    try {
                        // Check if there's a parameter in the event string
                        String[] eventParams = event.split(" ");
                        if (eventParams.length > 1) {
                            int interval = Integer.parseInt(eventParams[1]);
                            setTraderRefreshInterval(interval);
                            htmltext = "37003-12.htm";
                            player.sendMessage("Trader refresh interval set to " + interval + " ms.");
                        } else {
                            // No parameter provided, show an input dialog
                            StringBuilder sb = new StringBuilder();
                            sb.append("<html><body>Trade Manager:<br>");
                            sb.append("Enter the refresh interval (in milliseconds):<br>");
                            sb.append("<table>");
                            sb.append("<tr><td><edit var=\"interval\" width=120></td></tr>");
                            sb.append("<tr><td><button value=\"Set Interval\" action=\"bypass -h Quest Q00802_ticaretyapanPcTrade set_trader_interval $interval\" width=120 height=20></td></tr>");
                            sb.append("</table><br>");
                            sb.append("Examples:<br>");
                            sb.append("- 60000 = 1 minute<br>");
                            sb.append("- 300000 = 5 minutes<br>");
                            sb.append("- 3600000 = 1 hour<br>");
                            sb.append("</body></html>");
                            
                            NpcHtmlMessage html = new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0);
                            html.setHtml(sb.toString());
                            player.sendPacket(html);
                            return null; // Special case - we've sent a custom HTML
                        }
                    } catch (Exception e) {
                        htmltext = "37003-13.htm";
                        player.sendMessage("Invalid interval value! Please enter a number in milliseconds.");
                    }
                    break;

                case "toggle_auto_refresh":
                    boolean enabled = toggleTraderAutoRefresh();
                    htmltext = enabled ? "37003-14.htm" : "37003-15.htm";
                    // Send custom message
                    player.sendMessage("Trader auto refresh is now " + (enabled ? "enabled" : "disabled") + ".");
                    break;

                default:
                    LOGGER.warning("Default HTML: 37003.htm");
                    htmltext = "37003.htm";
                    break;
            }
            
            // Add this after the switch statement but before sending HTML
            File htmlFile = new File(org.l2jmobius.Config.DATAPACK_ROOT, 
                "data/scripts/quests/Q00802_ticaretyapanPcTrade/" + htmltext);
            LOGGER.warning("Checking for HTML file: " + htmlFile.getAbsolutePath() + 
                " - Exists: " + htmlFile.exists());
            
            // Always send the HTML directly to ensure it reaches the player
            try
            {
                LOGGER.warning("Sending HTML directly: " + htmltext);
                NpcHtmlMessage html = new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0);
                html.setFile(player, "data/scripts/quests/Q00802_ticaretyapanPcTrade/" + htmltext);
                player.sendPacket(html);
            }
            catch (Exception e)
            {
                LOGGER.warning("Error sending HTML: " + e.getMessage());
            }
            
            LOGGER.warning("Returning HTML: " + htmltext);
            return htmltext;
        }
        catch (Exception e)
        {
            LOGGER.severe("ERROR IN onEvent: " + e.getMessage());
            e.printStackTrace();
            
            // In case of error, try to at least show the default dialog
            try
            {
                NpcHtmlMessage html = new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0);
                html.setFile(player, "data/scripts/quests/ Q00802_ticaretyapanPcTrade/37003.htm");
                player.sendPacket(html);
            }
            catch (Exception ex)
            {
                LOGGER.severe("Failed to send fallback HTML: " + ex.getMessage());
            }
            
            return "37003.htm";
        }
    }
    
    @Override
    public String onTalk(Npc npc, Player player)
    {
        if (player == null)
        {
            LOGGER.warning("Player is null in onTalk!");
            return null;
        }
        
        LOGGER.info("onTalk called by player: " + player.getName());
        
        // Send the main dialog HTML directly
        try
        {
            NpcHtmlMessage html = new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0);
            html.setFile(player, "data/scripts/quests/ Q00802_ticaretyapanPcTrade/37003.htm");
            player.sendPacket(html);
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error sending HTML in onTalk: " + e.getMessage(), e);
        }
        
        return "37003.htm";
    }
    
    @Override
    public String onFirstTalk(Npc npc, Player player)
    {
        if (player == null)
        {
            LOGGER.warning("Player is null in onFirstTalk!");
            return null;
        }
        
        LOGGER.info("onFirstTalk called by player: " + player.getName());
        
        // Send the main dialog HTML directly
        try
        {
            NpcHtmlMessage html = new NpcHtmlMessage(npc != null ? npc.getObjectId() : 0);
            html.setFile(player, "data/scripts/quests/Q00802_ticaretyapanPcTrade/37003.htm");
            player.sendPacket(html);
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error sending HTML in onFirstTalk: " + e.getMessage(), e);
        }
        
        return "37003.htm";
    }
    
    /**
     * Start the trader system
     * @return true if started, false if already running
     */
    private boolean startTraders() {
        if (_traderUpdateTask != null) {
            return false; // Already running
        }
        
        // Load the XML item data if it hasn't been loaded yet
        if (XMLItemManager.getTradableItems().isEmpty()) {
            XMLItemManager.load();
        }
        
        // Clear any existing traders
        clearTraders();
        
        // Initialize sell traders
        for (int i = 0; i < Config.INITIAL_TRADER_COUNT_SELL; i++) {
            // Decide trader type
            int typeRoll = Rnd.get(100);
            
            int runningTotal = 0;
            
            // Weapon traders
            runningTotal += Config.WEAPON_TRADER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createWeaponTrader(false); // Sell mode
                continue;
            }
            
            // Armor traders
            runningTotal += Config.ARMOR_TRADER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createArmorTrader(false); // Sell mode
                continue;
            }
            
            // EtcItem traders
            runningTotal += Config.ETCITEM_TRADER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createEtcItemTrader(false); // Sell mode
                continue;
            }
            
            // Craft traders - use the percentage value to determine if this trader should be created
            // This ensures CRAFT_TRADER_PERCENTAGE is actually used
            if (runningTotal < 100 && runningTotal + Config.CRAFT_TRADER_PERCENTAGE >= 100) {
                createCraftTrader(false); // Sell mode
            }
        }
        
        // Initialize buy traders
        for (int i = 0; i < Config.INITIAL_TRADER_COUNT_BUY; i++) {
            // Decide trader type
            int typeRoll = Rnd.get(100);
            
            int runningTotal = 0;
            
            // Weapon traders
            runningTotal += Config.WEAPON_TRADER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createWeaponTrader(true); // Buy mode
                continue;
            }
            
            // Armor traders
            runningTotal += Config.ARMOR_TRADER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createArmorTrader(true); // Buy mode
                continue;
            }
            
            // EtcItem traders
            runningTotal += Config.ETCITEM_TRADER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createEtcItemTrader(true); // Buy mode
                continue;
            }
            
            // Craft traders
            createCraftTrader(true); // Buy mode
        }
        
        // Initialize crafters
        for (int i = 0; i < Config.INITIAL_CRAFTER_TRADER_COUNT; i++) {
            // Decide crafter type
            int typeRoll = Rnd.get(100);
            
            int runningTotal = 0;
            
            // Weapon crafters
            runningTotal += Config.WEAPON_CRAFTER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createWeaponCrafter();
                continue;
            }
            
            // Armor crafters
            runningTotal += Config.ARMOR_CRAFTER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createArmorCrafter();
                continue;
            }
            
            // EtcItem crafters
            runningTotal += Config.ETCITEM_CRAFTER_PERCENTAGE;
            if (typeRoll < runningTotal) {
                createEtcItemCrafter();
                continue;
            }
            
            // Soul/Spirit Shot crafters
            createSoulSpiritShotCrafter();
        }
        
        // Schedule regular updates to refresh traders
        if (Config.TRADER_AUTO_REFRESH) {
            _traderUpdateTask = ThreadPool.scheduleAtFixedRate(() -> {
                // Remove a random trader
                if (!ACTIVE_TRADERS.isEmpty()) {
                    final int[] objectIds = ACTIVE_TRADERS.keySet().stream().mapToInt(Integer::intValue).toArray();
                    if (objectIds.length > 0) {
                        final int toRemove = objectIds[Rnd.get(objectIds.length)];
                        final PlayerTrader traderToRemove = ACTIVE_TRADERS.get(toRemove);
                        removeTrader(toRemove);
                        
                        // Add a new trader of the same type (buy/sell)
                        addRandomTraderOfType(traderToRemove.isBuyMode());
                    }
                }
            }, Config.TRADER_REFRESH_INTERVAL, Config.TRADER_REFRESH_INTERVAL);
        }
        
        // Start trade announcements if enabled
        if (Config.ENABLE_TRADE_ANNOUNCEMENTS) {
            startTradeAnnouncements();
        }
        
        return true;
    }
    
    /**
     * Stop the trader system
     * @return true if stopped, false if not running
     */
    private boolean stopTraders()
    {
        if (_traderUpdateTask == null)
        {
            return false; // Not running
        }
        
        // Cancel the update task
        _traderUpdateTask.cancel(true);
        _traderUpdateTask = null;
        
        // Cancel the announcement task if it's running
        if (_tradeAnnouncementTask != null)
        {
            _tradeAnnouncementTask.cancel(true);
            _tradeAnnouncementTask = null;
        }
        
        // Clear all traders
        clearTraders();
        
        return true;
    }
    
    /**
     * Clear all active traders
     */
    private void clearTraders()
    {
        for (Integer objectId : new ArrayList<>(ACTIVE_TRADERS.keySet()))
        {
            removeTrader(objectId);
        }
        
        ACTIVE_TRADERS.clear();
    }
    
    /**
     * Add a random trader of specified type (buy or sell)
     * @param buyMode true for buy mode, false for sell mode
     */
    private void addRandomTraderOfType(boolean buyMode) {
        // Decide trader type
        int typeRoll = Rnd.get(100);
        
        int runningTotal = 0;
        
        // Weapon traders
        runningTotal += Config.WEAPON_TRADER_PERCENTAGE;
        if (typeRoll < runningTotal) {
            createWeaponTrader(buyMode);
            return;
        }
        
        // Armor traders
        runningTotal += Config.ARMOR_TRADER_PERCENTAGE;
        if (typeRoll < runningTotal) {
            createArmorTrader(buyMode);
            return;
        }
        
        // EtcItem traders
        runningTotal += Config.ETCITEM_TRADER_PERCENTAGE;
        if (typeRoll < runningTotal) {
            createEtcItemTrader(buyMode);
            return;
        }
        
        // Craft traders
        createCraftTrader(buyMode);
    }
    
    /**
     * Create a new random trader with random properties
     */
    private void createRandomTrader()
    {
        try
        {
            // Determine if this should be a buy or sell trader
            boolean buyMode = false;
            if (Config.ENABLE_BUY_MODE && Rnd.get(100) < 50)  // 50/50 chance for buy/sell
            {
                buyMode = true;
                
                // Only create buy mode traders if we have items in the buy database
                if (ITEM_BUY_DATABASE.isEmpty())
                {
                    buyMode = false;
                    LOGGER.warning("Buy mode requested but buy item database is empty, defaulting to sell mode");
                }
            }
            
            // Generate a random name
            final boolean isFemale = Rnd.nextBoolean();
            String name;
            if (!FAKE_PLAYER_NAMES.isEmpty())
            {
                // Use a name directly from the list
                name = FAKE_PLAYER_NAMES.get(Rnd.get(FAKE_PLAYER_NAMES.size()));
                
                // Make sure this name isn't already used
                boolean nameExists;
                int attempts = 0;
                do {
                    nameExists = false;
                    for (PlayerTrader existing : ACTIVE_TRADERS.values()) {
                        if (existing.getName().equals(name)) {
                            nameExists = true;
                            name = FAKE_PLAYER_NAMES.get(Rnd.get(FAKE_PLAYER_NAMES.size()));
                            break;
                        }
                    }
                    attempts++;
                } while (nameExists && attempts < 10);
            }
            else
            {
                final String[] namePool = isFemale ? FEMALE_NAMES : MALE_NAMES;
                name = namePool[Rnd.get(namePool.length)] + " " + LAST_NAMES[Rnd.get(LAST_NAMES.length)];
            }
            
            // Generate a random location based on trader type (buy/sell)
            final Location location = getRandomTraderLocation(buyMode);
            
            // Generate random class and race
            final Race race = Race.values()[Rnd.get(Race.values().length)];
            
            // Select a class ID for the race
            int classId;
            switch (race)
            {
                case HUMAN:
                    classId = isFemale ? 0x01 : 0x00; // Human Fighter
                    break;
                case ELF:
                    classId = isFemale ? 0x12 : 0x11; // Elven Fighter
                    break;
                case DARK_ELF:
                    classId = isFemale ? 0x26 : 0x25; // Dark Elf Fighter
                    break;
                case ORC:
                    classId = isFemale ? 0x31 : 0x30; // Orc Fighter
                    break;
                case DWARF:
                    classId = isFemale ? 0x39 : 0x38; // Dwarven Fighter
                    break;
                case KAMAEL:
                    classId = isFemale ? 0x7b : 0x7A; // Kamael Soldier
                    break;
                default:
                    classId = 0x00; // Default to human fighter
                    break;
            }
            
            // Pre-select items for trade
            // Use config values for min/max item types based on mode
            final int minItemCount = buyMode ? Config.MIN_BUY_ITEM_TYPES : Config.MIN_SELL_ITEM_TYPES;
            final int maxItemCount = buyMode ? Config.MAX_BUY_ITEM_TYPES : Config.MAX_SELL_ITEM_TYPES;
            final int itemCount = minItemCount + Rnd.get(maxItemCount - minItemCount + 1);
            List<ItemData> selectedItems = new ArrayList<>();
            
            // Source the items from the appropriate database
            List<ItemData> sourceDatabase = buyMode ? ITEM_BUY_DATABASE : ITEM_DATABASE;
            
            if (!sourceDatabase.isEmpty())
            {
                LOGGER.warning("Selecting random items for " + (buyMode ? "buyer" : "seller") + " " + name + 
                              " from " + sourceDatabase.size() + " available items");
                
                // Create a set to avoid duplicates
                Set<Integer> selectedIndexes = new HashSet<>();
                
                // Choose unique random items
                for (int i = 0; i < itemCount*3 && selectedIndexes.size() < sourceDatabase.size() && selectedItems.size() < itemCount; i++)
                {
                    int randomIndex = Rnd.get(sourceDatabase.size());
                    if (selectedIndexes.add(randomIndex)) {
                        final ItemData item = sourceDatabase.get(randomIndex);
                        final ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(item.getId());
                        
                        if (itemTemplate != null)
                        {
                            // Check if item is tradable (for sell mode)
                            boolean canBeTraded = true;
                            if (!buyMode) {
                                // Config.ALLOW_SELL_NON_TRADABLE is true by default, but kept for future configurability
                                if (!Config.ALLOW_SELL_NON_TRADABLE) {
                                    canBeTraded = !itemTemplate.isQuestItem() && itemTemplate.isTradeable();
                                }
                            }
                            
                            if ((buyMode || canBeTraded) && item.getPrice() > 0)
                            {
                                selectedItems.add(item);
                                LOGGER.warning("Selected item ID " + item.getId() + " for " + (buyMode ? "buyer" : "seller") + " " + name);
                            }
                        }
                    }
                }
                
                LOGGER.warning("Selected " + selectedItems.size() + " items for " + (buyMode ? "buyer" : "seller") + " " + name);
            }
            
            // Generate store title with only item names
            String title = generateStoreTitle(selectedItems, buyMode);
            
            LOGGER.warning("Created title for " + (buyMode ? "buyer" : "seller") + " " + name + 
                          ": '" + title + "' (length: " + title.length() + ")");
            
            // Create the trader
            final int objectId = getNewObjectId();
            final TradeList tradeList = new TradeList(null);
            tradeList.setTitle(title);
            
            final PlayerTrader trader = new PlayerTrader(objectId, name, tradeList, classId, race, isFemale, location, title, buyMode);
            trader.setSelectedItems(selectedItems);
            
            // Create and spawn the fake player
            spawnTrader(trader);
            
            ACTIVE_TRADERS.put(objectId, trader);
        }
        catch (Exception e)
        {
            LOGGER.warning("Error creating trader: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Find an item by its ID
     * @param id The item ID
     * @return The ItemData or null if not found Note: Utility method kept for potential future use
     */
    @SuppressWarnings("unused")
    private ItemData findItemById(int id)
    {
        for (ItemData item : ITEM_DATABASE)
        {
            if (item.getId() == id)
            {
                return item;
            }
        }
        return null;
    }
    
    /**
     * Spawn a player trader in the game world
     * @param trader The trader to spawn
     */
    private void spawnTrader(PlayerTrader trader)
    {
        try
        {
            // Add debugging information
            LOGGER.warning("==============================");
            LOGGER.warning("SPAWNING " + (trader.isBuyMode() ? "BUYER" : "SELLER") + ": " + trader.getName());
            LOGGER.warning("ObjectID: " + trader.getObjectId());
            LOGGER.warning("Title: " + trader.getTitle() + " (Length: " + trader.getTitle().length() + ")");
            LOGGER.warning("Race: " + trader.getRace());
            LOGGER.warning("Class ID: " + trader.getClassId());
            LOGGER.warning("Location: " + trader.getLocation().getX() + ", " + trader.getLocation().getY() + ", " + trader.getLocation().getZ());
            LOGGER.warning("Selected items: " + trader.getSelectedItems().size());
            LOGGER.warning("==============================");
            
            // Create a fake player using the proper constructors
            PlayerTemplate playerTemplate = PlayerTemplateData.getInstance().getTemplate(trader.getClassId());
            PlayerAppearance appearance = new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, trader.isFemale());
            
            // Create the player instance
            final Player fakePc;
            try
            {
                java.lang.reflect.Constructor<Player> constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class);
                constructor.setAccessible(true);
                fakePc = constructor.newInstance(trader.getObjectId(), playerTemplate, trader.getName(), appearance);
            }
            catch (Exception e)
            {
                LOGGER.warning("Failed to create player via reflection: " + e.getMessage());
                return;
            }
            
            // Set player properties
            fakePc.setName(trader.getName());
            fakePc.setTitle(""); // No title
            fakePc.setAccessLevel(0);
            fakePc.setClassId(trader.getClassId());
            fakePc.setBaseClass(trader.getClassId());
            fakePc.setHeading(trader.getLocation().getHeading());
            fakePc.setXYZ(trader.getLocation().getX(), trader.getLocation().getY(), trader.getLocation().getZ());
            
            // CRITICAL: First check on title length before database insertion
            String safeTitle = trader.getTitle();
            if (safeTitle.length() > 16)
            {
                safeTitle = safeTitle.substring(0, 13) + "...";
                LOGGER.warning("Title truncated for database insertion: " + safeTitle);
            }
            
            // 1. CREATE CHARACTER FIRST - Create character entry in DB
            try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO characters (account_name, charId, char_name, level, maxHp, curHp, maxCp, curCp, maxMp, curMp, face, hairStyle, hairColor, sex, heading, x, y, z, exp, sp, race, classid, base_class, title, title_color, accesslevel, online, lastAccess) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
            {
                String accountName = "fakeacc" + String.format("%07d", trader.getObjectId());
                ps.setString(1, accountName); // account_name
                ps.setInt(2, fakePc.getObjectId()); // charId
                ps.setString(3, trader.getName()); // char_name
                ps.setInt(4, 80); // level
                ps.setInt(5, 8000); // maxHp
                ps.setInt(6, 8000); // curHp
                ps.setInt(7, 4000); // maxCp
                ps.setInt(8, 4000); // curCp
                ps.setInt(9, 1800); // maxMp
                ps.setInt(10, 1800); // curMp
                ps.setInt(11, 0); // face
                ps.setInt(12, 0); // hairStyle
                ps.setInt(13, 0); // hairColor
                ps.setInt(14, trader.isFemale() ? 1 : 0); // sex
                ps.setInt(15, trader.getLocation().getHeading()); // heading
                ps.setInt(16, trader.getLocation().getX()); // x
                ps.setInt(17, trader.getLocation().getY()); // y
                ps.setInt(18, trader.getLocation().getZ()); // z
                ps.setLong(19, 10000000000L); // exp
                ps.setLong(20, 1000000000); // sp
                ps.setInt(21, trader.getRace().ordinal()); // race
                ps.setInt(22, trader.getClassId()); // classid
                ps.setInt(23, trader.getClassId()); // base_class
                ps.setString(24, safeTitle); // title (using safe truncated version)
                ps.setInt(25, 0xECF9A2); // title_color
                ps.setInt(26, 0); // accesslevel
                ps.setInt(27, 0); // online
                ps.setLong(28, System.currentTimeMillis()); // lastAccess
                
                int characterResult = ps.executeUpdate();
                LOGGER.warning("Characters table insert result: " + characterResult + " rows affected");
            }
            catch (SQLException e)
            {
                LOGGER.warning("Error creating character entry: " + e.getMessage());
                e.printStackTrace();
                return; // Don't proceed if character creation failed
            }
            
            // Check if character was actually inserted
            try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement checkPs = con.prepareStatement("SELECT COUNT(*) FROM characters WHERE charId = ?"))
            {
                checkPs.setInt(1, fakePc.getObjectId());
                try (ResultSet rs = checkPs.executeQuery())
                {
                    if (rs.next())
                    {
                        int count = rs.getInt(1);
                        LOGGER.warning("Character exists in database: " + (count > 0 ? "YES" : "NO"));
                    }
                }
            }
            catch (SQLException e)
            {
                LOGGER.warning("Error checking if character exists: " + e.getMessage());
            }
            
            // 2. Create account entry
            try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO accounts (login, password, email, created_time, lastactive, accessLevel, lastIP, lastServer, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"))
            {
                String accountName = "fakeacc" + String.format("%07d", trader.getObjectId());
                ps.setString(1, accountName);
                ps.setString(2, "7XDFfXVk6ZTn1fb9aWfOqLNH77w="); // Standard encrypted password
                ps.setString(3, accountName + "@gmail.com");
                ps.setString(4, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                ps.setLong(5, System.currentTimeMillis());
                ps.setInt(6, 0); // accessLevel
                ps.setString(7, "127.0.0.1");
                ps.setInt(8, 1); // lastServer
                ps.setInt(9, 1); // is_active
                
                int accountResult = ps.executeUpdate();
                LOGGER.warning("Account created for trader " + trader.getName() + ": " + accountName + ", Result: " + accountResult);
            }
            catch (SQLException e)
            {
                LOGGER.warning("Error creating account entry: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 3. CRITICAL: Now add to world so the player exists
            World.getInstance().addObject(fakePc);
            fakePc.spawnMe();
            LOGGER.warning("Trader " + trader.getName() + " added to world successfully");
            
            // 4. TRADE SETUP - Now set up offline trade in DB
            try (Connection con = DatabaseFactory.getConnection())
            {
                // Set up character_offline_trade first
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_offline_trade (charId, time, type, title) VALUES (?, ?, ?, ?)"))
                {
                    ps.setInt(1, fakePc.getObjectId());
                    ps.setLong(2, System.currentTimeMillis());
                    
                    if (trader.isBuyMode()) {
                        ps.setInt(3, 3); // BUY mode
                        LOGGER.warning("Setting trader " + trader.getName() + " in BUY mode (type 3)");
                    } else {
                        ps.setInt(3, 1); // SELL mode
                        LOGGER.warning("Setting trader " + trader.getName() + " in SELL mode (type 1)");
                    }
                    ps.setString(4, trader.getTitle());
                    
                    int tradeResult = ps.executeUpdate();
                    LOGGER.warning("character_offline_trade insert result: " + tradeResult);
                }
                
                // Create items one by one with detailed logging
                int itemsAdded = 0;
                
                for (ItemData itemData : trader.getSelectedItems())
                {
                    int itemId = itemData.getId();
                    LOGGER.warning("Processing item ID " + itemId + " for " + (trader.isBuyMode() ? "buyer" : "seller") + " " + trader.getName());
                    
                    ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
                    if (itemTemplate == null)
                    {
                        LOGGER.warning("Skipping item ID " + itemId + " - template not found");
                        continue;
                    }
                    
                    // Generate item object ID
                    int itemObjectId = IdManager.getInstance().getNextId();
                    // Random quantity within the item's min-max range
                    long count = itemData.getRandomQuantity();
                    
                    LOGGER.warning("Adding item to DB: ID=" + itemId + ", objectID=" + itemObjectId + ", name=" + itemTemplate.getName() + ", count=" + count);
                    
                    // Only insert items to inventory for SELL mode (sellers need items to sell)
                    // Buy mode traders only need adena (given separately)
                    if (!trader.isBuyMode()) {
                        // Insert item into items table
                        try (PreparedStatement ps = con.prepareStatement("INSERT INTO items (owner_id, object_id, item_id, count, enchant_level, loc, loc_data) VALUES (?, ?, ?, ?, ?, ?, ?)"))
                        {
                            ps.setInt(1, fakePc.getObjectId()); // owner_id
                            ps.setInt(2, itemObjectId); // object_id
                            ps.setInt(3, itemId); // item_id
                            ps.setLong(4, count); // count
                            ps.setInt(5, 0); // enchant_level
                            ps.setString(6, "INVENTORY"); // loc
                            ps.setInt(7, 0); // loc_data
                            
                            int itemResult = ps.executeUpdate();
                            LOGGER.warning("items DB insert result: " + itemResult);
                        }
                        catch (SQLException e)
                        {
                            LOGGER.warning("Error inserting item " + itemId + ": " + e.getMessage());
                            continue; // Skip to next item if this one fails
                        }
                    }
                    
                    // Then add to offline trade items table (both buy and sell mode)
                    try (PreparedStatement psTradeItem = con.prepareStatement("INSERT INTO character_offline_trade_items (charId, item, count, price) VALUES (?, ?, ?, ?)"))
                    {
                        psTradeItem.setInt(1, fakePc.getObjectId()); // charId
                        
                        if (trader.isBuyMode()) {
                            // For buy mode, store the actual item ID directly in the 'item' field
                            // This ensures we can retrieve the correct item ID for the buy list
                            psTradeItem.setInt(2, itemId); // use the item_id directly
                        } else {
                            psTradeItem.setInt(2, itemObjectId); // use the object_id for sell mode
                        }
                        
                        psTradeItem.setLong(3, count); // count
                        psTradeItem.setLong(4, itemData.getPrice()); // price
                        
                        int tradeItemResult = psTradeItem.executeUpdate();
                        LOGGER.warning("character_offline_trade_items insert result: " + tradeItemResult);
                        
                        if (tradeItemResult > 0)
                        {
                            itemsAdded++;
                        }
                    }
                }
                
                LOGGER.warning("Added " + itemsAdded + " items to database for " + (trader.isBuyMode() ? "buyer" : "seller") + " " + trader.getName());
                
                // Set up the private store based on mode
                fakePc.setPrivateStoreType(trader.isBuyMode() ? PrivateStoreType.BUY : PrivateStoreType.SELL);
                
                if (trader.isBuyMode())
                {
                    fakePc.getBuyList().setTitle(trader.getTitle());
                    
                    // Give adena to buyer before loading trade items
                    giveAdenaToTrader(fakePc);
                }
                else
                {
                    fakePc.getSellList().setTitle(trader.getTitle());
                }
                
                // Now load items from database to trade list
                LOGGER.warning("Loading trade items from database for " + (trader.isBuyMode() ? "buyer" : "seller") + " " + trader.getName());
                int itemsLoaded = 0;
                
                // FIXED: Use different queries for buy and sell modes
                if (trader.isBuyMode()) {
                    // For buy mode traders - simplified loading directly using item IDs
                    try (PreparedStatement ps = con.prepareStatement(
                        "SELECT item, count, price FROM character_offline_trade_items WHERE charId = ?"))
                    {
                        ps.setInt(1, fakePc.getObjectId());
                        try (ResultSet rs = ps.executeQuery())
                        {
                            while (rs.next())
                            {
                                // For buy mode, the 'item' column now directly contains the itemId
                                int itemId = rs.getInt("item");
                                long count = rs.getLong("count");
                                long price = rs.getLong("price");
                                
                                // Verify itemId with a lookup to ensure it's valid
                                ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
                                if (itemTemplate != null) {
                                    LOGGER.warning("Adding to BUY list: itemId=" + itemId + ", count=" + count + ", price=" + price);
                                    
                                    try {
                                        // Add directly to buy list by item ID - the key fix for buy mode
                                        fakePc.getBuyList().addItemByItemId(itemId, count, price);
                                        itemsLoaded++;
                                        LOGGER.warning("Added item to BUY list: " + itemId + " x" + count + " @" + price);
                                    } catch (Exception e) {
                                        LOGGER.warning("Error adding item " + itemId + " to buy list: " + e.getMessage());
                                    }
                                } else {
                                    LOGGER.warning("Cannot find template for item ID: " + itemId);
                                }
                            }
                        }
                    }
                } else {
                    // Original code for sell mode - they need actual inventory items
                    try (PreparedStatement ps = con.prepareStatement(
                        "SELECT i.object_id, i.item_id, coti.count, coti.price FROM character_offline_trade_items coti JOIN items i ON coti.item = i.object_id WHERE coti.charId = ?"))
                    {
                        ps.setInt(1, fakePc.getObjectId());
                        try (ResultSet rs = ps.executeQuery())
                        {
                            while (rs.next())
                            {
                                int objectId = rs.getInt("object_id");
                                int itemId = rs.getInt("item_id");
                                long count = rs.getLong("count");
                                long price = rs.getLong("price");
                                
                                // Verify object ID with a lookup to ensure it exists
                                ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
                                if (itemTemplate != null) {
                                    LOGGER.warning("Adding to SELL list: objectId=" + objectId + ", itemId=" + itemId + ", count=" + count + ", price=" + price);
                                    
                                    try {
                                        // Add to sell list using the object ID
                                        fakePc.getSellList().addItem(objectId, count, price);
                                        itemsLoaded++;
                                        LOGGER.warning("Added item to SELL list: " + itemId + " x" + count + " @" + price);
                                    } catch (Exception e) {
                                        LOGGER.warning("Error adding item " + itemId + " to sell list: " + e.getMessage());
                                    }
                                } else {
                                    LOGGER.warning("Cannot find template for item ID: " + itemId);
                                }
                            }
                        }
                    }
                }
                
                // Force update trade lists
                if (trader.isBuyMode()) {
                    fakePc.getBuyList().updateItems();
                } else {
                    fakePc.getSellList().updateItems();
                }
                
                LOGGER.warning("Loaded " + itemsLoaded + " items to trader " + trader.getName() + "'s " + (trader.isBuyMode() ? "buy" : "sell") + " list");
            }
            catch (SQLException e)
            {
                LOGGER.warning("Database error in trader setup: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Make sure they sit down
            fakePc.sitDown();
            LOGGER.warning("Trader " + trader.getName() + " is now sitting");
            
            // Make sure all nearby players can see their store message
            World.getInstance().forEachVisibleObject(fakePc, Player.class, player ->
            {
                if (trader.isBuyMode())
                {
                    player.sendPacket(new PrivateStoreMsgBuy(fakePc));
                }
                else
                {
                    player.sendPacket(new PrivateStoreMsgSell(fakePc));
                }
            });
            
            // Store reference to the fake player
            trader.setFakePc(fakePc);
            LOGGER.warning("Trader " + trader.getName() + " successfully spawned");
        }
        catch (Exception e)
        {
            LOGGER.warning("Failed to spawn trader: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get a new unique object ID for creating a fake player
     * @return An unused object ID
     */
    private int getNewObjectId()
    {
        int objectId = IdManager.getInstance().getNextId();
        
        // Make sure this ID isn't being used by a real player (very important!)
        boolean idExists = (World.getInstance().getPlayer(objectId) != null);
        
        // Check if this ID already exists in active traders
        idExists = idExists || ACTIVE_TRADERS.containsKey(objectId);
        
        if (idExists)
        {
            // If we have a conflicting ID, go through IdManager again
            IdManager.getInstance().releaseId(objectId);
            
            // Get a fresh ID with a large offset to avoid real player IDs
            objectId = IdManager.getInstance().getNextId() + 1000000;
            LOGGER.warning("Generated alternative objectId to avoid conflict: " + objectId);
        }
        
        return objectId;
    }
    
    /**
     * Remove a trader by object ID
     * @param objectId The trader's object ID
     */
    private void removeTrader(int objectId)
    {
        final PlayerTrader trader = ACTIVE_TRADERS.remove(objectId);
        if (trader != null && trader.getFakePc() != null)
        {
            final Player fakePc = trader.getFakePc();
            fakePc.decayMe();
            World.getInstance().removeObject(fakePc);
        }
    }
    
    public static Q00802_ticaretyapanPcTrade getInstance()
    {
        return (Q00802_ticaretyapanPcTrade) QuestManager.getInstance().getQuest(802);
    }
    
    /**
     * Delete all fake traders from the database
     * @return Number of traders removed
     */
    private int deleteFakeTradersFromDB()
    {
        int totalRemoved = 0;
        
        try (Connection con = DatabaseFactory.getConnection())
        {
            // Find all accounts that start with "fakeacc"
            try (PreparedStatement ps = con.prepareStatement("SELECT login FROM accounts WHERE login LIKE 'fakeacc%'"))
            {
                try (ResultSet rs = ps.executeQuery())
                {
                    List<String> fakeAccounts = new ArrayList<>();
                    while (rs.next())
                    {
                        fakeAccounts.add(rs.getString("login"));
                    }
                    
                    LOGGER.warning("Found " + fakeAccounts.size() + " fake accounts to delete");
                    
                    // For each fake account, get the charId and delete all related data
                    for (String account : fakeAccounts)
                    {
                        try (PreparedStatement ps2 = con.prepareStatement("SELECT charId FROM characters WHERE account_name = ?"))
                        {
                            ps2.setString(1, account);
                            try (ResultSet rs2 = ps2.executeQuery())
                            {
                                while (rs2.next())
                                {
                                    int charId = rs2.getInt("charId");
                                    
                                    // Delete from character_offline_trade_items
                                    try (PreparedStatement ps3 = con.prepareStatement("DELETE FROM character_offline_trade_items WHERE charId = ?"))
                                    {
                                        ps3.setInt(1, charId);
                                        int deleted = ps3.executeUpdate();
                                        LOGGER.warning("Deleted " + deleted + " rows from character_offline_trade_items for charId " + charId);
                                    }
                                    
                                    // Delete from character_offline_trade
                                    try (PreparedStatement ps4 = con.prepareStatement("DELETE FROM character_offline_trade WHERE charId = ?"))
                                    {
                                        ps4.setInt(1, charId);
                                        int deleted = ps4.executeUpdate();
                                        LOGGER.warning("Deleted " + deleted + " rows from character_offline_trade for charId " + charId);
                                    }
                                    
                                    // Delete items
                                    try (PreparedStatement ps5 = con.prepareStatement("DELETE FROM items WHERE owner_id = ?"))
                                    {
                                        ps5.setInt(1, charId);
                                        int deleted = ps5.executeUpdate();
                                        LOGGER.warning("Deleted " + deleted + " items for charId " + charId);
                                    }
                                    
                                    // Delete character
                                    try (PreparedStatement ps6 = con.prepareStatement("DELETE FROM characters WHERE charId = ?"))
                                    {
                                        ps6.setInt(1, charId);
                                        int deleted = ps6.executeUpdate();
                                        LOGGER.warning("Deleted " + deleted + " character entries for charId " + charId);
                                        totalRemoved += deleted;
                                    }
                                }
                            }
                        }
                        
                        // Delete the account
                        try (PreparedStatement ps7 = con.prepareStatement("DELETE FROM accounts WHERE login = ?"))
                        {
                            ps7.setString(1, account);
                            int deleted = ps7.executeUpdate();
                            LOGGER.warning("Deleted account: " + account + " (" + deleted + " rows affected)");
                        }
                    }
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.warning("Error deleting fake traders from database: " + e.getMessage());
            e.printStackTrace();
        }
        
        return totalRemoved;
    }
    
    /**
     * Start the trade announcement system
     */
    private void startTradeAnnouncements()
    {
        if (_tradeAnnouncementTask != null)
        {
            return; // Already running
        }
        
        // Schedule regular announcements from traders
        _tradeAnnouncementTask = ThreadPool.scheduleAtFixedRate(() ->
        {
            if (ACTIVE_TRADERS.isEmpty())
            {
                return; // No traders to announce
            }
            
            final long currentTime = System.currentTimeMillis();
            
            // Select a random trader to announce
            final PlayerTrader[] traders = ACTIVE_TRADERS.values().toArray(new PlayerTrader[0]);
            if (traders.length > 0)
            {
                // Find traders who haven't announced recently
                List<PlayerTrader> eligibleTraders = new ArrayList<>();
                for (PlayerTrader trader : traders)
                {
                    if (currentTime - trader.getLastAnnouncementTime() > Config.TRADE_ANNOUNCEMENT_INTERVAL)
                    {
                        eligibleTraders.add(trader);
                    }
                }
                
                // If there are eligible traders, pick one randomly
                if (!eligibleTraders.isEmpty())
                {
                    PlayerTrader announcer = eligibleTraders.get(Rnd.get(eligibleTraders.size()));
                    
                    // Generate and send the announcement - updated method call
                    String message = generateTradeAnnouncement(announcer);
                    
                    // Broadcast to the trade chat channel
                    LOGGER.warning("Broadcasting trade announcement: " + message);
                    
                    // Update the last announcement time
                    announcer.setLastAnnouncementTime(currentTime);
                    
                    // Get the player object if available
                    Player fakePc = announcer.getFakePc();
                    
                    // Send to all players using proper constructor
                    World.getInstance().getPlayers().forEach(player -> {
                        // Send message to trade chat channel using the correct constructor
                        player.sendPacket(new CreatureSay(fakePc, 
                                                         ChatType.TRADE, 
                                                         announcer.getName(), 
                                                         message));
                    });
                }
            }
        }, 30000, Config.TRADE_ANNOUNCEMENT_INTERVAL); // Start after 30 seconds, then every minute
    }
    
    // Add these methods to the main class
    /**
     * Set the trader refresh interval
     * @param interval The new interval in milliseconds
     */
    public void setTraderRefreshInterval(int interval) {
        if (interval < 10000) {
            interval = 10000; // Minimum 10 seconds to prevent abuse
        }
        Config.TRADER_REFRESH_INTERVAL = interval;
        
        // Restart the task if it's running
        if (_traderUpdateTask != null) {
            _traderUpdateTask.cancel(false);
            
            if (Config.TRADER_AUTO_REFRESH) {
                _traderUpdateTask = ThreadPool.scheduleAtFixedRate(() -> {
                    // Remove a random trader
                    if (!ACTIVE_TRADERS.isEmpty()) {
                        final int[] objectIds = ACTIVE_TRADERS.keySet().stream().mapToInt(Integer::intValue).toArray();
                        if (objectIds.length > 0) {
                            final int toRemove = objectIds[Rnd.get(objectIds.length)];
                            removeTrader(toRemove);
                        }
                    }
                    
                    // Add a new trader
                    createRandomTrader();
                    
                }, Config.TRADER_REFRESH_INTERVAL, Config.TRADER_REFRESH_INTERVAL);
            }
        }
    }
    
    /**
     * Toggle automatic trader refresh
     * @return true if enabled, false if disabled
     */
    public boolean toggleTraderAutoRefresh() {
        Config.TRADER_AUTO_REFRESH = !Config.TRADER_AUTO_REFRESH;
        
        if (_traderUpdateTask != null) {
            _traderUpdateTask.cancel(false);
            _traderUpdateTask = null;
        }
        
        if (Config.TRADER_AUTO_REFRESH) {
            _traderUpdateTask = ThreadPool.scheduleAtFixedRate(() -> {
                // Remove a random trader
                if (!ACTIVE_TRADERS.isEmpty()) {
                    final int[] objectIds = ACTIVE_TRADERS.keySet().stream().mapToInt(Integer::intValue).toArray();
                    if (objectIds.length > 0) {
                        final int toRemove = objectIds[Rnd.get(objectIds.length)];
                        removeTrader(toRemove);
                    }
                }
                
                // Add a new trader
                createRandomTrader();
                
            }, Config.TRADER_REFRESH_INTERVAL, Config.TRADER_REFRESH_INTERVAL);
        }
        
        return Config.TRADER_AUTO_REFRESH;
    }
    
    // Add this method to your class
    private void giveAdenaToTrader(Player fakePc) {
        try (Connection con = DatabaseFactory.getConnection()) {
            // Create adena item
            int adenaObjectId = IdManager.getInstance().getNextId();
            
            // Insert adena with large amount
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO items (owner_id, object_id, item_id, count, enchant_level, loc, loc_data) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, fakePc.getObjectId()); // owner_id
                ps.setInt(2, adenaObjectId); // object_id
                ps.setInt(3, 57); // item_id (Adena)
                ps.setLong(4, 10000000000L); // count (10 billion)
                ps.setInt(5, 0); // enchant_level
                ps.setString(6, "INVENTORY"); // loc
                ps.setInt(7, 0); // loc_data
                
                ps.executeUpdate();
                LOGGER.warning("Added 10 billion adena to trader " + fakePc.getName());
            }
        } catch (SQLException e) {
            LOGGER.warning("Error giving adena to trader: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Class to handle XML item data
     */
        public static class XMLItemManager {
        private static final Map<Integer, ItemTemplate> ITEM_TEMPLATES = new HashMap<>();
        private static final Map<String, List<ItemTemplate>> ITEM_BY_TYPE = new HashMap<>();
        private static final Map<String, List<ItemTemplate>> ITEM_BY_WEAPON_TYPE = new HashMap<>();
        private static final Map<String, List<ItemTemplate>> ITEM_BY_ARMOR_TYPE = new HashMap<>();
        private static final Map<String, List<ItemTemplate>> ITEM_BY_BODYPART = new HashMap<>();
        private static final Map<String, List<ItemTemplate>> ITEM_BY_CRYSTAL_TYPE = new HashMap<>();
        private static final List<ItemTemplate> STACKABLE_ITEMS = new ArrayList<>();
        private static final List<ItemTemplate> NON_STACKABLE_ITEMS = new ArrayList<>();
        private static final List<ItemTemplate> TRADABLE_ITEMS = new ArrayList<>();
        
        public static void load() {
            LOGGER.info("Loading XML item data for trader system...");
            
            // Use the game's ItemData class to get all item templates
            for (ItemTemplate itemTemplate : org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getAllItems()) {
                if (itemTemplate == null) {
                    continue;
                }
                
                ITEM_TEMPLATES.put(itemTemplate.getId(), itemTemplate);
                
                // Categorize by item type
                String type = itemTemplate.getItemType().toString();
                ITEM_BY_TYPE.computeIfAbsent(type, _ -> new ArrayList<>()).add(itemTemplate);
                
                // Categorize by weapon type
                if (itemTemplate instanceof Weapon) {
                    Weapon weapon = (Weapon) itemTemplate;
                    String weaponType = weapon.getItemType().toString();
                    ITEM_BY_WEAPON_TYPE.computeIfAbsent(weaponType, _ -> new ArrayList<>()).add(itemTemplate);
                    
                    // Magic weapons
                    if (weapon.isMagicWeapon()) {
                        ITEM_BY_WEAPON_TYPE.computeIfAbsent("MAGIC", _ -> new ArrayList<>()).add(itemTemplate);
                    }
                }
                
                // Categorize by armor type
                if (itemTemplate instanceof Armor) {
                    Armor armor = (Armor) itemTemplate;
                    String armorType = armor.getItemType().toString();
                    ITEM_BY_ARMOR_TYPE.computeIfAbsent(armorType, _ -> new ArrayList<>()).add(itemTemplate);
                }
                
                // Categorize by bodypart
                String bodyPart = String.valueOf(itemTemplate.getBodyPart());
                ITEM_BY_BODYPART.computeIfAbsent(bodyPart, _ -> new ArrayList<>()).add(itemTemplate);
                
                // Categorize by crystal type
                String crystalType = itemTemplate.getCrystalType().toString();
                ITEM_BY_CRYSTAL_TYPE.computeIfAbsent(crystalType, _ -> new ArrayList<>()).add(itemTemplate);
                
                // Stackable/non-stackable
                if (itemTemplate.isStackable()) {
                    STACKABLE_ITEMS.add(itemTemplate);
                } else {
                    NON_STACKABLE_ITEMS.add(itemTemplate);
                }
                
                // Tradable items
                if (itemTemplate.isTradeable()) {
                    TRADABLE_ITEMS.add(itemTemplate);
                }
            }
            
            LOGGER.info("XML item data loaded: " + ITEM_TEMPLATES.size() + " items categorized.");
        }
        
        public static List<ItemTemplate> getItemsByType(String type) {
            return ITEM_BY_TYPE.getOrDefault(type, Collections.emptyList());
        }
        
        public static List<ItemTemplate> getItemsByWeaponType(String weaponType) {
            return ITEM_BY_WEAPON_TYPE.getOrDefault(weaponType, Collections.emptyList());
        }
        
        public static List<ItemTemplate> getItemsByArmorType(String armorType) {
            return ITEM_BY_ARMOR_TYPE.getOrDefault(armorType, Collections.emptyList());
        }
        
        public static List<ItemTemplate> getItemsByBodyPart(String bodyPart) {
            return ITEM_BY_BODYPART.getOrDefault(bodyPart, Collections.emptyList());
        }
        
        public static List<ItemTemplate> getItemsByCrystalType(String crystalType) {
            return ITEM_BY_CRYSTAL_TYPE.getOrDefault(crystalType, Collections.emptyList());
        }
        
        public static List<ItemTemplate> getStackableItems() {
            return STACKABLE_ITEMS;
        }
        
        public static List<ItemTemplate> getNonStackableItems() {
            return NON_STACKABLE_ITEMS;
        }
        
        public static List<ItemTemplate> getTradableItems() {
            return TRADABLE_ITEMS;
        }
        
        public static ItemTemplate getItemTemplate(int id) {
            return ITEM_TEMPLATES.get(id);
        }
        
        /**
         * Get items matching specified criteria
         * @param criteria Parameters for filtering items
         * @return List of matching item templates
         */
        public static List<ItemTemplate> getItemsByCriteria(TraderItemCriteria criteria) {
            List<ItemTemplate> result = new ArrayList<>(TRADABLE_ITEMS); // Start with all tradable items
            
            if (criteria.getType() != null && !criteria.getType().isEmpty()) {
                result.removeIf(item -> !item.getItemType().toString().equals(criteria.getType()));
            }
            
            if (criteria.getWeaponType() != null && !criteria.getWeaponType().isEmpty()) {
                result.removeIf(item -> !(item instanceof Weapon) || 
                    !((Weapon)item).getItemType().toString().equals(criteria.getWeaponType()));
            }
            
            if (criteria.getArmorType() != null && !criteria.getArmorType().isEmpty()) {
                result.removeIf(item -> !(item instanceof Armor) || 
                    !((Armor)item).getItemType().toString().equals(criteria.getArmorType()));
            }
            
            if (criteria.getBodyPart() != null && !criteria.getBodyPart().isEmpty()) {
                result.removeIf(item -> !String.valueOf(item.getBodyPart()).equals(criteria.getBodyPart()));
            }
            
            if (criteria.getCrystalType() != null && !criteria.getCrystalType().isEmpty()) {
                result.removeIf(item -> !item.getCrystalType().toString().equals(criteria.getCrystalType()));
            }
            
            if (criteria.isMagicWeapon() != null) {
                result.removeIf(item -> !(item instanceof Weapon) || 
                    ((Weapon)item).isMagicWeapon() != criteria.isMagicWeapon());
            }
            
            if (criteria.isStackable() != null) {
                result.removeIf(item -> item.isStackable() != criteria.isStackable());
            }
            
            if (criteria.isTradable() != null) {
                result.removeIf(item -> item.isTradeable() != criteria.isTradable());
            }
            
            if (criteria.isDropable() != null) {
                result.removeIf(item -> item.isDropable() != criteria.isDropable());
            }
            
            if (criteria.isSellable() != null) {
                result.removeIf(item -> item.isSellable() != criteria.isSellable());
            }
            
            if (criteria.isDepositable() != null) {
                result.removeIf(item -> item.isDepositable() != criteria.isDepositable());
            }
            
            return result;
        }
    }
    
    /**
     * Class to specify criteria for selecting trader items
     */
    public static class TraderItemCriteria {
        private String type;
        private String weaponType;
        private String armorType;
        private String bodyPart;
        private String crystalType;
        private Boolean isMagicWeapon;
        private Boolean isStackable;
        private Boolean isTradable;
        private Boolean isDropable;
        private Boolean isSellable;
        private Boolean isDepositable;
        private Boolean isQuestItem;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getWeaponType() { return weaponType; }
        public void setWeaponType(String weaponType) { this.weaponType = weaponType; }
        
        public String getArmorType() { return armorType; }
        public void setArmorType(String armorType) { this.armorType = armorType; }
        
        public String getBodyPart() { return bodyPart; }
        public void setBodyPart(String bodyPart) { this.bodyPart = bodyPart; }
        
        public String getCrystalType() { return crystalType; }
        public void setCrystalType(String crystalType) { this.crystalType = crystalType; }
        
        public Boolean isMagicWeapon() { return isMagicWeapon; }
        public void setMagicWeapon(Boolean isMagicWeapon) { this.isMagicWeapon = isMagicWeapon; }
        
        public Boolean isStackable() { return isStackable; }
        public void setStackable(Boolean isStackable) { this.isStackable = isStackable; }
        
        public Boolean isTradable() { return isTradable; }
        public void setTradable(Boolean isTradable) { this.isTradable = isTradable; }
        
        public Boolean isDropable() { return isDropable; }
        public void setDropable(Boolean isDropable) { this.isDropable = isDropable; }
        
        public Boolean isSellable() { return isSellable; }
        public void setSellable(Boolean isSellable) { this.isSellable = isSellable; }
        
        public Boolean isDepositable() { return isDepositable; }
        public void setDepositable(Boolean isDepositable) { this.isDepositable = isDepositable; }
        
        public Boolean isQuestItem() { return isQuestItem; }
        public void setQuestItem(Boolean isQuestItem) { this.isQuestItem = isQuestItem; }
    }
    
    /**
     * Create a trader specializing in weapons
     * @param buyMode Whether the trader should be in buy mode
     */
    private void createWeaponTrader(boolean buyMode) {
        TraderItemCriteria criteria = new TraderItemCriteria();
        criteria.setType("Weapon");
        criteria.setTradable(true);
        
        // Randomly select weapon type if desired
        if (Rnd.get(100) < 70) { // 70% chance to specialize in a specific weapon type
            List<String> weaponTypes = new ArrayList<>(Config.WEAPON_TYPE_DISTRIBUTION.keySet());
            String selectedType = weaponTypes.get(Rnd.get(weaponTypes.size()));
            criteria.setWeaponType(selectedType);
        }
        
        // Randomly decide on magic weapons
        if (Rnd.get(100) < 50) { // 50% chance to specialize in either magic or non-magic
            criteria.setMagicWeapon(Rnd.get(100) < Config.MAGIC_WEAPON_PERCENTAGE);
        }
        
        // Randomly select crystal type if desired
        if (Rnd.get(100) < 70) { // 70% chance to specialize in a specific crystal type
            List<String> crystalTypes = new ArrayList<>(Config.CRYSTAL_TYPE_DISTRIBUTION.keySet());
            String selectedType = crystalTypes.get(Rnd.get(crystalTypes.size()));
            criteria.setCrystalType(selectedType);
        }
        
        // Create the trader with these criteria
        createSpecializedTrader(criteria, buyMode, "Weapon Dealer");
    }
    
    /**
     * Create a trader specializing in armor
     * @param buyMode Whether the trader should be in buy mode
     */
    private void createArmorTrader(boolean buyMode) {
        TraderItemCriteria criteria = new TraderItemCriteria();
        criteria.setType("Armor");
        criteria.setTradable(true);
        
        // Randomly select armor type if desired
        if (Rnd.get(100) < 70) { // 70% chance to specialize in a specific armor type
            List<String> armorTypes = new ArrayList<>(Config.ARMOR_TYPE_DISTRIBUTION.keySet());
            String selectedType = armorTypes.get(Rnd.get(armorTypes.size()));
            criteria.setArmorType(selectedType);
        }
        
        // Randomly select body part if desired
        if (Rnd.get(100) < 50) { // 50% chance to specialize in a specific body part
            List<String> bodyParts = new ArrayList<>(Config.BODYPART_DISTRIBUTION.keySet());
            String selectedPart = bodyParts.get(Rnd.get(bodyParts.size()));
            criteria.setBodyPart(selectedPart);
        }
        
        // Randomly select crystal type if desired
        if (Rnd.get(100) < 70) { // 70% chance to specialize in a specific crystal type
            List<String> crystalTypes = new ArrayList<>(Config.CRYSTAL_TYPE_DISTRIBUTION.keySet());
            String selectedType = crystalTypes.get(Rnd.get(crystalTypes.size()));
            criteria.setCrystalType(selectedType);
        }
        
        // Create the trader with these criteria
        createSpecializedTrader(criteria, buyMode, "Armor Dealer");
    }
    
    /**
     * Create a trader specializing in etc items
     * @param buyMode Whether the trader should be in buy mode
     */
    private void createEtcItemTrader(boolean buyMode) {
        TraderItemCriteria criteria = new TraderItemCriteria();
        criteria.setType("EtcItem");
        criteria.setTradable(true);
        criteria.setStackable(true); // Most EtcItems are stackable
        
        // Create the trader with these criteria
        createSpecializedTrader(criteria, buyMode, "Item Vendor");
    }
    
    /**
     * Create a trader specializing in craft materials
     * @param buyMode Whether the trader should be in buy mode
     */
    private void createCraftTrader(boolean buyMode) {
        // For craft traders, we'll use the specific list of craft items
        createCraftItemsTrader(buyMode);
    }
    
    /**
     * Create a trader with craft items from the config
     * @param buyMode Whether the trader should be in buy mode
     */
    private void createCraftItemsTrader(boolean buyMode) {
        // We'll handle this differently - using the predefined list in Config
        List<ItemData> items = new ArrayList<>();
        
        // Select the appropriate map based on mode
        Map<Integer, int[]> craftItemsMap = buyMode ? Config.CRAFT_ITEMS_BUY : Config.CRAFT_ITEMS_SELL;
        
        // If the mode-specific map is empty, fall back to the general map
        if (craftItemsMap.isEmpty()) {
            LOGGER.warning("Mode-specific craft items map is empty! Using general CRAFT_ITEMS as fallback.");
            craftItemsMap = Config.CRAFT_ITEMS;
        }
        
        LOGGER.warning("Using " + (buyMode ? "CRAFT_ITEMS_BUY" : "CRAFT_ITEMS_SELL") + " map with " + craftItemsMap.size() + " items");
        
        for (Map.Entry<Integer, int[]> entry : craftItemsMap.entrySet()) {
            int itemId = entry.getKey();
            int[] priceRange = entry.getValue();
            
            ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
            if (itemTemplate != null) {
                String name = itemTemplate.getName();
                int price = Rnd.get(priceRange[0], priceRange[1]);
                
                ItemData itemData = new ItemData(itemId, price, name);
                
                // Use different min/max quantities based on mode
                if (buyMode) {
                    itemData.setMinQuantity(Config.CRAFT_ITEMS_MIN_QUANTITY_FOR_BUY);
                    itemData.setMaxQuantity(Config.CRAFT_ITEMS_MAX_QUANTITY_FOR_BUY);
                } else {
                    itemData.setMinQuantity(Config.CRAFT_ITEMS_MIN_QUANTITY_FOR_SELL);
                    itemData.setMaxQuantity(Config.CRAFT_ITEMS_MAX_QUANTITY_FOR_SELL);
                }
                
                items.add(itemData);
            }
        }
        
        if (items.isEmpty()) {
            LOGGER.warning("No craft items configured. Creating a regular trader instead.");
            createRandomTrader();
            return;
        }
        
        // Generate store title with only item names (no quantities)
        String title = generateStoreTitle(items, buyMode);
        
        // Now create a trader with these items
        createTraderWithItems(items, buyMode, "Materials Trader", title);
    }
    
    /**
     * Create a specialized trader based on criteria
     * @param criteria The criteria for item selection
     * @param buyMode Whether the trader should be in buy mode
     * @param specialization The specialization type of the trader
     */
    private void createSpecializedTrader(TraderItemCriteria criteria, boolean buyMode, String specialization) {
        // Get items matching criteria
        List<ItemTemplate> matchingItems = XMLItemManager.getItemsByCriteria(criteria);
        
        if (matchingItems.isEmpty()) {
            LOGGER.warning("No items match the criteria for " + specialization + ". Creating a regular trader instead.");
            createRandomTrader();
            return;
        }
        
        // Convert to our ItemData format
        List<ItemData> items = new ArrayList<>();
        
        // Shuffle and take a random subset
        Collections.shuffle(matchingItems);
        int itemCount = Rnd.get(Config.MIN_SELL_ITEM_TYPES, Math.min(Config.MAX_SELL_ITEM_TYPES, matchingItems.size()));
        
        for (int i = 0; i < itemCount && i < matchingItems.size(); i++) {
            ItemTemplate itemTemplate = matchingItems.get(i);
            
            // Calculate price based on the template's default price and item grade
            int basePrice = itemTemplate.getReferencePrice();
            int price;
            
            // Check for exception prices
            if (buyMode && Config.EXCEPTION_ITEMS_BUY_PRICE.containsKey(itemTemplate.getId())) {
                int[] range = Config.EXCEPTION_ITEMS_BUY_PRICE.get(itemTemplate.getId());
                price = Rnd.get(range[0], range[1]);
            } else if (!buyMode && Config.EXCEPTION_ITEMS_SELL_PRICE.containsKey(itemTemplate.getId())) {
                int[] range = Config.EXCEPTION_ITEMS_SELL_PRICE.get(itemTemplate.getId());
                price = Rnd.get(range[0], range[1]);
            } else {
                // Apply price multiplier based on crystal type (item grade)
                int multiplier = getPriceMultiplierByGrade(itemTemplate.getCrystalType().toString(), buyMode);
                price = (int) (basePrice * (multiplier / 100.0));
                
                // Add some randomization (±10%)
                price = (int) (price * (0.9 + (Rnd.nextDouble() * 0.2)));
            }
            
            // Ensure minimum price
            price = Math.max(price, 1);
            
            ItemData itemData = new ItemData(itemTemplate.getId(), price, itemTemplate.getName());
            
            // Set quantity based on stackable status
            if (itemTemplate.isStackable()) {
                if (buyMode) {
                    itemData.setMinQuantity(Config.MIN_BUY_ITEM_COUNT);
                    itemData.setMaxQuantity(Config.MAX_BUY_ITEM_COUNT);
                } else {
                    itemData.setMinQuantity(Config.MIN_SELL_ITEM_COUNT);
                    itemData.setMaxQuantity(Config.MAX_SELL_ITEM_COUNT);
                }
            } else {
                // Non-stackable items like weapons and armor should be quantity 1
                itemData.setMinQuantity(1);
                itemData.setMaxQuantity(1);
            }
            
            items.add(itemData);
        }
        
        // Generate title for the trader
        String title = generateStoreTitle(items, buyMode);
        
        // Pass the generated title as the fourth parameter
        createTraderWithItems(items, buyMode, specialization, title);
    }
    
    /**
     * Get price multiplier based on item grade
     * @param grade The crystal type (grade) of the item
     * @param buyMode Whether this is for buy mode
     * @return The price multiplier percentage
     */
    private int getPriceMultiplierByGrade(String grade, boolean buyMode) {
        if (buyMode) {
            // Buy mode multipliers (lower price = better deal for buyers)
            switch (grade) {
                case "NONE":
                    return Config.BUY_PRICE_MULTIPLIER_NO_GRADE;
                case "D":
                    return Config.BUY_PRICE_MULTIPLIER_D_GRADE;
                case "C":
                    return Config.BUY_PRICE_MULTIPLIER_C_GRADE;
                case "B":
                    return Config.BUY_PRICE_MULTIPLIER_B_GRADE;
                case "A":
                    return Config.BUY_PRICE_MULTIPLIER_A_GRADE;
                case "S":
                    return Config.BUY_PRICE_MULTIPLIER_S_GRADE;
                case "S80":
                    return Config.BUY_PRICE_MULTIPLIER_S80_GRADE;
                case "S84":
                    return Config.BUY_PRICE_MULTIPLIER_S84_GRADE;
                default:
                    return Config.BUY_PRICE_MULTIPLIER;
            }
        }
        
        // Sell mode multipliers (higher price = better profit for sellers)
        switch (grade) {
            case "NONE":
                return Config.SELL_PRICE_MULTIPLIER_NO_GRADE;
            case "D":
                return Config.SELL_PRICE_MULTIPLIER_D_GRADE;
            case "C":
                return Config.SELL_PRICE_MULTIPLIER_C_GRADE;
            case "B":
                return Config.SELL_PRICE_MULTIPLIER_B_GRADE;
            case "A":
                return Config.SELL_PRICE_MULTIPLIER_A_GRADE;
            case "S":
                return Config.SELL_PRICE_MULTIPLIER_S_GRADE;
            case "S80":
                return Config.SELL_PRICE_MULTIPLIER_S80_GRADE;
            case "S84":
                return Config.SELL_PRICE_MULTIPLIER_S84_GRADE;
            default:
                return Config.SELL_PRICE_MULTIPLIER;
        }
    }
    
    /**
     * Create a trader with a specific list of items
     * @param items List of items to sell/buy
     * @param buyMode Whether the trader should be in buy mode
     * @param specialization The specialization type of the trader
     * @param title The store title to use
     */
    private void createTraderWithItems(List<ItemData> items, boolean buyMode, String specialization, String title) {
        try {
            // Generate a random name
            final boolean isFemale = Rnd.nextBoolean();
            String name;
            if (!FAKE_PLAYER_NAMES.isEmpty()) {
                name = FAKE_PLAYER_NAMES.get(Rnd.get(FAKE_PLAYER_NAMES.size()));
                
                // Make sure this name isn't already used
                boolean nameExists;
                int attempts = 0;
                do {
                    nameExists = false;
                    for (PlayerTrader existing : ACTIVE_TRADERS.values()) {
                        if (existing.getName().equals(name)) {
                            nameExists = true;
                            name = FAKE_PLAYER_NAMES.get(Rnd.get(FAKE_PLAYER_NAMES.size()));
                            break;
                        }
                    }
                    attempts++;
                } while (nameExists && attempts < 10);
            } else {
                final String[] namePool = isFemale ? FEMALE_NAMES : MALE_NAMES;
                name = namePool[Rnd.get(namePool.length)] + " " + LAST_NAMES[Rnd.get(LAST_NAMES.length)];
            }
            
            // Generate a random location based on trader type (buy/sell)
            final Location location = getRandomTraderLocation(buyMode);
            
            // Generate random class and race
            final Race race = Race.values()[Rnd.get(Race.values().length)];
            
            // Select a class ID for the race
            int classId;
            switch (race) {
                case HUMAN:
                    classId = isFemale ? 0x01 : 0x00; // Human Fighter
                    break;
                case ELF:
                    classId = isFemale ? 0x12 : 0x11; // Elven Fighter
                    break;
                case DARK_ELF:
                    classId = isFemale ? 0x26 : 0x25; // Dark Elf Fighter
                    break;
                case ORC:
                    classId = isFemale ? 0x31 : 0x30; // Orc Fighter
                    break;
                case DWARF:
                    classId = isFemale ? 0x39 : 0x38; // Dwarven Fighter
                    break;
                case KAMAEL:
                    classId = isFemale ? 0x7b : 0x7A; // Kamael Soldier
                    break;
                default:
                    classId = 0x00; // Default to human fighter
                    break;
            }
            
            // Create the trader
            final int objectId = getNewObjectId();
            final TradeList tradeList = new TradeList(null);
            tradeList.setTitle(title);
            
            final PlayerTrader trader = new PlayerTrader(objectId, name, tradeList, classId, race, isFemale, location, title, buyMode);
            
            // IMPORTANT: Set the selected items BEFORE spawning
            trader.setSelectedItems(items);
            
            // Create and spawn the fake player
            spawnTrader(trader);
            
            ACTIVE_TRADERS.put(objectId, trader);
        } catch (Exception e) {
            LOGGER.warning("Error creating specialized trader: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create a crafter specializing in weapons
     */
    private void createWeaponCrafter() {
        List<RecipeData> weaponRecipes = getRecipesByType(0);
        if (weaponRecipes.isEmpty()) {
            LOGGER.warning("No weapon recipes found. Creating a random crafter instead.");
            createRandomCrafter();
            return;
        }
        
        createCrafterWithRecipes(weaponRecipes, "Weapon Crafter");
    }
    
    /**
     * Create a crafter specializing in armor
     */
    private void createArmorCrafter() {
        List<RecipeData> armorRecipes = getRecipesByType(1);
        if (armorRecipes.isEmpty()) {
            LOGGER.warning("No armor recipes found. Creating a random crafter instead.");
            createRandomCrafter();
            return;
        }
        
        createCrafterWithRecipes(armorRecipes, "Armor Crafter");
    }
    
    /**
     * Create a crafter specializing in etc items
     */
    private void createEtcItemCrafter() {
        List<RecipeData> etcRecipes = getRecipesByType(2);
        if (etcRecipes.isEmpty()) {
            LOGGER.warning("No etc item recipes found. Creating a random crafter instead.");
            createRandomCrafter();
            return;
        }
        
        createCrafterWithRecipes(etcRecipes, "Item Crafter");
    }
    
    /**
     * Create a crafter specializing in soul/spirit shots
     */
    private void createSoulSpiritShotCrafter() {
        List<RecipeData> shotRecipes = new ArrayList<>();
        
        // Get all soul/spirit shot recipes
        for (RecipeData recipe : RECIPE_DATABASE) {
            if (recipe.isSoulOrSpiritShot()) {
                shotRecipes.add(recipe);
            }
        }
        
        if (shotRecipes.isEmpty()) {
            LOGGER.warning("No soul/spirit shot recipes found. Creating a random crafter instead.");
            createRandomCrafter();
            return;
        }
        
        createCrafterWithRecipes(shotRecipes, "Shot Crafter");
    }
    
    /**
     * Create a random crafter with random recipes
     */
    private void createRandomCrafter() {
        if (RECIPE_DATABASE.isEmpty()) {
            LOGGER.warning("Recipe database is empty. Cannot create random crafter.");
            return;
        }
        
        // Select random recipes
        Collections.shuffle(RECIPE_DATABASE);
        int recipeCount = Math.min(20, RECIPE_DATABASE.size()); // Maximum 20 recipes per crafter
        List<RecipeData> selectedRecipes = RECIPE_DATABASE.subList(0, recipeCount);
        
        createCrafterWithRecipes(selectedRecipes, "Master Crafter");
    }
    
    /**
     * Get recipes by type
     * @param type 0 = weapon, 1 = armor, 2 = etc
     * @return List of recipes of the specified type
     */
    private List<RecipeData> getRecipesByType(int type) {
        List<RecipeData> result = new ArrayList<>();
        
        for (RecipeData recipe : RECIPE_DATABASE) {
            if (recipe.getItemType() == type) {
                result.add(recipe);
            }
        }
        
        return result;
    }
    
    /**
     * Create a crafter with a specific list of recipes
     * @param availableRecipes List of available recipes to choose from
     * @param specialization The specialization type of the crafter
     */
    private void createCrafterWithRecipes(List<RecipeData> availableRecipes, String specialization) {
        try {
            // Shuffle available recipes
            Collections.shuffle(availableRecipes);
            
            // Select random recipes (up to 20, which is the max for private manufacture)
            int recipeCount = Math.min(20, availableRecipes.size());
            List<RecipeData> selectedRecipes = new ArrayList<>(availableRecipes.subList(0, recipeCount));
            
            // Generate a random name
            final boolean isFemale = Rnd.nextBoolean();
            String name;
            if (!FAKE_PLAYER_NAMES.isEmpty()) {
                name = FAKE_PLAYER_NAMES.get(Rnd.get(FAKE_PLAYER_NAMES.size()));
                
                // Make sure this name isn't already used
                boolean nameExists;
                int attempts = 0;
                do {
                    nameExists = false;
                    for (PlayerTrader existing : ACTIVE_TRADERS.values()) {
                        if (existing.getName().equals(name)) {
                            nameExists = true;
                            name = FAKE_PLAYER_NAMES.get(Rnd.get(FAKE_PLAYER_NAMES.size()));
                            break;
                        }
                    }
                    attempts++;
                } while (nameExists && attempts < 10);
            } else {
                final String[] namePool = isFemale ? FEMALE_NAMES : MALE_NAMES;
                name = namePool[Rnd.get(namePool.length)] + " " + LAST_NAMES[Rnd.get(LAST_NAMES.length)];
            }
            
            // Generate a random location
            final Location location = getRandomTraderLocation(false); // Crafters will use the sell zone
            
            // Generate random class (always Dwarf for crafters)
            final Race race = Race.DWARF;
            
            // Select a class ID for Dwarf
            int classId = isFemale ? 0x39 : 0x38; // Dwarven Fighter
            
            // Generate store title based on specialization and recipes
            StringBuilder titleBuilder = new StringBuilder(specialization);
            titleBuilder.append(": ");
            
            // Add some recipe names
            int count = 0;
            for (RecipeData recipe : selectedRecipes) {
                if (count >= 3) {
                    break; // Only show 3 recipes in title
                }
                
                if (count > 0) {
                    titleBuilder.append(", ");
                }
                
                // Get the item name
                ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(recipe.getItemId());
                String itemName = (itemTemplate != null) ? itemTemplate.getName() : "Unknown";
                
                // Add to title
                if (itemName.length() > 12) {
                    itemName = itemName.substring(0, 12);
                }
                titleBuilder.append(itemName);
                count++;
            }
            
            if (selectedRecipes.size() > 3) {
                titleBuilder.append("...");
            }
            
            // Ensure title is not longer than the configured length
            String title = titleBuilder.toString();
            if (title.length() > Config.MAX_STORE_TITLE_LENGTH) {
                title = title.substring(0, Config.MAX_STORE_TITLE_LENGTH - 3) + "...";
            }
            
            // Create the trader
            final int objectId = getNewObjectId();
            final TradeList tradeList = new TradeList(null);
            tradeList.setTitle(title);
            
            final PlayerTrader trader = new PlayerTrader(objectId, name, tradeList, classId, race, isFemale, location, title, false, true);
            trader.setSelectedRecipes(selectedRecipes);
            
            // Create and spawn the fake player
            spawnCrafter(trader);
            
            ACTIVE_TRADERS.put(objectId, trader);
        } catch (Exception e) {
            LOGGER.warning("Error creating crafter: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Spawn a crafter in the game world
     * @param trader The trader with crafter mode
     */
    private void spawnCrafter(PlayerTrader trader) {
        try {
            // Add debugging information
            LOGGER.warning("==============================");
            LOGGER.warning("SPAWNING CRAFTER: " + trader.getName());
            LOGGER.warning("ObjectID: " + trader.getObjectId());
            LOGGER.warning("Title: " + trader.getTitle() + " (Length: " + trader.getTitle().length() + ")");
            LOGGER.warning("Race: " + trader.getRace());
            LOGGER.warning("Class ID: " + trader.getClassId());
            LOGGER.warning("Location: " + trader.getLocation().getX() + ", " + trader.getLocation().getY() + ", " + trader.getLocation().getZ());
            LOGGER.warning("Selected recipes: " + trader.getSelectedRecipes().size());
            LOGGER.warning("==============================");
            
            // Create a fake player using the proper constructors
            PlayerTemplate playerTemplate = PlayerTemplateData.getInstance().getTemplate(trader.getClassId());
            PlayerAppearance appearance = new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, trader.isFemale());
            
            // Create the player instance
            final Player fakePc;
            try {
                java.lang.reflect.Constructor<Player> constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class);
                constructor.setAccessible(true);
                fakePc = constructor.newInstance(trader.getObjectId(), playerTemplate, trader.getName(), appearance);
            } catch (Exception e) {
                LOGGER.warning("Failed to create player via reflection: " + e.getMessage());
                return;
            }
            
            // Set player properties
            fakePc.setName(trader.getName());
            fakePc.setTitle(""); // No title
            fakePc.setAccessLevel(0);
            fakePc.setClassId(trader.getClassId());
            fakePc.setBaseClass(trader.getClassId());
            fakePc.setHeading(trader.getLocation().getHeading());
            fakePc.setXYZ(trader.getLocation().getX(), trader.getLocation().getY(), trader.getLocation().getZ());
            
            // CRITICAL: First check on title length before database insertion
            String safeTitle = trader.getTitle();
            if (safeTitle.length() > 16) {
                safeTitle = safeTitle.substring(0, 13) + "...";
                LOGGER.warning("Title truncated for database insertion: " + safeTitle);
            }
            
            // 1. CREATE CHARACTER FIRST - Create character entry in DB
            try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO characters (account_name, charId, char_name, level, maxHp, curHp, maxCp, curCp, maxMp, curMp, face, hairStyle, hairColor, sex, heading, x, y, z, exp, sp, race, classid, base_class, title, title_color, accesslevel, online, lastAccess, cancraft) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                String accountName = "fakeacc" + String.format("%07d", trader.getObjectId());
                ps.setString(1, accountName); // account_name
                ps.setInt(2, fakePc.getObjectId()); // charId
                ps.setString(3, trader.getName()); // char_name
                ps.setInt(4, 85); // level (set to 85 for crafters)
                ps.setInt(5, 8000); // maxHp
                ps.setInt(6, 8000); // curHp
                ps.setInt(7, 4000); // maxCp
                ps.setInt(8, 4000); // curCp
                ps.setInt(9, 1800); // maxMp
                ps.setInt(10, 1800); // curMp
                ps.setInt(11, 0); // face
                ps.setInt(12, 0); // hairStyle
                ps.setInt(13, 0); // hairColor
                ps.setInt(14, trader.isFemale() ? 1 : 0); // sex
                ps.setInt(15, trader.getLocation().getHeading()); // heading
                ps.setInt(16, trader.getLocation().getX()); // x
                ps.setInt(17, trader.getLocation().getY()); // y
                ps.setInt(18, trader.getLocation().getZ()); // z
                ps.setLong(19, 10000000000L); // exp
                ps.setLong(20, 1000000000); // sp
                ps.setInt(21, trader.getRace().ordinal()); // race
                ps.setInt(22, trader.getClassId()); // classid
                ps.setInt(23, trader.getClassId()); // base_class
                ps.setString(24, safeTitle); // title (using safe truncated version)
                ps.setInt(25, 0xECF9A2); // title_color
                ps.setInt(26, 0); // accesslevel
                ps.setInt(27, 0); // online
                ps.setLong(28, System.currentTimeMillis()); // lastAccess
                ps.setInt(29, 1); // cancraft - set to 1 for crafters
                
                int characterResult = ps.executeUpdate();
                LOGGER.warning("Characters table insert result: " + characterResult + " rows affected");
            } catch (SQLException e) {
                LOGGER.warning("Error creating character entry: " + e.getMessage());
                e.printStackTrace();
                return; // Don't proceed if character creation failed
            }
            
            // ...existing account creation code...
            
            // Create account entry
            try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO accounts (login, password, email, created_time, lastactive, accessLevel, lastIP, lastServer, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                String accountName = "fakeacc" + String.format("%07d", trader.getObjectId());
                ps.setString(1, accountName);
                ps.setString(2, "7XDFfXVk6ZTn1fb9aWfOqLNH77w="); // Standard encrypted password
                ps.setString(3, accountName + "@gmail.com");
                ps.setString(4, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                ps.setLong(5, System.currentTimeMillis());
                ps.setInt(6, 0); // accessLevel
                ps.setString(7, "127.0.0.1");
                ps.setInt(8, 1); // lastServer
                ps.setInt(9, 1); // is_active
                
                int accountResult = ps.executeUpdate();
                LOGGER.warning("Account created for crafter " + trader.getName() + ": " + accountName + ", Result: " + accountResult);
            } catch (SQLException e) {
                LOGGER.warning("Error creating account entry: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Add to world
            World.getInstance().addObject(fakePc);
            fakePc.spawnMe();
            LOGGER.warning("Crafter " + trader.getName() + " added to world successfully");
            
            // MANUFACTURING SETUP - Now set up manufacturing in DB
            try (Connection con = DatabaseFactory.getConnection()) {
                // Insert recipes to character_recipebook
                for (RecipeData recipe : trader.getSelectedRecipes()) {
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_recipebook (charId, id, classIndex, type) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, fakePc.getObjectId()); // charId
                        ps.setInt(2, recipe.getRecipeId()); // id
                        ps.setInt(3, 0); // classIndex
                        ps.setInt(4, 1); // type (1 = dwarven)
                        
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        LOGGER.warning("Error inserting recipe " + recipe.getRecipeId() + ": " + e.getMessage());
                    }
                }
                
                // Set up character_offline_trade for manufacturing
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_offline_trade (charId, time, type, title) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, fakePc.getObjectId()); // charId
                    ps.setLong(2, System.currentTimeMillis()); // time
                    ps.setInt(3, 5); // type (5 for manufacture)
                    ps.setString(4, trader.getTitle()); // title
                    
                    int tradeResult = ps.executeUpdate();
                    LOGGER.warning("character_offline_trade insert result: " + tradeResult);
                }
                
                // Insert recipes into character_offline_trade_items
                int recipesAdded = 0;
                for (RecipeData recipe : trader.getSelectedRecipes()) {
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_offline_trade_items (charId, item, count, price) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, fakePc.getObjectId()); // charId
                        ps.setInt(2, recipe.getRecipeId()); // item (recipe ID)
                        ps.setLong(3, 0); // count (always 0 for manufacturing)
                        ps.setLong(4, recipe.getPrice()); // price
                        
                        int result = ps.executeUpdate();
                        if (result > 0) {
                            recipesAdded++;
                        }
                    } catch (SQLException e) {
                        LOGGER.warning("Error inserting recipe " + recipe.getRecipeId() + " to trade items: " + e.getMessage());
                    }
                }
                
                LOGGER.warning("Added " + recipesAdded + " recipes to trade items for crafter " + trader.getName());
            } catch (SQLException e) {
                LOGGER.warning("Database error in crafter setup: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Set private store type to MANUFACTURE (5)
            fakePc.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
            fakePc.getSellList().setTitle(trader.getTitle());
            
            // Make sure they sit down
            fakePc.sitDown();
            LOGGER.warning("Crafter " + trader.getName() + " is now sitting");
            
            // Store reference to the fake player
            trader.setFakePc(fakePc);
            LOGGER.warning("Crafter " + trader.getName() + " successfully spawned");
        } catch (Exception e) {
            LOGGER.warning("Failed to spawn crafter: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load trader config from XML file
     */
    private void loadTraderConfig() {
        // No XML config loading - using embedded config values directly
        LOGGER.info("Using default trader configuration values");
    }

    /**
     * Class to store item data from the game XML files
     */
    private static class GameItemData {
        public int id;
        public String name;
        public int price;
        public boolean isTradable;
        public boolean isDropable;
        public boolean isSellable;
        public boolean isDepositable;
        public boolean isStackable;
        public boolean isQuestItem;
        public String itemType;
        public String weaponType;
        public String armorType;
        public String bodyPart;
        public String crystalType;
        public boolean isMagicWeapon;
        
        public GameItemData(int id, String name) {
            this.id = id;
            this.name = name;
            this.isTradable = true;
            this.isDropable = true;
            this.isSellable = true;
            this.isDepositable = true;
            this.isStackable = false;
            this.isQuestItem = false;
        }
    }
    
    /**
     * Load items from all game XML files
     * @return List of GameItemData objects
     */
    private List<GameItemData> loadGameItems() {
        List<GameItemData> result = new ArrayList<>();
        File gameItemsDir = new File(org.l2jmobius.Config.DATAPACK_ROOT, GAME_ITEMS_PATH);
        
        if (!gameItemsDir.exists() || !gameItemsDir.isDirectory()) {
            LOGGER.warning("Game items directory not found: " + gameItemsDir.getAbsolutePath());
            return result;
        }
        
        LOGGER.info("Loading items from game XML files in " + gameItemsDir.getAbsolutePath());
        
        // Get all XML files in the items directory
        File[] xmlFiles = gameItemsDir.listFiles((_, name) -> name.toLowerCase().endsWith(".xml"));
        
        if (xmlFiles == null || xmlFiles.length == 0) {
            LOGGER.warning("No XML files found in game items directory");
            return result;
        }
        
        LOGGER.info("Found " + xmlFiles.length + " XML files to process");
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            for (File xmlFile : xmlFiles) {
                try {
                    Document doc = builder.parse(xmlFile);
                    doc.getDocumentElement().normalize();
                    
                    NodeList itemNodes = doc.getElementsByTagName("item");
                    
                    for (int i = 0; i < itemNodes.getLength(); i++) {
                        Element itemElement = (Element) itemNodes.item(i);
                        
                        int id = Integer.parseInt(itemElement.getAttribute("id"));
                        String name = itemElement.getAttribute("name");
                        String type = itemElement.getAttribute("type");
                        
                        GameItemData itemData = new GameItemData(id, name);
                        itemData.itemType = type;
                        
                        // Process item properties
                        NodeList setNodes = itemElement.getElementsByTagName("set");
                        for (int j = 0; j < setNodes.getLength(); j++) {
                            Element setElement = (Element) setNodes.item(j);
                            String propertyName = setElement.getAttribute("name");
                            String propertyValue = setElement.getAttribute("val");
                            
                            switch (propertyName) {
                                case "price":
                                    try {
                                        itemData.price = Integer.parseInt(propertyValue);
                                    } catch (NumberFormatException e) {
                                        itemData.price = 0;
                                    }
                                    break;
                                case "is_tradable":
                                    itemData.isTradable = Boolean.parseBoolean(propertyValue);
                                    break;
                                case "is_dropable":
                                    itemData.isDropable = Boolean.parseBoolean(propertyValue);
                                    break;
                                case "is_sellable":
                                    itemData.isSellable = Boolean.parseBoolean(propertyValue);
                                    break;
                                case "is_depositable":
                                    itemData.isDepositable = Boolean.parseBoolean(propertyValue);
                                    break;
                                case "is_stackable":
                                    itemData.isStackable = Boolean.parseBoolean(propertyValue);
                                    break;
                                case "is_questitem":
                                    itemData.isQuestItem = Boolean.parseBoolean(propertyValue);
                                    break;
                                case "weapon_type":
                                    itemData.weaponType = propertyValue;
                                    break;
                                case "armor_type":
                                    itemData.armorType = propertyValue;
                                    break;
                                case "bodypart":
                                    itemData.bodyPart = propertyValue;
                                    break;
                                case "crystal_type":
                                    itemData.crystalType = propertyValue;
                                    break;
                                case "is_magic_weapon":
                                    itemData.isMagicWeapon = Boolean.parseBoolean(propertyValue);
                                    break;
                            }
                        }
                        
                        // Add the item to our list
                        result.add(itemData);
                    }
                    
                    LOGGER.fine("Processed " + itemNodes.getLength() + " items from " + xmlFile.getName());
                    
                } catch (Exception e) {
                    LOGGER.warning("Error processing game XML file " + xmlFile.getName() + ": " + e.getMessage());
                }
            }
            
            LOGGER.info("Loaded " + result.size() + " items from game XML files");
            
        } catch (Exception e) {
            LOGGER.warning("Error setting up XML parser: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    // ...existing code...

    /**
     * Generate a store title based on mode
     * @param selectedItems List of items for the trader
     * @param buyMode Whether this is a buy mode trader
     * @return The formatted store title
     */
    private String generateStoreTitle(List<ItemData> selectedItems, boolean buyMode)
    {
        final StringBuilder titleBuilder = new StringBuilder();
        
        // Add item names only (no quantities)
        final int itemsToShow = Math.min(5, selectedItems.size());
        int count = 0;
        
        for (ItemData item : selectedItems) {
            if (count >= itemsToShow) {
                break;
            }
            
            if (count > 0) {
                titleBuilder.append(", ");
            }
            
            // Format: ItemName only (no quantity)
            String itemName = item.getName();
            if (itemName.length() > 12) {
                itemName = itemName.substring(0, 12);
            }
            
            titleBuilder.append(itemName);
            count++;
        }
        
        if (selectedItems.size() > itemsToShow) {
            titleBuilder.append("...");
        }
        
        // Ensure title is not longer than the configured length
        String title = titleBuilder.toString();
        if (title.length() > Config.MAX_STORE_TITLE_LENGTH)
        {
            title = title.substring(0, Config.MAX_STORE_TITLE_LENGTH - 3) + "...";
        }
        
        return title;
    }

    /**
     * Generate a trade announcement message for a trader
     * @param trader The trader to generate an announcement for
     * @return The announcement message
     */
    private String generateTradeAnnouncement(PlayerTrader trader)
    {
        StringBuilder sb = new StringBuilder();
        
        if (trader.isCraftMode()) {
            // Format for crafters: Crafting ItemName1, ItemName2, ItemName3...
            sb.append("Crafting ");
            
            int itemCount = 0;
            for (RecipeData recipe : trader.getSelectedRecipes()) {
                if (itemCount > 0) {
                    sb.append(", ");
                }
                
                if (itemCount >= 5) {
                    sb.append("...");
                    break;
                }
                
                // Get the item name
                ItemTemplate itemTemplate = org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(recipe.getItemId());
                sb.append(itemTemplate != null ? itemTemplate.getName() : "Unknown");
                itemCount++;
            }
        } else {
            // Existing format for buyers/sellers
            int itemCount = 0;
            for (ItemData item : trader.getSelectedItems()) {
                if (itemCount > 0) {
                    sb.append(", ");
                }
                
                if (itemCount >= 5) {
                    sb.append("...");
                    break;
                }
                
                sb.append(item.getName());
                itemCount++;
            }
        }
        
        // No name at the end
        String announcement = sb.toString();
        if (announcement.length() > Config.MAX_TRADE_ANNOUNCEMENT_LENGTH) {
            announcement = announcement.substring(0, Config.MAX_TRADE_ANNOUNCEMENT_LENGTH - 3) + "...";
        }
        
        return announcement;
    }

    /**
     * Generate a random location based on trader type
     * @param buyMode true for buy mode, false for sell mode
     * @return A valid location for the trader
     */
    private Location getRandomTraderLocation(boolean buyMode) {
        final int minX = buyMode ? BUY_MIN_X : SELL_MIN_X;
        final int maxX = buyMode ? BUY_MAX_X : SELL_MAX_X;
        final int minY = buyMode ? BUY_MIN_Y : SELL_MIN_Y;
        final int maxY = buyMode ? BUY_MAX_Y : SELL_MAX_Y;
        
        int x, y;
        int attempts = 0;
        
        // Try to find a location not in the exclusion zone
        do {
            x = Rnd.get(minX, maxX);
            y = Rnd.get(minY, maxY);
            attempts++;
            
            // Avoid infinite loop
            if (attempts > 50) {
                LOGGER.warning("Could not find non-exclusion zone location after 50 attempts. Using current coordinates.");
                break;
            }
        } while (isInExclusionZone(x, y));
        
        return new Location(x, y, Z_COORD, Rnd.get(65536));
    }
    
    /**
     * Check if coordinates are in the exclusion zone
     * @param x X coordinate
     * @param y Y coordinate
     * @return true if in exclusion zone, false otherwise
     */
    private boolean isInExclusionZone(int x, int y) {
        return x >= EXCLUSION_MIN_X && x <= EXCLUSION_MAX_X && 
               y >= EXCLUSION_MIN_Y && y <= EXCLUSION_MAX_Y;
    }

}