package com.nnpg.glazed.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ToastCompat {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void show(Item item, String title, String message) {
        showInternal(item, title, message);
    }

    public static void show(String title, String message) {
        showInternal(null, title, message);
    }

    private static void showInternal(Item item, String title, String message) {
        try {
            // Try to use MeteorToast via reflection so we remain binary compatible across Meteor versions
            Class<?> toastClass = Class.forName("meteordevelopment.meteorclient.utils.render.MeteorToast");
            Class<?> builderClass = null;
            for (Class<?> inner : toastClass.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Builder")) {
                    builderClass = inner;
                    break;
                }
            }

            Object builder = null;
            if (builderClass != null) {
                // Try constructors for Builder
                for (Constructor<?> c : builderClass.getConstructors()) {
                    Class<?>[] params = c.getParameterTypes();
                    if (params.length == 1) {
                        if (params[0].isAssignableFrom(Item.class)) {
                            builder = c.newInstance(item);
                            break;
                        } else if (params[0].getName().contains("ItemStack")) {
                            builder = c.newInstance(new ItemStack(item));
                            break;
                        }
                    }
                }

                // If we couldn't construct builder via constructor try static builder(Item)
                if (builder == null) {
                    for (Method m : toastClass.getMethods()) {
                        if (m.getName().equalsIgnoreCase("builder") && m.getParameterCount() == 1) {
                            builder = m.invoke(null, item);
                            break;
                        }
                    }
                }

                if (builder != null) {
                    // Try to set title/message via common method names
                    for (Method m : builderClass.getMethods()) {
                        String n = m.getName().toLowerCase();
                        if ((n.contains("title") || n.contains("name")) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(String.class)) {
                            try { m.invoke(builder, title); } catch (Exception ignored) {}
                        }
                        if ((n.contains("message") || n.contains("text") || n.contains("desc")) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(String.class)) {
                            try { m.invoke(builder, message); } catch (Exception ignored) {}
                        }
                    }

                    // Build the toast
                    Object toast = null;
                    for (Method m : builderClass.getMethods()) {
                        if ((m.getName().equalsIgnoreCase("build") || m.getName().equalsIgnoreCase("create")) && m.getParameterCount() == 0) {
                            toast = m.invoke(builder);
                            break;
                        }
                    }

                    if (toast != null) {
                        // Add toast to toast manager
                        ToastManager manager = mc.getToastManager();
                        Method addMethod = null;
                        for (Method m : manager.getClass().getMethods()) {
                            if (m.getName().equals("add")) {
                                addMethod = m;
                                break;
                            }
                        }
                        if (addMethod != null) addMethod.invoke(manager, toast);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall back to chat message
        }

        // Fallback: send chat message
        try {
            if (mc.player != null) mc.player.sendMessage(Text.literal("[" + title + "] " + message), false);
        } catch (Throwable ignored) {}
    }
}
