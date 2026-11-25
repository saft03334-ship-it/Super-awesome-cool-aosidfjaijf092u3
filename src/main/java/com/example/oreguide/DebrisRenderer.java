package com.example.oreguide;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Draws filled boxes through walls. The pipeline/draw code follows the Fabric docs'
 * "Rendering in the World" page for 1.21.11.
 */
public final class DebrisRenderer {
	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(OreGuideClient.MOD_ID, "pipeline/through_walls_filled"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build());

	private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static BufferBuilder buffer;
	private static MappableRingBuffer vertexBuffer;

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private DebrisRenderer() {}

	public static void init() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(DebrisRenderer::onRender);
	}

	private static void onRender(WorldRenderContext context) {
		if (!OreGuideClient.enabled) return;

		List<BlockPos> debris = OreGuideClient.debris;
		PathFinder.Route route = OreGuideClient.currentRoute();
		if (debris.isEmpty() && route == null) return;

		PoseStack matrices = context.matrices();
		Vec3 camera = context.worldState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		Matrix4fc m = matrices.last().pose();

		buffer = new BufferBuilder(allocator, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());

		// every known debris block: orange
		for (BlockPos pos : debris) {
			if (route != null && pos.equals(route.goal())) continue;
			box(m, pos.getX() - 0.02f, pos.getY() - 0.02f, pos.getZ() - 0.02f,
					pos.getX() + 1.02f, pos.getY() + 1.02f, pos.getZ() + 1.02f, 1f, 0.5f, 0f, 0.45f);
		}

		if (route != null) {
			List<BlockPos> cells = route.cells();
			// trail: cyan line through the centres of the route cells
			float r = 0.1f;
			for (int i = 0; i < cells.size() - 1; i++) {
				BlockPos a = cells.get(i);
				BlockPos b = cells.get(i + 1);
				box(m,
						Math.min(a.getX(), b.getX()) + 0.5f - r, Math.min(a.getY(), b.getY()) + 0.5f - r, Math.min(a.getZ(), b.getZ()) + 0.5f - r,
						Math.max(a.getX(), b.getX()) + 0.5f + r, Math.max(a.getY(), b.getY()) + 0.5f + r, Math.max(a.getZ(), b.getZ()) + 0.5f + r,
						0.1f, 0.9f, 1f, 0.85f);
			}
			// selected target: green
			BlockPos g = route.goal();
			box(m, g.getX() - 0.05f, g.getY() - 0.05f, g.getZ() - 0.05f,
					g.getX() + 1.05f, g.getY() + 1.05f, g.getZ() + 1.05f, 0.1f, 1f, 0.2f, 0.6f);
		}

		matrices.popPose();
		drawThroughWalls(Minecraft.getInstance());
	}

	private static void box(Matrix4fc m, float x0, float y0, float z0, float x1, float y1, float z1,
			float r, float g, float b, float a) {
		BufferBuilder buf = buffer;
		// Front
		buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		// Back
		buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		// Left
		buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		// Right
		buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		// Top
		buf.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		buf.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		buf.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		// Bottom
		buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		buf.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		buf.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
	}

	private static void drawThroughWalls(Minecraft client) {
		RenderPipeline pipeline = THROUGH_WALLS;
		MeshData builtBuffer = buffer.buildOrThrow();
		MeshData.DrawState drawParameters = builtBuffer.drawState();
		VertexFormat format = drawParameters.format();

		GpuBuffer vertices = upload(drawParameters, format, builtBuffer);
		draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

		vertexBuffer.rotate();
		buffer = null;
	}

	private static GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
		int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

		if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = new MappableRingBuffer(() -> OreGuideClient.MOD_ID + " through walls buffer",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
		}

		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
				vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
		}
		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer,
			MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;

		if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			builtBuffer.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
			indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
			indexType = builtBuffer.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
			indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
			indexType = shapeIndexBuffer.type();
		}

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> OreGuideClient.MOD_ID + " through walls rendering",
						client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
						client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setVertexBuffer(0, vertices);
			renderPass.setIndexBuffer(indices, indexType);
			renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
		}
		builtBuffer.close();
	}

	public static void close() {
		allocator.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
