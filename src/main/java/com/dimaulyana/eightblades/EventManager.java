package com.dimaulyana.eightblades;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class EventManager {
    public static final String GOBLIN_TAG = "eightblades_goblin";
    public static final String HORDE_TAG = "eightblades_horde";
    private static final Random RNG = new Random();
    private static long lastMeteorTime = 0;
    private static long lastGoblinTime = 0;
    private static long lastCometTime = 0;
    private static long lastHordeTime = 0;
    private static final Map<UUID, Integer> activeGoblins = new HashMap<>();

    // ===== Нашествие Нежити =====
    private static boolean hordeActive = false;
    private static int hordeWave = 0;
    private static int hordeTicksUntilWave = 0;
    private static RegistryKey<World> hordeWorldKey = null;

    public static boolean triggerMeteorEvent(ServerPlayerEntity caller) {
        long now = System.currentTimeMillis();
        if (now - lastMeteorTime < 600_000L) {
            long secLeft = (600_000L - (now - lastMeteorTime)) / 1000L;
            caller.sendMessage(Text.literal("§cМетеоритный дождь на перезарядке! Осталось: §e" + secLeft + " сек."), false);
            return false;
        }
        lastMeteorTime = now;
        MinecraftServer server = caller.getServer();
        ServerWorld world = caller.getServerWorld();
        StoryManager.broadcast(server, "§8==================================================");
        StoryManager.broadcast(server, "§e§l   ☄ ВНИМАНИЕ: ЗВЕЗДНЫЙ МЕТЕОРИТ ПАДАЕТ С НЕБЕС! ☄");
        StoryManager.broadcast(server, "§f   Дима и Ульяна, ищите место падения в радиусе 20 блоков!");
        StoryManager.broadcast(server, "§8==================================================");
        Vec3d pPos = caller.getPos();
        double ox = (RNG.nextDouble() - 0.5) * 35.0;
        double oz = (RNG.nextDouble() - 0.5) * 35.0;
        BlockPos impactPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                new BlockPos((int) (pPos.x + ox), (int) pPos.y, (int) (pPos.z + oz)));
        world.playSound(null, impactPos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 2.0f, 0.8f);
        world.playSound(null, impactPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 2.0f, 0.7f);
        for (int y = impactPos.getY(); y < impactPos.getY() + 40; y += 2) {
            world.spawnParticles(ParticleTypes.LAVA, impactPos.getX() + 0.5, y, impactPos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.FLASH, impactPos.getX() + 0.5, y, impactPos.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impactPos.getX() + 0.5, impactPos.getY() + 1.0, impactPos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0);
        dropLootItem(world, impactPos, new ItemStack(Items.DIAMOND, 12));
        dropLootItem(world, impactPos, new ItemStack(Items.QUARTZ, 24));
        dropLootItem(world, impactPos, new ItemStack(Items.GLOWSTONE, 16));
        dropLootItem(world, impactPos, new ItemStack(Items.PRISMARINE_CRYSTALS, 12));
        dropLootItem(world, impactPos, new ItemStack(Items.GOLDEN_APPLE, 3));
        dropLootItem(world, impactPos, new ItemStack(Items.OBSIDIAN, 8));
        return true;
    }

    // ===== НОВЫЙ ИВЕНТ: Ледяная Комета (даёт Плотный лёд для Главы 5!) =====
    public static boolean triggerIceCometEvent(ServerPlayerEntity caller) {
        long now = System.currentTimeMillis();
        if (now - lastCometTime < 480_000L) {
            long secLeft = (480_000L - (now - lastCometTime)) / 1000L;
            caller.sendMessage(Text.literal("§cЛедяная комета на перезарядке! Осталось: §e" + secLeft + " сек."), false);
            return false;
        }
        lastCometTime = now;
        MinecraftServer server = caller.getServer();
        ServerWorld world = caller.getServerWorld();
        StoryManager.broadcast(server, "§8==================================================");
        StoryManager.broadcast(server, "§b§l   ❄ С НЕБЕС ПАДАЕТ ЛЕДЯНАЯ КОМЕТА! ❄");
        StoryManager.broadcast(server, "§f   Она несет Плотный лёд и снег — всё для Клинка Мерзлоты!");
        StoryManager.broadcast(server, "§8==================================================");
        Vec3d pPos = caller.getPos();
        double ox = (RNG.nextDouble() - 0.5) * 30.0;
        double oz = (RNG.nextDouble() - 0.5) * 30.0;
        BlockPos impactPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                new BlockPos((int) (pPos.x + ox), (int) pPos.y, (int) (pPos.z + oz)));
        world.playSound(null, impactPos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.AMBIENT, 2.0f, 0.6f);
        world.playSound(null, impactPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 1.5f, 1.3f);
        for (int y = impactPos.getY(); y < impactPos.getY() + 40; y += 2) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, impactPos.getX() + 0.5, y, impactPos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticles(ParticleTypes.END_ROD, impactPos.getX() + 0.5, y, impactPos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
        }
        world.spawnParticles(ParticleTypes.SNOWFLAKE, impactPos.getX() + 0.5, impactPos.getY() + 1.0, impactPos.getZ() + 0.5, 120, 3.0, 1.5, 3.0, 0.15);
        world.spawnParticles(ParticleTypes.EXPLOSION, impactPos.getX() + 0.5, impactPos.getY() + 1.0, impactPos.getZ() + 0.5, 2, 0.5, 0.5, 0.5, 0);
        // Плотный лёд добывается ОБЫЧНОЙ киркой — никакого шелкового касания!
        dropLootItem(world, impactPos, new ItemStack(Items.PACKED_ICE, 40));
        dropLootItem(world, impactPos, new ItemStack(Items.SNOWBALL, 64));
        dropLootItem(world, impactPos, new ItemStack(Items.SNOW_BLOCK, 16));
        dropLootItem(world, impactPos, new ItemStack(Items.LAPIS_LAZULI, 24));
        dropLootItem(world, impactPos, new ItemStack(Items.BLUE_ICE, 4));
        return true;
    }

    // ===== НОВЫЙ ИВЕНТ: Нашествие Нежити (3 волны + награда) =====
    public static boolean triggerHordeEvent(ServerPlayerEntity caller) {
        long now = System.currentTimeMillis();
        if (hordeActive) {
            caller.sendMessage(Text.literal("§cНашествие уже идет! Отбейте текущие волны!"), false);
            return false;
        }
        if (now - lastHordeTime < 600_000L) {
            long secLeft = (600_000L - (now - lastHordeTime)) / 1000L;
            caller.sendMessage(Text.literal("§cНашествие Нежити на перезарядке! Осталось: §e" + secLeft + " сек."), false);
            return false;
        }
        lastHordeTime = now;
        hordeActive = true;
        hordeWave = 0;
        hordeTicksUntilWave = 60;
        hordeWorldKey = caller.getWorld().getRegistryKey();
        MinecraftServer server = caller.getServer();
        StoryManager.broadcast(server, "§8==================================================");
        StoryManager.broadcast(server, "§4§l   ☠ НАШЕСТВИЕ НЕЖИТИ! ДЕРЖИТЕ ОБОРОНУ! ☠");
        StoryManager.broadcast(server, "§f   Дима и Ульяна, вас ждут §c3 волны §fмонстров!");
        StoryManager.broadcast(server, "§f   Отбейте все — и получите сокровища Аркадия!");
        StoryManager.broadcast(server, "§8==================================================");
        ServerWorld world = caller.getServerWorld();
        world.playSound(null, caller.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 0.7f);
        return true;
    }

       private static void spawnHordeWave(MinecraftServer server) {
        hordeWave++;
        ServerWorld world = hordeWorldKey == null ? null : server.getWorld(hordeWorldKey);
        if (world == null || world.getPlayers().isEmpty()) {
            finishHorde(server, false);
            return;
        }
        ServerPlayerEntity anchor = world.getPlayers().get(RNG.nextInt(world.getPlayers().size()));
        int count = 4 + hordeWave * 3; // 7, 10, 13
        StoryManager.broadcast(server, "§4[Нашествие] §cВОЛНА " + hordeWave + "/3§f: §e" + count + " §fмонстров наступают!");
        world.playSound(null, anchor.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
        for (int i = 0; i < count; i++) {
            MobEntity mob;
            int roll = RNG.nextInt(100);
            if (hordeWave >= 3 && roll < 20) {
                mob = EntityType.WITHER_SKELETON.create(world);
            } else if (roll < 50) {
                mob = EntityType.ZOMBIE.create(world);
            } else if (roll < 80) {
                mob = EntityType.SKELETON.create(world);
            } else {
                mob = EntityType.HUSK.create(world);
            }
            if (mob == null) continue;
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = 8.0 + RNG.nextDouble() * 6.0;
            double mx = anchor.getX() + Math.cos(angle) * dist;
            double mz = anchor.getZ() + Math.sin(angle) * dist;
            BlockPos ground = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                    new BlockPos((int) mx, (int) anchor.getY(), (int) mz));
            mob.refreshPositionAndAngles(mx, ground.getY(), mz, 0, 0);
            mob.addCommandTag(HORDE_TAG);
            mob.setPersistent();
            if (mob instanceof ZombieEntity z) z.setBaby(false);
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 1200, 0, false, false));
            world.spawnEntity(mob);
            world.spawnParticles(ParticleTypes.SOUL, mx, ground.getY() + 1.0, mz, 10, 0.3, 0.5, 0.3, 0.05);
        }
    }

        private static void finishHorde(MinecraftServer server, boolean victory) {
        hordeActive = false;
        hordeWave = 0;
        ServerWorld world = hordeWorldKey == null ? null : server.getWorld(hordeWorldKey);
        if (world == null) return;
        for (MobEntity m : world.getEntitiesByClass(MobEntity.class,
                new net.minecraft.util.math.Box(-30000000, -64, -30000000, 30000000, 320, 30000000),
                e -> e.getCommandTags().contains(HORDE_TAG))) {
            if (victory) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + 1.0, m.getZ(), 8, 0.2, 0.4, 0.2, 0.05);
            }
            m.discard();
        }
        if (victory) {
            StoryManager.broadcast(server, "§6§l★ НАШЕСТВИЕ ОТБИТО! Дима и Ульяна выстояли! Аркадий высыпает награду! ★");
            for (ServerPlayerEntity p : world.getPlayers()) {
                world.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
                dropLootItem(world, p.getBlockPos(), new ItemStack(Items.DIAMOND, 8));
                dropLootItem(world, p.getBlockPos(), new ItemStack(Items.GOLDEN_APPLE, 3));
                dropLootItem(world, p.getBlockPos(), new ItemStack(Items.EMERALD, 10));
                dropLootItem(world, p.getBlockPos(), new ItemStack(Items.EXPERIENCE_BOTTLE, 16));
            }
        }
    }
    public static boolean triggerGoblinEvent(ServerPlayerEntity caller) {
        long now = System.currentTimeMillis();
        if (now - lastGoblinTime < 480_000L) {
            long secLeft = (480_000L - (now - lastGoblinTime)) / 1000L;
            caller.sendMessage(Text.literal("§cОхота на гоблина на перезарядке! Осталось: §e" + secLeft + " сек."), false);
            return false;
        }
        lastGoblinTime = now;
        MinecraftServer server = caller.getServer();
        ServerWorld world = caller.getServerWorld();
        ZombieEntity goblin = EntityType.ZOMBIE.create(world);
        if (goblin == null) return false;
        Vec3d look = caller.getRotationVec(1.0f);
        double gx = caller.getX() + look.x * 6.0;
        double gz = caller.getZ() + look.z * 6.0;
        double gy = caller.getY() + 0.5;
        goblin.refreshPositionAndAngles(gx, gy, gz, 0, 0);
        goblin.setBaby(true);
        goblin.setCustomName(Text.literal("§6§l★ Жадный Гоблин-Кладоискатель §e[Бейте его!]"));
        goblin.setCustomNameVisible(true);
        goblin.addCommandTag(GOBLIN_TAG);
        goblin.setPersistent();
        goblin.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        goblin.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        goblin.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BUNDLE));
        goblin.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(70.0);
        goblin.setHealth(70.0f);
        goblin.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 999999, 1, false, false));
        world.spawnEntity(goblin);
        activeGoblins.put(goblin.getUuid(), 700);
        StoryManager.broadcast(server, "§6[Событие] §eРядом появился §6Жадный Гоблин-Кладоискатель§e! Бейте его — с каждого удара летят руды, а за победу ждет ДЖЕКПОТ!");
        world.playSound(null, goblin.getBlockPos(), SoundEvents.ENTITY_PIGLIN_ANGRY, SoundCategory.NEUTRAL, 1.4f, 1.3f);
        return true;
    }

    public static void onGoblinHit(ServerWorld world, ZombieEntity goblin, ServerPlayerEntity attacker) {
        world.playSound(null, goblin.getBlockPos(), SoundEvents.ENTITY_PIGLIN_HURT, SoundCategory.NEUTRAL, 1.2f, 1.5f);
        world.spawnParticles(ParticleTypes.CRIT, goblin.getX(), goblin.getY() + 0.5, goblin.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
        int roll = RNG.nextInt(100);
        if (roll < 40) {
            dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.GOLD_INGOT, 2));
        } else if (roll < 75) {
            dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.EMERALD, 1));
        } else if (roll < 90) {
            dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.IRON_INGOT, 3));
        } else {
            dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.DIAMOND, 1));
            world.playSound(null, goblin.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.4f);
        }
    }

    public static void onGoblinKilled(ServerWorld world, ZombieEntity goblin) {
        activeGoblins.remove(goblin.getUuid());
        StoryManager.broadcast(world.getServer(), "§6§l★ ДЖЕКПОТ! Жадный Гоблин повержен! Россыпь сокровищ на земле! ★");
        world.playSound(null, goblin.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, goblin.getX(), goblin.getY() + 0.8, goblin.getZ(), 40, 0.4, 0.5, 0.4, 0.1);
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.DIAMOND, 16));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.NETHERITE_SCRAP, 2));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.GOLDEN_APPLE, 4));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.REDSTONE_BLOCK, 8));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.EMERALD, 12));
    }

    public static void tick(MinecraftServer server) {
        // Тикаем нашествие
        if (hordeActive) {
            ServerWorld hordeWorld = hordeWorldKey == null ? null : server.getWorld(hordeWorldKey);
            if (hordeWorld == null || hordeWorld.getPlayers().isEmpty()) {
            if (hordeWorld == null || hordeWorld.getPlayers().isEmpty()) {
                hordeActive = false;
            } else {
                List<MobEntity> alive = hordeWorld.getEntitiesByClass(MobEntity.class,
                        new net.minecraft.util.math.Box(-30000000, -64, -30000000, 30000000, 320, 30000000),
                        e -> e.getCommandTags().contains(HORDE_TAG) && e.isAlive());
                if (hordeTicksUntilWave > 0) {
                    hordeTicksUntilWave--;
                    if (hordeTicksUntilWave == 0) {
                        spawnHordeWave(server);
                    }
                } else if (alive.isEmpty()) {
                    if (hordeWave >= 3) {
                        finishHorde(server, true);
                    } else {
                        hordeTicksUntilWave = 100; // 5 секунд передышки
                        StoryManager.broadcast(server, "§a[Нашествие] §fВолна " + hordeWave + " отбита! Следующая через 5 секунд...");
                    }
                }
            }
        }

        if (activeGoblins.isEmpty()) return;
        ServerWorld world = server.getOverworld();
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : activeGoblins.entrySet()) {
            int time = entry.getValue() - 1;
            ZombieEntity goblin = (ZombieEntity) world.getEntity(entry.getKey());
            if (goblin == null || !goblin.isAlive()) {
                toRemove.add(entry.getKey());
                continue;
            }
            if (time <= 0) {
                world.spawnParticles(ParticleTypes.CLOUD, goblin.getX(), goblin.getY() + 0.5, goblin.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                world.playSound(null, goblin.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.2f, 1.2f);
                StoryManager.broadcast(server, "§c[Событие] Жадный Гоблин скрылся в норе с остатками сокровищ!");
                goblin.discard();
                toRemove.add(entry.getKey());
            } else {
                activeGoblins.put(entry.getKey(), time);
            }
        }
        for (UUID id : toRemove) {
            activeGoblins.remove(id);
        }
    }

    private static void dropLootItem(ServerWorld world, BlockPos pos, ItemStack stack) {
        ItemEntity entity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setVelocity((RNG.nextDouble() - 0.5) * 0.25, 0.35, (RNG.nextDouble() - 0.5) * 0.25);
        world.spawnEntity(entity);
    }
}
