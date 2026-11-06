package io.github.nordavid.minekit.feature.crafting;

import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/** Dashboard page of the Crafting Assistant: open the planner and manage saved plans. */
final class CraftingDashboardPage implements DashboardPage {
	static final String ID = "crafting";
	private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

	private final CraftingAssistantModule module;

	CraftingDashboardPage(CraftingAssistantModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.crafting");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		content.text(Component.translatable("minekit.crafting.how_to"), MinekitTheme.TEXT_MUTED);
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.crafting.open"),
				button -> module.openPlanner(null)));

		content.heading(Component.translatable("minekit.crafting.saved_plans"));
		SavedPlans plans = module.savedPlans().current();
		if (plans.plans().isEmpty()) {
			content.text(Component.translatable("minekit.crafting.no_saved_plans"), MinekitTheme.TEXT_MUTED);
		}
		for (SavedPlans.SavedPlan plan : plans.plans()) {
			String target = plan.request().target();
			FlatButton open = new FlatButton(0, 0, 54, MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.crafting.open_plan"),
					button -> module.openPlanner(plan.request()));
			FlatButton delete = new FlatButton(0, 0, 54, MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.inventory.remove"),
					button -> {
						module.deletePlan(target);
						builder.rebuildLater();
					});
			builder.line(ItemIds.icon(target), Component.literal(plan.request().quantity() + "× ").append(ItemIds.name(target)),
					Component.literal(SAVED_AT.format(Instant.ofEpochMilli(plan.savedAt()))), MinekitTheme.TEXT_MUTED, open, delete);
		}
	}
}
