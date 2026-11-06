package io.github.nordavid.minekit.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nordavid.minekit.core.persistence.LoadResult;
import io.github.nordavid.minekit.core.persistence.LoadResult.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T14:30:00Z"), ZoneOffset.UTC);

	@TempDir
	Path dir;

	private Path configFile() {
		return dir.resolve("minekit").resolve("config.json");
	}

	private ConfigManager manager() {
		return new ConfigManager(configFile(), CLOCK);
	}

	private List<String> filesInConfigDir() throws IOException {
		try (Stream<Path> files = Files.list(configFile().getParent())) {
			return files.map(path -> path.getFileName().toString()).sorted().toList();
		}
	}

	@Test
	void missingFileIsCreatedWithDefaults() throws IOException {
		LoadResult result = manager().load();

		assertEquals(Status.CREATED, result.status());
		assertFalse(result.needsAttention());
		assertTrue(Files.readString(configFile()).contains("\"schemaVersion\": 1"));
	}

	@Test
	void changesSurviveARestart() {
		ConfigManager first = manager();
		first.load();
		assertTrue(first.update(config -> config
				.withModuleEnabled("quick_actions", false)
				.withHudElement("notifications", new HudElementConfig(true, HudAnchor.BOTTOM_RIGHT, 8, 40))));

		ConfigManager afterRestart = manager();
		LoadResult result = afterRestart.load();

		assertEquals(Status.LOADED, result.status());
		assertEquals(first.current(), afterRestart.current());
		assertEquals(Optional.of(false), afterRestart.current().moduleEnabled("quick_actions"));
	}

	@Test
	void saveLeavesNoTemporaryFileBehind() throws IOException {
		ConfigManager manager = manager();
		manager.load();
		manager.update(config -> config.withModuleEnabled("quick_actions", true));

		assertEquals(List.of("config.json"), filesInConfigDir());
	}

	@Test
	void corruptFileIsBackedUpAndReplacedWithDefaults() throws IOException {
		Files.createDirectories(configFile().getParent());
		Files.writeString(configFile(), "{ this is not json", StandardCharsets.UTF_8);

		ConfigManager manager = manager();
		LoadResult result = manager.load();

		assertEquals(Status.RECOVERED_CORRUPT, result.status());
		assertTrue(result.needsAttention());
		assertEquals(MinekitConfig.DEFAULT, manager.current());
		Path backup = result.backup().orElseThrow();
		assertEquals("config.json.corrupt-20261106-143000", backup.getFileName().toString());
		assertEquals("{ this is not json", Files.readString(backup));
		assertTrue(Files.readString(configFile()).contains("schemaVersion"));
	}

	@Test
	void fileFromNewerVersionIsBackedUpBeforeFirstOverwrite() throws IOException {
		Files.createDirectories(configFile().getParent());
		String newer = "{ \"schemaVersion\": 2, \"modules\": { \"quick_actions\": { \"enabled\": false } }, \"futureField\": 1 }";
		Files.writeString(configFile(), newer, StandardCharsets.UTF_8);

		ConfigManager manager = manager();
		LoadResult result = manager.load();
		assertEquals(Status.NEWER_SCHEMA, result.status());
		assertEquals(Optional.of(false), manager.current().moduleEnabled("quick_actions"));
		assertEquals(List.of("config.json"), filesInConfigDir(), "loading alone must not write anything");

		manager.update(config -> config.withModuleEnabled("quick_actions", true));

		Path backup = configFile().resolveSibling("config.json.newer-schema-20261106-143000.bak");
		assertEquals(newer, Files.readString(backup));
	}

	@Test
	void unreadableFileIsNeverOverwritten() throws IOException {
		// A directory in place of the file makes reading fail with an IOException on every platform.
		Files.createDirectories(configFile());

		ConfigManager manager = manager();
		LoadResult result = manager.load();

		assertEquals(Status.READ_FAILED, result.status());
		assertFalse(manager.isSavingAllowed());
		assertFalse(manager.update(config -> config.withModuleEnabled("quick_actions", false)));
		assertTrue(Files.isDirectory(configFile()));
		assertEquals(Optional.of(false), manager.current().moduleEnabled("quick_actions"), "value still applies for this session");
	}

	@Test
	void unchangedUpdateDoesNotWrite() throws IOException {
		ConfigManager manager = manager();
		manager.load();
		Files.writeString(configFile(), "{ \"schemaVersion\": 1 }", StandardCharsets.UTF_8);

		assertTrue(manager.update(config -> config));

		assertEquals("{ \"schemaVersion\": 1 }", Files.readString(configFile()));
	}
}
