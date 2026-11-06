package io.github.nordavid.minekit.feature.storage;

import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Dashboard page of the Storage Finder: what is known in this world, open the finder, forget everything. */
final class StorageDashboardPage implements DashboardPage {
	static final String ID = "storage";

	private final StorageFinderModule module;
	/** Forgetting everything needs a second click. */
	private boolean confirmingForgetAll;

	StorageDashboardPage(StorageFinderModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.storage");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		content.text(Component.translatable("minekit.storage.page_intro"), MinekitTheme.TEXT_MUTED);
		Optional<StorageIndex> index = module.index();
		if (index.isEmpty()) {
			content.text(Component.translatable("minekit.storage.unavailable.no_world"), MinekitTheme.TEXT_MUTED);
			confirmingForgetAll = false;
			return;
		}
		StorageIndex current = index.get();
		content.status(Component.translatable("minekit.storage.world"), Component.literal(current.label()), MinekitTheme.TEXT);
		content.status(Component.translatable("minekit.storage.known_containers"), Component.literal(String.valueOf(current.containers().size())),
				MinekitTheme.TEXT);
		content.status(Component.translatable("minekit.storage.known_items"), Component.literal(String.valueOf(current.knownItems().size())),
				MinekitTheme.TEXT);
		FlatButton open = builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.storage.open"), button -> module.openFinder()));
		open.active = module.unavailableReason().isEmpty();
		FlatButton forgetAll = builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable(confirmingForgetAll ? "minekit.storage.forget_all_confirm" : "minekit.storage.forget_all"), button -> {
					if (confirmingForgetAll) {
						module.forgetAll();
						confirmingForgetAll = false;
					} else {
						confirmingForgetAll = true;
					}
					builder.rebuildLater();
				}));
		forgetAll.active = !current.containers().isEmpty();
	}
}
