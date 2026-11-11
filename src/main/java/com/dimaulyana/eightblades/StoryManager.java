package com.dimaulyana.eightblades;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

public class StoryManager extends PersistentState {
    public static final String BLACKSMITH_TAG = "eightblades_blacksmith";
    public static final String BOSS_TAG = "eightblades_boss";
    public static final String MINION_TAG = "eightblades_minion";

    private boolean storyStarted = false;
    private int currentStage = 1;
    private boolean swordCraftedForStage = false;
    private int bossSpawnCountdown = -1;
    private int bossAbilityCooldown = 80;
    private int bossPhase = 1;
    private long lastHintTime = 0;

    private final Map<String, Integer> deliveredItems = new HashMap<>();
    private final Map<UUID, String> playerRoles = new HashMap<>();
    private final Map<UUID, String> playerIdentities = new HashMap<>();

    private UUID blacksmithUuid = null;
    private UUID activeBossUuid = null;

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("§cБосс"), BossBar.Color.RED, BossBar.Style.NOTCHED_10);

    // ==================== ПЛАНИРОВЩИК ЭФФЕКТОВ ====================
    private record ScheduledTask(int dueTick, Runnable action) {}
    private static final Deque<ScheduledTask> TASKS = new ArrayDeque<>();
    private static int globalTick = 0;

    public static void schedule(int delayTicks, Runnable action) {
        if (action == null) return;
        TASKS.add(new ScheduledTask(globalTick + Math.max(1, delayTicks), action));
    }

    private static void tickScheduled() {
        globalTick++;
        if (TASKS.isEmpty()) return;
        Iterator<ScheduledTask> it = TASKS.iterator();
        List<Runnable> ready = new ArrayList<>();
        while (it.hasNext()) {
            ScheduledTask t = it.next();
            if (t.dueTick() <= globalTick) {
                ready.add(t.action());
                it.remove();
            }
        }
        for (Runnable r : ready) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== ЗАМОРОЗКА ВРЕМЕНИ (ХРОНОСТАЗИС) ====================
    private static final Map<UUID, FrozenData> FROZEN = new HashMap<>();
    private static final Random RNG = new Random();

    private static class FrozenData {
        int ticksLeft;
        boolean wasAiDisabled;
        Vec3d anchor;
    }

    public static void freezeEntity(LivingEntity entity, int ticks) {
        if (entity == null || !entity.isAlive()) return;
        if (entity instanceof PlayerEntity || isBlacksmith(entity)) return;
        FrozenData data = FROZEN.get(entity.getUuid());
        if (data == null) {
            data = new FrozenData();
            data.wasAiDisabled = entity instanceof MobEntity m && m.isAiDisabled();
            data.anchor = entity.getPos();
            FROZEN.put(entity.getUuid(), data);
        }
        data.ticksLeft = Math.max(data.ticksLeft, ticks);
        if (entity instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;
    }

    private static void tickFrozen(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, FrozenData> entry : FROZEN.entrySet()) {
            LivingEntity living = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(entry.getKey());
                if (e instanceof LivingEntity le) {
                    living = le;
                    break;
                }
            }
            FrozenData data = entry.getValue();
            if (living == null || !living.isAlive()) {
                done.add(entry.getKey());
                continue;
            }
            data.ticksLeft--;
            if (data.ticksLeft <= 0) {
                if (living instanceof MobEntity mob && !data.wasAiDisabled) {
                    mob.setAiDisabled(false);
                }
                done.add(entry.getKey());
                continue;
            }
            living.setVelocity(Vec3d.ZERO);
            living.velocityModified = true;
            living.fallDistance = 0.0f;
            if (data.anchor != null && living.squaredDistanceTo(data.anchor.x, data.anchor.y, data.anchor.z) > 0.09) {
                living.refreshPositionAndAngles(data.anchor.x, data.anchor.y, data.anchor.z, living.getYaw(), living.getPitch());
            }
        }
        for (UUID id : done) {
            FROZEN.remove(id);
        }
    }

    // ==================== ДАННЫЕ ГЛАВ ====================
    public record Requirement(Item item, String key, String displayName, int requiredCount) {}

    public record StageData(
            int stage,
            String title,
            List<Requirement> requirements,
            Supplier<Item> swordSupplier,
            String swordName,
            String npcStartQuote,
            String npcForgedQuote,
            String npcVictoryQuote,
            String bossName,
            double bossMaxHp,
            BossBar.Color bossColor
    ) {}

    private static final List<StageData> STAGES = new ArrayList<>();

    static {
        STAGES.add(new StageData(
                1, "Глава 1: Грязевое Начало",
                List.of(
                        new Requirement(Items.DIRT, "dirt", "Земля", 64),
                        new Requirement(Items.STICK, "stick", "Палка", 32),
                        new Requirement(Items.CLAY_BALL, "clay", "Глина", 16)
                ),
                () -> EightBladesMod.MUD_SWORD, "§6[I] Грязевой Меч Нуба",
                "§e[Аркадий]: §fДима, Ульяна! Начнём с малого — с той грязи, из которой вырастают легенды. Несите §e64 Земли§f, §e32 Палки §fи §b16 Глины§f. Бросайте (Q) мне под ноги или тыкните в меня предметом ПКМ!",
                "§e[Аркадий]: §aХа! Первая сталь пошла! Грязь тоже бывает крепкой, если её правильно закалить. Держите оба §6Грязевой Меч Нуба§a! ПКМ — двойная волна тины!",
                "§e[Аркадий]: §aЧвак захлюпал в последний раз! Видите? Даже грязь может стать оружием. Но дальше будет жарче — пустыня не прощает слабых.",
                "Болотный Грязежер Чвак", 80.0, BossBar.Color.GREEN
        ));

        STAGES.add(new StageData(
                2, "Глава 2: Пески Пустыни",
                List.of(
                        new Requirement(Items.CACTUS, "cactus", "Кактус", 32),
                        new Requirement(Items.SAND, "sand", "Песок", 64),
                        new Requirement(Items.ROTTEN_FLESH, "flesh", "Гнилая плоть", 32),
                        new Requirement(Items.BONE, "bone", "Кость", 16)
                ),
                () -> EightBladesMod.CACTUS_SWORD, "§a[II] Кактусовый Шиполом",
                "§e[Аркадий]: §fПустыня выжигает всё, кроме колючек. Мне нужны §a32 Кактуса§f, §e64 Песка§f, §c32 Гнилой плоти §fи §f16 Костей§f. Осторожно, кактус колется — но меч будет колоться сильнее!",
                "§e[Аркадий]: §aШиполом готов! Тысяча игл в одном клинке. ПКМ — три кольца шипов и щит регенерации. Оба получают §2Кактусовый Шиполом§a!",
                "§e[Аркадий]: §aХасан осыпался песком! Вы уже не нубы, вы — ученики. А теперь спустимся туда, где темно и пахнет ядом...",
                "Пустынный Иглострел Хасан", 140.0, BossBar.Color.YELLOW
        ));

        STAGES.add(new StageData(
                3, "Глава 3: Токсичные Глубины",
                List.of(
                        new Requirement(Items.STRING, "string", "Нить", 32),
                        new Requirement(Items.SPIDER_EYE, "spider_eye", "Паучий глаз", 24),
                        new Requirement(Items.COBBLESTONE, "cobble", "Булыжник", 64),
                        new Requirement(Items.FERMENTED_SPIDER_EYE, "fermented_eye", "Маринованный глаз", 8)
                ),
                () -> EightBladesMod.VENOM_SWORD, "§2[III] Паучий Клинок Токсина",
                "§e[Аркадий]: §fАрахна плетёт сети под землёй. Нужны §f32 Нити§f, §224 Паучьих глаза§f, §764 Булыжника §fи §58 Маринованных глаз§f. Пауков бейте смелее — глаза сами набегут!",
                "§e[Аркадий]: §aЯд застыл на кромке! ПКМ — рывок сквозь врагов и ядовитое облако на 4 секунды. Забирайте §2Паучий Клинок Токсина§a!",
                "§e[Аркадий]: §aМатриарх пала, паутина порвана! Кстати, я слышу гром... Медь и редстоун — вот что нам нужно дальше.",
                "Матриарх Черной Паутины Арахна", 210.0, BossBar.Color.PURPLE
        ));

        STAGES.add(new StageData(
                4, "Глава 4: Медный Разряд",
                List.of(
                        new Requirement(Items.COPPER_INGOT, "copper", "Медный слиток", 48),
                        new Requirement(Items.IRON_INGOT, "iron", "Железный слиток", 32),
                        new Requirement(Items.REDSTONE, "redstone", "Редстоун", 64),
                        new Requirement(Items.LIGHTNING_ROD, "lightning_rod", "Громоотвод", 4)
                ),
                () -> EightBladesMod.THUNDER_SWORD, "§b[IV] Грозовой Клинок Цепи",
                "§e[Аркадий]: §fМолнию нельзя ковать голыми руками! Принесите §648 Меди§f, §f32 Железа§f, §c64 Редстоуна §fи §64 Громоотвода§f — они примут удар на себя.",
                "§e[Аркадий]: §bСлышите, как поёт сталь? Это гром внутри! ПКМ — цепь молний по 8 врагам подряд. Ваш §bГрозовой Клинок Цепи§f!",
                "§e[Аркадий]: §bТитан разряжен до нуля! Отличная работа, дуэт. Но следующий враг не боится тока — он боится только холода.",
                "Перегруженный Автоматон Титан", 300.0, BossBar.Color.BLUE
        ));

        STAGES.add(new StageData(
                5, "Глава 5: Вечная Мерзлота",
                List.of(
                        new Requirement(Items.SNOWBALL, "snowball", "Снежок", 64),
                        new Requirement(Items.PACKED_ICE, "packed_ice", "Плотный лёд", 32),
                        new Requirement(Items.LAPIS_LAZULI, "lapis", "Лазурит", 32),
                        new Requirement(Items.BONE_BLOCK, "bone_block", "Костный блок", 8)
                ),
                () -> EightBladesMod.FROST_SWORD, "§3[V] Клинок Вечной Мерзлоты",
                "§e[Аркадий]: §fОбычный лёд тает в горне — нужен §bПЛОТНЫЙ ЛЁД§f! Он ломается §aобычной киркой§f, без всякого шёлкового касания. Несите §b64 Снежка§f, §b32 Плотного льда§f, §932 Лазурита §fи §f8 Костных блоков§f. §eЛень искать? Жмите «Ледяная Комета» в Компасе — она насыплет льда прямо с неба!",
                "§e[Аркадий]: §bХолод запечатан навеки! ПКМ — три кольца бурана, враги превращаются в ледышки. Примите §3Клинок Вечной Мерзлоты§f!",
                "§e[Аркадий]: §bБорей растаял! Ха, король мерзлоты и тот не выдержал вашего жара. Дальше — кровь. Не морщитесь, кузнец без крови не куёт.",
                "Король Мерзлоты Борей", 380.0, BossBar.Color.WHITE
        ));

        STAGES.add(new StageData(
                6, "Глава 6: Кровавая Жатва",
                List.of(
                        new Requirement(Items.BEEF, "beef", "Сырая говядина", 32),
                        new Requirement(Items.REDSTONE_BLOCK, "redstone_block", "Блок редстоуна", 16),
                        new Requirement(Items.FERMENTED_SPIDER_EYE, "ferm_eye", "Маринованный глаз", 16),
                        new Requirement(Items.GOLD_INGOT, "gold", "Золотой слиток", 24)
                ),
                () -> EightBladesMod.BLOOD_SWORD, "§4[VI] Кровавый Жнец Вампиризма",
                "§e[Аркадий]: §fБарон Валтасар пьёт жизнь из живых. Чтобы выковать противоядие, нужно §c32 Сырой говядины§f, §416 Блоков редстоуна§f, §516 Маринованных глаз §fи §624 Золота§f.",
                "§e[Аркадий]: §4Клинок жаждет! ПКМ — тотемы крови качают HP из врагов в вас обоих целых 4 секунды. Вот ваш §4Кровавый Жнец Вампиризма§f!",
                "§e[Аркадий]: §4Барон обезглавлен! Вы научились забирать жизнь — теперь научитесь управлять весом мира. Гравитация ждёт.",
                "Барон Кровавой Луны Валтасар", 460.0, BossBar.Color.RED
        ));

        STAGES.add(new StageData(
                7, "Глава 7: Гравитационная Аномалия",
                List.of(
                        new Requirement(Items.IRON_BLOCK, "iron_block", "Железный блок", 8),
                        new Requirement(Items.AMETHYST_SHARD, "amethyst", "Осколок аметиста", 24),
                        new Requirement(Items.ENDER_PEARL, "pearl", "Жемчуг Эндера", 16),
                        new Requirement(Items.OBSIDIAN, "obsidian", "Обсидиан", 20)
                ),
                () -> EightBladesMod.GRAVITY_SWORD, "§5[VII] Гравитационный Разрушитель",
                "§e[Аркадий]: §fНебула искривляет пространство. Мне нужны §f8 Железных блоков§f, §d24 Аметиста§f, §316 Жемчуга Эндера §fи §520 Обсидиана§f. Жемчуг выбивайте из эндерменов — или крутите кейс!",
                "§e[Аркадий]: §5ГРАВИПУШКА ГОТОВА! ПКМ — воронка стягивает толпу 2.5 секунды, потом впечатывает в землю. §5Гравитационный Разрушитель §fваш!",
                "§e[Аркадий]: §5Колосс схлопнулся сам в себя! Красиво. Теперь поднимемся к огню — там, где перья не горят только у феникса.",
                "Сингулярный Колосс Небула", 550.0, BossBar.Color.PURPLE
        ));

        STAGES.add(new StageData(
                8, "Глава 8: Пламя Феникса",
                List.of(
                        new Requirement(Items.BLAZE_ROD, "blaze_rod", "Стержень ифрита", 24),
                        new Requirement(Items.GOLD_INGOT, "gold_ingot", "Золотой слиток", 32),
                        new Requirement(Items.MAGMA_CREAM, "magma_cream", "Сгусток магмы", 16),
                        new Requirement(Items.FIRE_CHARGE, "fire_charge", "Огненный заряд", 8)
                ),
                () -> EightBladesMod.PHOENIX_SWORD, "§c[VIII] Клинок Пепельного Феникса",
                "§e[Аркадий]: §fИгнис летит с неба, и его пламя не гаснет водой. Нужны §624 Стержня ифрита§f, §e32 Золота§f, §c16 Сгустков магмы §fи §c8 Огненных зарядов§f.",
                "§e[Аркадий]: §6Пламя поёт в руках! ПКМ — сверхновая и РЕЖИМ ФЕНИКСА на 10 секунд: огненная аура жжёт врагов и лечит вас. §cКлинок Пепельного Феникса§f!",
                "§e[Аркадий]: §6Вестник сгорел дотла — ирония судьбы! Вы прошли половину пути, ребята. Я горжусь вами. Но небо ещё не покорено.",
                "Инфернальный Вестник Игнис", 640.0, BossBar.Color.RED
        ));

        STAGES.add(new StageData(
                9, "Глава 9: Небесный Шквал",
                List.of(
                        new Requirement(Items.FEATHER, "feather", "Перо", 48),
                        new Requirement(Items.SUGAR_CANE, "sugar_cane", "Сахарный тростник", 32),
                        new Requirement(Items.EMERALD, "emerald", "Изумруд", 32),
                        new Requirement(Items.ARROW, "arrow", "Стрела", 64)
                ),
                () -> EightBladesMod.WIND_SWORD, "§f[IX] Клинок Небесного Бурана",
                "§e[Аркадий]: §fЗабудьте про фантомов — спать спокойно важнее! Для лёгкости клинка берём §aСАХАРНЫЙ ТРОСТНИК§f (растёт у любой воды). Несите §f48 Перьев§f, §a32 Тростника§f, §a32 Изумруда §fи §764 Стрелы§f!",
                "§e[Аркадий]: §fЛёгкий, как ветер! ПКМ — сверхзвуковой рывок и торнадо, поднимающий врагов в небо. §fКлинок Небесного Бурана §fдля обоих!",
                "§e[Аркадий]: §fЗефир упал с небес и рассыпался перьями! Полдела сделано. А теперь — тишина. Самая страшная тишина, в Древнем Городе.",
                "Буревестник Верхнего Мира Зефир", 720.0, BossBar.Color.WHITE
        ));

        STAGES.add(new StageData(
                10, "Глава 10: Глубинный Скалк",
                List.of(
                        new Requirement(Items.SCULK, "sculk", "Скалк", 32),
                        new Requirement(Items.ECHO_SHARD, "echo_shard", "Осколок эха", 8),
                        new Requirement(Items.CRYING_OBSIDIAN, "crying_obsidian", "Плачущий обсидиан", 16),
                        new Requirement(Items.SOUL_SAND, "soul_sand", "Песок душ", 32)
                ),
                () -> EightBladesMod.SCULK_SWORD, "§1[X] Скалковый Клинок Глубин",
                "§e[Аркадий]: §fТам внизу нельзя шуметь. §332 Скалка§f, §b8 Осколков эха§f, §516 Плачущего обсидиана §fи §832 Песка душ§f. Эхо-осколки часто выпадают из Алмазного Кейса — не мучайтесь!",
                "§e[Аркадий]: §1Звук Вардена заперт в стали! ПКМ — ТРИ звуковых луча подряд, пробивающих любую броню. §1Скалковый Клинок Глубин§f!",
                "§e[Аркадий]: §1Слепец замолчал навсегда. Вы услышали бездну и не сошли с ума — редкий дар. Осталось пять клинков!",
                "Эхо Вардена Могильный Слепец", 820.0, BossBar.Color.BLUE
        ));

        STAGES.add(new StageData(
                11, "Глава 11: Звездный Опад",
                List.of(
                        new Requirement(Items.DIAMOND, "diamond", "Алмаз", 20),
                        new Requirement(Items.GLOWSTONE_DUST, "glowstone", "Светопыль", 48),
                        new Requirement(Items.PRISMARINE_CRYSTALS, "prismarine", "Кристалл призмарина", 24),
                        new Requirement(Items.GOLDEN_CARROT, "carrot", "Золотая морковь", 16)
                ),
                () -> EightBladesMod.STARFALL_SWORD, "§e[XI] Клинок Звездного Опада",
                "§e[Аркадий]: §fЛюцис судит по свету в душе. Нужно §b20 Алмазов§f, §e48 Светопыли§f, §324 Призмарина §fи §616 Золотой моркови§f. §eСовет: метеорит в Компасе сыплет и светопыль, и призмарин!",
                "§e[Аркадий]: §eЗвёзды легли на кромку! ПКМ — восемь метеоритов с неба. Держите §eКлинок Звездного Опада§f!",
                "§e[Аркадий]: §eИнквизитор погас! Ваш свет оказался ярче его суда. Дальше — моя родная стихия. Адская кузня. Не обожгитесь.",
                "Астральный Инквизитор Люцис", 920.0, BossBar.Color.YELLOW
        ));

        STAGES.add(new StageData(
                12, "Глава 12: Адская Кузня",
                List.of(
                        new Requirement(Items.NETHERITE_INGOT, "netherite", "Незеритовый слиток", 2),
                        new Requirement(Items.QUARTZ, "quartz", "Кварц", 64),
                        new Requirement(Items.MAGMA_BLOCK, "magma_block", "Магма-блок", 32),
                        new Requirement(Items.OBSIDIAN, "obsidian", "Обсидиан", 24)
                ),
                () -> EightBladesMod.HELLFORGE_SWORD, "§c[XII] Клинок Адской Кузни",
                "§e[Аркадий]: §fВулкан кует в лаве. Мне нужны §82 Незеритовых слитка§f, §f64 Кварца§f, §c32 Магма-блока §fи §524 Обсидиана§f. Незерит реально выбить из Алмазного Кейса — проверено!",
                "§e[Аркадий]: §cАдский жар в ваших руках! ПКМ — разлом земли и расширяющееся огненное кольцо. §cКлинок Адской Кузни§f!",
                "§e[Аркадий]: §cВладыка горнила остыл! Вы ковёте лучше меня, признаю. Но время... время не куётся молотом. Его нужно остановить.",
                "Владыка Адского Горнила Вулкан", 1050.0, BossBar.Color.RED
        ));

        STAGES.add(new StageData(
                13, "Глава 13: Искажение Времени",
                List.of(
                        new Requirement(Items.DIAMOND, "diamond", "diamond13", 26),
                        new Requirement(Items.CLOCK, "clock", "Часы", 8),
                        new Requirement(Items.REDSTONE_BLOCK, "redstone_block", "Блок редстоуна", 20),
                        new Requirement(Items.ENDER_EYE, "ender_eye", "Око Эндера", 16)
                ),
                () -> EightBladesMod.CHRONO_SWORD, "§6[XIII] Хроно-Клинок Искажения",
                "§e[Аркадий]: §fХронос крадёт секунды у живых. Верните их: §b26 Алмазов§f, §e8 Часов§f, §420 Блоков редстоуна §fи §216 Очей Эндера§f!",
                "§e[Аркадий]: §6ВРЕМЯ ПОДЧИНЯЕТСЯ ВАМ! ПКМ — ХРОНОСТАЗИС: враги реально замирают на 7 секунд, а потом получают весь накопленный урон разом!",
                "§e[Аркадий]: §6Хранитель Времени опоздал на собственную смерть! Ха! Осталось двое: Эон и... тот, о ком я боюсь говорить вслух.",
                "Хранитель Времени Хронос", 1200.0, BossBar.Color.YELLOW
        ));

        STAGES.add(new StageData(
                14, "Глава 14: Астральный Разлом",
                List.of(
                        new Requirement(Items.DIAMOND, "diamond", "Алмаз", 32),
                        new Requirement(Items.CRYING_OBSIDIAN, "crying_obsidian", "Плачущий обсидиан", 12),
                        new Requirement(Items.ENDER_PEARL, "pearl", "Жемчуг Эндера", 24),
                        new Requirement(Items.OBSIDIAN, "obsidian", "Обсидиан", 32)
                ),
                () -> EightBladesMod.ASTRAL_SWORD, "§d[XIV] Клинок Астрального Разлома",
                "§e[Аркадий]: §fЧерепов иссушителя НЕ надо — я не хочу звать эту тварь сюда! Вместо них §5ПЛАЧУЩИЙ ОБСИДИАН§f (бартер с пиглинами или Золотой Кейс). Несите §b32 Алмаза§f, §512 Плачущего обсидиана§f, §324 Жемчуга §fи §532 Обсидиана§f!",
                "§e[Аркадий]: §dГрань реальности разорвана! ПКМ — чёрная дыра 3 секунды засасывает толпы, потом взрыв антиматерии. §dКлинок Астрального Разлома§f!",
                "§e[Аркадий]: §dЭон схлопнулся... Ребята, вы сделали то, что не смог ни один кузнец до меня. Остался ОН. Архонт Пустоты Малкор. Держитесь вместе — врозь он вас сотрёт.",
                "Пожиратель Пространства Эон", 1400.0, BossBar.Color.PURPLE
        ));

        STAGES.add(new StageData(
                15, "Глава 15: ФИНАЛ — Первородное Затмение",
                List.of(
                        new Requirement(Items.DIAMOND, "diamond", "Алмаз", 48),
                        new Requirement(Items.NETHERITE_INGOT, "netherite", "Незеритовый слиток", 6),
                        new Requirement(Items.GOLDEN_APPLE, "gapple", "Золотое яблоко", 8),
                        new Requirement(Items.CRYING_OBSIDIAN, "crying_obsidian", "Плачущий обсидиан", 32),
                        new Requirement(Items.OBSIDIAN, "obsidian", "Обсидиан", 64)
                ),
                () -> EightBladesMod.ECLIPSE_SWORD, "§5§l[XV] Клинок Первородного Затмения",
                "§e[Аркадий]: §c§lДИМА! УЛЬЯНА! ЭТО ФИНАЛ! §fЯ ковал этот клинок в мыслях всю жизнь. Принесите §b48 Алмазов§f, §86 Незеритовых слитков§f, §68 Золотых яблок§f, §532 Плачущего обсидиана §fи §564 Обсидиана§f. И ради всего святого — выберите разные роли!",
                "§e[Аркадий]: §5§lВЕЛИЧАЙШИЙ КЛИНОК ВЫКОВАН! §fПКМ — ТРИ волны катаклизма: молнии, звуковые взрывы и полное исцеление дуэта. У Малкора §c1800 HP §fи §e3 фазы§f. Вперёд, легенды!",
                "§e[Аркадий]: §6§l«...Я ковал мечи семьдесят лет. Но最好的 клинок — это не сталь. Это вы двое. Спасибо, Дима. Спасибо, Ульяна.»",
                "АРХОНТ ПУСТОТЫ МАЛКОР", 1800.0, BossBar.Color.PINK
        ));
    }

    private static final String[] IDLE_HINTS = {
            "§e[Аркадий]: §7Не забывайте про Компас — там бесплатный кейс каждые 5 минут!",
            "§e[Аркадий]: §7Дима, Ульяна, если стоите рядом с разными ролями — Резонанс даёт вам Силу!",
            "§e[Аркадий]: §7Мечи не ломаются и не бьют друг друга. Бейте смело, даже сквозь союзника!",
            "§e[Аркадий]: §7Застряли на ресурсах? Крутите кейс или зовите ивент — метеорит, комету, гоблина или нежить!",
            "§e[Аркадий]: §7Ресурсы можно кидать (Q) мне под ноги или тыкать в меня предметом ПКМ.",
            "§e[Аркадий]: §7ПКМ мечом — способность. Обычные мечи тут рассыпаются в прах, помните!"
    };

    // ==================== ДОСТУП ====================
    public static StoryManager get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(
                StoryManager::fromNbt, StoryManager::new, "eightblades_story");
    }

    public static StageData getStageData(int stage) {
        int idx = Math.max(0, Math.min(STAGES.size() - 1, stage - 1));
        return STAGES.get(idx);
    }

    public boolean isStoryStarted() { return storyStarted; }
    public int getCurrentStage() { return currentStage; }
    public boolean isSwordCraftedForStage() { return swordCraftedForStage; }
    public String getPlayerRole(UUID uuid) { return playerRoles.getOrDefault(uuid, "NONE"); }
    public String getPlayerIdentity(UUID uuid) { return playerIdentities.getOrDefault(uuid, "NONE"); }

    public String getRoleDisplayName(String roleCode) {
        return switch (roleCode) {
            case "WARRIOR" -> "§c⚔ Хранитель Ярости";
            case "MAGE" -> "§b✦ Ткач Стихий";
            default -> "§7Не выбрана (ПКМ Компасом)";
        };
    }

    public String getIdentityDisplayName(String code) {
        return switch (code) {
            case "DIMA" -> "§b♂ Дима";
            case "ULYANA" -> "§d♀ Ульяна";
            default -> "§7Не выбран (ПКМ Компасом)";
        };
    }

    public boolean areDuoPartners(ServerPlayerEntity a, ServerPlayerEntity b) {
        if (a == null || b == null || a == b) return false;
        String ia = getPlayerIdentity(a.getUuid());
        String ib = getPlayerIdentity(b.getUuid());
        return ("DIMA".equals(ia) && "ULYANA".equals(ib)) || ("ULYANA".equals(ia) && "DIMA".equals(ib));
    }

    public void setPlayerRole(ServerPlayerEntity player, String roleCode) {
        playerRoles.put(player.getUuid(), roleCode);
        markDirty();
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        if ("WARRIOR".equals(roleCode)) {
            broadcast(player.getServer(), "§6[Судьба] §e" + player.getName().getString() + " §fвыбрал роль: §c⚔ Хранитель Ярости §7(+HP, Сопротивление, +35% урона навыков)!");
        } else if ("MAGE".equals(roleCode)) {
            broadcast(player.getServer(), "§6[Судьба] §e" + player.getName().getString() + " §fвыбрал роль: §b✦ Ткач Стихий §7(Скорость, -35% перезарядки, лечение дуэта)!");
        }
        syncHudToAll(player.getServer());
    }

    public void setPlayerIdentity(ServerPlayerEntity player, String code) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (Map.Entry<UUID, String> e : playerIdentities.entrySet()) {
            if (!e.getKey().equals(player.getUuid()) && e.getValue().equals(code)) {
                ServerPlayerEntity other = server.getPlayerManager().getPlayer(e.getKey());
                String who = other != null ? other.getName().getString() : "другой игрок";
                player.sendMessage(Text.literal("§c[Компас] §fЭту личность уже взял §e" + who + "§f! Выберите вторую — дуэт должен быть Дима + Ульяна."), false);
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.7f);
                return;
            }
        }
        playerIdentities.put(player.getUuid(), code);
        markDirty();
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.4f);
        ServerWorld w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.7, 0.4, 0.12);
        broadcast(server, "§6[Судьба] §e" + player.getName().getString() + " §fтеперь §l" + getIdentityDisplayName(code) + "§f!");
        if ("DIMA".equals(code)) {
            broadcast(server, "§e[Аркадий]: §bДима§f, ты — щит этого дуэта. Обычно ему идёт роль §c⚔ Хранителя Ярости§f, но выбор за тобой!");
        } else {
            broadcast(server, "§e[Аркадий]: §dУльяна§f, ты — сердце дуэта. Тебе часто подходит §b✦ Ткач Стихий§f, но решать тебе!");
        }
        checkDuoReady(server);
        syncHudToAll(server);
    }

    private void checkDuoReady(MinecraftServer server) {
        boolean dima = false, ulyana = false;
        for (String s : playerIdentities.values()) {
            if ("DIMA".equals(s)) dima = true;
            if ("ULYANA".equals(s)) ulyana = true;
        }
        if (dima && ulyana) {
            broadcast(server, "§6§l★ ДУЭТ СОБРАН: ДИМА И УЛЬЯНА! §fТеперь ваши клинки §aне могут ранить друг друга§f, а рядом включается Резонанс!");
        }
    }

    public static boolean isBlacksmith(Entity entity) {
        return entity != null && entity.getCommandTags().contains(BLACKSMITH_TAG);
    }

    public static boolean isBoss(Entity entity) {
        return entity != null && entity.getCommandTags().contains(BOSS_TAG);
    }

    public void giveCompassIfNeeded(ServerPlayerEntity player) {
        boolean hasCompass = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(Items.COMPASS)) {
                hasCompass = true;
                break;
            }
        }
        if (!hasCompass) {
            ItemStack compass = new ItemStack(Items.COMPASS);
            compass.setCustomName(Text.literal("§6✦ Компас Судьбы [Дима и Ульяна] §7(ПКМ)"));
            NbtCompound display = compass.getOrCreateSubNbt("display");
            NbtList lore = new NbtList();
            lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal("§7Артефакт привязан к душе: §cнельзя выбросить"))));
            lore.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal("§eПКМ §7— личности, роли, кейс и ивенты"))));
            display.put("Lore", lore);
            player.getInventory().insertStack(compass);
        }
    }

    // ==================== СТАРТ / СБРОС ====================
    public void startStory(ServerPlayerEntity starter) {
        MinecraftServer server = starter.getServer();
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            giveCompassIfNeeded(p);
        }
        if (!storyStarted) {
            storyStarted = true;
            currentStage = 1;
            swordCraftedForStage = false;
            deliveredItems.clear();
            markDirty();
            broadcast(server, "§8==================================================");
            broadcast(server, "§6§l  ✦ ЛЕГЕНДА ПЯТНАДЦАТИ КЛИНКОВ: ДИМА И УЛЬЯНА ✦");
            broadcast(server, "§e[Аркадий]: §f«Слушайте старика. Тысячу лет назад Архонт Пустоты Малкор");
            broadcast(server, "§f   проклял этот мир: любая обычная сталь рассыпается в прах.");
            broadcast(server, "§f   Спасти нас могут лишь двое — §bДима §fи §dУльяна§f, и лишь пятнадцать");
            broadcast(server, "§f   клинков, выкованных вместе. Я ковал их всю жизнь. Помогите мне.»");
            broadcast(server, "§7Откройте §eКомпас (ПКМ)§7: выберите §fкто вы§7, свою §fРоль§7, кейс и ивенты.");
            broadcast(server, "§8==================================================");
        }
        spawnOrTeleportBlacksmith(starter);
        if (currentStage <= 15) {
            broadcast(server, getStageData(currentStage).npcStartQuote());
        }
        syncHudToAll(server);
    }

    public void resetStory(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        clearExistingBoss(server.getOverworld());
        storyStarted = false;
        currentStage = 1;
        swordCraftedForStage = false;
        bossSpawnCountdown = -1;
        deliveredItems.clear();
        bossBar.clearPlayers();
        markDirty();
        broadcast(server, "§e[Аркадий]: §f«Ну что ж, начнём легенду заново. Молот не устаёт.» Введите §a/st§f!");
        syncHudToAll(server);
    }

    // ==================== КУЗНЕЦ ====================
    public void spawnOrTeleportBlacksmith(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        VillagerEntity existing = findBlacksmith(world);
        Vec3d look = player.getRotationVec(1.0f);
        double tx = player.getX() + look.x * 2.5;
        double tz = player.getZ() + look.z * 2.5;
        double ty = player.getY();
        if (existing != null && existing.isAlive()) {
            existing.refreshPositionAndAngles(tx, ty, tz, player.getYaw() + 180.0f, 0.0f);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, existing.getX(), existing.getY() + 1.0, existing.getZ(), 15, 0.4, 0.5, 0.4, 0.05);
            world.playSound(null, existing.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            return;
        }
        VillagerEntity villager = EntityType.VILLAGER.create(world);
        if (villager != null) {
            villager.refreshPositionAndAngles(tx, ty, tz, player.getYaw() + 180.0f, 0.0f);
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.WEAPONSMITH).withLevel(5));
            villager.setCustomName(Text.literal("§6§lКузнец Аркадий §e[Кидайте ресурсы (Q) / ПКМ]"));
            villager.setCustomNameVisible(true);
            villager.setInvulnerable(true);
            villager.setAiDisabled(true);
            villager.setPersistent();
            villager.addCommandTag(BLACKSMITH_TAG);
            world.spawnEntity(villager);
            this.blacksmithUuid = villager.getUuid();
            markDirty();
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.0, villager.getZ(), 35, 0.5, 0.8, 0.5, 0.1);
            world.playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.2f, 1.0f);
        }
    }

    @Nullable
    public VillagerEntity findBlacksmith(ServerWorld world) {
        if (blacksmithUuid != null) {
            Entity e = world.getEntity(blacksmithUuid);
            if (e instanceof VillagerEntity v && v.isAlive()) return v;
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            List<VillagerEntity> list = world.getEntitiesByClass(VillagerEntity.class, p.getBoundingBox().expand(64.0), StoryManager::isBlacksmith);
            if (!list.isEmpty()) {
                blacksmithUuid = list.get(0).getUuid();
                return list.get(0);
            }
        }
        return null;
    }

    @Nullable
    public LivingEntity findActiveBoss(ServerWorld world) {
        if (activeBossUuid != null) {
            Entity e = world.getEntity(activeBossUuid);
            if (e instanceof LivingEntity living && living.isAlive()) return living;
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(96.0), StoryManager::isBoss);
            if (!list.isEmpty()) {
                activeBossUuid = list.get(0).getUuid();
                return list.get(0);
            }
        }
        return null;
    }

    private void clearExistingBoss(ServerWorld world) {
        LivingEntity boss = findActiveBoss(world);
        if (boss != null) boss.discard();
        for (ServerPlayerEntity p : world.getPlayers()) {
            List<MobEntity> minions = world.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(80.0),
                    m -> m.getCommandTags().contains(MINION_TAG) || m.getCommandTags().contains(BOSS_TAG));
            for (MobEntity m : minions) m.discard();
        }
        activeBossUuid = null;
        bossBar.clearPlayers();
    }

    // ==================== СДАЧА РЕСУРСОВ ====================
    public boolean trySubmitStack(MinecraftServer server, ItemStack stack) {
        if (!storyStarted || currentStage > 15 || swordCraftedForStage || stack.isEmpty()) return false;
        StageData stage = getStageData(currentStage);
        for (Requirement req : stage.requirements()) {
            if (stack.isOf(req.item())) {
                int current = deliveredItems.getOrDefault(req.key(), 0);
                int needed = req.requiredCount() - current;
                if (needed > 0) {
                    int take = Math.min(needed, stack.getCount());
                    stack.decrement(take);
                    deliveredItems.put(req.key(), current + take);
                    markDirty();
                    broadcast(server, "§a[Аркадий] §fПринято: §e" + req.displayName() + " +" + take
                            + " §7(" + (current + take) + "/" + req.requiredCount() + ")");
                    checkStageRequirementsComplete(server);
                    syncHudToAll(server);
                    return true;
                }
            }
        }
        return false;
    }

    private void checkStageRequirementsComplete(MinecraftServer server) {
        StageData stage = getStageData(currentStage);
        for (Requirement req : stage.requirements()) {
            if (deliveredItems.getOrDefault(req.key(), 0) < req.requiredCount()) return;
        }
        swordCraftedForStage = true;
        bossSpawnCountdown = 100;
        markDirty();
        broadcast(server, "§8--------------------------------------------------");
        broadcast(server, stage.npcForgedQuote());
        broadcast(server, "§6§l✦ ОБА ИГРОКА ПОЛУЧАЮТ: " + stage.swordName() + " §6§l✦");
        broadcast(server, "§c⚠ Босс §e" + stage.bossName() + " §cпоявится через 5 секунд!");
        broadcast(server, "§8--------------------------------------------------");
        Item rewardItem = stage.swordSupplier().get();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack reward = new ItemStack(rewardItem);
            if (!player.getInventory().insertStack(reward)) player.dropItem(reward, false);
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.0f, 1.1f);
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8f, 1.2f);
            player.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.2, player.getZ(), 45, 0.5, 0.8, 0.5, 0.15);
        }
    }

    // ==================== БОССЫ ====================
    public void spawnCurrentStageBoss(ServerWorld world, BlockPos centerPos) {
        if (currentStage > 15) return;
        clearExistingBoss(world);
        StageData stage = getStageData(currentStage);
        MobEntity boss = createBossEntityForStage(world, currentStage);
        if (boss == null) return;
        boss.refreshPositionAndAngles(centerPos.getX() + 6.0, centerPos.getY() + 0.5, centerPos.getZ() + 6.0, 0.0f, 0.0f);
        boss.addCommandTag(BOSS_TAG);
        boss.setPersistent();
        boss.setCustomNameVisible(true);
        EntityAttributeInstance maxHpAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(stage.bossMaxHp());
        EntityAttributeInstance kbAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kbAttr != null) kbAttr.setBaseValue(0.65);
        EntityAttributeInstance speedAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && currentStage >= 3) speedAttr.setBaseValue(Math.max(speedAttr.getBaseValue(), 0.29));
        boss.setHealth((float) stage.bossMaxHp());
        bossPhase = 1;
        bossAbilityCooldown = 80;
        String hpTitle = "§6[Босс " + currentStage + "/15] §e" + stage.bossName() + " §c❤ " + (int) stage.bossMaxHp() + " / " + (int) stage.bossMaxHp() + " HP";
        boss.setCustomName(Text.literal(hpTitle));
        world.spawnEntity(boss);
        activeBossUuid = boss.getUuid();
        markDirty();
        bossBar.setColor(stage.bossColor());
        bossBar.setName(Text.literal(hpTitle));
        bossBar.setPercent(1.0f);
        for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) bossBar.addPlayer(p);
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2f, 1.0f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0, boss.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
    }

    private MobEntity createBossEntityForStage(ServerWorld world, int stage) {
        switch (stage) {
            case 1 -> {
                ZombieEntity z = EntityType.ZOMBIE.create(world);
                if (z != null) {
                    z.setBaby(false);
                    z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.SLIME_BLOCK));
                    z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                    z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.MUD));
                }
                return z;
            }
            case 2 -> {
                HuskEntity h = EntityType.HUSK.create(world);
                if (h != null) {
                    h.setBaby(false);
                    h.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CACTUS));
                    h.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                    h.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
                }
                return h;
            }
            case 3 -> {
                CaveSpiderEntity s = EntityType.CAVE_SPIDER.create(world);
                if (s != null) s.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1, false, false));
                return s;
            }
            case 4 -> {
                IronGolemEntity g = EntityType.IRON_GOLEM.create(world);
                if (g != null) g.setPlayerCreated(false);
                return g;
            }
            case 5 -> {
                StrayEntity st = EntityType.STRAY.create(world);
                if (st != null) {
                    st.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    st.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    st.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
                }
                return st;
            }
            case 6 -> {
                WitherSkeletonEntity ws = EntityType.WITHER_SKELETON.create(world);
                if (ws != null) {
                    ws.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                    ws.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                    ws.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
                }
                return ws;
            }
            case 7 -> {
                IronGolemEntity neb = EntityType.IRON_GOLEM.create(world);
                if (neb != null) {
                    neb.setPlayerCreated(false);
                    neb.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 999999, 0, false, false));
                }
                return neb;
            }
            case 8 -> {
                WitherSkeletonEntity ign = EntityType.WITHER_SKELETON.create(world);
                if (ign != null) {
                    ign.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                    ign.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    ign.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
                    ign.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 999999, 0, false, false));
                }
                return ign;
            }
            case 9 -> {
                SkeletonEntity sk = EntityType.SKELETON.create(world);
                if (sk != null) {
                    sk.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    sk.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    sk.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    sk.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1, false, false));
                }
                return sk;
            }
            case 10 -> {
                WitherSkeletonEntity sc = EntityType.WITHER_SKELETON.create(world);
                if (sc != null) {
                    sc.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.SCULK_CATALYST));
                    sc.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    sc.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.ECHO_SHARD));
                }
                return sc;
            }
            case 11 -> {
                VindicatorEntity vind = EntityType.VINDICATOR.create(world);
                if (vind != null) {
                    vind.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DRAGON_HEAD));
                    vind.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    vind.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
                }
                return vind;
            }
            case 12 -> {
                WitherSkeletonEntity vul = EntityType.WITHER_SKELETON.create(world);
                if (vul != null) {
                    vul.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                    vul.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    vul.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
                    vul.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
                    vul.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
                    vul.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 999999, 0, false, false));
                }
                return vul;
            }
            case 13 -> {
                StrayEntity chr = EntityType.STRAY.create(world);
                if (chr != null) {
                    chr.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CLOCK));
                    chr.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    chr.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    chr.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 2, false, false));
                }
                return chr;
            }
            case 14 -> {
                VindicatorEntity aeon = EntityType.VINDICATOR.create(world);
                if (aeon != null) {
                    aeon.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DRAGON_HEAD));
                    aeon.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    aeon.equipStack(EquipmentSlot.MAINHAND, new ItemStack(EightBladesMod.ASTRAL_SWORD));
                    aeon.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 999999, 0, false, false));
                }
                return aeon;
            }
            default -> {
                WitherSkeletonEntity archon = EntityType.WITHER_SKELETON.create(world);
                if (archon != null) {
                    archon.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                    archon.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    archon.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
                    archon.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
                    archon.equipStack(EquipmentSlot.MAINHAND, new ItemStack(EightBladesMod.ECLIPSE_SWORD));
                    archon.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 999999, 0, false, false));
                }
                return archon;
            }
        }
    }

    // ==================== ГЛАВНЫЙ ТИК ====================
    public void tick(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        long time = overworld.getTime();

        tickScheduled();
        tickFrozen(server);
        tickPlayersAndSynergy(server, time);
        RouletteManager.tick();
        EventManager.tick(server);

        if (!storyStarted) {
            if (time % 20 == 0) syncHudToAll(server);
            return;
        }

        // Автоподбор ресурсов кузнецом
        if (time % 10 == 0 && currentStage <= 15 && !swordCraftedForStage) {
            VillagerEntity smith = findBlacksmith(overworld);
            if (smith != null) {
                Box pickupBox = smith.getBoundingBox().expand(3.5);
                List<ItemEntity> drops = overworld.getEntitiesByClass(ItemEntity.class, pickupBox, ItemEntity::isAlive);
                for (ItemEntity itemEntity : drops) {
                    ItemStack stack = itemEntity.getStack();
                    if (trySubmitStack(server, stack)) {
                        overworld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, smith.getX(), smith.getY() + 1.2, smith.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                        overworld.spawnParticles(ParticleTypes.ENCHANT, smith.getX(), smith.getY() + 1.5, smith.getZ(), 20, 0.5, 0.5, 0.5, 0.2);
                        overworld.playSound(null, smith.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
                        if (stack.isEmpty()) itemEntity.discard();
                    }
                }
            }
        }

        // Периодические реплики кузнеца
        if (currentStage <= 15 && !swordCraftedForStage) {
            long now = System.currentTimeMillis();
            if (now - lastHintTime > 180_000L) {
                lastHintTime = now;
                broadcast(server, IDLE_HINTS[RNG.nextInt(IDLE_HINTS.length)]);
            }
        }

        if (bossSpawnCountdown > 0) {
            bossSpawnCountdown--;
            if (bossSpawnCountdown == 0) {
                VillagerEntity smith = findBlacksmith(overworld);
                BlockPos center = smith != null ? smith.getBlockPos()
                        : (!overworld.getPlayers().isEmpty() ? overworld.getPlayers().get(0).getBlockPos() : BlockPos.ORIGIN);
                spawnCurrentStageBoss(overworld, center);
            }
        }

        if (currentStage <= 15 && swordCraftedForStage && bossSpawnCountdown <= 0) {
            LivingEntity boss = findActiveBoss(overworld);
            if (boss != null && boss.isAlive()) {
                tickActiveBoss(server, overworld, boss);
            } else if (activeBossUuid != null) {
                onBossDefeated(server, overworld);
            }
        }

        if (time % 15 == 0) syncHudToAll(server);
    }

    private void tickPlayersAndSynergy(MinecraftServer server, long time) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        ServerPlayerEntity warriorPlayer = null;
        ServerPlayerEntity magePlayer = null;

        for (ServerPlayerEntity p : players) {
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (isBannedVanillaSword(stack.getItem())) {
                    inv.setStack(i, ItemStack.EMPTY);
                    p.playSound(SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                    p.sendMessage(Text.literal("§c[Проклятие Печати] §7Обычный меч рассыпался в прах! Куйте Магические Клинки у Аркадия."), true);
                }
            }
            String role = getPlayerRole(p.getUuid());
            if ("WARRIOR".equals(role)) {
                warriorPlayer = p;
                if (time % 40 == 0) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 100, 0, true, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, false, true));
                }
            } else if ("MAGE".equals(role)) {
                magePlayer = p;
                if (time % 40 == 0) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, true, false, true));
                }
            }
        }

        if (time % 40 == 0 && warriorPlayer != null && magePlayer != null
                && areDuoPartners(warriorPlayer, magePlayer)
                && warriorPlayer.getWorld() == magePlayer.getWorld()
                && warriorPlayer.squaredDistanceTo(magePlayer) <= 256.0) {
            warriorPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 80, 0, true, false, true));
            magePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 80, 0, true, false, true));
            ServerWorld w = warriorPlayer.getServerWorld();
            Vec3d mid = warriorPlayer.getPos().add(magePlayer.getPos()).multiply(0.5).add(0, 1.0, 0);
            w.spawnParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 4, 0.6, 0.4, 0.6, 0.01);
        }
    }

    public static boolean isBannedVanillaSword(Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD
                || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }

    private void tickActiveBoss(MinecraftServer server, ServerWorld world, LivingEntity boss) {
        StageData stage = getStageData(currentStage);
        float hp = Math.max(0.0f, boss.getHealth());
        float maxHp = boss.getMaxHealth();
        float ratio = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        String phaseSuffix = "";
        if (currentStage == 15) {
            if (hp <= 500.0f && bossPhase < 3) {
                bossPhase = 3;
                broadcast(server, "§4§l[МАЛКОР — ФАЗА 3/3]: §c«ПОСЛЕДНЕЕ ЗАТМЕНИЕ! ПУСТОТА ПОГЛОТИТ ВАС!»");
                broadcast(server, "§e[Аркадий]: §cОн в ярости! Держитесь рядом, жмите ПКМ и не давайте ему передышки!");
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.5f, 0.7f);
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1));
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 999999, 1));
            } else if (hp <= 1200.0f && bossPhase < 2) {
                bossPhase = 2;
                broadcast(server, "§5§l[МАЛКОР — ФАЗА 2/3]: §d«Призываю Стражей Затмения!»");
                broadcast(server, "§e[Аркадий]: §dУбейте теней сначала — иначе он задавит вас числом!");
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2f, 1.2f);
                spawnBossMinions(world, boss, EntityType.VEX, 4, "§dТень Затмения");
            }
            phaseSuffix = " §d[Фаза " + bossPhase + "/3]";
        }
        String barTitle = "§6[Босс " + currentStage + "/15] §e" + stage.bossName() + phaseSuffix
                + " §c❤ " + (int) Math.ceil(hp) + " / " + (int) maxHp + " HP";
        bossBar.setName(Text.literal(barTitle));
        bossBar.setPercent(ratio);
        boss.setCustomName(Text.literal(barTitle));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        }
        ServerPlayerEntity nearest = findNearestPlayer(world, boss);
        if (nearest != null && boss instanceof MobEntity mob && mob.getTarget() == null) mob.setTarget(nearest);
        if (bossAbilityCooldown > 0) {
            bossAbilityCooldown--;
            return;
        }
        if (nearest == null) return;
        bossAbilityCooldown = currentStage == 15 ? (bossPhase == 3 ? 55 : 75) : 90;
        executeBossSpecialAbility(server, world, boss, nearest, currentStage);
    }

    private void executeBossSpecialAbility(MinecraftServer server, ServerWorld world, LivingEntity boss, ServerPlayerEntity target, int stage) {
        Box area = boss.getBoundingBox().expand(11.0);
        List<ServerPlayerEntity> nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity.class, area, ServerPlayerEntity::isAlive);
        switch (stage) {
            case 1 -> {
                Vec3d dir = target.getPos().subtract(boss.getPos()).normalize();
                boss.setVelocity(dir.x * 0.9, 0.5, dir.z * 0.9);
                world.spawnParticles(ParticleTypes.SPLASH, boss.getX(), boss.getY() + 1.0, boss.getZ(), 45, 2.0, 0.5, 2.0, 0.15);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.2f, 0.6f);
                for (ServerPlayerEntity p : nearbyPlayers) p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
                spawnBossMinions(world, boss, EntityType.SLIME, 2, "§2Болотный комок");
            }
            case 2 -> {
                world.spawnParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 3.0, 1.0, 3.0, 0.2);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_HUSK_CONVERTED_TO_ZOMBIE, SoundCategory.HOSTILE, 1.2f, 0.9f);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.damage(p.getDamageSources().mobAttack(boss), 6.0f);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                }
            }
            case 3 -> {
                Vec3d behind = target.getPos().subtract(target.getRotationVec(1.0f).multiply(1.5));
                boss.refreshPositionAfterTeleport(behind.x, target.getY(), behind.z);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, boss.getX(), boss.getY() + 0.8, boss.getZ(), 30, 1.5, 0.5, 1.5, 0.1);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.HOSTILE, 1.4f, 0.7f);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 1));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2));
                }
            }
            case 4 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 1.4f, 0.6f);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.2, boss.getZ(), 60, 3.0, 1.2, 3.0, 0.2);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    Vec3d pull = boss.getPos().subtract(p.getPos()).normalize().multiply(0.8);
                    p.setVelocity(pull.x, 0.4, pull.z);
                    p.velocityModified = true;
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(p.getX(), p.getY(), p.getZ());
                        bolt.setCosmetic(true);
                        world.spawnEntity(bolt);
                    }
                    p.damage(p.getDamageSources().mobAttack(boss), 8.0f);
                }
            }
            case 5 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.4f, 0.6f);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 1.2, boss.getZ(), 85, 4.0, 1.5, 4.0, 0.08);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.setFrozenTicks(260);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                    p.damage(p.getDamageSources().mobAttack(boss), 9.0f);
                }
            }
            case 6 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 1.4f, 1.2f);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 2.5, 1.0, 2.5, 0.1);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.damage(p.getDamageSources().mobAttack(boss), 10.0f);
                    boss.heal(6.0f);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
                }
                spawnBossMinions(world, boss, EntityType.VEX, 2, "§4Кровавая Тень");
            }
            case 7 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.6f);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.0, boss.getZ(), 80, 4.0, 2.0, 4.0, 0.25);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addVelocity(0, 1.1, 0);
                    p.velocityModified = true;
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 2));
                    p.damage(p.getDamageSources().mobAttack(boss), 11.0f);
                }
            }
            case 8 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.4f, 0.7f);
                world.spawnParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 1.0, boss.getZ(), 90, 4.0, 1.2, 4.0, 0.12);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.setOnFireFor(6);
                    p.damage(p.getDamageSources().mobAttack(boss), 11.0f);
                }
            }
            case 9 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.HOSTILE, 1.5f, 0.8f);
                world.spawnParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 1.0, boss.getZ(), 70, 4.0, 1.5, 4.0, 0.2);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    Vec3d push = p.getPos().subtract(boss.getPos()).normalize().multiply(1.5);
                    p.setVelocity(push.x, 0.5, push.z);
                    p.velocityModified = true;
                    p.damage(p.getDamageSources().mobAttack(boss), 12.0f);
                }
            }
            case 10 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 1.0f);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0));
                    p.damage(p.getDamageSources().mobAttack(boss), 14.0f);
                }
            }
            case 11 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 1.4f, 1.2f);
                world.spawnParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1.5, boss.getZ(), 5, 2.0, 1.0, 2.0, 0.0);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                    p.damage(p.getDamageSources().mobAttack(boss), 15.0f);
                }
                spawnBossMinions(world, boss, EntityType.VEX, 2, "§eЗвездный Страж");
            }
            case 12 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.HOSTILE, 1.4f, 0.7f);
                world.spawnParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + 0.5, boss.getZ(), 70, 4.0, 1.0, 4.0, 0.15);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.setOnFireFor(8);
                    p.damage(p.getDamageSources().mobAttack(boss), 16.0f);
                }
            }
            case 13 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, 1.5f, 1.6f);
                world.spawnParticles(ParticleTypes.ENCHANT, boss.getX(), boss.getY() + 1.2, boss.getZ(), 90, 4.5, 1.5, 4.5, 0.25);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 3));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 2));
                    p.damage(p.getDamageSources().mobAttack(boss), 17.0f);
                }
            }
            case 14 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.6f);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.0, boss.getZ(), 90, 4.0, 1.5, 4.0, 0.25);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    Vec3d pull = boss.getPos().subtract(p.getPos()).normalize().multiply(1.0);
                    p.setVelocity(pull.x, 0.4, pull.z);
                    p.velocityModified = true;
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 1));
                    p.damage(p.getDamageSources().mobAttack(boss), 18.0f);
                }
                spawnBossMinions(world, boss, EntityType.VEX, 3, "§5Фантом Эона");
            }
            case 15 -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.8f);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.5, 0.5, 0.5, 0.0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 1.2, boss.getZ(), 100, 5.0, 2.0, 5.0, 0.15);
                for (ServerPlayerEntity p : nearbyPlayers) {
                    if (bossPhase >= 2) {
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                        if (bolt != null) {
                            bolt.refreshPositionAfterTeleport(p.getX(), p.getY(), p.getZ());
                            bolt.setCosmetic(true);
                            world.spawnEntity(bolt);
                        }
                    }
                    if (bossPhase == 3) {
                        Vec3d pull = boss.getPos().subtract(p.getPos()).normalize().multiply(0.85);
                        p.setVelocity(pull.x, 0.45, pull.z);
                        p.velocityModified = true;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 90, 1));
                    }
                    p.damage(p.getDamageSources().mobAttack(boss), bossPhase == 3 ? 19.0f : 15.0f);
                }
            }
        }
    }

    private <T extends MobEntity> void spawnBossMinions(ServerWorld world, LivingEntity boss, EntityType<T> type, int count, String name) {
        List<MobEntity> existing = world.getEntitiesByClass(MobEntity.class, boss.getBoundingBox().expand(24.0),
                e -> e.getCommandTags().contains(MINION_TAG));
        if (existing.size() >= 6) return;
        for (int i = 0; i < count; i++) {
            T m = type.create(world);
            if (m != null) {
                double ox = (world.random.nextDouble() - 0.5) * 4.0;
                double oz = (world.random.nextDouble() - 0.5) * 4.0;
                m.refreshPositionAndAngles(boss.getX() + ox, boss.getY() + 0.5, boss.getZ() + oz, 0.0f, 0.0f);
                if (m instanceof SlimeEntity slime) slime.setSize(1, true);
                m.setCustomName(Text.literal(name));
                m.setCustomNameVisible(true);
                m.addCommandTag(MINION_TAG);
                world.spawnEntity(m);
            }
        }
    }

    @Nullable
    private ServerPlayerEntity findNearestPlayer(ServerWorld world, Entity center) {
        ServerPlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isAlive() && !p.isSpectator()) {
                double d = p.squaredDistanceTo(center);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        }
        return best;
    }

    // ==================== ПОБЕДА НАД БОССОМ ====================
    private void onBossDefeated(MinecraftServer server, ServerWorld world) {
        StageData defeatedStage = getStageData(currentStage);
        activeBossUuid = null;
        bossBar.clearPlayers();

        // подчищаем прислужников
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            for (MobEntity m : world.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(80.0),
                    e -> e.getCommandTags().contains(MINION_TAG))) {
                world.spawnParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 8, 0.2, 0.3, 0.2, 0.03);
                m.discard();
            }
            p.heal(p.getMaxHealth());
            p.giveItemStack(new ItemStack(Items.GOLDEN_APPLE, 2));
            world.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 50, 0.6, 0.8, 0.6, 0.15);
        }

        broadcast(server, "§a§l★ БОСС ПОВЕРЖЕН: §e" + defeatedStage.bossName() + "§a§l! ★");
        broadcast(server, defeatedStage.npcVictoryQuote());

        if (currentStage < 15) {
            currentStage++;
            swordCraftedForStage = false;
            deliveredItems.clear();
            markDirty();
            broadcast(server, "§8--------------------------------------------------");
            broadcast(server, getStageData(currentStage).npcStartQuote());
        } else {
            currentStage = 16;
            swordCraftedForStage = true;
            markDirty();
            broadcast(server, "§6§l==================================================");
            broadcast(server, "§e§l  ★ ВЕЛИКАЯ ПОБЕДА! АРХОНТ ПУСТОТЫ МАЛКОР ПОВЕРЖЕН! ★");
            broadcast(server, "§f   Дима и Ульяна покорили все 15 Клинков");
            broadcast(server, "§f   и навсегда сняли Древнее Проклятие с этого мира!");
            broadcast(server, "§e[Аркадий]: §a«Молот остыл. Легенда — нет. Спасибо вам, дети.»");
            broadcast(server, "§6§l==================================================");
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.giveItemStack(new ItemStack(Items.NETHERITE_INGOT, 4));
                p.giveItemStack(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
                p.getServerWorld().spawnParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 2.0, p.getZ(), 80, 1.0, 1.5, 1.0, 0.25);
            }
        }
        syncHudToAll(server);
    }

    // ==================== МЕНЮ КОМПАСА ====================
    public void openCompassMenu(ServerPlayerEntity player) {
        SimpleInventory menuInv = new SimpleInventory(27);
        populateMenuInventory(player, menuInv);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, p) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, menuInv, 3) {
                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clickingPlayer) {
                        if (slotIndex >= 0 && slotIndex < 27) {
                            if (!(clickingPlayer instanceof ServerPlayerEntity sp)) return;
                            MinecraftServer srv = sp.getServer();
                            if (srv == null) return;
                            switch (slotIndex) {
                                case 1 -> { setPlayerIdentity(sp, "DIMA"); populateMenuInventory(sp, menuInv); sendContentUpdates(); }
                                case 7 -> { setPlayerIdentity(sp, "ULYANA"); populateMenuInventory(sp, menuInv); sendContentUpdates(); }
                                case 10 -> { setPlayerRole(sp, "WARRIOR"); populateMenuInventory(sp, menuInv); sendContentUpdates(); }
                                case 16 -> { setPlayerRole(sp, "MAGE"); populateMenuInventory(sp, menuInv); sendContentUpdates(); }
                                case 12 -> { sp.closeHandledScreen(); RouletteManager.openRouletteMenu(sp); }
                                case 3 -> { sp.closeHandledScreen(); EventManager.triggerMeteorEvent(sp); }
                                case 4 -> { sp.closeHandledScreen(); EventManager.triggerIceCometEvent(sp); }
                                case 5 -> { sp.closeHandledScreen(); EventManager.triggerGoblinEvent(sp); }
                                case 6 -> { sp.closeHandledScreen(); EventManager.triggerHordeEvent(sp); }
                                case 13 -> { sp.closeHandledScreen(); startStory(sp); }
                                case 21 -> { sp.closeHandledScreen(); spawnOrTeleportBlacksmith(sp); }
                                case 23 -> {
                                    sp.closeHandledScreen();
                                    if (storyStarted && currentStage <= 15 && swordCraftedForStage) {
                                        spawnCurrentStageBoss(sp.getServerWorld(), sp.getBlockPos());
                                        broadcast(srv, "§6[Сюжет] §e" + sp.getName().getString() + " §fпризвал босса текущей главы!");
                                    } else {
                                        sp.sendMessage(Text.literal("§cСначала сдайте все ресурсы Аркадию и получите меч этой главы!"), false);
                                    }
                                }
                                default -> { }
                            }
                            return;
                        }
                        super.onSlotClick(slotIndex, button, actionType, clickingPlayer);
                    }

                    @Override
                    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

                    @Override
                    public boolean canUse(PlayerEntity player) { return true; }
                },
                Text.literal("§8✦ §6Меню Судьбы: Дима и Ульяна §8✦")
        ));
    }

    private void populateMenuInventory(ServerPlayerEntity player, SimpleInventory inv) {
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.setCustomName(Text.literal(" "));
        for (int i = 0; i < 27; i++) inv.setStack(i, glass.copy());

        String currentRole = getPlayerRole(player.getUuid());
        String currentId = getPlayerIdentity(player.getUuid());

        // --- ЛИЧНОСТИ ---
        ItemStack dimaIcon = new ItemStack(Items.PLAYER_HEAD);
        dimaIcon.setCustomName(Text.literal("§b§l♂ Я — ДИМА " + ("DIMA".equals(currentId) ? "§a[ВЫБРАНО]" : "§7[Нажмите]")));
        setItemLore(dimaIcon, List.of(
                "§7Первый герой легенды.",
                "§f• Ваши мечи §aне ранят Ульяну",
                "§f• Резонанс Дуэта рядом с Ульяной",
                "§7Рекомендуемая роль: §c⚔ Хранитель Ярости",
                "§aНажмите, чтобы стать Димой!"
        ));
        inv.setStack(1, dimaIcon);

        ItemStack ulyanaIcon = new ItemStack(Items.POPPY);
        ulyanaIcon.setCustomName(Text.literal("§d§l♀ Я — УЛЬЯНА " + ("ULYANA".equals(currentId) ? "§a[ВЫБРАНО]" : "§7[Нажмите]")));
        setItemLore(ulyanaIcon, List.of(
                "§7Вторая героиня легенды.",
                "§f• Ваши мечи §aне ранят Диму",
                "§f• Резонанс Дуэта рядом с Димой",
                "§7Рекомендуемая роль: §b✦ Ткач Стихий",
                "§aНажмите, чтобы стать Ульяной!"
        ));
        inv.setStack(7, ulyanaIcon);

        // --- РОЛИ ---
        ItemStack warriorIcon = new ItemStack(Items.NETHERITE_CHESTPLATE);
        warriorIcon.setCustomName(Text.literal("§c§l⚔ Хранитель Ярости " + ("WARRIOR".equals(currentRole) ? "§a[ВЫБРАНО]" : "§7[Нажмите]")));
        setItemLore(warriorIcon, List.of(
                "§7Авангард дуэта!",
                "§f• +2 Сердца и Сопротивление I",
                "§f• +35% урона способностей [ПКМ]",
                "§f• Щит Поглощения при активации клинка",
                "§aНажмите, чтобы выбрать!"
        ));
        inv.setStack(10, warriorIcon);

        ItemStack mageIcon = new ItemStack(Items.ENCHANTED_BOOK);
        mageIcon.setCustomName(Text.literal("§b§l✦ Ткач Стихий " + ("MAGE".equals(currentRole) ? "§a[ВЫБРАНО]" : "§7[Нажмите]")));
        setItemLore(mageIcon, List.of(
                "§7Поддержка дуэта и контроль стихий!",
                "§f• Скорость I в походе и бою",
                "§f• -35% перезарядки способностей",
                "§f• Способности лечат обоих на 3 сердца",
                "§aНажмите, чтобы выбрать!"
        ));
        inv.setStack(16, mageIcon);

        // --- КЕЙС ---
        ItemStack caseIcon = new ItemStack(Items.CHEST);
        caseIcon.setCustomName(Text.literal("§6§l🎰 Кейс Судьбы (CS:GO Рулетка)"));
        setItemLore(caseIcon, List.of(
                "§7Быстрая лента — всего ~4 секунды!",
                "§e• Бесплатный спин каждые 5 минут",
                "§a• Ставки: 16 булыжника / 4 железа / 4 золота / 2 алмаза",
                "§d• Дроп: до 32 алмазов, незерит, эхо-осколки,",
                "§d  плачущий обсидиан и ресурсы для глав!",
                "§6Нажмите, чтобы открыть рулетку!"
        ));
        inv.setStack(12, caseIcon);

        // --- ИВЕНТЫ ---
        ItemStack meteorIcon = new ItemStack(Items.FIRE_CHARGE);
        meteorIcon.setCustomName(Text.literal("§e§l☄ Ивент: Звездный Метеорит"));
        setItemLore(meteorIcon, List.of(
                "§7Падение метеорита рядом с вами!",
                "§b• 12 Алмазов, 24 Кварца, Светопыль, Призмарин",
                "§7Перезарядка: 10 минут.",
                "§eНажмите для призыва!"
        ));
        inv.setStack(3, meteorIcon);

        ItemStack cometIcon = new ItemStack(Items.PACKED_ICE);
        cometIcon.setCustomName(Text.literal("§b§l❄ Ивент: Ледяная Комета §a[НОВОЕ]"));
        setItemLore(cometIcon, List.of(
                "§7Ледяная глыба падает с небес!",
                "§b• 40 Плотного льда (для Главы 5!), 64 Снежка,",
                "§b  16 Снежных блоков, 24 Лазурита, 4 Синего льда",
                "§aПлотный лёд ломается обычной киркой!",
                "§7Перезарядка: 8 минут.",
                "§eНажмите для призыва!"
        ));
        inv.setStack(4, cometIcon);

        ItemStack goblinIcon = new ItemStack(Items.GOLD_INGOT);
        goblinIcon.setCustomName(Text.literal("§6§l👺 Ивент: Жадный Гоблин"));
        setItemLore(goblinIcon, List.of(
                "§7Быстрый гоблин с мешком сокровищ!",
                "§fС каждого удара летят руды и изумруды.",
                "§aУспейте за 35 сек — ДЖЕКПОТ (16 алмазов + незерит)!",
                "§7Перезарядка: 8 минут.",
                "§6Нажмите для призыва!"
        ));
        inv.setStack(5, goblinIcon);

        ItemStack hordeIcon = new ItemStack(Items.ZOMBIE_HEAD);
        hordeIcon.setCustomName(Text.literal("§4§l☠ Ивент: Нашествие Нежити §a[НОВОЕ]"));
        setItemLore(hordeIcon, List.of(
                "§7Три волны монстров идут на вас!",
                "§c• Волна 1: 7 мобов, Волна 2: 10, Волна 3: 13",
                "§fВ последней волне появляются визер-скелеты.",
                "§aОтбейте все три — награда каждому:",
                "§a  8 алмазов, 3 золотых яблока, 10 изумрудов, опыт!",
                "§7Перезарядка: 10 минут.",
                "§4Нажмите, если не боитесь!"
        ));
        inv.setStack(6, hordeIcon);

        // --- СЮЖЕТ ---
        ItemStack storyIcon = new ItemStack(Items.NETHER_STAR);
        if (!storyStarted) {
            storyIcon.setCustomName(Text.literal("§a§l▶ Запустить Сюжет (/st)"));
            setItemLore(storyIcon, List.of(
                    "§7Нажмите здесь или введите §e/st§7,",
                    "§7чтобы призвать §6Кузнеца Аркадия §7и начать легенду!"
            ));
        } else if (currentStage <= 15) {
            StageData sd = getStageData(currentStage);
            storyIcon.setCustomName(Text.literal("§6§l✦ " + sd.title() + " §e[" + currentStage + "/15]"));
            List<String> lines = new ArrayList<>();
            lines.add("§7Награда главы: " + sd.swordName());
            lines.add("§7Босс главы: §c" + sd.bossName() + " (" + (int) sd.bossMaxHp() + " HP)");
            lines.add("");
            if (!swordCraftedForStage) {
                lines.add("§eНужно сдать Кузнецу Аркадию:");
                for (Requirement r : sd.requirements()) {
                    int got = deliveredItems.getOrDefault(r.key(), 0);
                    lines.add(" §f- " + r.displayName() + ": §a" + got + " / " + r.requiredCount());
                }
            } else {
                lines.add("§a✔ Меч выкован! Победите босса: §c" + sd.bossName());
            }
            setItemLore(storyIcon, lines);
        } else {
            storyIcon.setCustomName(Text.literal("§6§l★ СЮЖЕТ ПРОЙДЕН НА 100%! ★"));
            setItemLore(storyIcon, List.of("§aДима и Ульяна победили всех 15 боссов и спасли мир!"));
        }
        inv.setStack(13, storyIcon);

        ItemStack smithIcon = new ItemStack(Items.ANVIL);
        smithIcon.setCustomName(Text.literal("§e§l⚒ Позвать Кузнеца Аркадия"));
        setItemLore(smithIcon, List.of("§7Телепортирует кузнеца прямо к вам!"));
        inv.setStack(21, smithIcon);

        ItemStack bossIcon = new ItemStack(Items.DRAGON_HEAD);
        bossIcon.setCustomName(Text.literal("§c§l☠ Призвать / Перепризвать Босса"));
        setItemLore(bossIcon, List.of("§7Доступно после получения меча текущей главы."));
        inv.setStack(23, bossIcon);
    }

    private static void setItemLore(ItemStack stack, List<String> lines) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        for (String line : lines) {
            loreList.add(net.minecraft.nbt.NbtString.of(Text.Serializer.toJson(Text.literal(line))));
        }
        display.put("Lore", loreList);
    }

    // ==================== HUD ====================
    public void syncHudToAll(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        boolean synergyActive = false;
        for (int i = 0; i < players.size() && !synergyActive; i++) {
            for (int j = i + 1; j < players.size(); j++) {
                ServerPlayerEntity a = players.get(i);
                ServerPlayerEntity b = players.get(j);
                if (!areDuoPartners(a, b)) continue;
                String ra = getPlayerRole(a.getUuid());
                String rb = getPlayerRole(b.getUuid());
                boolean diffRoles = ("WARRIOR".equals(ra) && "MAGE".equals(rb)) || ("MAGE".equals(ra) && "WARRIOR".equals(rb));
                if (diffRoles && a.getWorld() == b.getWorld() && a.squaredDistanceTo(b) <= 256.0) {
                    synergyActive = true;
                    break;
                }
            }
        }
        List<String> lines = buildHudLines();
        for (ServerPlayerEntity player : players) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(storyStarted);
            buf.writeInt(currentStage);
            buf.writeBoolean(swordCraftedForStage);
            buf.writeString(getRoleDisplayName(getPlayerRole(player.getUuid())));
            buf.writeBoolean(synergyActive);
            buf.writeString(getIdentityDisplayName(getPlayerIdentity(player.getUuid())));
            buf.writeInt(lines.size());
            for (String line : lines) buf.writeString(line);
            ServerPlayNetworking.send(player, EightBladesMod.HUD_SYNC_ID, buf);
        }
    }

    private List<String> buildHudLines() {
        List<String> lines = new ArrayList<>();
        if (!storyStarted) {
            lines.add("§e1. Компас (ПКМ): выберите §fКТО ВЫ §eи Роль");
            lines.add("§a2. Введите §f/st §aдля старта сюжета");
            lines.add("§63. В компасе: Кейс и 4 Ивента!");
            return lines;
        }
        if (currentStage > 15) {
            lines.add("§6★ ФИНАЛ ПРОЙДЕН! ★");
            lines.add("§aДима и Ульяна победили всех 15 боссов!");
            return lines;
        }
        StageData stage = getStageData(currentStage);
        lines.add("§6" + stage.title());
        lines.add("§7Клинок: " + stage.swordName());
        if (!swordCraftedForStage) {
            lines.add("§eСдайте Аркадию (Q или ПКМ по нему):");
            for (Requirement req : stage.requirements()) {
                int got = deliveredItems.getOrDefault(req.key(), 0);
                boolean done = got >= req.requiredCount();
                String prefix = done ? "§a ✔ " : "§f • ";
                String countColor = done ? "§a" : "§e";
                lines.add(prefix + req.displayName() + ": " + countColor + got + "/" + req.requiredCount());
            }
        } else {
            lines.add("§a✔ Меч получен обоими игроками!");
            if (bossSpawnCountdown > 0) {
                lines.add("§c⚔ Босс появится через: §e" + (bossSpawnCountdown / 20 + 1) + " сек.");
            } else {
                lines.add("§c⚔ Победите босса: §f" + stage.bossName());
                lines.add("§7(ПКМ Компасом, если нужно перепризвать)");
            }
        }
        return lines;
    }

    public static void broadcast(MinecraftServer server, String message) {
        if (server == null) return;
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    public static void broadcastMessage(MinecraftServer server, String message) {
        broadcast(server, message);
    }

    // ==================== СОХРАНЕНИЕ ====================
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("storyStarted", storyStarted);
        nbt.putInt("currentStage", currentStage);
        nbt.putBoolean("swordCraftedForStage", swordCraftedForStage);
        if (blacksmithUuid != null) nbt.putUuid("blacksmithUuid", blacksmithUuid);
        if (activeBossUuid != null) nbt.putUuid("activeBossUuid", activeBossUuid);

        NbtCompound deliveredNbt = new NbtCompound();
        for (Map.Entry<String, Integer> e : deliveredItems.entrySet()) deliveredNbt.putInt(e.getKey(), e.getValue());
        nbt.put("deliveredItems", deliveredNbt);

        NbtList rolesList = new NbtList();
        for (Map.Entry<UUID, String> e : playerRoles.entrySet()) {
            NbtCompound r = new NbtCompound();
            r.putUuid("uuid", e.getKey());
            r.putString("role", e.getValue());
            rolesList.add(r);
        }
        nbt.put("playerRoles", rolesList);

        NbtList idList = new NbtList();
        for (Map.Entry<UUID, String> e : playerIdentities.entrySet()) {
            NbtCompound r = new NbtCompound();
            r.putUuid("uuid", e.getKey());
            r.putString("identity", e.getValue());
            idList.add(r);
        }
        nbt.put("playerIdentities", idList);
        return nbt;
    }

    public static StoryManager fromNbt(NbtCompound nbt) {
        StoryManager m = new StoryManager();
        m.storyStarted = nbt.getBoolean("storyStarted");
        m.currentStage = Math.max(1, nbt.getInt("currentStage"));
        m.swordCraftedForStage = nbt.getBoolean("swordCraftedForStage");
        if (nbt.containsUuid("blacksmithUuid")) m.blacksmithUuid = nbt.getUuid("blacksmithUuid");
        if (nbt.containsUuid("activeBossUuid")) m.activeBossUuid = nbt.getUuid("activeBossUuid");

        NbtCompound deliveredNbt = nbt.getCompound("deliveredItems");
        for (String key : deliveredNbt.getKeys()) m.deliveredItems.put(key, deliveredNbt.getInt(key));

        NbtList rolesList = nbt.getList("playerRoles", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rolesList.size(); i++) {
            NbtCompound r = rolesList.getCompound(i);
            m.playerRoles.put(r.getUuid("uuid"), r.getString("role"));
        }
        NbtList idList = nbt.getList("playerIdentities", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < idList.size(); i++) {
            NbtCompound r = idList.getCompound(i);
            m.playerIdentities.put(r.getUuid("uuid"), r.getString("identity"));
        }
        return m;
    }
}
