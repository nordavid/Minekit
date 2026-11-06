package io.github.nordavid.minekit.feature.deposit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Which items Smart Deposit moves (PROJECT_PLAN.md §6.8). Favorites and, by default, the hotbar are always kept. */
public enum DepositProfile {
	/** Items of which the container already holds some (same item id). */
	MATCHING,
	/** Every item that is not protected. */
	ALL,
	BLOCKS,
	RESOURCES,
	/** Food plus farming items: seeds, crops, saplings, fertilizers. */
	FOOD,
	/** The categories the player selected. */
	CUSTOM;

	public static final List<DepositProfile> ORDER = List.of(values());

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "minekit.deposit.profile." + id();
	}

	public DepositProfile next() {
		return ORDER.get((ordinal() + 1) % ORDER.size());
	}

	public static Optional<DepositProfile> byId(String id) {
		for (DepositProfile profile : values()) {
			if (profile.id().equals(id)) {
				return Optional.of(profile);
			}
		}
		return Optional.empty();
	}
}
