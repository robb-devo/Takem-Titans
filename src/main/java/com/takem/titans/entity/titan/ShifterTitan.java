package com.takem.titans.entity.titan;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The body a Titan Shift wraps around its player. It never exists in the world: each client keeps one per shifted player
 * and draws it in the player's place, walking it along with the player (gait, footfalls and all, through the titan's own
 * walk cycle) and playing the blows the server tells it about. When the shift ends it is left behind to boil away.
 */
public class ShifterTitan extends PureTitan {
    /** 1 while the body is still raw muscle, easing to 0 as the skin closes over it. */
    public float forming = 1.0F;
    public float formingO = 1.0F;

    public ShifterTitan(EntityType<? extends ShifterTitan> type, Level level) {
        super(type, level);
        this.setVariant(0);
    }

    /** Follows its player for one client tick: position, facing, height and gait. */
    public void follow(Player p, float height, float bodyYaw) {
        this.setOldPosAndRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.formingO = this.forming;
        this.tickCount++;
        if (Math.abs(this.getTitanHeight() - height) > 0.02F) {
            this.setTitanHeight(height);
        }
        this.setPos(p.getX(), p.getY(), p.getZ());
        this.setYRot(p.getYRot());
        this.setXRot(p.getXRot() * 0.6F);
        this.yHeadRot = p.getYHeadRot();
        this.yBodyRot = bodyYaw;
        this.setFlag(8, p.isSprinting());
        this.setOnGround(p.onGround());
        TitanAction a = this.getAction();
        if (a != TitanAction.NONE && ++this.actionTicks >= a.duration) {
            this.setAction(TitanAction.NONE, null);
        }
        this.updateWalk();
    }

    /** Starts a blow some way into its wind-up, so it lands sooner after the click than a wild titan's swing would. */
    public void play(TitanAction action, int skip) {
        this.setAction(action, null);
        this.actionTicks = skip;
    }
}
