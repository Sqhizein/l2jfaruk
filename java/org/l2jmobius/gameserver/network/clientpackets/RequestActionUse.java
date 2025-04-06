/*
 * This file is part of the L2J Faruk project.
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
package org.l2jmobius.gameserver.network.clientpackets;

import java.util.Arrays;

import org.l2jmobius.Config;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.ai.CtrlEvent;
import org.l2jmobius.gameserver.ai.CtrlIntention;
import org.l2jmobius.gameserver.ai.NextAction;
import org.l2jmobius.gameserver.ai.SummonAI;
import org.l2jmobius.gameserver.data.BotReportTable;
import org.l2jmobius.gameserver.data.xml.PetDataTable;
import org.l2jmobius.gameserver.data.xml.PetSkillData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.enums.ChatType;
import org.l2jmobius.gameserver.enums.MountType;
import org.l2jmobius.gameserver.enums.PrivateStoreType;
import org.l2jmobius.gameserver.instancemanager.AirShipManager;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.instance.BabyPet;
import org.l2jmobius.gameserver.model.actor.instance.Pet;
import org.l2jmobius.gameserver.model.actor.instance.SiegeFlag;
import org.l2jmobius.gameserver.model.actor.instance.StaticObject;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.holders.SkillHolder;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.NpcStringId;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.ChairSit;
import org.l2jmobius.gameserver.network.serverpackets.ExAskCoupleAction;
import org.l2jmobius.gameserver.network.serverpackets.ExBasicActionList;
import org.l2jmobius.gameserver.network.serverpackets.NpcSay;
import org.l2jmobius.gameserver.network.serverpackets.RecipeShopManageList;
import org.l2jmobius.gameserver.network.serverpackets.SocialAction;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.taskmanager.AttackStanceTaskManager;

/**
 * This class manages the action use request
 * @author Zoey76
 */
public class RequestActionUse extends ClientPacket
{
	private static final int SIN_EATER_ID = 12564;
	private static final int SWITCH_STANCE_ID = 6054;
	private static final NpcStringId[] NPC_STRINGS =
	{
		NpcStringId.USING_A_SPECIAL_SKILL_HERE_COULD_TRIGGER_A_BLOODBATH,
		NpcStringId.HEY_WHAT_DO_YOU_EXPECT_OF_ME,
		NpcStringId.UGGGGGH_PUSH_IT_S_NOT_COMING_OUT,
		NpcStringId.AH_I_MISSED_THE_MARK
	};
	
	private int _actionId;
	private boolean _ctrlPressed;
	private boolean _shiftPressed;
	
	@Override
	protected void readImpl()
	{
		_actionId = readInt();
		_ctrlPressed = readInt() == 1;
		_shiftPressed = readByte() == 1;
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}
		
		// Don't do anything if player is dead or confused
		if ((player.isFakeDeath() && (_actionId != 0)) || player.isDead() || player.isOutOfControl())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final BuffInfo info = player.getEffectList().getBuffInfoByAbnormalType(AbnormalType.BOT_PENALTY);
		if (info != null)
		{
			for (AbstractEffect effect : info.getEffects())
			{
				if (!effect.checkCondition(_actionId))
				{
					player.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_YOUR_ACTIONS_HAVE_BEEN_RESTRICTED);
					player.sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
			}
		}
		
		// Don't allow to do some action if player is transformed
		if (player.isTransformed())
		{
			final int[] allowedActions = player.isTransformed() ? ExBasicActionList.ACTIONS_ON_TRANSFORM : ExBasicActionList.DEFAULT_ACTION_LIST;
			if (Arrays.binarySearch(allowedActions, _actionId) < 0)
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				PacketLogger.warning(player + " used action which he does not have! Id = " + _actionId + " transform: " + player.getTransformation().getId());
				return;
			}
		}
		
		final Summon summon = player.getSummon();
		final WorldObject target = player.getTarget();
		switch (_actionId)
		{
			case 0: // Sit/Stand
			{
				if (player.isSitting() || !player.isMoving() || player.isFakeDeath())
				{
					useSit(player, target);
				}
				else
				{
					// Sit when arrive using next action.
					// Creating next action class.
					final NextAction nextAction = new NextAction(CtrlEvent.EVT_ARRIVED, CtrlIntention.AI_INTENTION_MOVE_TO, () -> useSit(player, target));
					// Binding next action to AI.
					player.getAI().setNextAction(nextAction);
				}
				break;
			}
			case 1: // Walk/Run
			{
				if (player.isRunning())
				{
					player.setWalking();
				}
				else
				{
					player.setRunning();
				}
				break;
			}
			case 10: // Private Store - Sell
			{
				player.tryOpenPrivateSellStore(false);
				break;
			}
			case 15: // Change Movement Mode (Pets)
			{
				if (validateSummon(player, summon, true))
				{
					((SummonAI) summon.getAI()).notifyFollowStatusChange();
				}
				break;
			}
			case 16: // Attack (Pets)
			{
				if (validateSummon(player, summon, true) && summon.canAttack(_ctrlPressed))
				{
					summon.doSummonAttack(target);
				}
				break;
			}
			case 17: // Stop (Pets)
			{
				if (validateSummon(player, summon, true))
				{
					summon.cancelAction();
				}
				break;
			}
			case 19: // Unsummon Pet
			{
				if (!validateSummon(player, summon, true))
				{
					break;
				}
				if (summon.isDead())
				{
					player.sendPacket(SystemMessageId.DEAD_PETS_CANNOT_BE_RETURNED_TO_THEIR_SUMMONING_ITEM);
					break;
				}
				if (summon.isAttackingNow() || summon.isInCombat() || summon.isMovementDisabled())
				{
					player.sendPacket(SystemMessageId.A_PET_CANNOT_BE_UNSUMMONED_DURING_BATTLE);
					break;
				}
				if (summon.isHungry())
				{
					if (summon.isPet() && !((Pet) summon).getPetData().getFood().isEmpty())
					{
						player.sendPacket(SystemMessageId.YOU_MAY_NOT_RESTORE_A_HUNGRY_PET);
					}
					else
					{
						player.sendPacket(SystemMessageId.THE_HUNTING_HELPER_PET_CANNOT_BE_RETURNED_BECAUSE_THERE_IS_NOT_MUCH_TIME_REMAINING_UNTIL_IT_LEAVES);
					}
					break;
				}
				summon.unSummon(player);
				break;
			}
			case 21: // Change Movement Mode (Servitors)
			{
				if (validateSummon(player, summon, false))
				{
					((SummonAI) summon.getAI()).notifyFollowStatusChange();
				}
				break;
			}
			case 22: // Attack (Servitors)
			{
				if (validateSummon(player, summon, false) && summon.canAttack(_ctrlPressed))
				{
					summon.doSummonAttack(target);
				}
				break;
			}
			case 23: // Stop (Servitors)
			{
				if (validateSummon(player, summon, false))
				{
					summon.cancelAction();
				}
				break;
			}
			case 28: // Private Store - Buy
			{
				player.tryOpenPrivateBuyStore();
				break;
			}
			case 32: // Wild Hog Cannon - Wild Cannon
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 36: // Soulless - Toxic Smoke
			{
				useSkill(player, "RangeDebuff", false);
				break;
			}
			case 37: // Dwarven Manufacture
			{
				if (player.isAlikeDead() || player.isSellingBuffs())
				{
					player.sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
				if (player.isInStoreMode())
				{
					player.setPrivateStoreType(PrivateStoreType.NONE);
					player.broadcastUserInfo();
				}
				if (player.isSitting())
				{
					player.standUp();
				}
				player.sendPacket(new RecipeShopManageList(player, true));
				break;
			}
			case 38: // Mount/Dismount
			{
				player.mountPlayer(summon);
				break;
			}
			case 39: // Soulless - Parasite Burst
			{
				useSkill(player, "RangeDD", false);
				break;
			}
			case 41: // Wild Hog Cannon - Attack
			{
				if (validateSummon(player, summon, false))
				{
					if ((target != null) && (target.isDoor() || (target instanceof SiegeFlag)))
					{
						useSkill(player, 4230, false);
					}
					else
					{
						player.sendPacket(SystemMessageId.INVALID_TARGET);
					}
				}
				break;
			}
			case 42: // Kai the Cat - Self Damage Shield
			{
				useSkill(player, "HealMagic", false);
				break;
			}
			case 43: // Merrow the Unicorn - Hydro Screw
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 44: // Big Boom - Boom Attack
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 45: // Boxer the Unicorn - Master Recharge
			{
				useSkill(player, "HealMagic", player, false);
				break;
			}
			case 46: // Mew the Cat - Mega Storm Strike
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 47: // Silhouette - Steal Blood
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 48: // Mechanic Golem - Mech. Cannon
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 51: // General Manufacture
			{
				// Player shouldn't be able to set stores if he/she is alike dead (dead or fake death)
				if (player.isAlikeDead())
				{
					player.sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
				if (player.getPrivateStoreType() != PrivateStoreType.NONE)
				{
					player.setPrivateStoreType(PrivateStoreType.NONE);
					player.broadcastUserInfo();
				}
				if (player.isSitting())
				{
					player.standUp();
				}
				player.sendPacket(new RecipeShopManageList(player, false));
				break;
			}
			case 52: // Unsummon Servitor
			{
				if (validateSummon(player, summon, false))
				{
					if (summon.isAttackingNow() || summon.isInCombat())
					{
						player.sendPacket(SystemMessageId.A_SERVITOR_WHOM_IS_ENGAGED_IN_BATTLE_CANNOT_BE_DE_ACTIVATED);
						break;
					}
					summon.unSummon(player);
				}
				break;
			}
			case 53: // Move to target (Servitors)
			{
				if (validateSummon(player, summon, false) && (target != null) && (summon != target) && !summon.isMovementDisabled())
				{
					summon.setFollowStatus(false);
					summon.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, target.getLocation());
				}
				break;
			}
			case 54: // Move to target (Pets)
			{
				if (validateSummon(player, summon, true) && (target != null) && (summon != target) && !summon.isMovementDisabled())
				{
					summon.setFollowStatus(false);
					summon.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, target.getLocation());
				}
				break;
			}
			case 61: // Private Store Package Sell
			{
				player.tryOpenPrivateSellStore(true);
				break;
			}
			case 65: // Bot Report Button
			{
				if (Config.BOTREPORT_ENABLE)
				{
					BotReportTable.getInstance().reportBot(player);
				}
				else
				{
					player.sendMessage("This feature is disabled.");
				}
				break;
			}
			case 67: // Steer
			{
				if (player.isInAirShip() && player.getAirShip().setCaptain(player))
				{
					player.broadcastUserInfo();
				}
				break;
			}
			case 68: // Cancel Control
			{
				if (player.isInAirShip() && player.getAirShip().isCaptain(player) && player.getAirShip().setCaptain(null))
				{
					player.broadcastUserInfo();
				}
				break;
			}
			case 69: // Destination Map
			{
				AirShipManager.getInstance().sendAirShipTeleportList(player);
				break;
			}
			case 70: // Exit Airship
			{
				if (player.isInAirShip())
				{
					if (player.getAirShip().isCaptain(player))
					{
						if (player.getAirShip().setCaptain(null))
						{
							player.broadcastUserInfo();
						}
					}
					else if (player.getAirShip().isInDock())
					{
						player.getAirShip().oustPlayer(player);
					}
				}
				break;
			}
			case 71:
			case 72:
			case 73:
			{
				useCoupleSocial(player, _actionId - 55);
				break;
			}
			case 1000: // Siege Golem - Siege Hammer
			{
				if ((target != null) && target.isDoor())
				{
					useSkill(player, 4079, false);
				}
				break;
			}
			case 1001: // Sin Eater - Ultimate Bombastic Buster
			{
				if (validateSummon(player, summon, true) && (summon.getId() == SIN_EATER_ID))
				{
					summon.broadcastPacket(new NpcSay(summon.getObjectId(), ChatType.NPC_GENERAL, summon.getId(), NPC_STRINGS[Rnd.get(NPC_STRINGS.length)]));
				}
				break;
			}
			case 1003: // Wind Hatchling/Strider - Wild Stun
			{
				useSkill(player, "PhysicalSpecial", true);
				break;
			}
			case 1004: // Wind Hatchling/Strider - Wild Defense
			{
				useSkill(player, "Buff", player, true);
				break;
			}
			case 1005: // Star Hatchling/Strider - Bright Burst
			{
				useSkill(player, "DDMagic", true);
				break;
			}
			case 1006: // Star Hatchling/Strider - Bright Heal
			{
				useSkill(player, "Heal", player, true);
				break;
			}
			case 1007: // Feline Queen - Blessing of Queen
			{
				useSkill(player, "Buff1", player, false);
				break;
			}
			case 1008: // Feline Queen - Gift of Queen
			{
				useSkill(player, "Buff2", player, false);
				break;
			}
			case 1009: // Feline Queen - Cure of Queen
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 1010: // Unicorn Seraphim - Blessing of Seraphim
			{
				useSkill(player, "Buff1", player, false);
				break;
			}
			case 1011: // Unicorn Seraphim - Gift of Seraphim
			{
				useSkill(player, "Buff2", player, false);
				break;
			}
			case 1012: // Unicorn Seraphim - Cure of Seraphim
			{
				useSkill(player, "DDMagic", false);
				break;
			}
			case 1013: // Nightshade - Curse of Shade
			{
				useSkill(player, "DeBuff1", false);
				break;
			}
			case 1014: // Nightshade - Mass Curse of Shade
			{
				useSkill(player, "DeBuff2", false);
				break;
			}
			case 1015: // Nightshade - Shade Sacrifice
			{
				useSkill(player, "Heal", false);
				break;
			}
			case 1016: // Cursed Man - Cursed Blow
			{
				useSkill(player, "PhysicalSpecial1", false);
				break;
			}
			case 1017: // Cursed Man - Cursed Strike
			{
				useSkill(player, "PhysicalSpecial2", false);
				break;
			}
			case 1031: // Feline King - Slash
			{
				useSkill(player, "PhysicalSpecial1", false);
				break;
			}
			case 1032: // Feline King - Spinning Slash
			{
				useSkill(player, "PhysicalSpecial2", false);
				break;
			}
			case 1033: // Feline King - Hold of King
			{
				useSkill(player, "PhysicalSpecial3", false);
				break;
			}
			case 1034: // Magnus the Unicorn - Whiplash
			{
				useSkill(player, "PhysicalSpecial1", false);
				break;
			}
			case 1035: // Magnus the Unicorn - Tridal Wave
			{
				useSkill(player, "PhysicalSpecial2", false);
				break;
			}
			case 1036: // Spectral Lord - Corpse Kaboom
			{
				useSkill(player, "PhysicalSpecial1", false);
				break;
			}
			case 1037: // Spectral Lord - Dicing Death
			{
				useSkill(player, "PhysicalSpecial2", false);
				break;
			}
			case 1038: // Spectral Lord - Dark Curse
			{
				useSkill(player, "PhysicalSpecial3", false);
				break;
			}
			case 1039: // Swoop Cannon - Cannon Fodder
			{
				useSkill(player, 5110, false);
				break;
			}
			case 1040: // Swoop Cannon - Big Bang
			{
				useSkill(player, 5111, false);
				break;
			}
			case 1041: // Great Wolf - Bite Attack
			{
				useSkill(player, "Skill01", true);
				break;
			}
			case 1042: // Great Wolf - Maul
			{
				useSkill(player, "Skill03", true);
				break;
			}
			case 1043: // Great Wolf - Cry of the Wolf
			{
				useSkill(player, "Skill02", true);
				break;
			}
			case 1044: // Great Wolf - Awakening
			{
				useSkill(player, "Skill04", true);
				break;
			}
			case 1045: // Great Wolf - Howl
			{
				useSkill(player, 5584, true);
				break;
			}
			case 1046: // Strider - Roar
			{
				useSkill(player, 5585, true);
				break;
			}
			case 1047: // Divine Beast - Bite
			{
				useSkill(player, 5580, false);
				break;
			}
			case 1048: // Divine Beast - Stun Attack
			{
				useSkill(player, 5581, false);
				break;
			}
			case 1049: // Divine Beast - Fire Breath
			{
				useSkill(player, 5582, false);
				break;
			}
			case 1050: // Divine Beast - Roar
			{
				useSkill(player, 5583, false);
				break;
			}
			case 1051: // Feline Queen - Bless The Body
			{
				useSkill(player, "buff3", false);
				break;
			}
			case 1052: // Feline Queen - Bless The Soul
			{
				useSkill(player, "buff4", false);
				break;
			}
			case 1053: // Feline Queen - Haste
			{
				useSkill(player, "buff5", false);
				break;
			}
			case 1054: // Unicorn Seraphim - Acumen
			{
				useSkill(player, "buff3", false);
				break;
			}
			case 1055: // Unicorn Seraphim - Clarity
			{
				useSkill(player, "buff4", false);
				break;
			}
			case 1056: // Unicorn Seraphim - Empower
			{
				useSkill(player, "buff5", false);
				break;
			}
			case 1057: // Unicorn Seraphim - Wild Magic
			{
				useSkill(player, "buff6", false);
				break;
			}
			case 1058: // Nightshade - Death Whisper
			{
				useSkill(player, "buff3", false);
				break;
			}
			case 1059: // Nightshade - Focus
			{
				useSkill(player, "buff4", false);
				break;
			}
			case 1060: // Nightshade - Guidance
			{
				useSkill(player, "buff5", false);
				break;
			}
			case 1061: // Wild Beast Fighter, White Weasel - Death blow
			{
				useSkill(player, 5745, true);
				break;
			}
			case 1062: // Wild Beast Fighter - Double attack
			{
				useSkill(player, 5746, true);
				break;
			}
			case 1063: // Wild Beast Fighter - Spin attack
			{
				useSkill(player, 5747, true);
				break;
			}
			case 1064: // Wild Beast Fighter - Meteor Shower
			{
				useSkill(player, 5748, true);
				break;
			}
			case 1065: // Fox Shaman, Wild Beast Fighter, White Weasel, Fairy Princess - Awakening
			{
				useSkill(player, 5753, true);
				break;
			}
			case 1066: // Fox Shaman, Spirit Shaman - Thunder Bolt
			{
				useSkill(player, 5749, true);
				break;
			}
			case 1067: // Fox Shaman, Spirit Shaman - Flash
			{
				useSkill(player, 5750, true);
				break;
			}
			case 1068: // Fox Shaman, Spirit Shaman - Lightning Wave
			{
				useSkill(player, 5751, true);
				break;
			}
			case 1069: // Fox Shaman, Fairy Princess - Flare
			{
				useSkill(player, 5752, true);
				break;
			}
			case 1070: // White Weasel, Fairy Princess, Improved Baby Buffalo, Improved Baby Kookaburra, Improved Baby Cougar, Spirit Shaman, Toy Knight, Turtle Ascetic - Buff control
			{
				useSkill(player, 5771, true);
				break;
			}
			case 1071: // Tigress - Power Strike
			{
				useSkill(player, "DDMagic", true);
				break;
			}
			case 1072: // Toy Knight - Piercing attack
			{
				useSkill(player, 6046, true);
				break;
			}
			case 1073: // Toy Knight - Whirlwind
			{
				useSkill(player, 6047, true);
				break;
			}
			case 1074: // Toy Knight - Lance Smash
			{
				useSkill(player, 6048, true);
				break;
			}
			case 1075: // Toy Knight - Battle Cry
			{
				useSkill(player, 6049, true);
				break;
			}
			case 1076: // Turtle Ascetic - Power Smash
			{
				useSkill(player, 6050, true);
				break;
			}
			case 1077: // Turtle Ascetic - Energy Burst
			{
				useSkill(player, 6051, true);
				break;
			}
			case 1078: // Turtle Ascetic - Shockwave
			{
				useSkill(player, 6052, true);
				break;
			}
			case 1079: // Turtle Ascetic - Howl
			{
				useSkill(player, 6053, true);
				break;
			}
			case 1080: // Phoenix Rush
			{
				useSkill(player, 6041, false);
				break;
			}
			case 1081: // Phoenix Cleanse
			{
				useSkill(player, 6042, false);
				break;
			}
			case 1082: // Phoenix Flame Feather
			{
				useSkill(player, 6043, false);
				break;
			}
			case 1083: // Phoenix Flame Beak
			{
				useSkill(player, 6044, false);
				break;
			}
			case 1084: // Switch State
			{
				if (summon instanceof BabyPet)
				{
					useSkill(player, 6054, true);
				}
				break;
			}
			case 1086: // Panther Cancel
			{
				useSkill(player, 6094, false);
				break;
			}
			case 1087: // Panther Dark Claw
			{
				useSkill(player, 6095, false);
				break;
			}
			case 1088: // Panther Fatal Claw
			{
				useSkill(player, 6096, false);
				break;
			}
			case 1089: // Deinonychus - Tail Strike
			{
				useSkill(player, 6199, true);
				break;
			}
			case 1090: // Guardian's Strider - Strider Bite
			{
				useSkill(player, 6205, true);
				break;
			}
			case 1091: // Guardian's Strider - Strider Fear
			{
				useSkill(player, 6206, true);
				break;
			}
			case 1092: // Guardian's Strider - Strider Dash
			{
				useSkill(player, 6207, true);
				break;
			}
			case 1093: // Maguen - Maguen Strike
			{
				useSkill(player, 6618, true);
				break;
			}
			case 1094: // Maguen - Maguen Wind Walk
			{
				useSkill(player, 6681, true);
				break;
			}
			case 1095: // Elite Maguen - Maguen Power Strike
			{
				useSkill(player, 6619, true);
				break;
			}
			case 1096: // Elite Maguen - Elite Maguen Wind Walk
			{
				useSkill(player, 6682, true);
				break;
			}
			case 1097: // Maguen - Maguen Return
			{
				useSkill(player, 6683, true);
				break;
			}
			case 1098: // Elite Maguen - Maguen Party Return
			{
				useSkill(player, 6684, true);
				break;
			}
			case 5000: // Baby Rudolph - Reindeer Scratch
			{
				useSkill(player, 23155, true);
				break;
			}
			case 5001: // Deseloph, Hyum, Rekang, Lilias, Lapham, Mafum - Rosy Seduction
			{
				useSkill(player, 23167, true);
				break;
			}
			case 5002: // Deseloph, Hyum, Rekang, Lilias, Lapham, Mafum - Critical Seduction
			{
				useSkill(player, 23168, true);
				break;
			}
			case 5003: // Hyum, Lapham, Hyum, Lapham - Thunder Bolt
			{
				useSkill(player, 5749, true);
				break;
			}
			case 5004: // Hyum, Lapham, Hyum, Lapham - Flash
			{
				useSkill(player, 5750, true);
				break;
			}
			case 5005: // Hyum, Lapham, Hyum, Lapham - Lightning Wave
			{
				useSkill(player, 5751, true);
				break;
			}
			case 5006: // Deseloph, Hyum, Rekang, Lilias, Lapham, Mafum, Deseloph, Hyum, Rekang, Lilias, Lapham, Mafum - Buff Control
			{
				useSkill(player, 5771, true);
				break;
			}
			case 5007: // Deseloph, Lilias, Deseloph, Lilias - Piercing Attack
			{
				useSkill(player, 6046, true);
				break;
			}
			case 5008: // Deseloph, Lilias, Deseloph, Lilias - Spin Attack
			{
				useSkill(player, 6047, true);
				break;
			}
			case 5009: // Deseloph, Lilias, Deseloph, Lilias - Smash
			{
				useSkill(player, 6048, true);
				break;
			}
			case 5010: // Deseloph, Lilias, Deseloph, Lilias - Ignite
			{
				useSkill(player, 6049, true);
				break;
			}
			case 5011: // Rekang, Mafum, Rekang, Mafum - Power Smash
			{
				useSkill(player, 6050, true);
				break;
			}
			case 5012: // Rekang, Mafum, Rekang, Mafum - Energy Burst
			{
				useSkill(player, 6051, true);
				break;
			}
			case 5013: // Rekang, Mafum, Rekang, Mafum - Shockwave
			{
				useSkill(player, 6052, true);
				break;
			}
			case 5014: // Rekang, Mafum, Rekang, Mafum - Ignite
			{
				useSkill(player, 6053, true);
				break;
			}
			case 5015: // Deseloph, Hyum, Rekang, Lilias, Lapham, Mafum, Deseloph, Hyum, Rekang, Lilias, Lapham, Mafum - Switch Stance
			{
				useSkill(player, 6054, true);
				break;
			}
			
			case 5100: // Triple Slash
			{
				useSkill(player, 1, true);
				break;
			}
			
			case 5101: // Double Shot
			{
				useSkill(player, 19, true);
				break;
			}
			
			case 5102: // Sacrifice
			{
				useSkill(player, 69, true);
				break;
			}
			
			case 5103: // Stun Shot
			{
				useSkill(player, 101, true);
				break;
			}
			
			case 5104: // Hex
			{
				useSkill(player, 122, true);
				break;
			}
			
			case 5105: // Fatal Strike
			{
				useSkill(player, 190, true);
				break;
			}
			
			case 5106: // Hammer Crush
			{
				useSkill(player, 260, true);
				break;
			}
			
			case 5107: // Burning Fist
			{
				useSkill(player, 280, true);
				break;
			}
			
			case 5108: // Soul Breaker
			{
				useSkill(player, 281, true);
				break;
			}
			
			case 5109: // Strider Siege Assault
			{
				useSkill(player, 325, true);
				break;
			}
			
			case 5110: // Wyvern Aegis
			{
				useSkill(player, 327, true);
				break;
			}
			
			case 5111: // Disarm
			{
				useSkill(player, 485, true);
				break;
			}
			
			case 5112: // Shoulder Charge
			{
				useSkill(player, 494, true);
				break;
			}
			
			case 5113: // Twin Shot
			{
				useSkill(player, 507, true);
				break;
			}
			
			case 5114: // Recharge
			{
				useSkill(player, 1013, true);
				break;
			}
			
			case 5115: // Regeneration
			{
				useSkill(player, 1044, true);
				break;
			}
			
			case 5116: // Wind Walk
			{
				useSkill(player, 1204, true);
				break;
			}
			
			case 5117: // Greater Battle Heal
			{
				useSkill(player, 1218, true);
				break;
			}
			
			case 5118: // Eye of Pa'agrio
			{
				useSkill(player, 1364, true);
				break;
			}
			
			case 5119: // Death Mark
			{
				useSkill(player, 1435, true);
				break;
			}
			
			case 5120: // Improved Combat
			{
				useSkill(player, 1499, true);
				break;
			}
			
			case 5121: // Improved Magic
			{
				useSkill(player, 1500, true);
				break;
			}
			
			case 5122: // Improved Condition
			{
				useSkill(player, 1501, true);
				break;
			}
			
			case 5123: // Improved Critical Attack
			{
				useSkill(player, 1502, true);
				break;
			}
			
			case 5124: // Improved Movement
			{
				useSkill(player, 1504, true);
				break;
			}
			
			case 5125: // Chant of Blood Awakening
			{
				useSkill(player, 1519, true);
				break;
			}
			
			case 5126: // Golden Spice
			{
				useSkill(player, 2188, true);
				break;
			}
			
			case 5127: // Crystal Spice
			{
				useSkill(player, 2189, true);
				break;
			}
			
			case 5128: // Water Dragon Scale
			{
				useSkill(player, 2369, true);
				break;
			}
			
			case 5129: // NPC Windstrike
			{
				useSkill(player, 4001, true);
				break;
			}
			
			case 5130: // NPC HP Drain
			{
				useSkill(player, 4002, true);
				break;
			}
			
			case 5131: // Queen Ant Brandish
			{
				useSkill(player, 4017, true);
				break;
			}
			
			case 5132: // Decrease Speed
			{
				useSkill(player, 4018, true);
				break;
			}
			
			case 5133: // Decrease Speed
			{
				useSkill(player, 4019, true);
				break;
			}
			
			case 5134: // Heal Queen Ant1
			{
				useSkill(player, 4020, true);
				break;
			}
			
			case 5135: // Heal Queen Ant2
			{
				useSkill(player, 4024, true);
				break;
			}
			
			case 5136: // Master Recharge
			{
				useSkill(player, 4025, true);
				break;
			}
			
			case 5137: // Gludio Flame
			{
				useSkill(player, 4026, true);
				break;
			}
			
			case 5138: // Gludio Heal
			{
				useSkill(player, 4027, true);
				break;
			}
			
			case 5139: // NPC Might
			{
				useSkill(player, 4028, true);
				break;
			}
			
			case 5140: // NPC Shield
			{
				useSkill(player, 4029, true);
				break;
			}
			
			case 5141: // NPC Clan Might
			{
				useSkill(player, 4030, true);
				break;
			}
			
			case 5142: // NPC Clan Aegis
			{
				useSkill(player, 4031, true);
				break;
			}
			
			case 5143: // NPC Strike
			{
				useSkill(player, 4032, true);
				break;
			}
			
			case 5144: // NPC Burn
			{
				useSkill(player, 4033, true);
				break;
			}
			
			case 5145: // Decrease Speed
			{
				useSkill(player, 4034, true);
				break;
			}
			
			case 5146: // Poison
			{
				useSkill(player, 4035, true);
				break;
			}
			
			case 5147: // Poison
			{
				useSkill(player, 4036, true);
				break;
			}
			
			case 5148: // Decrease P. Atk.
			{
				useSkill(player, 4037, true);
				break;
			}
			
			case 5149: // Decrease Atk. Spd.
			{
				useSkill(player, 4038, true);
				break;
			}
			
			case 5150: // NPC MP Drain
			{
				useSkill(player, 4039, true);
				break;
			}
			
			case 5151: // NPC Bow Attack
			{
				useSkill(player, 4040, true);
				break;
			}
			
			case 5152: // Nurka Blaze
			{
				useSkill(player, 4042, true);
				break;
			}
			
			case 5153: // Sleep
			{
				useSkill(player, 4046, true);
				break;
			}
			
			case 5154: // Hold
			{
				useSkill(player, 4047, true);
				break;
			}
			
			case 5155: // NPC Dash
			{
				useSkill(player, 4048, true);
				break;
			}
			
			case 5156: // Decrease P. Def.
			{
				useSkill(player, 4054, true);
				break;
			}
			
			case 5157: // Paralysis
			{
				useSkill(player, 4063, true);
				break;
			}
			
			case 5158: // Paralysis
			{
				useSkill(player, 4064, true);
				break;
			}
			
			case 5159: // NPC Heal
			{
				useSkill(player, 4065, true);
				break;
			}
			
			case 5160: // NPC Twister
			{
				useSkill(player, 4066, true);
				break;
			}
			
			case 5161: // NPC Mortal Blow
			{
				useSkill(player, 4067, true);
				break;
			}
			
			case 5162: // Mechanical Cannon
			{
				useSkill(player, 4068, true);
				break;
			}
			
			case 5163: // NPC Curve Beam Cannon
			{
				useSkill(player, 4069, true);
				break;
			}
			
			case 5164: // Stun
			{
				useSkill(player, 4072, true);
				break;
			}
			
			case 5165: // Stun
			{
				useSkill(player, 4073, true);
				break;
			}
			
			case 5166: // NPC Haste
			{
				useSkill(player, 4074, true);
				break;
			}
			
			case 5167: // Stun
			{
				useSkill(player, 4075, true);
				break;
			}
			
			case 5168: // Decrease Speed
			{
				useSkill(player, 4076, true);
				break;
			}
			
			case 5169: // NPC Aura Burn
			{
				useSkill(player, 4077, true);
				break;
			}
			
			case 5170: // NPC Flamestrike
			{
				useSkill(player, 4078, true);
				break;
			}
			
			case 5171: // Siege Hammer
			{
				useSkill(player, 4079, true);
				break;
			}
			
			case 5172: // Resist Physical Attack
			{
				useSkill(player, 4084, true);
				break;
			}
			
			case 5173: // NPC Blaze
			{
				useSkill(player, 4087, true);
				break;
			}
			
			case 5174: // Bleed
			{
				useSkill(player, 4088, true);
				break;
			}
			
			case 5175: // NPC Bear Spirit Totem
			{
				useSkill(player, 4089, true);
				break;
			}
			
			case 5176: // NPC Wolf Spirit Totem
			{
				useSkill(player, 4090, true);
				break;
			}
			
			case 5177: // NPC Ogre Spirit Totem
			{
				useSkill(player, 4091, true);
				break;
			}
			
			case 5178: // NPC Puma Spirit Totem
			{
				useSkill(player, 4092, true);
				break;
			}
			
			case 5179: // NPC Cancel Magic
			{
				useSkill(player, 4094, true);
				break;
			}
			
			case 5180: // NPC Hawkeye
			{
				useSkill(player, 4096, true);
				break;
			}
			
			case 5181: // NPC Chant of Life
			{
				useSkill(player, 4097, true);
				break;
			}
			
			case 5182: // Silence
			{
				useSkill(player, 4098, true);
				break;
			}
			
			case 5183: // NPC Berserk
			{
				useSkill(player, 4099, true);
				break;
			}
			
			case 5184: // NPC Prominence
			{
				useSkill(player, 4100, true);
				break;
			}
			
			case 5185: // NPC Spinning Slash
			{
				useSkill(player, 4101, true);
				break;
			}
			
			case 5186: // Surrender To Fire
			{
				useSkill(player, 4102, true);
				break;
			}
			
			case 5187: // NPC Ultimate Evasion
			{
				useSkill(player, 4103, true);
				break;
			}
			
			case 5188: // Flame
			{
				useSkill(player, 4104, true);
				break;
			}
			
			case 5189: // NPC Straight Beam Cannon
			{
				useSkill(player, 4105, true);
				break;
			}
			
			case 5190: // Antharas Stun
			{
				useSkill(player, 4106, true);
				break;
			}
			
			case 5191: // Antharas Stun
			{
				useSkill(player, 4107, true);
				break;
			}
			
			case 5192: // Antharas Terror
			{
				useSkill(player, 4108, true);
				break;
			}
			
			case 5193: // Curse of Antharas
			{
				useSkill(player, 4109, true);
				break;
			}
			
			case 5194: // Breath Attack
			{
				useSkill(player, 4110, true);
				break;
			}
			
			case 5195: // Antharas Fossilization
			{
				useSkill(player, 4111, true);
				break;
			}
			
			case 5196: // Ordinary Attack
			{
				useSkill(player, 4112, true);
				break;
			}
			
			case 5197: // Animal doing ordinary attack
			{
				useSkill(player, 4113, true);
				break;
			}
			
			case 5198: // Aden Flame
			{
				useSkill(player, 4114, true);
				break;
			}
			
			case 5199: // Aden Heal
			{
				useSkill(player, 4115, true);
				break;
			}
			
			case 5200: // Paralysis
			{
				useSkill(player, 4117, true);
				break;
			}
			
			case 5201: // Paralysis
			{
				useSkill(player, 4118, true);
				break;
			}
			
			case 5202: // Decrease Accuracy
			{
				useSkill(player, 4119, true);
				break;
			}
			
			case 5203: // Stun
			{
				useSkill(player, 4120, true);
				break;
			}
			
			case 5204: // Hostile Feeling
			{
				useSkill(player, 4123, true);
				break;
			}
			
			case 5205: // NPC Spear Attack
			{
				useSkill(player, 4124, true);
				break;
			}
			
			case 5206: // Baium: General Attack
			{
				useSkill(player, 4127, true);
				break;
			}
			
			case 5207: // Wind Of Force
			{
				useSkill(player, 4128, true);
				break;
			}
			
			case 5208: // Earthquake
			{
				useSkill(player, 4129, true);
				break;
			}
			
			case 5209: // Striking of Thunderbolt
			{
				useSkill(player, 4130, true);
				break;
			}
			
			case 5210: // Stun
			{
				useSkill(player, 4131, true);
				break;
			}
			
			case 5211: // Spear: Pound the Ground
			{
				useSkill(player, 4132, true);
				break;
			}
			
			case 5212: // Hydro Screw
			{
				useSkill(player, 4137, true);
				break;
			}
			
			case 5213: // NPC AE - Corpse Burst
			{
				useSkill(player, 4138, true);
				break;
			}
			
			case 5214: // Boom Attack
			{
				useSkill(player, 4139, true);
				break;
			}
			
			case 5215: // Contract Payment
			{
				useSkill(player, 4140, true);
				break;
			}
			
			case 5216: // NPC Wind Fist
			{
				useSkill(player, 4141, true);
				break;
			}
			
			case 5217: // NPC Fast Wind Fist
			{
				useSkill(player, 4142, true);
				break;
			}
			
			case 5218: // Treasure Bomb
			{
				useSkill(player, 4143, true);
				break;
			}
			
			case 5219: // NPC Windstrike - Magic
			{
				useSkill(player, 4151, true);
				break;
			}
			
			case 5220: // NPC HP Drain - Magic
			{
				useSkill(player, 4152, true);
				break;
			}
			
			case 5221: // Decrease Speed
			{
				useSkill(player, 4153, true);
				break;
			}
			
			case 5222: // NPC MP Drain - Magic
			{
				useSkill(player, 4154, true);
				break;
			}
			
			case 5223: // NPC Twister - Magic
			{
				useSkill(player, 4155, true);
				break;
			}
			
			case 5224: // NPC Curve Beam Cannon - Magic
			{
				useSkill(player, 4156, true);
				break;
			}
			
			case 5225: // NPC Blaze - Magic
			{
				useSkill(player, 4157, true);
				break;
			}
			
			case 5226: // NPC Prominence - Magic
			{
				useSkill(player, 4158, true);
				break;
			}
			
			case 5227: // NPC Aura Burn - Magic
			{
				useSkill(player, 4160, true);
				break;
			}
			
			case 5228: // Summon PC
			{
				useSkill(player, 4161, true);
				break;
			}
			
			case 5229: // NPC Self-Damage Shield
			{
				useSkill(player, 4163, true);
				break;
			}
			
			case 5230: // BOSS Strike
			{
				useSkill(player, 4168, true);
				break;
			}
			
			case 5231: // Stun
			{
				useSkill(player, 4169, true);
				break;
			}
			
			case 5232: // BOSS Mortal Blow
			{
				useSkill(player, 4170, true);
				break;
			}
			
			case 5233: // BOSS Spinning Slash
			{
				useSkill(player, 4171, true);
				break;
			}
			
			case 5234: // Stun
			{
				useSkill(player, 4172, true);
				break;
			}
			
			case 5235: // BOSS Might
			{
				useSkill(player, 4173, true);
				break;
			}
			
			case 5236: // BOSS Shield
			{
				useSkill(player, 4174, true);
				break;
			}
			
			case 5237: // BOSS Haste
			{
				useSkill(player, 4175, true);
				break;
			}
			
			case 5238: // BOSS Reflect Damage
			{
				useSkill(player, 4176, true);
				break;
			}
			
			case 5239: // BOSS Cancel Magic
			{
				useSkill(player, 4177, true);
				break;
			}
			
			case 5240: // BOSS Flamestrike
			{
				useSkill(player, 4178, true);
				break;
			}
			
			case 5241: // BOSS Strike
			{
				useSkill(player, 4179, true);
				break;
			}
			
			case 5242: // Stun
			{
				useSkill(player, 4180, true);
				break;
			}
			
			case 5243: // BOSS Mortal Blow
			{
				useSkill(player, 4181, true);
				break;
			}
			
			case 5244: // Poison
			{
				useSkill(player, 4182, true);
				break;
			}
			
			case 5245: // Decrease P. Atk.
			{
				useSkill(player, 4183, true);
				break;
			}
			
			case 5246: // Decrease Atk. Spd.
			{
				useSkill(player, 4184, true);
				break;
			}
			
			case 5247: // Sleep
			{
				useSkill(player, 4185, true);
				break;
			}
			
			case 5248: // Hold
			{
				useSkill(player, 4186, true);
				break;
			}
			
			case 5249: // Decrease Speed
			{
				useSkill(player, 4187, true);
				break;
			}
			
			case 5250: // Bleed
			{
				useSkill(player, 4188, true);
				break;
			}
			
			case 5251: // Paralysis
			{
				useSkill(player, 4189, true);
				break;
			}
			
			case 5252: // Decrease MP
			{
				useSkill(player, 4190, true);
				break;
			}
			
			case 5253: // BOSS Windstrike
			{
				useSkill(player, 4191, true);
				break;
			}
			
			case 5254: // BOSS HP Drain
			{
				useSkill(player, 4192, true);
				break;
			}
			
			case 5255: // BOSS Life Drain
			{
				useSkill(player, 4193, true);
				break;
			}
			
			case 5256: // BOSS Aura Burn
			{
				useSkill(player, 4194, true);
				break;
			}
			
			case 5257: // BOSS Twister
			{
				useSkill(player, 4195, true);
				break;
			}
			
			case 5258: // Decrease Speed
			{
				useSkill(player, 4196, true);
				break;
			}
			
			case 5259: // Hold
			{
				useSkill(player, 4197, true);
				break;
			}
			
			case 5260: // Poison
			{
				useSkill(player, 4198, true);
				break;
			}
			
			case 5261: // Decrease P. Atk.
			{
				useSkill(player, 4199, true);
				break;
			}
			
			case 5262: // Decrease Atk. Spd.
			{
				useSkill(player, 4200, true);
				break;
			}
			
			case 5263: // Sleep
			{
				useSkill(player, 4201, true);
				break;
			}
			
			case 5264: // Hold
			{
				useSkill(player, 4202, true);
				break;
			}
			
			case 5265: // Decrease Speed
			{
				useSkill(player, 4203, true);
				break;
			}
			
			case 5266: // Bleed
			{
				useSkill(player, 4204, true);
				break;
			}
			
			case 5267: // Paralysis
			{
				useSkill(player, 4205, true);
				break;
			}
			
			case 5268: // Decrease MP
			{
				useSkill(player, 4206, true);
				break;
			}
			
			case 5269: // Stun
			{
				useSkill(player, 4208, true);
				break;
			}
			
			case 5270: // BOSS Heal
			{
				useSkill(player, 4209, true);
				break;
			}
			
			case 5271: // BOSS Chant of Life
			{
				useSkill(player, 4210, true);
				break;
			}
			
			case 5272: // BOSS Might
			{
				useSkill(player, 4211, true);
				break;
			}
			
			case 5273: // BOSS Shield
			{
				useSkill(player, 4212, true);
				break;
			}
			
			case 5274: // BOSS Haste
			{
				useSkill(player, 4213, true);
				break;
			}
			
			case 5275: // BOSS Reflect Damage
			{
				useSkill(player, 4214, true);
				break;
			}
			
			case 5276: // Scatter Enemy
			{
				useSkill(player, 4216, true);
				break;
			}
			
			case 5277: // none
			{
				useSkill(player, 4217, true);
				break;
			}
			
			case 5278: // Absorb HP MP
			{
				useSkill(player, 4218, true);
				break;
			}
			
			case 5279: // Hold
			{
				useSkill(player, 4219, true);
				break;
			}
			
			case 5280: // Deadly Dual-Sword Weapon
			{
				useSkill(player, 4220, true);
				break;
			}
			
			case 5281: // Deadly Dual-Sword Weapon: Range Attack
			{
				useSkill(player, 4221, true);
				break;
			}
			
			case 5282: // Instant Move
			{
				useSkill(player, 4222, true);
				break;
			}
			
			case 5283: // NPC Double Dagger Attack
			{
				useSkill(player, 4228, true);
				break;
			}
			
			case 5284: // NPC Double Wind Fist
			{
				useSkill(player, 4229, true);
				break;
			}
			
			case 5285: // Wild Cannon
			{
				useSkill(player, 4230, true);
				break;
			}
			
			case 5286: // NPC AE Strike
			{
				useSkill(player, 4232, true);
				break;
			}
			
			case 5287: // none
			{
				useSkill(player, 4235, true);
				break;
			}
			
			case 5288: // Decrease Heal
			{
				useSkill(player, 4236, true);
				break;
			}
			
			case 5289: // Decrease Speed
			{
				useSkill(player, 4237, true);
				break;
			}
			
			case 5290: // Increase Re-use Time
			{
				useSkill(player, 4238, true);
				break;
			}
			
			case 5291: // NPC Wild Sweep
			{
				useSkill(player, 4244, true);
				break;
			}
			
			case 5292: // NPC Windstrike - Slow
			{
				useSkill(player, 4247, true);
				break;
			}
			
			case 5293: // NPC HP Drain - Slow
			{
				useSkill(player, 4248, true);
				break;
			}
			
			case 5294: // Decrease Speed
			{
				useSkill(player, 4249, true);
				break;
			}
			
			case 5295: // NPC Twister - Slow
			{
				useSkill(player, 4250, true);
				break;
			}
			
			case 5296: // NPC Flamestrike - Slow
			{
				useSkill(player, 4252, true);
				break;
			}
			
			case 5297: // NPC Blaze - Slow
			{
				useSkill(player, 4253, true);
				break;
			}
			
			case 5298: // NPC Prominence - Slow
			{
				useSkill(player, 4254, true);
				break;
			}
			
			case 5299: // NPC Hydroblast - Magic
			{
				useSkill(player, 4257, true);
				break;
			}
			
			case 5300: // Toxic Smoke
			{
				useSkill(player, 4259, true);
				break;
			}
			
			case 5301: // Steal Blood
			{
				useSkill(player, 4260, true);
				break;
			}
			
			case 5302: // Mega Storm Strike
			{
				useSkill(player, 4261, true);
				break;
			}
			
			case 5303: // Holiday Wind Walk
			{
				useSkill(player, 4262, true);
				break;
			}
			
			case 5304: // Holiday Haste
			{
				useSkill(player, 4263, true);
				break;
			}
			
			case 5305: // Holiday Empower
			{
				useSkill(player, 4264, true);
				break;
			}
			
			case 5306: // Holiday Might
			{
				useSkill(player, 4265, true);
				break;
			}
			
			case 5307: // Holiday Shield
			{
				useSkill(player, 4266, true);
				break;
			}
			
			case 5308: // Wyvern Breath
			{
				useSkill(player, 4289, true);
				break;
			}
			
			case 5309: // BOSS Holy Light Burst
			{
				useSkill(player, 4314, true);
				break;
			}
			
			case 5310: // Hold
			{
				useSkill(player, 4315, true);
				break;
			}
			
			case 5311: // BOSS Lilim Drain
			{
				useSkill(player, 4316, true);
				break;
			}
			
			case 5312: // Increase Rage Might
			{
				useSkill(player, 4317, true);
				break;
			}
			
			case 5313: // Ultimate Buff
			{
				useSkill(player, 4318, true);
				break;
			}
			
			case 5314: // Poison
			{
				useSkill(player, 4320, true);
				break;
			}
			
			case 5315: // Adventurer's Wind Walk
			{
				useSkill(player, 4322, true);
				break;
			}
			
			case 5316: // Adventurer's Bless the Body
			{
				useSkill(player, 4324, true);
				break;
			}
			
			case 5317: // Adventurer's Haste
			{
				useSkill(player, 4327, true);
				break;
			}
			
			case 5318: // Adventurer's Acumen
			{
				useSkill(player, 4329, true);
				break;
			}
			
			case 5319: // Ultimate Buff, 2nd
			{
				useSkill(player, 4340, true);
				break;
			}
			
			case 5320: // Ultimate Buff, 3rd
			{
				useSkill(player, 4341, true);
				break;
			}
			
			case 5321: // Might
			{
				useSkill(player, 4345, true);
				break;
			}
			
			case 5322: // Berserker Spirit
			{
				useSkill(player, 4352, true);
				break;
			}
			
			case 5323: // Vampiric Rage
			{
				useSkill(player, 4354, true);
				break;
			}
			
			case 5324: // Acumen
			{
				useSkill(player, 4355, true);
				break;
			}
			
			case 5325: // Empower
			{
				useSkill(player, 4356, true);
				break;
			}
			
			case 5326: // Haste
			{
				useSkill(player, 4357, true);
				break;
			}
			
			case 5327: // Guidance
			{
				useSkill(player, 4358, true);
				break;
			}
			
			case 5328: // Focus
			{
				useSkill(player, 4359, true);
				break;
			}
			
			case 5329: // Death Whisper
			{
				useSkill(player, 4360, true);
				break;
			}
			
			case 5330: // Wield Temper
			{
				useSkill(player, 4377, true);
				break;
			}
			
			case 5331: // Self Damage Shield
			{
				useSkill(player, 4378, true);
				break;
			}
			
			case 5332: // Curse of Lake Ghost
			{
				useSkill(player, 4382, true);
				break;
			}
			
			case 5333: // NPC Hate Stone
			{
				useSkill(player, 4383, true);
				break;
			}
			
			case 5334: // Surrender To Water
			{
				useSkill(player, 4463, true);
				break;
			}
			
			case 5335: // Surrender To Wind
			{
				useSkill(player, 4464, true);
				break;
			}
			
			case 5336: // Surrender To Earth
			{
				useSkill(player, 4465, true);
				break;
			}
			
			case 5337: // Surrender To Darkness
			{
				useSkill(player, 4466, true);
				break;
			}
			
			case 5338: // Surrender To Light
			{
				useSkill(player, 4467, true);
				break;
			}
			
			case 5339: // NPC Spoils
			{
				useSkill(player, 4470, true);
				break;
			}
			
			case 5340: // NPC Anger
			{
				useSkill(player, 4471, true);
				break;
			}
			
			case 5341: // NPC Strike Golem
			{
				useSkill(player, 4472, true);
				break;
			}
			
			case 5342: // Stun
			{
				useSkill(player, 4473, true);
				break;
			}
			
			case 5343: // NPC Frost Wall
			{
				useSkill(player, 4477, true);
				break;
			}
			
			case 5344: // Ice Fairy Aqua Splash
			{
				useSkill(player, 4478, true);
				break;
			}
			
			case 5345: // Ice Fairy Hex
			{
				useSkill(player, 4481, true);
				break;
			}
			
			case 5346: // Ice Fairy Silence
			{
				useSkill(player, 4482, true);
				break;
			}
			
			case 5347: // Hold
			{
				useSkill(player, 4483, true);
				break;
			}
			
			case 5348: // Eating Follower Heal
			{
				useSkill(player, 4484, true);
				break;
			}
			
			case 5349: // Eating Follower
			{
				useSkill(player, 4485, true);
				break;
			}
			
			case 5350: // Decrease P. Def.
			{
				useSkill(player, 4486, true);
				break;
			}
			
			case 5351: // Hold
			{
				useSkill(player, 4488, true);
				break;
			}
			
			case 5352: // NPC Death MP Bomb
			{
				useSkill(player, 4489, true);
				break;
			}
			
			case 5353: // Holy Weapon
			{
				useSkill(player, 4491, true);
				break;
			}
			
			case 5354: // Enlarging Head Curse
			{
				useSkill(player, 4492, true);
				break;
			}
			
			case 5355: // BOSS Power Shot
			{
				useSkill(player, 4495, true);
				break;
			}
			
			case 5356: // Orfen Heal
			{
				useSkill(player, 4516, true);
				break;
			}
			
			case 5357: // Quest - BOSS Defend
			{
				useSkill(player, 4517, true);
				break;
			}
			
			case 5358: // Quest - BOSS Rampage
			{
				useSkill(player, 4518, true);
				break;
			}
			
			case 5359: // Quest - BOSS Defend
			{
				useSkill(player, 4519, true);
				break;
			}
			
			case 5360: // Quest - BOSS Rampage
			{
				useSkill(player, 4520, true);
				break;
			}
			
			case 5361: // Eye of Assassin
			{
				useSkill(player, 4522, true);
				break;
			}
			
			case 5362: // Quest - BOSS Evasion
			{
				useSkill(player, 4523, true);
				break;
			}
			
			case 5363: // Quest - BOSS Bluff
			{
				useSkill(player, 4524, true);
				break;
			}
			
			case 5364: // Quest - BOSS Defend
			{
				useSkill(player, 4525, true);
				break;
			}
			
			case 5365: // Quest - BOSS Summon
			{
				useSkill(player, 4526, true);
				break;
			}
			
			case 5366: // Quest - BOSS Inc HP to Summoned
			{
				useSkill(player, 4527, true);
				break;
			}
			
			case 5367: // Quest - BOSS Movement to Summoned
			{
				useSkill(player, 4528, true);
				break;
			}
			
			case 5368: // Quest - Summoned Explosion
			{
				useSkill(player, 4529, true);
				break;
			}
			
			case 5369: // Quest - Summoned HP Heal
			{
				useSkill(player, 4530, true);
				break;
			}
			
			case 5370: // Quest - Summoned MP Heal
			{
				useSkill(player, 4531, true);
				break;
			}
			
			case 5371: // Quest - BOSS Reflect
			{
				useSkill(player, 4532, true);
				break;
			}
			
			case 5372: // Dance of Resistance
			{
				useSkill(player, 4533, true);
				break;
			}
			
			case 5373: // Dance of Nihil
			{
				useSkill(player, 4534, true);
				break;
			}
			
			case 5374: // Dance of Weakness
			{
				useSkill(player, 4535, true);
				break;
			}
			
			case 5375: // Song of Seduce
			{
				useSkill(player, 4536, true);
				break;
			}
			
			case 5376: // Song of Sweet Whisper
			{
				useSkill(player, 4537, true);
				break;
			}
			
			case 5377: // Song of Temptation
			{
				useSkill(player, 4538, true);
				break;
			}
			
			case 5378: // Curse of Vague
			{
				useSkill(player, 4539, true);
				break;
			}
			
			case 5379: // Curse of Weakness
			{
				useSkill(player, 4540, true);
				break;
			}
			
			case 5380: // Curse of Nihil
			{
				useSkill(player, 4541, true);
				break;
			}
			
			case 5381: // Quest - BOSS Weakness
			{
				useSkill(player, 4544, true);
				break;
			}
			
			case 5382: // Quest - BOSS Reflect
			{
				useSkill(player, 4545, true);
				break;
			}
			
			case 5383: // NPC Fire Burn
			{
				useSkill(player, 4560, true);
				break;
			}
			
			case 5384: // NPC Fire Burn - Magic
			{
				useSkill(player, 4561, true);
				break;
			}
			
			case 5385: // NPC Solar Flare
			{
				useSkill(player, 4562, true);
				break;
			}
			
			case 5386: // NPC Solar Flare - Magic
			{
				useSkill(player, 4563, true);
				break;
			}
			
			case 5387: // NPC Eruption
			{
				useSkill(player, 4565, true);
				break;
			}
			
			case 5388: // NPC Eruption - Magic
			{
				useSkill(player, 4566, true);
				break;
			}
			
			case 5389: // NPC Eruption - Slow
			{
				useSkill(player, 4567, true);
				break;
			}
			
			case 5390: // NPC AE Solar Flare
			{
				useSkill(player, 4568, true);
				break;
			}
			
			case 5391: // NPC AE Solar Flare - Magic
			{
				useSkill(player, 4569, true);
				break;
			}
			
			case 5392: // NPC Blazing Circle
			{
				useSkill(player, 4571, true);
				break;
			}
			
			case 5393: // NPC Triple Sonic Slash
			{
				useSkill(player, 4572, true);
				break;
			}
			
			case 5394: // NPC Sonic Blaster
			{
				useSkill(player, 4573, true);
				break;
			}
			
			case 5395: // NPC Sonic Storm
			{
				useSkill(player, 4574, true);
				break;
			}
			
			case 5396: // NPC Clan Buff - Haste
			{
				useSkill(player, 4575, true);
				break;
			}
			
			case 5397: // NPC Clan Buff - Damage Shield
			{
				useSkill(player, 4576, true);
				break;
			}
			
			case 5398: // Decrease Accuracy
			{
				useSkill(player, 4577, true);
				break;
			}
			
			case 5400: // Bleed
			{
				useSkill(player, 4579, true);
				break;
			}
			
			case 5401: // Decrease P. Atk.
			{
				useSkill(player, 4580, true);
				break;
			}
			
			case 5402: // Hold
			{
				useSkill(player, 4581, true);
				break;
			}
			
			case 5403: // Poison
			{
				useSkill(player, 4582, true);
				break;
			}
			
			case 5404: // Reducing P. Def Stun
			{
				useSkill(player, 4584, true);
				break;
			}
			
			case 5405: // NPC Clan Buff - Berserk Might
			{
				useSkill(player, 4585, true);
				break;
			}
			
			case 5406: // Decrease Evasion
			{
				useSkill(player, 4586, true);
				break;
			}
			
			case 5407: // Decrease P. Atk.
			{
				useSkill(player, 4587, true);
				break;
			}
			
			case 5408: // Decrease Speed
			{
				useSkill(player, 4589, true);
				break;
			}
			
			case 5409: // Decrease Speed
			{
				useSkill(player, 4590, true);
				break;
			}
			
			case 5410: // Decrease Speed
			{
				useSkill(player, 4591, true);
				break;
			}
			
			case 5411: // Decrease P. Def.
			{
				useSkill(player, 4592, true);
				break;
			}
			
			case 5412: // Decrease P. Def.
			{
				useSkill(player, 4593, true);
				break;
			}
			
			case 5413: // Decrease P. Def.
			{
				useSkill(player, 4594, true);
				break;
			}
			
			case 5414: // NPC Clan Buff - Acumen Shield
			{
				useSkill(player, 4595, true);
				break;
			}
			
			case 5415: // Bleed
			{
				useSkill(player, 4596, true);
				break;
			}
			
			case 5416: // Bleed
			{
				useSkill(player, 4597, true);
				break;
			}
			
			case 5417: // Decrease Speed
			{
				useSkill(player, 4599, true);
				break;
			}
			
			case 5418: // Reducing P. Def Stun
			{
				useSkill(player, 4600, true);
				break;
			}
			
			case 5419: // NPC Clan Buff - Acumen Focus
			{
				useSkill(player, 4601, true);
				break;
			}
			
			case 5420: // Decrease P. Atk.
			{
				useSkill(player, 4602, true);
				break;
			}
			
			case 5421: // Decrease P. Atk.
			{
				useSkill(player, 4603, true);
				break;
			}
			
			case 5422: // Surrender To Fire
			{
				useSkill(player, 4605, true);
				break;
			}
			
			case 5423: // Poison
			{
				useSkill(player, 4606, true);
				break;
			}
			
			case 5424: // Magma Attack
			{
				useSkill(player, 4607, true);
				break;
			}
			
			case 5425: // NPC Clan Buff - Berserk
			{
				useSkill(player, 4608, true);
				break;
			}
			
			case 5426: // NPC Clan Buff - Vampiric Rage
			{
				useSkill(player, 4609, true);
				break;
			}
			
			case 5427: // NPC Clan Buff - Focus
			{
				useSkill(player, 4610, true);
				break;
			}
			
			case 5428: // NPC Wide Wild Sweep
			{
				useSkill(player, 4612, true);
				break;
			}
			
			case 5429: // NPC Clan Heal
			{
				useSkill(player, 4613, true);
				break;
			}
			
			case 5430: // NPC Death Bomb
			{
				useSkill(player, 4614, true);
				break;
			}
			
			case 5431: // Bleed
			{
				useSkill(player, 4615, true);
				break;
			}
			
			case 5432: // Fake Petrificiation
			{
				useSkill(player, 4616, true);
				break;
			}
			
			case 5433: // NPC Cancel PC Target
			{
				useSkill(player, 4618, true);
				break;
			}
			
			case 5434: // Paralysis
			{
				useSkill(player, 4620, true);
				break;
			}
			
			case 5435: // NPC AE - 80% HP Drain
			{
				useSkill(player, 4621, true);
				break;
			}
			
			case 5436: // NPC AE - 80% HP Drain - Magic
			{
				useSkill(player, 4622, true);
				break;
			}
			
			case 5437: // Mysterious Aura
			{
				useSkill(player, 4628, true);
				break;
			}
			
			case 5438: // NPC MR - HP Drain
			{
				useSkill(player, 4629, true);
				break;
			}
			
			case 5439: // NPC MR - Twister
			{
				useSkill(player, 4630, true);
				break;
			}
			
			case 5440: // NPC Buff - Acumen Shield WildMagic
			{
				useSkill(player, 4631, true);
				break;
			}
			
			case 5441: // NPC Buff - Acumen Empower WildMagic
			{
				useSkill(player, 4632, true);
				break;
			}
			
			case 5442: // NPC Buff - Acumen Empower Berserk
			{
				useSkill(player, 4633, true);
				break;
			}
			
			case 5443: // NPC Buff - Acumen Empower DamageShield
			{
				useSkill(player, 4634, true);
				break;
			}
			
			case 5444: // NPC Buff - Acumen Berserk WildMagic
			{
				useSkill(player, 4635, true);
				break;
			}
			
			case 5445: // NPC Buff - Acumen Berserk DamageShield
			{
				useSkill(player, 4636, true);
				break;
			}
			
			case 5446: // NPC Buff - Acumen WildMagic DamageShield
			{
				useSkill(player, 4637, true);
				break;
			}
			
			case 5447: // NPC Clan Buff - Acumen Empower WildMagic
			{
				useSkill(player, 4638, true);
				break;
			}
			
			case 5448: // NPC Clan Buff - Acumen Empower Berserk
			{
				useSkill(player, 4639, true);
				break;
			}
			
			case 5449: // Sleep
			{
				useSkill(player, 4640, true);
				break;
			}
			
			case 5450: // NPC Super Strike
			{
				useSkill(player, 4641, true);
				break;
			}
			
			case 5451: // Decrease Speed
			{
				useSkill(player, 4643, true);
				break;
			}
			
			case 5452: // Poison
			{
				useSkill(player, 4649, true);
				break;
			}
			
			case 5453: // NPC AE - Dispel Hold
			{
				useSkill(player, 4650, true);
				break;
			}
			
			case 5454: // NPC AE - Dispel Slow
			{
				useSkill(player, 4651, true);
				break;
			}
			
			case 5455: // NPC AE - Dispel Silence
			{
				useSkill(player, 4652, true);
				break;
			}
			
			case 5456: // NPC Death Link
			{
				useSkill(player, 4654, true);
				break;
			}
			
			case 5457: // NPC Death Link - Magic
			{
				useSkill(player, 4655, true);
				break;
			}
			
			case 5458: // NPC Death Link - Slow
			{
				useSkill(player, 4656, true);
				break;
			}
			
			case 5459: // Hold
			{
				useSkill(player, 4657, true);
				break;
			}
			
			case 5460: // Hold
			{
				useSkill(player, 4658, true);
				break;
			}
			
			case 5461: // NPC Hate
			{
				useSkill(player, 4663, true);
				break;
			}
			
			case 5462: // NPC 100% HP Drain
			{
				useSkill(player, 4664, true);
				break;
			}
			
			case 5463: // NPC 100% HP Drain - Magic
			{
				useSkill(player, 4665, true);
				break;
			}
			
			case 5464: // NPC 100% HP Drain - Slow
			{
				useSkill(player, 4666, true);
				break;
			}
			
			case 5465: // NPC Strong 100% HP Drain - Magic
			{
				useSkill(player, 4668, true);
				break;
			}
			
			case 5466: // Short Stun
			{
				useSkill(player, 4670, true);
				break;
			}
			
			case 5467: // AV - Teleport
			{
				useSkill(player, 4671, true);
				break;
			}
			
			case 5468: // NPC Corpse Remove
			{
				useSkill(player, 4672, true);
				break;
			}
			
			case 5469: // NPC Dispel Fighter Buff
			{
				useSkill(player, 4675, true);
				break;
			}
			
			case 5470: // Valakas Lava Skin
			{
				useSkill(player, 4680, true);
				break;
			}
			
			case 5471: // Valakas Trample
			{
				useSkill(player, 4681, true);
				break;
			}
			
			case 5472: // Valakas Trample
			{
				useSkill(player, 4682, true);
				break;
			}
			
			case 5473: // Valakas Dragon Breath
			{
				useSkill(player, 4683, true);
				break;
			}
			
			case 5474: // Valakas Dragon Breath
			{
				useSkill(player, 4684, true);
				break;
			}
			
			case 5475: // Valakas Tail Stomp
			{
				useSkill(player, 4685, true);
				break;
			}
			
			case 5476: // Valakas Tail Stomp
			{
				useSkill(player, 4687, true);
				break;
			}
			
			case 5477: // Valakas Stun
			{
				useSkill(player, 4688, true);
				break;
			}
			
			case 5478: // Valakas Fear
			{
				useSkill(player, 4689, true);
				break;
			}
			
			case 5479: // Valakas Meteor Storm
			{
				useSkill(player, 4690, true);
				break;
			}
			
			case 5480: // Quest BOSS Big Body
			{
				useSkill(player, 4692, true);
				break;
			}
			
			case 5481: // Quest BOSS Dispel Big Body
			{
				useSkill(player, 4693, true);
				break;
			}
			
			case 5482: // Ultimate Debuff
			{
				useSkill(player, 4694, true);
				break;
			}
			
			case 5483: // Ultimate Debuff
			{
				useSkill(player, 4695, true);
				break;
			}
			
			case 5484: // Ultimate Debuff
			{
				useSkill(player, 4696, true);
				break;
			}
			
			case 5485: // NPC Monster Hate
			{
				useSkill(player, 4697, true);
				break;
			}
			
			case 5486: // Blessing of Queen
			{
				useSkill(player, 4699, true);
				break;
			}
			
			case 5487: // Gift of Queen
			{
				useSkill(player, 4700, true);
				break;
			}
			
			case 5488: // Cure of Queen
			{
				useSkill(player, 4701, true);
				break;
			}
			
			case 5489: // Blessing of Seraphim
			{
				useSkill(player, 4702, true);
				break;
			}
			
			case 5490: // Gift of Seraphim
			{
				useSkill(player, 4703, true);
				break;
			}
			
			case 5491: // Cure of Seraphim
			{
				useSkill(player, 4704, true);
				break;
			}
			
			case 5492: // Curse of Shade
			{
				useSkill(player, 4705, true);
				break;
			}
			
			case 5493: // Mass Curse of Shade
			{
				useSkill(player, 4706, true);
				break;
			}
			
			case 5494: // Shade Sacrifice
			{
				useSkill(player, 4707, true);
				break;
			}
			
			case 5495: // Cursed Strike
			{
				useSkill(player, 4708, true);
				break;
			}
			
			case 5496: // Cursed Blow
			{
				useSkill(player, 4709, true);
				break;
			}
			
			case 5497: // Wild Stun
			{
				useSkill(player, 4710, true);
				break;
			}
			
			case 5498: // Wild Defense
			{
				useSkill(player, 4711, true);
				break;
			}
			
			case 5499: // Bright Burst
			{
				useSkill(player, 4712, true);
				break;
			}
			
			case 5500: // Bright Heal
			{
				useSkill(player, 4713, true);
				break;
			}
			
			case 5501: // Heal Trick
			{
				useSkill(player, 4717, true);
				break;
			}
			
			case 5502: // Greater Heal Trick
			{
				useSkill(player, 4718, true);
				break;
			}
			
			case 5503: // BOSS Strike
			{
				useSkill(player, 4719, true);
				break;
			}
			
			case 5504: // BOSS Strike
			{
				useSkill(player, 4720, true);
				break;
			}
			
			case 5505: // BOSS Strike
			{
				useSkill(player, 4721, true);
				break;
			}
			
			case 5506: // BOSS Strike
			{
				useSkill(player, 4722, true);
				break;
			}
			
			case 5507: // BOSS Strike
			{
				useSkill(player, 4723, true);
				break;
			}
			
			case 5508: // Stun
			{
				useSkill(player, 4724, true);
				break;
			}
			
			case 5509: // Stun
			{
				useSkill(player, 4725, true);
				break;
			}
			
			case 5510: // Stun
			{
				useSkill(player, 4726, true);
				break;
			}
			
			case 5511: // Stun
			{
				useSkill(player, 4727, true);
				break;
			}
			
			case 5512: // Stun
			{
				useSkill(player, 4728, true);
				break;
			}
			
			case 5513: // BOSS Mortal Blow
			{
				useSkill(player, 4729, true);
				break;
			}
			
			case 5514: // BOSS Mortal Blow
			{
				useSkill(player, 4730, true);
				break;
			}
			
			case 5515: // BOSS Mortal Blow
			{
				useSkill(player, 4731, true);
				break;
			}
			
			case 5516: // BOSS Mortal Blow
			{
				useSkill(player, 4732, true);
				break;
			}
			
			case 5517: // BOSS Mortal Blow
			{
				useSkill(player, 4733, true);
				break;
			}
			
			case 5518: // BOSS Spinning Slash
			{
				useSkill(player, 4734, true);
				break;
			}
			
			case 5519: // BOSS Spinning Slash
			{
				useSkill(player, 4735, true);
				break;
			}
			
			case 5520: // BOSS Spinning Slash
			{
				useSkill(player, 4736, true);
				break;
			}
			
			case 5521: // BOSS Spinning Slash
			{
				useSkill(player, 4737, true);
				break;
			}
			
			case 5522: // BOSS Spinning Slash
			{
				useSkill(player, 4738, true);
				break;
			}
			
			case 5523: // BOSS Strike
			{
				useSkill(player, 4739, true);
				break;
			}
			
			case 5524: // BOSS Strike
			{
				useSkill(player, 4740, true);
				break;
			}
			
			case 5525: // BOSS Strike
			{
				useSkill(player, 4741, true);
				break;
			}
			
			case 5526: // BOSS Strike
			{
				useSkill(player, 4742, true);
				break;
			}
			
			case 5527: // BOSS Strike
			{
				useSkill(player, 4743, true);
				break;
			}
			
			case 5528: // Stun
			{
				useSkill(player, 4744, true);
				break;
			}
			
			case 5529: // Stun
			{
				useSkill(player, 4745, true);
				break;
			}
			
			case 5530: // Stun
			{
				useSkill(player, 4746, true);
				break;
			}
			
			case 5531: // Stun
			{
				useSkill(player, 4748, true);
				break;
			}
			
			case 5532: // BOSS Mortal Blow
			{
				useSkill(player, 4751, true);
				break;
			}
			
			case 5533: // BOSS Mortal Blow
			{
				useSkill(player, 4752, true);
				break;
			}
			
			case 5534: // BOSS Mortal Blow
			{
				useSkill(player, 4753, true);
				break;
			}
			
			case 5535: // BOSS Power Shot
			{
				useSkill(player, 4754, true);
				break;
			}
			
			case 5536: // BOSS Power Shot
			{
				useSkill(player, 4755, true);
				break;
			}
			
			case 5537: // BOSS Power Shot
			{
				useSkill(player, 4756, true);
				break;
			}
			
			case 5538: // BOSS Power Shot
			{
				useSkill(player, 4757, true);
				break;
			}
			
			case 5539: // BOSS Power Shot
			{
				useSkill(player, 4758, true);
				break;
			}
			
			case 5540: // Stun
			{
				useSkill(player, 4760, true);
				break;
			}
			
			case 5541: // Stun
			{
				useSkill(player, 4761, true);
				break;
			}
			
			case 5542: // BOSS Power Shot
			{
				useSkill(player, 4766, true);
				break;
			}
			
			case 5543: // Stun
			{
				useSkill(player, 4771, true);
				break;
			}
			
			case 5544: // BOSS Spear Attack
			{
				useSkill(player, 4774, true);
				break;
			}
			
			case 5545: // BOSS Spear Attack
			{
				useSkill(player, 4777, true);
				break;
			}
			
			case 5546: // BOSS Spear Attack
			{
				useSkill(player, 4778, true);
				break;
			}
			
			case 5547: // BOSS Heal
			{
				useSkill(player, 4779, true);
				break;
			}
			
			case 5548: // BOSS Heal
			{
				useSkill(player, 4780, true);
				break;
			}
			
			case 5549: // BOSS Heal
			{
				useSkill(player, 4781, true);
				break;
			}
			
			case 5550: // BOSS Heal
			{
				useSkill(player, 4782, true);
				break;
			}
			
			case 5551: // BOSS Heal
			{
				useSkill(player, 4783, true);
				break;
			}
			
			case 5552: // BOSS Chant of Life
			{
				useSkill(player, 4784, true);
				break;
			}
			
			case 5553: // BOSS Chant of Life
			{
				useSkill(player, 4785, true);
				break;
			}
			
			case 5554: // BOSS Chant of Life
			{
				useSkill(player, 4786, true);
				break;
			}
			
			case 5555: // BOSS Chant of Life
			{
				useSkill(player, 4788, true);
				break;
			}
			
			case 5556: // Stun
			{
				useSkill(player, 4992, true);
				break;
			}
			
			case 5557: // Venom - Strike
			{
				useSkill(player, 4993, true);
				break;
			}
			
			case 5558: // Venom - Sonic Storm
			{
				useSkill(player, 4994, true);
				break;
			}
			
			case 5559: // Venom - Teleport
			{
				useSkill(player, 4995, true);
				break;
			}
			
			case 5560: // Venom - Range Teleport
			{
				useSkill(player, 4996, true);
				break;
			}
			
			case 5561: // Lidia - Twister
			{
				useSkill(player, 4998, true);
				break;
			}
			
			case 5562: // Lidia - Range Life Drain
			{
				useSkill(player, 4999, true);
				break;
			}
			
			case 5563: // Alfred - Super Strike
			{
				useSkill(player, 5000, true);
				break;
			}
			
			case 5564: // Alfred - Life Drain
			{
				useSkill(player, 5001, true);
				break;
			}
			
			case 5565: // Giselle - Vampiric Rage
			{
				useSkill(player, 5002, true);
				break;
			}
			
			case 5566: // Giselle - Tempest
			{
				useSkill(player, 5003, true);
				break;
			}
			
			case 5567: // Dimensional Stun
			{
				useSkill(player, 5004, true);
				break;
			}
			
			case 5568: // Frintezza's Ghostly Fighter
			{
				useSkill(player, 5009, true);
				break;
			}
			
			case 5569: // Frintezza's Ghostly Mage
			{
				useSkill(player, 5010, true);
				break;
			}
			
			case 5570: // Frintezza's Bomber Ghost
			{
				useSkill(player, 5011, true);
				break;
			}
			
			case 5571: // Breath of Scarlet
			{
				useSkill(player, 5012, true);
				break;
			}
			
			case 5572: // Frintezza's Daemon Attack
			{
				useSkill(player, 5014, true);
				break;
			}
			
			case 5573: // Frintezza's Daemon Morph
			{
				useSkill(player, 5017, true);
				break;
			}
			
			case 5574: // Frintezza's Daemon Field
			{
				useSkill(player, 5018, true);
				break;
			}
			
			case 5575: // Frintezza's Daemon Drain
			{
				useSkill(player, 5019, true);
				break;
			}
			
			case 5576: // Inspiration of Einhasad
			{
				useSkill(player, 5021, true);
				break;
			}
			
			case 5577: // Decrease Atk. Spd.
			{
				useSkill(player, 5024, true);
				break;
			}
			
			case 5578: // NPC - Healing Potion
			{
				useSkill(player, 5040, true);
				break;
			}
			
			case 5579: // NPC Dispel Bomb
			{
				useSkill(player, 5042, true);
				break;
			}
			
			case 5580: // NPC Super Sonic Blaster
			{
				useSkill(player, 5043, true);
				break;
			}
			
			case 5581: // NPC Ultimate Defense
			{
				useSkill(player, 5044, true);
				break;
			}
			
			case 5582: // Castle Power Strike
			{
				useSkill(player, 5045, true);
				break;
			}
			
			case 5583: // Castle Power Shot
			{
				useSkill(player, 5046, true);
				break;
			}
			
			case 5584: // Castle Self AE Fire
			{
				useSkill(player, 5051, true);
				break;
			}
			
			case 5585: // Castle Long AE Fire
			{
				useSkill(player, 5052, true);
				break;
			}
			
			case 5586: // Castle DD Water
			{
				useSkill(player, 5053, true);
				break;
			}
			
			case 5587: // Castle DD Wind
			{
				useSkill(player, 5056, true);
				break;
			}
			
			case 5588: // Castle DD Unholy
			{
				useSkill(player, 5065, true);
				break;
			}
			
			case 5589: // NPC Remove Death Penalty
			{
				useSkill(player, 5077, true);
				break;
			}
			
			case 5590: // NPC Focused Haste
			{
				useSkill(player, 5079, true);
				break;
			}
			
			case 5591: // NPC Mighty Haste
			{
				useSkill(player, 5080, true);
				break;
			}
			
			case 5592: // Silence
			{
				useSkill(player, 5081, true);
				break;
			}
			
			case 5593: // NPC Spinning, Slashing Trick
			{
				useSkill(player, 5082, true);
				break;
			}
			
			case 5594: // Stun
			{
				useSkill(player, 5083, true);
				break;
			}
			
			case 5595: // NPC Blinding Blow
			{
				useSkill(player, 5084, true);
				break;
			}
			
			case 5596: // Anesthesia
			{
				useSkill(player, 5085, true);
				break;
			}
			
			case 5597: // Deadly Poison
			{
				useSkill(player, 5086, true);
				break;
			}
			
			case 5598: // Berserk
			{
				useSkill(player, 5087, true);
				break;
			}
			
			case 5599: // Sailren Production
			{
				useSkill(player, 5090, true);
				break;
			}
			
			case 5600: // Sailren Production 2
			{
				useSkill(player, 5091, true);
				break;
			}
			
			case 5601: // Antharas Terror
			{
				useSkill(player, 5092, true);
				break;
			}
			
			case 5602: // Antharas Meteor
			{
				useSkill(player, 5093, true);
				break;
			}
			
			case 5603: // Antharas Subordinate Suicide
			{
				useSkill(player, 5094, true);
				break;
			}
			
			case 5604: // Antharas Subordinate Melee Attack
			{
				useSkill(player, 5095, true);
				break;
			}
			
			case 5605: // Antharas Subordinate Remote Attack
			{
				useSkill(player, 5096, true);
				break;
			}
			
			case 5606: // Antharas Subordinate Suicide (Narrow Range)
			{
				useSkill(player, 5097, true);
				break;
			}
			
			case 5607: // Capture Penalty
			{
				useSkill(player, 5098, true);
				break;
			}
			
			case 5608: // Cancel Capture A
			{
				useSkill(player, 5099, true);
				break;
			}
			
			case 5609: // Cancel Capture B
			{
				useSkill(player, 5100, true);
				break;
			}
			
			case 5610: // Cancel Capture C
			{
				useSkill(player, 5101, true);
				break;
			}
			
			case 5611: // Cancel All Capture
			{
				useSkill(player, 5102, true);
				break;
			}
			
			case 5612: // Production - Clan / Transfer
			{
				useSkill(player, 5103, true);
				break;
			}
			
			case 5613: // Battle Force
			{
				useSkill(player, 5104, true);
				break;
			}
			
			case 5614: // Spell Force
			{
				useSkill(player, 5105, true);
				break;
			}
			
			case 5615: // Capture A State
			{
				useSkill(player, 5106, true);
				break;
			}
			
			case 5616: // Capture B State
			{
				useSkill(player, 5107, true);
				break;
			}
			
			case 5617: // Capture C State
			{
				useSkill(player, 5108, true);
				break;
			}
			
			case 5618: // Cannon Fodder
			{
				useSkill(player, 5110, true);
				break;
			}
			
			case 5619: // Big Bang
			{
				useSkill(player, 5111, true);
				break;
			}
			
			case 5620: // Stun
			{
				useSkill(player, 5112, true);
				break;
			}
			
			case 5621: // Castle Self AE Dispell Buff
			{
				useSkill(player, 5113, true);
				break;
			}
			
			case 5622: // Hold
			{
				useSkill(player, 5114, true);
				break;
			}
			
			case 5623: // Stun
			{
				useSkill(player, 5117, true);
				break;
			}
			
			case 5624: // Cancel Sailren Use
			{
				useSkill(player, 5118, true);
				break;
			}
			
			case 5625: // Bleed
			{
				useSkill(player, 5119, true);
				break;
			}
			
			case 5626: // Stun
			{
				useSkill(player, 5120, true);
				break;
			}
			
			case 5627: // Sailren Use Might
			{
				useSkill(player, 5122, true);
				break;
			}
			
			case 5628: // Maximum Defense
			{
				useSkill(player, 5123, true);
				break;
			}
			
			case 5629: // Anti-Music
			{
				useSkill(player, 5124, true);
				break;
			}
			
			case 5630: // Maximum Resist Status
			{
				useSkill(player, 5125, true);
				break;
			}
			
			case 5631: // Maximum Recovery
			{
				useSkill(player, 5126, true);
				break;
			}
			
			case 5632: // Recover Force
			{
				useSkill(player, 5127, true);
				break;
			}
			
			case 5633: // Maximize long-range weapon use
			{
				useSkill(player, 5128, true);
				break;
			}
			
			case 5634: // Smokescreen
			{
				useSkill(player, 5129, true);
				break;
			}
			
			case 5635: // Volcano
			{
				useSkill(player, 5130, true);
				break;
			}
			
			case 5636: // Tsunami
			{
				useSkill(player, 5131, true);
				break;
			}
			
			case 5637: // Cyclone
			{
				useSkill(player, 5132, true);
				break;
			}
			
			case 5638: // Gehenna
			{
				useSkill(player, 5133, true);
				break;
			}
			
			case 5639: // Anti-Summoning Field
			{
				useSkill(player, 5134, true);
				break;
			}
			
			case 5640: // Slash
			{
				useSkill(player, 5135, true);
				break;
			}
			
			case 5641: // Spin Slash
			{
				useSkill(player, 5136, true);
				break;
			}
			
			case 5642: // Hold of King
			{
				useSkill(player, 5137, true);
				break;
			}
			
			case 5643: // Whiplash
			{
				useSkill(player, 5138, true);
				break;
			}
			
			case 5644: // Tidal Wave
			{
				useSkill(player, 5139, true);
				break;
			}
			
			case 5645: // Dark Curse
			{
				useSkill(player, 5140, true);
				break;
			}
			
			case 5646: // Dicing Death
			{
				useSkill(player, 5141, true);
				break;
			}
			
			case 5647: // Corpse Kaboom
			{
				useSkill(player, 5142, true);
				break;
			}
			
			case 5648: // Sailren Use Blow
			{
				useSkill(player, 5143, true);
				break;
			}
			
			case 5649: // Day of Doom
			{
				useSkill(player, 5145, true);
				break;
			}
			
			case 5650: // NPC Burn
			{
				useSkill(player, 5178, true);
				break;
			}
			
			case 5651: // Dimensional Stun
			{
				useSkill(player, 5183, true);
				break;
			}
			
			case 5652: // Dragon Breath
			{
				useSkill(player, 5184, true);
				break;
			}
			
			case 5653: // Production: Magic-type Guard
			{
				useSkill(player, 5185, true);
				break;
			}
			
			case 5654: // Pet Haste
			{
				useSkill(player, 5186, true);
				break;
			}
			
			case 5655: // Pet Vampiric Rage
			{
				useSkill(player, 5187, true);
				break;
			}
			
			case 5656: // Pet Regeneration
			{
				useSkill(player, 5188, true);
				break;
			}
			
			case 5657: // Pet Blessed Body
			{
				useSkill(player, 5189, true);
				break;
			}
			
			case 5658: // Pet Blessed Soul
			{
				useSkill(player, 5190, true);
				break;
			}
			
			case 5659: // Pet Guidance
			{
				useSkill(player, 5191, true);
				break;
			}
			
			case 5660: // Pet Wind Walk
			{
				useSkill(player, 5192, true);
				break;
			}
			
			case 5661: // Pet Acumen
			{
				useSkill(player, 5193, true);
				break;
			}
			
			case 5662: // Pet Empower
			{
				useSkill(player, 5194, true);
				break;
			}
			
			case 5663: // Pet Greater Heal
			{
				useSkill(player, 5195, true);
				break;
			}
			
			case 5664: // Pet Wind Shackle
			{
				useSkill(player, 5196, true);
				break;
			}
			
			case 5665: // Pet Hex
			{
				useSkill(player, 5197, true);
				break;
			}
			
			case 5666: // Pet Slow
			{
				useSkill(player, 5198, true);
				break;
			}
			
			case 5667: // Pet Curse Gloom
			{
				useSkill(player, 5199, true);
				break;
			}
			
			case 5668: // Pet Recharge
			{
				useSkill(player, 5200, true);
				break;
			}
			
			case 5669: // Pet Concentration
			{
				useSkill(player, 5201, true);
				break;
			}
			
			case 5670: // Stun
			{
				useSkill(player, 5202, true);
				break;
			}
			
			case 5671: // Fear
			{
				useSkill(player, 5203, true);
				break;
			}
			
			case 5672: // Decrease Speed
			{
				useSkill(player, 5206, true);
				break;
			}
			
			case 5673: // Decrease Atk. Spd.
			{
				useSkill(player, 5207, true);
				break;
			}
			
			case 5674: // Event Wind walk
			{
				useSkill(player, 5208, true);
				break;
			}
			
			case 5675: // Event Shield
			{
				useSkill(player, 5209, true);
				break;
			}
			
			case 5676: // Event Bless the body
			{
				useSkill(player, 5210, true);
				break;
			}
			
			case 5677: // Event Regeneration
			{
				useSkill(player, 5212, true);
				break;
			}
			
			case 5678: // Event Haste
			{
				useSkill(player, 5213, true);
				break;
			}
			
			case 5679: // Event Bless the soul
			{
				useSkill(player, 5214, true);
				break;
			}
			
			case 5680: // Event Acumen
			{
				useSkill(player, 5215, true);
				break;
			}
			
			case 5681: // Event Concentration
			{
				useSkill(player, 5216, true);
				break;
			}
			
			case 5682: // Event Empower
			{
				useSkill(player, 5217, true);
				break;
			}
			
			case 5683: // Production: Event Teleport
			{
				useSkill(player, 5218, true);
				break;
			}
			
			case 5684: // Stun of giant mutated animal
			{
				useSkill(player, 5219, true);
				break;
			}
			
			case 5685: // Fear of giant mutated animal
			{
				useSkill(player, 5220, true);
				break;
			}
			
			case 5686: // Balor - Physical Close Range Weak Point
			{
				useSkill(player, 5221, true);
				break;
			}
			
			case 5687: // Balor - Physical Long Range Weak Point
			{
				useSkill(player, 5222, true);
				break;
			}
			
			case 5688: // Balor - Magic Weak Point
			{
				useSkill(player, 5223, true);
				break;
			}
			
			case 5689: // Berserk
			{
				useSkill(player, 5224, true);
				break;
			}
			
			case 5690: // Invincible
			{
				useSkill(player, 5225, true);
				break;
			}
			
			case 5691: // Imprison
			{
				useSkill(player, 5226, true);
				break;
			}
			
			case 5692: // Ground Strike
			{
				useSkill(player, 5227, true);
				break;
			}
			
			case 5693: // Jump Attack
			{
				useSkill(player, 5228, true);
				break;
			}
			
			case 5694: // Strong Punch
			{
				useSkill(player, 5229, true);
				break;
			}
			
			case 5695: // Stun
			{
				useSkill(player, 5230, true);
				break;
			}
			
			case 5696: // Stun
			{
				useSkill(player, 5231, true);
				break;
			}
			
			case 5697: // Stun
			{
				useSkill(player, 5232, true);
				break;
			}
			
			case 5698: // Weight Spin Attack - Weak
			{
				useSkill(player, 5233, true);
				break;
			}
			
			case 5699: // Weight Spin Attack - Mid
			{
				useSkill(player, 5234, true);
				break;
			}
			
			case 5700: // Weight Spin Attack - Strong
			{
				useSkill(player, 5235, true);
				break;
			}
			
			case 5701: // Freezing
			{
				useSkill(player, 5238, true);
				break;
			}
			
			case 5702: // Event Timer
			{
				useSkill(player, 5239, true);
				break;
			}
			
			case 5703: // Sickness
			{
				useSkill(player, 5242, true);
				break;
			}
			
			case 5704: // Boss Dark Explosion
			{
				useSkill(player, 5246, true);
				break;
			}
			
			case 5705: // Seed of Darkness
			{
				useSkill(player, 5247, true);
				break;
			}
			
			case 5706: // Boss Dark Circle
			{
				useSkill(player, 5249, true);
				break;
			}
			
			case 5707: // Stun
			{
				useSkill(player, 5250, true);
				break;
			}
			
			case 5708: // Poison
			{
				useSkill(player, 5251, true);
				break;
			}
			
			case 5709: // Bleed
			{
				useSkill(player, 5253, true);
				break;
			}
			
			case 5710: // Invasion of Spirit
			{
				useSkill(player, 5254, true);
				break;
			}
			
			case 5711: // Announcement of Death
			{
				useSkill(player, 5256, true);
				break;
			}
			
			case 5712: // Death
			{
				useSkill(player, 5257, true);
				break;
			}
			
			case 5713: // Fear
			{
				useSkill(player, 5259, true);
				break;
			}
			
			case 5714: // Disarm
			{
				useSkill(player, 5260, true);
				break;
			}
			
			case 5715: // NPC - Rise Shot
			{
				useSkill(player, 5262, true);
				break;
			}
			
			case 5716: // NPC _ Chain Lightning
			{
				useSkill(player, 5263, true);
				break;
			}
			
			case 5717: // Death Mark
			{
				useSkill(player, 5264, true);
				break;
			}
			
			case 5718: // NPC - Soul Emission
			{
				useSkill(player, 5265, true);
				break;
			}
			
			case 5719: // Magical Backfire
			{
				useSkill(player, 5266, true);
				break;
			}
			
			case 5720: // Trap Explosion
			{
				useSkill(player, 5267, true);
				break;
			}
			
			case 5721: // Poison
			{
				useSkill(player, 5268, true);
				break;
			}
			
			case 5722: // Slow Trap
			{
				useSkill(player, 5269, true);
				break;
			}
			
			case 5723: // Flash Trap
			{
				useSkill(player, 5270, true);
				break;
			}
			
			case 5724: // Hold
			{
				useSkill(player, 5271, true);
				break;
			}
			
			case 5725: // Decoy Provocation
			{
				useSkill(player, 5272, true);
				break;
			}
			
			case 5726: // NPC(party) - Physical Single Close Range Attack
			{
				useSkill(player, 5273, true);
				break;
			}
			
			case 5727: // NPC(party) - Physical Single Long Range Attack
			{
				useSkill(player, 5274, true);
				break;
			}
			
			case 5728: // NPC(party) - Physical Range Close Range Attack
			{
				useSkill(player, 5275, true);
				break;
			}
			
			case 5729: // NPC(party) - Physical Single Close Range Attack - Fire
			{
				useSkill(player, 5277, true);
				break;
			}
			
			case 5730: // NPC(party) - Physical Range Close Range Attack - Fire
			{
				useSkill(player, 5279, true);
				break;
			}
			
			case 5731: // NPC(party) - Physical Range Long Range Attack - Fire
			{
				useSkill(player, 5280, true);
				break;
			}
			
			case 5732: // NPC(party) - Physical Single Long Range Attack - Water
			{
				useSkill(player, 5282, true);
				break;
			}
			
			case 5733: // NPC(party) - Physical Range Close Range Attack - Water
			{
				useSkill(player, 5283, true);
				break;
			}
			
			case 5734: // NPC(party) - Physical Single Long Range Attack - Wind
			{
				useSkill(player, 5286, true);
				break;
			}
			
			case 5735: // NPC(party) - Physical Single Close Range Attack - Dark
			{
				useSkill(player, 5297, true);
				break;
			}
			
			case 5736: // NPC(party) - Physical Single Long Range Attack - Dark
			{
				useSkill(player, 5298, true);
				break;
			}
			
			case 5737: // NPC(party) - Physical Range Close Range Attack - Dark
			{
				useSkill(player, 5299, true);
				break;
			}
			
			case 5738: // NPC(party) - Physical Range Long Range Attack - Dark
			{
				useSkill(player, 5300, true);
				break;
			}
			
			case 5739: // Stun
			{
				useSkill(player, 5301, true);
				break;
			}
			
			case 5740: // Poison
			{
				useSkill(player, 5302, true);
				break;
			}
			
			case 5741: // Bleed
			{
				useSkill(player, 5303, true);
				break;
			}
			
			case 5742: // Paralysis
			{
				useSkill(player, 5304, true);
				break;
			}
			
			case 5743: // Poison
			{
				useSkill(player, 5305, true);
				break;
			}
			
			case 5744: // Paralysis
			{
				useSkill(player, 5306, true);
				break;
			}
			
			case 5745: // Stun
			{
				useSkill(player, 5307, true);
				break;
			}
			
			case 5746: // Bleed
			{
				useSkill(player, 5309, true);
				break;
			}
			
			case 5747: // NPC(party) - Magic Close Range DD - less powerful
			{
				useSkill(player, 5310, true);
				break;
			}
			
			case 5748: // NPC(party) - Magic Close Range DD - less powerful
			{
				useSkill(player, 5311, true);
				break;
			}
			
			case 5749: // NPC(party) -Magic Single Long Range DD - Fire
			{
				useSkill(player, 5312, true);
				break;
			}
			
			case 5750: // NPC(party) -Magic Range Long Range DD - Fire
			{
				useSkill(player, 5313, true);
				break;
			}
			
			case 5751: // NPC(party) -Magic Range Close Range DD - Fire
			{
				useSkill(player, 5314, true);
				break;
			}
			
			case 5752: // NPC(party) -Magic Range Close Range DD - Fire(Self-Destruction)
			{
				useSkill(player, 5315, true);
				break;
			}
			
			case 5753: // NPC(party) -Magic Single Long Range DD - Water
			{
				useSkill(player, 5316, true);
				break;
			}
			
			case 5754: // NPC(party) -Magic Range Long Range DD - Water
			{
				useSkill(player, 5317, true);
				break;
			}
			
			case 5755: // NPC(party) -Magic Range Close Range DD - Water
			{
				useSkill(player, 5318, true);
				break;
			}
			
			case 5756: // NPC(party) -Magic Single Long Range DD - Dark
			{
				useSkill(player, 5328, true);
				break;
			}
			
			case 5757: // NPC(party) -Magic Range Long Range DD - Holy
			{
				useSkill(player, 5329, true);
				break;
			}
			
			case 5758: // NPC(party) -Magic Range Close Range DD - Holy
			{
				useSkill(player, 5330, true);
				break;
			}
			
			case 5759: // NPC HP Drain
			{
				useSkill(player, 5331, true);
				break;
			}
			
			case 5760: // NPC MP Burn
			{
				useSkill(player, 5332, true);
				break;
			}
			
			case 5761: // Poison
			{
				useSkill(player, 5333, true);
				break;
			}
			
			case 5762: // NPC(party) - Physical Single Close Range Attack
			{
				useSkill(player, 5334, true);
				break;
			}
			
			case 5763: // NPC(party) - Physical Single Long Range Attack
			{
				useSkill(player, 5335, true);
				break;
			}
			
			case 5764: // NPC(party) - Physical Range Close Range Attack
			{
				useSkill(player, 5336, true);
				break;
			}
			
			case 5765: // NPC(party) - Physical Single Close Range Attack - Fire
			{
				useSkill(player, 5338, true);
				break;
			}
			
			case 5766: // NPC(party) - Physical Range Close Range Attack - Fire
			{
				useSkill(player, 5340, true);
				break;
			}
			
			case 5767: // NPC(party) - Physical Single Close Range Attack - Water
			{
				useSkill(player, 5342, true);
				break;
			}
			
			case 5768: // NPC(party) - Physical Single Close Range Attack - Wind
			{
				useSkill(player, 5346, true);
				break;
			}
			
			case 5769: // NPC(party) - Physical Single Close Range Attack - Earth
			{
				useSkill(player, 5350, true);
				break;
			}
			
			case 5770: // NPC(party) - Physical Range Close Range Attack - Earth
			{
				useSkill(player, 5352, true);
				break;
			}
			
			case 5771: // NPC(party) - Physical Range Close Range Attack - Holy
			{
				useSkill(player, 5356, true);
				break;
			}
			
			case 5772: // NPC(party) - Physical Single Close Range Attack - Dark
			{
				useSkill(player, 5358, true);
				break;
			}
			
			case 5773: // NPC(party) - Physical Single Long Range Attack - Dark
			{
				useSkill(player, 5359, true);
				break;
			}
			
			case 5774: // NPC(party) - Physical Range Close Range Attack - Dark
			{
				useSkill(player, 5360, true);
				break;
			}
			
			case 5775: // Stun
			{
				useSkill(player, 5362, true);
				break;
			}
			
			case 5776: // Bleed
			{
				useSkill(player, 5364, true);
				break;
			}
			
			case 5777: // Paralysis
			{
				useSkill(player, 5365, true);
				break;
			}
			
			case 5778: // Paralysis
			{
				useSkill(player, 5367, true);
				break;
			}
			
			case 5779: // NPC(party) - Magic Close Range DD - less powerful
			{
				useSkill(player, 5371, true);
				break;
			}
			
			case 5780: // NPC(party) - Magic Close Range DD - less powerful
			{
				useSkill(player, 5372, true);
				break;
			}
			
			case 5781: // NPC(party) -Magic Range Long Range DD - Fire
			{
				useSkill(player, 5374, true);
				break;
			}
			
			case 5782: // NPC(party) -Magic Range Close Range DD - Fire
			{
				useSkill(player, 5375, true);
				break;
			}
			
			case 5783: // NPC(party) -Magic Range Close Range DD - Fire(Self-Destruction)
			{
				useSkill(player, 5376, true);
				break;
			}
			
			case 5784: // NPC(party) -Magic Single Long Range DD - Wind
			{
				useSkill(player, 5380, true);
				break;
			}
			
			case 5785: // NPC(party) -Magic Range Close Range DD - Wind
			{
				useSkill(player, 5382, true);
				break;
			}
			
			case 5786: // NPC(party) -Magic Single Long Range DD - Earth
			{
				useSkill(player, 5383, true);
				break;
			}
			
			case 5787: // NPC(party) -Magic Single Long Range DD - Dark
			{
				useSkill(player, 5389, true);
				break;
			}
			
			case 5788: // NPC(party) -Magic Range Long Range DD - Holy
			{
				useSkill(player, 5390, true);
				break;
			}
			
			case 5789: // NPC(party) -Magic Range Close Range DD - Holy
			{
				useSkill(player, 5391, true);
				break;
			}
			
			case 5790: // NPC HP Drain
			{
				useSkill(player, 5392, true);
				break;
			}
			
			case 5791: // NPC MP Burn
			{
				useSkill(player, 5393, true);
				break;
			}
			
			case 5792: // NPC Clan Buff - Super Might Haste
			{
				useSkill(player, 5395, true);
				break;
			}
			
			case 5793: // NPC - Spell Stance
			{
				useSkill(player, 5396, true);
				break;
			}
			
			case 5794: // NPC - Combination Force
			{
				useSkill(player, 5398, true);
				break;
			}
			
			case 5795: // Stun
			{
				useSkill(player, 5401, true);
				break;
			}
			
			case 5796: // Presentation - Balor 2
			{
				useSkill(player, 5402, true);
				break;
			}
			
			case 5797: // Presentation - Balor 3
			{
				useSkill(player, 5403, true);
				break;
			}
			
			case 5798: // Presentation - Balor 4
			{
				useSkill(player, 5404, true);
				break;
			}
			
			case 5799: // Presentation - Demonic 1
			{
				useSkill(player, 5405, true);
				break;
			}
			
			case 5800: // Presentation - Demonic 2
			{
				useSkill(player, 5406, true);
				break;
			}
			
			case 5801: // Presentation - Crystalline Golem 1
			{
				useSkill(player, 5407, true);
				break;
			}
			
			case 5802: // Presentation - Crystalline Golem 2
			{
				useSkill(player, 5408, true);
				break;
			}
			
			case 5803: // Presentation - Crystalline Golem 3
			{
				useSkill(player, 5409, true);
				break;
			}
			
			case 5804: // Performing Agathion - Beast Farm
			{
				useSkill(player, 5413, true);
				break;
			}
			
			case 5805: // Performing Agathion - Rainbow Clan Hall
			{
				useSkill(player, 5414, true);
				break;
			}
			
			case 5806: // Performing Agathion - Castle
			{
				useSkill(player, 5415, true);
				break;
			}
			
			case 5807: // Invincible
			{
				useSkill(player, 5417, true);
				break;
			}
			
			case 5808: // Invincible
			{
				useSkill(player, 5418, true);
				break;
			}
			
			case 5809: // Invincible
			{
				useSkill(player, 5420, true);
				break;
			}
			
			case 5810: // Unknown Skill 5421
			{
				useSkill(player, 5421, true);
				break;
			}
			
			case 5811: // Flame
			{
				useSkill(player, 5422, true);
				break;
			}
			
			case 5812: // Poison
			{
				useSkill(player, 5423, true);
				break;
			}
			
			case 5813: // Bleed
			{
				useSkill(player, 5424, true);
				break;
			}
			
			case 5814: // Weapon Supply
			{
				useSkill(player, 5432, true);
				break;
			}
			
			case 5815: // NPC party 30 Clan Heal
			{
				useSkill(player, 5438, true);
				break;
			}
			
			case 5816: // NPC party 60 Clan Heal
			{
				useSkill(player, 5439, true);
				break;
			}
			
			case 5817: // Presentation - Trap Activate
			{
				useSkill(player, 5440, true);
				break;
			}
			
			case 5818: // Presentation - Tears Mirror Image
			{
				useSkill(player, 5441, true);
				break;
			}
			
			case 5819: // Bite Attack
			{
				useSkill(player, 5442, true);
				break;
			}
			
			case 5820: // Cry of the Wolf
			{
				useSkill(player, 5443, true);
				break;
			}
			
			case 5821: // Maul
			{
				useSkill(player, 5444, true);
				break;
			}
			
			case 5822: // Awakening
			{
				useSkill(player, 5445, true);
				break;
			}
			
			case 5823: // Stubborn Resistance
			{
				useSkill(player, 5456, true);
				break;
			}
			
			case 5824: // NPC Full Recover
			{
				useSkill(player, 5457, true);
				break;
			}
			
			case 5825: // Performing Agathion - Fortress
			{
				useSkill(player, 5458, true);
				break;
			}
			
			case 5826: // Castle Gunner Shot
			{
				useSkill(player, 5461, true);
				break;
			}
			
			case 5827: // Castle Gunner Fire
			{
				useSkill(player, 5468, true);
				break;
			}
			
			case 5828: // NPC CrossBow Attack
			{
				useSkill(player, 5492, true);
				break;
			}
			
			case 5829: // Bleed
			{
				useSkill(player, 5495, true);
				break;
			}
			
			case 5830: // Dark Fireball
			{
				useSkill(player, 5496, true);
				break;
			}
			
			case 5831: // Horn of Rising Darkness
			{
				useSkill(player, 5497, true);
				break;
			}
			
			case 5832: // Self-Destruct
			{
				useSkill(player, 5498, true);
				break;
			}
			
			case 5833: // Dark Lightening
			{
				useSkill(player, 5499, true);
				break;
			}
			
			case 5834: // Black Dragon Claw
			{
				useSkill(player, 5500, true);
				break;
			}
			
			case 5835: // Stuns
			{
				useSkill(player, 5501, true);
				break;
			}
			
			case 5836: // Stuns
			{
				useSkill(player, 5502, true);
				break;
			}
			
			case 5837: // Ultimate Guard
			{
				useSkill(player, 5503, true);
				break;
			}
			
			case 5838: // Shield Defense
			{
				useSkill(player, 5504, true);
				break;
			}
			
			case 5839: // Fire Blossom
			{
				useSkill(player, 5505, true);
				break;
			}
			
			case 5840: // Water Blossom
			{
				useSkill(player, 5506, true);
				break;
			}
			
			case 5841: // Wind Blossom
			{
				useSkill(player, 5507, true);
				break;
			}
			
			case 5842: // Earth Blossom
			{
				useSkill(player, 5508, true);
				break;
			}
			
			case 5843: // Fire Power
			{
				useSkill(player, 5509, true);
				break;
			}
			
			case 5844: // Water Power
			{
				useSkill(player, 5510, true);
				break;
			}
			
			case 5845: // Wind Power
			{
				useSkill(player, 5511, true);
				break;
			}
			
			case 5846: // Earth Power
			{
				useSkill(player, 5512, true);
				break;
			}
			
			case 5847: // Fire Taint
			{
				useSkill(player, 5513, true);
				break;
			}
			
			case 5848: // Water Taint
			{
				useSkill(player, 5514, true);
				break;
			}
			
			case 5849: // Wind Taint
			{
				useSkill(player, 5515, true);
				break;
			}
			
			case 5850: // Earth Taint
			{
				useSkill(player, 5516, true);
				break;
			}
			
			case 5851: // Nurture
			{
				useSkill(player, 5517, true);
				break;
			}
			
			case 5852: // Chain Buff - Power Up
			{
				useSkill(player, 5519, true);
				break;
			}
			
			case 5853: // Chain Buff - Vampiric Shield
			{
				useSkill(player, 5520, true);
				break;
			}
			
			case 5854: // Chain Buff - Critical Sense
			{
				useSkill(player, 5521, true);
				break;
			}
			
			case 5855: // Chain Magic - Dark Explosion
			{
				useSkill(player, 5522, true);
				break;
			}
			
			case 5856: // Chain Magic - Unholy Castle
			{
				useSkill(player, 5523, true);
				break;
			}
			
			case 5857: // Chain Buff - Resistance to Bow and Magic attacks
			{
				useSkill(player, 5524, true);
				break;
			}
			
			case 5858: // Chain Buff - Resistance to Melee Attacks
			{
				useSkill(player, 5525, true);
				break;
			}
			
			case 5859: // Challenger's Blessing
			{
				useSkill(player, 5526, true);
				break;
			}
			
			case 5860: // Overflow
			{
				useSkill(player, 5527, true);
				break;
			}
			
			case 5861: // Self-Destruct
			{
				useSkill(player, 5528, true);
				break;
			}
			
			case 5862: // Surrender to the Unholy
			{
				useSkill(player, 5529, true);
				break;
			}
			
			case 5863: // Direction-Beleth Magic Skill 1
			{
				useSkill(player, 5531, true);
				break;
			}
			
			case 5864: // Direction-Beleth Summon Skill 1
			{
				useSkill(player, 5532, true);
				break;
			}
			
			case 5865: // Direction-Beleth Self-Destruction Skill 1
			{
				useSkill(player, 5533, true);
				break;
			}
			
			case 5866: // Little Angel Cuteness Trick
			{
				useSkill(player, 5535, true);
				break;
			}
			
			case 5867: // Little Devil Cuteness Trick
			{
				useSkill(player, 5536, true);
				break;
			}
			
			case 5868: // Rudolph Cuteness Trick
			{
				useSkill(player, 5537, true);
				break;
			}
			
			case 5869: // Little Angel Agathion Special Skill - Firecrackers
			{
				useSkill(player, 5538, true);
				break;
			}
			
			case 5870: // Little Angel Agathion Special Skill - Mysterious Power
			{
				useSkill(player, 5539, true);
				break;
			}
			
			case 5871: // Little Angel Agathion Special Skill - Blessed Escape
			{
				useSkill(player, 5540, true);
				break;
			}
			
			case 5872: // Little Angel Agathion Special Skill - Blessed Resurrection
			{
				useSkill(player, 5541, true);
				break;
			}
			
			case 5873: // Little Devil Agathion Special Skill - Firecrackers
			{
				useSkill(player, 5542, true);
				break;
			}
			
			case 5874: // Little Devil Agathion Special Skill - Mysterious Power
			{
				useSkill(player, 5543, true);
				break;
			}
			
			case 5875: // Little Devil Agathion Special Skill - Blessed Escape
			{
				useSkill(player, 5544, true);
				break;
			}
			
			case 5876: // Little Devil Agathion Special Skill - Blessed Resurrection
			{
				useSkill(player, 5545, true);
				break;
			}
			
			case 5877: // Alert
			{
				useSkill(player, 5546, true);
				break;
			}
			
			case 5878: // Expose Weak Point
			{
				useSkill(player, 5565, true);
				break;
			}
			
			case 5879: // Divine Beast Bite
			{
				useSkill(player, 5580, true);
				break;
			}
			
			case 5880: // Divine Beast Stun Attack
			{
				useSkill(player, 5581, true);
				break;
			}
			
			case 5881: // Divine Beast Fire Breath
			{
				useSkill(player, 5582, true);
				break;
			}
			
			case 5882: // Divine Beast Roar
			{
				useSkill(player, 5583, true);
				break;
			}
			
			case 5883: // Wolf Howl
			{
				useSkill(player, 5584, true);
				break;
			}
			
			case 5884: // Strider Roar
			{
				useSkill(player, 5585, true);
				break;
			}
			
			case 5885: // Pet Might
			{
				useSkill(player, 5586, true);
				break;
			}
			
			case 5886: // Pet Shield
			{
				useSkill(player, 5587, true);
				break;
			}
			
			case 5887: // Pet Focus
			{
				useSkill(player, 5588, true);
				break;
			}
			
			case 5888: // Pet Death Whisper
			{
				useSkill(player, 5589, true);
				break;
			}
			
			case 5889: // Pet Battle Heal
			{
				useSkill(player, 5590, true);
				break;
			}
			
			case 5890: // Vampiric Mana Burn
			{
				useSkill(player, 5593, true);
				break;
			}
			
			case 5891: // Weakened Magic Force
			{
				useSkill(player, 5623, true);
				break;
			}
			
			case 5892: // Soul Confinement
			{
				useSkill(player, 5624, true);
				break;
			}
			
			case 5893: // Soul Confinement
			{
				useSkill(player, 5625, true);
				break;
			}
			
			case 5894: // Feline Queen - Bless the Body
			{
				useSkill(player, 5638, true);
				break;
			}
			
			case 5895: // Feline Queen - Bless the Soul
			{
				useSkill(player, 5639, true);
				break;
			}
			
			case 5896: // Feline Queen - Haste
			{
				useSkill(player, 5640, true);
				break;
			}
			
			case 5897: // Unicorn Seraphim - Acumen
			{
				useSkill(player, 5643, true);
				break;
			}
			
			case 5898: // not_used
			{
				useSkill(player, 5646, true);
				break;
			}
			
			case 5899: // Unicorn Seraphim - Clarity
			{
				useSkill(player, 5647, true);
				break;
			}
			
			case 5900: // Unicorn Seraphim - Empower
			{
				useSkill(player, 5648, true);
				break;
			}
			
			case 5901: // Nightshade - Death Whisper
			{
				useSkill(player, 5652, true);
				break;
			}
			
			case 5902: // Nightshade - Focus
			{
				useSkill(player, 5653, true);
				break;
			}
			
			case 5903: // Nightshade - Guidance
			{
				useSkill(player, 5654, true);
				break;
			}
			
			case 5904: // Gatekeeper Aura Flare
			{
				useSkill(player, 5656, true);
				break;
			}
			
			case 5905: // Gatekeeper Prominence
			{
				useSkill(player, 5657, true);
				break;
			}
			
			case 5906: // Gatekeeper Flame Strike
			{
				useSkill(player, 5658, true);
				break;
			}
			
			case 5907: // Oblivion Trap
			{
				useSkill(player, 5679, true);
				break;
			}
			
			case 5908: // Decrease P. Def
			{
				useSkill(player, 5699, true);
				break;
			}
			
			case 5909: // Magic Resistance Decrease
			{
				useSkill(player, 5700, true);
				break;
			}
			
			case 5910: // Decrease P. Atk
			{
				useSkill(player, 5701, true);
				break;
			}
			
			case 5911: // Adiantum Round Fighter
			{
				useSkill(player, 5702, true);
				break;
			}
			
			case 5912: // Adiantum Water Strike Deflect
			{
				useSkill(player, 5703, true);
				break;
			}
			
			case 5913: // Water Strike
			{
				useSkill(player, 5704, true);
				break;
			}
			
			case 5914: // Fire Trap
			{
				useSkill(player, 5705, true);
				break;
			}
			
			case 5915: // Poison
			{
				useSkill(player, 5706, true);
				break;
			}
			
			case 5916: // Paralysis
			{
				useSkill(player, 5707, true);
				break;
			}
			
			case 5917: // Water Cannon
			{
				useSkill(player, 5708, true);
				break;
			}
			
			case 5918: // Whirlpool
			{
				useSkill(player, 5709, true);
				break;
			}
			
			case 5919: // Triple Sword
			{
				useSkill(player, 5710, true);
				break;
			}
			
			case 5920: // Power of Rage
			{
				useSkill(player, 5711, true);
				break;
			}
			
			case 5921: // Energy Ditch
			{
				useSkill(player, 5712, true);
				break;
			}
			
			case 5922: // Electric Flame
			{
				useSkill(player, 5715, true);
				break;
			}
			
			case 5923: // Stun
			{
				useSkill(player, 5716, true);
				break;
			}
			
			case 5924: // Fire Breath
			{
				useSkill(player, 5717, true);
				break;
			}
			
			case 5925: // Anger
			{
				useSkill(player, 5718, true);
				break;
			}
			
			case 5926: // Kamabion Susceptibility
			{
				useSkill(player, 5719, true);
				break;
			}
			
			case 5927: // Blade Cut
			{
				useSkill(player, 5720, true);
				break;
			}
			
			case 5928: // Blade Strike
			{
				useSkill(player, 5721, true);
				break;
			}
			
			case 5929: // Hammer Assault
			{
				useSkill(player, 5722, true);
				break;
			}
			
			case 5930: // Hammer Swing
			{
				useSkill(player, 5723, true);
				break;
			}
			
			case 5931: // Broom Strike
			{
				useSkill(player, 5724, true);
				break;
			}
			
			case 5932: // Broom Trusting
			{
				useSkill(player, 5725, true);
				break;
			}
			
			case 5933: // Scissors Attack
			{
				useSkill(player, 5726, true);
				break;
			}
			
			case 5934: // Scissors Strike
			{
				useSkill(player, 5727, true);
				break;
			}
			
			case 5935: // Shobel Attack
			{
				useSkill(player, 5728, true);
				break;
			}
			
			case 5936: // Shobel Whirlwind
			{
				useSkill(player, 5729, true);
				break;
			}
			
			case 5937: // Made Fireball
			{
				useSkill(player, 5730, true);
				break;
			}
			
			case 5938: // Incense of Death
			{
				useSkill(player, 5731, true);
				break;
			}
			
			case 5939: // Flame Strike
			{
				useSkill(player, 5732, true);
				break;
			}
			
			case 5940: // Fear of Steward
			{
				useSkill(player, 5733, true);
				break;
			}
			
			case 5941: // Gust of Wind
			{
				useSkill(player, 5734, true);
				break;
			}
			
			case 5942: // Curse of Steward
			{
				useSkill(player, 5735, true);
				break;
			}
			
			case 5943: // Katar Trusting
			{
				useSkill(player, 5736, true);
				break;
			}
			
			case 5944: // Power Stamp
			{
				useSkill(player, 5737, true);
				break;
			}
			
			case 5945: // Power Roar
			{
				useSkill(player, 5738, true);
				break;
			}
			
			case 5946: // Death Blow
			{
				useSkill(player, 5745, true);
				break;
			}
			
			case 5947: // Double Attack
			{
				useSkill(player, 5746, true);
				break;
			}
			
			case 5948: // Spin Attack
			{
				useSkill(player, 5747, true);
				break;
			}
			
			case 5949: // Meteor Shower
			{
				useSkill(player, 5748, true);
				break;
			}
			
			case 5950: // Thunder Bolt
			{
				useSkill(player, 5749, true);
				break;
			}
			
			case 5951: // Flash
			{
				useSkill(player, 5750, true);
				break;
			}
			
			case 5952: // Lightning Wave
			{
				useSkill(player, 5751, true);
				break;
			}
			
			case 5953: // Flare
			{
				useSkill(player, 5752, true);
				break;
			}
			
			case 5954: // Awakening
			{
				useSkill(player, 5753, true);
				break;
			}
			
			case 5955: // Presentation - Adiantum Round Fighter
			{
				useSkill(player, 5754, true);
				break;
			}
			
			case 5956: // Presentation - Trap On
			{
				useSkill(player, 5755, true);
				break;
			}
			
			case 5957: // Presentation - Energy Ditch
			{
				useSkill(player, 5757, true);
				break;
			}
			
			case 5958: // Presentation - The Rise of Latana
			{
				useSkill(player, 5759, true);
				break;
			}
			
			case 5959: // Critical Hit
			{
				useSkill(player, 5760, true);
				break;
			}
			
			case 5960: // Power Strike
			{
				useSkill(player, 5761, true);
				break;
			}
			
			case 5961: // Wink
			{
				useSkill(player, 5763, true);
				break;
			}
			
			case 5962: // Naia Sprout
			{
				useSkill(player, 5765, true);
				break;
			}
			
			case 5963: // Naia Sprout
			{
				useSkill(player, 5766, true);
				break;
			}
			
			case 5964: // Naia Sprout
			{
				useSkill(player, 5767, true);
				break;
			}
			
			case 5965: // Naia Sprout
			{
				useSkill(player, 5768, true);
				break;
			}
			
			case 5966: // Buff Control
			{
				useSkill(player, 5771, true);
				break;
			}
			
			case 5967: // Surrender to Fire
			{
				useSkill(player, 5772, true);
				break;
			}
			
			case 5968: // Surrender to Water
			{
				useSkill(player, 5773, true);
				break;
			}
			
			case 5969: // Agathion Collection
			{
				useSkill(player, 5780, true);
				break;
			}
			
			case 5970: // Presentation - Lindvior Approach
			{
				useSkill(player, 5781, true);
				break;
			}
			
			case 5971: // Presentation - Lindvior Gust
			{
				useSkill(player, 5782, true);
				break;
			}
			
			case 5972: // Presentation - Lindvior Descent
			{
				useSkill(player, 5783, true);
				break;
			}
			
			case 5973: // Presentation - Dimensional Door
			{
				useSkill(player, 5785, true);
				break;
			}
			
			case 5974: // Presentation - Ekimus Separation
			{
				useSkill(player, 5786, true);
				break;
			}
			
			case 5975: // Presentation - Ekimus Approach
			{
				useSkill(player, 5787, true);
				break;
			}
			
			case 5976: // Presentation - Ekimus Blood Hail
			{
				useSkill(player, 5788, true);
				break;
			}
			
			case 5977: // Presentation - Ekimus Sneer
			{
				useSkill(player, 5789, true);
				break;
			}
			
			case 5978: // Presentation - Magyun's Entrance
			{
				useSkill(player, 5791, true);
				break;
			}
			
			case 5979: // Presentation - Summon Banshee Queen Boomi
			{
				useSkill(player, 5792, true);
				break;
			}
			
			case 5980: // Presentation - Summon Boomi
			{
				useSkill(player, 5793, true);
				break;
			}
			
			case 5981: // Decrease Spd.
			{
				useSkill(player, 5794, true);
				break;
			}
			
			case 5982: // Poison
			{
				useSkill(player, 5795, true);
				break;
			}
			
			case 5983: // Decrease Spd.
			{
				useSkill(player, 5796, true);
				break;
			}
			
			case 5984: // Bursting Flame
			{
				useSkill(player, 5797, true);
				break;
			}
			
			case 5985: // Fireball
			{
				useSkill(player, 5798, true);
				break;
			}
			
			case 5986: // Bleed
			{
				useSkill(player, 5799, true);
				break;
			}
			
			case 5987: // Drake Breath
			{
				useSkill(player, 5801, true);
				break;
			}
			
			case 5988: // Stun
			{
				useSkill(player, 5802, true);
				break;
			}
			
			case 5989: // Green Chili
			{
				useSkill(player, 5803, true);
				break;
			}
			
			case 5990: // Fireball
			{
				useSkill(player, 5804, true);
				break;
			}
			
			case 5991: // Air Assault
			{
				useSkill(player, 5805, true);
				break;
			}
			
			case 5992: // High Wave Beam
			{
				useSkill(player, 5806, true);
				break;
			}
			
			case 5993: // Self-destruction
			{
				useSkill(player, 5807, true);
				break;
			}
			
			case 5994: // Burst Flame
			{
				useSkill(player, 5808, true);
				break;
			}
			
			case 5995: // Dark Blood
			{
				useSkill(player, 5809, true);
				break;
			}
			
			case 5996: // Decrease Spd.
			{
				useSkill(player, 5810, true);
				break;
			}
			
			case 5997: // Sonic Bomb
			{
				useSkill(player, 5811, true);
				break;
			}
			
			case 5998: // Fire Breath
			{
				useSkill(player, 5812, true);
				break;
			}
			
			case 5999: // Sand Ball
			{
				useSkill(player, 5813, true);
				break;
			}
			
			case 6000: // Self-Destruction
			{
				useSkill(player, 5814, true);
				break;
			}
			
			case 6001: // Presentation - Mercenary Troops Shout
			{
				useSkill(player, 5815, true);
				break;
			}
			
			case 6002: // Presentation - Female Priest Transformation
			{
				useSkill(player, 5816, true);
				break;
			}
			
			case 6003: // Presentation - Repentance
			{
				useSkill(player, 5818, true);
				break;
			}
			
			case 6004: // Presentation - Victory
			{
				useSkill(player, 5819, true);
				break;
			}
			
			case 6005: // Presentation - Removal
			{
				useSkill(player, 5820, true);
				break;
			}
			
			case 6006: // Presentation - Female Priest Exit
			{
				useSkill(player, 5821, true);
				break;
			}
			
			case 6007: // Presentation - District1 Boss Defeat
			{
				useSkill(player, 5823, true);
				break;
			}
			
			case 6008: // Presentation - District1 Boss Arise
			{
				useSkill(player, 5824, true);
				break;
			}
			
			case 6009: // Presentation - Algraia Exit
			{
				useSkill(player, 5825, true);
				break;
			}
			
			case 6010: // Fire Breath
			{
				useSkill(player, 5826, true);
				break;
			}
			
			case 6011: // Fireball
			{
				useSkill(player, 5827, true);
				break;
			}
			
			case 6012: // Unholy Flare
			{
				useSkill(player, 5828, true);
				break;
			}
			
			case 6013: // Fire Flare
			{
				useSkill(player, 5829, true);
				break;
			}
			
			case 6014: // Polearm Thrust
			{
				useSkill(player, 5830, true);
				break;
			}
			
			case 6015: // Polearm Swing
			{
				useSkill(player, 5831, true);
				break;
			}
			
			case 6016: // Hate Aura
			{
				useSkill(player, 5832, true);
				break;
			}
			
			case 6017: // Shield
			{
				useSkill(player, 5833, true);
				break;
			}
			
			case 6018: // Magic Barrier
			{
				useSkill(player, 5834, true);
				break;
			}
			
			case 6019: // Major Heal
			{
				useSkill(player, 5835, true);
				break;
			}
			
			case 6020: // Greater Heal
			{
				useSkill(player, 5836, true);
				break;
			}
			
			case 6021: // Double Throwing Javelin
			{
				useSkill(player, 5837, true);
				break;
			}
			
			case 6022: // Throwing Javelin
			{
				useSkill(player, 5838, true);
				break;
			}
			
			case 6023: // Breath
			{
				useSkill(player, 5839, true);
				break;
			}
			
			case 6024: // Fire Breath
			{
				useSkill(player, 5840, true);
				break;
			}
			
			case 6025: // Multi Defense
			{
				useSkill(player, 5841, true);
				break;
			}
			
			case 6026: // Spinning Slasher
			{
				useSkill(player, 5842, true);
				break;
			}
			
			case 6027: // Terror
			{
				useSkill(player, 5843, true);
				break;
			}
			
			case 6028: // Flip Block
			{
				useSkill(player, 5852, true);
				break;
			}
			
			case 6029: // Flip Block
			{
				useSkill(player, 5853, true);
				break;
			}
			
			case 6030: // Weakened Sweep
			{
				useSkill(player, 5855, true);
				break;
			}
			
			case 6031: // Wrath of Valakas
			{
				useSkill(player, 5860, true);
				break;
			}
			
			case 6032: // Authority of Valakas
			{
				useSkill(player, 5861, true);
				break;
			}
			
			case 6033: // Destruction of the Body
			{
				useSkill(player, 5862, true);
				break;
			}
			
			case 6034: // Destruction of the Soul
			{
				useSkill(player, 5863, true);
				break;
			}
			
			case 6035: // Decrease Valakas Defense
			{
				useSkill(player, 5864, true);
				break;
			}
			
			case 6036: // Wide-bodied Valakas
			{
				useSkill(player, 5865, true);
				break;
			}
			
			case 6037: // Chant of Worm Waking
			{
				useSkill(player, 5877, true);
				break;
			}
			
			case 6038: // Cruel Puncture
			{
				useSkill(player, 5878, true);
				break;
			}
			
			case 6039: // Cruel Expunge
			{
				useSkill(player, 5879, true);
				break;
			}
			
			case 6040: // Vicious Mutilation
			{
				useSkill(player, 5880, true);
				break;
			}
			
			case 6041: // Vicious Mutilation
			{
				useSkill(player, 5881, true);
				break;
			}
			
			case 6042: // Weakened Pommel
			{
				useSkill(player, 5882, true);
				break;
			}
			
			case 6043: // Meggling Injury
			{
				useSkill(player, 5883, true);
				break;
			}
			
			case 6044: // Weakened Sweep
			{
				useSkill(player, 5884, true);
				break;
			}
			
			case 6045: // Weakened Sweep
			{
				useSkill(player, 5885, true);
				break;
			}
			
			case 6046: // Ground Shaker
			{
				useSkill(player, 5886, true);
				break;
			}
			
			case 6047: // Howl from Beyond
			{
				useSkill(player, 5887, true);
				break;
			}
			
			case 6048: // Earth Shaker
			{
				useSkill(player, 5888, true);
				break;
			}
			
			case 6049: // Tainted Shackle
			{
				useSkill(player, 5889, true);
				break;
			}
			
			case 6050: // Tainted Mass Shackle
			{
				useSkill(player, 5890, true);
				break;
			}
			
			case 6051: // Threatening Bellow
			{
				useSkill(player, 5891, true);
				break;
			}
			
			case 6052: // Cunning Coercion
			{
				useSkill(player, 5892, true);
				break;
			}
			
			case 6053: // Ritual of Cannibalism
			{
				useSkill(player, 5893, true);
				break;
			}
			
			case 6054: // Mark of Despair
			{
				useSkill(player, 5894, true);
				break;
			}
			
			case 6055: // Mark of Cowardice
			{
				useSkill(player, 5895, true);
				break;
			}
			
			case 6056: // Soulless
			{
				useSkill(player, 5896, true);
				break;
			}
			
			case 6057: // Spike of Anchor
			{
				useSkill(player, 5897, true);
				break;
			}
			
			case 6058: // Blast of Anchor
			{
				useSkill(player, 5898, true);
				break;
			}
			
			case 6059: // Burst of Pain
			{
				useSkill(player, 5899, true);
				break;
			}
			
			case 6060: // Burst of Oblivion
			{
				useSkill(player, 5900, true);
				break;
			}
			
			case 6061: // Mist of Oblivion
			{
				useSkill(player, 5901, true);
				break;
			}
			
			case 6062: // Burst of Misplace
			{
				useSkill(player, 5903, true);
				break;
			}
			
			case 6063: // Mist of Souleater
			{
				useSkill(player, 5904, true);
				break;
			}
			
			case 6064: // Confusing Nuph
			{
				useSkill(player, 5905, true);
				break;
			}
			
			case 6065: // Ritual of Revive
			{
				useSkill(player, 5906, true);
				break;
			}
			
			case 6066: // Ritual of Entombment
			{
				useSkill(player, 5907, true);
				break;
			}
			
			case 6067: // Ritual of Entombment
			{
				useSkill(player, 5908, true);
				break;
			}
			
			case 6068: // Stronghold Attack
			{
				useSkill(player, 5909, true);
				break;
			}
			
			case 6069: // Stronghold Attack
			{
				useSkill(player, 5910, true);
				break;
			}
			
			case 6070: // Mummifying Aura
			{
				useSkill(player, 5915, true);
				break;
			}
			
			case 6071: // Cruising Aura
			{
				useSkill(player, 5916, true);
				break;
			}
			
			case 6072: // Shrouding Aura
			{
				useSkill(player, 5917, true);
				break;
			}
			
			case 6073: // Obey
			{
				useSkill(player, 5919, true);
				break;
			}
			
			case 6074: // Inhale
			{
				useSkill(player, 5921, true);
				break;
			}
			
			case 6075: // Exhale
			{
				useSkill(player, 5922, true);
				break;
			}
			
			case 6076: // Devour Soul
			{
				useSkill(player, 5924, true);
				break;
			}
			
			case 6077: // Breath Corruption
			{
				useSkill(player, 5926, true);
				break;
			}
			
			case 6078: // Ultimate Shield
			{
				useSkill(player, 5931, true);
				break;
			}
			
			case 6079: // Dark Blade
			{
				useSkill(player, 5932, true);
				break;
			}
			
			case 6080: // Dark Blade
			{
				useSkill(player, 5933, true);
				break;
			}
			
			case 6081: // Meet the soul of darkness and fight against it.
			{
				useSkill(player, 5934, true);
				break;
			}
			
			case 6082: // Dark Ring
			{
				useSkill(player, 5935, true);
				break;
			}
			
			case 6083: // Earth Ring
			{
				useSkill(player, 5936, true);
				break;
			}
			
			case 6084: // Self Stun
			{
				useSkill(player, 5937, true);
				break;
			}
			
			case 6085: // Agathion's Blessing
			{
				useSkill(player, 5951, true);
				break;
			}
			
			case 6086: // Agathion's Blessing
			{
				useSkill(player, 5952, true);
				break;
			}
			
			case 6087: // Agathion's Blessing
			{
				useSkill(player, 5953, true);
				break;
			}
			
			case 6088: // Agathion Cute Trick - Joy
			{
				useSkill(player, 5955, true);
				break;
			}
			
			case 6089: // Agathion Cute Trick - Anger
			{
				useSkill(player, 5956, true);
				break;
			}
			
			case 6090: // Agathion Cute Trick - Sorrow
			{
				useSkill(player, 5957, true);
				break;
			}
			
			case 6091: // Agathion Cute Trick - Teddy Bear Boy
			{
				useSkill(player, 5958, true);
				break;
			}
			
			case 6092: // Agathion Cute Trick - Teddy Bear Girl
			{
				useSkill(player, 5959, true);
				break;
			}
			
			case 6093: // Barrier Thunder Storm
			{
				useSkill(player, 5961, true);
				break;
			}
			
			case 6094: // Air Defense Cannon
			{
				useSkill(player, 5962, true);
				break;
			}
			
			case 6095: // Rapid Shot
			{
				useSkill(player, 5968, true);
				break;
			}
			
			case 6096: // Punch of Doom
			{
				useSkill(player, 5969, true);
				break;
			}
			
			case 6097: // Seduced Knight's Help
			{
				useSkill(player, 5970, true);
				break;
			}
			
			case 6098: // Seduced Ranger's Help
			{
				useSkill(player, 5971, true);
				break;
			}
			
			case 6099: // Seduced Wizard's Help
			{
				useSkill(player, 5972, true);
				break;
			}
			
			case 6100: // Seduced Warrior's Help
			{
				useSkill(player, 5973, true);
				break;
			}
			
			case 6101: // Tiat Transformation
			{
				useSkill(player, 5974, true);
				break;
			}
			
			case 6102: // Dark Attack
			{
				useSkill(player, 5975, true);
				break;
			}
			
			case 6103: // Dark Storm
			{
				useSkill(player, 5976, true);
				break;
			}
			
			case 6104: // Dark Strike
			{
				useSkill(player, 5977, true);
				break;
			}
			
			case 6105: // Death Strike
			{
				useSkill(player, 5978, true);
				break;
			}
			
			case 6106: // Seal Isolation
			{
				useSkill(player, 5980, true);
				break;
			}
			
			case 6107: // Decaying Aura
			{
				useSkill(player, 5985, true);
				break;
			}
			
			case 6108: // Vampiric Aura
			{
				useSkill(player, 5986, true);
				break;
			}
			
			case 6109: // Weapon Maintenance
			{
				useSkill(player, 5987, true);
				break;
			}
			
			case 6110: // Armor Maintenance
			{
				useSkill(player, 5988, true);
				break;
			}
			
			case 6111: // Presentation - Summon Tiat
			{
				useSkill(player, 5991, true);
				break;
			}
			
			case 6112: // Phoenix Rush
			{
				useSkill(player, 6041, true);
				break;
			}
			
			case 6113: // Phoenix Cleanse
			{
				useSkill(player, 6042, true);
				break;
			}
			
			case 6114: // Phoenix Flame Feather
			{
				useSkill(player, 6043, true);
				break;
			}
			
			case 6115: // Phoenix Flame Beak
			{
				useSkill(player, 6044, true);
				break;
			}
			
			case 6116: // Presentation - Fortune Bug
			{
				useSkill(player, 6045, true);
				break;
			}
			
			case 6117: // Piercing Attack
			{
				useSkill(player, 6046, true);
				break;
			}
			
			case 6118: // Whirlwind
			{
				useSkill(player, 6047, true);
				break;
			}
			
			case 6119: // Lance Smash
			{
				useSkill(player, 6048, true);
				break;
			}
			
			case 6120: // Battle Cry
			{
				useSkill(player, 6049, true);
				break;
			}
			
			case 6121: // Power Smash
			{
				useSkill(player, 6050, true);
				break;
			}
			
			case 6122: // Energy Burst
			{
				useSkill(player, 6051, true);
				break;
			}
			
			case 6123: // Shockwave
			{
				useSkill(player, 6052, true);
				break;
			}
			
			case 6124: // Ignite
			{
				useSkill(player, 6053, true);
				break;
			}
			
			case 6125: // Switch State
			{
				useSkill(player, 6054, true);
				break;
			}
			
			case 6126: // Panther Hide
			{
				useSkill(player, 6093, true);
				break;
			}
			
			case 6127: // Panther Cancel
			{
				useSkill(player, 6094, true);
				break;
			}
			
			case 6128: // Panther Dark Claw
			{
				useSkill(player, 6095, true);
				break;
			}
			
			case 6129: // Panther Fatal Claw
			{
				useSkill(player, 6096, true);
				break;
			}
			
			case 6130: // Turkey's Choice - Paper
			{
				useSkill(player, 6100, true);
				break;
			}
			
			case 6131: // Turkey's Mistake
			{
				useSkill(player, 6105, true);
				break;
			}
			
			case 6132: // Attack Turkey
			{
				useSkill(player, 6116, true);
				break;
			}
			
			case 6133: // Agathion's New Year's Gift 1
			{
				useSkill(player, 6121, true);
				break;
			}
			
			case 6134: // Agathion's New Year's Gift 2
			{
				useSkill(player, 6122, true);
				break;
			}
			
			case 6135: // Agathion Cute Trick - Neolithica
			{
				useSkill(player, 6124, true);
				break;
			}
			
			case 6136: // Effect of Doubt
			{
				useSkill(player, 6133, true);
				break;
			}
			
			case 6137: // Mutant Curse
			{
				useSkill(player, 6135, true);
				break;
			}
			
			case 6138: // Agathion Cute Trick - Juju
			{
				useSkill(player, 6136, true);
				break;
			}
			
			case 6139: // Agathion Juju - Effect of Doubt
			{
				useSkill(player, 6137, true);
				break;
			}
			
			case 6140: // Agathion Juju - Large Firework
			{
				useSkill(player, 6138, true);
				break;
			}
			
			case 6141: // Tar Trap
			{
				useSkill(player, 6142, true);
				break;
			}
			
			case 6142: // Ancient Beam
			{
				useSkill(player, 6143, true);
				break;
			}
			
			case 6143: // Ancient Beam
			{
				useSkill(player, 6144, true);
				break;
			}
			
			case 6144: // Earthworm's Evil Aura
			{
				useSkill(player, 6145, true);
				break;
			}
			
			case 6145: // Forfeiture of Will
			{
				useSkill(player, 6146, true);
				break;
			}
			
			case 6146: // Kasha's Divine Protection
			{
				useSkill(player, 6147, true);
				break;
			}
			
			case 6147: // Hold
			{
				useSkill(player, 6166, true);
				break;
			}
			
			case 6148: // Shock
			{
				useSkill(player, 6167, true);
				break;
			}
			
			case 6149: // Shock
			{
				useSkill(player, 6168, true);
				break;
			}
			
			case 6150: // Presentation - Tyranno
			{
				useSkill(player, 6172, true);
				break;
			}
			
			case 6151: // Presentation - Anakim's Message
			{
				useSkill(player, 6177, true);
				break;
			}
			
			case 6152: // Presentation - Lilith Presentation1
			{
				useSkill(player, 6180, true);
				break;
			}
			
			case 6153: // Presentation - Lilith Battle
			{
				useSkill(player, 6187, true);
				break;
			}
			
			case 6154: // Presentation - Lilith's Steward Battle1
			{
				useSkill(player, 6188, true);
				break;
			}
			
			case 6155: // Presentation - Lilith's Bodyguards Battle1
			{
				useSkill(player, 6190, true);
				break;
			}
			
			case 6156: // Presentation - Anakim Battle
			{
				useSkill(player, 6191, true);
				break;
			}
			
			case 6157: // Presentation - Anakim's Guardian Battle1
			{
				useSkill(player, 6192, true);
				break;
			}
			
			case 6158: // Presentation - Anakim's Guard Battle
			{
				useSkill(player, 6194, true);
				break;
			}
			
			case 6159: // Presentation - Anakim's Executor Battle
			{
				useSkill(player, 6195, true);
				break;
			}
			
			case 6160: // Agathion Oink Oink's Ability
			{
				useSkill(player, 6196, true);
				break;
			}
			
			case 6161: // Agathion Cute Trick - Oink Oink
			{
				useSkill(player, 6197, true);
				break;
			}
			
			case 6162: // Tail Strike
			{
				useSkill(player, 6199, true);
				break;
			}
			
			case 6163: // Strider Bite
			{
				useSkill(player, 6205, true);
				break;
			}
			
			case 6164: // Strider Fear
			{
				useSkill(player, 6206, true);
				break;
			}
			
			case 6165: // Strider Dash
			{
				useSkill(player, 6207, true);
				break;
			}
			
			case 6166: // Presentation - Adena Firework
			{
				useSkill(player, 6234, true);
				break;
			}
			
			case 6167: // Blessed Focus
			{
				useSkill(player, 6235, true);
				break;
			}
			
			case 6168: // Presentation - Party Recall
			{
				useSkill(player, 6236, true);
				break;
			}
			
			case 6169: // Speed Decrease
			{
				useSkill(player, 6237, true);
				break;
			}
			
			case 6170: // Speed Decrease
			{
				useSkill(player, 6238, true);
				break;
			}
			
			case 6171: // Heatstroke
			{
				useSkill(player, 6240, true);
				break;
			}
			
			case 6172: // Severe Heatstroke
			{
				useSkill(player, 6250, true);
				break;
			}
			
			case 6173: // Humidity Attack
			{
				useSkill(player, 6252, true);
				break;
			}
			
			case 6174: // EMP Power
			{
				useSkill(player, 6262, true);
				break;
			}
			
			case 6175: // EMP Shock
			{
				useSkill(player, 6263, true);
				break;
			}
			
			case 6176: // Golem Boom
			{
				useSkill(player, 6264, true);
				break;
			}
			
			case 6177: // Smoke
			{
				useSkill(player, 6265, true);
				break;
			}
			
			case 6178: // Eternal Blizzard
			{
				useSkill(player, 6276, true);
				break;
			}
			
			case 6179: // Summon Spirits
			{
				useSkill(player, 6277, true);
				break;
			}
			
			case 6180: // Ice Ball
			{
				useSkill(player, 6278, true);
				break;
			}
			
			case 6181: // Death Clack
			{
				useSkill(player, 6280, true);
				break;
			}
			
			case 6182: // Reflect Magic
			{
				useSkill(player, 6282, true);
				break;
			}
			
			case 6183: // Freya's Bless
			{
				useSkill(player, 6284, true);
				break;
			}
			
			case 6184: // Bless of Sword
			{
				useSkill(player, 6286, true);
				break;
			}
			
			case 6185: // Jinia's Prayer
			{
				useSkill(player, 6288, true);
				break;
			}
			
			case 6186: // Kegor's Courage
			{
				useSkill(player, 6289, true);
				break;
			}
			
			case 6187: // Power Strike
			{
				useSkill(player, 6290, true);
				break;
			}
			
			case 6188: // Power Strike
			{
				useSkill(player, 6291, true);
				break;
			}
			
			case 6189: // Rush
			{
				useSkill(player, 6292, true);
				break;
			}
			
			case 6190: // Power Strike
			{
				useSkill(player, 6293, true);
				break;
			}
			
			case 6191: // Leader's Roar
			{
				useSkill(player, 6294, true);
				break;
			}
			
			case 6192: // Point Target
			{
				useSkill(player, 6295, true);
				break;
			}
			
			case 6193: // Cylinder Throw
			{
				useSkill(player, 6297, true);
				break;
			}
			
			case 6194: // Summon Follower Knight
			{
				useSkill(player, 6298, true);
				break;
			}
			
			case 6195: // Breath of Ice Palace - Ice Storm
			{
				useSkill(player, 6299, true);
				break;
			}
			
			case 6196: // Self-Destruction
			{
				useSkill(player, 6300, true);
				break;
			}
			
			case 6197: // Cold Mana's Fragment
			{
				useSkill(player, 6301, true);
				break;
			}
			
			case 6198: // Trial of the Coup
			{
				useSkill(player, 6303, true);
				break;
			}
			
			case 6199: // Shock
			{
				useSkill(player, 6304, true);
				break;
			}
			
			case 6200: // Sacred Gnosis
			{
				useSkill(player, 6305, true);
				break;
			}
			
			case 6201: // Solina Strike
			{
				useSkill(player, 6306, true);
				break;
			}
			
			case 6202: // Opus of the Hand
			{
				useSkill(player, 6307, true);
				break;
			}
			
			case 6203: // Opus of the Wave
			{
				useSkill(player, 6308, true);
				break;
			}
			
			case 6204: // Pain of the Ascetic
			{
				useSkill(player, 6309, true);
				break;
			}
			
			case 6205: // Loss of Quest
			{
				useSkill(player, 6310, true);
				break;
			}
			
			case 6206: // Solina Thrust
			{
				useSkill(player, 6311, true);
				break;
			}
			
			case 6207: // Launch Sacred Sword Energy
			{
				useSkill(player, 6312, true);
				break;
			}
			
			case 6208: // Solina Bless
			{
				useSkill(player, 6313, true);
				break;
			}
			
			case 6209: // Sacred Judgement
			{
				useSkill(player, 6314, true);
				break;
			}
			
			case 6210: // Sacred Strike
			{
				useSkill(player, 6315, true);
				break;
			}
			
			case 6211: // Accomplish of Authority
			{
				useSkill(player, 6316, true);
				break;
			}
			
			case 6212: // Sacred Tacit
			{
				useSkill(player, 6317, true);
				break;
			}
			
			case 6213: // Divine Shield Protection
			{
				useSkill(player, 6318, true);
				break;
			}
			
			case 6214: // Sacred Magic Protection
			{
				useSkill(player, 6319, true);
				break;
			}
			
			case 6215: // Summon Sacred Magic Force
			{
				useSkill(player, 6320, true);
				break;
			}
			
			case 6216: // Divine Flash
			{
				useSkill(player, 6321, true);
				break;
			}
			
			case 6217: // Divine Impact
			{
				useSkill(player, 6322, true);
				break;
			}
			
			case 6218: // Divine Bolt
			{
				useSkill(player, 6323, true);
				break;
			}
			
			case 6219: // Divine Strike
			{
				useSkill(player, 6324, true);
				break;
			}
			
			case 6220: // Divine Nova
			{
				useSkill(player, 6325, true);
				break;
			}
			
			case 6221: // Martyr's Happiness
			{
				useSkill(player, 6326, true);
				break;
			}
			
			case 6222: // Sacred Worship
			{
				useSkill(player, 6327, true);
				break;
			}
			
			case 6223: // Fighter Judgement
			{
				useSkill(player, 6328, true);
				break;
			}
			
			case 6224: // Fighter Strike
			{
				useSkill(player, 6329, true);
				break;
			}
			
			case 6225: // Salmon Porridge Attack
			{
				useSkill(player, 6330, true);
				break;
			}
			
			case 6226: // Camp Fire Tired
			{
				useSkill(player, 6331, true);
				break;
			}
			
			case 6227: // Camp Fire Full
			{
				useSkill(player, 6332, true);
				break;
			}
			
			case 6228: // Electric Bolt 1
			{
				useSkill(player, 6333, true);
				break;
			}
			
			case 6229: // Shock
			{
				useSkill(player, 6334, true);
				break;
			}
			
			case 6230: // Electric Rain 1
			{
				useSkill(player, 6335, true);
				break;
			}
			
			case 6231: // Electric Rain 2
			{
				useSkill(player, 6336, true);
				break;
			}
			
			case 6232: // Red Slash 1
			{
				useSkill(player, 6337, true);
				break;
			}
			
			case 6233: // Red Slash 2
			{
				useSkill(player, 6338, true);
				break;
			}
			
			case 6234: // Sacred Hammer Attack
			{
				useSkill(player, 6339, true);
				break;
			}
			
			case 6235: // Shock
			{
				useSkill(player, 6340, true);
				break;
			}
			
			case 6236: // Maguen Plasma - Power
			{
				useSkill(player, 6343, true);
				break;
			}
			
			case 6237: // Maguen Plasma - Speed
			{
				useSkill(player, 6365, true);
				break;
			}
			
			case 6238: // Maguen Plasma - Critical
			{
				useSkill(player, 6366, true);
				break;
			}
			
			case 6239: // Maguen Plasma - Bistakon
			{
				useSkill(player, 6367, true);
				break;
			}
			
			case 6240: // Maguen Plasma - Cokrakon
			{
				useSkill(player, 6368, true);
				break;
			}
			
			case 6241: // Maguen Plasma - Reptilikon
			{
				useSkill(player, 6369, true);
				break;
			}
			
			case 6242: // Sonic Strike
			{
				useSkill(player, 6376, true);
				break;
			}
			
			case 6243: // Assault Boost
			{
				useSkill(player, 6377, true);
				break;
			}
			
			case 6244: // Earth Shift
			{
				useSkill(player, 6378, true);
				break;
			}
			
			case 6245: // Blood Spurt
			{
				useSkill(player, 6379, true);
				break;
			}
			
			case 6246: // Bistakon Roar
			{
				useSkill(player, 6380, true);
				break;
			}
			
			case 6247: // Bistakon Soul Beam
			{
				useSkill(player, 6381, true);
				break;
			}
			
			case 6248: // Bistakon Rolling Claw
			{
				useSkill(player, 6382, true);
				break;
			}
			
			case 6249: // Bistakon Deadly Roar
			{
				useSkill(player, 6383, true);
				break;
			}
			
			case 6250: // Bistakon Deadly Blow
			{
				useSkill(player, 6384, true);
				break;
			}
			
			case 6251: // Bistakon Critical Claw
			{
				useSkill(player, 6385, true);
				break;
			}
			
			case 6252: // Bistakon Earth Rise
			{
				useSkill(player, 6386, true);
				break;
			}
			
			case 6253: // Bistakon Earthquake
			{
				useSkill(player, 6387, true);
				break;
			}
			
			case 6254: // Bistakon Jump Blow
			{
				useSkill(player, 6388, true);
				break;
			}
			
			case 6255: // Cokrakon Sonic Explosion
			{
				useSkill(player, 6390, true);
				break;
			}
			
			case 6256: // Cokrakon Sonic Slash
			{
				useSkill(player, 6391, true);
				break;
			}
			
			case 6257: // Cokrakon Sonic Shot
			{
				useSkill(player, 6392, true);
				break;
			}
			
			case 6258: // Cokrakon Sonic Beam
			{
				useSkill(player, 6393, true);
				break;
			}
			
			case 6259: // Cokrakon Sonic Bomb
			{
				useSkill(player, 6394, true);
				break;
			}
			
			case 6260: // Cokrakon Dreadful Clow
			{
				useSkill(player, 6395, true);
				break;
			}
			
			case 6261: // Cokrakon Panic
			{
				useSkill(player, 6396, true);
				break;
			}
			
			case 6262: // Cokrakon Staggering Blow
			{
				useSkill(player, 6397, true);
				break;
			}
			
			case 6263: // Cokrakon Sonic Shout
			{
				useSkill(player, 6398, true);
				break;
			}
			
			case 6264: // Cokrakon Dreadful Piercing
			{
				useSkill(player, 6399, true);
				break;
			}
			
			case 6265: // Cokrakon Dwindling Velocity
			{
				useSkill(player, 6400, true);
				break;
			}
			
			case 6266: // Cokrakon Wheeling Clow
			{
				useSkill(player, 6401, true);
				break;
			}
			
			case 6267: // Torumba Numbing Poison
			{
				useSkill(player, 6403, true);
				break;
			}
			
			case 6268: // Torumba Poison Swing
			{
				useSkill(player, 6404, true);
				break;
			}
			
			case 6269: // Torumba Roar
			{
				useSkill(player, 6405, true);
				break;
			}
			
			case 6270: // Reptilikon Earthquake
			{
				useSkill(player, 6409, true);
				break;
			}
			
			case 6271: // Reptilikon Rush
			{
				useSkill(player, 6410, true);
				break;
			}
			
			case 6272: // Reptilikon Summon
			{
				useSkill(player, 6411, true);
				break;
			}
			
			case 6273: // Reptilikon Critical Blow
			{
				useSkill(player, 6412, true);
				break;
			}
			
			case 6274: // Reptilikon Missile
			{
				useSkill(player, 6413, true);
				break;
			}
			
			case 6275: // Reptilikon Charge
			{
				useSkill(player, 6414, true);
				break;
			}
			
			case 6276: // Reptilikon Scratch
			{
				useSkill(player, 6415, true);
				break;
			}
			
			case 6277: // Reptilikon Poison Breath
			{
				useSkill(player, 6416, true);
				break;
			}
			
			case 6278: // Reptilikon Poison Shot
			{
				useSkill(player, 6417, true);
				break;
			}
			
			case 6279: // Reptilikon Fury Poison Bomb
			{
				useSkill(player, 6418, true);
				break;
			}
			
			case 6280: // Light of Scout
			{
				useSkill(player, 6419, true);
				break;
			}
			
			case 6281: // Lizard Strike
			{
				useSkill(player, 6420, true);
				break;
			}
			
			case 6282: // Rage of Soldier
			{
				useSkill(player, 6421, true);
				break;
			}
			
			case 6283: // Resistance Explosion
			{
				useSkill(player, 6422, true);
				break;
			}
			
			case 6284: // Shock
			{
				useSkill(player, 6423, true);
				break;
			}
			
			case 6285: // Double Shot
			{
				useSkill(player, 6424, true);
				break;
			}
			
			case 6286: // Demotivation Hex
			{
				useSkill(player, 6425, true);
				break;
			}
			
			case 6287: // Priest's Ire
			{
				useSkill(player, 6426, true);
				break;
			}
			
			case 6288: // Medicinal Mushroom 1
			{
				useSkill(player, 6427, true);
				break;
			}
			
			case 6289: // Hold
			{
				useSkill(player, 6428, true);
				break;
			}
			
			case 6290: // Cozy Mucus
			{
				useSkill(player, 6429, true);
				break;
			}
			
			case 6291: // Roar Hip Heal
			{
				useSkill(player, 6430, true);
				break;
			}
			
			case 6292: // Feral Might
			{
				useSkill(player, 6431, true);
				break;
			}
			
			case 6293: // Feral Focus
			{
				useSkill(player, 6432, true);
				break;
			}
			
			case 6294: // Feral Guidance
			{
				useSkill(player, 6433, true);
				break;
			}
			
			case 6295: // Feral Haste
			{
				useSkill(player, 6434, true);
				break;
			}
			
			case 6296: // Presentation - Freya's Message 1
			{
				useSkill(player, 6445, true);
				break;
			}
			
			case 6297: // Presentation - Freya's Message 3
			{
				useSkill(player, 6447, true);
				break;
			}
			
			case 6298: // Presentation - Freya's Message 5
			{
				useSkill(player, 6449, true);
				break;
			}
			
			case 6299: // Presentation - Freya Move
			{
				useSkill(player, 6451, true);
				break;
			}
			
			case 6300: // Presentation - Freya Space Change
			{
				useSkill(player, 6452, true);
				break;
			}
			
			case 6301: // Presentation - Freya Death
			{
				useSkill(player, 6453, true);
				break;
			}
			
			case 6302: // Presentation - Freya Frustration
			{
				useSkill(player, 6454, true);
				break;
			}
			
			case 6303: // Presentation - Freya Destroy
			{
				useSkill(player, 6455, true);
				break;
			}
			
			case 6304: // Presentation - Absorption of Sirr
			{
				useSkill(player, 6457, true);
				break;
			}
			
			case 6305: // Presentation - Transform of Sirr
			{
				useSkill(player, 6458, true);
				break;
			}
			
			case 6306: // Presentation - Kegor Super Buff
			{
				useSkill(player, 6459, true);
				break;
			}
			
			case 6307: // Presentation - Jinia Super Buff
			{
				useSkill(player, 6460, true);
				break;
			}
			
			case 6308: // Presentation - Kegor's Message 1
			{
				useSkill(player, 6461, true);
				break;
			}
			
			case 6309: // Presentation - Kegor's Message 1
			{
				useSkill(player, 6462, true);
				break;
			}
			
			case 6310: // Jack O'Lantern Card
			{
				useSkill(player, 6465, true);
				break;
			}
			
			case 6311: // Rotten Jack O'Lantern Card
			{
				useSkill(player, 6466, true);
				break;
			}
			
			case 6312: // NPC holy attack
			{
				useSkill(player, 6494, true);
				break;
			}
			
			case 6313: // NPC holy shot
			{
				useSkill(player, 6502, true);
				break;
			}
			
			case 6314: // NPC holy magic
			{
				useSkill(player, 6509, true);
				break;
			}
			
			case 6315: // NPC dark attack
			{
				useSkill(player, 6515, true);
				break;
			}
			
			case 6316: // NPC water attack
			{
				useSkill(player, 6556, true);
				break;
			}
			
			case 6317: // NPC water attack
			{
				useSkill(player, 6559, true);
				break;
			}
			
			case 6318: // NPC water shot
			{
				useSkill(player, 6566, true);
				break;
			}
			
			case 6319: // NPC earth attack
			{
				useSkill(player, 6597, true);
				break;
			}
			
			case 6320: // NPC earth attack
			{
				useSkill(player, 6598, true);
				break;
			}
			
			case 6321: // NPC earth attack
			{
				useSkill(player, 6599, true);
				break;
			}
			
			case 6322: // NPC earth attack
			{
				useSkill(player, 6602, true);
				break;
			}
			
			case 6323: // NPC earth shot
			{
				useSkill(player, 6608, true);
				break;
			}
			
			case 6324: // NPC earth magic
			{
				useSkill(player, 6610, true);
				break;
			}
			
			case 6325: // NPC earth magic
			{
				useSkill(player, 6611, true);
				break;
			}
			
			case 6326: // Maguen Strike
			{
				useSkill(player, 6618, true);
				break;
			}
			
			case 6327: // Maguen Power Strike
			{
				useSkill(player, 6619, true);
				break;
			}
			
			case 6328: // Shock
			{
				useSkill(player, 6622, true);
				break;
			}
			
			case 6329: // Holy Ball
			{
				useSkill(player, 6641, true);
				break;
			}
			
			case 6330: // Dash
			{
				useSkill(player, 6642, true);
				break;
			}
			
			case 6331: // Holy Strike
			{
				useSkill(player, 6643, true);
				break;
			}
			
			case 6332: // Komodo Heal
			{
				useSkill(player, 6648, true);
				break;
			}
			
			case 6333: // Tacrakahn's Alienation
			{
				useSkill(player, 6650, true);
				break;
			}
			
			case 6334: // Lavasaurus Firstborn Attack
			{
				useSkill(player, 6656, true);
				break;
			}
			
			case 6335: // Lavasaurus Firstborn Heal
			{
				useSkill(player, 6657, true);
				break;
			}
			
			case 6336: // Feral Shield
			{
				useSkill(player, 6666, true);
				break;
			}
			
			case 6337: // Feral Wind Walk
			{
				useSkill(player, 6667, true);
				break;
			}
			
			case 6338: // Feral Death Whisper
			{
				useSkill(player, 6668, true);
				break;
			}
			
			case 6339: // Feral Body Bless
			{
				useSkill(player, 6669, true);
				break;
			}
			
			case 6340: // Feral Vampiric Rage
			{
				useSkill(player, 6670, true);
				break;
			}
			
			case 6341: // Feral Berserker Spirit
			{
				useSkill(player, 6671, true);
				break;
			}
			
			case 6342: // Feral Bless Shield
			{
				useSkill(player, 6672, true);
				break;
			}
			
			case 6343: // Lavasaurus Firstborn Attack
			{
				useSkill(player, 6675, true);
				break;
			}
			
			case 6344: // Magician's Perversity
			{
				useSkill(player, 6676, true);
				break;
			}
			
			case 6345: // Hold
			{
				useSkill(player, 6677, true);
				break;
			}
			
			case 6346: // Hold Cancel
			{
				useSkill(player, 6678, true);
				break;
			}
			
			case 6347: // Summoner's Strike
			{
				useSkill(player, 6679, true);
				break;
			}
			
			case 6348: // Maguen Speed Walk
			{
				useSkill(player, 6681, true);
				break;
			}
			
			case 6349: // Elite Maguen Speed Walk
			{
				useSkill(player, 6682, true);
				break;
			}
			
			case 6350: // Maguen Recall
			{
				useSkill(player, 6683, true);
				break;
			}
			
			case 6351: // Maguen Party Recall
			{
				useSkill(player, 6684, true);
				break;
			}
			
			case 6352: // Sacred Protector Cancel
			{
				useSkill(player, 6686, true);
				break;
			}
			
			case 6353: // Soup of Failure
			{
				useSkill(player, 6688, true);
				break;
			}
			
			case 6354: // Absorb HP MP
			{
				useSkill(player, 6689, true);
				break;
			}
			
			case 6355: // Hold
			{
				useSkill(player, 6690, true);
				break;
			}
			
			case 6356: // Dualsword Deadly Move
			{
				useSkill(player, 6691, true);
				break;
			}
			
			case 6357: // Dualsword Range Deadly Move
			{
				useSkill(player, 6692, true);
				break;
			}
			
			case 6358: // NPC Strike
			{
				useSkill(player, 6693, true);
				break;
			}
			
			case 6359: // BOSS Strike
			{
				useSkill(player, 6694, true);
				break;
			}
			
			case 6360: // BOSS Spinning Slasher
			{
				useSkill(player, 6695, true);
				break;
			}
			
			case 6361: // Stun
			{
				useSkill(player, 6705, true);
				break;
			}
			
			case 6362: // Self-destruction
			{
				useSkill(player, 6707, true);
				break;
			}
			
			case 6363: // Self-destruction
			{
				useSkill(player, 6708, true);
				break;
			}
			
			case 6364: // Multi Shot
			{
				useSkill(player, 6709, true);
				break;
			}
			
			case 6365: // Etis Haste
			{
				useSkill(player, 6710, true);
				break;
			}
			
			case 6366: // Etis Power Up
			{
				useSkill(player, 6711, true);
				break;
			}
			
			case 6367: // Unknown Skill 6712
			{
				useSkill(player, 6712, true);
				break;
			}
			
			case 6368: // Unknown Skill 6713
			{
				useSkill(player, 6713, true);
				break;
			}
			
			case 6369: // Greater Heal of Elcadia
			{
				useSkill(player, 6724, true);
				break;
			}
			
			case 6370: // Bless the Blood of Elcadia
			{
				useSkill(player, 6725, true);
				break;
			}
			
			case 6371: // Bless the Blood
			{
				useSkill(player, 6726, true);
				break;
			}
			
			case 6372: // Vampiric Rage of Elcadia
			{
				useSkill(player, 6727, true);
				break;
			}
			
			case 6373: // Recharge of Elcadia
			{
				useSkill(player, 6728, true);
				break;
			}
			
			case 6374: // Resist Holy of Elcadia
			{
				useSkill(player, 6729, true);
				break;
			}
			
			case 6375: // Greater Battle Heal of Elcadia
			{
				useSkill(player, 6730, true);
				break;
			}
			
			case 6376: // Mirage
			{
				useSkill(player, 6732, true);
				break;
			}
			
			case 6377: // Antharas's Stigma
			{
				useSkill(player, 6733, true);
				break;
			}
			
			case 6378: // Petrify
			{
				useSkill(player, 6735, true);
				break;
			}
			
			case 6379: // Fierce Attack
			{
				useSkill(player, 6736, true);
				break;
			}
			
			case 6380: // Heal
			{
				useSkill(player, 6737, true);
				break;
			}
			
			case 6381: // Hold
			{
				useSkill(player, 6738, true);
				break;
			}
			
			case 6382: // Presentation - Behemoth Leader Casting Preparation
			{
				useSkill(player, 6739, true);
				break;
			}
			
			case 6383: // Presentation - Behemoth Leader Casting
			{
				useSkill(player, 6740, true);
				break;
			}
			
			case 6384: // Presentation - Behemoth Leader Casting Failure
			{
				useSkill(player, 6741, true);
				break;
			}
			
			case 6385: // Presentation - Behemoth Object Channeling
			{
				useSkill(player, 6742, true);
				break;
			}
			
			case 6386: // Dark Wind
			{
				useSkill(player, 6743, true);
				break;
			}
			
			case 6387: // Stun Attack
			{
				useSkill(player, 6748, true);
				break;
			}
			
			case 6388: // Death Strike
			{
				useSkill(player, 6749, true);
				break;
			}
			
			case 6389: // Power Strike
			{
				useSkill(player, 6750, true);
				break;
			}
			
			case 6390: // Revival
			{
				useSkill(player, 6752, true);
				break;
			}
			
			case 6391: // Death Blow
			{
				useSkill(player, 6753, true);
				break;
			}
			
			case 6392: // Bleed
			{
				useSkill(player, 6754, true);
				break;
			}
			
			case 6393: // Death Strike
			{
				useSkill(player, 6755, true);
				break;
			}
			
			case 6394: // Death Talon
			{
				useSkill(player, 6756, true);
				break;
			}
			
			case 6395: // Slow
			{
				useSkill(player, 6757, true);
				break;
			}
			
			case 6396: // Rage
			{
				useSkill(player, 6758, true);
				break;
			}
			
			case 6397: // Death Shot
			{
				useSkill(player, 6759, true);
				break;
			}
			
			case 6398: // Dragon Strike
			{
				useSkill(player, 6760, true);
				break;
			}
			
			case 6399: // Dragon Blow Strike
			{
				useSkill(player, 6761, true);
				break;
			}
			
			case 6400: // Rage
			{
				useSkill(player, 6762, true);
				break;
			}
			
			case 6401: // Dragon Earth Strike
			{
				useSkill(player, 6763, true);
				break;
			}
			
			case 6402: // Dragon Earth Shot
			{
				useSkill(player, 6764, true);
				break;
			}
			
			case 6403: // Complete Recovery
			{
				useSkill(player, 6765, true);
				break;
			}
			
			case 6404: // Earth Tremor
			{
				useSkill(player, 6766, true);
				break;
			}
			
			case 6405: // Stun Attack
			{
				useSkill(player, 6768, true);
				break;
			}
			
			case 6406: // Petrify
			{
				useSkill(player, 6769, true);
				break;
			}
			
			case 6407: // Heal
			{
				useSkill(player, 6770, true);
				break;
			}
			
			case 6408: // Death Strike
			{
				useSkill(player, 6771, true);
				break;
			}
			
			case 6409: // Power Strike
			{
				useSkill(player, 6772, true);
				break;
			}
			
			case 6410: // Rage
			{
				useSkill(player, 6773, true);
				break;
			}
			
			case 6411: // Stun Attack
			{
				useSkill(player, 6774, true);
				break;
			}
			
			case 6412: // Soul Breath
			{
				useSkill(player, 6775, true);
				break;
			}
			
			case 6413: // Paralysis
			{
				useSkill(player, 6776, true);
				break;
			}
			
			case 6414: // Bleeding Gash
			{
				useSkill(player, 6777, true);
				break;
			}
			
			case 6415: // Death Strike
			{
				useSkill(player, 6778, true);
				break;
			}
			
			case 6416: // Presentation - Eris's Suffering
			{
				useSkill(player, 6784, true);
				break;
			}
			
			case 6417: // Presentation - Dialogue with Etis 1
			{
				useSkill(player, 6785, true);
				break;
			}
			
			case 6418: // Presentation - Etis Pre-Transformation
			{
				useSkill(player, 6787, true);
				break;
			}
			
			case 6419: // Presentation - Etis Post-Transformation
			{
				useSkill(player, 6788, true);
				break;
			}
			
			case 6420: // Presentation - Etis's Defeat
			{
				useSkill(player, 6789, true);
				break;
			}
			
			case 6421: // Presentation - Etis Kneeling
			{
				useSkill(player, 6790, true);
				break;
			}
			
			case 6422: // Presentation - Dialogue with Elcadia 2
			{
				useSkill(player, 6792, true);
				break;
			}
			
			case 6423: // Presentation - Dialogue with Elcadia 3
			{
				useSkill(player, 6793, true);
				break;
			}
			
			case 6424: // Presentation - Elcadia Casting 1
			{
				useSkill(player, 6794, true);
				break;
			}
			
			case 6425: // Powerful Rage
			{
				useSkill(player, 6818, true);
				break;
			}
			
			case 6426: // Banshee's Curse
			{
				useSkill(player, 6819, true);
				break;
			}
			
			case 6427: // P. Def. Enhance
			{
				useSkill(player, 6820, true);
				break;
			}
			
			case 6428: // P. Def. Decrease
			{
				useSkill(player, 6821, true);
				break;
			}
			
			case 6429: // P. Def. Decrease
			{
				useSkill(player, 6822, true);
				break;
			}
			
			case 6430: // Piercing Storm
			{
				useSkill(player, 6824, true);
				break;
			}
			
			case 6431: // Bleed
			{
				useSkill(player, 6825, true);
				break;
			}
			
			case 6432: // Critical Strike
			{
				useSkill(player, 6826, true);
				break;
			}
			
			case 6433: // Petrify
			{
				useSkill(player, 6827, true);
				break;
			}
			
			case 6434: // Dust Storm
			{
				useSkill(player, 6828, true);
				break;
			}
			
			case 6435: // Vampiric Attack
			{
				useSkill(player, 6829, true);
				break;
			}
			
			case 6436: // Bleed
			{
				useSkill(player, 6830, true);
				break;
			}
			
			case 6437: // Blood Burst
			{
				useSkill(player, 6831, true);
				break;
			}
			
			case 6438: // Power Strike
			{
				useSkill(player, 6833, true);
				break;
			}
			
			case 6439: // Range Magic Attack
			{
				useSkill(player, 6834, true);
				break;
			}
			
			case 6440: // Summon Skeleton
			{
				useSkill(player, 6835, true);
				break;
			}
			
			case 6441: // Silence
			{
				useSkill(player, 6836, true);
				break;
			}
			
			case 6442: // Range Magic Attack
			{
				useSkill(player, 6837, true);
				break;
			}
			
			case 6443: // Self-destruction
			{
				useSkill(player, 6838, true);
				break;
			}
			
			case 6444: // Power Attack
			{
				useSkill(player, 6839, true);
				break;
			}
			
			case 6445: // Paralysis
			{
				useSkill(player, 6840, true);
				break;
			}
			
			case 6446: // Summon Subordinate
			{
				useSkill(player, 6841, true);
				break;
			}
			
			case 6447: // Enhance
			{
				useSkill(player, 6842, true);
				break;
			}
			
			case 6448: // Power Strike
			{
				useSkill(player, 6844, true);
				break;
			}
			
			case 6449: // Power Strike
			{
				useSkill(player, 6845, true);
				break;
			}
			
			case 6450: // Vampiric Claw
			{
				useSkill(player, 6846, true);
				break;
			}
			
			case 6451: // Summoner Strike
			{
				useSkill(player, 6849, true);
				break;
			}
			
			case 6452: // Self-destruction
			{
				useSkill(player, 6850, true);
				break;
			}
			
			case 6453: // Power Strike
			{
				useSkill(player, 6851, true);
				break;
			}
			
			case 6454: // Magic Strike
			{
				useSkill(player, 6852, true);
				break;
			}
			
			case 6455: // Sleep
			{
				useSkill(player, 6853, true);
				break;
			}
			
			case 6456: // Hold
			{
				useSkill(player, 6854, true);
				break;
			}
			
			case 6457: // Magic Strike
			{
				useSkill(player, 6855, true);
				break;
			}
			
			case 6458: // Power Strike
			{
				useSkill(player, 6857, true);
				break;
			}
			
			case 6459: // Summon
			{
				useSkill(player, 6858, true);
				break;
			}
			
			case 6460: // Power Strike
			{
				useSkill(player, 6859, true);
				break;
			}
			
			case 6461: // Power Strike
			{
				useSkill(player, 6860, true);
				break;
			}
			
			case 6462: // Enhance
			{
				useSkill(player, 6861, true);
				break;
			}
			
			case 6463: // Poison
			{
				useSkill(player, 6862, true);
				break;
			}
			
			case 6464: // Poison
			{
				useSkill(player, 6863, true);
				break;
			}
			
			case 6465: // Summon
			{
				useSkill(player, 6864, true);
				break;
			}
			
			case 6466: // Poison
			{
				useSkill(player, 6865, true);
				break;
			}
			
			case 6467: // Vampiric Claw
			{
				useSkill(player, 6866, true);
				break;
			}
			
			case 6468: // Magic Strike
			{
				useSkill(player, 6867, true);
				break;
			}
			
			case 6469: // Summon
			{
				useSkill(player, 6868, true);
				break;
			}
			
			case 6470: // Detonate
			{
				useSkill(player, 6869, true);
				break;
			}
			
			case 6471: // Vampiric Claw
			{
				useSkill(player, 6870, true);
				break;
			}
			
			case 6472: // Range Magic Attack
			{
				useSkill(player, 6871, true);
				break;
			}
			
			case 6473: // Self-destruction
			{
				useSkill(player, 6872, true);
				break;
			}
			
			case 6474: // Hex
			{
				useSkill(player, 6873, true);
				break;
			}
			
			case 6475: // Range Magic Attack
			{
				useSkill(player, 6874, true);
				break;
			}
			
			case 6476: // Poison
			{
				useSkill(player, 6875, true);
				break;
			}
			
			case 6477: // Power Strike
			{
				useSkill(player, 6876, true);
				break;
			}
			
			case 6478: // Power Strike
			{
				useSkill(player, 6877, true);
				break;
			}
			
			case 6479: // Paralysis
			{
				useSkill(player, 6878, true);
				break;
			}
			
			case 6480: // Vampiric Claw
			{
				useSkill(player, 6879, true);
				break;
			}
			
			case 6481: // Silence
			{
				useSkill(player, 6880, true);
				break;
			}
			
			case 6482: // Critical Strike
			{
				useSkill(player, 6881, true);
				break;
			}
			
			case 6483: // Petrify
			{
				useSkill(player, 6882, true);
				break;
			}
			
			case 6484: // Morale Boost
			{
				useSkill(player, 6884, true);
				break;
			}
			
			case 6485: // Morale Boost
			{
				useSkill(player, 6885, true);
				break;
			}
			
			case 6486: // Complete Recovery
			{
				useSkill(player, 6886, true);
				break;
			}
			
			case 6487: // Fatal Strike
			{
				useSkill(player, 6887, true);
				break;
			}
			
			case 6488: // Fatal Shot
			{
				useSkill(player, 6888, true);
				break;
			}
			
			case 6489: // Range Attack
			{
				useSkill(player, 6889, true);
				break;
			}
			
			case 6490: // Bleed
			{
				useSkill(player, 6890, true);
				break;
			}
			
			case 6491: // Power Attack
			{
				useSkill(player, 6891, true);
				break;
			}
			
			case 6492: // Power Shot
			{
				useSkill(player, 6892, true);
				break;
			}
			
			case 6493: // Power Attack
			{
				useSkill(player, 6893, true);
				break;
			}
			
			case 6494: // Power Shot
			{
				useSkill(player, 6894, true);
				break;
			}
			
			case 6495: // Power Attack
			{
				useSkill(player, 6895, true);
				break;
			}
			
			case 6496: // Power Shot
			{
				useSkill(player, 6896, true);
				break;
			}
			
			case 6497: // npc Acumen
			{
				useSkill(player, 6915, true);
				break;
			}
			
			case 6498: // NPC Default
			{
				useSkill(player, 7000, true);
				break;
			}
			
			case 6499: // Unknown Skill 9999
			{
				useSkill(player, 9999, true);
				break;
			}
			
			case 6500: // Dragon Kick
			{
				useSkill(player, 20002, true);
				break;
			}
			
			case 6501: // Dragon Slash
			{
				useSkill(player, 20003, true);
				break;
			}
			
			case 6502: // Dragon Dash
			{
				useSkill(player, 20004, true);
				break;
			}
			
			case 6503: // Dragon Aura
			{
				useSkill(player, 20005, true);
				break;
			}
			
			case 6504: // Majo Agathion Special Skill - Mysterious Power
			{
				useSkill(player, 23004, true);
				break;
			}
			
			case 6505: // Gold Majo Agathion Special Skill - Blessed Resurrection
			{
				useSkill(player, 23005, true);
				break;
			}
			
			case 6506: // Black Majo Agathion Special Skill - Blessed Escape
			{
				useSkill(player, 23006, true);
				break;
			}
			
			case 6507: // Plaipitak Agathion Special Skill - Mysterious Power
			{
				useSkill(player, 23007, true);
				break;
			}
			
			case 6508: // Plaipitak Agathion Special Skill - Blessed Escape
			{
				useSkill(player, 23008, true);
				break;
			}
			
			case 6509: // Plaipitak Agathion Special Skill - Blessed Resurrection
			{
				useSkill(player, 23009, true);
				break;
			}
			
			case 6510: // Baby Panda Agathion Special Skill - Mysterious Power
			{
				useSkill(player, 23013, true);
				break;
			}
			
			case 6511: // Bamboo Panda Agathion Special Skill - Blessed Resurrection
			{
				useSkill(player, 23014, true);
				break;
			}
			
			case 6512: // Sexy Panda Agathion Special Skill - Blessed Escape
			{
				useSkill(player, 23015, true);
				break;
			}
			
			case 6513: // Christmas Festival
			{
				useSkill(player, 23017, true);
				break;
			}
			
			case 6514: // Stupid Turkey's Mistake
			{
				useSkill(player, 23018, true);
				break;
			}
			
			case 6515: // White Maneki Neko Agathion Special Skill - Ability of Blessed Resurrection
			{
				useSkill(player, 23030, true);
				break;
			}
			
			case 6516: // Brown Maneki Neko Agathion Special Skill - Ability of Vitality Recovery
			{
				useSkill(player, 23032, true);
				break;
			}
			
			case 6517: // One-eyed Bat Drove Agathion Special Skill - Ability of Resist Unholy
			{
				useSkill(player, 23034, true);
				break;
			}
			
			case 6518: // One-eyed Bat Drove Agathion Special Skill - Ability of Vitality Recovery
			{
				useSkill(player, 23035, true);
				break;
			}
			
			case 6519: // Pegasus Agathion Special Skill - Ability of Wind Walk
			{
				useSkill(player, 23037, true);
				break;
			}
			
			case 6520: // Pegasus Agathion Special Skill - Ability of Blessed Return
			{
				useSkill(player, 23038, true);
				break;
			}
			
			case 6521: // Yellow-robed Tojigong Agathion Special Skill - Ability of Greater Heal
			{
				useSkill(player, 23042, true);
				break;
			}
			
			case 6522: // Blue-robed Tojigong Agathion Special Skill - Ability of Reflect Damage
			{
				useSkill(player, 23043, true);
				break;
			}
			
			case 6523: // Green-robed Tojigong Agathion Special Skill - Ability of Mana Regeneration
			{
				useSkill(player, 23044, true);
				break;
			}
			
			case 6524: // White Maneki Neko Agathion Cute Trick II
			{
				useSkill(player, 23046, true);
				break;
			}
			
			case 6525: // Black Maneki Neko Agathion Cute Trick II
			{
				useSkill(player, 23047, true);
				break;
			}
			
			case 6526: // Brown Maneki Neko Agathion Cute Trick II
			{
				useSkill(player, 23048, true);
				break;
			}
			
			case 6527: // Red Sumo Wrestler Agathion Special Skill - Death Whisper Ability
			{
				useSkill(player, 23057, true);
				break;
			}
			
			case 6528: // Blue Sumo Wrestler Agathion Special Skill - Wild Magic Ability
			{
				useSkill(player, 23058, true);
				break;
			}
			
			case 6529: // Great Sumo Match Agathion Special Skill - Mysterious Power
			{
				useSkill(player, 23059, true);
				break;
			}
			
			case 6530: // Great Sumo Match Agathion Special Skill - Ability of Firework
			{
				useSkill(player, 23060, true);
				break;
			}
			
			case 6531: // Button-Eyed Bear Doll Agathion Special Skill - Ability of Blessed Resurrection
			{
				useSkill(player, 23062, true);
				break;
			}
			
			case 6532: // Button-Eyed Bear Doll Agathion Special Skill - Ability of Vitality Recovery
			{
				useSkill(player, 23063, true);
				break;
			}
			
			case 6533: // God of Fortune Agathion Special Skill - Vitality Recovery Ability
			{
				useSkill(player, 23064, true);
				break;
			}
			
			case 6534: // Wonboso Agathion Special Skill - Wind Walk Ability
			{
				useSkill(player, 23065, true);
				break;
			}
			
			case 6535: // Daewoonso Agathion Special Skill - New Year's Gift Ability
			{
				useSkill(player, 23066, true);
				break;
			}
			
			case 6536: // Pomona Agathion Special Skill - Mental Shield Ability
			{
				useSkill(player, 23068, true);
				break;
			}
			
			case 6537: // Threatening Roar
			{
				useSkill(player, 23069, true);
				break;
			}
			
			case 6538: // Red Flame
			{
				useSkill(player, 23070, true);
				break;
			}
			
			case 6539: // Blue Flame
			{
				useSkill(player, 23071, true);
				break;
			}
			
			case 6540: // Weaver Agathion Special Skill - Power of the Golden Calf
			{
				useSkill(player, 23077, true);
				break;
			}
			
			case 6541: // Weaver Agathion Special Skill - Flute Sound
			{
				useSkill(player, 23078, true);
				break;
			}
			
			case 6542: // Lucky Girl Agathion Special Skill - Brave Warrior Soul's Ability
			{
				useSkill(player, 23080, true);
				break;
			}
			
			case 6543: // Lucky Boy Agathion Special Skill - Great Magician Soul's Ability
			{
				useSkill(player, 23082, true);
				break;
			}
			
			case 6544: // Dancing Child Agathion Special Skill - Prominent Adventurer Soul's Ability
			{
				useSkill(player, 23084, true);
				break;
			}
			
			case 6545: // Son-o-gong Agathion Special Skill - Great Magician Soul's Ability
			{
				useSkill(player, 23086, true);
				break;
			}
			
			case 6546: // Wutangka Agathion Special Skill - Brave Warrior Soul's Ability
			{
				useSkill(player, 23088, true);
				break;
			}
			
			case 6547: // Bonus B Agathion Special Skill - Prominent Adventurer Soul's Ability
			{
				useSkill(player, 23090, true);
				break;
			}
			
			case 6548: // Jack O'Lantern Card
			{
				useSkill(player, 23092, true);
				break;
			}
			
			case 6549: // Rotten Jack O'Lantern Card
			{
				useSkill(player, 23093, true);
				break;
			}
			
			case 6550: // Gwangong Agathion Special Skill - Aura of Fury
			{
				useSkill(player, 23124, true);
				break;
			}
			
			case 6551: // Miss Chipao Agathion Special Skill - Decrease Weight
			{
				useSkill(player, 23134, true);
				break;
			}
			
			case 6552: // Nepal Snow Agathion Special Skill - Snow's Haste
			{
				useSkill(player, 23136, true);
				break;
			}
			
			case 6553: // Round Ball Snow Agathion Special Skill - Snow's Acumen
			{
				useSkill(player, 23138, true);
				break;
			}
			
			case 6554: // Ladder Snow Agathion Special Skill - Snow's Wind Walk
			{
				useSkill(player, 23140, true);
				break;
			}
			
			case 6555: // Prominent Outsider Adventurer's Ability
			{
				useSkill(player, 23153, true);
				break;
			}
			
			case 6556: // Kadomas Special Skill - Fireworks
			{
				useSkill(player, 23154, true);
				break;
			}
			
			case 6557: // Opera Agathion Special Skill - Sword of Life
			{
				useSkill(player, 23164, true);
				break;
			}
			
			case 6558: // Energized Rose Spirit
			{
				useSkill(player, 23166, true);
				break;
			}
			
			case 6559: // Rosy Seduction
			{
				useSkill(player, 23167, true);
				break;
			}
			
			case 6560: // Critical Seduction
			{
				useSkill(player, 23168, true);
				break;
			}
			
			case 6561: // Mesmerization
			{
				useSkill(player, 23169, true);
				break;
			}
			
			case 6562: // Mesmerization
			{
				useSkill(player, 23170, true);
				break;
			}
			
			case 6563: // Phoenix Agathion Special Skill - Nirvana Cycle
			{
				useSkill(player, 23172, true);
				break;
			}
			
			case 6564: // Phoenix Agathion Special Skill - Mountain Echoes
			{
				useSkill(player, 23173, true);
				break;
			}
			
			case 6565: // Narrow Escape
			{
				useSkill(player, 23174, true);
				break;
			}
			
			case 6566: // Three Heads Agathion Special Skill - Wind Walk
			{
				useSkill(player, 23182, true);
				break;
			}
			
			case 6567: // Ball Trapping Gnocian Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23183, true);
				break;
			}
			
			case 6568: // Ball Trapping Orodriel Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23185, true);
				break;
			}
			
			case 6569: // Penalty Kick Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23187, true);
				break;
			}
			
			case 6570: // Ball Trapping Gnocian Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23189, true);
				break;
			}
			
			case 6571: // Ball Trapping Orodriel Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23191, true);
				break;
			}
			
			case 6572: // Penalty Kick Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23193, true);
				break;
			}
			
			case 6573: // Ball Trapping Gnocian Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23195, true);
				break;
			}
			
			case 6574: // Ball Trapping Orodriel Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23197, true);
				break;
			}
			
			case 6575: // Penalty Kick Agathion Special Skill - Buff of Cheering
			{
				useSkill(player, 23199, true);
				break;
			}
			
			case 6576: // Granny Tiger Agathion Special Skill - Fundamentals
			{
				useSkill(player, 23217, true);
				break;
			}
			
			case 6577: // Flower Fairy Spirit Agathion Special Skill - Fairy's Blessing
			{
				useSkill(player, 23219, true);
				break;
			}
			
			case 6578: // Cheerleader Orodriel Agathion Special Skill - Joy of Cheering
			{
				useSkill(player, 23221, true);
				break;
			}
			
			case 6579: // Cheerleader Lana Agathion Special Skill - Joy of Cheering
			{
				useSkill(player, 23223, true);
				break;
			}
			
			case 6580: // Cheerleader Naonin Agathion Special Skill - Joy of Cheering
			{
				useSkill(player, 23225, true);
				break;
			}
			
			case 6581: // Cheerleader Mortia Agathion Special Skill - Joy of Cheering
			{
				useSkill(player, 23227, true);
				break;
			}
			
			case 6582: // Cheerleader Kaurin Agathion Special Skill - Joy of Cheering
			{
				useSkill(player, 23229, true);
				break;
			}
			
			case 6583: // Cheerleader Meruril Agathion Special Skill - Joy of Cheering
			{
				useSkill(player, 23231, true);
				break;
			}
			
			case 6584: // Handy Agathion Special Skill - Halloween's Blessing
			{
				useSkill(player, 23233, true);
				break;
			}
			
			case 6585: // Singer & Dancer Agathion Special Skill - Song of Hunter
			{
				useSkill(player, 23235, true);
				break;
			}
			
			case 6586: // Zaken's Spirit Swords Special Skill - Swords' Song
			{
				useSkill(player, 23237, true);
				break;
			}
			
			case 6587: // Kau Agathion Cute Trick
			{
				useSkill(player, 23250, true);
				break;
			}
			
			case 6588: // Tow Agathion Cute Trick
			{
				useSkill(player, 23251, true);
				break;
			}
			
			case 6589: // Griffin Agathion Cute Trick
			{
				useSkill(player, 23252, true);
				break;
			}
			
			case 6590: // Santa Claus's Blessing
			{
				useSkill(player, 23297, true);
				break;
			}
			
			case 6591: // Cat the Ranger Boots
			{
				useSkill(player, 23318, true);
				break;
			}
			
			case 6592: // Enlarge - Luckpy
			{
				useSkill(player, 23325, true);
				break;
			}
			
			case 6593: // Reduce - Luckpy
			{
				useSkill(player, 23326, true);
				break;
			}
			// Social Packets
			case 12: // Greeting
			{
				tryBroadcastSocial(player, 2);
				break;
			}
			case 13: // Victory
			{
				tryBroadcastSocial(player, 3);
				break;
			}
			case 14: // Advance
			{
				tryBroadcastSocial(player, 4);
				break;
			}
			case 24: // Yes
			{
				tryBroadcastSocial(player, 6);
				break;
			}
			case 25: // No
			{
				tryBroadcastSocial(player, 5);
				break;
			}
			case 26: // Bow
			{
				tryBroadcastSocial(player, 7);
				break;
			}
			case 29: // Unaware
			{
				tryBroadcastSocial(player, 8);
				break;
			}
			case 30: // Social Waiting
			{
				tryBroadcastSocial(player, 9);
				break;
			}
			case 31: // Laugh
			{
				tryBroadcastSocial(player, 10);
				break;
			}
			case 33: // Applaud
			{
				tryBroadcastSocial(player, 11);
				break;
			}
			case 34: // Dance
			{
				tryBroadcastSocial(player, 12);
				break;
			}
			case 35: // Sorrow
			{
				tryBroadcastSocial(player, 13);
				break;
			}
			case 62: // Charm
			{
				tryBroadcastSocial(player, 14);
				break;
			}
			case 66: // Shyness
			{
				tryBroadcastSocial(player, 15);
				break;
			}
		}
	}
	
	/**
	 * Use the sit action.
	 * @param player the player trying to sit
	 * @param target the target to sit, throne, bench or chair
	 * @return {@code true} if the player can sit, {@code false} otherwise
	 */
	protected boolean useSit(Player player, WorldObject target)
	{
		if (player.getMountType() != MountType.NONE)
		{
			return false;
		}
		
		if (!player.isSitting() && (target instanceof StaticObject) && (((StaticObject) target).getType() == 1) && player.isInsideRadius2D(target, StaticObject.INTERACTION_DISTANCE))
		{
			final ChairSit cs = new ChairSit(player, target.getId());
			player.sendPacket(cs);
			player.sitDown();
			player.broadcastPacket(cs);
			return true;
		}
		
		if (player.isFakeDeath())
		{
			player.stopEffects(EffectType.FAKE_DEATH);
		}
		else if (player.isSitting())
		{
			player.standUp();
		}
		else
		{
			player.sitDown();
		}
		return true;
	}
	
	/**
	 * Cast a skill for active summon.<br>
	 * Target is specified as a parameter but can be overwrited or ignored depending on skill type.
	 * @param player the Player
	 * @param skillId the skill Id to be casted by the summon
	 * @param target the target to cast the skill on, overwritten or ignored depending on skill type
	 * @param pet if {@code true} it'll validate a pet, if {@code false} it will validate a servitor
	 */
	private void useSkill(Player player, int skillId, WorldObject target, boolean pet)
	{
		final Summon summon = player.getSummon();
		if (!validateSummon(player, summon, pet))
		{
			return;
		}
		
		if (!canControl(player, summon))
		{
			return;
		}
		
		int level = 0;
		if (summon.isPet())
		{
			level = PetDataTable.getInstance().getPetData(summon.getId()).getAvailableLevel(skillId, summon.getLevel());
		}
		else
		{
			level = PetSkillData.getInstance().getAvailableLevel(summon, skillId);
		}
		
		if (level > 0)
		{
			summon.setTarget(target);
			summon.useMagic(SkillData.getInstance().getSkill(skillId, level), _ctrlPressed, _shiftPressed);
		}
		
		if (skillId == SWITCH_STANCE_ID)
		{
			summon.switchMode();
		}
	}
	
	private void useSkill(Player player, String skillName, WorldObject target, boolean pet)
	{
		final Summon summon = player.getSummon();
		if (!validateSummon(player, summon, pet))
		{
			return;
		}
		
		if (!canControl(player, summon))
		{
			return;
		}
		
		if ((summon instanceof BabyPet) && !((BabyPet) summon).isInSupportMode())
		{
			player.sendPacket(SystemMessageId.A_PET_ON_AUXILIARY_MODE_CANNOT_USE_SKILLS);
			return;
		}
		
		final SkillHolder skillHolder = summon.getTemplate().getParameters().getSkillHolder(skillName);
		if (skillHolder == null)
		{
			return;
		}
		
		final Skill skill = skillHolder.getSkill();
		if (skill != null)
		{
			summon.setTarget(target);
			summon.useMagic(skill, _ctrlPressed, _shiftPressed);
			if (skill.getId() == SWITCH_STANCE_ID)
			{
				summon.switchMode();
			}
		}
	}
	
	private boolean canControl(Player player, Summon summon)
	{
		if ((summon instanceof BabyPet) && !((BabyPet) summon).isInSupportMode())
		{
			player.sendPacket(SystemMessageId.A_PET_ON_AUXILIARY_MODE_CANNOT_USE_SKILLS);
			return false;
		}
		
		if (summon.isPet() && ((summon.getLevel() - player.getLevel()) > 20))
		{
			player.sendPacket(SystemMessageId.YOUR_PET_IS_TOO_HIGH_LEVEL_TO_CONTROL);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Cast a skill for active summon.<br>
	 * Target is retrieved from owner's target, then validated by overloaded method useSkill(int, Creature).
	 * @param player the Player
	 * @param skillId the skill Id to use
	 * @param pet if {@code true} it'll validate a pet, if {@code false} it will validate a servitor
	 */
	private void useSkill(Player player, int skillId, boolean pet)
	{
		useSkill(player, skillId, player.getTarget(), pet);
	}
	
	/**
	 * Cast a skill for active summon.<br>
	 * Target is retrieved from owner's target, then validated by overloaded method useSkill(int, Creature).
	 * @param player the Player
	 * @param skillName the skill name to use
	 * @param pet if {@code true} it'll validate a pet, if {@code false} it will validate a servitor
	 */
	private void useSkill(Player player, String skillName, boolean pet)
	{
		useSkill(player, skillName, player.getTarget(), pet);
	}
	
	/**
	 * Validates the given summon and sends a system message to the master.
	 * @param player the game client
	 * @param summon the summon to validate
	 * @param checkPet if {@code true} it'll validate a pet, if {@code false} it will validate a servitor
	 * @return {@code true} if the summon is not null and whether is a pet or a servitor depending on {@code checkPet} value, {@code false} otherwise
	 */
	private boolean validateSummon(Player player, Summon summon, boolean checkPet)
	{
		if ((summon != null) && ((checkPet && summon.isPet()) || summon.isServitor()))
		{
			if (summon.isPet() && ((Pet) summon).isUncontrollable())
			{
				player.sendPacket(SystemMessageId.ONLY_A_CLAN_LEADER_THAT_IS_A_NOBLESSE_CAN_VIEW_THE_SIEGE_WAR_STATUS_WINDOW_DURING_A_SIEGE_WAR);
				return false;
			}
			if (summon.isBetrayed())
			{
				player.sendPacket(SystemMessageId.YOUR_PET_SERVITOR_IS_UNRESPONSIVE_AND_WILL_NOT_OBEY_ANY_ORDERS);
				return false;
			}
			return true;
		}
		
		if (checkPet)
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_A_PET);
		}
		else
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_A_SERVITOR);
		}
		return false;
	}
	
	/**
	 * Try to broadcast SocialAction
	 * @param player the Player
	 * @param id the social action Id to broadcast
	 */
	private void tryBroadcastSocial(Player player, int id)
	{
		if (player.isFishing())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_DO_THAT_WHILE_FISHING_3);
			return;
		}
		
		if (player.canMakeSocialAction())
		{
			player.broadcastPacket(new SocialAction(player.getObjectId(), id));
		}
	}
	
	/**
	 * Perform a couple social action.
	 * @param player the Player
	 * @param id the couple social action Id
	 */
	private void useCoupleSocial(Player player, int id)
	{
		final WorldObject target = player.getTarget();
		if ((target == null) || !target.isPlayer())
		{
			player.sendPacket(SystemMessageId.INVALID_TARGET);
			return;
		}
		
		final int distance = (int) player.calculateDistance2D(target);
		if ((distance > 125) || (distance < 15) || (player.getObjectId() == target.getObjectId()))
		{
			player.sendPacket(SystemMessageId.THE_REQUEST_CANNOT_BE_COMPLETED_BECAUSE_THE_TARGET_DOES_NOT_MEET_LOCATION_REQUIREMENTS);
			return;
		}
		
		SystemMessage sm;
		if (player.isInStoreMode() || player.isCrafting())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_PRIVATE_SHOP_MODE_OR_IN_A_BATTLE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isInCombat() || player.isInDuel() || AttackStanceTaskManager.getInstance().hasAttackStanceTask(player))
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_BATTLE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isFishing())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_DO_THAT_WHILE_FISHING_3);
			return;
		}
		
		if (player.getKarma() > 0)
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_CHAOTIC_STATE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isInOlympiadMode())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_PARTICIPATING_IN_THE_OLYMPIAD_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isInSiege())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_CASTLE_SIEGE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isInHideoutSiege())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_PARTICIPATING_IN_A_HIDEOUT_SIEGE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
		}
		
		if (player.isMounted() || player.isFlyingMounted() || player.isInBoat() || player.isInAirShip())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_RIDING_A_SHIP_STEED_OR_STRIDER_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isTransformed())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_CURRENTLY_TRANSFORMING_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isAlikeDead())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_CURRENTLY_DEAD_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(player);
			player.sendPacket(sm);
			return;
		}
		
		// Checks for partner.
		final Player partner = target.getActingPlayer();
		if (partner.isInStoreMode() || partner.isCrafting())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_PRIVATE_SHOP_MODE_OR_IN_A_BATTLE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isInCombat() || partner.isInDuel() || AttackStanceTaskManager.getInstance().hasAttackStanceTask(partner))
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_BATTLE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.getMultiSociaAction() > 0)
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_ALREADY_PARTICIPATING_IN_A_COUPLE_ACTION_AND_CANNOT_BE_REQUESTED_FOR_ANOTHER_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isFishing())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_FISHING_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.getKarma() > 0)
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_CHAOTIC_STATE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isInOlympiadMode())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_PARTICIPATING_IN_THE_OLYMPIAD_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isInHideoutSiege())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_PARTICIPATING_IN_A_HIDEOUT_SIEGE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isInSiege())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_IN_A_CASTLE_SIEGE_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isMounted() || partner.isFlyingMounted() || partner.isInBoat() || partner.isInAirShip())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_RIDING_A_SHIP_STEED_OR_STRIDER_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isTeleporting())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_CURRENTLY_TELEPORTING_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isTransformed())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_CURRENTLY_TRANSFORMING_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (partner.isAlikeDead())
		{
			sm = new SystemMessage(SystemMessageId.C1_IS_CURRENTLY_DEAD_AND_CANNOT_BE_REQUESTED_FOR_A_COUPLE_ACTION);
			sm.addPcName(partner);
			player.sendPacket(sm);
			return;
		}
		
		if (player.isAllSkillsDisabled() || partner.isAllSkillsDisabled())
		{
			player.sendPacket(SystemMessageId.THE_COUPLE_ACTION_WAS_CANCELLED);
			return;
		}
		
		player.setMultiSocialAction(id, partner.getObjectId());
		sm = new SystemMessage(SystemMessageId.YOU_HAVE_REQUESTED_A_COUPLE_ACTION_WITH_C1);
		sm.addPcName(partner);
		player.sendPacket(sm);
		
		if ((player.getAI().getIntention() != CtrlIntention.AI_INTENTION_IDLE) || (partner.getAI().getIntention() != CtrlIntention.AI_INTENTION_IDLE))
		{
			player.getAI().setNextAction(new NextAction(CtrlEvent.EVT_ARRIVED, CtrlIntention.AI_INTENTION_MOVE_TO, () -> partner.sendPacket(new ExAskCoupleAction(player.getObjectId(), id))));
			return;
		}
		
		if (player.isCastingNow() || player.isCastingSimultaneouslyNow())
		{
			player.getAI().setNextAction(new NextAction(CtrlEvent.EVT_FINISH_CASTING, CtrlIntention.AI_INTENTION_CAST, () -> partner.sendPacket(new ExAskCoupleAction(player.getObjectId(), id))));
			return;
		}
		
		partner.sendPacket(new ExAskCoupleAction(player.getObjectId(), id));
	}
}
