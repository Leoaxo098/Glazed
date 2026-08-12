package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.DoAttackEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;

public class LungeSwapper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ---- Trigger Slot ----
    private final Setting<Integer> triggerSlot = sgGeneral.add(new IntSetting.Builder()
        .name("trigger-slot")
        .description("The hotbar slot (1-9) to hold when attacking. When you attack while this slot is selected, it will swap to the lunge spear.")
        .sliderRange(1, 9)
        .defaultValue(1)
        .min(1)
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

    // ---- Charge Delay ----
    private final Setting<Integer> chargeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("charge-delay")
        .description("Ticks to wait after equipping instant item before swap is allowed (cooldown charging time).")
        .sliderRange(0, 40)
        .defaultValue(0)
        .min(0)
        .build()
    );

    // ---- Swap Back ----
    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to the instant slot after a delay.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapBackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Delay in ticks before swapping back to the instant slot.")
        .defaultValue(2)
        .min(0)
        .max(100)
        .sliderRange(0, 20)
        .visible(swapBack::get)
        .build()
    );

    // ---- General ----
    private final Setting<Boolean> swapOnMiss = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-on-miss")
        .description("Swap even when attacking the air (for lunge travelling).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print debug messages in chat.")
        .defaultValue(false)
        .build()
    );

    // ---- State ----
    private int chargeTimer = 0;
    private boolean charging = false;
    private int backTimer = 0;
    private boolean awaitingBack = false;
    private int lastHeldSlot = -1;

    public LungeSwapper() {
        super(GlazedAddon.pvp, "lunge-swapper", "When you attack while the configured trigger slot is selected, swaps to a lunge-enchanted spear for the attribute swap exploit. Auto-returns to the trigger slot.");
    }

    // ---- Enums ----
    public enum LungeMode { Auto, Slot }

    // ---- Attack event (swing in air) ----
    @EventHandler
    private void onAttack(DoAttackEvent event) {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) return;
        if (!isHoldingValidInstantItem()) return;
        if (!swapOnMiss.get()) return;

        performSwap();
    }

    // ---- Attack entity event ----
    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!isHoldingValidInstantItem()) return;
        if (swapOnMiss.get()) return;

        performSwap();
    }

    // ---- Core swap logic ----
    private void performSwap() {
        if (awaitingBack) return;

        // Check charge delay
        if (charging) {
            if (chargeTimer < chargeDelay.get()) {
                chargeTimer++;
                if (debug.get()) info("Charging... " + chargeTimer + "/" + chargeDelay.get());
                return;
            }
            charging = false;
        } else if (chargeDelay.get() > 0) {
            // Start charging
            charging = true;
            chargeTimer = 0;
            if (debug.get()) info("Started charging (delay: " + chargeDelay.get() + " ticks).");
            return;
        }

        // Find lunge spear slot
        int spearSlot = findLungeSpearSlot();
        if (spearSlot == -1) {
            if (debug.get()) error("No lunge-enchanted spear found.");
            return;
        }

        if (spearSlot == mc.player.getInventory().getSelectedSlot()) {
            if (debug.get()) info("Already holding the spear.");
            return;
        }

        // Save current slot for swap-back
        lastHeldSlot = mc.player.getInventory().getSelectedSlot();

        // Swap to spear — InvUtils.swap handles the attribute swap via server packet
        if (debug.get()) info("Swapping to lunge spear at slot " + (spearSlot + 1));
        InvUtils.swap(spearSlot, false);

        // Start swap-back timer
        if (swapBack.get()) {
            awaitingBack = true;
            backTimer = swapBackDelay.get();
        }
    }

    // ---- Tick handler for swap-back ----
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!awaitingBack) return;

        if (backTimer-- > 0) return;

        // Swap back to the instant item slot
        if (lastHeldSlot >= 0 && lastHeldSlot < 9) {
            if (debug.get()) info("Swapping back to slot " + (lastHeldSlot + 1));
            InvUtils.swap(lastHeldSlot, false);
        }

        awaitingBack = false;
        lastHeldSlot = -1;
    }

    // ---- Check if holding the configured trigger slot ----
    private boolean isHoldingValidInstantItem() {
        if (mc.player == null) return false;

        int heldSlot = mc.player.getInventory().getSelectedSlot();
        int configSlot = triggerSlot.get() - 1;

        // Must be holding the configured slot
        return heldSlot == configSlot;
    }

    // ---- Helper: Find lunge-enchanted spear slot ----
    private int findLungeSpearSlot() {
        if (lungeMode.get() == LungeMode.Slot) {
            int slot = lungeSlot.get() - 1;
            if (slot >= 0 && slot < 9) {
                if (hasLungeEnchant(mc.player.getInventory().getStack(slot))) return slot;
                if (debug.get()) error("Configured lunge slot " + (slot + 1) + " has no lunge enchant.");
            }
            return -1;
        }

        // Auto: find best lunge spear
        int bestSlot = -1;
        int highestLevel = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                int lungeLevel = Utils.getEnchantmentLevel(stack, Enchantments.LUNGE);
                if (lungeLevel > 0) {
                    if (debug.get()) info("Slot " + (i + 1) + ": lunge level " + lungeLevel);
                    if (lungeLevel > highestLevel) {
                        highestLevel = lungeLevel;
                        bestSlot = i;
                    }
                }
            }
        }
        return bestSlot;
    }

    // ---- Helper: Check if item has lunge enchant ----
    private boolean hasLungeEnchant(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return Utils.getEnchantmentLevel(stack, Enchantments.LUNGE) > 0;
    }

    @Override
    public void onDeactivate() {
        chargeTimer = 0;
        charging = false;
        backTimer = 0;
        awaitingBack = false;
        lastHeldSlot = -1;
    }
}