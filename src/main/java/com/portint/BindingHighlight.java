package com.portint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders a highlight on the bound block when holding a binding card.
 * Mimics Entangled mod's approach: translucent filled faces + wireframe edges,
 * both fitted to the bound block's actual occlusion shape (not an inflated cube).
 * All rendering uses disableDepthTest for X-ray visibility.
 */
@EventBusSubscriber(modid = PortableInterface.MOD_ID, value = Dist.CLIENT)
public class BindingHighlight {

    private static final float MAX_DISTANCE = 48f;

    // AE2-style cyan-blue color
    private static final float R = 0.2f;
    private static final float G = 0.8f;
    private static final float B = 1.0f;
    private static final float WIRE_ALPHA = 1.0f;
    private static final float FACE_ALPHA = 0.12f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) held = mc.player.getOffhandItem();
        if (held.isEmpty()) return;

        var opt = held.get(ModDataComponents.BOUND_TARGET.get());
        if (opt == null || opt.isEmpty()) return;
        BoundTarget bt = opt.get();

        if (!mc.player.level().dimension().equals(bt.dimension())) return;

        BlockPos pos = bt.pos();
        if (Math.sqrt(pos.distSqr(mc.player.blockPosition())) > MAX_DISTANCE) return;

        // Get the bound block's actual occlusion shape for tight surface fit
        BlockState boundState = mc.player.level().getBlockState(pos);
        VoxelShape shape = boundState.getOcclusionShape(mc.player.level(), pos);

        Vec3 cam = event.getCamera().getPosition();
        var ps = event.getPoseStack();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        ps.translate(pos.getX(), pos.getY(), pos.getZ());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        renderFilledFaces(ps, shape);
        renderWireframe(ps, shape);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        ps.popPose();
    }

    /** Translucent filled faces (QUADS), depth test already disabled by caller. */
    private static void renderFilledFaces(PoseStack ps, VoxelShape shape) {
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buf.getBuffer(RenderType.debugQuads());
        Matrix4f mat = ps.last().pose();

        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            quad(quads, mat, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2,  0,-1, 0); // bottom
            quad(quads, mat, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1,  0, 1, 0); // top
            quad(quads, mat, x1, y2, z1, x1, y1, z1, x2, y1, z1, x2, y2, z1,  0, 0,-1); // north
            quad(quads, mat, x2, y2, z2, x2, y1, z2, x1, y1, z2, x1, y2, z2,  0, 0, 1); // south
            quad(quads, mat, x1, y2, z2, x1, y1, z2, x1, y1, z1, x1, y2, z1, -1, 0, 0); // west
            quad(quads, mat, x2, y2, z1, x2, y1, z1, x2, y1, z2, x2, y2, z2,  1, 0, 0); // east
        });

        buf.endBatch(RenderType.debugQuads());
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             float nx, float ny, float nz) {
        vc.addVertex(mat, (float)x1, (float)y1, (float)z1).setColor(R, G, B, FACE_ALPHA).setNormal(nx, ny, nz);
        vc.addVertex(mat, (float)x2, (float)y2, (float)z2).setColor(R, G, B, FACE_ALPHA).setNormal(nx, ny, nz);
        vc.addVertex(mat, (float)x3, (float)y3, (float)z3).setColor(R, G, B, FACE_ALPHA).setNormal(nx, ny, nz);
        vc.addVertex(mat, (float)x4, (float)y4, (float)z4).setColor(R, G, B, FACE_ALPHA).setNormal(nx, ny, nz);
    }

    /** Wireframe edges (LINES) from the actual shape edges, depth test already disabled by caller. */
    private static void renderWireframe(PoseStack ps, VoxelShape shape) {
        var buf = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.LINES);
        Matrix4f mat = ps.last().pose();

        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float nx = (float)(x2 - x1), ny = (float)(y2 - y1), nz = (float)(z2 - z1);
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0) { nx /= len; ny /= len; nz /= len; }
            lines.addVertex(mat, (float)x1, (float)y1, (float)z1).setColor(R, G, B, WIRE_ALPHA).setNormal(nx, ny, nz);
            lines.addVertex(mat, (float)x2, (float)y2, (float)z2).setColor(R, G, B, WIRE_ALPHA).setNormal(nx, ny, nz);
        });

        buf.endBatch(RenderType.LINES);
    }
}
