package io.github.nordavid.minekit.core.screen;

import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.input.KeyConflicts;
import io.github.nordavid.minekit.core.module.ModuleManager.ModuleStatus;
import io.github.nordavid.minekit.core.module.ModuleState;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Dashboard page with version, module states and shortcuts. */
public final class OverviewPage implements DashboardPage {
	public static final String ID = "overview";

	private final MinekitRuntime runtime;

	public OverviewPage(MinekitRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.overview");
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		MinekitRuntime.Versions versions = runtime.versions();
		content.text(Component.translatable("minekit.overview.version", versions.mod(), versions.minecraft()), MinekitTheme.TEXT_MUTED);
		content.text(Component.translatable("minekit.overview.client_only"), MinekitTheme.TEXT_MUTED);

		content.heading(Component.translatable("minekit.overview.modules"));
		for (ModuleStatus module : runtime.modules().modules()) {
			content.status(Component.translatable(module.metadata().nameKey()), stateLabel(module.state()), stateColor(module.state()));
			content.text(Component.translatable(module.metadata().descriptionKey()), MinekitTheme.TEXT_MUTED);
			module.failure().ifPresent(failure ->
					content.text(Component.translatable("minekit.module.failed", failure), MinekitTheme.ERROR));
		}

		content.heading(Component.translatable("minekit.overview.keys"));
		for (KeyMapping mapping : runtime.keybinds().mappings()) {
			content.status(Component.translatable(mapping.getName()), mapping.getTranslatedKeyMessage(), MinekitTheme.TEXT);
		}
		List<KeyConflicts.Conflict> conflicts = runtime.keybinds().conflicts(Minecraft.getInstance().options);
		if (conflicts.isEmpty()) {
			content.text(Component.translatable("minekit.overview.no_conflicts"), MinekitTheme.TEXT_MUTED);
		}
		for (KeyConflicts.Conflict conflict : conflicts) {
			KeyMapping own = KeyMapping.get(conflict.ownMapping());
			Component key = own != null ? own.getTranslatedKeyMessage() : Component.literal(conflict.key());
			content.text(Component.translatable("minekit.overview.key_conflict", Component.translatable(conflict.ownMapping()), key,
					Component.translatable(conflict.otherMapping())), MinekitTheme.WARNING);
		}

		if (!runtime.config().isSavingAllowed()) {
			content.spacer(6);
			content.text(Component.translatable("minekit.overview.config_read_only"), MinekitTheme.WARNING);
		}
	}

	private static Component stateLabel(ModuleState state) {
		return Component.translatable("minekit.module.state." + state.name().toLowerCase(Locale.ROOT));
	}

	private static int stateColor(ModuleState state) {
		return switch (state) {
			case ENABLED -> MinekitTheme.SUCCESS;
			case DISABLED, REGISTERED -> MinekitTheme.TEXT_MUTED;
			case FAILED -> MinekitTheme.ERROR;
		};
	}
}
