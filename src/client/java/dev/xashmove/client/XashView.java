package dev.xashmove.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import dev.xashmove.Mode;
import dev.xashmove.XashRuntime;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;
import dev.xashmove.physics.MoveVars;
import dev.xashmove.physics.PmCore;
import dev.xashmove.physics.XashPlayerAccess;
import dev.xashmove.physics.XashPlayerState;

/** V_CalcRoll + punch angles, applied to the first-person camera. */
public final class XashView {
	private XashView() {
	}

	public static void apply(PoseStack pose) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		MoveVars v = XashRuntime.clientVars();
		if (p == null || v == null || v.mode == Mode.OFF || !mc.options.getCameraType().isFirstPerson()) return;
		CvarStore c = ClientState.config();

		float roll = 0;
		double rollAngle = c.get(Cvars.CL_ROLLANGLE);
		if (rollAngle != 0) {
			Vec3 dm = p.getDeltaMovement();
			double[] vel = {v.toUnitsPerSecond(dm.x), 0, v.toUnitsPerSecond(dm.z)};
			roll = (float) PmCore.calcRoll(vel, p.getYRot(), rollAngle, c.get(Cvars.CL_ROLLSPEED));
		}
		float pitch = 0;
		if (c.bool(Cvars.CL_VIEWPUNCH)) {
			XashPlayerState s = ((XashPlayerAccess) p).xash$getState();
			roll += s.punchRoll;
			pitch += s.punchPitch;
		}
		if (roll != 0) pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(roll)));
		if (pitch != 0) pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(pitch)));
	}
}
