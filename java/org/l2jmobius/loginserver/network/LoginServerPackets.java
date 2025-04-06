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
package org.l2jmobius.loginserver.network;

import org.l2jmobius.commons.network.base.BaseWritablePacket;

/**
 * @author Mobius
 */
public enum LoginServerPackets
{
	INIT(0x00),
	LOGIN_FAIL(0x01),
	ACCOUNT_KICKED(0x02),
	LOGIN_OK(0x03),
	SERVER_LIST(0x04),
	PLAY_FAIL(0x06),
	PLAY_OK(0x07),
	
	PI_AGREEMENT_CHECK(0x11),
	PI_AGREEMENT_ACK(0x12),
	GG_AUTH(0x0b),
	LOGIN_OPT_FAIL(0x0D);
	
	private final int _id;
	
	LoginServerPackets(int id)
	{
		_id = id;
	}
	
	public void writeId(BaseWritablePacket packet)
	{
		packet.writeByte(_id);
	}
}
