package io.github.nordavid.minekit.core.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered set of dashboard pages. Pages appear in the sidebar in registration order. */
public final class DashboardPages {
	private final List<DashboardPage> pages = new ArrayList<>();

	public void register(DashboardPage page) {
		Objects.requireNonNull(page, "page");
		if (pages.stream().anyMatch(existing -> existing.id().equals(page.id()))) {
			throw new IllegalArgumentException("Duplicate dashboard page id: " + page.id());
		}
		pages.add(page);
	}

	public List<DashboardPage> visible() {
		return pages.stream().filter(DashboardPage::isVisible).toList();
	}

	public Optional<DashboardPage> byId(String id) {
		return pages.stream().filter(page -> page.id().equals(id)).findFirst();
	}
}
