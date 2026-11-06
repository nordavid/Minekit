package io.github.nordavid.minekit.feature.blueprint;

import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Dashboard page of the blueprint module: what it does, the active blueprint and its progress, and the measuring tool. */
final class BlueprintDashboardPage implements DashboardPage {
	static final String ID = "blueprints";

	private final BlueprintModule module;

	BlueprintDashboardPage(BlueprintModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.blueprints");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		content.text(Component.translatable("minekit.blueprint.how_to"), MinekitTheme.TEXT_MUTED);
		FlatButton open = builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.blueprint.open"), button -> module.openScreen()));
		open.active = module.unavailableReason().isEmpty();

		content.heading(Component.translatable("minekit.blueprint.active"));
		Optional<ResolvedBlueprint> active = module.active();
		if (active.isEmpty()) {
			content.text(Component.translatable("minekit.blueprint.none_active", module.files().size()), MinekitTheme.TEXT_MUTED);
		} else {
			Blueprint blueprint = active.get().blueprint();
			content.status(Component.literal(blueprint.name()), Component.translatable("minekit.blueprint.size", blueprint.sizeX(), blueprint.sizeY(),
					blueprint.sizeZ()), MinekitTheme.TEXT_MUTED);
			module.progress().ifPresentOrElse(progress -> content.status(Component.translatable("minekit.blueprint.built"),
					Component.translatable("minekit.blueprint.built_value", Math.round(progress.completion() * 100), progress.count(Status.CORRECT),
							progress.total()), MinekitTheme.TEXT),
					() -> content.text(Component.translatable("minekit.blueprint.progress_after_placing"), MinekitTheme.TEXT_MUTED));
		}
		content.heading(Component.translatable("minekit.blueprint.measure"));
		content.text(Component.translatable("minekit.measure.how_to"), MinekitTheme.TEXT_MUTED);
		content.heading(Component.translatable("minekit.blueprint.limits"));
		BlueprintSettings settings = module.settings().current();
		content.text(Component.translatable("minekit.blueprint.limits_text", settings.maxFileMegabytes(), settings.maxPositions()), MinekitTheme.TEXT_MUTED);
	}
}
