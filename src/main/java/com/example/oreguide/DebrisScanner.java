package com.example.oreguide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Finds every ancient debris block in the chunks the client currently has loaded. */
public final class DebrisScanner {
	private static final int MAX_RESULTS = 400;

	private DebrisScanner() {}

	public static List<BlockPos> scan(ClientLevel level, BlockPos center, int chunkRadius) {
		List<BlockPos> found = new ArrayList<>();
		int ccx = center.getX() >> 4;
		int ccz = center.getZ() >> 4;
		int minY = level.getMinY();

		for (int cx = ccx - chunkRadius; cx <= ccx + chunkRadius; cx++) {
			for (int cz = ccz - chunkRadius; cz <= ccz + chunkRadius; cz++) {
				ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) continue;

				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					// Palette check lets us skip almost every section cheaply
					if (section == null || section.hasOnlyAir()
							|| !section.maybeHas(state -> state.is(Blocks.ANCIENT_DEBRIS))) {
						continue;
					}
					int baseY = minY + (i << 4);
					for (int x = 0; x < 16; x++) {
						for (int y = 0; y < 16; y++) {
							for (int z = 0; z < 16; z++) {
								if (section.getBlockState(x, y, z).is(Blocks.ANCIENT_DEBRIS)) {
									found.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
								}
							}
						}
					}
				}
			}
		}

		found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
		return found.size() > MAX_RESULTS ? new ArrayList<>(found.subList(0, MAX_RESULTS)) : found;
	}
}
