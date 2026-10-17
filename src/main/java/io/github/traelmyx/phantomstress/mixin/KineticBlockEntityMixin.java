package io.github.traelmyx.phantomstress.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.traelmyx.phantomstress.SavedShare;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the share a block saved with its network, measured at the speed it was saved at. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBlockEntityMixin implements SavedShare {
	@Shadow
	protected float speed;
	@Shadow
	protected float lastStressApplied;
	@Shadow
	protected float lastCapacityProvided;

	@Unique
	private boolean phantom_stress_fix$pending;
	@Unique
	private float phantom_stress_fix$stress, phantom_stress_fix$capacity;

	@Inject(method = "read", at = @At("RETURN"), require = 0)
	private void phantom_stress_fix$remember(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
		if (clientPacket || !tag.contains("Network"))
			return;
		phantom_stress_fix$pending = true;
		phantom_stress_fix$stress = lastStressApplied * Math.abs(speed);
		phantom_stress_fix$capacity = lastCapacityProvided * Math.abs(speed);
	}

	@Override
	public boolean phantom_stress_fix$pending() {
		return phantom_stress_fix$pending;
	}

	@Override
	public void phantom_stress_fix$settle() {
		phantom_stress_fix$pending = false;
	}

	@Override
	public float phantom_stress_fix$stress() {
		return phantom_stress_fix$stress;
	}

	@Override
	public float phantom_stress_fix$capacity() {
		return phantom_stress_fix$capacity;
	}
}
