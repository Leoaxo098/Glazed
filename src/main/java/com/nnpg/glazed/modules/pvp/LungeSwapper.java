package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

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
    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Delay in ticks before starting the sequence.")
        .sliderRange(0, 10)
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print debug messages in chat.")
        .defaultValue(false)
        .build()
    );

    // ---- State ----
    private int prevSlot = -1;
    private int stage = 0;          // 0=waiting, 1=swap to instant, 2=attack+swap, 3=final swap
    private int tickCounter = 0;

    public LungeSwapper() {
        super(GlazedAddon.pvp, "lunge-swapper", "Swaps to an instant-cooldown item, left-clicks, then swaps to a lunge-enchanted spear in the same tick for the attribute swap exploit.");
    }

    // ---- Enums ----
    public enum InstantMode {
        Auto,
        Slot
    }

    public enum LungeMode {
        Auto,
        Slot
    }

    public enum SwapBackMode {
        None,
        Previous,
        Configured
    }

    // ---- Activation ----
    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        prevSlot = mc.player.getInventory().getSelectedSlot();

        // Check if we already hold an instant-cooldown item
        if (isHoldingInstantItem()) {
            if (debug.get()) info("Already holding instant item, going straight to attack phase.");
            stage = 2; // Skip swap, go straight to attack+swap
        } else {
            stage = 1; // Need to swap first
        }

        tickCounter = 0;
    }

    // ---- Tick Handler ----
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // Delay phase
        if (tickCounter < delayTicks.get()) {
            tickCounter++;
            return;
        }

        switch (stage) {
            case 1 -> handleSwapToInstant();
            case 2 -> handleAttackAndSwapToLunge();
            case 3 -> handleFinalSwap();
            default -> toggle();
        }
    }

    // ---- Stage 1: Swap to instant item ----
    private void handleSwapToInstant() {
        if (debug.get()) info("Stage 1: Swapping to instant item.");

        int instantItemSlot = findInstantItemSlot();
        if (instantItemSlot == -1) {
            error("No instant-cooldown item (wind charge / golden apple) found in hotbar.");
            toggle();
            return;
        }

        // If already holding it, skip directly to attack phase
        if (mc.player.getInventory().getSelectedSlot() == instantItemSlot) {
            if (debug.get()) info("Already on instant slot, skipping to attack phase.");
            stage = 2;
            return;
        }

        InvUtils.swap(instantItemSlot, false);
        stage = 2; // Next tick will do attack+swap
    }

    // ---- Stage 2: Attack + Swap to Lunge (same tick) ----
    private void handleAttackAndSwapToLunge() {
        if (debug.get()) info("Stage 2: Attacking + swapping to lunge spear (same tick).");

        int lungeSpearSlot = findLungeSpearSlot();
        if (lungeSpearSlot == -1) {
            error("No lunge-enchanted spear found in hotbar.");
            toggle();
            return;
        }

        // --- The attribute swap exploit: attack + swap in the same tick ---

        // 1. Left-click swing (simulate attack using attack key)
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.options.attackKey.setPressed(true);

        // 2. Immediately swap to lunge spear while the attack is being processed
        InvUtils.swap(lungeSpearSlot, false);

        // Release attack key (will register on next game cycle, but the swap already happened)
        mc.options.attackKey.setPressed(false);

        if (debug.get()) info("Attack + swap to lunge spear executed in same tick.");

        stage = 3; // Next tick handles optional swap back
    }

    // ---- Stage 3: Optional swap back ----
    private void handleFinalSwap() {
        if (debug.get()) info("Stage 3: Handling final swap.");

        switch (swapBackMode.get()) {
            case Previous -> {
                if (prevSlot != -1 && prevSlot >= 0 && prevSlot < 9) {
                    InvUtils.swap(prevSlot, false);
                    if (debug.get()) info("Swapped back to previous slot: " + (prevSlot + 1));
                }
            }
            case Configured -> {
                int slot = swapBackSlot.get() - 1;
                if (slot >= 0 && slot < 9) {
                    InvUtils.swap(slot, false);
                    if (debug.get()) info("Swapped to configured slot: " + (slot + 1));
                }
            }
            case None -> {
                if (debug.get()) info("Staying on lunge spear.");
            }
        }

        if (debug.get()) info("Lunge Swapper sequence complete. Toggling off.");

        // Reset and disable
        reset();
        toggle();
    }

    // ---- Helper: Check if holding an instant-cooldown item ----
    private boolean isHoldingInstantItem() {
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandStack();
        return isInstantItem(held);
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

        // Auto: scan hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isInstantItem(stack)) return i;
        }
        return -1;
    }

    // ---- Helper: Find lunge-enchanted spear slot ----
    private int findLungeSpearSlot() {
        if (lungeMode.get() == LungeMode.Slot) {
            int slot = lungeSlot.get() - 1;
            if (slot >= 0 && slot < 9) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (hasLungeEnchant(stack)) return slot;
                if (debug.get()) error("Configured lunge slot " + (slot + 1) + " does not have a lunge enchantment.");
            }
            return -1;
        }

        // Auto: scan hotbar for lunge enchantment
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

    // ---- Reset state ----
    private void reset() {
        stage = 0;
        tickCounter = 0;
        prevSlot = -1;
    }
}