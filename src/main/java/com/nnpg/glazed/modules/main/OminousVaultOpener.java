package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.*;

public class OminousVaultOpener extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOminous = settings.createGroup("Ominous Vault Items");
    private final SettingGroup sgRegular = settings.createGroup("Regular Vault Items");
    
    // General Settings
    private final Setting<VaultType> vaultType = sgGeneral.add(new EnumSetting.Builder<VaultType>()
        .name("vault-type")
        .description("Type of vault to target")
        .defaultValue(VaultType.OMINOUS)
        .build()
    );
    
    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Radius to scan for vaults")
        .defaultValue(5)
        .range(1, 50)
        .sliderRange(1, 20)
        .build()
    );
    
    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("How many consecutive matches before opening (higher = more accurate)")
        .defaultValue(3)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );
    
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between scans")
        .defaultValue(5)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );
    
    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Automatically disable after opening a vault")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send info messages to chat")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Boolean> showCalibration = sgGeneral.add(new BoolSetting.Builder()
        .name("show-calibration")
        .description("Show calibration progress in chat")
        .defaultValue(true)
        .build()
    );
    
    // Ominous Vault Items
    private final Setting<List<String>> ominousItems = sgOminous.add(new StringListSetting.Builder()
        .name("target-items")
        .description("Items to look for in ominous vaults")
        .defaultValue(List.of("Heavy Core"))
        .build()
    );
    
    private final Setting<Boolean> ominousEmerald = sgOminous.add(new BoolSetting.Builder()
        .name("emerald")
        .description("Target Emerald")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Emerald", val))
        .build()
    );
    
    private final Setting<Boolean> ominousWindCharge = sgOminous.add(new BoolSetting.Builder()
        .name("wind-charge")
        .description("Target Wind Charge")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Wind Charge", val))
        .build()
    );
    
    private final Setting<Boolean> ominousDiamond = sgOminous.add(new BoolSetting.Builder()
        .name("diamond")
        .description("Target Diamond")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Diamond", val))
        .build()
    );
    
    private final Setting<Boolean> ominousEnchantedGoldenApple = sgOminous.add(new BoolSetting.Builder()
        .name("enchanted-golden-apple")
        .description("Target Enchanted Golden Apple")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Enchanted Golden Apple", val))
        .build()
    );
    
    private final Setting<Boolean> ominousFlowBanner = sgOminous.add(new BoolSetting.Builder()
        .name("flow-banner-pattern")
        .description("Target Flow Banner Pattern")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Flow Banner Pattern", val))
        .build()
    );
    
    private final Setting<Boolean> ominousBottle = sgOminous.add(new BoolSetting.Builder()
        .name("ominous-bottle")
        .description("Target Ominous Bottle")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Ominous Bottle", val))
        .build()
    );
    
    private final Setting<Boolean> ominousEmeraldBlock = sgOminous.add(new BoolSetting.Builder()
        .name("emerald-block")
        .description("Target Block of Emerald")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Block of Emerald", val))
        .build()
    );
    
    private final Setting<Boolean> ominousCrossbow = sgOminous.add(new BoolSetting.Builder()
        .name("crossbow")
        .description("Target Crossbow")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Crossbow", val))
        .build()
    );
    
    private final Setting<Boolean> ominousIronBlock = sgOminous.add(new BoolSetting.Builder()
        .name("iron-block")
        .description("Target Block of Iron")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Block of Iron", val))
        .build()
    );
    
    private final Setting<Boolean> ominousGoldenApple = sgOminous.add(new BoolSetting.Builder()
        .name("golden-apple")
        .description("Target Golden Apple")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Golden Apple", val))
        .build()
    );
    
    private final Setting<Boolean> ominousDiamondAxe = sgOminous.add(new BoolSetting.Builder()
        .name("diamond-axe")
        .description("Target Diamond Axe")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Diamond Axe", val))
        .build()
    );
    
    private final Setting<Boolean> ominousDiamondChestplate = sgOminous.add(new BoolSetting.Builder()
        .name("diamond-chestplate")
        .description("Target Diamond Chestplate")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Diamond Chestplate", val))
        .build()
    );
    
    private final Setting<Boolean> ominousMusicDisc = sgOminous.add(new BoolSetting.Builder()
        .name("music-disc")
        .description("Target Music Disc")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Music Disc", val))
        .build()
    );
    
    private final Setting<Boolean> ominousHeavyCore = sgOminous.add(new BoolSetting.Builder()
        .name("heavy-core")
        .description("Target Heavy Core")
        .defaultValue(true)
        .onChanged(val -> updateItemList(ominousItems, "Heavy Core", val))
        .build()
    );
    
    private final Setting<Boolean> ominousEnchantedBook = sgOminous.add(new BoolSetting.Builder()
        .name("enchanted-book")
        .description("Target Enchanted Book")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Enchanted Book", val))
        .build()
    );
    
    private final Setting<Boolean> ominousDiamondBlock = sgOminous.add(new BoolSetting.Builder()
        .name("diamond-block")
        .description("Target Block of Diamond")
        .defaultValue(false)
        .onChanged(val -> updateItemList(ominousItems, "Block of Diamond", val))
        .build()
    );
    
    // Regular Vault Items
    private final Setting<List<String>> regularItems = sgRegular.add(new StringListSetting.Builder()
        .name("target-items")
        .description("Items to look for in regular vaults")
        .defaultValue(List.of())
        .build()
    );
    
    private final Setting<Boolean> regularEmerald = sgRegular.add(new BoolSetting.Builder()
        .name("emerald")
        .description("Target Emerald")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Emerald", val))
        .build()
    );
    
    private final Setting<Boolean> regularArrow = sgRegular.add(new BoolSetting.Builder()
        .name("arrow")
        .description("Target Arrow")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Arrow", val))
        .build()
    );
    
    private final Setting<Boolean> regularPoisonArrow = sgRegular.add(new BoolSetting.Builder()
        .name("poison-arrow")
        .description("Target Arrow of Poison")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Arrow of Poison", val))
        .build()
    );
    
    private final Setting<Boolean> regularIronIngot = sgRegular.add(new BoolSetting.Builder()
        .name("iron-ingot")
        .description("Target Iron Ingot")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Iron Ingot", val))
        .build()
    );
    
    private final Setting<Boolean> regularWindCharge = sgRegular.add(new BoolSetting.Builder()
        .name("wind-charge")
        .description("Target Wind Charge")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Wind Charge", val))
        .build()
    );
    
    private final Setting<Boolean> regularHoneyBottle = sgRegular.add(new BoolSetting.Builder()
        .name("honey-bottle")
        .description("Target Honey Bottle")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Honey Bottle", val))
        .build()
    );
    
    private final Setting<Boolean> regularOminousBottle = sgRegular.add(new BoolSetting.Builder()
        .name("ominous-bottle")
        .description("Target Ominous Bottle")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Ominous Bottle", val))
        .build()
    );
    
    private final Setting<Boolean> regularShield = sgRegular.add(new BoolSetting.Builder()
        .name("shield")
        .description("Target Shield")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Shield", val))
        .build()
    );
    
    private final Setting<Boolean> regularBow = sgRegular.add(new BoolSetting.Builder()
        .name("bow")
        .description("Target Bow")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Bow", val))
        .build()
    );
    
    private final Setting<Boolean> regularDiamond = sgRegular.add(new BoolSetting.Builder()
        .name("diamond")
        .description("Target Diamond")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Diamond", val))
        .build()
    );
    
    private final Setting<Boolean> regularGoldenApple = sgRegular.add(new BoolSetting.Builder()
        .name("golden-apple")
        .description("Target Golden Apple")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Golden Apple", val))
        .build()
    );
    
    private final Setting<Boolean> regularGoldenCarrot = sgRegular.add(new BoolSetting.Builder()
        .name("golden-carrot")
        .description("Target Golden Carrot")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Golden Carrot", val))
        .build()
    );
    
    private final Setting<Boolean> regularEnchantedBook = sgRegular.add(new BoolSetting.Builder()
        .name("enchanted-book")
        .description("Target Enchanted Book")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Enchanted Book", val))
        .build()
    );
    
    private final Setting<Boolean> regularCrossbow = sgRegular.add(new BoolSetting.Builder()
        .name("crossbow")
        .description("Target Crossbow")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Crossbow", val))
        .build()
    );
    
    private final Setting<Boolean> regularIronAxe = sgRegular.add(new BoolSetting.Builder()
        .name("iron-axe")
        .description("Target Iron Axe")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Iron Axe", val))
        .build()
    );
    
    private final Setting<Boolean> regularIronChestplate = sgRegular.add(new BoolSetting.Builder()
        .name("iron-chestplate")
        .description("Target Iron Chestplate")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Iron Chestplate", val))
        .build()
    );
    
    private final Setting<Boolean> regularBoltTrim = sgRegular.add(new BoolSetting.Builder()
        .name("bolt-trim")
        .description("Target Bolt Armor Trim Smithing Template")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Bolt Armor Trim Smithing Template", val))
        .build()
    );
    
    private final Setting<Boolean> regularMusicDisc = sgRegular.add(new BoolSetting.Builder()
        .name("music-disc")
        .description("Target Music Disc")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Music Disc", val))
        .build()
    );
    
    private final Setting<Boolean> regularGusterBanner = sgRegular.add(new BoolSetting.Builder()
        .name("guster-banner")
        .description("Target Guster Banner Pattern")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Guster Banner Pattern", val))
        .build()
    );
    
    private final Setting<Boolean> regularDiamondAxe = sgRegular.add(new BoolSetting.Builder()
        .name("diamond-axe")
        .description("Target Diamond Axe")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Diamond Axe", val))
        .build()
    );
    
    private final Setting<Boolean> regularDiamondChestplate = sgRegular.add(new BoolSetting.Builder()
        .name("diamond-chestplate")
        .description("Target Diamond Chestplate")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Diamond Chestplate", val))
        .build()
    );
    
    private final Setting<Boolean> regularTrident = sgRegular.add(new BoolSetting.Builder()
        .name("trident")
        .description("Target Trident")
        .defaultValue(false)
        .onChanged(val -> updateItemList(regularItems, "Trident", val))
        .build()
    );

    // State variables
    private int tickCounter = 0;
    private final List<String> calibrationArray = new ArrayList<>();
    private final Set<BlockPos> processedVaults = new HashSet<>();
    
    public enum VaultType {
        OMINOUS("Ominous"),
        REGULAR("Regular"),
        BOTH("Both");
        
        private final String name;
        
        VaultType(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }

    public OminousVaultOpener() {
        super(GlazedAddon.CATEGORY, "ominous-vault-opener", "Automatically opens vaults with specific items");
    }

    @Override
    public void onActivate() {
        resetState();
        if (chatInfo.get()) {
            List<String> targets = vaultType.get() == VaultType.REGULAR ? regularItems.get() : ominousItems.get();
            if (targets.isEmpty()) {
                warning("No target items selected! Enable items in the settings.");
            } else {
                info("Scanning for: " + String.join(", ", targets));
            }
        }
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        // Get current target items based on vault type
        List<String> targetItems = getTargetItems();
        if (targetItems.isEmpty()) return;

        // Scan for vaults
        scanNearbyVaults(targetItems);
    }

    private List<String> getTargetItems() {
        return switch (vaultType.get()) {
            case OMINOUS -> ominousItems.get();
            case REGULAR -> regularItems.get();
            case BOTH -> {
                List<String> combined = new ArrayList<>();
                combined.addAll(ominousItems.get());
                combined.addAll(regularItems.get());
                yield combined;
            }
        };
    }

    private void scanNearbyVaults(List<String> targetItems) {
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = scanRadius.get();
        
        List<VaultInfo> vaults = new ArrayList<>();
        
        // Scan area around player
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    
                    if (state.getBlock() instanceof VaultBlock) {
                        boolean isOminous = state.get(VaultBlock.OMINOUS);
                        
                        // Filter based on vault type setting
                        if (vaultType.get() == VaultType.OMINOUS && !isOminous) continue;
                        if (vaultType.get() == VaultType.REGULAR && isOminous) continue;
                        
                        // Skip already processed vaults
                        if (processedVaults.contains(pos)) continue;
                        
                        ItemStack displayItem = getVaultDisplayItem(pos);
                        if (displayItem != null && !displayItem.isEmpty()) {
                            String itemName = displayItem.getName().getString();
                            double distance = Math.sqrt(playerPos.getSquaredDistance(pos));
                            
                            vaults.add(new VaultInfo(pos, itemName, distance, isOminous));
                        }
                    }
                }
            }
        }
        
        // Sort by distance (closest first)
        vaults.sort(Comparator.comparingDouble(v -> v.distance));
        
        // Process vaults
        for (VaultInfo vault : vaults) {
            processVault(vault, targetItems);
        }
    }

    private void processVault(VaultInfo vault, List<String> targetItems) {
        // Check if item matches any target
        boolean matches = targetItems.stream().anyMatch(target -> vault.itemName.contains(target));
        
        if (matches) {
            calibrationArray.add(vault.itemName);
            
            if (showCalibration.get() && chatInfo.get()) {
                info(String.format("§%c%s§f at (%.0f, %.0f, %.0f): §e%s §7(%.1fm) - Cal: §a%d§7/§e%d",
                    vault.isOminous ? 'c' : 'a',
                    vault.isOminous ? "Ominous" : "Regular",
                    vault.position.getX(), vault.position.getY(), vault.position.getZ(),
                    vault.itemName, vault.distance,
                    calibrationArray.size(), sensitivity.get()));
            }
            
            // Check if we have enough consecutive matches
            if (calibrationArray.size() >= sensitivity.get()) {
                boolean allMatch = calibrationArray.stream()
                    .allMatch(item -> targetItems.stream().anyMatch(item::contains));
                
                if (allMatch) {
                    // Found target! Open the vault
                    openVault(vault.position);
                    processedVaults.add(vault.position);
                    calibrationArray.clear();
                    
                    if (chatInfo.get()) {
                        info("§aOpened vault with §e" + vault.itemName + " §aat " + vault.position.toShortString());
                    }
                    
                    if (autoDisable.get()) {
                        toggle();
                        if (chatInfo.get()) {
                            info("§6Auto-disabled after opening vault");
                        }
                    }
                    return;
                }
                
                // Reset if mismatch
                calibrationArray.clear();
            }
        } else {
            // Reset calibration if we see a different item
            if (!calibrationArray.isEmpty()) {
                calibrationArray.clear();
                if (showCalibration.get() && chatInfo.get()) {
                    info("§cCalibration reset - found different item");
                }
            }
        }
    }

    private void openVault(BlockPos pos) {
        // Calculate the center of the block
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        
        // Create a block hit result
        BlockHitResult hitResult = new BlockHitResult(
            blockCenter,
            Direction.UP,
            pos,
            false
        );
        
        // Interact with the block
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
    }

    private ItemStack getVaultDisplayItem(BlockPos pos) {
        try {
            BlockEntity blockEntity = mc.world.getBlockEntity(pos);
            if (blockEntity == null) return null;
            
            // Use reflection to get the display item
            Field sharedDataField = blockEntity.getClass().getDeclaredField("sharedData");
            sharedDataField.setAccessible(true);
            Object sharedData = sharedDataField.get(blockEntity);
            
            Field displayItemField = sharedData.getClass().getDeclaredField("displayItem");
            displayItemField.setAccessible(true);
            ItemStack item = (ItemStack) displayItemField.get(sharedData);
            
            return item.isEmpty() ? null : item;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void updateItemList(Setting<List<String>> listSetting, String item, boolean add) {
        List<String> current = new ArrayList<>(listSetting.get());
        if (add && !current.contains(item)) {
            current.add(item);
        } else if (!add) {
            current.remove(item);
        }
        listSetting.set(current);
    }

    private void resetState() {
        tickCounter = 0;
        calibrationArray.clear();
        processedVaults.clear();
    }

    @Override
    public String getInfoString() {
        if (calibrationArray.size() > 0) {
            return calibrationArray.size() + "/" + sensitivity.get();
        }
        List<String> targets = getTargetItems();
        return targets.isEmpty() ? "No items" : targets.size() + " items";
    }

    private static class VaultInfo {
        BlockPos position;
        String itemName;
        double distance;
        boolean isOminous;
        
        VaultInfo(BlockPos position, String itemName, double distance, boolean isOminous) {
            this.position = position;
            this.itemName = itemName;
            this.distance = distance;
            this.isOminous = isOminous;
        }
    }
}