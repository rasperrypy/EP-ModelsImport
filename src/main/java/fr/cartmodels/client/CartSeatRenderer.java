package fr.cartmodels.client;

import fr.cartmodels.entity.CartSeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public final class CartSeatRenderer extends EntityRenderer<CartSeatEntity> {
    public CartSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CartSeatEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    }

    @Override
    public void render(CartSeatEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
    }
}
