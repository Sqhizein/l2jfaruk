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

import org.l2jmobius.gameserver.model.Party;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * This packet is received from client when a party leader requests to change the leadership to another player in his party.
 */
public class RequestChangePartyLeader extends ClientPacket
{
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_name = readString();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getPlayer();
		if (player == null)
		{
			return;
		}
		
		final Party party = player.getParty();
		if ((party != null) && party.isLeader(player))
		{
			party.changePartyLeader(_name);
		}
	}
}
