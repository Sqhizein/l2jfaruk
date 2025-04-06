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
package handlers.bypasshandlers;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import org.l2jmobius.Config;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.cache.HtmCache;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.enums.DropType;
import org.l2jmobius.gameserver.handler.IBypassHandler;
import org.l2jmobius.gameserver.instancemanager.QuestManager;
import org.l2jmobius.gameserver.model.Elementals;
import org.l2jmobius.gameserver.model.Spawn;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.holders.DropGroupHolder;
import org.l2jmobius.gameserver.model.holders.DropHolder;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.quest.Quest;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.HtmlUtil;
import org.l2jmobius.gameserver.util.Util;
import java.util.logging.Logger;
import quests.Q00801_MonsterHunterSpecialization.Q00801_MonsterHunterSpecialization;

/**
 * @author NosBit
 */
public class NpcViewMod implements IBypassHandler
{
	
	private static final Logger LOGGER = Logger.getLogger(NpcViewMod.class.getName());
	
	private static final String[] COMMANDS =
	{
		"NpcViewMod"
	};
	
	private static final int DROP_LIST_ITEMS_PER_PAGE = 10;
	
	// Cache yapısı
	private static final Map<Integer, HuntingInfoCache> HUNTING_INFO_CACHE = new ConcurrentHashMap<>();
	private static final long CACHE_DURATION = 10000; // 30 Saniye (milisaniye cinsinden)
	
	// Cache sınıfı
	private static class HuntingInfoCache
	{
		private final String content;
		private final boolean hasHuntingInfo;
		private final long timestamp;
		
		public HuntingInfoCache(String content, boolean hasHuntingInfo)
		{
			this.content = content;
			this.hasHuntingInfo = hasHuntingInfo;
			this.timestamp = System.currentTimeMillis();
		}
		
		public boolean isExpired()
		{
			return System.currentTimeMillis() - timestamp > CACHE_DURATION;
		}
	}
	
	// SQL query'si
	private static final String HUNTING_INFO_QUERY = "SELECT m.*, mkt.kills, mhr.weekly_score, mhr.daily_score " + "FROM monsters_hunter m " + "LEFT JOIN monster_kill_tracker mkt ON m.monster_id = mkt.monster_id AND mkt.char_id = ? AND mkt.quest_id = 801 " + "LEFT JOIN monster_hunter_rankings mhr ON mhr.char_id = ? " + "WHERE m.monster_id = ?";
	
	@Override
	public boolean useBypass(String command, Player player, Creature bypassOrigin)
	{
		final StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (!st.hasMoreTokens())
		{
			LOGGER.warning("Bypass[NpcViewMod] used without enough parameters.");
			return false;
		}
		
		final String actualCommand = st.nextToken();
		switch (actualCommand.toLowerCase())
		{
			case "view":
			{
				final WorldObject target;
				if (st.hasMoreElements())
				{
					try
					{
						target = World.getInstance().findObject(Integer.parseInt(st.nextToken()));
					}
					catch (NumberFormatException e)
					{
						return false;
					}
				}
				else
				{
					target = player.getTarget();
				}
				
				final Npc npc = target instanceof Npc ? (Npc) target : null;
				if (npc == null)
				{
					return false;
				}
				
				sendNpcView(player, npc);
				break;
			}
			case "droplist":
			{
				if (st.countTokens() < 2)
				{
					LOGGER.warning("Bypass[NpcViewMod] used without enough parameters.");
					return false;
				}
				
				final String dropListTypeString = st.nextToken();
				try
				{
					final DropType dropListType = Enum.valueOf(DropType.class, dropListTypeString);
					final WorldObject target = World.getInstance().findObject(Integer.parseInt(st.nextToken()));
					final Npc npc = target instanceof Npc ? (Npc) target : null;
					if (npc == null)
					{
						return false;
					}
					final int page = st.hasMoreElements() ? Integer.parseInt(st.nextToken()) : 0;
					sendNpcDropList(player, npc, dropListType, page);
				}
				catch (NumberFormatException e)
				{
					return false;
				}
				catch (IllegalArgumentException e)
				{
					LOGGER.warning("Bypass[NpcViewMod] unknown drop list scope: " + dropListTypeString);
					return false;
				}
				break;
			}
			case "huntinginfo":
			{
				if (!st.hasMoreTokens())
				{
					LOGGER.warning("Bypass[NpcViewMod] used without enough parameters for hunting info.");
					return false;
				}
				try
				{
					final WorldObject target = World.getInstance().findObject(Integer.parseInt(st.nextToken()));
					final Npc npc = target instanceof Npc ? (Npc) target : null;
					if (npc == null)
					{
						return false;
					}
					showHuntingInfo(player, npc);
				}
				catch (NumberFormatException e)
				{
					LOGGER.warning("Error in hunting info bypass: " + e.getMessage());
					return false;
				}
				break;
			}
		}
		
		return true;
	}
	
	@Override
	public String[] getBypassList()
	{
		return COMMANDS;
	}
	
	private String getChampionType(int type)
	{
		switch (type)
		{
			case 1:
				return "Easy";
			case 2:
				return "Normal";
			case 3:
				return "Hard";
			case 4:
				return "Very Hard";
			case 5:
				return "Catastrophic";
			case 6:
				return "Boss";
			default:
				return "Unknown";
		}
	}
	
	private void showHuntingInfo(Player player, Npc npc)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(player, "data/html/mods/NpcView/HuntingInfo.htm");
		
		// Cache key oluştur (NPC ID ve Player ID kombinasyonu)
		int cacheKey = Objects.hash(npc.getId(), player.getObjectId());
		
		// Cache kontrol
		HuntingInfoCache cachedInfo = HUNTING_INFO_CACHE.get(cacheKey);
		if (cachedInfo != null && !cachedInfo.isExpired())
		{
			html.replace("%content%", cachedInfo.content);
			addDropSpoilButtons(html, npc, cachedInfo.hasHuntingInfo);
			html.replace("%objectId%", String.valueOf(npc.getObjectId()));
			player.sendPacket(html);
			return;
		}
		
		StringBuilder contentSb = new StringBuilder(2048);
		boolean hasHuntingInfo = false;
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement(HUNTING_INFO_QUERY))
			{
				ps.setInt(1, player.getObjectId());
				ps.setInt(2, player.getObjectId());
				ps.setInt(3, npc.getId());
				
				try (ResultSet rs = ps.executeQuery())
				{
					if (rs.next())
					{
						hasHuntingInfo = true;
						int currentKills = rs.getInt("kills");
						int currentStage = calculateCurrentStage(currentKills, rs);
						int nextStageKills = calculateNextStageKills(currentKills, rs);
						double[] bonuses = calculateBonuses(currentStage, rs);
						
						// Monster Info Header
						contentSb.append("<table border=0 cellspacing=0 cellpadding=0 width=270 background=\"L2UI_CT1.Windows.Windows_DF_TooltipBG\">");
						contentSb.append("<tr><td align=center height=\"").append(npc.isChampion() ? "50" : "28").append("\" valign=center>");
						contentSb.append("<table width=270 border=0 cellspacing=0 cellpadding=4>");
						contentSb.append("<tr><td align=center>");
						contentSb.append("<font name=\"hs8\" color=\"CD9000\">").append(npc.getName()).append("</font>");
						
						if (npc.isChampion())
						{
							contentSb.append("<br1>");
							contentSb.append("<font color=\"FF0000\">[").append(getChampionType(((Attackable) npc).getChampionType())).append(" Champion]</font>");
						}
						
						contentSb.append("</td></tr>");
						contentSb.append("</table>");
						contentSb.append("</td></tr></table>");
						
						// Basic Info
						contentSb.append("<table width=270 border=0 cellspacing=1 cellpadding=3 bgcolor=666666>");
						contentSb.append("<tr><td width=130 bgcolor=333333>Level:</td><td width=140 align=right bgcolor=292929><font color=\"LEVEL\">").append(rs.getInt("monster_lvl")).append("</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Race:</td><td align=right bgcolor=292929><font color=\"LEVEL\">").append(rs.getString("monster_race")).append("</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Base Score:</td><td align=right bgcolor=292929><font color=\"LEVEL\">").append(rs.getInt("base_score")).append("</font></td></tr>");
						contentSb.append("</table><br>");
						
						// Progress Info Header
						contentSb.append("<table border=0 cellspacing=0 cellpadding=0 width=270 background=\"L2UI_CT1.Windows.Windows_DF_TooltipBG\">");
						contentSb.append("<tr><td align=center height=25 valign=center>");
						contentSb.append("<table width=270 border=0 cellspacing=0 cellpadding=4>");
						contentSb.append("<tr><td align=center height=25><font name=\"hs8\" color=\"CD9000\">Progress Information</font></td></tr>");
						contentSb.append("</table>");
						contentSb.append("</td></tr></table>");
						
						// Progress Details
						contentSb.append("<table width=270 border=0 cellspacing=1 cellpadding=3 bgcolor=666666>");
						contentSb.append("<tr><td width=130 bgcolor=333333>Current Stage:</td><td width=140 align=right bgcolor=292929><font color=\"00FF00\">").append(currentStage).append("/10</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Total Kills:</td><td align=right bgcolor=292929><font color=\"LEVEL\">").append(formatNumber(currentKills)).append("</font></td></tr>");
						if (nextStageKills > 0)
						{
							contentSb.append("<tr><td bgcolor=333333>Next Stage At:</td><td align=right bgcolor=292929>").append(formatNumber(nextStageKills)).append(" kills</td></tr>");
							contentSb.append("<tr><td bgcolor=333333>Remaining Kills:</td><td align=right bgcolor=292929><font color=\"FF9900\">").append(formatNumber(nextStageKills - currentKills)).append("</font></td></tr>");
						}
						contentSb.append("</table><br>");
						
						// Active Bonuses Header
						contentSb.append("<table border=0 cellspacing=0 cellpadding=0 width=270 background=\"L2UI_CT1.Windows.Windows_DF_TooltipBG\">");
						contentSb.append("<tr><td align=center height=25 valign=center>");
						contentSb.append("<table width=270 border=0 cellspacing=0 cellpadding=4>");
						contentSb.append("<tr><td align=center height=25><font name=\"hs8\" color=\"CD9000\">Active Bonuses</font></td></tr>");
						contentSb.append("</table>");
						contentSb.append("</td></tr></table>");
						
						// Bonus Details
						contentSb.append("<table width=270 border=0 cellspacing=1 cellpadding=3 bgcolor=666666>");
						contentSb.append("<tr><td width=130 bgcolor=333333>EXP Bonus:</td><td width=140 align=right bgcolor=292929><font color=\"00FF00\">+").append(String.format("%.1f", bonuses[0] * 100)).append("%</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Drop Rate:</td><td align=right bgcolor=292929><font color=\"00FF00\">+").append(String.format("%.1f", bonuses[1] * 100)).append("%</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Drop Amount:</td><td align=right bgcolor=292929><font color=\"00FF00\">+").append(String.format("%.1f", bonuses[2] * 100)).append("%</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Spoil Rate:</td><td align=right bgcolor=292929><font color=\"00FF00\">+").append(String.format("%.1f", bonuses[3] * 100)).append("%</font></td></tr>");
						contentSb.append("<tr><td bgcolor=333333>Spoil Amount:</td><td align=right bgcolor=292929><font color=\"00FF00\">+").append(String.format("%.1f", bonuses[4] * 100)).append("%</font></td></tr>");
						contentSb.append("</table><br>");
						
						// Stage Rewards Header
						contentSb.append("<table border=0 cellspacing=0 cellpadding=0 width=270 background=\"L2UI_CT1.Windows.Windows_DF_TooltipBG\">");
						contentSb.append("<tr><td align=center height=25 valign=center>");
						contentSb.append("<table width=270 border=0 cellspacing=0 cellpadding=4>");
						contentSb.append("<tr><td align=center height=25><font name=\"hs8\" color=\"CD9000\">Stage Rewards</font></td></tr>");
						contentSb.append("</table>");
						contentSb.append("</td></tr></table>");
						
						// Stage Rewards Details
						for (int i = 1; i <= 10; i++)
						{
							appendStageRewardsL2Style(contentSb, i, currentKills, rs);
						}
						
						// Cache'e ekle
						HUNTING_INFO_CACHE.put(cacheKey, new HuntingInfoCache(contentSb.toString(), hasHuntingInfo));
					}
					else
					{
						setDefaultValuesL2Style(contentSb, npc.getName());
						// Cache'e ekle (default değerler için)
						HUNTING_INFO_CACHE.put(cacheKey, new HuntingInfoCache(contentSb.toString(), false));
					}
				}
			}
		}
		catch (Exception e)
		{
			setDefaultValuesL2Style(contentSb, npc.getName());
			HUNTING_INFO_CACHE.put(cacheKey, new HuntingInfoCache(contentSb.toString(), false));
			LOGGER.warning("Error in showHuntingInfo: " + e.getMessage());
		}
		
		html.replace("%content%", contentSb.toString());
		addDropSpoilButtons(html, npc, hasHuntingInfo);
		html.replace("%objectId%", String.valueOf(npc.getObjectId()));
		player.sendPacket(html);
	}
	
	private void addDropSpoilButtons(NpcHtmlMessage html, Npc npc, boolean hasHuntingInfo)
	{
		if (hasHuntingInfo)
		{
			StringBuilder buttonsSb = new StringBuilder();
			buttonsSb.append("<table width=210 cellpadding=0 cellspacing=0><tr>");
			
			if (npc instanceof Attackable)
			{
				buttonsSb.append("<td align=center><button value=\"Show Drop\" width=100 height=25 action=\"bypass NpcViewMod dropList DROP ").append(npc.getObjectId()).append("\" back=\"L2UI_CT1.Button_DF_Calculator_Down\" fore=\"L2UI_CT1.Button_DF_Calculator\"></td>");
			}
			
			List<DropHolder> spoilList = npc.getTemplate().getSpoilList();
			if (spoilList != null && !spoilList.isEmpty())
			{
				buttonsSb.append("<td align=center><button value=\"Show Spoil\" width=100 height=25 action=\"bypass NpcViewMod dropList SPOIL ").append(npc.getObjectId()).append("\" back=\"L2UI_CT1.Button_DF_Calculator_Down\" fore=\"L2UI_CT1.Button_DF_Calculator\"></td>");
			}
			
			buttonsSb.append("</tr></table>");
			html.replace("%buttons%", buttonsSb.toString());
		}
		else
		{
			html.replace("%buttons%", "");
		}
	}
	
	private void appendStageRewardsL2Style(StringBuilder sb, int stage, int currentKills, ResultSet rs) throws SQLException
	{
		String rewards = rs.getString("monster_reward" + stage);
		String quantities = rs.getString("monster_reward_quantity" + stage);
		int requiredKills = rs.getInt("monster_need_to_kill_for_reward" + stage);
		
		if (requiredKills > 0)
		{
			boolean isCompleted = currentKills >= requiredKills;
			String bgColor = isCompleted ? "333333" : "292929";
			String textColor = isCompleted ? "00FF00" : "FFFFFF";
			
			sb.append("<table width=270 border=0 cellspacing=1 cellpadding=3 bgcolor=666666>");
			sb.append("<tr>");
			sb.append("<td bgcolor=\"").append(bgColor).append("\"><font color=\"").append(textColor).append("\">");
			sb.append("Stage ").append(stage).append(": ").append(formatNumber(requiredKills)).append(" kills");
			if (isCompleted)
			{
				sb.append(" (Complete)");
			}
			sb.append("</font></td>");
			sb.append("</tr>");
			
			if (rewards != null && quantities != null)
			{
				String[] rewardArray = rewards.split(",");
				String[] quantityArray = quantities.split(",");
				
				for (int i = 0; i < rewardArray.length && i < quantityArray.length; i++)
				{
					int itemId = Integer.parseInt(rewardArray[i].trim());
					int quantity = Integer.parseInt(quantityArray[i].trim());
					ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
					
					if (item != null)
					{
						sb.append("<tr>");
						sb.append("<td bgcolor=\"").append(bgColor).append("\"><font color=\"").append(textColor).append("\">• ").append(formatNumber(quantity)).append("x ").append(item.getName()).append("</font></td>");
						sb.append("</tr>");
					}
				}
			}
			sb.append("</table><br>");
		}
	}
	
	private void setDefaultValuesL2Style(StringBuilder contentSb, String npcName)
	{
		// Monster Info Header
		contentSb.append("<table border=0 cellspacing=0 cellpadding=0 width=270 background=\"L2UI_CT1.Windows.Windows_DF_TooltipBG\">");
		contentSb.append("<tr><td align=center height=28 valign=center>");
		contentSb.append("<table width=270 border=0 cellspacing=0 cellpadding=4>");
		contentSb.append("<tr><td align=center><font name=\"hs12\" color=\"CD9000\" size=-2>").append(npcName).append("</font></td></tr>");
		contentSb.append("</table>");
		contentSb.append("</td></tr></table><br>");
		
		// No Info Message
		contentSb.append("<table width=270>");
		contentSb.append("<tr><td height=50 align=center><font color=\"FF0000\">No hunting information available for this monster or NPC.</font></td></tr>");
		contentSb.append("</table>");
	}
	
	private String formatNumber(int number)
	{
		return String.format("%,d", number).replace(",", ".");
	}
	
	private double[] calculateBonuses(int currentStage, ResultSet rs) throws SQLException
	{
		double[] bonuses = new double[5]; // [exp, drop, dropAmount, spoil, spoilAmount]
		
		// Stage'e göre bonus değerlerini al
		if (currentStage > 0 && currentStage <= 10)
		{
			// Sadece mevcut stage'in bonuslarını al
			bonuses[0] = rs.getDouble("exp_bonus" + currentStage);
			bonuses[1] = rs.getDouble("drop_bonus" + currentStage);
			bonuses[2] = rs.getDouble("drop_amount_bonus" + currentStage);
			bonuses[3] = rs.getDouble("spoil_bonus" + currentStage);
			bonuses[4] = rs.getDouble("spoil_amount_bonus" + currentStage);
		}
		
		return bonuses;
	}
	
	private int calculateCurrentStage(int currentKills, ResultSet rs) throws SQLException
	{
		for (int i = 10; i >= 1; i--)
		{
			int requiredKills = rs.getInt("monster_need_to_kill_for_reward" + i);
			if (currentKills >= requiredKills && requiredKills > 0)
			{
				return i;
			}
		}
		return 0;
	}
	
	private int calculateNextStageKills(int currentKills, ResultSet rs) throws SQLException
	{
		for (int i = 1; i <= 10; i++)
		{
			int requiredKills = rs.getInt("monster_need_to_kill_for_reward" + i);
			if (requiredKills > currentKills && requiredKills > 0)
			{
				return requiredKills;
			}
		}
		return 0;
	}
	
	public static void sendNpcView(Player player, Npc npc)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage();
		html.setFile(player, "data/html/mods/NpcView/Info.htm");
		html.replace("%name%", npc.getName());
		
		// HP/MP modifiers hesaplama
		StringBuilder hpModSb = new StringBuilder();
		StringBuilder mpModSb = new StringBuilder();
		
		// Base HP/MP değerleri
		float baseHp = npc.getTemplate().getBaseHpMax();
		float baseMp = npc.getTemplate().getBaseMpMax();
		
		// Final HP/MP değerleri - NPC'nin gerçek max değerlerini al
		float finalHp = npc.getMaxHp(); // Skill modifierlar dahil
		float finalMp = npc.getMaxMp();
		
		// HP Modifier Skill kontrolü (4408) - Sadece göstermek için
		org.l2jmobius.gameserver.model.skill.Skill hpModSkill = npc.getKnownSkill(4408);
		if (hpModSkill != null)
		{
			double hpMod = 1.0;
			switch (hpModSkill.getLevel())
			{
				case 1:
					hpMod = 1.0;
					break;
				case 2:
					hpMod = 1.1;
					break;
				case 3:
					hpMod = 1.21;
					break;
				case 4:
					hpMod = 1.33;
					break;
				case 5:
					hpMod = 1.46;
					break;
				case 6:
					hpMod = 1.61;
					break;
				case 7:
					hpMod = 1.77;
					break;
				case 8:
					hpMod = 0.25;
					break;
				case 9:
					hpMod = 0.5;
					break;
				case 10:
					hpMod = 2.0;
					break;
				case 11:
					hpMod = 3.0;
					break;
				case 12:
					hpMod = 4.0;
					break;
				case 13:
					hpMod = 5.0;
					break;
				case 14:
					hpMod = 6.0;
					break;
				case 15:
					hpMod = 7.0;
					break;
				case 16:
					hpMod = 8.0;
					break;
				case 17:
					hpMod = 9.0;
					break;
				case 18:
					hpMod = 10.0;
					break;
				case 19:
					hpMod = 11.0;
					break;
				case 20:
					hpMod = 12.0;
					break;
			}
			hpModSb.append("<td width=65 valign=middle><font size=\"-2\" color=\"70FFCA\">Skill: ×").append(String.format("%.2f", hpMod)).append("</font></td>");
		}
		
		// Champion HP çarpanı kontrolü
		if (npc.isChampion() && (npc instanceof Attackable))
		{
			int championHp = 1;
			switch (((Attackable) npc).getChampionType())
			{
				case 1:
					championHp = Config.EASY_CHAMPION_HP;
					break;
				case 2:
					championHp = Config.CHAMPION_HP;
					break;
				case 3:
					championHp = Config.HARD_CHAMPION_HP;
					break;
				case 4:
					championHp = Config.VERY_HARD_CHAMPION_HP;
					break;
				case 5:
					championHp = Config.CATASTROPHIC_CHAMPION_HP;
					break;
				case 6:
					championHp = Config.BOSS_CHAMPION_HP;
					break;
			}
			
			if (championHp > 1)
			{
				finalHp *= championHp; // Champion çarpanını uygula
				hpModSb.append("<td width=65 valign=middle><font size=\"-2\" color=\"FF0000\">Champ: ×").append(championHp).append("</font></td>");
			}
		}
		
		// Debug için yaratık bilgilerini logla
		LOGGER.info("NPC View Debug - " + npc.getName() + " (ID: " + npc.getId() + "):");
		LOGGER.info("Base HP: " + baseHp);
		LOGGER.info("NPC Max HP: " + npc.getMaxHp());
		LOGGER.info("Final HP: " + finalHp);
		LOGGER.info("Current HP: " + npc.getCurrentHp());
		
		// Değerleri HTML'e yerleştir
		html.replace("%baseHp%", String.format("%,d", (int) baseHp));
		html.replace("%baseMp%", String.format("%,d", (int) baseMp));
		html.replace("%hpModifiers%", hpModSb.toString());
		html.replace("%mpModifiers%", mpModSb.toString());
		
		// HP/MP gauge ve değerler
		double currentHpRatio = npc.getCurrentHp() / npc.getMaxHp();
		long adjustedCurrentHp = (long) (finalHp * currentHpRatio);
		
		// MP için hesaplama
		double currentMpRatio = npc.getCurrentMp() / npc.getMaxMp();
		long adjustedCurrentMp = (long) (finalMp * currentMpRatio);
		
		// Gauge'leri güncelle
		html.replace("%hpGauge%", HtmlUtil.getHpGauge(250, adjustedCurrentHp, (long) finalHp, false));
		html.replace("%mpGauge%", HtmlUtil.getMpGauge(250, adjustedCurrentMp, (long) finalMp, false));
		
		// HP/MP Yüzdeleri
		html.replace("%hpPercent%", String.format("%.1f", (currentHpRatio * 100)));
		html.replace("%mpPercent%", String.format("%.1f", (currentMpRatio * 100)));
		
		// Respawn bilgisi
		final Spawn npcSpawn = npc.getSpawn();
		if ((npcSpawn == null) || (npcSpawn.getRespawnMinDelay() == 0))
		{
			html.replace("%respawn%", "None");
		}
		else
		{
			TimeUnit timeUnit = TimeUnit.MILLISECONDS;
			long min = Long.MAX_VALUE;
			for (TimeUnit tu : TimeUnit.values())
			{
				final long minTimeFromMillis = tu.convert(npcSpawn.getRespawnMinDelay(), TimeUnit.MILLISECONDS);
				final long maxTimeFromMillis = tu.convert(npcSpawn.getRespawnMaxDelay(), TimeUnit.MILLISECONDS);
				if ((TimeUnit.MILLISECONDS.convert(minTimeFromMillis, tu) == npcSpawn.getRespawnMinDelay()) && (TimeUnit.MILLISECONDS.convert(maxTimeFromMillis, tu) == npcSpawn.getRespawnMaxDelay()) && (min > minTimeFromMillis))
				{
					min = minTimeFromMillis;
					timeUnit = tu;
				}
			}
			final long minRespawnDelay = timeUnit.convert(npcSpawn.getRespawnMinDelay(), TimeUnit.MILLISECONDS);
			final long maxRespawnDelay = timeUnit.convert(npcSpawn.getRespawnMaxDelay(), TimeUnit.MILLISECONDS);
			final String timeUnitName = timeUnit.name().charAt(0) + timeUnit.name().toLowerCase().substring(1);
			if (npcSpawn.hasRespawnRandom())
			{
				html.replace("%respawn%", minRespawnDelay + "-" + maxRespawnDelay + " " + timeUnitName);
			}
			else
			{
				html.replace("%respawn%", minRespawnDelay + " " + timeUnitName);
			}
		}
		
		// Basic Info
		html.replace("%level%", String.valueOf(npc.getLevel()));
		html.replace("%type%", npc.getTemplate().getType());
		html.replace("%race%", npc.getTemplate().getRace().toString());
		if (npc instanceof Attackable)
		{
			html.replace("%aggroRange%", String.valueOf(((Attackable) npc).getAggroRange()));
		}
		else
		{
			html.replace("%aggroRange%", "N/A");
		}
		
		// Base Stats
		html.replace("%str%", String.valueOf(npc.getStat().getSTR()));
		html.replace("%dex%", String.valueOf(npc.getStat().getDEX()));
		html.replace("%con%", String.valueOf(npc.getStat().getCON()));
		html.replace("%int%", String.valueOf(npc.getStat().getINT()));
		html.replace("%wit%", String.valueOf(npc.getStat().getWIT()));
		html.replace("%men%", String.valueOf(npc.getStat().getMEN()));
		
		// Combat Stats
		html.replace("%atktype%", Util.capitalizeFirst(npc.getAttackType().name().toLowerCase()));
		html.replace("%atkrange%", String.valueOf(npc.getStat().getPhysicalAttackRange()));
		html.replace("%patk%", String.valueOf((int) npc.getPAtk(player)));
		html.replace("%pdef%", String.valueOf((int) npc.getPDef(player)));
		html.replace("%matk%", String.valueOf((int) npc.getMAtk(player, null)));
		html.replace("%mdef%", String.valueOf((int) npc.getMDef(player, null)));
		html.replace("%atkspd%", String.valueOf(npc.getPAtkSpd()));
		html.replace("%castspd%", String.valueOf(npc.getMAtkSpd()));
		html.replace("%critrate%", String.valueOf(npc.getStat().getCriticalHit(player, null)));
		html.replace("%evasion%", String.valueOf(npc.getEvasionRate(player)));
		html.replace("%accuracy%", String.valueOf(npc.getStat().getAccuracy()));
		html.replace("%speed%", String.valueOf((int) npc.getStat().getMoveSpeed()));
		
		// Movement
		html.replace("%runSpeed%", String.valueOf((int) npc.getStat().getRunSpeed()));
		html.replace("%walkSpeed%", String.valueOf((int) npc.getStat().getWalkSpeed()));
		
		// Movement Stats
		html.replace("%runspd%", String.valueOf((int) npc.getStat().getRunSpeed()));
		html.replace("%walkspd%", String.valueOf((int) npc.getStat().getWalkSpeed()));
		html.replace("%movespd%", String.valueOf((int) npc.getStat().getMoveSpeed()));
		
		// Additional Stats
		html.replace("%maxhp%", String.valueOf(npc.getMaxHp()));
		html.replace("%maxmp%", String.valueOf(npc.getMaxMp()));
		html.replace("%patkrange%", String.valueOf(npc.getStat().getPhysicalAttackRange()));
		html.replace("%atktype%", String.valueOf(npc.getAttackType()));
		
		// Attributes
		html.replace("%attributeatktype%", Elementals.getElementName(npc.getStat().getAttackElement()));
		html.replace("%attributeatkvalue%", String.valueOf(npc.getStat().getAttackElementValue(npc.getStat().getAttackElement())));
		html.replace("%attributefire%", String.valueOf(npc.getStat().getDefenseElementValue(Elementals.FIRE)));
		html.replace("%attributewater%", String.valueOf(npc.getStat().getDefenseElementValue(Elementals.WATER)));
		html.replace("%attributewind%", String.valueOf(npc.getStat().getDefenseElementValue(Elementals.WIND)));
		html.replace("%attributeearth%", String.valueOf(npc.getStat().getDefenseElementValue(Elementals.EARTH)));
		html.replace("%attributedark%", String.valueOf(npc.getStat().getDefenseElementValue(Elementals.DARK)));
		html.replace("%attributeholy%", String.valueOf(npc.getStat().getDefenseElementValue(Elementals.HOLY)));
		
		html.replace("%dropListButtons%", getDropListButtons(npc));
		html.replace("%npcObjectId%", String.valueOf(npc.getObjectId()));
		player.sendPacket(html);
	}
	
	private static String getDropListButtons(Npc npc)
	{
		final StringBuilder sb = new StringBuilder();
		final List<DropGroupHolder> dropListGroups = npc.getTemplate().getDropGroups();
		final List<DropHolder> dropListDeath = npc.getTemplate().getDropList();
		final List<DropHolder> dropListSpoil = npc.getTemplate().getSpoilList();
		if ((dropListGroups != null) || (dropListDeath != null) || (dropListSpoil != null))
		{
			sb.append("<table width=275 cellpadding=0 cellspacing=0><tr>");
			if ((dropListGroups != null) || (dropListDeath != null))
			{
				sb.append("<td align=center><button value=\"Show Drop\" width=100 height=25 action=\"bypass NpcViewMod dropList DROP " + npc.getObjectId() + "\" back=\"L2UI_CT1.Button_DF_Calculator_Down\" fore=\"L2UI_CT1.Button_DF_Calculator\"></td>");
			}
			
			if (dropListSpoil != null)
			{
				sb.append("<td align=center><button value=\"Show Spoil\" width=100 height=25 action=\"bypass NpcViewMod dropList SPOIL " + npc.getObjectId() + "\" back=\"L2UI_CT1.Button_DF_Calculator_Down\" fore=\"L2UI_CT1.Button_DF_Calculator\"></td>");
			}
			
			sb.append("</tr></table>");
		}
		return sb.toString();
	}
	
	private void sendNpcDropList(Player player, Npc npc, DropType dropType, int pageValue)
	{
		List<DropHolder> dropList = null;
		if (dropType == DropType.SPOIL)
		{
			dropList = new ArrayList<>(npc.getTemplate().getSpoilList());
		}
		else
		{
			final List<DropHolder> drops = npc.getTemplate().getDropList();
			if (drops != null)
			{
				dropList = new ArrayList<>(drops);
			}
			final List<DropGroupHolder> dropGroups = npc.getTemplate().getDropGroups();
			if (dropGroups != null)
			{
				if (dropList == null)
				{
					dropList = new ArrayList<>();
				}
				for (DropGroupHolder dropGroup : dropGroups)
				{
					final double chance = dropGroup.getChance() / 100;
					for (DropHolder dropHolder : dropGroup.getDropList())
					{
						dropList.add(new DropHolder(dropHolder.getDropType(), dropHolder.getItemId(), dropHolder.getMin(), dropHolder.getMax(), dropHolder.getChance() * chance));
					}
				}
			}
		}
		if (dropList == null)
		{
			return;
		}
		
		Collections.sort(dropList, (d1, d2) -> Integer.valueOf(d1.getItemId()).compareTo(Integer.valueOf(d2.getItemId())));
		
		int pages = dropList.size() / DROP_LIST_ITEMS_PER_PAGE;
		if ((DROP_LIST_ITEMS_PER_PAGE * pages) < dropList.size())
		{
			pages++;
		}
		
		final StringBuilder pagesSb = new StringBuilder();
		if (pages > 1)
		{
			pagesSb.append("<table><tr>");
			for (int i = 0; i < pages; i++)
			{
				pagesSb.append("<td align=center><button value=\"" + (i + 1) + "\" width=20 height=20 action=\"bypass NpcViewMod dropList " + dropType + " " + npc.getObjectId() + " " + i + "\" back=\"L2UI_CT1.Button_DF_Calculator_Down\" fore=\"L2UI_CT1.Button_DF_Calculator\"></td>");
			}
			pagesSb.append("</tr></table>");
		}
		
		int page = pageValue;
		if (page >= pages)
		{
			page = pages - 1;
		}
		
		final int start = page > 0 ? page * DROP_LIST_ITEMS_PER_PAGE : 0;
		int end = (page * DROP_LIST_ITEMS_PER_PAGE) + DROP_LIST_ITEMS_PER_PAGE;
		if (end > dropList.size())
		{
			end = dropList.size();
		}
		
		final DecimalFormat amountFormat = new DecimalFormat("#,###");
		final DecimalFormat chanceFormat = new DecimalFormat("0.00##");
		int leftHeight = 0;
		int rightHeight = 0;
		final double dropAmountAdenaEffectBonus = player.getStat().getBonusDropAdenaMultiplier();
		final double dropAmountEffectBonus = player.getStat().getBonusDropAmountMultiplier();
		final double dropRateEffectBonus = player.getStat().getBonusDropRateMultiplier();
		final double spoilRateEffectBonus = player.getStat().getBonusSpoilRateMultiplier();
		final StringBuilder leftSb = new StringBuilder();
		final StringBuilder rightSb = new StringBuilder();
		String limitReachedMsg = "";
		for (int i = start; i < end; i++)
		{
			final StringBuilder sb = new StringBuilder();
			final int height = 64;
			final DropHolder dropItem = dropList.get(i);
			final ItemTemplate item = ItemData.getInstance().getTemplate(dropItem.getItemId());
			
			// real time server rate calculations
			double rateChance = 1;
			double rateAmount = 1;
			
			if (dropType == DropType.DROP)
			{
				// Önce item-specific rate'leri kontrol et
				boolean hasSpecificAmountRate = false;
				
				// Chance rates
				if (Config.RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId()) != null)
				{
					rateChance = Config.RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId());
				}
				else if (item.hasExImmediateEffect())
				{
					rateChance = Config.RATE_HERB_DROP_CHANCE_MULTIPLIER;
				}
				else if (npc.isRaid())
				{
					rateChance = Config.RATE_RAID_DROP_CHANCE_MULTIPLIER;
				}
				else
				{
					rateChance = Config.RATE_DEATH_DROP_CHANCE_MULTIPLIER;
				}
				
				// Amount rates
				if (Config.RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId()) != null)
				{
					rateAmount = Config.RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId());
					hasSpecificAmountRate = true;
				}
				else if (item.hasExImmediateEffect())
				{
					rateAmount = Config.RATE_HERB_DROP_AMOUNT_MULTIPLIER;
				}
				else if (npc.isRaid())
				{
					rateAmount = Config.RATE_RAID_DROP_AMOUNT_MULTIPLIER;
				}
				else
				{
					rateAmount = Config.RATE_DEATH_DROP_AMOUNT_MULTIPLIER;
				}
				
				// Champion bonusları
				if (npc.isChampion())
				{
					switch (((Attackable) npc).getChampionType())
					{
						case 1: // Easy Champion
							rateChance *= Config.EASY_CHAMPION_REWARDS_CHANCE;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.EASY_CHAMPION_REWARDS_AMOUNT;
							}
							break;
						case 2: // Normal Champion
							rateChance *= Config.CHAMPION_REWARDS_CHANCE;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.CHAMPION_REWARDS_AMOUNT;
							}
							break;
						case 3: // Hard Champion
							rateChance *= Config.HARD_CHAMPION_REWARDS_CHANCE;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.HARD_CHAMPION_REWARDS_AMOUNT;
							}
							break;
						case 4: // Very Hard Champion
							rateChance *= Config.VERY_HARD_CHAMPION_REWARDS_CHANCE;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.VERY_HARD_CHAMPION_REWARDS_AMOUNT;
							}
							break;
						case 5: // Catastrophic Champion
							rateChance *= Config.CATASTROPHIC_CHAMPION_REWARDS_CHANCE;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.CATASTROPHIC_CHAMPION_REWARDS_AMOUNT;
							}
							break;
						case 6: // Boss Champion
							rateChance *= Config.BOSS_CHAMPION_REWARDS_CHANCE;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.BOSS_CHAMPION_REWARDS_AMOUNT;
							}
							break;
					}
				}
				
				// Adena için her zaman champion bonuslarını uygula
				if (dropItem.getItemId() == Inventory.ADENA_ID && npc.isChampion())
				{
					switch (((Attackable) npc).getChampionType())
					{
						case 1: // Easy Champion
							rateChance *= Config.EASY_CHAMPION_ADENAS_REWARDS_CHANCE;
							rateAmount *= Config.EASY_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 2: // Normal Champion
							rateChance *= Config.CHAMPION_ADENAS_REWARDS_CHANCE;
							rateAmount *= Config.CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 3: // Hard Champion
							rateChance *= Config.HARD_CHAMPION_ADENAS_REWARDS_CHANCE;
							rateAmount *= Config.HARD_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 4: // Very Hard Champion
							rateChance *= Config.VERY_HARD_CHAMPION_ADENAS_REWARDS_CHANCE;
							rateAmount *= Config.VERY_HARD_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 5: // Catastrophic Champion
							rateChance *= Config.CATASTROPHIC_CHAMPION_ADENAS_REWARDS_CHANCE;
							rateAmount *= Config.CATASTROPHIC_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 6: // Boss Champion
							rateChance *= Config.BOSS_CHAMPION_ADENAS_REWARDS_CHANCE;
							rateAmount *= Config.BOSS_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
					}
				}
			}
			
			// Monster Hunter bonusları
			double mhDropBonus = 0;
			double mhDropAmountBonus = 0;
			double mhSpoilBonus = 0;
			double mhSpoilAmountBonus = 0;
			
			// Monster Hunter bonuslarını al
			try
			{
				Quest quest = QuestManager.getInstance().getQuest("Q00801_MonsterHunterSpecialization");
				if (quest != null)
				{
					if (quest.getClass().getSimpleName().equals("Q00801_MonsterHunterSpecialization"))
					{
						Method getMonsterHunterBonuses = quest.getClass().getMethod("getMonsterHunterBonuses", Player.class, int.class);
						Object bonuses = getMonsterHunterBonuses.invoke(quest, player, npc.getId());
						
						if (bonuses != null)
						{
							Class<?> bonusClass = bonuses.getClass();
							if (dropType == DropType.SPOIL)
							{
								mhSpoilBonus = ((Double) bonusClass.getField("spoilBonus").get(bonuses)).doubleValue();
								mhSpoilAmountBonus = ((Double) bonusClass.getField("spoilAmountBonus").get(bonuses)).doubleValue();
							}
							else
							{
								mhDropBonus = ((Double) bonusClass.getField("dropBonus").get(bonuses)).doubleValue();
								mhDropAmountBonus = ((Double) bonusClass.getField("dropAmountBonus").get(bonuses)).doubleValue();
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				// Sessizce devam et
			}
			if (dropType == DropType.SPOIL)
			{
				// Önce item-specific rate'leri kontrol et
				boolean hasSpecificAmountRate = false;
				
				// Chance rates
				if (Config.RATE_SPOIL_CHANCE_BY_ID.get(dropItem.getItemId()) != null)
				{
					rateChance = Config.RATE_SPOIL_CHANCE_BY_ID.get(dropItem.getItemId());
				}
				else
				{
					rateChance = Config.RATE_SPOIL_DROP_CHANCE_MULTIPLIER;
				}
				
				// Amount rates
				if (Config.RATE_SPOIL_AMOUNT_BY_ID.get(dropItem.getItemId()) != null)
				{
					rateAmount = Config.RATE_SPOIL_AMOUNT_BY_ID.get(dropItem.getItemId());
					hasSpecificAmountRate = true;
				}
				else
				{
					rateAmount = Config.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER;
				}
				
				// Champion bonusları
				if (npc.isChampion())
				{
					switch (((Attackable) npc).getChampionType())
					{
						case 1: // Easy Champion
							rateChance *= Config.EASY_CHAMPION_REWARDS_CHANCE_SPOIL;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.EASY_CHAMPION_REWARDS_AMOUNT_SPOIL;
							}
							break;
						case 2: // Normal Champion
							rateChance *= Config.CHAMPION_REWARDS_CHANCE_SPOIL;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.CHAMPION_REWARDS_AMOUNT_SPOIL;
							}
							break;
						case 3: // Hard Champion
							rateChance *= Config.HARD_CHAMPION_REWARDS_CHANCE_SPOIL;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.HARD_CHAMPION_REWARDS_AMOUNT_SPOIL;
							}
							break;
						case 4: // Very Hard Champion
							rateChance *= Config.VERY_HARD_CHAMPION_REWARDS_CHANCE_SPOIL;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.VERY_HARD_CHAMPION_REWARDS_AMOUNT_SPOIL;
							}
							break;
						case 5: // Catastrophic Champion
							rateChance *= Config.CATASTROPHIC_CHAMPION_REWARDS_CHANCE_SPOIL;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.CATASTROPHIC_CHAMPION_REWARDS_AMOUNT_SPOIL;
							}
							break;
						case 6: // Boss Champion
							rateChance *= Config.BOSS_CHAMPION_REWARDS_CHANCE_SPOIL;
							if (!hasSpecificAmountRate)
							{
								rateAmount *= Config.BOSS_CHAMPION_REWARDS_AMOUNT_SPOIL;
							}
							break;
					}
				}
				
				// Monster Hunter bonusları
				rateChance *= (1.0 + mhSpoilBonus);
				if (!hasSpecificAmountRate)
				{
					rateAmount *= (1.0 + mhSpoilAmountBonus);
				}
				
				// Premium kontrolleri
				if (Config.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus())
				{
					if (dropType == DropType.SPOIL)
					{
						rateChance *= Config.PREMIUM_RATE_SPOIL_CHANCE;
						if (!hasSpecificAmountRate) // Spoil için amount rate kontrolü
						{
							rateAmount *= Config.PREMIUM_RATE_SPOIL_AMOUNT; // PremiumRateSpoilAmount kullanılıyor
						}
					}
					else // Normal drop için
					{
						rateChance *= Config.PREMIUM_RATE_DROP_CHANCE;
						if (!hasSpecificAmountRate)
						{
							rateAmount *= Config.PREMIUM_RATE_DROP_AMOUNT;
						}
					}
				}
				
				// Bonus efektler
				rateChance *= spoilRateEffectBonus;
			}
			else
			{
				// Monster Hunter drop bonusları
				rateChance *= (1.0 + mhDropBonus);
				rateAmount *= (1.0 + mhDropAmountBonus);
				
				if (Config.RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId()) != null)
				{
					rateChance *= Config.RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId());
				}
				else if (item.hasExImmediateEffect())
				{
					rateChance *= Config.RATE_HERB_DROP_CHANCE_MULTIPLIER;
				}
				else if (npc.isRaid())
				{
					rateChance *= Config.RATE_RAID_DROP_CHANCE_MULTIPLIER;
				}
				else
				{
					rateChance *= Config.RATE_DEATH_DROP_CHANCE_MULTIPLIER;
				}
				
				if (Config.RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId()) != null)
				{
					rateAmount *= Config.RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId());
				}
				else if (item.hasExImmediateEffect())
				{
					rateAmount *= Config.RATE_HERB_DROP_AMOUNT_MULTIPLIER;
				}
				else if (npc.isRaid())
				{
					rateAmount *= Config.RATE_RAID_DROP_AMOUNT_MULTIPLIER;
				}
				else
				{
					rateAmount *= Config.RATE_DEATH_DROP_AMOUNT_MULTIPLIER;
				}
				
				// also check premium rates if available
				if (Config.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus())
				{
					if (Config.PREMIUM_RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId()) != null)
					{
						rateChance *= Config.PREMIUM_RATE_DROP_CHANCE_BY_ID.get(dropItem.getItemId());
					}
					else if (item.hasExImmediateEffect())
					{
						// TODO: Premium herb chance? :)
					}
					else if (npc.isRaid())
					{
						// TODO: Premium raid chance? :)
					}
					else
					{
						rateChance *= Config.PREMIUM_RATE_DROP_CHANCE;
					}
					
					if (Config.PREMIUM_RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId()) != null)
					{
						rateAmount *= Config.PREMIUM_RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId());
					}
					else if (item.hasExImmediateEffect())
					{
						// TODO: Premium herb amount? :)
					}
					else if (npc.isRaid())
					{
						// TODO: Premium raid amount? :)
					}
					else
					{
						rateAmount *= Config.PREMIUM_RATE_DROP_AMOUNT;
					}
				}
				
				// bonus drop amount effect
				rateAmount *= dropAmountEffectBonus;
				if (item.getId() == Inventory.ADENA_ID)
				{
					rateAmount *= dropAmountAdenaEffectBonus;
				}
				// bonus drop rate effect
				rateChance *= dropRateEffectBonus;
			}
			sb.append("<table width=370 height=100 cellpadding=2 cellspacing=2 background=\"L2UI_CT1.Windows.Windows_DF_TooltipBG\">");
			sb.append("<tr>");
			sb.append("<td width=32 valign=top>");
			sb.append("<img src=\"" + (item.getIcon() == null ? "icon.etc_question_mark_i00" : item.getIcon()) + "\" width=32 height=32>");
			sb.append("</td>");
			sb.append("<td width=330>");
			sb.append("<table width=330 cellpadding=0 cellspacing=0>");
			sb.append("<tr><td align=center><font name=\"hs9\" color=\"CD9000\">");
			sb.append(item.getName());
			sb.append("</font></td></tr>");
			
			// hasSpecificAmountRate değişkenini tanımla
			boolean hasSpecificAmountRate = false;
			if (dropType == DropType.SPOIL)
			{
				hasSpecificAmountRate = Config.RATE_SPOIL_AMOUNT_BY_ID.get(dropItem.getItemId()) != null;
			}
			else
			{
				// Adena için özel kontrol
				if (dropItem.getItemId() == Inventory.ADENA_ID)
				{
					hasSpecificAmountRate = false; // Adena için amount rate'i göstersin
				}
				else
				{
					hasSpecificAmountRate = Config.RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId()) != null;
				}
			}
			// Champion bonus bilgisini göster
			if (npc.isChampion())
			{
				if (dropType == DropType.SPOIL)
				{
					switch (((Attackable) npc).getChampionType())
					{
						case 1: // Easy
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (dropItem.getItemId() == Inventory.ADENA_ID)
							{
								sb.append("Amount: x").append(String.format("%.0f", Config.EASY_CHAMPION_ADENAS_REWARDS_AMOUNT));
							}
							else if (!hasSpecificAmountRate)
							{
								sb.append("Amount: +").append(String.format("%.0f", (Config.EASY_CHAMPION_REWARDS_AMOUNT_SPOIL - 1) * 100)).append("%");
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.EASY_CHAMPION_REWARDS_CHANCE_SPOIL - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 2: // Normal
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (dropItem.getItemId() == Inventory.ADENA_ID)
							{
								sb.append("Amount: x").append(String.format("%.0f", Config.CHAMPION_ADENAS_REWARDS_AMOUNT));
							}
							else if (!hasSpecificAmountRate)
							{
								sb.append("Amount: +").append(String.format("%.0f", (Config.CHAMPION_REWARDS_AMOUNT_SPOIL - 1) * 100)).append("%");
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.CHAMPION_REWARDS_CHANCE_SPOIL - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 3: // Hard
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (dropItem.getItemId() == Inventory.ADENA_ID)
							{
								sb.append("Amount: x").append(String.format("%.0f", Config.HARD_CHAMPION_ADENAS_REWARDS_AMOUNT));
							}
							else if (!hasSpecificAmountRate)
							{
								sb.append("Amount: +").append(String.format("%.0f", (Config.HARD_CHAMPION_REWARDS_AMOUNT_SPOIL - 1) * 100)).append("%");
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.HARD_CHAMPION_REWARDS_CHANCE_SPOIL - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 4: // Very Hard
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (dropItem.getItemId() == Inventory.ADENA_ID)
							{
								sb.append("Amount: x").append(String.format("%.0f", Config.VERY_HARD_CHAMPION_ADENAS_REWARDS_AMOUNT));
							}
							else if (!hasSpecificAmountRate)
							{
								sb.append("Amount: +").append(String.format("%.0f", (Config.VERY_HARD_CHAMPION_REWARDS_AMOUNT_SPOIL - 1) * 100)).append("%");
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.VERY_HARD_CHAMPION_REWARDS_CHANCE_SPOIL - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 5: // Catastrophic
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (dropItem.getItemId() == Inventory.ADENA_ID)
							{
								sb.append("Amount: x").append(String.format("%.0f", Config.CATASTROPHIC_CHAMPION_ADENAS_REWARDS_AMOUNT));
							}
							else if (!hasSpecificAmountRate)
							{
								sb.append("Amount: +").append(String.format("%.0f", (Config.CATASTROPHIC_CHAMPION_REWARDS_AMOUNT_SPOIL - 1) * 100)).append("%");
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.CATASTROPHIC_CHAMPION_REWARDS_CHANCE_SPOIL - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 6: // Boss
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (dropItem.getItemId() == Inventory.ADENA_ID)
							{
								sb.append("Amount: x").append(String.format("%.0f", Config.BOSS_CHAMPION_ADENAS_REWARDS_AMOUNT));
							}
							else if (!hasSpecificAmountRate)
							{
								sb.append("Amount: +").append(String.format("%.0f", (Config.BOSS_CHAMPION_REWARDS_AMOUNT_SPOIL - 1) * 100)).append("%");
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.BOSS_CHAMPION_REWARDS_CHANCE_SPOIL - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
					}
				}
				else // DROP
				{
					switch (((Attackable) npc).getChampionType())
					{
						case 1: // Easy
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (!hasSpecificAmountRate)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.0f", Config.EASY_CHAMPION_ADENAS_REWARDS_AMOUNT));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", (Config.EASY_CHAMPION_REWARDS_AMOUNT - 1) * 100)).append("%");
								}
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.EASY_CHAMPION_REWARDS_CHANCE - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 2: // Normal
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (!hasSpecificAmountRate)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.0f", Config.CHAMPION_ADENAS_REWARDS_AMOUNT));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", (Config.CHAMPION_REWARDS_AMOUNT - 1) * 100)).append("%");
								}
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.CHAMPION_REWARDS_CHANCE - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 3: // Hard
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (!hasSpecificAmountRate)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.0f", Config.HARD_CHAMPION_ADENAS_REWARDS_AMOUNT));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", (Config.HARD_CHAMPION_REWARDS_AMOUNT - 1) * 100)).append("%");
								}
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.HARD_CHAMPION_REWARDS_CHANCE - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 4: // Very Hard
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (!hasSpecificAmountRate)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.0f", Config.VERY_HARD_CHAMPION_ADENAS_REWARDS_AMOUNT));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", (Config.VERY_HARD_CHAMPION_REWARDS_AMOUNT - 1) * 100)).append("%");
								}
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.VERY_HARD_CHAMPION_REWARDS_CHANCE - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 5: // Catastrophic
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (!hasSpecificAmountRate)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.0f", Config.CATASTROPHIC_CHAMPION_ADENAS_REWARDS_AMOUNT));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", (Config.CATASTROPHIC_CHAMPION_REWARDS_AMOUNT - 1) * 100)).append("%");
								}
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.CATASTROPHIC_CHAMPION_REWARDS_CHANCE - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
						case 6: // Boss
							sb.append("<tr><td>");
							sb.append("<table width=330>");
							sb.append("<tr>");
							sb.append("<td width=100 align=right><font color=\"FF0000\">Champion Bonus:</font></td>");
							sb.append("<td width=200 align=center><font color=\"FFFF00\">");
							if (!hasSpecificAmountRate)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.0f", Config.BOSS_CHAMPION_ADENAS_REWARDS_AMOUNT));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", (Config.BOSS_CHAMPION_REWARDS_AMOUNT - 1) * 100)).append("%");
								}
							}
							sb.append(" Chance: +").append(String.format("%.0f", (Config.BOSS_CHAMPION_REWARDS_CHANCE - 1) * 100)).append("%");
							sb.append("</font></td></tr></table></td></tr>");
							break;
					}
				}
			}
			
			// İkinci try-catch bloğu
			try
			{
				Q00801_MonsterHunterSpecialization mhQuest = Q00801_MonsterHunterSpecialization.getInstance();
				if (mhQuest != null)
				{
					Q00801_MonsterHunterSpecialization.BonusInfo mhBonuses = mhQuest.getMonsterHunterBonuses(player, npc.getId());
					if (mhBonuses != null)
					{
						if (dropType == DropType.SPOIL)
						{
							mhSpoilBonus = mhBonuses.spoilBonus;
							mhSpoilAmountBonus = mhBonuses.spoilAmountBonus;
						}
						else
						{
							mhDropBonus = mhBonuses.dropBonus;
							mhDropAmountBonus = mhBonuses.dropAmountBonus;
						}
					}
				}
			}
			catch (Exception e)
			{
				// Sessizce devam et
			}
			
			// Bonus bilgisini göster
			if ((dropType == DropType.SPOIL && (mhSpoilBonus > 0 || mhSpoilAmountBonus > 0)) || (dropType != DropType.SPOIL && (mhDropBonus > 0 || mhDropAmountBonus > 0)))
			{
				// Stage kontrolü
				boolean hasStage = false;
				
				try (Connection con = DatabaseFactory.getConnection();
					PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM monster_kill_tracker WHERE char_id=? AND quest_id=801 AND monster_id=? AND (reward1_claimed=1 OR reward2_claimed=1 OR reward3_claimed=1 OR reward4_claimed=1 OR reward5_claimed=1 OR reward6_claimed=1 OR reward7_claimed=1 OR reward8_claimed=1 OR reward9_claimed=1 OR reward10_claimed=1)"))
				{
					ps.setInt(1, player.getObjectId());
					ps.setInt(2, npc.getId());
					
					try (ResultSet rs = ps.executeQuery())
					{
						if (rs.next())
						{
							hasStage = rs.getInt(1) > 0;
						}
					}
				}
				catch (Exception e)
				{
					LOGGER.warning("Error checking monster hunter stages: " + e.getMessage());
				}
				
				sb.append("<tr><td>");
				sb.append("<table width=330>");
				sb.append("<tr>");
				sb.append("<td width=100 align=right><font color=\"00FF00\">Hunting Bonus:</font></td>");
				sb.append("<td width=200 align=center><font color=\"FFFF00\">");
				
				if (hasStage)
				{
					if (dropType == DropType.SPOIL)
					{
						if (dropItem.getItemId() == Inventory.ADENA_ID || !Config.RATE_SPOIL_AMOUNT_BY_ID.containsKey(dropItem.getItemId()))
						{
							if (mhSpoilAmountBonus > 0)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.1f", 1.0 + mhSpoilAmountBonus));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", mhSpoilAmountBonus * 100)).append("%");
								}
							}
						}
						if (mhSpoilBonus > 0)
						{
							if (mhSpoilAmountBonus > 0 && (dropItem.getItemId() == Inventory.ADENA_ID || !Config.RATE_SPOIL_AMOUNT_BY_ID.containsKey(dropItem.getItemId())))
							{
								sb.append(" ");
							}
							sb.append("Chance: +").append(String.format("%.0f", mhSpoilBonus * 100)).append("%");
						}
					}
					else
					{
						if (dropItem.getItemId() == Inventory.ADENA_ID || !Config.RATE_DROP_AMOUNT_BY_ID.containsKey(dropItem.getItemId()))
						{
							if (mhDropAmountBonus > 0)
							{
								if (dropItem.getItemId() == Inventory.ADENA_ID)
								{
									sb.append("Amount: x").append(String.format("%.1f", 1.0 + mhDropAmountBonus));
								}
								else
								{
									sb.append("Amount: +").append(String.format("%.0f", mhDropAmountBonus * 100)).append("%");
								}
							}
						}
						if (mhDropBonus > 0)
						{
							if (mhDropAmountBonus > 0 && (dropItem.getItemId() == Inventory.ADENA_ID || !Config.RATE_DROP_AMOUNT_BY_ID.containsKey(dropItem.getItemId())))
							{
								sb.append(" ");
							}
							sb.append("Chance: +").append(String.format("%.0f", mhDropBonus * 100)).append("%");
						}
					}
				}
				else
				{
					// Stage 0 için +0% göster
					if (dropType == DropType.SPOIL)
					{
						sb.append("Amount: +0% Chance: +0%");
					}
					else
					{
						sb.append("Amount: +0% Chance: +0%");
					}
				}
				
				sb.append("</font></td></tr></table></td></tr>");
			}
			
			// Amount ve Chance bilgileri
			sb.append("<tr><td>");
			sb.append("<table width=330>");
			sb.append("<tr>");
			sb.append("<td width=100 align=right><font color=\"LEVEL\">Total Amount:</font></td>");
			sb.append("<td width=200 align=center>");
			double finalRateAmount = 1.0;
			
			// Base rate
			if (dropItem.getItemId() == Inventory.ADENA_ID)
			{
				// Adena için özel kontrol
				if (Config.RATE_DROP_AMOUNT_BY_ID.containsKey(Inventory.ADENA_ID))
				{
					finalRateAmount *= Config.RATE_DROP_AMOUNT_BY_ID.get(Inventory.ADENA_ID);
					System.out.println("Adena amount rate from config: " + Config.RATE_DROP_AMOUNT_BY_ID.get(Inventory.ADENA_ID));
					System.out.println("Current finalRateAmount: " + finalRateAmount);
				}
				else
				{
					finalRateAmount *= Config.RATE_DEATH_DROP_AMOUNT_MULTIPLIER;
				}
				
				// Champion adena bonusları
				if (npc.isChampion())
				{
					switch (((Attackable) npc).getChampionType())
					{
						case 1: // Easy Champion
							finalRateAmount *= Config.EASY_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 2: // Normal Champion
							finalRateAmount *= Config.CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 3: // Hard Champion
							finalRateAmount *= Config.HARD_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 4: // Very Hard Champion
							finalRateAmount *= Config.VERY_HARD_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 5: // Catastrophic Champion
							finalRateAmount *= Config.CATASTROPHIC_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
						case 6: // Boss Champion
							finalRateAmount *= Config.BOSS_CHAMPION_ADENAS_REWARDS_AMOUNT;
							break;
					}
					System.out.println("After champion bonus: " + finalRateAmount);
				}
			}
			else
			{
				// Spoil ve normal drop için ayrı hesaplama
				if (dropType == DropType.SPOIL)
				{
					// Spoil için base rate
					finalRateAmount *= Config.RATE_SPOIL_DROP_AMOUNT_MULTIPLIER;
					
					// Spoil için champion bonusları
					if (npc.isChampion())
					{
						switch (((Attackable) npc).getChampionType())
						{
							case 1: // Easy Champion
								finalRateAmount *= Config.EASY_CHAMPION_REWARDS_AMOUNT_SPOIL;
								break;
							case 2: // Normal Champion
								finalRateAmount *= Config.CHAMPION_REWARDS_AMOUNT_SPOIL;
								break;
							case 3: // Hard Champion
								finalRateAmount *= Config.HARD_CHAMPION_REWARDS_AMOUNT_SPOIL;
								break;
							case 4: // Very Hard Champion
								finalRateAmount *= Config.VERY_HARD_CHAMPION_REWARDS_AMOUNT_SPOIL;
								break;
							case 5: // Catastrophic Champion
								finalRateAmount *= Config.CATASTROPHIC_CHAMPION_REWARDS_AMOUNT_SPOIL;
								break;
							case 6: // Boss Champion
								finalRateAmount *= Config.BOSS_CHAMPION_REWARDS_AMOUNT_SPOIL;
								break;
						}
					}
				}
				else
				{
					// Normal drop için base rate
					finalRateAmount *= Config.RATE_DEATH_DROP_AMOUNT_MULTIPLIER;
					
					// Normal drop için champion bonusları
					if (npc.isChampion())
					{
						switch (((Attackable) npc).getChampionType())
						{
							case 1: // Easy Champion
								finalRateAmount *= Config.EASY_CHAMPION_REWARDS_AMOUNT;
								break;
							case 2: // Normal Champion
								finalRateAmount *= Config.CHAMPION_REWARDS_AMOUNT;
								break;
							case 3: // Hard Champion
								finalRateAmount *= Config.HARD_CHAMPION_REWARDS_AMOUNT;
								break;
							case 4: // Very Hard Champion
								finalRateAmount *= Config.VERY_HARD_CHAMPION_REWARDS_AMOUNT;
								break;
							case 5: // Catastrophic Champion
								finalRateAmount *= Config.CATASTROPHIC_CHAMPION_REWARDS_AMOUNT;
								break;
							case 6: // Boss Champion
								finalRateAmount *= Config.BOSS_CHAMPION_REWARDS_AMOUNT;
								break;
						}
					}
				}
			}
			
			// Premium rate - Spoil ve Drop için ayrı kontrol
			if (Config.PREMIUM_SYSTEM_ENABLED && player.hasPremiumStatus())
			{
				if (dropType == DropType.SPOIL)
				{
					finalRateAmount *= Config.PREMIUM_RATE_SPOIL_AMOUNT;
				}
				else
				{
					finalRateAmount *= Config.PREMIUM_RATE_DROP_AMOUNT;
				}
			}
			
			// Monster Hunter bonus (quest'ten gelen değer)
			if (dropType == DropType.SPOIL && mhSpoilAmountBonus > 0)
			{
				finalRateAmount *= (1.0 + mhSpoilAmountBonus);
			}
			else if (dropType != DropType.SPOIL && mhDropAmountBonus > 0)
			{
				finalRateAmount *= (1.0 + mhDropAmountBonus);
			}
			
			// Override item kontrolü (Adena hariç diğer itemler için)
			if (dropItem.getItemId() != Inventory.ADENA_ID)
			{
				if (dropType == DropType.SPOIL && Config.RATE_SPOIL_AMOUNT_BY_ID.containsKey(dropItem.getItemId()))
				{
					finalRateAmount = Config.RATE_SPOIL_AMOUNT_BY_ID.get(dropItem.getItemId());
				}
				else if (dropType != DropType.SPOIL && Config.RATE_DROP_AMOUNT_BY_ID.containsKey(dropItem.getItemId()))
				{
					finalRateAmount = Config.RATE_DROP_AMOUNT_BY_ID.get(dropItem.getItemId());
				}
			}
			
			final long min = (long) (dropItem.getMin() * finalRateAmount);
			final long max = (long) (dropItem.getMax() * finalRateAmount);
			if (min == max)
			{
				sb.append(amountFormat.format(min));
			}
			else
			{
				sb.append(amountFormat.format(min));
				sb.append(" - ");
				sb.append(amountFormat.format(max));
			}
			
			sb.append("</td></tr>");
			sb.append("<tr>");
			sb.append("<td width=100 align=right><font color=\"LEVEL\">Total Chance:</font></td>");
			sb.append("<td width=200 align=center>");
			sb.append(chanceFormat.format(Math.min(dropItem.getChance() * rateChance, 100)));
			sb.append("%</td></tr>");
			sb.append("</table>");
			sb.append("</td></tr>");
			sb.append("</table>");
			sb.append("</td>");
			sb.append("</tr>");
			sb.append("</table>");
			
			if ((sb.length() + rightSb.length() + leftSb.length()) < 16000) // limit of 32766?
			{
				if (leftHeight >= (rightHeight + height))
				{
					rightSb.append(sb);
					rightHeight += height;
				}
				else
				{
					leftSb.append(sb);
					leftHeight += height;
				}
			}
			else
			{
				limitReachedMsg = "<br><center>Too many drops! Could not display them all!</center>";
			}
		}
		
		final StringBuilder bodySb = new StringBuilder();
		bodySb.append("<table><tr>");
		bodySb.append("<td>");
		bodySb.append(leftSb.toString());
		bodySb.append("</td><td>");
		bodySb.append(rightSb.toString());
		bodySb.append("</td>");
		bodySb.append("</tr></table>");
		
		String html = HtmCache.getInstance().getHtm(player, "data/html/mods/NpcView/DropList.htm");
		if (html == null)
		{
			LOGGER.warning(NpcViewMod.class.getSimpleName() + ": The html file data/html/mods/NpcView/DropList.htm could not be found.");
			return;
		}
		html = html.replace("%name%", npc.getName());
		html = html.replace("%dropListButtons%", getDropListButtons(npc));
		html = html.replace("%pages%", pagesSb.toString());
		html = html.replace("%items%", bodySb.toString() + limitReachedMsg);
		Util.sendCBHtml(player, html);
	}
}
