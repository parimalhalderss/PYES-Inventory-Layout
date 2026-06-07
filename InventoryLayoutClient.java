package com.codex.inventorylayout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class InventoryLayoutClient implements ClientModInitializer {

    // ── Singleton ─────────────────────────────────────────────────────────────
    public static InventoryLayoutClient INSTANCE;

    // ── Gson ──────────────────────────────────────────────────────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Storage ───────────────────────────────────────────────────────────────
    private Path saveFile;
    // kit name → list of 36 item NBT strings (null = empty slot)
    private Map<String, List<String>> layouts = new LinkedHashMap<>();

    // ── Keybind ───────────────────────────────────────────────────────────────
    private KeyBinding openGuiKey;

    // ── Apply state ───────────────────────────────────────────────────────────
    public enum ApplyResult { SUCCESS, NOT_FOUND, BUSY }
    private boolean applying = false;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        // Save file: .minecraft/config/pyes-inventory-layouts.json
        saveFile = MinecraftClient.getInstance()
                .runDirectory.toPath()
                .resolve("config")
                .resolve("pyes-inventory-layouts.json");
        loadLayouts();

        // Register keybind  (default: O)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inventory-layout.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "PYES Inventory Layout"
        ));

        // Tick: open GUI when key pressed
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new LayoutScreen(this));
            }
        });
    }

    // ── Public API (called by LayoutScreen) ───────────────────────────────────

    /** Returns an unmodifiable view of saved kit names → slot data. */
    public Map<String, List<String>> getLayouts() {
        return Collections.unmodifiableMap(layouts);
    }

    /** Save current player inventory as a named kit. */
    public void saveLayout(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<String> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                slots.add(null);
            } else {
                // Serialize to NBT string
                slots.add(stack.encode(client.player.getRegistryManager()).toString());
            }
        }
        layouts.put(name, slots);
        saveLayouts();
    }

    /** Apply a saved kit to the player inventory (Creative mode only). */
    public ApplyResult applyLayout(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return ApplyResult.BUSY;
        if (applying) return ApplyResult.BUSY;

        List<String> slots = layouts.get(name);
        if (slots == null) return ApplyResult.NOT_FOUND;

        // Only works in Creative
        if (!client.player.getAbilities().creativeMode) {
            client.player.sendMessage(
                Text.literal("§cNeed Creative mode to apply kits!"), false);
            return ApplyResult.BUSY;
        }

        applying = true;
        try {
            for (int i = 0; i < Math.min(slots.size(), 36); i++) {
                String nbt = slots.get(i);
                if (nbt == null || nbt.isEmpty()) {
                    client.player.getInventory().setStack(i, ItemStack.EMPTY);
                } else {
                    try {
                        var tag = net.minecraft.nbt.StringNbtReader.parse(nbt);
                        ItemStack stack = ItemStack.fromNbt(
                                client.player.getRegistryManager(), tag).orElse(ItemStack.EMPTY);
                        client.player.getInventory().setStack(i, stack);
                    } catch (Exception e) {
                        client.player.getInventory().setStack(i, ItemStack.EMPTY);
                    }
                }
            }
            client.player.playerScreenHandler.sendContentUpdates();
        } finally {
            applying = false;
        }
        return ApplyResult.SUCCESS;
    }

    /** Delete a saved kit. */
    public void deleteLayout(String name) {
        layouts.remove(name);
        saveLayouts();
    }

    /** Rename a kit. */
    public boolean renameLayout(String oldName, String newName) {
        if (!layouts.containsKey(oldName) || newName == null || newName.isBlank()) return false;
        List<String> data = layouts.remove(oldName);
        layouts.put(newName, data);
        saveLayouts();
        return true;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadLayouts() {
        if (!Files.exists(saveFile)) return;
        try (Reader r = Files.newBufferedReader(saveFile)) {
            Type type = new TypeToken<LinkedHashMap<String, List<String>>>(){}.getType();
            Map<String, List<String>> loaded = GSON.fromJson(r, type);
            if (loaded != null) layouts = loaded;
        } catch (Exception e) {
            System.err.println("[PYES] Could not load layouts: " + e.getMessage());
        }
    }

    public void saveLayouts() {
        try {
            Files.createDirectories(saveFile.getParent());
            try (Writer w = Files.newBufferedWriter(saveFile)) {
                GSON.toJson(layouts, w);
            }
        } catch (Exception e) {
            System.err.println("[PYES] Could not save layouts: " + e.getMessage());
        }
    }
}
