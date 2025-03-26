package org.furranystudio.thefakeplayer.Entity.Renderer;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Entity.ModEntities;
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
                    int a = (argb >> 24) & 0xFF;  // Alpha
                    int r = (argb >> 16) & 0xFF;  // Red
                    int g = (argb >> 8) & 0xFF;   // Green
                    int b = (argb) & 0xFF;        // Blue

                    // We create a real argb value without modifying the order of the channels
                    int correctedARGB = (a << 24) | (r << 16) | (g << 8) | b;

                    // this.setPixelABGR(p_364494_, p_368505_, ARGB.toABGR(p_361991_));
                    // WTF is ARGB.toABGR() ??
                    nativeImage.setPixel(x, y, correctedARGB);
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
            // load the image
            BufferedImage image = ImageIO.read(skinFile);

            NativeImage nativeImage = convertToNativeImage(image);

            if(nativeImage == null) {
                return;
            }

            // create a DynamicTexture from the NativeImage
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);

            // get the TextureManager
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();

            // create a ResourceLocation
            ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "textures/entities/skins/custom_skin.png");

            if (dynamicTexture.getPixels() == null) {
                throw new RuntimeException("NativeImage is not allocated.");
            }

            RenderSystem.recordRenderCall(() -> {
                // Applique la texture au manager
                textureManager.release(textureLocation);
                textureManager.register(textureLocation, dynamicTexture);
                dynamicTexture.upload();
                updateTexture(textureLocation);
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

