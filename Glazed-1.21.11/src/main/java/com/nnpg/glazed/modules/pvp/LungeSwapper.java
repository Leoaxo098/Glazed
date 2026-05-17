package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class LungeSwapper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ---- Instant Item Settings ----
    private final Setting<InstantMode> instantMode = sgGeneral.add(new EnumSetting.Builder<InstantMode>()
        .name("instant-mode")
        .description("How to select the instant-cooldown item (wind charge / golden apple).")
        .defaultValue(InstantMode.Auto)
        .build()
    );

    private final Setting<Integer> instantSlot = sgGeneral.add(new IntSetting.Builder()
        .name("instant-slot")
        .description("The hotbar slot (1-9) containing the instant-cooldown item.")
        .sliderRange(1, 9)
        .defaultValue(1)
        .min(1)
        .visible(() -> instantMode.get() == InstantMode.Slot)
        .build()
    );

    // ---- Lunge Spear Settings ----
    private final Setting<LungeMode> lungeMode = sgGeneral.add(new EnumSetting.Builder<LungeMode>()
        .name("lunge-mode")
        .description("How to select the lunge-enchanted spear.")
        .defaultValue(LungeMode.Auto)
        .build()
    );

    private final Setting<Integer> lungeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("lunge-slot")
        .description("The hotbar slot (1-9) containing the lunge spear.")
        .sliderRange(1, 9)
        .defaultValue(2)
        .min(1)
        .visible(() -> lungeMode.get() == LungeMode.Slot)
        .build()
    );

    // ---- Swap Back Settings ----
    private final Setting<SwapBackMode> swapBackMode = sgGeneral.add(new EnumSetting.Builder<SwapBackMode>()
        .name("swap-back-mode")
        .description("What to swap to after the lunge attack.")
        .defaultValue(SwapBackMode.Previous)
        .build()
    );

    private final Setting<Integer> swapBackSlot = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-slot")
        .description("The hotbar slot (1-9) to swap to after lunging.")
        .sliderRange(1, 9)
        .defaultValue(1)
        .min(1)
        .visible(() -> swapBackMode.get() == SwapBackMode.Configured)
        .build()
    );

    // ---- General Settings ----
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print debug messages in chat.")
        .defaultValue(false)
        .build()
    );

    // ---- State Machine ----
    // Stage 0 = not running, 1 = swap to instant (if needed), 2 = attack+swap to lunge, 3 = swap-back (if needed)
    private int stage = 0;
    private int prevSlot = -1;
    private int lungeSpearSlot = -1;

    public LungeSwapper() {
        super(GlazedAddon.pvp, "lunge-swapper", "Swaps to an instant-cooldown item, attacks, then swaps to a lunge-enchanted spear in the same tick for the attribute swap exploit. Auto-toggles off.");
    }

    // ---- Enums ----
    public enum InstantMode { Auto, Slot }
    public enum LungeMode { Auto, Slot }
    public enum SwapBackMode { None, Previous, Configured }

    // ---- Activation ----
    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        prevSlot = mc.player.getInventory().getSelectedSlot();

        // Pre-find lunge spear slot
        lungeSpearSlot = findLungeSpearSlot();
        if (lungeSpearSlot == -1) {
            error("No lunge-enchanted spear found in hotbar.");
            toggle();
            return;
        }

        // Decide starting stage
        if (isHoldingInstantItem()) {
            // Already holding instant item — go straight to attack+swap (stage 2)
            stage = 2;
            if (debug.get()) info("Already holding instant item. Will attack+swap next tick.");
        } else {
            // Need to swap to instant item first (stage 1)
            int instantSlotFound = findInstantItemSlot();
            if (instantSlotFound == -1) {
                error("No instant-cooldown item (wind charge / golden apple) found in hotbar.");
                toggle();
                return;
            }
            // Swap now
            mc.player.getInventory().setSelectedSlot(instantSlotFound);
            stage = 2; // Next tick = attack+swap
            if (debug.get()) info("Swapped to instant item at slot " + (instantSlotFound + 1) + ". Will attack+swap next tick.");
        }
    }

    // ---- Tick Handler ----
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        switch (stage) {
            case 2 -> handleAttackAndSwap();
            case 3 -> handleSwapBack();
            default -> toggle();
        }
    }

    // ---- Stage 2: Attack + Swap to Lunge Spear (same tick) ----
    private void handleAttackAndSwap() {
        if (debug.get()) info("Stage 2: Attacking with instant item + swapping to lunge spear.");

        // Step 1: Trigger an attack while holding the instant item (no cooldown)
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
            Entity target = entityHit.getEntity();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (debug.get()) info("Attacked entity: " + target.getName().getString());
        } else {
            mc.player.swingHand(Hand.MAIN_HAND);
            if (debug.get()) info("No entity targeted — just swinging.");
        }

        // Step 2: Immediately swap to lunge spear (same tick, exploit)
        mc.player.getInventory().setSelectedSlot(lungeSpearSlot);
        if (debug.get()) info("Swapped to lunge spear at slot " + (lungeSpearSlot + 1));

        // Determine if we need a swap-back tick
        if (swapBackMode.get() == SwapBackMode.None) {
            // Done: stay on lunge spear and toggle off
            if (debug.get()) info("Swap-back disabled. Staying on lunge spear. Toggling off.");
            stage = 0;
            toggle();
        } else {
            // Need one more tick for swap-back
            stage = 3;
            if (debug.get()) info("Will swap back next tick.");
        }
    }

    // ---- Stage 3: Final slot swap ----
    private void handleSwapBack() {
        if (debug.get()) info("Stage 3: Handling final swap-back.");

        switch (swapBackMode.get()) {
            case Previous -> {
                if (prevSlot >= 0 && prevSlot < 9) {
                    mc.player.getInventory().setSelectedSlot(prevSlot);
                    if (debug.get()) info("Swapped back to previous slot: " + (prevSlot + 1));
                }
            }
            case Configured -> {
                int slot = swapBackSlot.get() - 1;
                if (slot >= 0 && slot < 9) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    if (debug.get()) info("Swapped to configured slot: " + (slot + 1));
                }
            }
        }

        stage = 0;
        toggle();
    }

    // ---- Helper: Check if holding an instant-cooldown item ----
    private boolean isHoldingInstantItem() {
        if (mc.player == null) return false;
        return isInstantItem(mc.player.getMainHandStack());
    }

    // ---- Helper: Identify instant-cooldown items ----
    private boolean isInstantItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.isOf(Items.WIND_CHARGE)
            || stack.isOf(Items.GOLDEN_APPLE)
            || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
    }

    // ---- Helper: Find instant item slot in hotbar ----
    private int findInstantItemSlot() {
        if (instantMode.get() == InstantMode.Slot) {
            int slot = instantSlot.get() - 1;
            if (slot >= 0 && slot < 9) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (isInstantItem(stack)) return slot;
                if (debug.get()) error("Configured instant slot " + (slot + 1) + " does not contain an instant item.");
            }
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (isInstantItem(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    // ---- Helper: Find lunge-enchanted spear slot ----
    private int findLungeSpearSlot() {
        if (lungeMode.get() == LungeMode.Slot) {
            int slot = lungeSlot.get() - 1;
            if (slot >= 0 && slot < 9) {
                if (hasLungeEnchant(mc.player.getInventory().getStack(slot))) return slot;
                if (debug.get()) error("Configured lunge slot " + (slot + 1) + " does not have a lunge enchantment.");
            }
            return -1;
        }

        int bestSlot = -1;
        int highestLevel = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String enchantString = stack.getEnchantments().toString();
                if (debug.get()) info("Slot " + (i + 1) + " enchants: " + enchantString);

                if (enchantString.contains("lunge") || enchantString.contains("minecraft:lunge")) {
                    try {
                        int levelStart = enchantString.lastIndexOf("=>");
                        if (levelStart != -1) {
                            String levelStr = enchantString.substring(levelStart + 2).replaceAll("[^0-9]", "");
                            int level = Integer.parseInt(levelStr);
                            if (debug.get()) info("Found lunge level " + level + " in slot " + (i + 1));
                            if (level > highestLevel) {
                                highestLevel = level;
                                bestSlot = i;
                            }
                        }
                    } catch (Exception e) {
                        if (debug.get()) error("Error parsing enchant level: " + e.getMessage());
                    }
                }
            }
        }
        return bestSlot;
    }

    // ---- Helper: Check if item has lunge enchant ----
    private boolean hasLungeEnchant(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String enchantString = stack.getEnchantments().toString();
        return enchantString.contains("lunge") || enchantString.contains("minecraft:lunge");
    }
}