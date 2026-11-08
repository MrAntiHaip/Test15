package com.dimaulyana.eightblades;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MagicSwordItem extends SwordItem {
    private final int tier;
    private final String abilityName;
    private final String abilityDesc;
    private final int baseCooldownTicks;

    public MagicSwordItem(int tier, int attackDamageBonus, float attackSpeed, String abilityName, String abilityDesc, int baseCooldownTicks, Settings settings) {
        super(ToolMaterials.NETHERITE, attackDamageBonus, attackSpeed, settings);
        this.tier = tier;
        this.abilityName = abilityName;
        this.abilityDesc = abilityDesc;
        this.baseCooldownTicks = baseCooldownTicks;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return tier >= 3;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient() && attacker.getWorld() instanceof ServerWorld serverWorld) {
            switch (this.tier) {
                case 1 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
                    serverWorld.spawnParticles(ParticleTypes.SPLASH, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                }
                case 2 -> {
                    serverWorld.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    attacker.heal(0.5f);
                }
                case 3 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1));
                    serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.25, 0.25, 0.25, 0.05);
                }
                case 4 -> serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                case 5 -> {
                    target.setFrozenTicks(Math.min(target.getFrozenTicks() + 100, 300));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 1));
                    serverWorld.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.04);
                }
                case 6 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0));
                    attacker.heal(1.0f);
                    serverWorld.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
                case 7 -> {
                    Vec3d launch = target.getPos().subtract(attacker.getPos()).normalize().multiply(1.2);
                    target.setVelocity(launch.x, 0.5, launch.z);
                    target.velocityModified = true;
                    serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                }
                case 8 -> {
                    target.setOnFireFor(5);
                    serverWorld.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                }
                case 9 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 1));
                    serverWorld.spawnParticles(ParticleTypes.CLOUD, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                }
                case 10 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
                    serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.04);
                }
                case 11 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
                    serverWorld.spawnParticles(ParticleTypes.GLOW, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.08);
                }
                case 12 -> {
                    target.setOnFireFor(8);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1));
                    serverWorld.spawnParticles(ParticleTypes.LAVA, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                }
                case 13 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 90, 3));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 90, 2));
                    serverWorld.spawnParticles(ParticleTypes.ENCHANT, target.getX(), target.getBodyY(0.5), target.getZ(), 14, 0.3, 0.3, 0.3, 0.15);
                }
                case 14 -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 2));
                    serverWorld.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getBodyY(0.5), target.getZ(), 14, 0.3, 0.3, 0.3, 0.2);
                }
                case 15 -> {
                    target.setOnFireFor(9);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 2));
                    attacker.heal(2.0f);
                    serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getBodyY(0.5), target.getZ(), 16, 0.4, 0.4, 0.4, 0.08);
                    serverWorld.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        StoryManager manager = StoryManager.get(serverWorld.getServer());
        String role = manager.getPlayerRole(player.getUuid());
        boolean isWarrior = "WARRIOR".equals(role);
        boolean isMage = "MAGE".equals(role);

        float damageMultiplier = isWarrior ? 1.35f : 1.0f;
        int cooldown = isMage ? Math.max(20, (int) (this.baseCooldownTicks * 0.65f)) : this.baseCooldownTicks;

        if (isWarrior) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 140, 1, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 90, 0, false, false, true));
        } else if (isMage) {
            for (ServerPlayerEntity ally : serverWorld.getPlayers()) {
                if (ally.squaredDistanceTo(player) <= 400.0) {
                    ally.heal(6.0f);
                    serverWorld.spawnParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2.0, ally.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
                }
            }
        }

        executeSwordAbility(serverWorld, player, damageMultiplier, isMage);
        player.getItemCooldownManager().set(this, cooldown);
        return TypedActionResult.success(stack);
    }

    private void executeSwordAbility(ServerWorld world, ServerPlayerEntity player, float dmgMult, boolean applyMageDebuff) {
        Vec3d look = player.getRotationVec(1.0f);
        Box area = player.getBoundingBox().expand(8.0);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && !(e instanceof PlayerEntity) && e.isAlive() && !StoryManager.isBlacksmith(e));

        switch (this.tier) {
            case 1 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 1.3f, 0.6f);
                world.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 60, 3.0, 1.0, 3.0, 0.2);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 30, 2.5, 0.8, 2.5, 0.1);
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 9.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 2));
                    Vec3d push = t.getPos().subtract(player.getPos()).normalize().multiply(1.1);
                    t.addVelocity(push.x, 0.35, push.z);
                    t.velocityModified = true;
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 2 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.3f, 1.4f);
                world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 70, 3.5, 0.8, 3.5, 0.25);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 1));
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 12.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 3 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.3f, 0.8f);
                player.setVelocity(look.x * 1.5, 0.35, look.z * 1.5);
                player.velocityModified = true;
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 50, 2.5, 0.8, 2.5, 0.1);
                world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 30, 2.0, 0.4, 2.0, 0.05);
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 15.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 2));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 3));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 4 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.1f, 1.2f);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2, player.getZ(), 75, 4.0, 1.2, 4.0, 0.25);
                int count = 0;
                for (LivingEntity t : targets) {
                    if (count++ >= 6) break;
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
                        bolt.setCosmetic(true);
                        world.spawnEntity(bolt);
                    }
                    t.damage(t.getDamageSources().playerAttack(player), 19.0f * dmgMult);
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 5 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.4f, 0.7f);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 100, 4.5, 1.2, 4.5, 0.08);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 180, 1));
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 22.0f * dmgMult);
                    t.setFrozenTicks(340);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 140, 4));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 6 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 1.2f, 1.4f);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.2, player.getZ(), 60, 3.5, 1.2, 3.5, 0.1);
                world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 35, 3.0, 1.0, 3.0, 0.08);
                float totalDrained = 0.0f;
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 24.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 140, 2));
                    totalDrained += 2.5f;
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
                float healAmount = Math.min(16.0f, totalDrained);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.heal(healAmount);
                    }
                }
            }
            case 7 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 0.5f);
                Vec3d vortexCenter = player.getPos().add(look.multiply(5.0)).add(0, 2.5, 0);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, vortexCenter.x, vortexCenter.y, vortexCenter.z, 110, 2.5, 2.0, 2.5, 0.25);
                world.spawnParticles(ParticleTypes.END_ROD, vortexCenter.x, vortexCenter.y, vortexCenter.z, 45, 2.0, 1.5, 2.0, 0.12);
                for (LivingEntity t : targets) {
                    Vec3d pull = vortexCenter.subtract(t.getPos()).multiply(0.7);
                    t.setVelocity(pull.x, pull.y + 0.5, pull.z);
                    t.velocityModified = true;
                    t.damage(t.getDamageSources().playerAttack(player), 27.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 2));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 8 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.4f, 0.8f);
                world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 110, 4.5, 1.2, 4.5, 0.15);
                world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1.0, player.getZ(), 35, 3.0, 1.0, 3.0, 0.1);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 500, 0));
                        ally.heal(8.0f);
                    }
                }
                for (LivingEntity t : targets) {
                    t.setOnFireFor(8);
                    t.damage(t.getDamageSources().playerAttack(player), 29.0f * dmgMult);
                    t.addVelocity(0, 0.65, 0);
                    t.velocityModified = true;
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 9 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.5f, 1.2f);
                player.setVelocity(look.x * 2.2, 0.45, look.z * 2.2);
                player.velocityModified = true;
                world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 70, 3.0, 1.0, 3.0, 0.2);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 16, 2.0, 0.5, 2.0, 0.1);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 240, 1));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 240, 0));
                    }
                }
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 32.0f * dmgMult);
                    t.addVelocity(0, 0.85, 0);
                    t.velocityModified = true;
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 10 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f, 1.0f);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX() + look.x * 2.5, player.getY() + 1.2, player.getZ() + look.z * 2.5, 3, 0.8, 0.4, 0.8, 0.0);
                world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 55, 4.0, 1.0, 4.0, 0.1);
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 36.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0));
                    Vec3d push = t.getPos().subtract(player.getPos()).normalize().multiply(1.5);
                    t.addVelocity(push.x, 0.4, push.z);
                    t.velocityModified = true;
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 11 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.4f, 1.1f);
                world.spawnParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 2.5, player.getZ(), 90, 4.5, 2.0, 4.5, 0.2);
                world.spawnParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 1.5, player.getZ(), 70, 4.0, 1.5, 4.0, 0.1);
                world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.5, player.getZ(), 5, 1.0, 1.0, 1.0, 0.0);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 240, 1));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 260, 1));
                    }
                }
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 40.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 260, 0));
                }
            }
            case 12 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1.4f, 0.6f);
                world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.5, player.getZ(), 70, 4.5, 0.8, 4.5, 0.15);
                world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 120, 4.5, 1.2, 4.5, 0.15);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 260, 1));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 700, 0));
                    }
                }
                for (LivingEntity t : targets) {
                    t.setOnFireFor(12);
                    t.damage(t.getDamageSources().playerAttack(player), 44.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 140, 1));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 13 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 1.5f, 1.6f);
                world.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.5, player.getZ(), 130, 5.0, 2.0, 5.0, 0.3);
                world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.5, player.getZ(), 90, 5.0, 2.0, 5.0, 0.3);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 260, 2));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 260, 2));
                        ally.heal(9.0f);
                    }
                }
                for (LivingEntity t : targets) {
                    t.damage(t.getDamageSources().playerAttack(player), 48.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 5));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 160, 3));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 160, 2));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 14 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.5f, 0.5f);
                Vec3d blackHole = player.getPos().add(look.multiply(5.5));
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, blackHole.x, blackHole.y + 1.0, blackHole.z, 140, 3.5, 2.0, 3.5, 0.35);
                world.spawnParticles(ParticleTypes.END_ROD, blackHole.x, blackHole.y + 1.0, blackHole.z, 70, 2.5, 2.0, 2.5, 0.15);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, blackHole.x, blackHole.y + 1.0, blackHole.z, 2, 0.2, 0.2, 0.2, 0);
                for (LivingEntity t : targets) {
                    Vec3d pull = blackHole.subtract(t.getPos()).multiply(0.85);
                    t.setVelocity(pull.x, 0.75, pull.z);
                    t.velocityModified = true;
                    t.damage(t.getDamageSources().playerAttack(player), 54.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 35, 2));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
            }
            case 15 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f, 0.8f);
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.5f, 1.0f);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX() + look.x * 3, player.getY() + 1.5, player.getZ() + look.z * 3, 5, 2.0, 0.6, 2.0, 0.0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.2, player.getZ(), 160, 6.0, 2.0, 6.0, 0.2);
                world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.5, player.getZ(), 90, 5.0, 2.0, 5.0, 0.2);
                world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.5, player.getZ(), 6, 2.0, 1.0, 2.0, 0.0);
                for (ServerPlayerEntity ally : world.getPlayers()) {
                    if (ally.squaredDistanceTo(player) <= 400.0) {
                        ally.heal(ally.getMaxHealth());
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 260, 2));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 260, 1));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 260, 2));
                        ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 320, 3));
                    }
                }
                Box cataclysmBox = player.getBoundingBox().expand(10.5);
                List<LivingEntity> hitList = world.getEntitiesByClass(LivingEntity.class, cataclysmBox,
                        e -> e != player && !(e instanceof PlayerEntity) && e.isAlive() && !StoryManager.isBlacksmith(e));
                for (LivingEntity t : hitList) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
                        bolt.setCosmetic(true);
                        world.spawnEntity(bolt);
                    }
                    t.damage(t.getDamageSources().playerAttack(player), 65.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 180, 2));
                    if (applyMageDebuff) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 260, 0));
                }
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Уровень клинка: §e" + this.tier + " / 15"));
        tooltip.add(Text.literal("§6Способность [ПКМ]: §f" + this.abilityName));
        tooltip.add(Text.literal("§7" + this.abilityDesc));
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("§c⚔ Роль [Хранитель Ярости]: §7+35% урона навыка и Щит Поглощения"));
        tooltip.add(Text.literal("§b✦ Роль [Ткач Стихий]: §7-35% перезарядки и исцеление дуэта"));
        tooltip.add(Text.literal("§8Нерушимый сюжетный артефакт Димы и Ульяны"));
    }
}
