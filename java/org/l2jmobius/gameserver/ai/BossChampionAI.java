package org.l2jmobius.gameserver.ai;

import java.util.logging.Logger;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.skill.Skill;

public class BossChampionAI extends AttackableAI
{
    private static final Logger LOGGER = Logger.getLogger(BossChampionAI.class.getName());
    private static final int SKILL_CHANCE = 95; // %95'e çıkardık
    private static final int SKILL_INTERVAL = 1500; // 1.5 saniyeye indirdik
    private long _lastSkillTime;
    private int _attackCount;

    public BossChampionAI(Monster creature)
    {
        super(creature);
        _lastSkillTime = 0;
        _attackCount = 0;
    }

    @Override
    protected void onEvtAttacked(Creature attacker)
    {
        super.onEvtAttacked(attacker);
        
        // Saldırıya uğradığında hemen karşılık ver
        if (attacker != null)
        {
            _actor.setRunning();
            tryUseSkill(attacker);
        }
    }

    @Override
    protected void thinkAttack()
    {
        final Monster monster = (Monster) _actor;
        final Creature target = getAttackTarget();
        
        if ((target == null) || target.isDead())
        {
            super.thinkAttack();
            return;
        }

        // Her zaman koşarak hareket et
        monster.setRunning();

        // Her 3 normal saldırıdan sonra kesin skill kullan
        _attackCount++;
        if (_attackCount >= 3)
        {
            if (forceUseSkill(target))
            {
                _attackCount = 0;
            }
        }
        else
        {
            tryUseSkill(target);
        }

        super.thinkAttack();
    }

    private boolean forceUseSkill(Creature target)
    {
        if ((target == null) || target.isDead())
        {
            return false;
        }

        final Monster monster = (Monster) _actor;
        if (monster.isCastingNow())
        {
            return false;
        }

        // En güçlü skilli bul ve kullan
        Skill bestSkill = null;
        int highestPower = 0;

        for (Skill skill : monster.getSkills().values())
        {
            if (skill != null && !monster.isSkillDisabled(skill))
            {
                if (bestSkill == null || skill.getPower() > highestPower)
                {
                    bestSkill = skill;
                    highestPower = (int) skill.getPower();
                }
            }
        }

        if (bestSkill != null)
        {
            monster.doCast(bestSkill);
            _lastSkillTime = System.currentTimeMillis();
            LOGGER.info("Boss Champion " + monster.getName() + " force using skill: " + bestSkill.getName());
            return true;
        }

        return false;
    }

    private void tryUseSkill(Creature target)
    {
        if ((target == null) || target.isDead())
        {
            return;
        }

        final Monster monster = (Monster) _actor;
        if (monster.isCastingNow())
        {
            return;
        }

        // Skill kullanma zamanı kontrolü
        if ((System.currentTimeMillis() - _lastSkillTime) < SKILL_INTERVAL)
        {
            return;
        }

        // Yüksek skill kullanma şansı
        if (Rnd.get(100) < SKILL_CHANCE)
        {
            // HP düşükse daha agresif davran
            if (monster.getCurrentHp() < (monster.getMaxHp() * 0.5))
            {
                forceUseSkill(target); // HP düşükse en güçlü skilli kullan
            }
            else
            {
                // Random skill kullan
                for (Skill skill : monster.getSkills().values())
                {
                    if (skill != null && !monster.isSkillDisabled(skill))
                    {
                        monster.doCast(skill);
                        _lastSkillTime = System.currentTimeMillis();
                        LOGGER.info("Boss Champion " + monster.getName() + " using skill: " + skill.getName());
                        break;
                    }
                }
            }
        }
    }
}