package com.example.oreguide;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

public class OreGuideClient implements ClientModInitializer {
	public static final String MOD_ID = "oreguide";

	/** Chunks around the player to scan for debris (limited by your render distance anyway). */
	private static final int SCAN_CHUNK_RADIUS = 10;

	public static volatile boolean enabled = true;
	public static volatile List<BlockPos> debris = List.of();
	public static volatile List<PathFinder.Route> routes = List.of();
	private static int selected = 0;

	private static KeyMapping toggleKey;
	private static KeyMapping cycleKey;

	private static int tickCount = 0;
	private static int lastSearchTick = -1000;
	private static BlockPos lastSearchPos = null;

	public static PathFinder.Route currentRoute() {
		List<PathFinder.Route> r = routes;
		if (r.isEmpty()) return null;
		return r.get(Math.floorMod(selected, r.size()));
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.oreguide.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));
		cycleKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.oreguide.cycle", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));

		DebrisRenderer.init();
		ClientTickEvents.END_CLIENT_TICK.register(OreGuideClient::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DebrisRenderer.close());
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;

		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			if (player != null) {
				player.displayClientMessage(Component.literal("Debris Guide: " + (enabled ? "ON" : "OFF")), true);
			}
			lastSearchTick = -1000; // force a refresh when turned back on
		}
		while (cycleKey.consumeClick()) {
			if (!routes.isEmpty()) selected = Math.floorMod(selected + 1, routes.size());
		}

		if (!enabled || player == null || level == null) return;
		tickCount++;
		BlockPos pos = player.blockPosition();

		if (tickCount % 20 == 1) {
			debris = DebrisScanner.scan(level, pos, SCAN_CHUNK_RADIUS);
		}

		PathFinder.Route cur = currentRoute();
		boolean goalGone = cur != null && !level.getBlockState(cur.goal()).is(Blocks.ANCIENT_DEBRIS);
		int since = tickCount - lastSearchTick;
		boolean moved = lastSearchPos == null || lastSearchPos.distSqr(pos) >= 9;
		boolean due = goalGone || since >= 100 || (since >= 20 && moved);

		if (due) {
			lastSearchTick = tickCount;
			lastSearchPos = pos;
			BlockPos previousGoal = cur == null ? null : cur.goal();

			boolean anyInRange = false;
			for (BlockPos d : debris) {
				if (Math.abs(d.getX() - pos.getX()) <= PathFinder.RADIUS
						&& Math.abs(d.getY() - pos.getY()) <= PathFinder.RADIUS
						&& Math.abs(d.getZ() - pos.getZ()) <= PathFinder.RADIUS) {
					anyInRange = true;
					break;
				}
			}
			List<PathFinder.Route> found = anyInRange ? PathFinder.search(level, pos) : List.of();

			selected = 0;
			if (previousGoal != null && !goalGone) {
				for (int i = 0; i < found.size(); i++) {
					if (found.get(i).goal().equals(previousGoal)) {
						selected = i;
						break;
					}
				}
			}
			routes = found;
		}

		if (tickCount % 20 == 0) {
			PathFinder.Route r = currentRoute();
			if (r != null) {
				BlockPos g = r.goal();
				player.displayClientMessage(Component.literal(String.format(
						"Debris at %d %d %d | %d blocks to mine | %d steps  [%d/%d, H = next]",
						g.getX(), g.getY(), g.getZ(), r.blocksToMine(), r.cells().size() - 1,
						Math.floorMod(selected, routes.size()) + 1, routes.size())), true);
			} else if (!debris.isEmpty()) {
				BlockPos n = debris.get(0);
				player.displayClientMessage(Component.literal(String.format(
						"Nearest debris %d %d %d (no route within %d blocks)",
						n.getX(), n.getY(), n.getZ(), PathFinder.RADIUS)), true);
			}
		}
	}
}
