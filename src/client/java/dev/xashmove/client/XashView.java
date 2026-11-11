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

/** V_CalcRoll, V_CalcBob, punch angles and zoom for the first-person camera. */
public final class XashView {
	private static boolean zoomToggled;
	private static float zoomBlend;

	private XashView() {
	}

	// ------------------------------------------------------------------ zoom
	public static void tickZoom(boolean keyDown, boolean keyClicked) {
		Minecraft mc = Minecraft.getInstance();
		CvarStore c = ClientState.config();
		boolean hold = c.integer(Cvars.CL_ZOOM_MODE) == 0;
		if (!hold && keyClicked) zoomToggled = !zoomToggled;
		boolean want = hold ? keyDown : zoomToggled;
		if (want && c.integer(Cvars.CL_ZOOM_ITEMS) == 1) {
			LocalPlayer p = mc.player;
			want = p != null && (ItemBuckets.isRanged(p.getMainHandItem()) || ItemBuckets.isRanged(p.getOffhandItem()));
		}
		if (mc.player == null) want = false;
		zoomBlend += ((want ? 1f : 0f) - zoomBlend) * 0.6f;
		if (zoomBlend < 0.001f) zoomBlend = 0;
	}

	/** Multiplier applied to the final field of view. */
	public static double fovMultiplier() {
		if (zoomBlend <= 0) return 1.0;
		double target = ClientState.config().get(Cvars.CL_ZOOM_FOV);
		return 1.0 + (target - 1.0) * zoomBlend;
	}

	// ------------------------------------------------------------------ bob
	/** @return true if vanilla bobbing should be skipped */
	public static boolean bob(PoseStack pose, float partialTick) {
		CvarStore c = ClientState.config();
		int style = c.integer(Cvars.CL_BOB_STYLE);
		if (style == 0) return false;
		if (style == 2) return true;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return true;
		MoveVars v = XashRuntime.clientVars();
		double upb = v != null ? v.unitsPerBlock : Cvars.SV_UNITS_PER_BLOCK.def;

		// V_CalcBob from the HL client
		double time = (p.tickCount + partialTick) / 20.0;
		double bobcycle = c.get(Cvars.CL_BOBCYCLE), bobup = c.get(Cvars.CL_BOBUP);
		double cycle = time - Math.floor(time / bobcycle) * bobcycle;
		cycle /= bobcycle;
		cycle = cycle < bobup ? Math.PI * cycle / bobup : Math.PI + Math.PI * (cycle - bobup) / (1.0 - bobup);
		Vec3 dm = p.getDeltaMovement();
		double speed = Math.hypot(dm.x, dm.z) * 20.0 * upb;
		if (!p.onGround()) speed = 0;
		double bob = speed * c.get(Cvars.CL_BOB);
		bob = bob * 0.3 + bob * 0.7 * Math.sin(cycle);
		bob = Math.max(-7, Math.min(4, bob));
		pose.translate(0.0f, (float) (-bob / upb), 0.0f);
		return true;
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
