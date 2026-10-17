package io.github.traelmyx.phantomstress;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Create issue #10516: phantom stress after a reload.
 * <p>
 * A kinetic network that loads from disk starts from the stress and capacity it saved, and each block subtracts its own
 * share when it loads. If something changes the network while it is loading, for example a redstone link switching a
 * gearshift a tick after the chunk loads, some blocks join the network before their own load step. When that step comes,
 * Create sees they are already members and skips them, so their share is never subtracted. It stays on the network as
 * stress nothing applies, is saved with it, and is counted again on the next load.
 */
@Mod(PhantomStressFix.MOD_ID)
public class PhantomStressFix {
	public static final String MOD_ID = "phantom_stress_fix";
	public static final Logger LOGGER = LoggerFactory.getLogger("Phantom Stress Fix");

	public static volatile boolean enabled = true;

	public PhantomStressFix() {
		loadConfig(FMLPaths.CONFIGDIR.get());
		try {
			Class.forName("com.simibubi.create.content.kinetics.KineticNetwork", false, PhantomStressFix.class.getClassLoader())
				.getDeclaredField("unloadedStress");
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("This Create version changed how networks load; the fix may be inactive ({})", e.toString());
		}
	}

	static void loadConfig(Path configDir) {
		Path file = configDir.resolve(MOD_ID + ".properties");
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				props.load(reader);
			} catch (IOException e) {
				LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
		props.setProperty("enabled", Boolean.toString(enabled));
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(file)) {
				props.store(writer, "Phantom Stress Fix. enabled: blocks that join a network while it loads have their saved stress removed from the network's saved total.");
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file, e);
		}
	}
}
