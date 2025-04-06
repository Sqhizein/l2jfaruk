package instances.CustomInstance;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.sql.Timestamp;


import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.Point2D;
import org.l2jmobius.gameserver.instancemanager.InstanceManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.instancezone.Instance;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.model.Party;
import java.util.logging.Logger;


import instances.AbstractInstance;

public class CustomInstance extends AbstractInstance
{
	
	public static class SpawnTerritory {
		private final List<Point2D> nodes = new ArrayList<>();
		private final int minZ;
		private final int maxZ;
		private final int count;
	
		public SpawnTerritory(int minZ, int maxZ, int count) {
			this.minZ = minZ;
			this.maxZ = maxZ;
			this.count = count;
		}
	
		public void addNode(int x, int y) {
			nodes.add(new Point2D(x, y));
		}
	}
	
	private static final Set<Integer> SPECIAL_ITEM_IDS = Set.of(8600, 8601, 8602, 8603, 8604, 8605, 8606, 8607, 8608, 8609, 8610, 8611, 8612, 8613, 8614, 8952, 8953, 10655, 10656, 10657, 13028, 14824, 14825, 14826, 14827
	
	);
	private static final Logger LOGGER = Logger.getLogger(CustomInstance.class.getName());
	private static final List<Integer> MONSTER_IDS = new ArrayList<>();
	private static final int MAX_PARTY_SIZE = 9;
	
	// Constants
	private static final int[] TEMPLATE_IDS =
	{
		300161, // Level 20-27
		300162, // Level 28-35
		300163, // Level 36-43
		300164, // Level 44-51
		300165, // Level 52-59
		300166, // Level 60-67
		300167, // Level 68-74
		300168, // Level 75-79
		300169, // Level 80-81
		300170, // Level 82-83
		300171 // Level 84-86
	};
	
	private static final int[][] LEVEL_RANGES =
	{
		{
			20,
			27
		}, // Index 0
		{
			28,
			35
		}, // Index 1
		{
			36,
			43
		}, // Index 2
		{
			44,
			51
		}, // Index 3
		{
			52,
			59
		}, // Index 4
		{
			60,
			67
		}, // Index 5
		{
			68,
			74
		}, // Index 6
		{
			75,
			79
		}, // Index 7
		{
			80,
			81
		}, // Index 8
		{
			82,
			83
		}, // Index 9
		{
			84,
			86
		} // Index 10
	};
	
	private static final int[] DURATION =
	{
		1200
	}; // Saniye cinsinden süre
	
	private static final Location[] TELEPORTS =
	{
		new Location(6098, -16427, -3711)
	};
	
	private static final Location EXIT_LOC = new Location(83483, 148593, -3408);
	private static final int START_NPC = 37002;
	
	// Inner Classes
	// CustomWorld class'ına drop rate multiplier ekleyelim
	protected class CustomWorld extends InstanceWorld {
		public int index;
		public List<Npc> monsters = new ArrayList<>();
		
		// Static final rate'ler
		public static final double INSTANCE_DROP_CHANCE_RATE = 1.50;
		public static final double INSTANCE_DROP_AMOUNT_RATE = 1.50;
		public static final double INSTANCE_SPOIL_CHANCE_RATE = 1.50;
		public static final double INSTANCE_SPOIL_AMOUNT_RATE = 1.50;
	}

	private static class InstanceMonster
	{
		int npcId;
		String name;
		int level;
		
		public InstanceMonster(int npcId, String name, int level)
		{
			this.npcId = npcId;
			this.name = name;
			this.level = level;
		}
	}
	
	// Constructor
	public CustomInstance()
	{
		LOGGER.info("(-------------------------------------------------=[ Monster Realm Instance ]");
		addStartNpc(START_NPC);
		addFirstTalkId(START_NPC);
		addTalkId(START_NPC);
		
		// Database'den monster ID'lerini yükle
		loadMonstersFromDB();
		
		// Yüklenen monster ID'lerini kaydet
		for (int monsterId : MONSTER_IDS)
		{
			addKillId(monsterId);
		}
		
	}
	
	@Override
	public String onFirstTalk(Npc npc, Player player) {
		if (npc.getId() == START_NPC) {
			LOGGER.info("CustomInstance: onFirstTalk triggered for NPC: " + npc.getId());
			
			String htmlContent;
			if (npc.getInstanceId() > 0) {
				htmlContent = getHtm(player, "37002-exit.htm");
			} else {
				htmlContent = getHtm(player, "37002.htm");
				// Kalan giriş bilgisini ekle
				htmlContent = htmlContent.replace("%remaining_entries%", getRemainingEntriesInfo(player));
			}
			
			return htmlContent;
		}
		return super.onFirstTalk(npc, player);
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		if (npc.getId() == START_NPC)
		{
			// Instance içinde mi kontrol et
			if (npc.getInstanceId() > 0)
			{
				return "37002-exit.htm";
			}
			return "37002.htm";
		}
		return super.onTalk(npc, player);
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		// Exit event kontrolü
		if ("exit_instance".equals(event)) {
			LOGGER.info("CustomInstance: Processing exit_instance event for player " + player.getName());
			
			final Party party = player.getParty();
			if (party != null) {
				if (!party.isLeader(player)) {
					player.sendPacket(SystemMessageId.ONLY_A_PARTY_LEADER_CAN_MAKE_THE_REQUEST_TO_ENTER);
					return null;
				}
				
				final InstanceWorld world = InstanceManager.getInstance().getWorld(player);
				if (world != null && world instanceof CustomWorld) {
					CustomWorld customWorld = (CustomWorld) world; // Explicit casting
					// Önce tüm monsterların rate'lerini temizle ve unspawn et
					for (Npc monster : customWorld.monsters) {
						if (monster != null) {
							// Rate'leri temizle
							monster.getTemplate().clearInstanceRates();
							// Eğer ölmediyse sil
							if (!monster.isDead()) {
								monster.deleteMe();
							}
						}
					}
					customWorld.monsters.clear(); // Liste temizle
					
					// Tüm party üyelerini çıkar
					for (Player member : party.getMembers()) {
						if (member.getInstanceId() == world.getInstanceId()) {
							teleportPlayer(member, EXIT_LOC, 0);
							world.removeAllowed(member);
						}
					}
					// Instance'ı temizle
					InstanceManager.getInstance().destroyInstance(world.getInstanceId());
					world.destroy();
					LOGGER.info("CustomInstance: Instance destroyed for party. Instance ID: " + world.getInstanceId());
				}
			} else {
				final InstanceWorld world = InstanceManager.getInstance().getWorld(player);
				if (world != null && world instanceof CustomWorld) {
					CustomWorld customWorld = (CustomWorld) world; // Explicit casting
					// Önce tüm monsterların rate'lerini temizle ve unspawn et
					for (Npc monster : customWorld.monsters) {
						if (monster != null) {
							// Rate'leri temizle
							monster.getTemplate().clearInstanceRates();
							// Eğer ölmediyse sil
							if (!monster.isDead()) {
								monster.deleteMe();
							}
						}
					}
					customWorld.monsters.clear(); // Liste temizle
					
					// Solo player'ı çıkar
					teleportPlayer(player, EXIT_LOC, 0);
					world.removeAllowed(player);
					InstanceManager.getInstance().destroyInstance(world.getInstanceId());
					world.destroy();
					LOGGER.info("CustomInstance: Instance destroyed for player: " + player.getName() + 
							   ". Instance ID: " + world.getInstanceId());
				}
			}
			return null;
		}
		
		// Instance giriş kontrolü
		try
		{
			final int index = Integer.parseInt(event);
			enterInstance(player, index);
			return null;
		}
		catch (Exception e)
		{
			LOGGER.warning("CustomInstance: " + e.getMessage());
			return null;
		}
	}
	
	@Override
	public void onEnterInstance(Player player, InstanceWorld world, boolean firstEntrance)
	{
		if (firstEntrance)
		{
			LOGGER.info("CustomInstance: First entrance to instance for player: " + player.getName());
			// İlk giriş olduğunda özel işlemler yapabilirsiniz
			world.setStatus(0);
		}
	}
	
	@Override
	public String onKill(Npc npc, Player player, boolean isSummon) {
		final InstanceWorld tmpWorld = InstanceManager.getInstance().getWorld(npc);
		if (tmpWorld instanceof CustomWorld) {
			// Sadece log atalım
		}
		return super.onKill(npc, player, isSummon);
	}
	
	private void loadMonstersFromDB()
	{
		try (Connection con = DatabaseFactory.getConnection(); // getInstance() olmadan direkt getConnection()
			PreparedStatement ps = con.prepareStatement("SELECT npc_id FROM instance_npc");
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				MONSTER_IDS.add(rs.getInt("npc_id"));
			}
		}
		catch (Exception e)
		{
			LOGGER.warning("CustomInstance: Error loading monster IDs from database: " + e.getMessage());
		}
	}
	
	private int getRequiredItemId(int level) {
		if (level >= 20 && level <= 27)
			return 62601;
		else if (level >= 28 && level <= 35)
			return 62602;
		else if (level >= 36 && level <= 43)
			return 62603;
		else if (level >= 44 && level <= 51)
			return 62604;
		else if (level >= 52 && level <= 59)
			return 62605;
		else if (level >= 60 && level <= 67)
			return 62606;
		else if (level >= 68 && level <= 74)
			return 62607;
		else if (level >= 75 && level <= 79)
			return 62608;
		else if (level >= 80 && level <= 81)
			return 62609;
		else if (level >= 82 && level <= 83)
			return 62610;
		else if (level >= 84 && level <= 86)
			return 62611;
		return 0;
	}

	@Override
	protected boolean checkConditions(Player player) {
		return checkInstanceConditions(player, 0); // Default index 0
	}


	private boolean removeRequiredItems(Player player) {
		int requiredItemId = getRequiredItemId(player.getLevel());
		if (player.getInventory().getInventoryItemCount(requiredItemId, -1) < 1) {
			return false;
		}
		// İtemi sil
		return player.destroyItemByItemId("Instance Entry", requiredItemId, 1, player, true);
	}
	
// Yeni method
private boolean checkInstanceConditions(Player player, int index) {
    // Party kontrolü
    final Party party = player.getParty();
    if (party != null) {
        return checkPartyConditions(player, index);
    }

    // Solo oyuncu kontrolleri
    // Level kontrolü
    if (player.getLevel() < LEVEL_RANGES[index][0] || player.getLevel() > LEVEL_RANGES[index][1]) {
        player.sendMessage("Your level is not appropriate for this instance.");
        return false;
    }

    // Item kontrolü - sadece kontrol et, silme
    int requiredItemId = getRequiredItemId(player.getLevel());
    if (player.getInventory().getInventoryItemCount(requiredItemId, -1) < 1) {
        player.sendMessage("You need Monster Stone for your level to enter this instance.");
        return false;
    }

    return true;
}


@Override
protected void enterInstance(Player player, int index) {
    try {
        // Solo giriş için instance limit kontrolü
        if (player.getParty() == null && !checkInstanceLimit(player)) {
            return;
        }

        // Koşulları kontrol et
        if (!checkInstanceConditions(player, index)) {
            return;
        }
        
        // Mevcut instance kontrolü
        InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
        if (world != null) {
            if (!(world instanceof CustomWorld)) {
                player.sendPacket(SystemMessageId.YOU_HAVE_ENTERED_ANOTHER_INSTANCE_ZONE_THEREFORE_YOU_CANNOT_ENTER_CORRESPONDING_DUNGEON);
                return;
            }
            world.removeAllowed(player);
            InstanceManager.getInstance().destroyInstance(world.getInstanceId());
            world.destroy();
            LOGGER.info("CustomInstance: Old instance destroyed for player: " + player.getName());
        }
        
        // Yeni instance oluştur
        final Instance instance = InstanceManager.getInstance().createDynamicInstance(TEMPLATE_IDS[index]);
        if (instance == null) {
            LOGGER.warning("CustomInstance: Failed to create instance!");
            return;
        }
        
        CustomWorld customWorld = new CustomWorld();
        customWorld.setInstance(instance);
        customWorld.index = index;
        
        // Instance ayarları
        instance.setExitLoc(new Location(player));
        instance.setDuration(DURATION[0] * 1000);
        instance.setEmptyDestroyTime(300000);
        instance.setAllowSummon(false);
        
        InstanceManager.getInstance().addWorld(customWorld);
        
        boolean success = false;
        final Party party = player.getParty();
        
        if (party != null) {
            // Party girişi
            for (Player member : party.getMembers()) {
                if (removeRequiredItems(member)) {
                    customWorld.addAllowed(member);
                    teleportPlayer(member, TELEPORTS[0], instance.getId());
                    recordInstanceEntry(member); // Her üye için kayıt
                    success = true;
                }
            }
        } else {
            // Solo giriş
            if (removeRequiredItems(player)) {
                customWorld.addAllowed(player);
                teleportPlayer(player, TELEPORTS[0], instance.getId());
                recordInstanceEntry(player);
                success = true;
            }
        }

        if (success) {
            // Mobları spawn et
            spawnInstanceMonsters(customWorld, player);
            // Exit NPC'yi spawn et
            spawnExitNpc(customWorld);
        } else {
            // Başarısız olursa instance'ı temizle
            InstanceManager.getInstance().destroyInstance(instance.getId());
            customWorld.destroy();
        }
        
    } catch (Exception e) {
        LOGGER.warning("CustomInstance Error: " + e.getMessage());
        e.printStackTrace();
    }
}
	
@Override
protected void finishInstance(InstanceWorld world) {
    if (world instanceof CustomWorld) {
        CustomWorld customWorld = (CustomWorld) world;
        try {
            // Önce tüm mobların rate'lerini temizle ve unspawn et
            for (Npc monster : customWorld.monsters) {
                if (monster != null) {
                    // Rate'leri temizle
                    monster.getTemplate().clearInstanceRates();
                    // Eğer ölmediyse sil
                    if (!monster.isDead()) {
                        monster.deleteMe();
                    }
                }
            }
            customWorld.monsters.clear();
            
            // Tüm oyuncuları çıkar
            for (Player player : world.getAllowed()) {
                if (player != null && player.getInstanceId() == world.getInstanceId()) {
                    teleportPlayer(player, EXIT_LOC, 0);
                    world.removeAllowed(player);
                }
            }
            
            // Instance'ı temizle
            InstanceManager.getInstance().destroyInstance(world.getInstanceId());
            world.destroy();
            
            LOGGER.info("CustomInstance: Instance finished and destroyed. Instance ID: " + world.getInstanceId());
        } catch (Exception e) {
            LOGGER.warning("Error while finishing instance: " + e.getMessage());
        }
    }
}
	
	private List<SpawnTerritory> loadSpawnTerritories()
	{
		List<SpawnTerritory> territories = new ArrayList<>();
		try
		{
			File xmlFile = new File("./data/InstanceMonsters.xml");
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(xmlFile);
			doc.getDocumentElement().normalize();
			
			NodeList spawnList = doc.getElementsByTagName("spawn");
			for (int i = 0; i < spawnList.getLength(); i++)
			{
				Element spawnElement = (Element) spawnList.item(i);
				
				NodeList territoryList = spawnElement.getElementsByTagName("territory");
				if (territoryList.getLength() > 0)
				{
					Element territory = (Element) territoryList.item(0);
					
					int minZ = Integer.parseInt(territory.getAttribute("minZ"));
					int maxZ = Integer.parseInt(territory.getAttribute("maxZ"));
					
					NodeList npcList = spawnElement.getElementsByTagName("npc");
					int count = 0;
					if (npcList.getLength() > 0)
					{
						Element npc = (Element) npcList.item(0);
						count = Integer.parseInt(npc.getAttribute("count"));
					}
					
					SpawnTerritory spawnTerritory = new SpawnTerritory(minZ, maxZ, count);
					
					NodeList nodes = territory.getElementsByTagName("node");
					for (int j = 0; j < nodes.getLength(); j++)
					{
						Element node = (Element) nodes.item(j);
						int x = Integer.parseInt(node.getAttribute("x"));
						int y = Integer.parseInt(node.getAttribute("y"));
						spawnTerritory.addNode(x, y);
					}
					
					territories.add(spawnTerritory);
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warning("Error loading spawn territories: " + e.getMessage());
		}
		return territories;
	}
	
	private Location getRandomLocationInTerritory(SpawnTerritory territory) {
		if (territory.nodes.isEmpty()) {
			return null;
		}
	
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		
		for (Point2D point : territory.nodes) {
			minX = Math.min(minX, point.getX());
			maxX = Math.max(maxX, point.getX());
			minY = Math.min(minY, point.getY());
			maxY = Math.max(maxY, point.getY());
		}
	
		for (int i = 0; i < 100; i++) {
			int x = minX + getRandom(maxX - minX);
			int y = minY + getRandom(maxY - minY);
			
			if (isPointInTerritory(x, y, territory.nodes)) {
				int z = territory.minZ + getRandom(territory.maxZ - territory.minZ + 1);
				return new Location(x, y, z);
			}
		}
	
		return null;
	}
	
	private boolean isPointInTerritory(int x, int y, List<Point2D> nodes)
	{
		boolean inside = false;
		int j = nodes.size() - 1;
		
		for (int i = 0; i < nodes.size(); i++)
		{
			Point2D pi = nodes.get(i);
			Point2D pj = nodes.get(j);
			
			if (((pi.getY() > y) != (pj.getY() > y)) && (x < (pj.getX() - pi.getX()) * (y - pi.getY()) / (pj.getY() - pi.getY()) + pi.getX()))
			{
				inside = !inside;
			}
			j = i;
		}
		
		return inside;
	}
	

	private List<Integer> loadCustomInstanceNpcs(Player player) {
		List<Integer> npcIds = new ArrayList<>();
		
		// Player leveline göre monster level aralığını belirle
		int minLevel;
		int maxLevel;
		int playerLevel = player.getLevel();
		
		if (playerLevel >= 20 && playerLevel <= 27) {
			minLevel = 20;
			maxLevel = 27;
		}
		else if (playerLevel >= 28 && playerLevel <= 35) {
			minLevel = 28;
			maxLevel = 35;
		}
		else if (playerLevel >= 36 && playerLevel <= 43) {
			minLevel = 36;
			maxLevel = 43;
		}
		else if (playerLevel >= 44 && playerLevel <= 51) {
			minLevel = 44;
			maxLevel = 51;
		}
		else if (playerLevel >= 52 && playerLevel <= 59) {
			minLevel = 52;
			maxLevel = 59;
		}
		else if (playerLevel >= 60 && playerLevel <= 67) {
			minLevel = 60;
			maxLevel = 67;
		}
		else if (playerLevel >= 68 && playerLevel <= 74) {
			minLevel = 68;
			maxLevel = 74;
		}
		else if (playerLevel >= 75 && playerLevel <= 79) {
			minLevel = 75;
			maxLevel = 79;
		}
		else if (playerLevel >= 80 && playerLevel <= 81) {
			minLevel = 80;
			maxLevel = 81;
		}
		else if (playerLevel >= 82 && playerLevel <= 83) {
			minLevel = 82;
			maxLevel = 83;
		}
		else if (playerLevel >= 84 && playerLevel <= 86) {
			minLevel = 84;
			maxLevel = 86;
		}
		else {
			LOGGER.warning("CustomInstance: Invalid player level: " + playerLevel);
			return npcIds;
		}
		
		// Level aralığına göre monster'ları çek
		String sql = "SELECT npc_id FROM instance_npc WHERE type = 'Monster' AND level BETWEEN ? AND ?";
		
		try (Connection con = DatabaseFactory.getConnection();
			 PreparedStatement ps = con.prepareStatement(sql)) 
		{
			ps.setInt(1, minLevel);
			ps.setInt(2, maxLevel);
			
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					npcIds.add(rs.getInt("npc_id"));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Could not load instance NPCs: " + e.getMessage());
		}
		
		LOGGER.info("Loaded " + npcIds.size() + " monsters for level range " + minLevel + "-" + maxLevel);
		return npcIds;
	}

	private void spawnInstanceMonsters(CustomWorld world, Player player) {
    if (world == null || player == null) {
        return;
    }

    try {
        List<SpawnTerritory> territories = loadSpawnTerritories();
        List<Integer> npcIds = loadCustomInstanceNpcs(player);

        if (territories.isEmpty()) {
            LOGGER.warning("No spawn territories found!");
            return;
        }

        if (npcIds.isEmpty()) {
            LOGGER.warning("No NPCs found for player level " + player.getLevel());
            return;
        }

        int spawnedCount = 0;
        for (SpawnTerritory territory : territories) {
            if (territory.count <= 0) {
                continue;
            }

            for (int i = 0; i < territory.count; i++) {
                Location pos = getRandomLocationInTerritory(territory);
                if (pos != null) {
                    int randomNpcId = npcIds.get(getRandom(npcIds.size()));
                    
                    Npc monster = addSpawn(randomNpcId, 
                                         pos.getX(), 
                                         pos.getY(), 
                                         pos.getZ(), 
                                         getRandom(65535),
                                         false,
                                         0,
                                         false,
                                         world.getInstanceId());
                    
                    if (monster != null) {
                        try {
                            // Drop rate'leri ayarla
                            monster.getTemplate().setInstanceRates(
                                CustomWorld.INSTANCE_DROP_CHANCE_RATE,
                                CustomWorld.INSTANCE_DROP_AMOUNT_RATE,
                                CustomWorld.INSTANCE_SPOIL_CHANCE_RATE,
                                CustomWorld.INSTANCE_SPOIL_AMOUNT_RATE,
                                SPECIAL_ITEM_IDS
                            );

                            // Monster'ı world listesine ekle
                            world.monsters.add(monster);
                            
                            // Instance'a bağlı olduğundan emin ol
                            monster.setInstanceId(world.getInstanceId());
                            
                            spawnedCount++;
                        
                        } catch (Exception e) {
                            LOGGER.warning("Error configuring monster " + monster.getName() + ": " + e.getMessage());
                            monster.deleteMe(); // Hata durumunda temizle
                            continue;
                        }
                    }
                }
            }
        }

        LOGGER.info("Successfully spawned " + spawnedCount + " monsters in instance " + 
                   world.getInstanceId() + " for player " + player.getName());

    } catch (Exception e) {
        LOGGER.warning("Error while spawning instance monsters: " + e.getMessage());
        e.printStackTrace();
    }
}

	
	private void spawnExitNpc(CustomWorld world)
	{
		final int CENTER_X = TELEPORTS[0].getX();
		final int CENTER_Y = TELEPORTS[0].getY();
		final int CENTER_Z = TELEPORTS[0].getZ();
		
		Npc exitNpc = addSpawn(START_NPC, CENTER_X, CENTER_Y, CENTER_Z, 0, false, 0, false, world.getInstanceId());
		if (exitNpc != null)
		{
			LOGGER.info("CustomInstance: Exit NPC spawned in instance " + world.getInstanceId());
			world.monsters.add(exitNpc);
		}
	}
	
	private static final int MAX_ENTRIES_PER_12_HOURS = 3;
private static final long HOURS_12_IN_MILLIS = 12 * 60 * 60 * 1000;

private boolean checkInstanceLimit(Player player) {
    try (Connection con = DatabaseFactory.getConnection();
         PreparedStatement ps = con.prepareStatement(
             "SELECT COUNT(*) as entry_count, MIN(entry_time) as first_entry " +
             "FROM instance_entries " +
             "WHERE charId = ? AND entry_time > DATE_SUB(NOW(), INTERVAL 12 HOUR)")) {
        
        ps.setInt(1, player.getObjectId());
        
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int entryCount = rs.getInt("entry_count");
                Timestamp firstEntry = rs.getTimestamp("first_entry");
                
                if (entryCount >= MAX_ENTRIES_PER_12_HOURS) {
                    // Kalan süreyi hesapla ve bildir
                    if (firstEntry != null) {
                        long resetTime = firstEntry.getTime() + HOURS_12_IN_MILLIS;
                        long remainingTime = resetTime - System.currentTimeMillis();
                        
                        if (remainingTime > 0) {
                            long hours = remainingTime / (60 * 60 * 1000);
                            long minutes = (remainingTime % (60 * 60 * 1000)) / (60 * 1000);
                            player.sendMessage("You have reached the maximum instance entries limit. Please wait " + 
                                             hours + " hours and " + minutes + " minutes.");
                        }
                    }
                    return false;
                }
            }
            return true;
        }
    } catch (Exception e) {
        LOGGER.warning("Error checking instance limit: " + e.getMessage());
        return false;
    }
}

private void recordInstanceEntry(Player player) {
    try (Connection con = DatabaseFactory.getConnection();
         PreparedStatement ps = con.prepareStatement(
             "INSERT INTO instance_entries (charId, entry_time) VALUES (?, NOW())")) {
        
        ps.setInt(1, player.getObjectId());
        ps.execute();
        LOGGER.info("Instance entry recorded for player: " + player.getName());
        
    } catch (Exception e) {
        LOGGER.warning("Error recording instance entry: " + e.getMessage());
    }
}

	
	private String getRemainingEntriesInfo(Player player) {
		try (Connection con = DatabaseFactory.getConnection();
			 PreparedStatement ps = con.prepareStatement(
				 "SELECT COUNT(*) as entry_count, MIN(entry_time) as first_entry " +
				 "FROM instance_entries " +
				 "WHERE charId = ? AND entry_time > DATE_SUB(NOW(), INTERVAL 12 HOUR)")) {
			
			ps.setInt(1, player.getObjectId());
			
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					int entryCount = rs.getInt("entry_count");
					Timestamp firstEntry = rs.getTimestamp("first_entry");
					int remainingEntries = MAX_ENTRIES_PER_12_HOURS - entryCount;
					
					StringBuilder sb = new StringBuilder();
					
					// Kalan giriş sayısı
					if (remainingEntries > 0) {
						sb.append("Remaining Entries: <font color=\"00FF00\">").append(remainingEntries).append("</font>");
					} else {
						sb.append("Remaining Entries: <font color=\"FF0000\">0</font>");
					}
					
					// Reset süresi
					if (entryCount > 0 && firstEntry != null) {
						long resetTime = firstEntry.getTime() + HOURS_12_IN_MILLIS;
						long remainingTime = resetTime - System.currentTimeMillis();
						
						if (remainingTime > 0) {
							long hours = remainingTime / (60 * 60 * 1000);
							long minutes = (remainingTime % (60 * 60 * 1000)) / (60 * 1000);
							sb.append("<br1>Reset in: <font color=\"FF8000\">")
							  .append(hours).append("h ").append(minutes).append("m</font>");
						}
					}
					
					return sb.toString();
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error getting remaining entries info: " + e.getMessage());
		}
		return "<font color=\"FF0000\">Error retrieving instance information.</font>";
	}

	
	private boolean checkPartyConditions(Player player, int index) {
		final Party party = player.getParty();
		
		// Party lider kontrolü
		if (!party.isLeader(player)) {
			player.sendPacket(SystemMessageId.ONLY_A_PARTY_LEADER_CAN_MAKE_THE_REQUEST_TO_ENTER);
			return false;
		}
		
		// Party boyutu kontrolü
		if (party.getMemberCount() > MAX_PARTY_SIZE) {
			player.sendPacket(SystemMessageId.YOU_CANNOT_ENTER_DUE_TO_THE_PARTY_HAVING_EXCEEDED_THE_LIMIT);
			return false;
		}
		
		// Her party üyesi için kontroller
		for (Player partyMember : party.getMembers()) {
			// Instance limit kontrolü
			if (!checkInstanceLimit(partyMember)) {
				// Party liderine özel mesaj
				player.sendMessage("Party member " + partyMember.getName() + " has reached their daily instance limit.");
				return false;
			}
	
			// Level kontrolü
			if (partyMember.getLevel() < LEVEL_RANGES[index][0] || partyMember.getLevel() > LEVEL_RANGES[index][1]) {
				final SystemMessage sm = new SystemMessage(SystemMessageId.C1_S_LEVEL_DOES_NOT_CORRESPOND_TO_THE_REQUIREMENTS_FOR_ENTRY);
				sm.addPcName(partyMember);
				player.sendPacket(sm);
				return false;
			}
			
			// Mesafe kontrolü
			if (!partyMember.isInsideRadius3D(player, 1000)) {
				final SystemMessage sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_LOCATION_WHICH_CANNOT_BE_ENTERED_THEREFORE_IT_CANNOT_BE_PROCESSED);
				sm.addPcName(partyMember);
				player.sendPacket(sm);
				return false;
			}
			
			// Item kontrolü - sadece kontrol et, silme
			int requiredItemId = getRequiredItemId(partyMember.getLevel());
			if (partyMember.getInventory().getInventoryItemCount(requiredItemId, -1) < 1) {
				final SystemMessage sm = new SystemMessage(SystemMessageId.C1_S_ITEM_REQUIREMENT_IS_NOT_SUFFICIENT_AND_CANNOT_BE_ENTERED);
				sm.addPcName(partyMember);
				player.sendPacket(sm);
				return false;
			}
		}
		return true;
	}
	
	private boolean checkRequiredItem(Player player, int level) {
		int requiredItemId = getRequiredItemId(level);
		return player.getInventory().getInventoryItemCount(requiredItemId, -1) >= 1;
	}

	// Singleton
	public static CustomInstance getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final CustomInstance INSTANCE = new CustomInstance();
	}
	
	private static class SpawnLocation
	{
		private final int x, y, z;
		private final int count;
		private int currentCount;
		private static final int MIN_OFFSET = 50;
		private static final int MAX_OFFSET = 300;
		
		public SpawnLocation(int x, int y, int z, int minZ, int maxZ, int count)
		{
			this.x = x;
			this.y = y;
			this.z = z;
			this.count = Math.min(count, 20);
			this.currentCount = 0;
		}
		
		public boolean canSpawn()
		{
			return currentCount < count;
		}
		
		public Location getRandomPosition()
		{
			try
			{
				int offsetX = MIN_OFFSET + getRandom(MAX_OFFSET - MIN_OFFSET);
				int offsetY = MIN_OFFSET + getRandom(MAX_OFFSET - MIN_OFFSET);
				
				if (getRandom(2) == 0)
					offsetX *= -1;
				if (getRandom(2) == 0)
					offsetY *= -1;
				
				int finalX = Math.max(-32768, Math.min(32767, x + offsetX));
				int finalY = Math.max(-32768, Math.min(32767, y + offsetY));
				
				currentCount++; // Başarılı spawn sonrası sayacı artır
				return new Location(finalX, finalY, z);
			}
			catch (Exception e)
			{
				LOGGER.warning("Error in getRandomPosition: " + e.getMessage());
				return new Location(x, y, z);
			}
		}
	}
	
	private List<SpawnLocation> loadSpawnLocations()
	{
		List<SpawnLocation> locations = new ArrayList<>();
		String sql = "SELECT loc_x, loc_y, loc_z, minZ, maxZ, count FROM spawnlist";
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(sql);
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				try
				{
					SpawnLocation location = new SpawnLocation(rs.getInt("loc_x"), rs.getInt("loc_y"), rs.getInt("loc_z"), rs.getInt("minZ"), rs.getInt("maxZ"), rs.getInt("count"));
					locations.add(location);
				}
				catch (Exception e)
				{
					LOGGER.warning("Error creating spawn location: " + e.getMessage());
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warning("Could not load spawn locations: " + e.getMessage());
		}
		
		if (locations.isEmpty())
		{
			LOGGER.warning("No spawn locations loaded!");
		}
		else
		{
			LOGGER.info("Loaded " + locations.size() + " spawn locations.");
		}
		
		return locations;
	}
	
	public static void main(String[] args)
	{
		LOGGER.info("(-------------------------------------------------=[ Monster Realm Instance ]");
		new CustomInstance();
	}
	
}