package io.github.nordavid.minekit.core.config;

import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import java.nio.file.Path;
import java.time.Clock;

/** The main settings file, {@code config/minekit/config.json}. All safety guarantees come from {@link JsonFileStore}. */
public final class ConfigManager extends JsonFileStore<MinekitConfig> {
	public ConfigManager(Path file, Clock clock) {
		super(file, clock, ConfigCodec.INSTANCE);
	}
}
