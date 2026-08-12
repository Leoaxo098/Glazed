package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class StunMace extends Module {
    private final SettingGroup sg;

    private final Setting<Integer> axeSlot;
    private final Setting<Integer> maceSlot;
    private final Setting<Integer> fallDistance;
    private final Setting<Boolean> ignoreShield;
    private final Setting<Boolean> enableReturnSlot;
    private final Setting<Integer> returnSlot;
    private final Setting<DelayMode> delayMode;
    private final Setting<Integer> presetDelay;
    private final Setting<Integer> minRandomDelay;
    private final Setting<Integer> maxRandomDelay;
    private final Setting<Integer> comboDelay;

    private final MinecraftClient mc;

    private boolean comboActive = false;
    private PlayerEntity currentTarget = null;
    private long axeHitTime = 0;
    private long lastComboTime = 0;
    private boolean waitingForMace = false;

    public enum DelayMode {
        None,
        Preset,
        Random
    }

    public StunMace() {
        super(GlazedAddon.pvp, "stunmace", "Hit axe + mace and return to slot all in one tick.");

        sg = settings.createGroup("Settings");

        axeSlot = sg.add(new IntSetting.Builder()
            .name("axe-slot")
            .description("Hotbar slot (0-8) containing your axe.")
            .defaultValue(1)
            .min(0)
            .max(8)
            .build()
        );

        maceSlot = sg.add(new IntSetting.Builder()
            .name("mace-slot")
            .description("Hotbar slot (0-8) containing your mace.")
            .defaultValue(2)
            .min(0)
            .max(8)
            .build()
        );

        fallDistance = sg.add(new IntSetting.Builder()
            .name("fall-distance")
            .description("Minimum fall distance to trigger the combo.")
            .defaultValue(2)
            .min(1)
            .max(10)
            .build()
        );

        ignoreShield = sg.add(new BoolSetting.Builder()
            .name("ignore-shield")
            .description("Perform the combo on any player, not just those blocking with a shield.")
            .defaultValue(false)
            .build()
        );

        enableReturnSlot = sg.add(new BoolSetting.Builder()
            .name("return-to-slot")
            .description("Automatically return to a specific hotbar slot after combo.")
            .defaultValue(true)
            .build()
        );

        returnSlot = sg.add(new IntSetting.Builder()
            .name("return-slot")
            .description("Hotbar slot to return to after combo (0-8).")
            .defaultValue(0)
            .min(0)
            .max(8)
            .visible(() -> enableReturnSlot.get())
            .build()
        );

        comboDelay = sg.add(new IntSetting.Builder()
            .name("combo-cooldown")
            .description("Minimum delay in milliseconds between combo attempts.")
            .defaultValue(1000)
            .min(0)
            .max(5000)
            .sliderRange(0, 5000)
            .build()
        );

        delayMode = sg.add(new EnumSetting.Builder<DelayMode>()
            .name("delay-mode")
            .description("Delay type between axe and mace hits: None, Preset, or Random.")
            .defaultValue(DelayMode.None)
            .build()
        );

        presetDelay = sg.add(new IntSetting.Builder()
            .name("preset-delay")
            .description("Delay in milliseconds for preset mode.")
            .defaultValue(100)
            .min(0)
            .max(1000)
            .sliderRange(0, 1000)
            .visible(() -> delayMode.get() == DelayMode.Preset)
            .build()
        );

        minRandomDelay = sg.add(new IntSetting.Builder()
            .name("min-random-delay")
            .description("Minimum delay in milliseconds for random mode.")
            .defaultValue(50)
            .min(0)
            .max(1000)
            .sliderRange(0, 1000)
            .visible(() -> delayMode.get() == DelayMode.Random)
            .build()
        );

        maxRandomDelay = sg.add(new IntSetting.Builder()
            .name("max-random-delay")
            .description("Maximum delay in milliseconds for random mode.")
            .defaultValue(150)
            .min(0)
            .max(1000)
            .sliderRange(0, 1000)
            .visible(() -> delayMode.get() == DelayMode.Random)
            .build()
        );

        mc = MinecraftClient.getInstance();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ClientPlayerEntity player = mc.player;
        long currentTime = System.currentTimeMillis();

        // Reset combo if grounded
        if (player.isOnGround() && comboActive) {
            comboActive = false;
            currentTarget = null;
            waitingForMace = false;
            return;
        }

        // Handle mace hit if waiting with delay
        if (waitingForMace && currentTarget != null) {
            long elapsedTime = currentTime - axeHitTime;
            long requiredDelay = getRequiredDelay();

            if (elapsedTime >= requiredDelay) {
                // Mace hit
                InvUtils.swap(maceSlot.get(), false);
                mc.interactionManager.attackEntity(player, currentTarget);
                player.swingHand(Hand.MAIN_HAND);

                // Return to chosen slot immediately (same tick) if enabled
                if (enableReturnSlot.get()) {
                    InvUtils.swap(returnSlot.get(), false);
                }

                comboActive = false;
                waitingForMace = false;
                currentTarget = null;
                lastComboTime = currentTime;
            }
            return;
        }

        // Check combo cooldown
        if (currentTime - lastComboTime < comboDelay.get()) {
            return;
        }

        // Combo trigger logic
        if (!comboActive && player.fallDistance >= fallDistance.get()) {
            PlayerEntity target = getTarget();
            if (target != null && (ignoreShield.get() || target.isBlocking())) {
                comboActive = true;
                currentTarget = target;

                // Axe hit
                InvUtils.swap(axeSlot.get(), false);
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);

                // Set up delay for mace
                DelayMode mode = delayMode.get();
                if (mode == DelayMode.None) {
                    // No delay - hit mace immediately in the same tick
                    InvUtils.swap(maceSlot.get(), false);
                    mc.interactionManager.attackEntity(player, target);
                    player.swingHand(Hand.MAIN_HAND);

                    // Return to chosen slot immediately if enabled
                    if (enableReturnSlot.get()) {
                        InvUtils.swap(returnSlot.get(), false);
                    }

                    comboActive = false;
                    currentTarget = null;
                    lastComboTime = currentTime;
                } else {
                    // Preset or Random delay - wait for next tick
                    axeHitTime = currentTime;
                    waitingForMace = true;
                }
            }
        }
    }

    private long getRequiredDelay() {
        DelayMode mode = delayMode.get();
        if (mode == DelayMode.Preset) {
            return presetDelay.get();
        } else if (mode == DelayMode.Random) {
            int min = minRandomDelay.get();
            int max = maxRandomDelay.get();
            int range = max - min;
            return min + (long) (Math.random() * (range + 1));
        }
        return 0;
    }

    private PlayerEntity getTarget() {
        Entity targeted = mc.targetedEntity;
        if (targeted instanceof PlayerEntity player) {
            if (Friends.get().isFriend(player)) return null;
            return player;
        }
        return null;
    }
}