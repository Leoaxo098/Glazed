package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ShieldBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStandard = settings.createGroup("Standard");
    private final SettingGroup sgStun = settings.createGroup("Stun Shield");
    private final SettingGroup sgAttribute = settings.createGroup("Attribute Swap");
    private final SettingGroup sgRetry = settings.createGroup("Retry");

    // ==================== GENERAL SETTINGS ====================

    // 1 = Standard, 2 = Stun, 3 = Attribute
    private final Setting<Integer> breakMode = sgGeneral.add(new IntSetting.Builder()
        .name("break-mode")
        .description("1 = Standard, 2 = Stun Shield, 3 = Attribute Swap")
        .defaultValue(1)
        .range(1, 3)
        .sliderRange(1, 3)
        .build()
    );

    private final Setting<Boolean> onlyWhenHittable = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-hittable")
        .description("Only run when the target player is in your crosshair and hittable.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Only break shields of players, not other entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum range to detect shield usage.")
        .defaultValue(6.0)
        .range(0.0, 10.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send info messages to chat.")
        .defaultValue(true)
        .build()
    );

    // ==================== STANDARD MODE SETTINGS ====================

    private final Setting<Boolean> autoBreak = sgStandard.add(new BoolSetting.Builder()
        .name("auto-break")
        .description("Automatically break shields without requiring clicks.")
        .defaultValue(true)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Boolean> returnToPrevSlot = sgStandard.add(new BoolSetting.Builder()
        .name("return-to-prev-slot")
        .description("Return to the previous slot after breaking shield instead of a specific weapon slot.")
        .defaultValue(true)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Integer> weaponSlot = sgStandard.add(new IntSetting.Builder()
        .name("weapon-slot")
        .description("The hotbar slot to switch back to after breaking shield (1-9).")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(() -> breakMode.get() == 1 && !returnToPrevSlot.get())
        .build()
    );

    private final Setting<Integer> attackDelay = sgStandard.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Delay in ticks between shield break and weapon switch.")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(0, 20)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Integer> killDelay = sgStandard.add(new IntSetting.Builder()
        .name("kill-delay")
        .description("Delay in ticks between weapon switch and kill attack.")
        .defaultValue(1)
        .range(0, 40)
        .sliderRange(0, 20)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Integer> axeSwitchDelay = sgStandard.add(new IntSetting.Builder()
        .name("axe-switch-delay")
        .description("Delay in ticks to ensure axe switch is completed.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Integer> weaponSwitchDelay = sgStandard.add(new IntSetting.Builder()
        .name("weapon-switch-delay")
        .description("Delay in ticks to ensure weapon switch is completed.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Boolean> killSwitch = sgStandard.add(new BoolSetting.Builder()
        .name("kill-switch")
        .description("Enable auto attack after breaking shield.")
        .defaultValue(true)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Integer> cycleCooldown = sgStandard.add(new IntSetting.Builder()
        .name("cycle-cooldown")
        .description("Delay in ticks before starting the next shield break cycle.")
        .defaultValue(4)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(() -> breakMode.get() == 1)
        .build()
    );

    private final Setting<Integer> manualShieldBreakDelay = sgStandard.add(new IntSetting.Builder()
        .name("manual-shield-break-delay")
        .description("Delay in ticks before switching back in manual mode.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .visible(() -> breakMode.get() == 1 && !autoBreak.get())
        .build()
    );

    // ==================== STUN SHIELD SETTINGS ====================

    private final Setting<Integer> stunSwapBackDelay = sgStun.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Delay in ticks before swapping back to sword after stun sequence.")
        .defaultValue(8)
        .range(0, 40)
        .sliderRange(1, 20)
        .visible(() -> breakMode.get() == 2)
        .build()
    );

    private final Setting<Boolean> stunDoubleHit = sgStun.add(new BoolSetting.Builder()
        .name("double-hit")
        .description("Hit the target twice with axe (break + damage).")
        .defaultValue(true)
        .visible(() -> breakMode.get() == 2)
        .build()
    );

    private final Setting<Integer> stunSecondHitDelay = sgStun.add(new IntSetting.Builder()
        .name("second-hit-delay")
        .description("Delay in ticks between break hit and second axe hit.")
        .defaultValue(1)
        .range(0, 10)
        .sliderRange(0, 5)
        .visible(() -> breakMode.get() == 2 && stunDoubleHit.get())
        .build()
    );

    private final Setting<Integer> stunAxeSwitchDelay = sgStun.add(new IntSetting.Builder()
        .name("axe-switch-delay")
        .description("Delay in ticks to ensure axe switch is completed before attacking.")
        .defaultValue(0)
        .range(0, 10)
        .sliderRange(0, 5)
        .visible(() -> breakMode.get() == 2)
        .build()
    );

    // ==================== ATTRIBUTE SWAP SETTINGS ====================

    private final Setting<Integer> attrSwapBackDelay = sgAttribute.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Delay in ticks before swapping back to original weapon after attribute swap.")
        .defaultValue(8)
        .range(0, 40)
        .sliderRange(1, 20)
        .visible(() -> breakMode.get() == 3)
        .build()
    );

    private final Setting<Boolean> attrOnlyWhenSword = sgAttribute.add(new BoolSetting.Builder()
        .name("only-when-sword")
        .description("Only activate attribute swap when holding a sword.")
        .defaultValue(true)
        .visible(() -> breakMode.get() == 3)
        .build()
    );

    // ==================== RETRY SETTINGS ====================

    private final Setting<Integer> maxRetries = sgRetry.add(new IntSetting.Builder()
        .name("max-retries")
        .description("Maximum retry attempts when shield break fails.")
        .defaultValue(3)
        .range(0, 10)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> retryBaseDelay = sgRetry.add(new IntSetting.Builder()
        .name("retry-delay")
        .description("Base delay in ticks before retrying a failed shield break.")
        .defaultValue(5)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> incrementalRetryDelay = sgRetry.add(new BoolSetting.Builder()
        .name("incremental-delay")
        .description("Increase delay with each consecutive retry.")
        .defaultValue(true)
        .build()
    );

    // ==================== STATE VARIABLES ====================

    private PlayerEntity targetPlayer = null;
    private int originalSlot = -1;
    private int tickCounter = 0;
    private ShieldBreakerState state = ShieldBreakerState.IDLE;
    private boolean shieldBroken = false;
    private long lastBreakAttempt = 0;
    private int cooldownTicks = 0;
    private int retryCount = 0;
    private int retryCooldownTicks = 0;
    private boolean stunSecondHitDone = false;

    private enum ShieldBreakerState {
        IDLE,
        SWITCHING_AXE,
        BREAKING,
        STUN_HITTING,
        SWITCHING_BACK,
        KILLING,
        WAITING_RETRY
    }

    public ShieldBreaker() {
        super(GlazedAddon.pvp, "shield-breaker", "Automatically breaks player shields with axe using multiple methods.");
    }

    @Override
    public void onActivate() {
        resetFully();
        if (chatInfo.get()) info("Shield Breaker activated - Mode: " + getModeName());
    }

    @Override
    public void onDeactivate() {
        resetFully();
    }

    private String getModeName() {
        return switch (breakMode.get()) {
            case 1 -> "Standard";
            case 2 -> "Stun";
            case 3 -> "Attribute";
            default -> "Unknown";
        };
    }

    private boolean isMode(int mode) {
        return breakMode.get() == mode;
    }

    // ==================== MAIN TICK HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem()) return;

        if (retryCooldownTicks > 0) {
            retryCooldownTicks--;
            return;
        }

        if (state == ShieldBreakerState.WAITING_RETRY) {
            attemptRetry();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (isMode(1)) handleStandardTick();
        else if (isMode(2)) handleStunTick();
        else if (isMode(3)) handleAttributeTick();
    }

    // ==================== ATTACK EVENT HANDLER ====================

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem()) return;

        if (isMode(3)) handleAttributeAttack(event);
    }

    // ==================== STANDARD MODE ====================

    private void handleStandardTick() {
        switch (state) {
            case IDLE -> {
                if (autoBreak.get()) standardCheckTarget();
                else if (mc.options.attackKey.isPressed()) standardManualCheck();
            }
            case SWITCHING_AXE -> standardHandleAxeSwitch();
            case BREAKING -> standardHandleBreak();
            case SWITCHING_BACK -> standardHandleWeaponSwitch();
            case KILLING -> standardHandleKill();
            default -> {}
        }
    }

    private void standardManualCheck() {
        PlayerEntity target = getTargetFromCrosshair();
        if (target == null) return;
        if (!validateTargetForInit(target)) return;

        targetPlayer = target;
        originalSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());

        FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axeResult.found()) {
            if (chatInfo.get()) error("No axe found in hotbar!");
            return;
        }

        if (chatInfo.get()) info("Manual: Shield detected! Breaking with axe");
        InvUtils.swap(axeResult.slot(), false);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        tickCounter = 0;
        state = ShieldBreakerState.BREAKING;
    }

    private void standardCheckTarget() {
        PlayerEntity target = getTargetFromCrosshair();
        if (target == null) return;
        if (!validateTargetForInit(target)) return;

        targetPlayer = target;

        if (originalSlot == -1) {
            originalSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());
        }

        FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axeResult.found()) {
            if (chatInfo.get()) error("No axe found in hotbar!");
            return;
        }

        if (chatInfo.get()) info("Auto: Shield detected! Breaking with axe");
        InvUtils.swap(axeResult.slot(), false);
        state = ShieldBreakerState.SWITCHING_AXE;
        tickCounter = 0;
    }

    private void standardHandleAxeSwitch() {
        tickCounter++;
        if (tickCounter >= axeSwitchDelay.get()) {
            if (!validateTargetForAttack(true)) {
                if (chatInfo.get()) info("Target lost during axe switch, aborting.");
                resetState();
                return;
            }

            if (!shieldBroken) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBreakAttempt > 150) {
                    mc.interactionManager.attackEntity(mc.player, targetPlayer);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    lastBreakAttempt = currentTime;
                    shieldBroken = true;
                }
            }
            state = ShieldBreakerState.BREAKING;
            tickCounter = 0;
        }
    }

    private void standardHandleBreak() {
        tickCounter++;

        if (!autoBreak.get()) {
            if (tickCounter >= manualShieldBreakDelay.get()) {
                if (originalSlot != -1) {
                    InvUtils.swap(originalSlot, false);
                    if (validateTargetForAttack(false)) {
                        mc.interactionManager.attackEntity(mc.player, targetPlayer);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        if (chatInfo.get()) info("Manual: Attacking with original weapon!");
                    }
                }
                resetState();
            }
            return;
        }

        if (tickCounter >= 3) {
            if (!validateTargetForAttack(false)) {
                if (chatInfo.get()) info("Target lost after break attempt, aborting.");
                resetState();
                return;
            }

            if (isPlayerUsingShieldOnly(targetPlayer)) {
                handleFailedBreak();
            } else {
                if (chatInfo.get()) info("Shield broken! Switching to weapon...");
                if (returnToPrevSlot.get()) {
                    if (originalSlot != -1) InvUtils.swap(originalSlot, false);
                } else {
                    InvUtils.swap(weaponSlot.get() - 1, false);
                }
                state = ShieldBreakerState.SWITCHING_BACK;
                tickCounter = 0;
            }
        }
    }

    private void standardHandleWeaponSwitch() {
        tickCounter++;
        if (tickCounter >= weaponSwitchDelay.get()) {
            state = ShieldBreakerState.KILLING;
            tickCounter = 0;
        }
    }

    private void standardHandleKill() {
        tickCounter++;
        if (tickCounter >= killDelay.get()) {
            if (killSwitch.get()) {
                if (validateTargetForAttack(false)) {
                    mc.interactionManager.attackEntity(mc.player, targetPlayer);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    if (chatInfo.get()) info("Kill attack executed!");
                } else if (chatInfo.get()) {
                    info("Target lost before kill attack.");
                }
            } else if (chatInfo.get()) {
                info("Shield broken - Kill switch disabled");
            }
            resetState();
            cooldownTicks = cycleCooldown.get();
        }
    }

    // ==================== STUN SHIELD MODE ====================

    private void handleStunTick() {
        switch (state) {
            case IDLE -> stunCheckTarget();
            case SWITCHING_AXE -> stunHandleAxeSwitch();
            case BREAKING -> stunHandleBreak();
            case STUN_HITTING -> stunHandleSecondHit();
            case SWITCHING_BACK -> stunHandleSwapBack();
            default -> {}
        }
    }

    private void stunCheckTarget() {
        PlayerEntity target = getTargetFromCrosshair();
        if (target == null) return;
        if (!validateTargetForInit(target)) return;
        if (!isHoldingSword()) return;

        targetPlayer = target;

        if (originalSlot == -1) {
            originalSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());
        }

        FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axeResult.found()) {
            if (chatInfo.get()) error("No axe found in hotbar!");
            return;
        }

        if (chatInfo.get()) info("Stun: Shield detected! Starting stun sequence...");
        InvUtils.swap(axeResult.slot(), false);
        state = ShieldBreakerState.SWITCHING_AXE;
        tickCounter = 0;
        stunSecondHitDone = false;
    }

    private void stunHandleAxeSwitch() {
        tickCounter++;
        if (tickCounter >= stunAxeSwitchDelay.get()) {
            if (!validateTargetForAttack(true)) {
                if (chatInfo.get()) info("Target lost during axe switch, aborting.");
                resetState();
                return;
            }

            if (!shieldBroken) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBreakAttempt > 150) {
                    mc.interactionManager.attackEntity(mc.player, targetPlayer);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    lastBreakAttempt = currentTime;
                    shieldBroken = true;
                }
            }

            state = ShieldBreakerState.BREAKING;
            tickCounter = 0;
        }
    }

    private void stunHandleBreak() {
        tickCounter++;
        if (tickCounter < 3) return;

        if (!validateTargetForAttack(false)) {
            if (chatInfo.get()) info("Target lost after break attempt, aborting.");
            resetState();
            return;
        }

        if (isPlayerUsingShieldOnly(targetPlayer)) {
            handleFailedBreak();
            return;
        }

        if (chatInfo.get()) info("Stun: Shield broken!");
        if (stunDoubleHit.get()) {
            state = ShieldBreakerState.STUN_HITTING;
        } else {
            state = ShieldBreakerState.SWITCHING_BACK;
        }
        tickCounter = 0;
    }

    private void stunHandleSecondHit() {
        tickCounter++;
        if (tickCounter >= stunSecondHitDelay.get()) {
            if (validateTargetForAttack(false)) {
                mc.interactionManager.attackEntity(mc.player, targetPlayer);
                mc.player.swingHand(Hand.MAIN_HAND);
                if (chatInfo.get()) info("Stun: Second axe hit executed!");
            } else if (chatInfo.get()) {
                info("Target lost before second hit.");
            }
            stunSecondHitDone = true;
            state = ShieldBreakerState.SWITCHING_BACK;
            tickCounter = 0;
        }
    }

    private void stunHandleSwapBack() {
        tickCounter++;
        if (tickCounter >= stunSwapBackDelay.get()) {
            if (originalSlot != -1) {
                InvUtils.swap(originalSlot, false);
                if (chatInfo.get()) info("Stun: Swapped back to sword.");
            }
            resetState();
            cooldownTicks = 4;
        }
    }

    // ==================== ATTRIBUTE SWAP MODE ====================

    private void handleAttributeTick() {
        if (state == ShieldBreakerState.SWITCHING_BACK && shieldBroken) {
            tickCounter++;
            if (tickCounter >= attrSwapBackDelay.get()) {
                if (originalSlot != -1) {
                    InvUtils.swap(originalSlot, false);
                    if (chatInfo.get()) info("Attribute: Swapped back to original weapon.");
                }
                resetState();
            }
        }
    }

    private void handleAttributeAttack(AttackEntityEvent event) {
        Entity target = event.entity;

        if (onlyPlayers.get() && !(target instanceof PlayerEntity)) return;
        if (!(target instanceof PlayerEntity player)) return;
        if (mc.player.distanceTo(player) > range.get()) return;
        if (!isPlayerUsingShieldOnly(player)) return;
        if (onlyWhenHittable.get() && !isTargetInCrosshair(player)) return;
        if (isPlayerBehindTarget(mc.player, player)) {
            if (chatInfo.get()) info("Cannot break shield from behind!");
            return;
        }

        if (attrOnlyWhenSword.get() && !isHoldingSword()) return;
        if (retryCooldownTicks > 0) return;

        targetPlayer = player;
        originalSlot = com.nnpg.glazed.utils.InventoryUtils.getSelectedSlot(mc.player.getInventory());

        FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axeResult.found()) {
            if (chatInfo.get()) error("No axe found in hotbar!");
            return;
        }

        if (chatInfo.get()) info("Attribute: Swapping to axe for shield break...");
        InvUtils.swap(axeResult.slot(), false);

        shieldBroken = true;
        state = ShieldBreakerState.SWITCHING_BACK;
        tickCounter = 0;
    }

    // ==================== RETRY SYSTEM ====================

    private void attemptRetry() {
        if (targetPlayer == null || targetPlayer.isRemoved()) {
            if (chatInfo.get()) info("Retry aborted - target gone.");
            resetState();
            return;
        }
        if (mc.player.distanceTo(targetPlayer) > range.get()) {
            if (chatInfo.get()) info("Retry aborted - target out of range.");
            resetState();
            return;
        }
        if (onlyWhenHittable.get() && !isTargetInCrosshair(targetPlayer)) {
            if (chatInfo.get()) info("Retry aborted - target not in crosshair.");
            resetState();
            return;
        }
        if (!isPlayerUsingShieldOnly(targetPlayer)) {
            if (chatInfo.get()) info("Retry aborted - target no longer using shield.");
            resetState();
            return;
        }
        if (isPlayerBehindTarget(mc.player, targetPlayer)) {
            if (chatInfo.get()) info("Retry aborted - behind target.");
            resetState();
            return;
        }

        FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axeResult.found()) {
            if (chatInfo.get()) error("No axe found for retry!");
            resetState();
            return;
        }

        if (chatInfo.get()) info("Retrying shield break... (Attempt " + retryCount + "/" + maxRetries.get() + ")");
        InvUtils.swap(axeResult.slot(), false);
        state = ShieldBreakerState.SWITCHING_AXE;
        tickCounter = 0;
        shieldBroken = false;
    }

    private void handleFailedBreak() {
        retryCount++;
        if (retryCount > maxRetries.get()) {
            if (chatInfo.get()) error("Shield break failed after " + maxRetries.get() + " retries. Giving up.");
            resetState();
            return;
        }

        int delay = retryBaseDelay.get();
        if (incrementalRetryDelay.get()) {
            delay += (retryCount - 1) * 2;
        }

        retryCooldownTicks = delay;
        state = ShieldBreakerState.WAITING_RETRY;
        tickCounter = 0;
        shieldBroken = false;

        if (chatInfo.get()) warning("Shield break failed, retrying in " + delay + " ticks... (Attempt " + retryCount + "/" + maxRetries.get() + ")");
    }

    // ==================== VALIDATION UTILITIES ====================

    private boolean isHoldingSword() {
        String heldItemId = mc.player.getMainHandStack().getItem().toString();
        return heldItemId.contains("sword");
    }

    private PlayerEntity getTargetFromCrosshair() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
        if (!(entityHit.getEntity() instanceof PlayerEntity player)) {
            return null;
        }
        if (mc.player.distanceTo(player) > range.get()) return null;
        return player;
    }

    private boolean validateTargetForInit(PlayerEntity player) {
        if (onlyWhenHittable.get() && !isTargetInCrosshair(player)) return false;
        if (!isUsingShield(player)) return false;
        return true;
    }

    private boolean validateTargetForAttack(boolean requireShield) {
        if (targetPlayer == null || targetPlayer.isRemoved()) return false;
        if (mc.player.distanceTo(targetPlayer) > range.get()) return false;
        if (onlyWhenHittable.get() && !isTargetInCrosshair(targetPlayer)) return false;
        if (requireShield && !isPlayerUsingShieldOnly(targetPlayer)) return false;
        if (isPlayerBehindTarget(mc.player, targetPlayer)) return false;
        return true;
    }

    private boolean isTargetInCrosshair(PlayerEntity target) {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return false;
        }
        EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
        return entityHit.getEntity() == target;
    }

    private boolean isPlayerUsingShieldOnly(PlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() == Items.SHIELD && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND) {
            return true;
        }
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() == Items.SHIELD && player.isUsingItem() && player.getActiveHand() == Hand.OFF_HAND) {
            return true;
        }
        return false;
    }

    private boolean isUsingShield(PlayerEntity player) {
        if (isPlayerBehindTarget(mc.player, player)) {
            if (chatInfo.get()) info("Cannot break shield from behind!");
            return false;
        }
        return isPlayerUsingShieldOnly(player);
    }

    private boolean isPlayerBehindTarget(PlayerEntity source, PlayerEntity target) {
        double dx = source.getX() - target.getX();
        double dz = source.getZ() - target.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        float targetYaw = target.getYaw() % 360;
        if (targetYaw < 0) targetYaw += 360;
        if (angle < 0) angle += 360;
        double angleDiff = Math.abs(angle - targetYaw);
        return angleDiff < 90 || angleDiff > 270;
    }

    // ==================== STATE MANAGEMENT ====================

    private void resetState() {
        targetPlayer = null;
        originalSlot = -1;
        tickCounter = 0;
        state = ShieldBreakerState.IDLE;
        shieldBroken = false;
        lastBreakAttempt = 0;
        stunSecondHitDone = false;
        retryCount = 0;
    }

    private void resetFully() {
        resetState();
        retryCooldownTicks = 0;
        cooldownTicks = 0;
    }

    @Override
    public String getInfoString() {
        if (retryCooldownTicks > 0) return "Retrying in " + retryCooldownTicks;
        return switch (state) {
            case IDLE -> null;
            case SWITCHING_AXE -> "Switching to Axe";
            case BREAKING -> "Breaking Shield";
            case STUN_HITTING -> "Stun Hit";
            case SWITCHING_BACK -> "Switching Back";
            case KILLING -> "Executing Kill";
            case WAITING_RETRY -> "Waiting Retry";
        };
    }
}