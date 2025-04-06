package org.l2jmobius.gameserver.network.serverpackets;

import java.util.Collection;

import org.l2jmobius.Config;
import org.l2jmobius.commons.network.WritableBuffer;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.ServerPackets;

/**
 * @author ShanSoft
 */
public class ExBuySellList extends AbstractItemPacket
{
	private final Collection<Item> _sellList;
	private Collection<Item> _refundList = null;
	private final boolean _done;
	private final Player _player;
	
	public ExBuySellList(Player player, boolean done)
	{
		_player = player;
		_sellList = player.getInventory().getAvailableItems(false, false, false);
		if (player.hasRefund())
		{
			_refundList = player.getRefund().getItems();
		}
		_done = done;
	}
	
	@Override
	public void writeImpl(GameClient client, WritableBuffer buffer)
	{
		ServerPackets.EX_BUY_SELL_LIST.writeId(this, buffer);
		buffer.writeInt(1);
		
		// Satış listesi
		if ((_sellList != null))
		{
			buffer.writeShort(_sellList.size());
			for (Item item : _sellList)
			{
				writeItem(item, buffer);
				
				// Normal satış fiyatı hesaplama
				long price = Config.MERCHANT_ZERO_SELL_PRICE ? 0 : item.getTemplate().getReferencePrice() / 2;
				
				// Premium bonus kontrolü - Satış için bonus uygula
				if (!Config.MERCHANT_ZERO_SELL_PRICE && Config.PREMIUM_SYSTEM_ENABLED && _player.hasPremiumStatus())
				{
					price = (long) (price * Config.PREMIUM_RATE_SELL_PRICE);
				}
				
				buffer.writeLong(price);
			}
		}
		else
		{
			buffer.writeShort(0);
		}
		
		// Geri alım listesi
		if ((_refundList != null) && !_refundList.isEmpty())
		{
			buffer.writeShort(_refundList.size());
			int i = 0;
			for (Item item : _refundList)
			{
				writeItem(item, buffer);
				buffer.writeInt(i++);
				
				// Geri alım fiyatı hesaplama - Aynı yuvarlama mantığı ile
				long basePrice = Config.MERCHANT_ZERO_SELL_PRICE ? 0 : (item.getTemplate().getReferencePrice() / 2);
				long refundPrice = basePrice * item.getCount();
				
				// Premium bonus kontrolü - Aynı yuvarlama mantığı
				if (!Config.MERCHANT_ZERO_SELL_PRICE && Config.PREMIUM_SYSTEM_ENABLED && _player.hasPremiumStatus())
				{
					refundPrice = (long) (basePrice * Config.PREMIUM_RATE_SELL_PRICE) * item.getCount();
				}
				
				buffer.writeLong(refundPrice);
			}
		}
		else
		{
			buffer.writeShort(0);
		}
		
		buffer.writeByte(_done);
	}
}