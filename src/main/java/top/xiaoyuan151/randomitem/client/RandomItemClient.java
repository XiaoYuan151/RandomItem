package top.xiaoyuan151.randomitem.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomItemClient implements ClientModInitializer {

    private int randomizeTime = 1200; // Default to 60 seconds (20 ticks per second)
    private final Random random = new Random();
    private boolean isReplacing = false;
    private int countdown = 0;
    private boolean gameRunning = false;

    @Override
    public void onInitializeClient() {
        // Register commands for setting randomize time, starting, and stopping item replacement
        registerCommands();

        // Register client tick event for performing random item replacement
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (gameRunning && isReplacing) {
                if (countdown > 0 && countdown % 100 == 0) { // Every 5 seconds (100 ticks)
                    int secondsLeft = countdown / 20;
                    if (secondsLeft <= 20 && secondsLeft % 5 == 0) {
                        MinecraftClient.getInstance().player.sendMessage(new LiteralText("Replacement in " + secondsLeft + " seconds"), false);
                    }
                }

                if (countdown <= 0) {
                    if (!replaceItem()) {
                        MinecraftClient.getInstance().player.sendMessage(new LiteralText("No item found to replace!"), false);
                    }
                    countdown = randomizeTime; // Reset countdown
                }

                countdown--;
            }
        });

        // Register client lifecycle event for tracking game state
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (MinecraftClient.getInstance().world != null && !gameRunning) {
                gameRunning = true;
            } else if (MinecraftClient.getInstance().world == null && gameRunning) {
                gameRunning = false;
                isReplacing = false; // Stop replacing items when game stops
            }
        });
    }

    private void registerCommands() {
        // Command to set randomize time
        ClientCommandManager.DISPATCHER.register(
                LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource>literal("setrndtime")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(ClientCommandManager.argument("time", IntegerArgumentType.integer(1))
                                .executes(this::setRandomizeTime))
        );

        // Command to start random item replacement
        ClientCommandManager.DISPATCHER.register(
                LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource>literal("startrnditem")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            isReplacing = true;
                            countdown = 0; // Reset countdown to start immediately
                            MinecraftClient.getInstance().player.sendMessage(new LiteralText("Starting item replacement!"), false);
                            return 1;
                        })
        );

        // Command to stop random item replacement
        ClientCommandManager.DISPATCHER.register(
                LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource>literal("stoprnditem")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            isReplacing = false;
                            MinecraftClient.getInstance().player.sendMessage(new LiteralText("Stopping item replacement!"), false);
                            return 1;
                        })
        );
    }

    private int setRandomizeTime(CommandContext<net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource> context) {
        int time = IntegerArgumentType.getInteger(context, "time");
        MinecraftClient.getInstance().player.sendMessage(new LiteralText("Randomize time set to " + time + " seconds."), false);
        randomizeTime = time * 20; // Convert seconds to ticks
        countdown = randomizeTime; // Immediately apply new time
        return 1;
    }

    private boolean replaceItem() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return false;
        }

        // Randomly select an item from player's inventory excluding main hand, off hand, and armor slots
        List<Integer> inventorySlots = getInventorySlotsWithoutEquippedItems(player.inventory);
        if (inventorySlots.isEmpty()) {
            return false;
        }

        while (!inventorySlots.isEmpty()) {
            int slot = inventorySlots.remove(random.nextInt(inventorySlots.size()));
            ItemStack itemToReplace = player.inventory.getStack(slot);

            if (!itemToReplace.isEmpty() && itemToReplace.getItem() != Items.AIR) {
                // Randomly select a replacement item from all Minecraft items
                Identifier replacementItemId = getRandomReplacementItem();

                // Ensure the replacement item is not air
                while (Registry.ITEM.get(replacementItemId) == Items.AIR) {
                    replacementItemId = getRandomReplacementItem();
                }

                ItemStack replacementStack = new ItemStack(Registry.ITEM.get(replacementItemId), itemToReplace.getCount());

                // Remove old item and add the replacement item
                player.inventory.removeStack(slot);
                player.inventory.setStack(slot, replacementStack);

                // Send packet to update client
                ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;
                if (interactionManager != null) {
                    interactionManager.clickCreativeStack(replacementStack, slot);
                }
                player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(player.inventory.selectedSlot));

                // Update inventory to ensure the item is usable without reopening
                player.inventory.markDirty();

                // Notify player about the replacement
                MinecraftClient.getInstance().player.sendMessage(new LiteralText("Your item " + itemToReplace.getName().getString() + " has been replaced with " + replacementStack.getName().getString() + "!"), false);

                return true;
            }
        }
        return false;
    }

    private List<Integer> getInventorySlotsWithoutEquippedItems(PlayerInventory inventory) {
        List<Integer> inventorySlots = new ArrayList<>();
        // 9-35 are the inventory slots, excluding hotbar (0-8) and armor/offhand (36-40)
        for (int i = 9; i < 36; i++) {
            if (!isEquippedSlot(i, inventory)) {
                inventorySlots.add(i);
            }
        }
        return inventorySlots;
    }

    private boolean isEquippedSlot(int slot, PlayerInventory inventory) {
        return slot == inventory.selectedSlot ||
                slot == 40 || // Offhand slot
                slot >= 36 && slot <= 39; // Armor slots
    }

    private Identifier getRandomReplacementItem() {
        List<Identifier> allItems = new ArrayList<>(Registry.ITEM.getIds());
        return allItems.get(random.nextInt(allItems.size()));
    }
}
