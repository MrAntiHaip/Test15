package com.dimaulyana.eightblades;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RouletteManager {
    private static final Random RNG = new Random();
    private static final Map<UUID, ActiveSpin> activeSpins = new HashMap<>();
    private static final Map<UUID, Long> freeSpinCooldowns = new HashMap<>();

    public record DropEntry(Item item, int count, String rarityColor, String tierName, int weight) {}

    private static final List<DropEntry> DROPS = new ArrayList<>();

    static {
        // 1. Ширпотреб (Consumer Grade, ~40% weight)
        DROPS.add(new DropEntry(Items.DIRT, 64, "§7", "Ширпотреб", 15));
        DROPS.add(new DropEntry(Items.COBBLESTONE, 64, "§7", "Ширпотреб", 15));
        DROPS.add(new DropEntry(Items.STICK, 32, "§7", "Ширпотреб", 12));
        DROPS.add(new DropEntry(Items.BONE, 16, "§7", "Ширпотреб", 12));
        DROPS.add(new DropEntry(Items.SAND, 32, "§7", "Ширпотреб", 12));
        DROPS.add(new DropEntry(Items.CACTUS, 16, "§7", "Ширпотреб", 10));

        // 2. Армейское (Mil-Spec Blue, ~30% weight)
        DROPS.add(new DropEntry(Items.IRON_INGOT, 16, "§9", "Армейское", 14));
        DROPS.add(new DropEntry(Items.COPPER_INGOT, 24, "§9", "Армейское", 14));
        DROPS.add(new DropEntry(Items.GOLD_INGOT, 16, "§9", "Армейское", 12));
        DROPS.add(new DropEntry(Items.REDSTONE, 32, "§9", "Армейское", 12));
        DROPS.add(new DropEntry(Items.LAPIS_LAZULI, 24, "§9", "Армейское", 12));
        DROPS.add(new DropEntry(Items.QUARTZ, 32, "§9", "Армейское", 10));

        // 3. Запрещенное (Restricted Purple, ~18% weight)
        DROPS.add(new DropEntry(Items.DIAMOND, 4, "§5", "Запрещенное", 8));
        DROPS.add(new DropEntry(Items.ENDER_PEARL, 8, "§5", "Запрещенное", 8));
        DROPS.add(new DropEntry(Items.OBSIDIAN, 12, "§5", "Запрещенное", 8));
        DROPS.add(new DropEntry(Items.GOLDEN_APPLE, 2, "§5", "Запрещенное", 6));
        DROPS.add(new DropEntry(Items.SCULK, 16, "§5", "Запрещенное", 6));
        DROPS.add(new DropEntry(Items.AMETHYST_SHARD, 16, "§5", "Запрещенное", 6));

        // 4. Засекреченное (Classified Pink, ~9% weight)
        DROPS.add(new DropEntry(Items.DIAMOND, 12, "§d", "Засекреченное", 5));
        DROPS.add(new DropEntry(Items.CRYING_OBSIDIAN, 12, "§d", "Засекреченное", 4));
        DROPS.add(new DropEntry(Items.ECHO_SHARD, 2, "§d", "Засекреченное", 3));
        DROPS.add(new DropEntry(Items.PRISMARINE_CRYSTALS, 16, "§d", "Засекреченное", 4));
        DROPS.add(new DropEntry(Items.WITHER_SKELETON_SKULL, 1, "§d", "Засекреченное", 3));

        // 5. Тайное / ЗОЛОТОЙ ДЖЕКПОТ (Covert Gold/Red, ~3% weight)
        DROPS.add(new DropEntry(Items.DIAMOND, 24, "§6§l", "★ ТАЙНОЕ (ДЖЕКПОТ) ★", 2));
        DROPS.add(new DropEntry(Items.NETHERITE_INGOT, 2, "§6§l", "★ ТАЙНОЕ (ДЖЕКПОТ) ★", 2));
        DROPS.add(new DropEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, "§6§l", "★ ТАЙНОЕ (ДЖЕКПОТ) ★", 1));
        DROPS.add(new DropEntry(Items.WITHER_SKELETON_SKULL, 2, "§6§l", "★ ТАЙНОЕ (ДЖЕКПОТ) ★", 1));
    }

    private static DropEntry getRandomDrop() {
        int total = 0;
        for (DropEntry d : DROPS) total += d.weight();
        int roll = RNG.nextInt(total);
        int cur = 0;
        for (DropEntry d : DROPS) {
            cur += d.weight();
            if (roll < cur) return d;
        }
        return DROPS.get(0);
    }

    private static ItemStack createDropStack(DropEntry drop) {
        ItemStack s = new ItemStack(drop.item(), drop.count());
        s.setCustomName(Text.literal(drop.rarityColor() + drop.item().getName().getString() + " x" + drop.count()));
        NbtCompound display = s.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        loreList.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal("§7Редкость: " + drop.rarityColor() + drop.tierName()))));
        loreList.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal("§8Кейс Судьбы (Дима и Ульяна)"))));
        display.put("Lore", loreList);
        return s;
    }

    public static class ActiveSpin {
        public final ServerPlayerEntity player;
        public final SimpleInventory inv;
        public final GenericContainerScreenHandler handler;
        public final List<ItemStack> sequence = new ArrayList<>();
        public int currentStep = 0;
        public int ticksUntilNext = 1;
        public final int maxSteps = 30;

        public ActiveSpin(ServerPlayerEntity player, SimpleInventory inv, GenericContainerScreenHandler handler) {
            this.player = player;
            this.inv = inv;
            this.handler = handler;

            // Generate 45 items in advance so we can slide window of 9 items
            for (int i = 0; i < 45; i++) {
                sequence.add(createDropStack(getRandomDrop()));
            }
        }
    }

    public static void openRouletteMenu(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(27);
        populateBaseRouletteView(player, inv);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, p) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inv, 3) {
                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clickingPlayer) {
                        if (slotIndex >= 0 && slotIndex < 27) {
                            if (!(clickingPlayer instanceof ServerPlayerEntity sp)) return;

                            if (activeSpins.containsKey(sp.getUuid())) {
                                return; // Already spinning!
                            }

                            if (slotIndex == 19) {
                                // Spin for 2 Emeralds or 16 Rotten Flesh or 4 Gold Ingots
                                if (tryConsumeSpinPayment(sp)) {
                                    startSpinAnimation(sp, inv, this);
                                }
                            } else if (slotIndex == 20) {
                                // Free Spin check (every 5 minutes = 300,000 ms)
                                long now = System.currentTimeMillis();
                                long last = freeSpinCooldowns.getOrDefault(sp.getUuid(), 0L);
                                if (now - last >= 300_000L) {
                                    freeSpinCooldowns.put(sp.getUuid(), now);
                                    startSpinAnimation(sp, inv, this);
                                } else {
                                    long secLeft = (300_000L - (now - last)) / 1000L;
                                    sp.sendMessage(Text.literal("§cБесплатный спин будет доступен через: §e" + secLeft + " сек.! §7(Или крутите за 2 Изумруда/16 Плоти)"), false);
                                    sp.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
                                }
                            } else if (slotIndex == 25) {
                                sp.closeHandledScreen();
                                StoryManager.get(sp.getServer()).openCompassMenu(sp);
                            }
                            return;
                        }
                        super.onSlotClick(slotIndex, button, actionType, clickingPlayer);
                    }

                    @Override
                    public ItemStack quickMove(PlayerEntity player, int slot) {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onClosed(PlayerEntity player) {
                        super.onClosed(player);
                        activeSpins.remove(player.getUuid());
                    }
                },
                Text.literal("§6✦ Кейс Судьбы: CS:GO Рулетка ✦")
        ));
    }

    private static boolean tryConsumeSpinPayment(ServerPlayerEntity player) {
        // Try pay with 2 Emeralds
        if (hasItem(player, Items.EMERALD, 2)) {
            consumeItem(player, Items.EMERALD, 2);
            player.sendMessage(Text.literal("§aОплачено: 2 Изумруда за прокрутку!"), true);
            return true;
        }
        // Try pay with 4 Gold Ingots
        if (hasItem(player, Items.GOLD_INGOT, 4)) {
            consumeItem(player, Items.GOLD_INGOT, 4);
            player.sendMessage(Text.literal("§aОплачено: 4 Золотых слитка за прокрутку!"), true);
            return true;
        }
        // Try pay with 16 Rotten Flesh
        if (hasItem(player, Items.ROTTEN_FLESH, 16)) {
            consumeItem(player, Items.ROTTEN_FLESH, 16);
            player.sendMessage(Text.literal("§aОплачено: 16 Гнилой плоти за прокрутку!"), true);
            return true;
        }
        player.sendMessage(Text.literal("§cДля платной прокрутки нужно: §a2 Изумруда §7ИЛИ §64 Золота §7ИЛИ §c16 Гнилой плоти!"), false);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.7f);
        return false;
    }

    private static boolean hasItem(ServerPlayerEntity p, Item item, int count) {
        int found = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) {
                found += s.getCount();
                if (found >= count) return true;
            }
        }
        return false;
    }

    private static void consumeItem(ServerPlayerEntity p, Item item, int count) {
        int rem = count;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(rem, s.getCount());
                s.decrement(take);
                rem -= take;
                if (rem <= 0) break;
            }
        }
    }

    public static void populateBaseRouletteView(ServerPlayerEntity player, SimpleInventory inv) {
        ItemStack blackGlass = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        blackGlass.setCustomName(Text.literal(" "));

        for (int i = 0; i < 27; i++) {
            inv.setStack(i, blackGlass.copy());
        }

        // Top pointer (slot 4)
        ItemStack topPointer = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        topPointer.setCustomName(Text.literal("§c▼ ВЫИГРЫШНЫЙ СЛОТ ▼"));
        inv.setStack(4, topPointer);

        // Bottom pointer (slot 22)
        ItemStack bottomPointer = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        bottomPointer.setCustomName(Text.literal("§c▲ ВЫИГРЫШНЫЙ СЛОТ ▲"));
        inv.setStack(22, bottomPointer);

        // Preview row (9..17)
        for (int i = 9; i <= 17; i++) {
            inv.setStack(i, createDropStack(getRandomDrop()));
        }

        // Slot 19: Paid Spin Button
        ItemStack spinBtn = new ItemStack(Items.EMERALD);
        spinBtn.setCustomName(Text.literal("§a§l▶ КРУТИТЬ РУЛЕТКУ (Оплата)"));
        setItemLore(spinBtn, List.of(
                "§7Стоимость 1 спина на выбор:",
                "§a• 2 Изумруда §7ИЛИ",
                "§6• 4 Золотых слитка §7ИЛИ",
                "§c• 16 Гнилой плоти",
                "§eШанс выбить до 24 алмазов и незерит!"
        ));
        inv.setStack(19, spinBtn);

        // Slot 20: Free Spin Button
        long now = System.currentTimeMillis();
        long last = freeSpinCooldowns.getOrDefault(player.getUuid(), 0L);
        long secLeft = Math.max(0, (300_000L - (now - last)) / 1000L);

        ItemStack freeBtn = new ItemStack(Items.NETHER_STAR);
        if (secLeft == 0) {
            freeBtn.setCustomName(Text.literal("§6§l★ БЕСПЛАТНЫЙ СПИН [ГОТОВО]"));
            setItemLore(freeBtn, List.of("§aНажмите, чтобы крутить бесплатно прямо сейчас!"));
        } else {
            freeBtn.setCustomName(Text.literal("§7★ БЕСПЛАТНЫЙ СПИН [§e" + secLeft + "с§7]"));
            setItemLore(freeBtn, List.of("§7Перезарядка 5 минут.", "§7Осталось: " + secLeft + " сек."));
        }
        inv.setStack(20, freeBtn);

        // Slot 25: Return to Menu
        ItemStack backBtn = new ItemStack(Items.BARRIER);
        backBtn.setCustomName(Text.literal("§c◀ Назад в Главное Меню"));
        inv.setStack(25, backBtn);
    }

    public static void startSpinAnimation(ServerPlayerEntity player, SimpleInventory inv, GenericContainerScreenHandler handler) {
        ActiveSpin spin = new ActiveSpin(player, inv, handler);
        activeSpins.put(player.getUuid(), spin);
        player.playSound(SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    public static void tick() {
        if (activeSpins.isEmpty()) return;

        List<UUID> finished = new ArrayList<>();

        for (Map.Entry<UUID, ActiveSpin> entry : activeSpins.entrySet()) {
            ActiveSpin spin = entry.getValue();
            if (spin.player.isDisconnected() || spin.player.currentScreenHandler != spin.handler) {
                finished.add(entry.getKey());
                continue;
            }

            spin.ticksUntilNext--;
            if (spin.ticksUntilNext <= 0) {
                spin.currentStep++;

                // Slide 9 visible items in slots 9..17
                for (int slot = 9; slot <= 17; slot++) {
                    int seqIndex = spin.currentStep + (slot - 9);
                    if (seqIndex < spin.sequence.size()) {
                        spin.inv.setStack(slot, spin.sequence.get(seqIndex).copy());
                    }
                }
                spin.handler.sendContentUpdates();

                // Authentic CS:GO clicking sound
                float pitch = 0.8f + (spin.currentStep / 35.0f);
                spin.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, pitch);

                if (spin.currentStep >= spin.maxSteps) {
                    // Winner is at center slot 13!
                    int winnerIndex = spin.currentStep + 4; // slot 13 corresponds to offset 4 (9+4=13)
                    ItemStack won = spin.sequence.get(winnerIndex).copy();

                    // Strip lore wrapper to give real item with normal lore
                    ItemStack realPrize = new ItemStack(won.getItem(), won.getCount());
                    if (!spin.player.getInventory().insertStack(realPrize)) {
                        spin.player.dropItem(realPrize, false);
                    }

                    ServerWorld world = spin.player.getServerWorld();
                    world.playSound(null, spin.player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
                    world.playSound(null, spin.player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.2f, 1.2f);
                    world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, spin.player.getX(), spin.player.getY() + 1.2, spin.player.getZ(), 45, 0.5, 0.8, 0.5, 0.15);
                    world.spawnParticles(ParticleTypes.FIREWORK, spin.player.getX(), spin.player.getY() + 1.5, spin.player.getZ(), 25, 0.4, 0.5, 0.4, 0.1);

                    String prizeName = won.getName().getString();
                    StoryManager.broadcast(spin.player.getServer(), "§6[Рулетка Судьбы] §e" + spin.player.getName().getString() + " §fвыбил из Кейса: " + prizeName + "§f! ★");

                    finished.add(entry.getKey());
                } else {
                    // Gradual deceleration curve like CS:GO
                    spin.ticksUntilNext = Math.max(1, (int) (1 + Math.pow(spin.currentStep / 5.2, 2.2)));
                }
            }
        }

        for (UUID uuid : finished) {
            activeSpins.remove(uuid);
        }
    }

    private static void setItemLore(ItemStack stack, List<String> lines) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        for (String line : lines) {
            loreList.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        }
        display.put("Lore", loreList);
    }
}
