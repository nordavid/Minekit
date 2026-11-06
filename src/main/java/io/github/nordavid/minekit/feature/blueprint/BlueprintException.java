package io.github.nordavid.minekit.feature.blueprint;

import java.util.Arrays;

/**
 * A blueprint file could not be read. Carries a translation key (under {@code minekit.blueprint.error.}) and its
 * arguments, so the player sees a useful message in their language.
 */
public final class BlueprintException extends Exception {
	private final String reason;
	private final transient Object[] arguments;

	public BlueprintException(String reason, Object... arguments) {
		super(reason + Arrays.toString(arguments));
		this.reason = reason;
		this.arguments = arguments.clone();
	}

	/** Last part of the translation key, e.g. {@code too_large}. */
	public String reason() {
		return reason;
	}

	public Object[] arguments() {
		return arguments.clone();
	}

	public String translationKey() {
		return "minekit.blueprint.error." + reason;
	}
}
