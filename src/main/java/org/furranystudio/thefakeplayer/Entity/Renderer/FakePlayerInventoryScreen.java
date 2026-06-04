package org.furranystudio.thefakeplayer.Entity.Renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Entity.Menu.FakePlayerInventoryMenu;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class FakePlayerInventoryScreen extends AbstractContainerScreen<FakePlayerInventoryMenu> {

    private static final int IMAGE_WIDTH       = 176;
    private static final int IMAGE_HEIGHT      = 282;
    private static final int SEPARATOR_Y       = 200;
    private static final int PLAYER_LABEL_Y    = 190;

    private FakePlayerEntity displayEntity = null;

    public FakePlayerInventoryScreen(FakePlayerInventoryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    public void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof FakePlayerEntity fp) {
                    displayEntity = fp;
                    break;
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Outer border
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        // Main background
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFC6C6C6);
        // Top equipment/model area (slightly darker)
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 101, 0xFFBCBCBC);
        // Separator between FakePlayer inventory and player inventory
        guiGraphics.fill(leftPos + 7, topPos + SEPARATOR_Y, leftPos + imageWidth - 7, topPos + SEPARATOR_Y + 2, 0xFF555555);

        // Slot backgrounds — vanilla-style sunken squares
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            int sx = this.leftPos + slot.x;
            int sy = this.topPos + slot.y;
            guiGraphics.fill(sx - 1, sy - 1, sx + 16, sy,      0xFF555555); // top shadow
            guiGraphics.fill(sx - 1, sy - 1, sx,      sy + 16, 0xFF555555); // left shadow
            guiGraphics.fill(sx - 1, sy + 16, sx + 17, sy + 17, 0xFFFFFFFF); // bottom highlight
            guiGraphics.fill(sx + 16, sy - 1, sx + 17, sy + 17, 0xFFFFFFFF); // right highlight
            guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);           // interior
        }

        // FakePlayer model
        if (displayEntity != null) {
            int modelX = leftPos + 88;
            int modelY = topPos + 90;
            InventoryScreen.renderEntityInInventory(
                guiGraphics,
                modelX, modelY,
                30,
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Quaternionf().rotateZ((float) Math.PI),
                new Quaternionf().rotateY((float)(mouseX - modelX) * 0.03f),
                displayEntity
            );
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Section labels
        String name = displayEntity != null ? displayEntity.getName().getString() : "FakePlayer";
        guiGraphics.drawString(font, name + "'s Inventory", leftPos + 30, topPos + 6, 0x404040, false);
        guiGraphics.drawString(font, "Player Inventory", leftPos + 7, topPos + PLAYER_LABEL_Y, 0x606060, false);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Handled in render()
    }
}
