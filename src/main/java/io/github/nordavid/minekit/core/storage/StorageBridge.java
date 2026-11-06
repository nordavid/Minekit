package io.github.nordavid.minekit.core.storage;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Lets features ask what the storage index knows about an item without depending on the Storage Finder module, e.g.
 * the crafting planner showing "64 in storage" next to a missing material. Without a connected, enabled source every
 * question is answered with "nothing known".
 */
public final class StorageBridge {
	/**
	 * What is known about an item in storage.
	 *
	 * @param total            sum over all known containers, as last observed
	 * @param containers       number of containers holding it
	 * @param oldestObservedAt epoch milliseconds of the oldest observation contributing to the total
	 */
	public record Known(long total, int containers, long oldestObservedAt) {
	}

	/** Implemented by the Storage Finder module. */
	public interface Source {
		Optional<Known> known(String itemId);
	}

	private @Nullable Source source;

	public void connect(Source newSource) {
		this.source = newSource;
	}

	public Optional<Known> known(String itemId) {
		return source == null ? Optional.empty() : source.known(itemId);
	}
}
