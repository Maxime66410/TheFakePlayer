package org.furranystudio.thefakeplayer.Events;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Thefakeplayer;

@Mod.EventBusSubscriber(modid = Thefakeplayer.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster monster)) return;

        monster.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(monster, FakePlayerEntity.class, true, false));
    }
}
