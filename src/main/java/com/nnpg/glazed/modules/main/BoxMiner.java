package com.example.glazed.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.IPlayerContext;

public class BoxMiner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTool = settings.createGroup("Tool Selection");

    private final Setting<Integer> corner1X = sgGeneral.add(new IntSetting.Builder()
        .name("corner-1-x")
        .description("Corner 1 X coordinate")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> corner1Y = sgGeneral.add(new IntSetting.Builder()
        .name("corner-1-y")
        .description("Corner 1 Y coordinate")
        .defaultValue(64)
        .build()
    );

    private final Setting<Integer> corner1Z = sgGeneral.add(new IntSetting.Builder()
        .name("corner-1-z")
        .description("Corner 1 Z coordinate")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> corner2X = sgGeneral.add(new IntSetting.Builder()
        .name("corner-2-x")
        .description("Corner 2 X coordinate")
        .defaultValue(30)
        .build()
    );

    private final Setting<Integer> corner2Y = sgGeneral.add(new IntSetting.Builder()
        .name("corner-2-y")
        .description("Corner 2 Y coordinate")
        .defaultValue(94)
        .build()
    );

    private final Setting<Integer> corner2Z = sgGeneral.add(new IntSetting.Builder()
        .name("corner-2-z")
        .description("Corner 2 Z coordinate")
        .defaultValue(30)
        .build()
    );

    private final Setting<Integer> layerSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("layer-spacing")
        .description("Vertical spacing between mining layers (3 for 3x3 pickaxe)")
        .defaultValue(3)
        .min(1)
        .max(10)
        .build()
    );

    private final Setting<Integer> xSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("x-spacing")
        .description("Horizontal spacing between mining points (3 for 3x3 pickaxe)")
        .defaultValue(3)
        .min(1)
        .max(10)
        .build()
    );

    private final Setting<Integer> amethystPickaxeSlot = sgTool.add(new IntSetting.Builder()
        .name("amethyst-pickaxe-slot")
        .description("Hotbar slot for Amethyst Pickaxe (1-9, 0 for auto-find)")
        .defaultValue(0)
        .min(0)
        .max(9)
        .build()
    );

    private final Setting<Integer> normalPickaxeSlot = sgTool.add(new IntSetting.Builder()
        .name("normal-pickaxe-slot")
        .description("Hotbar slot for Normal Pickaxe (1-9, 0 for auto-find)")
        .defaultValue(0)
        .min(0)
        .max(9)
        .build()
    );

    private final Setting<Boolean> autoFindTools = sgTool.add(new BoolSetting.Builder()
        .name("auto-find-tools")
        .description("Automatically find tools by name if slots are set to 0")
        .defaultValue(true)
        .build()
    );

    private MiningState state = MiningState.IDLE;
    private int currentY;
    private int currentX;
    private int currentZ;
    private int minX, maxX, minY, maxY, minZ, maxZ;
    private int descentStartX, descentStartZ;

    public BoxMiner() {
        super(Categories.World, "box-miner", "Automatically mines a box area using Baritone with 3x3 pickaxe optimization");
    }

    @Override
    public void onActivate() {
        // Calculate box boundaries
        minX = Math.min(corner1X.get(), corner2X.get());
        maxX = Math.max(corner1X.get(), corner2X.get());
        minY = Math.min(corner1Y.get(), corner2Y.get());
        maxY = Math.max(corner1Y.get(), corner2Y.get());
        minZ = Math.min(corner1Z.get(), corner2Z.get());
        maxZ = Math.max(corner1Z.get(), corner2Z.get());

        // Start from top layer
        currentY = maxY;
        currentX = minX + 1;
        currentZ = minZ;

        state = MiningState.MOVING_TO_LAYER;
        
        ChatUtils.info("BoxMiner started. Mining box from (%d,%d,%d) to (%d,%d,%d)", 
            minX, minY, minZ, maxX, maxY, maxZ);
        
        moveToNextPosition();
    }

    @Override
    public void onDeactivate() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        state = MiningState.IDLE;
        ChatUtils.info("BoxMiner stopped.");
    }

    private boolean switchToAmethystPickaxe() {
        if (mc.player == null) return false;

        int slot = amethystPickaxeSlot.get();
        
        if (slot == 0 && autoFindTools.get()) {
            // Auto-find Amethyst Pickaxe
            FindItemResult result = InvUtils.find(itemStack -> {
                String name = itemStack.getName().getString().toLowerCase();
                return name.contains("amethyst") && itemStack.getItem() instanceof MiningToolItem;
            });
            
            if (result.found()) {
                InvUtils.swap(result.slot(), false);
                return true;
            } else {
                ChatUtils.error("Amethyst Pickaxe not found in inventory!");
                return false;
            }
        } else if (slot > 0 && slot <= 9) {
            // Use specified slot (convert to 0-indexed)
            InvUtils.swap(slot - 1, false);
            return true;
        }
        
        return false;
    }

    private boolean switchToNormalPickaxe() {
        if (mc.player == null) return false;

        int slot = normalPickaxeSlot.get();
        
        if (slot == 0 && autoFindTools.get()) {
            // Auto-find normal pickaxe (not Amethyst)
            FindItemResult result = InvUtils.find(itemStack -> {
                String name = itemStack.getName().getString().toLowerCase();
                return !name.contains("amethyst") && itemStack.getItem() instanceof MiningToolItem;
            });
            
            if (result.found()) {
                InvUtils.swap(result.slot(), false);
                return true;
            } else {
                ChatUtils.error("Normal Pickaxe not found in inventory!");
                return false;
            }
        } else if (slot > 0 && slot <= 9) {
            // Use specified slot (convert to 0-indexed)
            InvUtils.swap(slot - 1, false);
            return true;
        }
        
        return false;
    }

    private void moveToNextPosition() {
        if (mc.player == null) return;

        BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);
        
        // Switch to Amethyst Pickaxe for layer mining
        switchToAmethystPickaxe();
        
        // Use Baritone to go to position
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
            .setGoalAndPath(new GoalBlock(targetPos));
        
        state = MiningState.MOVING_TO_POSITION;
    }

    private void mineCurrentBlock() {
        if (mc.player == null) return;

        BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);
        
        // Ensure Amethyst Pickaxe is equipped
        switchToAmethystPickaxe();
        
        // Use Baritone mine command for single block
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess()
            .mine(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        
        state = MiningState.MINING;
    }

    private void startDescent() {
        if (mc.player == null) return;
        
        // Save current position to descend from
        descentStartX = currentX;
        descentStartZ = currentZ;
        
        // Switch to normal pickaxe for descent
        if (switchToNormalPickaxe()) {
            ChatUtils.info("Starting descent to next layer. Y: %d -> %d", currentY, currentY - layerSpacing.get());
            state = MiningState.DESCENDING;
            mineDescentPath();
        } else {
            ChatUtils.error("Cannot descend without normal pickaxe!");
            toggle();
        }
    }

    private void mineDescentPath() {
        if (mc.player == null) return;
        
        int targetY = currentY - layerSpacing.get();
        
        // Mine straight down using normal pickaxe
        BlockPos descentPos = new BlockPos(descentStartX, targetY, descentStartZ);
        
        // Use Baritone to mine down to target Y level
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
            .setGoalAndPath(new GoalBlock(descentPos));
    }

    private void advanceToNextBlock() {
        // Move through Z axis (depth)
        currentZ++;
        
        if (currentZ > maxZ) {
            // Move to next X position
            currentZ = minZ;
            currentX += xSpacing.get();
            
            if (currentX > maxX - 1) {
                // Finished this layer, need to descend
                currentX = minX + 1;
                currentY -= layerSpacing.get();
                
                if (currentY < minY + 1) {
                    // Finished mining entire box
                    ChatUtils.info("BoxMiner completed! All blocks mined.");
                    toggle();
                    return;
                } else {
                    // Start descent to next layer
                    startDescent();
                    return;
                }
            }
        }
        
        moveToNextPosition();
    }

    @Override
    public void onTick() {
        if (mc.player == null || state == MiningState.IDLE) return;

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        
        switch (state) {
            case MOVING_TO_POSITION:
                // Check if Baritone reached the position
                if (!baritone.getPathingBehavior().isPathing()) {
                    BlockPos playerPos = mc.player.getBlockPos();
                    BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);
                    
                    if (playerPos.isWithinDistance(targetPos, 5)) {
                        mineCurrentBlock();
                    } else {
                        // Failed to reach, try again
                        moveToNextPosition();
                    }
                }
                break;
                
            case MINING:
                // Check if Baritone finished mining
                if (!baritone.getMineProcess().isActive()) {
                    advanceToNextBlock();
                }
                break;
                
            case DESCENDING:
                // Check if descent is complete
                if (!baritone.getPathingBehavior().isPathing()) {
                    BlockPos playerPos = mc.player.getBlockPos();
                    int targetY = currentY;
                    
                    if (Math.abs(playerPos.getY() - targetY) <= 1) {
                        // Descent complete, reset to start of new layer
                        currentZ = minZ;
                        ChatUtils.info("Descent complete. Starting new layer at Y=%d", currentY);
                        moveToNextPosition();
                    } else {
                        // Still descending or failed
                        mineDescentPath();
                    }
                }
                break;
                
            case MOVING_TO_LAYER:
                // Initial movement to starting position
                if (!baritone.getPathingBehavior().isPathing()) {
                    state = MiningState.MOVING_TO_POSITION;
                    moveToNextPosition();
                }
                break;
        }
    }

    private enum MiningState {
        IDLE,
        MOVING_TO_LAYER,
        MOVING_TO_POSITION,
        MINING,
        DESCENDING
    }
}
