package dev.xashmove.client.anim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import dev.xashmove.client.ClientState;
import dev.xashmove.client.ItemBuckets;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/**
 * GoldSrc-flavoured poses on the vanilla player model: weapons held up and aimed along the look pitch
 * (HL's ref_aim sequences), legs tucked while airborne (jump), hand-over-hand ladder climbing, longer
 * running stride with little arm swing. Applied after vanilla's setupAnim and blended by cl_anim_strength.
 */
public final class XashAnim {
	public static final RenderStateDataKey<Data> KEY = RenderStateDataKey.create(() -> "xashmove anim");

	/** aim: 0 nothing held, 1 ranged weapon, 2 anything else. */
	public record Data(boolean self, boolean onGround, boolean climbing, boolean liquid, boolean special,
			int aim, boolean busyArms, float climbPhase) {
	}

	private XashAnim() {
	}

	/** Called at the end of LivingEntityRenderer#extractRenderState. */
	public static void extract(LivingEntity e, EntityRenderState state, float partialTick) {
		if (!(e instanceof Player p)) return;
		CvarStore c = ClientState.config();
		if (c.integer(Cvars.CL_ANIM) != 1) return;
		boolean self = p == Minecraft.getInstance().player;
		if (!self && !c.bool(Cvars.CL_ANIM_OTHERS)) return;

		ItemStack held = p.getMainHandItem();
		int aim = held.isEmpty() ? 0 : ItemBuckets.isRanged(held) ? 1 : 2;
		boolean special = p.isFallFlying() || p.isSwimming() || p.isPassenger() || p.isSleeping() || p.isAutoSpinAttack();
		// Let vanilla's swing/use poses play: item use for everyone, the attack key for ourselves.
		boolean busy = p.isUsingItem() || (self && Minecraft.getInstance().options.keyAttack.isDown());
		((FabricRenderState) state).setData(KEY, new Data(self, p.onGround(), p.onClimbable() && !p.onGround(),
				p.isInWater() || p.isInLava(), special, aim, busy, (float) (p.getY() * 2.6)));
	}

	/** Called at the end of HumanoidModel#setupAnim. */
	public static void apply(HumanoidModel<?> m, EntityRenderState state) {
		Data d = ((FabricRenderState) state).getData(KEY);
		if (d == null || d.special()) return;
		CvarStore c = ClientState.config();
		float s = (float) c.get(Cvars.CL_ANIM_STRENGTH);
		if (s <= 0) return;

		// Running: longer stride, arms mostly still (HL models carry their weapon).
		if (d.onGround() && !d.climbing()) {
			float stride = (float) c.get(Cvars.CL_ANIM_STRIDE);
			set(m.rightLeg, m.rightLeg.xRot * stride, Axis.X, s);
			set(m.leftLeg, m.leftLeg.xRot * stride, Axis.X, s);
			if (!d.busyArms()) {
				float swing = (float) c.get(Cvars.CL_ANIM_ARMSWING);
				set(m.rightArm, m.rightArm.xRot * swing, Axis.X, s);
				set(m.leftArm, m.leftArm.xRot * swing, Axis.X, s);
			}
		}

		// Ladder: hand over hand, driven by height so it only moves while climbing.
		if (d.climbing() && c.bool(Cvars.CL_ANIM_LADDER)) {
			float ph = (float) Math.sin(d.climbPhase());
			set(m.rightArm, -2.7f + ph * 0.35f, Axis.X, s);
			set(m.leftArm, -2.7f - ph * 0.35f, Axis.X, s);
			set(m.rightArm, 0, Axis.Z, s);
			set(m.leftArm, 0, Axis.Z, s);
			set(m.rightLeg, -0.45f - ph * 0.45f, Axis.X, s);
			set(m.leftLeg, -0.45f + ph * 0.45f, Axis.X, s);
			return;
		}

		// Airborne: HL "jump" sequence, knees up.
		boolean airborne = !d.onGround() && !d.liquid();
		if (airborne && c.bool(Cvars.CL_ANIM_JUMP)) {
			set(m.rightLeg, -0.95f, Axis.X, s);
			set(m.leftLeg, -0.35f, Axis.X, s);
			set(m.rightLeg, 0.08f, Axis.Z, s);
			set(m.leftLeg, -0.08f, Axis.Z, s);
			if (!d.busyArms() && d.aim() == 0) {
				set(m.rightArm, -0.5f, Axis.X, s);
				set(m.leftArm, -0.5f, Axis.X, s);
				set(m.rightArm, 0.35f, Axis.Z, s);
				set(m.leftArm, -0.35f, Axis.Z, s);
			}
		}

		// Aim pose: weapon up, following the look pitch.
		int aimMode = c.integer(Cvars.CL_ANIM_AIM);
		if (!d.busyArms() && d.aim() != 0 && (aimMode == 2 || (aimMode == 1 && d.aim() == 1))) {
			float pitch = m.head.xRot, yaw = m.head.yRot;
			if (d.aim() == 1) { // two-handed
				set(m.rightArm, pitch - 1.45f, Axis.X, s);
				set(m.rightArm, yaw - 0.12f, Axis.Y, s);
				set(m.leftArm, pitch - 1.4f, Axis.X, s);
				set(m.leftArm, yaw + 0.55f, Axis.Y, s);
				set(m.rightArm, 0, Axis.Z, s);
				set(m.leftArm, 0, Axis.Z, s);
			} else { // one-handed: held forward, lower
				set(m.rightArm, pitch * 0.5f - 0.95f, Axis.X, s);
				set(m.rightArm, yaw - 0.1f, Axis.Y, s);
				set(m.rightArm, 0, Axis.Z, s);
			}
		}
	}

	private enum Axis { X, Y, Z }

	private static void set(ModelPart part, float target, Axis axis, float strength) {
		switch (axis) {
			case X -> part.xRot += (target - part.xRot) * strength;
			case Y -> part.yRot += (target - part.yRot) * strength;
			case Z -> part.zRot += (target - part.zRot) * strength;
		}
	}
}
