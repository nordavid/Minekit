package io.github.nordavid.minekit.core.notification;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Bounded queue of short status messages. Pure logic; the caller supplies the current time.
 *
 * <p>A message's display time starts when it is first shown, not when it is posted, so messages posted during
 * startup (before anything can be drawn) are not lost. Posting a message equal to one that is still queued moves it
 * to the front and restarts its display time instead of stacking duplicates.
 *
 * @param <T> message type, compared with {@link Object#equals}
 */
public final class NotificationQueue<T> {
	/**
	 * A message as it should be drawn now.
	 *
	 * @param opacity 1 while fully visible, falling towards 0 during the fade-out at the end of the display time
	 */
	public record Visible<T>(T message, NotificationLevel level, float opacity) {
	}

	private static final long NOT_SHOWN = -1;

	private static final class Entry<T> {
		final T message;
		final NotificationLevel level;
		long shownAt = NOT_SHOWN;

		Entry(T message, NotificationLevel level) {
			this.message = message;
			this.level = level;
		}
	}

	private final int maxVisible;
	private final int maxQueued;
	private final long displayMillis;
	private final long fadeMillis;
	/** Newest first. */
	private final Deque<Entry<T>> entries = new ArrayDeque<>();

	public NotificationQueue(int maxVisible, int maxQueued, long displayMillis, long fadeMillis) {
		if (maxVisible < 1 || maxQueued < maxVisible || displayMillis <= 0 || fadeMillis < 0 || fadeMillis > displayMillis) {
			throw new IllegalArgumentException("invalid queue limits");
		}
		this.maxVisible = maxVisible;
		this.maxQueued = maxQueued;
		this.displayMillis = displayMillis;
		this.fadeMillis = fadeMillis;
	}

	public void post(NotificationLevel level, T message) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(message, "message");
		entries.removeIf(entry -> entry.level == level && entry.message.equals(message));
		entries.addFirst(new Entry<>(message, level));
		while (entries.size() > maxQueued) {
			entries.removeLast();
		}
	}

	/**
	 * Returns the messages to draw at {@code now}, newest first, and starts the display time of messages that
	 * become visible.
	 */
	public List<Visible<T>> visible(long now) {
		entries.removeIf(entry -> entry.shownAt != NOT_SHOWN && now >= entry.shownAt + displayMillis);
		List<Visible<T>> result = new ArrayList<>(Math.min(maxVisible, entries.size()));
		Iterator<Entry<T>> iterator = entries.iterator();
		while (iterator.hasNext() && result.size() < maxVisible) {
			Entry<T> entry = iterator.next();
			if (entry.shownAt == NOT_SHOWN) {
				entry.shownAt = now;
			}
			result.add(new Visible<>(entry.message, entry.level, opacity(now - entry.shownAt)));
		}
		return result;
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	private float opacity(long elapsed) {
		long remaining = displayMillis - elapsed;
		if (fadeMillis == 0 || remaining >= fadeMillis) {
			return 1.0F;
		}
		return Math.max(0.0F, remaining / (float) fadeMillis);
	}
}
