package io.github.traelmyx.phantomstress.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.TorquePropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import io.github.traelmyx.phantomstress.PhantomStressFix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A creative motor drives a shaft, a gearshift, another shaft and an encased fan. The test "reloads" the network the way a
 * chunk load does: every block entity is saved, unloaded and loaded again from what it saved, in a set order, and the
 * network is rebuilt from the saved totals. As in the bug report, a redstone link pair powers the gearshift. When the
 * receiver loads before its transmitter, it briefly reads no signal and switches the gearshift off and on again while the
 * blocks behind the gearshift have not run their own load step yet.
 */
@GameTestHolder(PhantomStressTests.MOD_ID)
@PrefixGameTestTemplate(false)
public class PhantomStressTests {
	public static final String MOD_ID = "phantom_stress_fix_gametest";

	private static final BlockPos MOTOR = new BlockPos(1, 1, 2), GEARSHIFT = new BlockPos(3, 1, 2), FAN = new BlockPos(5, 1, 2);
	private static final BlockPos RECEIVER = new BlockPos(3, 2, 2), TRANSMITTER = new BlockPos(2, 1, 5);
	/** Load order in which the receiver comes before its transmitter, and the fan after both. */
	private static final List<BlockPos> RECEIVER_FIRST = List.of(MOTOR, new BlockPos(2, 1, 2), RECEIVER, GEARSHIFT, new BlockPos(4, 1, 2), TRANSMITTER, FAN);
	private static final List<BlockPos> TRANSMITTER_FIRST = List.of(TRANSMITTER, MOTOR, new BlockPos(2, 1, 2), RECEIVER, GEARSHIFT, new BlockPos(4, 1, 2), FAN);

	private static void build(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 1, 2), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		helper.setBlock(GEARSHIFT, AllBlocks.GEARSHIFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		helper.setBlock(new BlockPos(4, 1, 2), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		helper.setBlock(FAN, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
		// redstone link pair with no frequency items: transmitter powered by a redstone block, receiver on the gearshift
		helper.setBlock(TRANSMITTER, AllBlocks.REDSTONE_LINK.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
		helper.setBlock(TRANSMITTER.west(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(RECEIVER, AllBlocks.REDSTONE_LINK.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP)
			.setValue(RedstoneLinkBlock.RECEIVER, true));
	}

	private static KineticBlockEntity kinetic(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof KineticBlockEntity k ? k : null;
	}

	private static float stress(GameTestHelper helper) {
		KineticBlockEntity fan = kinetic(helper, FAN);
		helper.assertTrue(fan != null && fan.hasNetwork(), "the fan is not on a network");
		return fan.getOrCreateNetwork().calculateStress();
	}

	private static float speed(GameTestHelper helper) {
		KineticBlockEntity fan = kinetic(helper, FAN);
		return fan == null ? 0 : fan.getSpeed();
	}

	@SuppressWarnings("unchecked")
	private static void forgetNetwork(GameTestHelper helper, long id) throws ReflectiveOperationException {
		Field field = TorquePropagator.class.getDeclaredField("networks");
		field.setAccessible(true);
		Map<LevelAccessor, Map<Long, KineticNetwork>> networks = (Map<LevelAccessor, Map<Long, KineticNetwork>>) field.get(null);
		Map<Long, KineticNetwork> map = networks.get(helper.getLevel());
		if (map != null) map.remove(id);
	}

	/** Unloads every block entity of the machine and loads it again from its saved data in the given order, like a chunk reload. */
	private static void reload(GameTestHelper helper, List<BlockPos> order) {
		var level = helper.getLevel();
		var registries = level.registryAccess();
		List<CompoundTag> saved = new ArrayList<>();
		long id = kinetic(helper, MOTOR).network;
		for (BlockPos pos : order) saved.add(level.getBlockEntity(helper.absolutePos(pos)).saveWithFullMetadata(registries));
		for (BlockPos pos : order) {
			BlockEntity be = level.getBlockEntity(helper.absolutePos(pos));
			be.onChunkUnloaded();
			level.removeBlockEntity(helper.absolutePos(pos));
		}
		try {
			forgetNetwork(helper, id);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		for (int i = 0; i < order.size(); i++) {
			BlockPos abs = helper.absolutePos(order.get(i));
			BlockState state = level.getBlockState(abs);
			level.setBlockEntity(BlockEntity.loadStatic(abs, state, saved.get(i), registries));
		}
	}

	private static void run(GameTestHelper helper, boolean fixed, List<BlockPos> order, int reloads, Check check) {
		PhantomStressFix.enabled = fixed;
		build(helper);
		float[] before = {0}, speedBefore = {0};
		helper.runAtTickTime(40, () -> {
			before[0] = stress(helper);
			speedBefore[0] = speed(helper);
		});
		for (int r = 0; r < reloads; r++) helper.runAtTickTime(41 + r * 30, () -> reload(helper, order));
		helper.runAtTickTime(41 + reloads * 30 + 20, () -> {
			float after = stress(helper);
			PhantomStressFix.enabled = true;
			System.out.printf("[phantom_stress_fix] fix %s, %s, %d reloads: stress %.1f before, %.1f after; fan %.0f rpm before, %.0f after%n",
				fixed ? "on" : "off", order == RECEIVER_FIRST ? "receiver loads first" : "transmitter loads first", reloads, before[0], after,
				speedBefore[0], speed(helper));
			check.check(before[0], after);
			helper.assertTrue(Math.abs(speed(helper)) == 16, "the fan turns at " + speed(helper) + " rpm instead of 16");
			helper.succeed();
		});
	}

	private interface Check {
		void check(float before, float after);
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "switched_fixed")
	public static void switchedOnLoadKeepsStress(GameTestHelper helper) {
		run(helper, true, RECEIVER_FIRST, 3, (before, after) -> {
			helper.assertTrue(before > 0, "the fan applied no stress");
			helper.assertTrue(Math.abs(after - before) < 0.01f, "stress changed from " + before + " to " + after);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "switched_control")
	public static void controlReproducesBug(GameTestHelper helper) {
		run(helper, false, RECEIVER_FIRST, 3, (before, after) ->
			// without the fix, phantom stress must show up, or this test can't tell the fix works
			helper.assertTrue(after > before + 0.01f, "no phantom stress without the fix (" + before + " -> " + after + "): the bug did not reproduce"));
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "plain_reload")
	public static void plainReloadKeepsStress(GameTestHelper helper) {
		run(helper, true, TRANSMITTER_FIRST, 2, (before, after) -> {
			helper.assertTrue(before > 0, "the fan applied no stress");
			helper.assertTrue(Math.abs(after - before) < 0.01f, "stress changed from " + before + " to " + after);
		});
	}
}
