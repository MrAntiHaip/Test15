package com.dimaulyana.eightblades;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

public class EightBladesMod implements ModInitializer {
    public static final String MOD_ID = "eightblades";
    public static final Identifier HUD_SYNC_ID = new Identifier(MOD_ID, "hud_sync");

    public static final Item MUD_SWORD = registerSword("mud_sword", 1, 2, -2.4f,
            "Грязевое Цунами", "Двойная волна тины: отбрасывает, замедляет и топит врагов в болоте.", 90);
    public static final Item CACTUS_SWORD = registerSword("cactus_sword", 2, 3, -2.3f,
            "Кольцо Тысячи Игл", "Веер острых шипов во все стороны, шипастый щит и Регенерация II.", 100);
    public static final Item VENOM_SWORD = registerSword("venom_sword", 3, 4, -2.2f,
            "Токсичный Шторм", "Рывок сквозь врагов + ядовитое облако, травящее всё вокруг 4 секунды.", 100);
    public static final Item THUNDER_SWORD = registerSword("thunder_sword", 4, 5, -2.2f,
            "Цепная Молния", "Поражает до 8 врагов рядом разрядами молнии по очереди.", 110);
    public static final Item FROST_SWORD = registerSword("frost_sword", 5, 6, -2.1f,
            "Ледяной Буран", "Три кольца заморозки и Ледяной Щит для дуэта.", 110);
    public static final Item BLOOD_SWORD = registerSword("blood_sword", 6, 7, -2.1f,
            "Кровавый Сифон", "Тотемы крови качают HP из врагов в обоих игроков 4 секунды.", 120);
    public static final Item GRAVITY_SWORD = registerSword("gravity_sword", 7, 9, -2.0f,
            "Гравипушка: Воронка", "Стягивает врагов в точку 2.5 сек и впечатывает кинетическим ударом!", 120);
    public static final Item PHOENIX_SWORD = registerSword("phoenix_sword", 8, 10, -2.0f,
            "Взрыв Сверхновой", "Волна огня + РЕЖИМ ФЕНИКСА на 10 сек: аура, огнестойкость, регенерация.", 120);
    public static final Item WIND_SWORD = registerSword("wind_sword", 9, 11, -1.9f,
            "Шквальный Торнадо", "Сверхзвуковой рывок: торнадо подбрасывает врагов в небо, дуэту Скорость II.", 120);
    public static final Item SCULK_SWORD = registerSword("sculk_sword", 10, 13, -1.9f,
            "Тройной Резонанс Вардена", "Три звуковых луча Sonic Boom подряд, пробивающих броню и ослепляющих тьмой.", 130);
    public static final Item STARFALL_SWORD = registerSword("starfall_sword", 11, 14, -1.8f,
            "Звездный Метеоритный Залп", "Восемь падающих звезд с неба + Сила II и щит для дуэта.", 130);
    public static final Item HELLFORGE_SWORD = registerSword("hellforge_sword", 12, 16, -1.8f,
            "Магматический Разлом", "Раскалывает землю и выпускает расширяющееся огненное кольцо.", 130);
    public static final Item CHRONO_SWORD = registerSword("chrono_sword", 13, 18, -1.7f,
            "ХРОНОСТАЗИС", "ПОЛНАЯ остановка времени врагов на 7 секунд, затем весь урон разом!", 140);
    public static final Item ASTRAL_SWORD = registerSword("astral_sword", 14, 21, -1.6f,
            "Черная Дыра Астрала", "Сингулярность 3 секунды засасывает толпы монстров, затем взрыв антиматерией.", 140);
    public static final Item ECLIPSE_SWORD = registerSword("eclipse_sword", 15, 26, -1.5f,
            "Катаклизм Затмения", "ТРИ волны апокалипсиса: молнии, звуковые взрывы, полное исцеление и мощь богов!", 150);

    public static final ItemGroup EIGHT_BLADES_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier(MOD_ID, "main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ECLIPSE_SWORD))
                    .displayName(Text.translatable("itemGroup.eightblades.main"))
                    .entries((context, entries) -> {
                        entries.add(MUD_SWORD);
                        entries.add(CACTUS_SWORD);
                        entries.add(VENOM_SWORD);
                        entries.add(THUNDER_SWORD);
                        entries.add(FROST_SWORD);
                        entries.add(BLOOD_SWORD);
                        entries.add(GRAVITY_SWORD);
                        entries.add(PHOENIX_SWORD);
                        entries.add(WIND_SWORD);
                        entries.add(SCULK_SWORD);
                        entries.add(STARFALL_SWORD);
                        entries.add(HELLFORGE_SWORD);
                        entries.add(CHRONO_SWORD);
                        entries.add(ASTRAL_SWORD);
                        entries.add(ECLIPSE_SWORD);
                    })
                    .build()
    );

    private static Item registerSword(String name, int tier, int bonusDamage, float speed, String abilityName, String abilityDesc, int cooldown) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(MOD_ID, name),
                new MagicSwordItem(tier, bonusDamage, speed, abilityName, abilityDesc, cooldown, new Item.Settings().maxCount(1).fireproof())
        );
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("st")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).startStory(player);
                        return 1;
                    })
                    .then(CommandManager.literal("menu").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).openCompassMenu(player);
                        return 1;
                    }))
                    .then(CommandManager.literal("roulette").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        RouletteManager.openRouletteMenu(player);
                        return 1;
                    }))
                    .then(CommandManager.literal("event")
                            .then(CommandManager.literal("meteor").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                EventManager.triggerMeteorEvent(player);
                                return 1;
                            }))
                            .then(CommandManager.literal("goblin").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                EventManager.triggerGoblinEvent(player);
                                return 1;
                            }))
                            .then(CommandManager.literal("comet").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                EventManager.triggerIceCometEvent(player);
                                return 1;
                            }))
                            .then(CommandManager.literal("horde").executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                EventManager.triggerHordeEvent(player);
                                return 1;
                            }))
                    )
                    .then(CommandManager.literal("npc").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).spawnOrTeleportBlacksmith(player);
                        return 1;
                    }))
                    .then(CommandManager.literal("boss").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager sm = StoryManager.get(player.getServer());
                        if (sm.isStoryStarted() && sm.isSwordCraftedForStage() && sm.getCurrentStage() <= 15) {
                            sm.spawnCurrentStageBoss(player.getServerWorld(), player.getBlockPos());
                        } else {
                            player.sendMessage(Text.literal("§cСначала сдайте ресурсы Кузнецу Аркадию для текущего меча!"), false);
                        }
                        return 1;
                    }))
                    .then(CommandManager.literal("reset").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StoryManager.get(player.getServer()).resetStory(player);
                        return 1;
                    }))
            );
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            StoryManager sm = StoryManager.get(server);
            sm.giveCompassIfNeeded(player);
            player.sendMessage(Text.literal("§6✦ [Легенда 15 Клинков: Дима и Ульяна] §fНажмите §eПКМ Компасом §f— выберите, кто вы (Дима/Ульяна), Роль, Кейс и Ивенты!"), false);
            sm.syncHudToAll(server);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                StoryManager.get(newPlayer.getServer()).giveCompassIfNeeded(newPlayer));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.COMPASS)) {
                if (!world.isClient() && player instanceof ServerPlayerEntity sp && sp.getServer() != null) {
                    StoryManager.get(sp.getServer()).openCompassMenu(sp);
                }
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand == Hand.MAIN_HAND && StoryManager.isBlacksmith(entity)) {
                if (!world.isClient() && player instanceof ServerPlayerEntity sp && sp.getServer() != null) {
                    StoryManager sm = StoryManager.get(sp.getServer());
                    ItemStack held = sp.getMainHandStack();
                    if (sm.trySubmitStack(sp.getServer(), held)) {
                        ServerWorld sw = sp.getServerWorld();
                        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1.2, entity.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                        sw.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
                    } else {
                        sm.openCompassMenu(sp);
                    }
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (StoryManager.isBlacksmith(entity)) {
                return ActionResult.FAIL;
            }
            if (!world.isClient() && entity.getCommandTags().contains(EventManager.GOBLIN_TAG) && entity instanceof ZombieEntity goblin) {
                if (player instanceof ServerPlayerEntity sp) {
                    EventManager.onGoblinHit(sp.getServerWorld(), goblin, sp);
                }
            }
            ItemStack weapon = player.getMainHandStack();
            if (StoryManager.isBannedVanillaSword(weapon.getItem())) {
                if (!world.isClient()) {
                    weapon.setCount(0);
                    player.playSound(SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                    player.sendMessage(Text.literal("§c[Проклятие Печати] §7Обычные мечи запрещены! Используйте только Магические Клинки Аркадия."), true);
                }
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim && victim.getServer() != null) {
                if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
                    if (StoryManager.get(victim.getServer()).areDuoPartners(attacker, victim)) {
                        return false;
                    }
                }
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!entity.getWorld().isClient() && entity.getCommandTags().contains(EventManager.GOBLIN_TAG) && entity instanceof ZombieEntity goblin) {
                EventManager.onGoblinKilled((ServerWorld) entity.getWorld(), goblin);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            protectCompasses(server);
            StoryManager.get(server).tick(server);
        });
    }

    /** Компас Судьбы невозможно потерять: возврат с курсора + авто-выдача каждый тик. */
    private static void protectCompasses(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ScreenHandler handler = player.currentScreenHandler;
            if (handler != null) {
                ItemStack cursor = handler.getCursorStack();
                if (cursor.isOf(Items.COMPASS)) {
                    ItemStack back = cursor.copy();
                    handler.setCursorStack(ItemStack.EMPTY);
                    if (!player.getInventory().insertStack(back)) {
                        player.getInventory().offerOrDrop(back);
                    }
                    player.sendMessage(Text.literal("§6[Компас Судьбы] §cАртефакт привязан к душе — выбросить нельзя!"), true);
                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.7f);
                }
            }
            boolean hasCompass = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (player.getInventory().getStack(i).isOf(Items.COMPASS)) {
                    hasCompass = true;
                    break;
                }
            }
            if (!hasCompass) {
                StoryManager.get(server).giveCompassIfNeeded(player);
            }
        }
    }
}
