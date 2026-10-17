package io.github.traelmyx.phantomstress;

/** The stress and capacity a kinetic block saved with its network, until its load step has taken them out again. */
public interface SavedShare {
	/** True from loading until the block's share has been taken out of its network's saved totals. */
	boolean phantom_stress_fix$pending();

	void phantom_stress_fix$settle();

	float phantom_stress_fix$stress();

	float phantom_stress_fix$capacity();
}
