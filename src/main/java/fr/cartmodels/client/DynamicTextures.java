package fr.cartmodels.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DynamicTextures {
    private static final Logger LOGGER = LoggerFactory.getLogger("cartmodels");
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    private DynamicTextures() {
    }

    public static ResourceLocation register(String modelName, String textureId, String encoded) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(encoded);
            String textureKey = UUID.nameUUIDFromBytes(imageBytes).toString();
            return TEXTURES.computeIfAbsent(textureKey, ignored -> load(textureKey, imageBytes));
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Could not decode imported texture '{}' for model '{}'", textureId, modelName, exception);
            return ResourceLocation.withDefaultNamespace("block/white_wool");
        }
    }

    public static ResourceLocation registerPreview(BufferedImage preview) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(preview, "png", output);
        NativeImage image = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("cartmodels",
                "previews/" + UUID.randomUUID());
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return location;
    }

    public static void releasePreview(ResourceLocation location) {
        if (location != null && Minecraft.getInstance() != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
    }

    private static ResourceLocation load(String textureKey, byte[] imageBytes) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("cartmodels",
                    "imports/" + textureKey);
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
            return location;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not decode an imported model texture", exception);
            return ResourceLocation.withDefaultNamespace("block/white_wool");
        }
    }
}
