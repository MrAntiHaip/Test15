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

    public enum CaseType {
        COBBLESTONE("Булыжниковый Кейс", "§7", Items.COBBLESTONE, 16),
        IRON("Железный Кейс", "§9", Items.IRON_INGOT, 4),
        GOLD("Золотой Кейс", "§6", Items.GOLD_INGOT, 4),
        DIAMOND("Алмазный Кейс", "§b§l", Items.DIAMOND, 2),
        FREE("Бесплатный Кейс", "§a§l", Items.AIR, 0);

        public final String displayName;
        public final String color;
        public final Item costItem;
        public final int costCount;

        CaseType(String displayName, String color, Item costItem, int costCount) {
            this.displayName = displayName;
            this.color = color;
            this.costItem = costItem;
            this.costCount = costCount;
        }
    }

    private static final Map<CaseType, List<DropEntry>> CASE_DROPS = new HashMap<>();

    static {
        List<DropEntry> cobbleList = new ArrayList<>();
        cobbleList.add(new DropEntry(Items.DIRT, 64, "§7", "Обычное", 25));
        cobbleList.add(new DropEntry(Items.STICK, 64, "§7", "Обычное", 25));
        cobbleList.add(new DropEntry(Items.CLAY_BALL, 32, "§7", "Обычное", 20));
        cobbleList.add(new DropEntry(Items.COAL, 32, "§7", "Обычное", 20));
        cobbleList.add(new DropEntry(Items.SAND, 64, "§a", "Необычное", 15));
        cobbleList.add(new DropEntry(Items.CACTUS, 32, "§a", "Необычное", 15));
        cobbleList.add(new DropEntry(Items.BONE, 32, "§a", "Необычное", 15));
        cobbleList.add(new DropEntry(Items.STRING, 32, "§a", "Необычное", 15));
        cobbleList.add(new DropEntry(Items.IRON_INGOT, 8, "§9", "Редкое", 12));
        cobbleList.add(new DropEntry(Items.REDSTONE, 32, "§9", "Редкое", 10));
        cobbleList.add(new DropEntry(Items.GOLD_INGOT, 4, "§5", "Мифическое", 6));
        cobbleList.add(new DropEntry(Items.DIAMOND, 2, "§6§l", "★ ДЖЕКПОТ ★", 3));
        CASE_DROPS.put(CaseType.COBBLESTONE, cobbleList);

        List<DropEntry> ironList = new ArrayList<>();
        ironList.add(new DropEntry(Items.COPPER_INGOT, 48, "§a", "Необычное", 20));
        ironList.add(new DropEntry(Items.IRON_INGOT, 16, "§a", "Необычное", 20));
        ironList.add(new DropEntry(Items.REDSTONE, 48, "§a", "Необычное", 18));
        ironList.add(new DropEntry(Items.LAPIS_LAZULI, 32, "§9", "Редкое", 16));
        ironList.add(new DropEntry(Items.QUARTZ, 48, "§9", "Редкое", 16));
        ironList.add(new DropEntry(Items.GOLD_INGOT, 12, "§9", "Редкое", 14));
        ironList.add(new DropEntry(Items.AMETHYST_SHARD, 16, "§5", "Мифическое", 10));
        ironList.add(new DropEntry(Items.OBSIDIAN, 8, "§5", "Мифическое", 10));
        ironList.add(new DropEntry(Items.ENDER_PEARL, 6, "§5", "Мифическое", 8));
        ironList.add(new DropEntry(Items.DIAMOND, 4, "§6§l", "★ ДЖЕКПОТ ★", 5));
        ironList.add(new DropEntry(Items.GOLDEN_APPLE, 3, "§6§l", "★ ДЖЕКПОТ ★", 4));
        CASE_DROPS.put(CaseType.IRON, ironList);

        List<DropEntry> goldList = new ArrayList<>();
        goldList.add(new DropEntry(Items.GOLD_INGOT, 24, "§9", "Редкое", 20));
        goldList.add(new DropEntry(Items.BLAZE_ROD, 12, "§9", "Редкое", 18));
        goldList.add(new DropEntry(Items.OBSIDIAN, 16, "§9", "Редкое", 16));
        goldList.add(new DropEntry(Items.ENDER_PEARL, 12, "§5", "Мифическое", 14));
        goldList.add(new DropEntry(Items.GOLDEN_APPLE, 4, "§5", "Мифическое", 12));
        goldList.add(new DropEntry(Items.GLOWSTONE, 32, "§5", "Мифическое", 12));
        goldList.add(new DropEntry(Items.PRISMARINE_CRYSTALS, 16, "§5", "Мифическое", 10));
        goldList.add(new DropEntry(Items.CRYING_OBSIDIAN, 12, "§d", "Легендарное", 8));
        goldList.add(new DropEntry(Items.DIAMOND, 8, "§6§l", "★ ДЖЕКПОТ ★", 6));
        goldList.add(new DropEntry(Items.NETHERITE_SCRAP, 2, "§6§l", "★ ДЖЕКПОТ ★", 4));
        CASE_DROPS.put(CaseType.GOLD, goldList);

        List<DropEntry> diamondList = new ArrayList<>();
        diamondList.add(new DropEntry(Items.DIAMOND, 12, "§5", "Мифическое", 25));
        diamondList.add(new DropEntry(Items.OBSIDIAN, 32, "§5", "Мифическое", 20));
        diamondList.add(new DropEntry(Items.CRYING_OBSIDIAN, 24, "§d", "Легендарное", 16));
        diamondList.add(new DropEntry(Items.GOLDEN_APPLE, 8, "§d", "Легендарное", 14));
        diamondList.add(new DropEntry(Items.DIAMOND, 20, "§d", "Легендарное", 12));
        diamondList.add(new DropEntry(Items.WITHER_SKELETON_SKULL, 1, "§d", "Легендарное", 8));
        diamondList.add(new DropEntry(Items.ECHO_SHARD, 4, "§d", "Легендарное", 8));
        diamondList.add(new DropEntry(Items.NETHERITE_INGOT, 1, "§6§l", "★ ТАЙНОЕ (НОЖ) ★", 6));
        diamondList.add(new DropEntry(Items.DIAMOND, 32, "§6§l", "★ ТАЙНОЕ (НОЖ) ★", 5));
        diamondList.add(new DropEntry(Items.NETHERITE_INGOT, 2, "§6§l", "★ ТАЙНОЕ (НОЖ) ★", 3));
        diamondList.add(new DropEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, "§6§l", "★ ТАЙНОЕ (НОЖ) ★", 2));
        CASE_DROPS.put(CaseType.DIAMOND, diamondList);

        List<DropEntry> freeList = new ArrayList<>(goldList);
        freeList.addAll(ironList);
        CASE_DROPS.put(CaseType.FREE, freeList);
    }

    private static DropEntry getRandomDrop(CaseType type) {
        List<DropEntry> list = CASE_DROPS.getOrDefault(type, CASE_DROPS.get(CaseType.COBBLESTONE));
        int total = 0;
        for (DropEntry d : list) total += d.weight();
        int roll = RNG.nextInt(total);
        int cur = 0;
        for (DropEntry d : list) {
            cur += d.weight();
            if (roll < cur) return d;
        }
        return list.get(0);
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
        public final CaseType caseType;
        public final List<ItemStack> sequence = new ArrayList<>();
        public int currentStep = 0;
        public int ticksUntilNext = 1;
        public final int maxSteps = 32;

        public ActiveSpin(ServerPlayerEntity player, SimpleInventory inv, GenericContainerScreenHandler handler, CaseType caseType) {
            this.player = player;
            this.inv = inv;
            this.handler = handler;
            this.caseType = caseType;

            for (int i = 0; i < 50; i++) {
                sequence.add(createDropStack(getRandomDrop(caseType)));
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

                            if (activeSpins.containsKey(sp.getUuid())) return;

                            if (slotIndex == 2) {
                                if (tryConsumeCost(sp, Items.COBBLESTONE, 16, "16 Булыжника")) {
                                    startSpinAnimation(sp, inv, this, CaseType.COBBLESTONE);
                                }
                            } else if (slotIndex == 3) {
                                if (tryConsumeCost(sp, Items.IRON_INGOT, 4, "4 Железных слитка")) {
                                    startSpinAnimation(sp, inv, this, CaseType.IRON);
                                }
                            } else if (slotIndex == 5) {
                                if (tryConsumeCost(sp, Items.GOLD_INGOT, 4, "4 Золотых слитка")) {
                                    startSpinAnimation(sp, inv, this, CaseType.GOLD);
                                }
                            } else if (slotIndex == 6) {
                                if (tryConsumeCost(sp, Items.DIAMOND, 2, "2 Алмаза")) {
                                    startSpinAnimation(sp, inv, this, CaseType.DIAMOND);
                                }
                            } else if (slotIndex == 8) {
                                long now = System.currentTimeMillis();
                                long last = freeSpinCooldowns.getOrDefault(sp.getUuid(), 0L);
                                if (now - last >= 300_000L) {
                                    freeSpinCooldowns.put(sp.getUuid(), now);
                                    startSpinAnimation(sp, inv, this, CaseType.FREE);
                                } else {
                                    long secLeft = (300_000L - (now - last)) / 1000L;
                                    sp.sendMessage(Text.literal("§cБесплатный кейс перезаряжается! Осталось: §e" + secLeft + " сек."), false);
                                    sp.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.7f);
                                }
                            } else if (slotIndex == 26) {
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
                Text.literal("§8✦ §6Кейс Судьбы: CS:GO Рулетка §8✦")
        ));
    }

    private static boolean tryConsumeCost(ServerPlayerEntity player, Item item, int count, String costName) {
        int found = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) found += s.getCount();
        }
        if (found < count) {
            player.sendMessage(Text.literal("§cВам не хватает: §e" + costName + " §7(У вас: " + found + "/" + count + ")"), false);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.7f);
            return false;
        }

        int rem = count;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(rem, s.getCount());
                s.decrement(take);
                rem -= take;
                if (rem <= 0) break;
            }
        }
        player.sendMessage(Text.literal("§aОплачено: " + costName + " за открытие кейса!"), true);
        return true;
    }

    public static void populateBaseRouletteView(ServerPlayerEntity player, SimpleInventory inv) {
        ItemStack blackGlass = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        blackGlass.setCustomName(Text.literal(" "));

        for (int i = 0; i < 27; i++) {
            inv.setStack(i, blackGlass.copy());
        }

        ItemStack infoBook = new ItemStack(Items.KNOWLEDGE_BOOK);
        infoBook.setCustomName(Text.literal("§6§l★ Рулетка Судьбы CS:GO ★"));
        setItemLore(infoBook, List.of(
                "§7Крутите реальную ленту кейсов!",
                "§fСтавки сверху:",
                "§7• 16 Булыжника (База)",
                "§9• 4 Железа (Медь, золото, редстоун)",
                "§6• 4 Золота (Обсидиан, ифриты, яблоки)",
                "§b• 2 Алмаза (Дроп до 32 алмазов и незерит!)",
                "§a• Бесплатный спин каждые 5 минут!"
        ));
        inv.setStack(0, infoBook);

        ItemStack cobbleBtn = new ItemStack(Items.COBBLESTONE, 16);
        cobbleBtn.setCustomName(Text.literal("§7§l[1] Булыжниковый Кейс"));
        setItemLore(cobbleBtn, List.of("§eЦена: §f16 Булыжника", "§aНажмите, чтобы крутить!"));
        inv.setStack(2, cobbleBtn);

        ItemStack ironBtn = new ItemStack(Items.IRON_INGOT, 4);
        ironBtn.setCustomName(Text.literal("§9§l[2] Железный Кейс"));
        setItemLore(ironBtn, List.of("§eЦена: §f4 Железных слитка", "§aНажмите, чтобы крутить!"));
        inv.setStack(3, ironBtn);

        ItemStack topPointer = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        topPointer.setCustomName(Text.literal("§c§l▼ ВЫИГРЫШНЫЙ СЛОТ ▼"));
        inv.setStack(4, topPointer);

        ItemStack goldBtn = new ItemStack(Items.GOLD_INGOT, 4);
        goldBtn.setCustomName(Text.literal("§6§l[3] Золотой Кейс"));
        setItemLore(goldBtn, List.of("§eЦена: §f4 Золотых слитка", "§aНажмите, чтобы крутить!"));
        inv.setStack(5, goldBtn);

        ItemStack diamondBtn = new ItemStack(Items.DIAMOND, 2);
        diamondBtn.setCustomName(Text.literal("§b§l[4] Алмазный Кейс (CS:GO Нож)"));
        setItemLore(diamondBtn, List.of("§eЦена: §b2 Алмаза", "§c★ КУШ: до 32 алмазов и незерит!", "§bНажмите, чтобы крутить!"));
        inv.setStack(6, diamondBtn);

        long now = System.currentTimeMillis();
        long last = freeSpinCooldowns.getOrDefault(player.getUuid(), 0L);
        long secLeft = Math.max(0, (300_000L - (now - last)) / 1000L);

        ItemStack freeBtn = new ItemStack(Items.NETHER_STAR);
        if (secLeft == 0) {
            freeBtn.setCustomName(Text.literal("§a§l★ БЕСПЛАТНЫЙ КЕЙС [ГОТОВО]"));
            setItemLore(freeBtn, List.of("§aНажмите, чтобы крутить бесплатно!"));
        } else {
            freeBtn.setCustomName(Text.literal("§7★ БЕСПЛАТНЫЙ КЕЙС [§e" + secLeft + "с§7]"));
            setItemLore(freeBtn, List.of("§7Перезарядка 5 минут.", "§7Осталось: " + secLeft + " сек."));
        }
        inv.setStack(8, freeBtn);

        for (int i = 9; i <= 17; i++) {
            inv.setStack(i, createDropStack(getRandomDrop(CaseType.COBBLESTONE)));
        }

        ItemStack balance = new ItemStack(Items.EMERALD);
        balance.setCustomName(Text.literal("§a§lВаш Баланс Ресурсов:"));
        setItemLore(balance, List.of(
                "§7• Булыжник: §f" + countItem(player, Items.COBBLESTONE),
                "§7• Железо: §f" + countItem(player, Items.IRON_INGOT),
                "§7• Золото: §f" + countItem(player, Items.GOLD_INGOT),
                "§7• Алмазы: §b" + countItem(player, Items.DIAMOND)
        ));
        inv.setStack(18, balance);

        ItemStack bottomPointer = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        bottomPointer.setCustomName(Text.literal("§c§l▲ ВЫИГРЫШНЫЙ СЛОТ ▲"));
        inv.setStack(22, bottomPointer);

        ItemStack backBtn = new ItemStack(Items.BARRIER);
        backBtn.setCustomName(Text.literal("§c◀ Назад в Меню Судьбы"));
        inv.setStack(26, backBtn);
    }

    private static int countItem(ServerPlayerEntity p, Item item) {
        int c = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) c += s.getCount();
        }
        return c;
    }

    public static void startSpinAnimation(ServerPlayerEntity player, SimpleInventory inv, GenericContainerScreenHandler handler, CaseType caseType) {
        ActiveSpin spin = new ActiveSpin(player, inv, handler, caseType);
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

                for (int slot = 9; slot <= 17; slot++) {
                    int seqIndex = spin.currentStep + (slot - 9);
                    if (seqIndex < spin.sequence.size()) {
                        spin.inv.setStack(slot, spin.sequence.get(seqIndex).copy());
                    }
                }
                spin.handler.sendContentUpdates();

                float pitch = 0.8f + (spin.currentStep / 35.0f);
                spin.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.85f, pitch);

                if (spin.currentStep >= spin.maxSteps) {
                    int winnerIndex = spin.currentStep + 4;
                    ItemStack won = spin.sequence.get(winnerIndex).copy();

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
                    StoryManager.broadcast(spin.player.getServer(), "§6[Рулетка Судьбы] §e" + spin.player.getName().getString()
                            + " §fвыбил из " + spin.caseType.color + spin.caseType.displayName + ": " + prizeName + "§f! ★");

                    finished.add(entry.getKey());
                } else {
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
