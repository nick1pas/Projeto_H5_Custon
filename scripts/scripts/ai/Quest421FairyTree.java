package ai;

import l2mv.gameserver.ai.Fighter;
import l2mv.gameserver.model.Creature;
import l2mv.gameserver.model.Skill;
import l2mv.gameserver.model.instances.NpcInstance;
import l2mv.gameserver.tables.SkillTable;

public class Quest421FairyTree extends Fighter
{
	public Quest421FairyTree(NpcInstance actor)
	{
		super(actor);
		actor.startImmobilized();
	}

	@Override
	protected void onEvtAttacked(Creature attacker, int damage)
	{
		NpcInstance actor = getActor();
		if (attacker != null && attacker.isPlayer())
		{
			Skill skill = SkillTable.getInstance().getInfo(5423, 12);
			skill.getEffects(actor, attacker, false, false);
			return;
		}
		if (attacker.isPet())
		{
			super.onEvtAttacked(attacker, damage);
			return;
		}
	}

	@Override
	protected void onEvtAggression(Creature attacker, int aggro)
	{
		NpcInstance actor = getActor();
		if (attacker != null && attacker.isPlayer())
		{
			Skill skill = SkillTable.getInstance().getInfo(5423, 12);
			skill.getEffects(actor, attacker, false, false);
			return;
		}
	}

	@Override
	protected boolean randomWalk()
	{
		return false;
	}
}