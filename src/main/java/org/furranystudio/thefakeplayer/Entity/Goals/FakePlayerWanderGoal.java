package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

public class FakePlayerWanderGoal extends RandomStrollGoal {

    public FakePlayerWanderGoal(FakePlayerEntity entity) {
        super(entity, 1.0D);
    }

    @Override
    protected Vec3 getPosition() {
        return DefaultRandomPos.getPos(this.mob, 50, 7);
    }
}
