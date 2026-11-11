package dev.xashmove.physics;

/**
 * Values only reachable from inside the LivingEntity mixin (protected members), handed to the physics code.
 *
 * @param jumping           LivingEntity#jumping (jump key held)
 * @param blockSpeedFactor  soul sand / honey slowdown
 * @param blockJumpFactor   honey jump reduction
 * @param stuck             cobweb / berry bush / powder snow are slowing the player this tick
 */
public record MoveContext(boolean jumping, float blockSpeedFactor, float blockJumpFactor, boolean stuck) {
}
