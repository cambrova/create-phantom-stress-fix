package io.github.traelmyx.phantomstress.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.traelmyx.phantomstress.PhantomStressFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * {@code addSilently} is each block's load step: it takes the block's saved share out of the network's saved totals and
 * then adds the block for real. A block that already joined the network (because the network changed while loading) was
 * skipped entirely, leaving its share counted twice. Its share is now taken out of the saved totals anyway.
 */
@Mixin(value = KineticNetwork.class, remap = false)
public abstract class KineticNetworkMixin {
	@Shadow
	public Map<KineticBlockEntity, Float> members;
	@Shadow
	private float unloadedCapacity;
	@Shadow
	private float unloadedStress;
	@Shadow
	private int unloadedMembers;

	@Shadow
	public abstract void updateNetwork();

	@Inject(method = "addSilently", at = @At("HEAD"), cancellable = true, require = 0)
	private void phantom_stress_fix$removeSavedShare(KineticBlockEntity be, float lastCapacity, float lastStress, CallbackInfo ci) {
		if (!PhantomStressFix.enabled || !members.containsKey(be))
			return;
		if (be.isSource())
			unloadedCapacity = Math.max(0, unloadedCapacity - lastCapacity * Math.abs(be.getGeneratedSpeed()));
		unloadedStress = Math.max(0, unloadedStress - lastStress * Math.abs(be.getTheoreticalSpeed()));
		unloadedMembers = Math.max(0, unloadedMembers - 1);
		updateNetwork();
		ci.cancel();
	}
}
