package com.example.oreguide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Dijkstra over a voxel box around the player. Walking through air is cheap, mining costs more
 * (scaled by block hardness), lava and unbreakable blocks are forbidden, and digging next to
 * lava is heavily penalised. The search stops once it has popped the K cheapest debris blocks,
 * so the first route returned is the fastest one overall.
 */
public final class PathFinder {
	public record Route(BlockPos goal, List<BlockPos> cells, int blocksToMine, float cost) {}

	public static final int RADIUS = 32;
	private static final int N = RADIUS * 2 + 1;
	private static final int MAX_EXPANSIONS = 45_000;
	private static final int MAX_ROUTES = 5;
	private static final float MAX_HARDNESS = 10f; // skips obsidian and similar

	private static final int[] DX = {1, -1, 0, 0, 0, 0};
	private static final int[] DY = {0, 0, 1, -1, 0, 0};
	private static final int[] DZ = {0, 0, 0, 0, 1, -1};

	private PathFinder() {}

	private static int idx(int x, int y, int z) {
		return (x * N + y) * N + z;
	}

	private static long pack(float cost, int idx) {
		return ((long) Float.floatToIntBits(cost) << 32) | (idx & 0xFFFFFFFFL);
	}

	public static List<Route> search(ClientLevel level, BlockPos start) {
		int total = N * N * N;
		float[] dist = new float[total];
		int[] parent = new int[total];
		float[] cellCost = new float[total];
		boolean[] done = new boolean[total];
		Arrays.fill(dist, Float.MAX_VALUE);
		Arrays.fill(parent, -1);
		Arrays.fill(cellCost, Float.NaN);

		int ox = start.getX() - RADIUS;
		int oy = start.getY() - RADIUS;
		int oz = start.getZ() - RADIUS;

		PriorityQueue<Long> queue = new PriorityQueue<>();
		int startIdx = idx(RADIUS, RADIUS, RADIUS);
		dist[startIdx] = 0f;
		queue.add(pack(0f, startIdx));

		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		List<Route> routes = new ArrayList<>();
		int expansions = 0;

		while (!queue.isEmpty() && expansions < MAX_EXPANSIONS && routes.size() < MAX_ROUTES) {
			long v = queue.poll();
			int cur = (int) (v & 0xFFFFFFFFL);
			if (done[cur]) continue;
			done[cur] = true;
			expansions++;

			int cx = cur / (N * N);
			int cy = (cur / N) % N;
			int cz = cur % N;
			float d = dist[cur];
			p.set(ox + cx, oy + cy, oz + cz);

			if (cur != startIdx && level.getBlockState(p).is(Blocks.ANCIENT_DEBRIS)) {
				routes.add(buildRoute(level, parent, cur, d, ox, oy, oz));
				continue; // don't tunnel through debris
			}

			for (int k = 0; k < 6; k++) {
				int nx = cx + DX[k], ny = cy + DY[k], nz = cz + DZ[k];
				if (nx < 0 || ny < 0 || nz < 0 || nx >= N || ny >= N || nz >= N) continue;
				int n = idx(nx, ny, nz);
				if (done[n]) continue;

				float c = cellCost[n];
				if (Float.isNaN(c)) {
					q.set(ox + nx, oy + ny, oz + nz);
					c = computeCost(level, q);
					cellCost[n] = c;
				}
				if (c < 0) continue;

				float nd = d + c;
				if (nd < dist[n]) {
					dist[n] = nd;
					parent[n] = cur;
					queue.add(pack(nd, n));
				}
			}
		}
		return routes;
	}

	/** Cost of entering a cell, or -1 if it can't be entered. */
	private static float computeCost(ClientLevel level, BlockPos.MutableBlockPos pos) {
		if (!level.hasChunkAt(pos)) return -1f;
		if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) return -1f;

		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.ANCIENT_DEBRIS)) return 2f; // goal cell, same cost for every debris

		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			return fluid.is(FluidTags.LAVA) ? -1f : 2f;
		}

		if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
			return 1f; // walkable / open space
		}

		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0f || hardness > MAX_HARDNESS) return -1f;

		float cost = 2f + hardness * 3f;
		if (state.getBlock() instanceof FallingBlock) cost += 4f;

		// Breaking a block that touches lava lets it flow into the tunnel
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (int k = 0; k < 6; k++) {
			n.set(pos.getX() + DX[k], pos.getY() + DY[k], pos.getZ() + DZ[k]);
			if (level.getFluidState(n).is(FluidTags.LAVA)) {
				cost += 25f;
				break;
			}
		}
		return cost;
	}

	private static Route buildRoute(ClientLevel level, int[] parent, int goalIdx, float cost, int ox, int oy, int oz) {
		List<BlockPos> cells = new ArrayList<>();
		for (int i = goalIdx; i != -1; i = parent[i]) {
			cells.add(new BlockPos(ox + i / (N * N), oy + (i / N) % N, oz + i % N));
		}
		Collections.reverse(cells);

		int toMine = 0;
		for (int i = 1; i < cells.size(); i++) {
			BlockPos c = cells.get(i);
			BlockState s = level.getBlockState(c);
			if (s.getFluidState().isEmpty() && !s.getCollisionShape(level, c).isEmpty()) toMine++;
		}
		return new Route(cells.get(cells.size() - 1), cells, toMine, cost);
	}
}
