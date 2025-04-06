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
package handlers.admincommandhandlers;

import java.util.StringTokenizer;

import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.util.BuilderUtil;

/**
 * @author YourName
 */
public class AdminRealFakePlayer implements IAdminCommandHandler
{
    private static final String[] ADMIN_COMMANDS =
    {
        "admin_rfp",
        "admin_rfp_spawn",
        "admin_rfp_delete",
        "admin_rfp_info",
        "admin_rfp_reload"
    };
    
    @Override
    public boolean useAdminCommand(String command, Player activeChar)
    {
        final StringTokenizer st = new StringTokenizer(command, " ");
        final String actualCommand = st.nextToken();
        
        if (actualCommand.equals("admin_rfp"))
        {
            BuilderUtil.sendSysMessage(activeChar, "RealFakePlayer Command Received!");
            showMainMenu(activeChar);
            return true;
        }
        else if (actualCommand.equals("admin_rfp_spawn"))
        {
            BuilderUtil.sendSysMessage(activeChar, "RealFakePlayer Spawn Command Received!");
            return true;
        }
        else if (actualCommand.equals("admin_rfp_delete"))
        {
            BuilderUtil.sendSysMessage(activeChar, "RealFakePlayer Delete Command Received!");
            return true;
        }
        else if (actualCommand.equals("admin_rfp_info"))
        {
            BuilderUtil.sendSysMessage(activeChar, "RealFakePlayer Info Command Received!");
            showRealFakePlayerInfo(activeChar);
            return true;
        }
        else if (actualCommand.equals("admin_rfp_reload"))
        {
            BuilderUtil.sendSysMessage(activeChar, "RealFakePlayer Reload Command Received!");
            return true;
        }
        
        return false;
    }
    
    private void showMainMenu(Player activeChar)
    {
        final NpcHtmlMessage html = new NpcHtmlMessage();
        html.setFile(activeChar, "data/html/admin/realfakeplayer.htm");
        
        // Basic HTML with placeholder values
        html.replace("%equipmentTemplates%", "default");
        html.replace("%farmAreaTemplates%", "default");
        html.replace("%spawnAreaTemplates%", "default");
        
        activeChar.sendPacket(html);
    }
    
    private void showRealFakePlayerInfo(Player activeChar)
    {
        final NpcHtmlMessage html = new NpcHtmlMessage();
        html.setFile(activeChar, "data/html/admin/realfakeplayer_info.htm");
        
        // Placeholder for now
        html.replace("%fakePlayerList%", "No RealFakePlayers found.");
        
        activeChar.sendPacket(html);
    }
    
    @Override
    public String[] getAdminCommandList()
    {
        return ADMIN_COMMANDS;
    }
}