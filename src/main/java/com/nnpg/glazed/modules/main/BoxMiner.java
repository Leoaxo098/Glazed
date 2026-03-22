package com.nnpg.glazed.modules.main;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalTwoBlocks;

public class BoxMiner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTool = settings.createGroup("Tool Selection");

    private final Setting<Integer> corner1X = sgGeneral.add(new IntSetting.Builder()
        .name("corner-1-x").description("Corner 1 X coordinate").defaultValue(0).build());
    private final Setting<Integer> corner1Y = sgGeneral.add(new IntSetting.Builder()
        .name("corner-1-y").description("Corner 1 Y coordinate").defaultValue(64).build());
    private final Setting<Integer> corner1Z = sgGeneral.add(new IntSetting.Builder()
        .name("corner-1-z").description("Corner 1 Z coordinate").defaultValue(0).build());
    private final Setting<Integer> corner2X = sgGeneral.add(new IntSetting.Builder()
        .name("corner-2-x").description("Corner 2 X coordinate").defaultValue(30).build());
    private final Setting<Integer> corner2Y = sgGeneral.add(new IntSetting.Builder()
        .name("corner-2-y").description("Corner 2 Y coordinate").defaultValue(94).build());
    private final Setting<Integer> corner2Z = sgGeneral.add(new IntSetting.Builder()
        .name("corner-2-z").description("Corner 2 Z coordinate").defaultValue(30).build());

    private final Setting<MiningMode> miningMode = sgGeneral.add(new EnumSetting.Builder<MiningMode>()
        .name("mining-mode")
        .description("3x3: uses Amethyst Pickaxe sweeping layers. Tunnel: uses normal pickaxe with 1x2 tunnels covering the full box.")
        .defaultValue(MiningMode.THREE_BY_THREE)
        .build());

    // 3x3 mode only settings
    private final Setting<Integer> layerSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("layer-spacing").description("Vertical spacing between mining layers (3x3 mode only)").defaultValue(3).min(1).max(10).build());
    private final Setting<Integer> xSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("x-spacing").description("Horizontal spacing between mining points (3x3 mode only)").defaultValue(3).min(1).max(10).build());

    private final Setting<Integer> amethystPickaxeSlot = sgTool.add(new IntSetting.Builder()
        .name("amethyst-pickaxe-slot").description("Hotbar slot for Amethyst Pickaxe (1-9, 0 for auto-find)").defaultValue(0).min(0).max(9).build());
    private final Setting<Integer> normalPickaxeSlot = sgTool.add(new IntSetting.Builder()
        .name("normal-pickaxe-slot").description("Hotbar slot for Normal Pickaxe (1-9, 0 for auto-find)").defaultValue(0).min(0).max(9).build());
    private final Setting<Boolean> autoFindTools = sgTool.add(new BoolSetting.Builder()
        .name("auto-find-tools").description("Automatically find tools by name if slots are set to 0").defaultValue(true).build());

    private MiningState state = MiningState.IDLE;
    private int currentY, currentX, currentZ;
    private int minX, maxX, minY, maxY, minZ, maxZ;

    // 3x3 mode
    private int descentStartX, descentStartZ;

    // Tunnel mode
    private boolean tunnelGoingPositiveZ;

    public BoxMiner() {
        super(Categories.World, "box-miner", "Mines a box area using Baritone. Supports 3x3 pickaxe mode and 1x2 tunnel mode.");
    }

    @Override
    public void onActivate() {
        minX = Math.min(corner1X.get(), corner2X.get());
        maxX = Math.max(corner1X.get(), corner2X.get());
        minY = Math.min(corner1Y.get(), corner2Y.get());
        maxY = Math.max(corner1Y.get(), corner2Y.get());
        minZ = Math.min(corner1Z.get(), corner2Z.get());
        maxZ = Math.max(corner1Z.get(), corner2Z.get());

        ChatUtils.info("BoxMiner started in %s mode. Box: (%d,%d,%d) to (%d,%d,%d)",
            miningMode.get().name(), minX, minY, minZ, maxX, maxY, maxZ);

        if (miningMode.get() == MiningMode.TUNNEL) {
            initTunnel();
        } else {
            initThreeByThree();
        }
    }

    @Override
    public void onDeactivate() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        state = MiningState.IDLE;
        ChatUtils.info("BoxMiner stopped.");
    }

    // -------------------------------------------------------------------------
    // 3x3 Mode
    // -------------------------------------------------------------------------

    private void initThreeByThree() {
        currentY = maxY;
        currentX = minX + 1;
        currentZ = minZ;
        state = MiningState.MOVING_TO_LAYER;
        moveToNextPosition();
    }

    private void moveToNextPosition() {
        if (mc.player == null) return;
        switchToAmethystPickaxe();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
            .setGoalAndPath(new GoalBlock(currentX, currentY, currentZ));
        state = MiningState.MOVING_TO_POSITION;
    }

    private void mineCurrentBlock() {
        if (mc.player == null) return;
        switchToAmethystPickaxe();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
            .setGoalAndPath(new GoalBlock(currentX, currentY, currentZ));
        state = MiningState.MINING;
    }

    private void startDescent() {
        if (mc.player == null) return;
        descentStartX = currentX;
        descentStartZ = currentZ;
        if (switchToNormalPickaxe()) {
            ChatUtils.info("Descending to next layer. Y: %d -> %d", currentY, currentY - layerSpacing.get());
            currentY -= layerSpacing.get();
            state = MiningState.DESCENDING;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                .setGoalAndPath(new GoalBlock(descentStartX, currentY, descentStartZ));
        } else {
            ChatUtils.error("Cannot descend without normal pickaxe!");
            toggle();
        }
    }

    private void advanceToNextBlock() {
        currentZ++;
        if (currentZ > maxZ) {
            currentZ = minZ;
            currentX += xSpacing.get();
            if (currentX > maxX - 1) {
                currentX = minX + 1;
                currentY -= layerSpacing.get();
                if (currentY < minY + 1) {
                    ChatUtils.info("BoxMiner (3x3 mode) completed!");
                    toggle();
                    return;
                }
                startDescent();
                return;
            }
        }
        moveToNextPosition();
    }

    // -------------------------------------------------------------------------
    // Tunnel Mode (1x2, normal pickaxe only, covers every block)
    // -------------------------------------------------------------------------

    private void initTunnel() {
        if (!switchToNormalPickaxe()) {
            ChatUtils.error("BoxMiner: No pickaxe found! Aborting.");
            toggle();
            return;
        }
        currentY = minY;
        currentX = minX;
        currentZ = minZ;
        tunnelGoingPositiveZ = true;
        moveToTunnelPos();
    }

    private void moveToTunnelPos() {
        if (mc.player == null) return;
        switchToNormalPickaxe();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
            .setGoalAndPath(new GoalTwoBlocks(currentX, currentY, currentZ));
        state = MiningState.TUNNEL_MINING;
    }

    private void advanceTunnel() {
        // Step along Z
        currentZ += tunnelGoingPositiveZ ? 1 : -1;
        boolean pastEnd = tunnelGoingPositiveZ ? currentZ > maxZ : currentZ < minZ;

        if (!pastEnd) {
            moveToTunnelPos();
            return;
        }

        // End of Z run — flip direction, step to next X column
        currentZ = tunnelGoingPositiveZ ? maxZ : minZ;
        tunnelGoingPositiveZ = !tunnelGoingPositiveZ;
        currentX++;

        if (currentX <= maxX) {
            moveToTunnelPos();
            return;
        }

        // All X columns done on this Y pair — step up 2 blocks
        currentX = minX;
        currentZ = minZ;
        tunnelGoingPositiveZ = true;
        currentY += 2;

        if (currentY > maxY - 1) {
            ChatUtils.info("BoxMiner (tunnel mode) completed!");
            toggle();
            return;
        }

        ChatUtils.info("Moving up to Y=%d", currentY);
        moveToTunnelPos();
    }

    // -------------------------------------------------------------------------
    // Tool helpers
    // -------------------------------------------------------------------------

    private boolean switchToAmethystPickaxe() {
        if (mc.player == null) return false;
        int slot = amethystPickaxeSlot.get();
        if (slot == 0 && autoFindTools.get()) {
            FindItemResult result = InvUtils.find(itemStack -> {
                String name = itemStack.getName().getString().toLowerCase();
                return name.contains("amethyst") && itemStack.isIn(ItemTags.PICKAXES);
            });
            if (result.found()) { InvUtils.swap(result.slot(), false); return true; }
            ChatUtils.error("Amethyst Pickaxe not found in inventory!");
            return false;
        } else if (slot > 0 && slot <= 9) {
            InvUtils.swap(slot - 1, false);
            return true;
        }
        return false;
    }

    private boolean switchToNormalPickaxe() {
        if (mc.player == null) return false;
        int slot = normalPickaxeSlot.get();
        if (slot == 0 && autoFindTools.get()) {
            FindItemResult result = InvUtils.find(itemStack -> {
                String name = itemStack.getName().getString().toLowerCase();
                return !name.contains("amethyst") && itemStack.isIn(ItemTags.PICKAXES);
            });
            if (result.found()) { InvUtils.swap(result.slot(), false); return true; }
            ChatUtils.error("Normal Pickaxe not found in inventory!");
            return false;
        } else if (slot > 0 && slot <= 9) {
            InvUtils.swap(slot - 1, false);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || state == MiningState.IDLE) return;

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        switch (state) {
            case MOVING_TO_POSITION -> {
                if (!baritone.getPathingBehavior().isPathing()) {
                    BlockPos playerPos = mc.player.getBlockPos();
                    BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);
                    if (playerPos.isWithinDistance(targetPos, 5)) mineCurrentBlock();
                    else moveToNextPosition();
                }
            }
            case MINING -> {
                if (!baritone.getPathingBehavior().isPathing()) advanceToNextBlock();
            }
            case DESCENDING -> {
                if (!baritone.getPathingBehavior().isPathing()) {
                    if (Math.abs(mc.player.getBlockPos().getY() - currentY) <= 1) {
                        currentZ = minZ;
                        ChatUtils.info("Descent complete. Starting new layer at Y=%d", currentY);
                        moveToNextPosition();
                    } else {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(descentStartX, currentY, descentStartZ));
                    }
                }
            }
            case MOVING_TO_LAYER -> {
                if (!baritone.getPathingBehavior().isPathing()) {
                    state = MiningState.MOVING_TO_POSITION;
                    moveToNextPosition();
                }
            }
            case TUNNEL_MINING -> {
                if (!baritone.getPathingBehavior().isPathing()) {
                    BlockPos playerPos = mc.player.getBlockPos();
                    BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);
                    if (playerPos.isWithinDistance(targetPos, 3)) advanceTunnel();
                    else moveToTunnelPos();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum MiningMode {
        THREE_BY_THREE, TUNNEL
    }

    private enum MiningState {
        IDLE, MOVING_TO_LAYER, MOVING_TO_POSITION, MINING, DESCENDING, TUNNEL_MINING
    }
}
