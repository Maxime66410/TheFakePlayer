package org.furranystudio.thefakeplayer.Entity.Renderer;


import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Thefakeplayer;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class FakePlayerRenderer extends MobRenderer<FakePlayerEntity, LivingEntityRenderState,FakePlayerModelWithAnim<FakePlayerEntity>> {

    private static ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "textures/entities/basefakeplayer.png");

    public FakePlayerRenderer(EntityRendererProvider.Context p_174169_) {
        super(p_174169_, new FakePlayerModelWithAnim<>(p_174169_.bakeLayer(FakePlayerModelWithAnim.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public @NotNull HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(LivingEntityRenderState p_362468_) {
        return TEXTURE;
    }

    public static void updateTexture(ResourceLocation localTexture) {
        TEXTURE = localTexture;
    }

    public static NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        if(bufferedImage != null)
        {
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = bufferedImage.getRGB(x, y);
                    int rgba = ((argb & 0xFF) << 24) | ((argb >> 8) & 0xFFFFFF);
                    nativeImage.setPixel(x, y, rgba);
                }
            }
            return nativeImage;
        }
        else {
            return null;
        }
    }

    public static void updateTextureFromFile(File skinFile) {
        try {
            // Charge l'image depuis le fichier local
            BufferedImage image = ImageIO.read(skinFile);

            NativeImage nativeImage = convertToNativeImage(image);

            if(nativeImage == null) {
                return;
            }

            // Crée une texture dynamique à partir de l'image
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);

            // Récupère le TextureManager de Minecraft
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();

            // Crée un ResourceLocation temporaire
            ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "skins/custom_skin");

            if (dynamicTexture.getPixels() == null) {
                throw new RuntimeException("NativeImage is not allocated.");
            }

            // Applique la texture au manager
            //textureManager.release(textureLocation);
            //textureManager.register(textureLocation, dynamicTexture);

            // Mets à jour la texture du renderer
            //updateTexture(textureLocation);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
