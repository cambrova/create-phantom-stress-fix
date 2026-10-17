package io.github.traelmyx.phantomstress.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.traelmyx.phantomstress.PhantomStressFix;
import io.github.traelmyx.phantomstress.SavedShare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * A loading network starts from its saved stress and capacity, and each block's load step ({@code addSilently}) takes
 * that block's share back out. Two ways skip that step: the block was disconnected before it (a gearshift switched by a
 * redstone link that loaded first), or it already rejoined the network. Its share then stayed on the network for good.
 * Here the share is taken out in both cases, once per loaded block.
 */
@Mixin(value = KineticNetwork.class, remap = false)
public abstract class KineticNetworkMixin {
	@Shadow
	public boolean initialized;
	@Shadow
	public Map<KineticBlockEntity, Float> members;
	@Shadow
	private float unloadedCapacity;
	@Shadow
	private float unloadedStress;
	@Shadow
	private int unloadedMembers;

	@Unique
	private float phantom_stress_fix$earlyStress, phantom_stress_fix$earlyCapacity;
	@Unique
	private int phantom_stress_fix$earlyMembers;

	@Shadow
	public abstract void updateNetwork();

	@Unique
	private void phantom_stress_fix$takeOut(SavedShare share) {
		share.phantom_stress_fix$settle();
		if (!initialized) {
			// the saved totals arrive later, with the first block that loads normally
			phantom_stress_fix$earlyStress += share.phantom_stress_fix$stress();
			phantom_stress_fix$earlyCapacity += share.phantom_stress_fix$capacity();
			phantom_stress_fix$earlyMembers++;
			return;
		}
		unloadedStress = Math.max(0, unloadedStress - share.phantom_stress_fix$stress());
		unloadedCapacity = Math.max(0, unloadedCapacity - share.phantom_stress_fix$capacity());
		unloadedMembers = Math.max(0, unloadedMembers - 1);
		updateNetwork();
	}

	@Inject(method = "initFromTE", at = @At("RETURN"), require = 0)
	private void phantom_stress_fix$applyEarly(float maxStress, float currentStress, int size, CallbackInfo ci) {
		if (phantom_stress_fix$earlyMembers == 0)
			return;
		unloadedStress = Math.max(0, unloadedStress - phantom_stress_fix$earlyStress);
		unloadedCapacity = Math.max(0, unloadedCapacity - phantom_stress_fix$earlyCapacity);
		unloadedMembers = Math.max(0, unloadedMembers - phantom_stress_fix$earlyMembers);
		phantom_stress_fix$earlyStress = phantom_stress_fix$earlyCapacity = 0;
		phantom_stress_fix$earlyMembers = 0;
		updateNetwork();
	}

	@Inject(method = "addSilently", at = @At("HEAD"), cancellable = true, require = 0)
	private void phantom_stress_fix$loadStep(KineticBlockEntity be, float lastCapacity, float lastStress, CallbackInfo ci) {
		if (!PhantomStressFix.enabled || !(be instanceof SavedShare share) || !share.phantom_stress_fix$pending())
			return;
		if (members.containsKey(be)) {
			// already rejoined; Create would skip it
			phantom_stress_fix$takeOut(share);
			ci.cancel();
		} else {
			// Create takes the share out itself
			share.phantom_stress_fix$settle();
		}
	}

	@Inject(method = "remove", at = @At("HEAD"), require = 0)
	private void phantom_stress_fix$leftBeforeLoading(KineticBlockEntity be, CallbackInfo ci) {
		if (PhantomStressFix.enabled && !members.containsKey(be) && be instanceof SavedShare share && share.phantom_stress_fix$pending())
			phantom_stress_fix$takeOut(share);
	}
}
