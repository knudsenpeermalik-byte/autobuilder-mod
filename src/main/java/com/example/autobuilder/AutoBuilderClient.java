package com.example.autobuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.WorldSchematic;

public class AutoBuilderClient implements ClientModInitializer {

    private static boolean enabled = false;
    private static KeyBinding toggleKey;
    private final int BUILD_RADIUS = 4;
    private int tickCounter = 0;
    private final int TICKS_PER_BLOCK = 3;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.autobuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                tickCounter = 0;
                client.player.sendMessage(
                    net.minecraft.text.Text.of("AutoBuilder: " + (enabled ? "§aAKTIVIERT (1 Block / 3 Ticks)" : "§cDEAKTIVIERT")), 
                    true
                );
            }

            if (enabled) {
                tickCounter++;
                if (tickCounter >= TICKS_PER_BLOCK) {
                    tickCounter = 0;
                    tickAutoBuilder(client);
                }
            }
        });
    }

    private void tickAutoBuilder(MinecraftClient client) {
        BlockPos playerPos = client.player.getBlockPos();

        for (int x = -BUILD_RADIUS; x <= BUILD_RADIUS; x++) {
            for (int y = -BUILD_RADIUS; y <= BUILD_RADIUS; y++) {
                for (int z = -BUILD_RADIUS; z <= BUILD_RADIUS; z++) {
                    BlockPos targetPos = playerPos.add(x, y, z);

                    BlockState requiredState = getLitematicaTargetState(targetPos);
                    if (requiredState == null || requiredState.isAir()) continue;

                    BlockState currentState = client.world.getBlockState(targetPos);
                    if (currentState.equals(requiredState)) continue;

                    if (currentState.isAir() || currentState.getMaterial().isReplaceable()) {
                        if (tryPlaceBlock(client, targetPos, requiredState)) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private BlockState getLitematicaTargetState(BlockPos pos) {
        WorldSchematic worldSchematic = DataManager.getSchematicWorldHandler().getSchematicWorld();
        if (worldSchematic != null) {
            return worldSchematic.getBlockState(pos);
        }
        return null;
    }

    private boolean tryPlaceBlock(MinecraftClient client, BlockPos pos, BlockState requiredState) {
        PlayerInventory inventory = client.player.getInventory();
        int slot = findMatchingSlot(inventory, requiredState);

        if (slot == -1) return false;

        if (slot >= 9) {
            client.interactionManager.pickFromInventory(slot);
        } else {
            inventory.selectedSlot = slot;
        }

        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);

        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
        client.player.swingHand(Hand.MAIN_HAND);

        return true;
    }

    private int findMatchingSlot(PlayerInventory inventory, BlockState requiredState) {
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == requiredState.getBlock()) {
                    return i;
                }
            }
        }
        return -1;
    }
}
