package dev.faithrunner;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** config/faithrunner.properties: where faith_ffi.dll and Mirror's Edge are. */
final class Config {
	Path dll;
	/** The Mirror's Edge folder; empty: faith_ffi looks in the usual places. */
	String mirrorsEdge = "";

	static Config load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("faithrunner.properties");
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				p.load(r);
			} catch (IOException e) {
				FaithRunner.LOG.warn("couldn't read {}", file, e);
			}
		}
		Config c = new Config();
		String dll = p.getProperty("faith_ffi", "");
		c.dll = dll.isEmpty() ? FabricLoader.getInstance().getGameDir().resolve("faith_ffi.dll") : Path.of(dll);
		c.mirrorsEdge = p.getProperty("mirrors_edge", "");
		// Development runs point at the DLL cargo built.
		String override = System.getProperty("faithrunner.dll", "");
		if (!override.isEmpty()) {
			c.dll = Path.of(override);
		}
		if (!Files.exists(file)) {
			p.setProperty("faith_ffi", dll);
			p.setProperty("mirrors_edge", c.mirrorsEdge);
			try (Writer w = Files.newBufferedWriter(file)) {
				p.store(w, "Faith Runner. faith_ffi: the DLL (empty: faith_ffi.dll in the game folder). mirrors_edge: your Mirror's Edge folder (empty: the usual places).");
			} catch (IOException e) {
				FaithRunner.LOG.warn("couldn't write {}", file, e);
			}
		}
		return c;
	}
}
