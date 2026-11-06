package io.github.nordavid.minekit.core.notification;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short, unobtrusive status messages for the player, drawn by {@link NotificationHudElement}. Every message is also
 * written to the log. Client thread only.
 */
public final class NotificationService {
	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	private final NotificationQueue<Component> queue = new NotificationQueue<>(3, 16, 4_000, 400);

	public void info(Component message) {
		post(NotificationLevel.INFO, message);
	}

	public void success(Component message) {
		post(NotificationLevel.SUCCESS, message);
	}

	public void warning(Component message) {
		post(NotificationLevel.WARNING, message);
	}

	public void error(Component message) {
		post(NotificationLevel.ERROR, message);
	}

	public void post(NotificationLevel level, Component message) {
		queue.post(level, message);
		// Translatable components resolve lazily; the log shows the key if the language is not loaded yet.
		LOGGER.info("[notification/{}] {}", level, message.getString());
	}

	List<NotificationQueue.Visible<Component>> visibleNow() {
		return queue.visible(Util.getMillis());
	}

	boolean isEmpty() {
		return queue.isEmpty();
	}
}
