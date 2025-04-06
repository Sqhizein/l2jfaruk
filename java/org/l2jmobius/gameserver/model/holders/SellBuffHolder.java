/*
 
This file is part of the L2J Mobius project.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program. If not, see http://www.gnu.org/licenses/.*/
package org.l2jmobius.gameserver.model.holders;

/**
 
Simple class for storing info for Selling Buffs system.
@author St3eT*/
public class SellBuffHolder
{
    private final int _skillId;
    private long _price;
    private final String _enchantType;  // Eklendi: Buff türünü belirtir (örn. "Power", "Time").
    private final String _enchantEffect; // Eklendi: Buff etkisini belirtir (örn. "+30").

    // Yeni eklenen enchantType ve enchantEffect parametreleriyle kurucu
    public SellBuffHolder(int skillId, long price, String enchantType, String enchantEffect)
    {
        _skillId = skillId;
        _price = price;
        _enchantType = enchantType;
        _enchantEffect = enchantEffect;
    }

    // Orijinal kurucu: Varsayılan olarak boş enchantType ve enchantEffect
    public SellBuffHolder(int skillId, long price)
    {
        this(skillId, price, "", "");
    }

    public int getSkillId()
    {
        return _skillId;
    }

    public void setPrice(int price)
    {
        _price = price;
    }

    public long getPrice()
    {
        return _price;
    }

    public String getEnchantType()
    {
        return _enchantType;
    }

    public String getEnchantEffect()
    {
        return _enchantEffect;
    }
}