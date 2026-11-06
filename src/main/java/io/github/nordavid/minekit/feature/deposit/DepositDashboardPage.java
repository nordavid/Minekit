package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/** Dashboard page of Smart Deposit: profile, custom categories, hotbar protection. */
final class DepositDashboardPage implements DashboardPage {
	static final String ID = "deposit";

	private final SmartDepositModule module;

	DepositDashboardPage(SmartDepositModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.deposit");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		DepositSettings settings = module.settings().current();
		content.text(Component.translatable("minekit.deposit.how_to", module.depositKey().getTranslatedKeyMessage()), MinekitTheme.TEXT_MUTED);
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.deposit.profile_button", Component.translatable(settings.profile().translationKey())), button -> {
					module.cycleProfile();
					builder.rebuildLater();
				}));
		content.text(Component.translatable(settings.profile().translationKey() + ".description"), MinekitTheme.TEXT_MUTED);
		builder.widget(new ToggleButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.deposit.include_hotbar"),
				settings.includeHotbar(), requested -> {
					module.update(current -> current.withIncludeHotbar(requested));
					return module.settings().current().includeHotbar();
				}));
		content.text(Component.translatable("minekit.deposit.favorites_note"), MinekitTheme.TEXT_MUTED);

		content.heading(Component.translatable("minekit.deposit.custom_categories"));
		for (ItemCategory category : ItemCategory.values()) {
			builder.widget(new ToggleButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable(category.translationKey()),
					settings.customCategories().contains(category), requested -> {
						module.update(current -> current.customCategories().contains(category) == requested
								? current : current.withCategoryToggled(category));
						return module.settings().current().customCategories().contains(category);
					}));
		}
	}
}
