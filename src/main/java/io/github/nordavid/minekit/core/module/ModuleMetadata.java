package io.github.nordavid.minekit.core.module;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Static description of a feature module.
 *
 * @param id               stable identifier, used as the config key; never change it after release
 * @param nameKey          translation key of the display name
 * @param descriptionKey   translation key of a one-sentence description
 * @param enabledByDefault state used until the user changes it
 */
public record ModuleMetadata(String id, String nameKey, String descriptionKey, boolean enabledByDefault) {
	private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,63}");

	public ModuleMetadata {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(nameKey, "nameKey");
		Objects.requireNonNull(descriptionKey, "descriptionKey");
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("Module id must match " + ID.pattern() + ": " + id);
		}
	}
}
