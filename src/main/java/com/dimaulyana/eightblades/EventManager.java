package com.dimaulyana.eightblades;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
    private static final Random RNG = new Random();

    private static long lastMeteorTime = 0;
    private static long lastGoblinTime = 0;

    private static final Map<UUID, Integer> activeGoblins = new HashMap<>();

    public static boolean triggerMeteorEvent(ServerPlayerEntity caller) {
        long now = System.currentTimeMillis();
        if (now - lastMeteorTime < 600_000L) { // 10 minutes
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
                new BlockPos((int)(pPos.x + ox), (int)pPos.y, (int)(pPos.z + oz)));

        world.playSound(null, impactPos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 2.0f, 0.8f);
        world.playSound(null, impactPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 2.0f, 0.7f);

        // Visual beam
        for (int y = impactPos.getY(); y < impactPos.getY() + 40; y += 2) {
            world.spawnParticles(ParticleTypes.LAVA, impactPos.getX() + 0.5, y, impactPos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.FLASH, impactPos.getX() + 0.5, y, impactPos.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impactPos.getX() + 0.5, impactPos.getY() + 1.0, impactPos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0);

        // Scatter rich loot around impact site
        dropLootItem(world, impactPos, new ItemStack(Items.DIAMOND, 12));
        dropLootItem(world, impactPos, new ItemStack(Items.QUARTZ, 24));
        dropLootItem(world, impactPos, new ItemStack(Items.GLOWSTONE, 16));
        dropLootItem(world, impactPos, new ItemStack(Items.PRISMARINE_CRYSTALS, 12));
        dropLootItem(world, impactPos, new ItemStack(Items.GOLDEN_APPLE, 3));
        dropLootItem(world, impactPos, new ItemStack(Items.OBSIDIAN, 8));

        return true;
    }

    public static boolean triggerGoblinEvent(ServerPlayerEntity caller) {
        long now = System.currentTimeMillis();
        if (now - lastGoblinTime < 480_000L) { // 8 minutes
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
        activeGoblins.put(goblin.getUuid(), 700); // 35 seconds (700 ticks)

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

        // Jackpot drops
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.DIAMOND, 16));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.NETHERITE_SCRAP, 2));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.GOLDEN_APPLE, 4));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.REDSTONE_BLOCK, 8));
        dropLootItem(world, goblin.getBlockPos(), new ItemStack(Items.EMERALD, 12));
    }

    public static void tick(MinecraftServer server) {
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
                // Escaped!
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
