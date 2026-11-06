package io.github.nordavid.minekit.core.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationQueueTest {
	private final NotificationQueue<String> queue = new NotificationQueue<>(3, 5, 4_000, 400);

	private List<String> messagesAt(long now) {
		return queue.visible(now).stream().map(NotificationQueue.Visible::message).toList();
	}

	@Test
	void newestMessageComesFirstAndAtMostMaxVisibleAreShown() {
		for (String message : List.of("a", "b", "c", "d")) {
			queue.post(NotificationLevel.INFO, message);
		}

		assertEquals(List.of("d", "c", "b"), messagesAt(0));
	}

	@Test
	void displayTimeStartsWhenFirstShownNotWhenPosted() {
		queue.post(NotificationLevel.INFO, "posted during startup");

		// Nothing was drawn for a long time (game still loading); the message must still appear in full.
		assertEquals(List.of("posted during startup"), messagesAt(60_000));
		assertEquals(List.of("posted during startup"), messagesAt(63_999));
		assertEquals(List.of(), messagesAt(64_000));
		assertTrue(queue.isEmpty());
	}

	@Test
	void hiddenMessagesMoveUpWhenOlderOnesExpire() {
		for (String message : List.of("a", "b", "c", "d")) {
			queue.post(NotificationLevel.INFO, message);
		}
		assertEquals(List.of("d", "c", "b"), messagesAt(0));

		assertEquals(List.of("a"), messagesAt(4_000));
	}

	@Test
	void fadesOutDuringLastPartOfDisplayTime() {
		queue.post(NotificationLevel.INFO, "x");

		assertEquals(1.0F, queue.visible(0).getFirst().opacity(), "first shown at t=0");
		assertEquals(1.0F, queue.visible(1_000).getFirst().opacity());
		assertEquals(1.0F, queue.visible(3_600).getFirst().opacity());
		assertEquals(0.5F, queue.visible(3_800).getFirst().opacity(), 0.001F);
	}

	@Test
	void repeatedMessageIsMovedToFrontInsteadOfDuplicated() {
		queue.post(NotificationLevel.INFO, "same");
		queue.post(NotificationLevel.INFO, "other");
		messagesAt(0);

		queue.post(NotificationLevel.INFO, "same");

		assertEquals(List.of("same", "other"), messagesAt(3_000));
		assertEquals(List.of("same"), messagesAt(4_000), "the repeated message restarted its display time");
	}

	@Test
	void sameTextWithDifferentLevelIsSeparate() {
		queue.post(NotificationLevel.INFO, "msg");
		queue.post(NotificationLevel.ERROR, "msg");

		assertEquals(2, queue.visible(0).size());
	}

	@Test
	void queueIsBoundedDroppingOldest() {
		for (int i = 0; i < 10; i++) {
			queue.post(NotificationLevel.INFO, "m" + i);
		}
		messagesAt(0);
		messagesAt(4_000);

		assertEquals(List.of("m6", "m5"), messagesAt(4_000));
	}

	@Test
	void rejectsInvalidLimits() {
		assertThrows(IllegalArgumentException.class, () -> new NotificationQueue<String>(0, 5, 1_000, 0));
		assertThrows(IllegalArgumentException.class, () -> new NotificationQueue<String>(3, 2, 1_000, 0));
		assertThrows(IllegalArgumentException.class, () -> new NotificationQueue<String>(3, 5, 1_000, 2_000));
	}
}
