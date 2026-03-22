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

    private final Setting<Integer> normalPickaxeSlot = sgTool.add(new IntSetting.Builder()
        .name("normal-pickaxe-slot").description("Hotbar slot for Normal Pickaxe (1-9, 0 for auto-find)").defaultValue(0).min(0).max(9).build());
    private final Setting<Boolean> autoFindTools = sgTool.add(new BoolSetting.Builder()
        .name("auto-find-tools").description("Automatically find pickaxe in inventory if slot is set to 0").defaultValue(true).build());

    private MiningState state = MiningState.IDLE;
    private int minX, maxX, minY, maxY, minZ, maxZ;

    // Current tunnel position
    private int currentY;   // bottom block of the 1x2 tunnel
    private int currentX;   // which tunnel column we're on
    private int currentZ;   // progress along the tunnel
    private boolean goingPositiveZ; // snake direction

    public BoxMiner() {
        super(Categories.World, "box-miner", "Mines a box area using 1x2 tunnels with a normal pickaxe.");
    }

    @Override
    public void onActivate() {
        minX = Math.min(corner1X.get(), corner2X.get());
        maxX = Math.max(corner1X.get(), corner2X.get());
        minY = Math.min(corner1Y.get(), corner2Y.get());
        maxY = Math.max(corner1Y.get(), corner2Y.get());
        minZ = Math.min(corner1Z.get(), corner2Z.get());
        maxZ = Math.max(corner1Z.get(), corner2Z.get());

        if (!switchToNormalPickaxe()) {
            ChatUtils.error("BoxMiner: No pickaxe found! Aborting.");
            toggle();
            return;
        }

        // Start at bottom-left-front, sweep upward in 1x2 slices
        currentY = minY;
        currentX = minX;
        currentZ = minZ;
        goingPositiveZ = true;

        ChatUtils.info("BoxMiner started. Mining (%d,%d,%d) to (%d,%d,%d) with 1x2 tunnels.",
            minX, minY, minZ, maxX, maxY, maxZ);

        moveToCurrentPos();
    }

    @Override
    public void onDeactivate() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        state = MiningState.IDLE;
        ChatUtils.info("BoxMiner stopped.");
    }

    private void moveToCurrentPos() {
        if (mc.player == null) return;
        switchToNormalPickaxe();
        // GoalTwoBlocks = mine a 1x2 (feet + head) column at this XZ
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
            .setGoalAndPath(new GoalTwoBlocks(currentX, currentY, currentZ));
        state = MiningState.MINING;
    }

    private void advance() {
        // Step along Z within the current tunnel
        currentZ += goingPositiveZ ? 1 : -1;

        boolean pastEnd = goingPositiveZ ? currentZ > maxZ : currentZ < minZ;

        if (!pastEnd) {
            moveToCurrentPos();
            return;
        }

        // End of this Z run — move to next X column, flip direction
        currentZ = goingPositiveZ ? maxZ : minZ;
        goingPositiveZ = !goingPositiveZ;
        currentX++;

        if (currentX <= maxX) {
            moveToCurrentPos();
            return;
        }

        // Finished all X columns on this Y pair — step up 2 blocks
        currentX = minX;
        goingPositiveZ = true;
        currentZ = minZ;
        currentY += 2;

        if (currentY > maxY - 1) {
            ChatUtils.info("BoxMiner completed! Entire box mined.");
            toggle();
            return;
        }

        ChatUtils.info("Moving up to next level. Y=%d", currentY);
        moveToCurrentPos();
    }

    private boolean switchToNormalPickaxe() {
        if (mc.player == null) return false;
        int slot = normalPickaxeSlot.get();
        if (slot == 0 && autoFindTools.get()) {
            FindItemResult result = InvUtils.find(itemStack ->
                itemStack.isIn(ItemTags.PICKAXES));
            if (result.found()) { InvUtils.swap(result.slot(), false); return true; }
            ChatUtils.error("No pickaxe found in inventory!");
            return false;
        } else if (slot > 0 && slot <= 9) {
            InvUtils.swap(slot - 1, false);
            return true;
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || state == MiningState.IDLE) return;

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        if (state == MiningState.MINING && !baritone.getPathingBehavior().isPathing()) {
            BlockPos playerPos = mc.player.getBlockPos();
            BlockPos target = new BlockPos(currentX, currentY, currentZ);

            if (playerPos.isWithinDistance(target, 3)) {
                advance();
            } else {
                // Baritone gave up — retry
                moveToCurrentPos();
            }
        }
    }

    private enum MiningState {
        IDLE, MINING
    }
}
