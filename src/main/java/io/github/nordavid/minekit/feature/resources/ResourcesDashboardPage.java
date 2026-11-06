package io.github.nordavid.minekit.feature.resources;

import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Dashboard page of the Resource Tracker: session controls, goals, counts and history. */
final class ResourcesDashboardPage implements DashboardPage {
	static final String ID = "resources";
	private static final int MAX_ITEMS_SHOWN = 10;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

	private final ResourceTrackerModule module;

	ResourcesDashboardPage(ResourceTrackerModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.resources");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		ResourceSession session = module.tracker().current();
		if (session == null) {
			content.text(Component.translatable("minekit.resources.no_session"), MinekitTheme.TEXT_MUTED);
			builder.widget(button(content.width(), "minekit.resources.start", () -> {
				module.toggleSession();
				builder.rebuildLater();
			}));
		} else {
			buildCurrent(builder, content, session);
		}
		content.spacer(4);
		content.text(Component.translatable("minekit.resources.rules"), MinekitTheme.TEXT_MUTED);
		buildHistory(builder, content);
	}

	private void buildCurrent(Builder builder, ContentPane content, ResourceSession session) {
		EditBox name = nameBox(content.width(), session.name(), value -> module.change(tracker -> tracker.renamed(session.id(), value)));
		builder.widget(name);
		long now = System.currentTimeMillis();
		Component state = Component.translatable(session.isRunning() ? "minekit.resources.running" : "minekit.resources.paused_state",
				Durations.format(session.durationMillis(now)));
		FlatButton pauseOrResume = session.isRunning()
				? button(62, "minekit.resources.pause", () -> {
					module.pause();
					builder.rebuildLater();
				})
				: button(62, "minekit.resources.resume", () -> {
					module.resume();
					builder.rebuildLater();
				});
		FlatButton stop = button(52, "minekit.resources.stop", () -> {
			module.stop();
			builder.rebuildLater();
		});
		builder.line(null, state, worldLabel(session), MinekitTheme.TEXT_MUTED, pauseOrResume, stop);

		content.heading(Component.translatable("minekit.resources.goals"));
		if (session.goals().isEmpty()) {
			content.text(Component.translatable("minekit.resources.no_goals"), MinekitTheme.TEXT_MUTED);
		}
		for (Map.Entry<String, Integer> goal : session.goals().entrySet()) {
			String item = goal.getKey();
			int target = goal.getValue();
			long have = session.collected(item);
			FlatButton less = small("−", "minekit.resources.goal_less", () -> setGoal(builder, session, item, target - 64));
			FlatButton more = small("+", "minekit.resources.goal_more", () -> setGoal(builder, session, item, target + 64));
			FlatButton remove = small("✕", "minekit.resources.goal_remove", () -> setGoal(builder, session, item, 0));
			builder.line(ItemIds.icon(item), ItemIds.name(item), Component.literal(Math.min(have, target) + " / " + target),
					have >= target ? MinekitTheme.SUCCESS : MinekitTheme.TEXT, less, more, remove);
		}
		ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
		FlatButton fromHand = button(content.width(), "minekit.resources.goal_from_hand", () -> {
			String item = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
			setGoal(builder, session, item, Math.max(64, session.goals().getOrDefault(item, 0) + 64));
		});
		fromHand.active = !held.isEmpty();
		builder.widget(fromHand);

		listCounts(content, "minekit.resources.collected", session.collected(), "+", MinekitTheme.SUCCESS);
		listCounts(content, "minekit.resources.consumed", session.consumed(), "−", MinekitTheme.WARNING);
		if (!session.unclearIn().isEmpty() || !session.unclearOut().isEmpty()) {
			content.heading(Component.translatable("minekit.resources.unclear"));
			content.text(Component.translatable("minekit.resources.unclear_explained"), MinekitTheme.TEXT_MUTED);
			session.unclearIn().forEach((item, count) -> content.line(ItemIds.icon(item), ItemIds.name(item),
					Component.literal("+" + count), MinekitTheme.TEXT_MUTED));
			session.unclearOut().forEach((item, count) -> content.line(ItemIds.icon(item), ItemIds.name(item),
					Component.literal("−" + count), MinekitTheme.TEXT_MUTED));
		}
	}

	private void setGoal(Builder builder, ResourceSession session, String item, int target) {
		module.change(tracker -> tracker.currentSession().map(current -> tracker.withCurrent(current.withGoal(item, target))).orElse(tracker));
		builder.rebuildLater();
	}

	private void buildHistory(Builder builder, ContentPane content) {
		content.heading(Component.translatable("minekit.resources.history"));
		if (module.tracker().history().isEmpty()) {
			content.text(Component.translatable("minekit.resources.no_history"), MinekitTheme.TEXT_MUTED);
		}
		for (ResourceSession past : module.tracker().history()) {
			if (past.id().equals(module.editingId())) {
				builder.widget(nameBox(content.width(), past.name(), value -> module.change(tracker -> tracker.renamed(past.id(), value))));
			}
			FlatButton rename = small("✎", "minekit.resources.rename", () -> {
				module.setEditingId(past.id().equals(module.editingId()) ? null : past.id());
				builder.rebuildLater();
			});
			FlatButton delete = small("✕", "minekit.resources.delete", () -> {
				module.change(tracker -> tracker.deleted(past.id()));
				builder.rebuildLater();
			});
			String top = past.collected().entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("minecraft:clock");
			Component status = Component.literal(WHEN.format(Instant.ofEpochMilli(past.startedAt())) + " · " + Durations.format(past.activeMillis()));
			builder.line(ItemIds.icon(top), Component.literal(past.name()), status, MinekitTheme.TEXT_MUTED, rename, delete);
		}
	}

	private static void listCounts(ContentPane content, String headingKey, Map<String, Long> counts, String sign, int color) {
		content.heading(Component.translatable(headingKey));
		if (counts.isEmpty()) {
			content.text(Component.translatable("minekit.resources.nothing_yet"), MinekitTheme.TEXT_MUTED);
			return;
		}
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
				.limit(MAX_ITEMS_SHOWN)
				.forEach(entry -> content.line(ItemIds.icon(entry.getKey()), ItemIds.name(entry.getKey()),
						Component.literal(sign + entry.getValue()), color));
		if (counts.size() > MAX_ITEMS_SHOWN) {
			content.text(Component.translatable("minekit.resources.more", counts.size() - MAX_ITEMS_SHOWN), MinekitTheme.TEXT_MUTED);
		}
	}

	private static Component worldLabel(ResourceSession session) {
		return session.world().isEmpty() ? Component.translatable("minekit.resources.singleplayer") : Component.literal(session.world());
	}

	private static EditBox nameBox(int width, String value, java.util.function.Consumer<String> onChange) {
		EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, width, ROW, Component.translatable("minekit.resources.name"));
		box.setMaxLength(48);
		box.setValue(value);
		box.setResponder(text -> {
			if (!text.isBlank()) {
				onChange.accept(text.strip());
			}
		});
		return box;
	}

	private static FlatButton button(int width, String key, Runnable action) {
		return new FlatButton(0, 0, width, ROW, Component.translatable(key), pressed -> action.run());
	}

	private static FlatButton small(String icon, String tooltipKey, Runnable action) {
		FlatButton button = new FlatButton(0, 0, 20, ROW, Component.literal(icon), pressed -> action.run());
		button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		return button;
	}
}
