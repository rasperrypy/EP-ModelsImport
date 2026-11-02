package fr.cartmodels.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.cartmodels.block.ModelBlockEntity;
import fr.cartmodels.block.ModelBlock;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class ModelBlockEntityRenderer implements BlockEntityRenderer<ModelBlockEntity> {
    private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation.withDefaultNamespace("block/white_wool");

    public ModelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ModelBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        JsonObject model = ModelRenderCache.get(blockEntity.getModelName());
        if (model == null || !model.has("cubes")) {
            return;
        }
        JsonObject textures = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
        JsonArray cubes = model.getAsJsonArray("cubes");
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        float yaw = switch (blockEntity.getBlockState().getValue(ModelBlock.FACING)) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        for (JsonElement element : cubes) {
            if (element.isJsonObject()) {
                renderCube(blockEntity.getModelName(), element.getAsJsonObject(), textures,
                        poseStack, bufferSource, packedLight, packedOverlay);
            }
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ModelBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static void renderCube(String modelName, JsonObject cube, JsonObject textures, PoseStack poseStack,
                                   MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        double[] from = vector(cube.getAsJsonArray("from"), 3);
        double[] to = vector(cube.getAsJsonArray("to"), 3);
        if (from == null || to == null) {
            return;
        }
        poseStack.pushPose();
        if (cube.has("rotation")) {
            JsonObject rotation = cube.getAsJsonObject("rotation");
            double[] origin = vector(rotation.getAsJsonArray("origin"), 3);
            if (origin != null) {
                poseStack.translate(origin[0] / 16.0, origin[1] / 16.0, origin[2] / 16.0);
                if (rotation.has("euler")) {
                    double[] angles = vector(rotation.getAsJsonArray("euler"), 3);
                    if (angles != null) {
                        poseStack.mulPose(Axis.XP.rotationDegrees((float) angles[0]));
                        poseStack.mulPose(Axis.YP.rotationDegrees((float) angles[1]));
                        poseStack.mulPose(Axis.ZP.rotationDegrees((float) angles[2]));
                    }
                } else {
                    String axis = rotation.get("axis").getAsString();
                    float angle = rotation.get("angle").getAsFloat();
                    poseStack.mulPose(switch (axis) {
                        case "x" -> Axis.XP.rotationDegrees(angle);
                        case "z" -> Axis.ZP.rotationDegrees(angle);
                        default -> Axis.YP.rotationDegrees(angle);
                    });
                }
                poseStack.translate(-origin[0] / 16.0, -origin[1] / 16.0, -origin[2] / 16.0);
            }
        }
        double x0 = from[0] / 16.0;
        double y0 = from[1] / 16.0;
        double z0 = from[2] / 16.0;
        double x1 = to[0] / 16.0;
        double y1 = to[1] / 16.0;
        double z1 = to[2] / 16.0;
        JsonObject faces = cube.has("faces") ? cube.getAsJsonObject("faces") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                renderFace(modelName, entry.getKey(), entry.getValue().getAsJsonObject(), textures,
                        x0, y0, z0, x1, y1, z1, poseStack, bufferSource, packedLight, packedOverlay);
            }
        }
        poseStack.popPose();
    }

    private static void renderFace(String modelName, String direction, JsonObject face, JsonObject textures,
                                   double x0, double y0, double z0, double x1, double y1, double z1,
                                   PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                   int packedOverlay) {
        double[][] positions = switch (direction) {
            case "down" -> new double[][]{{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}};
            case "up" -> new double[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}};
            case "north" -> new double[][]{{x1,y1,z0},{x0,y1,z0},{x0,y0,z0},{x1,y0,z0}};
            case "south" -> new double[][]{{x0,y1,z1},{x1,y1,z1},{x1,y0,z1},{x0,y0,z1}};
            case "west" -> new double[][]{{x0,y1,z0},{x0,y1,z1},{x0,y0,z1},{x0,y0,z0}};
            case "east" -> new double[][]{{x1,y1,z1},{x1,y1,z0},{x1,y0,z0},{x1,y0,z1}};
            default -> null;
        };
        if (positions == null) {
            return;
        }
        double[][] normals = switch (direction) {
            case "down" -> new double[][]{{0,-1,0}};
            case "up" -> new double[][]{{0,1,0}};
            case "north" -> new double[][]{{0,0,-1}};
            case "south" -> new double[][]{{0,0,1}};
            case "west" -> new double[][]{{-1,0,0}};
            default -> new double[][]{{1,0,0}};
        };
        JsonArray uv = face.has("uv") ? face.getAsJsonArray("uv") : null;
        double[] uvRect = vector(uv, 4);
        if (uvRect == null) {
            uvRect = new double[]{0, 0, 16, 16};
        }
        String textureId = face.has("texture") ? face.get("texture").getAsString() : "";
        JsonObject textureData = textures.has(textureId) && textures.get(textureId).isJsonObject()
                ? textures.getAsJsonObject(textureId) : null;
        String encodedTexture = textureData != null && textureData.has("image")
                ? textureData.get("image").getAsString() : "";
        ResourceLocation texture = encodedTexture.isEmpty() ? FALLBACK_TEXTURE
                : ModelRenderCache.textureLocation(modelName, textureId);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f positionMatrix = pose.pose();
        double[][] uvPoints = {
                {uvRect[0], uvRect[1]},
                {uvRect[2], uvRect[1]},
                {uvRect[2], uvRect[3]},
                {uvRect[0], uvRect[3]}
        };
        int uvRotation = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt() / 90, 4) : 0;
        if (uvRotation != 0) {
            double[][] rotated = new double[4][2];
            for (int index = 0; index < 4; index++) {
                rotated[index] = uvPoints[(index + uvRotation) % 4];
            }
            uvPoints = rotated;
        }
        double uvWidth = face.has("pixelUv") && face.get("pixelUv").getAsBoolean() && textureData != null
                ? textureData.get("width").getAsDouble() : 16.0;
        double uvHeight = face.has("pixelUv") && face.get("pixelUv").getAsBoolean() && textureData != null
                ? textureData.get("height").getAsDouble() : 16.0;
        for (int index = 0; index < 4; index++) {
            consumer.addVertex(positionMatrix, (float) positions[index][0], (float) positions[index][1],
                            (float) positions[index][2])
                    .setColor(255, 255, 255, 255)
                    .setUv((float) (uvPoints[index][0] / uvWidth), (float) (uvPoints[index][1] / uvHeight))
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, (float) normals[0][0], (float) normals[0][1],
                            (float) normals[0][2]);
        }
    }

    private static double[] vector(JsonArray array, int expectedSize) {
        if (array == null || array.size() != expectedSize) {
            return null;
        }
        double[] vector = new double[expectedSize];
        for (int index = 0; index < expectedSize; index++) {
            if (!array.get(index).isJsonPrimitive() || !array.get(index).getAsJsonPrimitive().isNumber()) {
                return null;
            }
            vector[index] = array.get(index).getAsDouble();
        }
        return vector;
    }
}
