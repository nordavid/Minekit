package io.github.nordavid.minekit.core.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Knows which container block the open container screen belongs to.
 *
 * <p>Minecraft does not tell the client where a container screen comes from. Minekit therefore only trusts a screen
 * when three things agree:
 * <ol>
 *   <li>it opens shortly after the player right-clicked a supported container block,</li>
 *   <li>its menu is of the kind that block opens, with the expected number of slots, and</li>
 *   <li>the block visibly opens: the server animates a chest or ender chest lid and a shulker box, and sets a barrel
 *       to "open". A plugin that cancels the real opening and shows its own menu instead (a crate preview, a sell
 *       menu) fails this check. Hoppers, dispensers and droppers have no such signal and rely on the first two.</li>
 * </ol>
 * Screens a server opens on its own are therefore never treated as a container block: nothing is indexed for them and
 * Smart Deposit is not offered there. Until the block has visibly opened, a screen is only a candidate.
 *
 * <p>Always active; cheap. Client thread only.
 */
public final class ContainerTracker {
	/** How long after the right-click the screen may open: generous, for slow servers. */
	static final int OPEN_WINDOW_TICKS = 60;
	/** How long a candidate may wait for the block to visibly open. */
	static final int CONFIRM_WINDOW_TICKS = 40;

	private record Interaction(BlockPos pos, BlockState state, String dimension, long tick) {
	}

	private record Candidate(OpenedContainer container, BlockPos clicked, long since) {
	}

	private final List<Consumer<OpenedContainer>> confirmListeners = new ArrayList<>();
	private final List<Consumer<OpenedContainer>> closeListeners = new ArrayList<>();
	private @Nullable Interaction pending;
	private @Nullable Candidate candidate;
	private @Nullable OpenedContainer current;
	private long ticks;

	public void install() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// With an item in hand, sneaking uses the item instead of opening the block.
			boolean opensBlock = !player.isSecondaryUseActive() || player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
			if (level.isClientSide() && opensBlock) {
				BlockState state = level.getBlockState(hit.getBlockPos());
				if (kindOf(state.getBlock()) != null) {
					pending = new Interaction(hit.getBlockPos().immutable(), state, level.dimension().identifier().toString(), ticks);
				}
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> onScreenInit(client, screen));
	}

	/** The container the player has open right now, if Minekit identified and confirmed it. */
	public Optional<OpenedContainer> current() {
		return Optional.ofNullable(current);
	}

	/** The container behind a given screen, if it is the one Minekit identified and confirmed. */
	public Optional<OpenedContainer> of(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> containerScreen && current != null && current.menu() == containerScreen.getMenu()
				? Optional.of(current)
				: Optional.empty();
	}

	/** Called on the client thread when the open container is confirmed, usually a tick or two after its screen opened. */
	public void onConfirmed(Consumer<OpenedContainer> listener) {
		confirmListeners.add(listener);
	}

	/** Called on the client thread when a confirmed container is closed, with its last state still in the menu. */
	public void onClosed(Consumer<OpenedContainer> listener) {
		closeListeners.add(listener);
	}

	private void tick(Minecraft client) {
		ticks++;
		Candidate waiting = candidate;
		if (waiting != null) {
			AbstractContainerMenu menu = waiting.container().menu();
			if (client.player == null || client.level == null || client.player.containerMenu != menu
					|| ticks - waiting.since() > CONFIRM_WINDOW_TICKS) {
				candidate = null;
			} else if (isVisiblyOpen(client.level, waiting.container().kind(), waiting.clicked())) {
				candidate = null;
				current = waiting.container();
				confirmListeners.forEach(listener -> listener.accept(waiting.container()));
			}
		}
		if (current != null && (client.player == null || client.player.containerMenu != current.menu())) {
			close();
		}
	}

	private void onScreenInit(Minecraft client, Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}
		AbstractContainerMenu menu = containerScreen.getMenu();
		if (current != null && current.menu() == menu || candidate != null && candidate.container().menu() == menu) {
			watchRemoval(screen, menu); // re-initialized (resize): Fabric has reset the screen's events
			return;
		}
		if (current != null) {
			close();
		}
		candidate = null;
		Interaction interaction = pending;
		pending = null;
		if (interaction == null || ticks - interaction.tick() > OPEN_WINDOW_TICKS || client.level == null
				|| !interaction.dimension().equals(client.level.dimension().identifier().toString())) {
			return;
		}
		OpenedContainer identified = identify(interaction, menu);
		if (identified != null) {
			candidate = new Candidate(identified, interaction.pos(), ticks);
			watchRemoval(screen, menu);
		}
	}

	private void watchRemoval(Screen screen, AbstractContainerMenu menu) {
		ScreenEvents.remove(screen).register(removed -> {
			// Only a real close counts; the end-of-tick check catches every other way the menu goes away.
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || minecraft.player.containerMenu != menu) {
				if (candidate != null && candidate.container().menu() == menu) {
					candidate = null;
				}
				if (current != null && current.menu() == menu) {
					close();
				}
			}
		});
	}

	private void close() {
		OpenedContainer closed = current;
		current = null;
		if (closed != null) {
			closeListeners.forEach(listener -> listener.accept(closed));
		}
	}

	/** Whether the client can see that the block itself opened, which a plugin's substitute menu does not cause. */
	private static boolean isVisiblyOpen(Level level, OpenedContainer.Kind kind, BlockPos pos) {
		return switch (kind) {
			case CHEST, ENDER_CHEST -> level.getBlockEntity(pos) instanceof LidBlockEntity lid && lid.getOpenNess(1.0F) > 0;
			case SHULKER_BOX -> level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box
					&& (box.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.OPENING
							|| box.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.OPENED);
			case BARREL -> level.getBlockState(pos).getOptionalValue(BarrelBlock.OPEN).orElse(false);
			case HOPPER, DISPENSER -> true; // no visible signal exists
		};
	}

	private static @Nullable OpenedContainer identify(Interaction interaction, AbstractContainerMenu menu) {
		Block block = interaction.state().getBlock();
		OpenedContainer.Kind kind = kindOf(block);
		if (kind == null) {
			return null;
		}
		String blockId = ContainerTypes.normalize(BuiltInRegistries.BLOCK.getKey(block).toString());
		BlockPos pos = interaction.pos();
		return switch (kind) {
			case CHEST -> {
				if (!(menu instanceof ChestMenu chest)) {
					yield null;
				}
				if (chest.getRowCount() == 3) {
					yield new OpenedContainer(menu, kind, blockId, interaction.dimension(), List.of(pos));
				}
				if (chest.getRowCount() == 6 && interaction.state().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
					BlockPos other = ChestBlock.getConnectedBlockPos(pos, interaction.state());
					yield new OpenedContainer(menu, kind, blockId, interaction.dimension(), List.of(pos, other));
				}
				yield null;
			}
			case BARREL -> menu instanceof ChestMenu chest && chest.getRowCount() == 3
					? new OpenedContainer(menu, kind, blockId, interaction.dimension(), List.of(pos)) : null;
			case ENDER_CHEST -> menu instanceof ChestMenu chest && chest.getRowCount() == 3
					? new OpenedContainer(menu, kind, blockId, "", List.of()) : null;
			case SHULKER_BOX -> menu instanceof ShulkerBoxMenu
					? new OpenedContainer(menu, kind, blockId, interaction.dimension(), List.of(pos)) : null;
			case HOPPER -> menu instanceof HopperMenu
					? new OpenedContainer(menu, kind, blockId, interaction.dimension(), List.of(pos)) : null;
			case DISPENSER -> menu instanceof DispenserMenu
					? new OpenedContainer(menu, kind, blockId, interaction.dimension(), List.of(pos)) : null;
		};
	}

	/** The kind of container a block opens, or {@code null} if Minekit does not support it. */
	public static OpenedContainer.@Nullable Kind kindOf(Block block) {
		if (block instanceof ChestBlock) {
			return OpenedContainer.Kind.CHEST; // includes trapped and copper chests
		}
		if (block instanceof BarrelBlock) {
			return OpenedContainer.Kind.BARREL;
		}
		if (block instanceof EnderChestBlock) {
			return OpenedContainer.Kind.ENDER_CHEST;
		}
		if (block instanceof ShulkerBoxBlock) {
			return OpenedContainer.Kind.SHULKER_BOX;
		}
		if (block instanceof HopperBlock) {
			return OpenedContainer.Kind.HOPPER;
		}
		if (block instanceof DispenserBlock) {
			return OpenedContainer.Kind.DISPENSER; // includes droppers
		}
		return null;
	}
}
