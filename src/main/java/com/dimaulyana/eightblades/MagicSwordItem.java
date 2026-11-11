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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    // ==================== ЗАЩИТА ИГРОКОВ ====================
    private static boolean isValidTarget(LivingEntity e, LivingEntity self) {
        return e != null && e.isAlive() && e != self
                && !(e instanceof PlayerEntity)          // НИКОГДА не бьём игроков
                && !StoryManager.isBlacksmith(e);
    }

    private List<LivingEntity> targets(ServerWorld world, Vec3d center, double radius, LivingEntity self) {
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        return world.getEntitiesByClass(LivingEntity.class, box, e -> isValidTarget(e, self));
    }

    private void hurt(ServerWorld world, ServerPlayerEntity player, LivingEntity t, float dmg) {
        if (!isValidTarget(t, player)) return;
        t.damage(world.getDamageSources().playerAttack(player), dmg);
    }

    private void duo(ServerWorld world, ServerPlayerEntity player, double radius, java.util.function.Consumer<ServerPlayerEntity> action) {
        for (ServerPlayerEntity ally : world.getPlayers()) {
            if (ally.squaredDistanceTo(player) <= radius * radius) {
                action.accept(ally);
            }
        }
    }

    private void strikeLightning(ServerWorld world, LivingEntity t) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
            bolt.setCosmetic(true);
            world.spawnEntity(bolt);
        }
    }

    private void ring(ServerWorld world, Vec3d c, double radius, int points, net.minecraft.particle.ParticleEffect particle, double speed) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            world.spawnParticles(particle, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, speed);
        }
    }

    // ==================== ПАССИВКИ ПРИ УДАРЕ ====================
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient() && attacker.getWorld() instanceof ServerWorld serverWorld) {
            if (target instanceof PlayerEntity || StoryManager.isBlacksmith(target)) {
                return super.postHit(stack, target, attacker);
            }
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

    // ==================== АКТИВКА [ПКМ] ====================
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
            duo(serverWorld, player, 20.0, ally -> {
                ally.heal(6.0f);
                serverWorld.spawnParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2.0, ally.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
            });
        }

        executeSwordAbility(serverWorld, player, damageMultiplier, isMage);
        player.getItemCooldownManager().set(this, cooldown);
        return TypedActionResult.success(stack);
    }

    private void executeSwordAbility(ServerWorld world, ServerPlayerEntity player, float dmgMult, boolean mage) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d pc = player.getPos();
        List<LivingEntity> targets = targets(world, pc, 8.0, player);

        switch (this.tier) {

            // ===== I. ГРЯЗЕВОЕ ЦУНАМИ: волна + вторая волна через 1.5 сек =====
            case 1 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 1.3f, 0.6f);
                world.spawnParticles(ParticleTypes.SPLASH, pc.x, pc.y + 1.0, pc.z, 70, 3.0, 1.0, 3.0, 0.2);
                ring(world, pc.add(0, 0.3, 0), 4.0, 40, ParticleTypes.FALLING_WATER, 0.0);
                mudWave(world, player, pc, 9.0f * dmgMult, mage, 1.1);
                StoryManager.schedule(30, () -> {
                    if (player.isRemoved()) return;
                    Vec3d p2 = player.getPos();
                    world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_MUD_BREAK, SoundCategory.PLAYERS, 1.4f, 0.5f);
                    ring(world, p2.add(0, 0.3, 0), 6.5, 60, ParticleTypes.SPLASH, 0.1);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p2.x, p2.y + 0.6, p2.z, 60, 4.0, 0.8, 4.0, 0.1);
                    mudWave(world, player, p2, 7.0f * dmgMult, mage, 1.5);
                });
            }

            // ===== II. КОЛЬЦО ТЫСЯЧИ ИГЛ =====
            case 2 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4f, 1.4f);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 140, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.THORNS, 300, 2));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
                for (int w = 0; w < 3; w++) {
                    final int wave = w;
                    StoryManager.schedule(wave * 8, () -> {
                        if (player.isRemoved()) return;
                        Vec3d c = player.getPos().add(0, 0.8, 0);
                        ring(world, c, 2.0 + wave * 2.2, 48, ParticleTypes.CRIT, 0.02);
                        world.spawnParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 20, 2.0 + wave, 0.4, 2.0 + wave, 0.05);
                        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 1.0f, 1.6f - wave * 0.2f);
                        for (LivingEntity t : targets(world, player.getPos(), 3.0 + wave * 2.2, player)) {
                            hurt(world, player, t, 6.0f * dmgMult);
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 140, 1));
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                        }
                    });
                }
            }

            // ===== III. ТОКСИЧНЫЙ ШТОРМ: рывок + ядовитое облако 4 сек =====
            case 3 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.3f, 0.8f);
                player.setVelocity(look.x * 1.6, 0.35, look.z * 1.6);
                player.velocityModified = true;
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, 1));
                for (LivingEntity t : targets) {
                    hurt(world, player, t, 12.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 2));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 3));
                }
                Vec3d cloud = pc.add(look.multiply(4.0)).add(0, 0.6, 0);
                for (int i = 0; i < 16; i++) {
                    StoryManager.schedule(i * 5, () -> {
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cloud.x, cloud.y + 0.5, cloud.z, 45, 3.2, 1.0, 3.2, 0.02);
                        world.spawnParticles(ParticleTypes.SNEEZE, cloud.x, cloud.y + 0.8, cloud.z, 10, 2.5, 0.6, 2.5, 0.02);
                        if (i % 4 == 0) {
                            world.playSound(null, BlockPos.ofFloored(cloud), SoundEvents.ENTITY_SPIDER_HURT, SoundCategory.PLAYERS, 0.7f, 0.7f);
                        }
                        for (LivingEntity t : targets(world, cloud, 4.0, player)) {
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 2));
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0));
                            if (i % 4 == 0) hurt(world, player, t, 3.0f * dmgMult);
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                        }
                    });
                }
            }

            // ===== IV. ЦЕПНАЯ МОЛНИЯ (до 8 целей, по очереди) =====
            case 4 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.2f, 1.2f);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pc.x, pc.y + 1.2, pc.z, 90, 4.0, 1.2, 4.0, 0.25);
                List<LivingEntity> chain = targets(world, pc, 12.0, player);
                int n = Math.min(8, chain.size());
                for (int i = 0; i < n; i++) {
                    LivingEntity t = chain.get(i);
                    StoryManager.schedule(i * 4, () -> {
                        if (!t.isAlive()) return;
                        strikeLightning(world, t);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getBodyY(0.5), t.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
                        world.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.9f, 1.4f);
                        hurt(world, player, t, 19.0f * dmgMult);
                        if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                    });
                }
            }

            // ===== V. ЛЕДЯНОЙ БУРАН: 3 кольца заморозки =====
            case 5 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.4f, 0.7f);
                duo(world, player, 20.0, ally -> {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 220, 2));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 0));
                });
                for (int w = 0; w < 3; w++) {
                    final int wave = w;
                    StoryManager.schedule(wave * 10, () -> {
                        Vec3d c = player.getPos().add(0, 0.4, 0);
                        ring(world, c, 2.5 + wave * 2.5, 56, ParticleTypes.SNOWFLAKE, 0.02);
                        world.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1.0, c.z, 80, 3.0 + wave, 1.2, 3.0 + wave, 0.06);
                        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f + wave * 0.2f);
                        for (LivingEntity t : targets(world, player.getPos(), 3.5 + wave * 2.5, player)) {
                            hurt(world, player, t, 9.0f * dmgMult);
                            t.setFrozenTicks(340);
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 4));
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                        }
                    });
                }
            }

            // ===== VI. КРОВАВЫЙ СИФОН: тотемы-сердца качают HP 4 сек =====
            case 6 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 1.2f, 1.4f);
                List<LivingEntity> victims = targets(world, pc, 9.0, player);
                for (LivingEntity t : victims) {
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 160, 2));
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
                for (int i = 0; i < 16; i++) {
                    final int step = i;
                    StoryManager.schedule(i * 5, () -> {
                        float drained = 0.0f;
                        for (LivingEntity t : victims) {
                            if (!t.isAlive()) continue;
                            Vec3d from = t.getPos().add(0, 1.0, 0);
                            Vec3d to = player.getPos().add(0, 1.0, 0);
                            Vec3d dir = to.subtract(from).normalize().multiply(0.55);
                            world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, from.x, from.y, from.z, 3, 0.15, 0.2, 0.15, 0.0);
                            for (int k = 1; k <= 5; k++) {
                                Vec3d pt = from.add(dir.multiply(k * 1.6));
                                world.spawnParticles(ParticleTypes.CRIMSON_SPORE, pt.x, pt.y, pt.z, 2, 0.05, 0.05, 0.05, 0.0);
                            }
                            if (step % 4 == 0) hurt(world, player, t, 6.0f * dmgMult);
                            drained += 0.6f;
                        }
                        if (drained > 0) {
                            float heal = Math.min(3.0f, drained);
                            duo(world, player, 20.0, ally -> {
                                ally.heal(heal);
                                world.spawnParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2.0, ally.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                            });
                        }
                        if (step % 5 == 0) {
                            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.5f, 0.6f);
                        }
                    });
                }
            }

            // ===== VII. ГРАВИПУШКА: воронка 2.5 сек + кинетический удар =====
            case 7 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 0.5f);
                Vec3d vortex = pc.add(look.multiply(5.0)).add(0, 2.5, 0);
                List<LivingEntity> caught = targets(world, vortex, 11.0, player);
                for (int i = 0; i < 25; i++) {
                    final int step = i;
                    StoryManager.schedule(i * 2, () -> {
                        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, vortex.x, vortex.y, vortex.z, 25, 1.6, 1.4, 1.6, 0.2);
                        ring(world, vortex, 3.0 - (step % 10) * 0.25, 24, ParticleTypes.PORTAL, 0.05);
                        for (LivingEntity t : caught) {
                            if (!t.isAlive()) continue;
                            Vec3d pull = vortex.subtract(t.getPos().add(0, 1.0, 0)).multiply(0.28);
                            t.setVelocity(pull.x, pull.y * 0.5 + 0.12, pull.z);
                            t.velocityModified = true;
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 1));
                            if (step % 6 == 0) hurt(world, player, t, 5.0f * dmgMult);
                        }
                    });
                }
                StoryManager.schedule(52, () -> {
                    world.playSound(null, BlockPos.ofFloored(vortex), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.6f, 0.7f);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, vortex.x, vortex.y, vortex.z, 2, 0.3, 0.3, 0.3, 0);
                    world.spawnParticles(ParticleTypes.END_ROD, vortex.x, vortex.y, vortex.z, 90, 3.0, 2.0, 3.0, 0.3);
                    for (LivingEntity t : targets(world, vortex, 9.0, player)) {
                        hurt(world, player, t, 27.0f * dmgMult);
                        Vec3d slam = t.getPos().subtract(vortex).normalize().multiply(1.6);
                        t.setVelocity(slam.x, -0.9, slam.z);
                        t.velocityModified = true;
                        t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 3));
                        if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
                    }
                });
            }

            // ===== VIII. СВЕРХНОВАЯ + РЕЖИМ ФЕНИКСА 10 сек =====
            case 8 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.8f);
                world.spawnParticles(ParticleTypes.FLAME, pc.x, pc.y + 1.0, pc.z, 130, 4.5, 1.2, 4.5, 0.15);
                world.spawnParticles(ParticleTypes.LAVA, pc.x, pc.y + 1.0, pc.z, 40, 3.0, 1.0, 3.0, 0.1);
                ring(world, pc.add(0, 0.4, 0), 5.0, 60, ParticleTypes.FLAME, 0.02);
                duo(world, player, 20.0, ally -> {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 600, 0));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 300, 2));
                    ally.heal(8.0f);
                });
                for (LivingEntity t : targets) {
                    t.setOnFireFor(10);
                    hurt(world, player, t, 29.0f * dmgMult);
                    t.addVelocity(0, 0.7, 0);
                    t.velocityModified = true;
                }
                // Аура Феникса: огненное кольцо вокруг героя каждую секунду
                for (int i = 1; i <= 10; i++) {
                    StoryManager.schedule(i * 20, () -> {
                        if (player.isRemoved()) return;
                        Vec3d c = player.getPos().add(0, 0.5, 0);
                        ring(world, c, 3.2, 36, ParticleTypes.FLAME, 0.01);
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1.0, c.z, 12, 0.4, 0.6, 0.4, 0.02);
                        for (LivingEntity t : targets(world, player.getPos(), 3.6, player)) {
                            t.setOnFireFor(4);
                            hurt(world, player, t, 4.0f * dmgMult);
                        }
                        player.heal(1.0f);
                    });
                }
            }

            // ===== IX. ТОРНАДО: рывок + вихрь подбрасывает врагов =====
            case 9 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.5f, 1.2f);
                player.setVelocity(look.x * 2.3, 0.45, look.z * 2.3);
                player.velocityModified = true;
                duo(world, player, 20.0, ally -> {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 260, 1));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 300, 0));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 260, 1));
                });
                Vec3d tornado = pc.add(look.multiply(4.0));
                for (int i = 0; i < 20; i++) {
                    final int step = i;
                    StoryManager.schedule(i * 3, () -> {
                        double h = step * 0.35;
                        double r = 1.0 + step * 0.14;
                        ring(world, tornado.add(0, h, 0), r, 20, ParticleTypes.CLOUD, 0.02);
                        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, tornado.x, tornado.y + h, tornado.z, 2, r * 0.4, 0.2, r * 0.4, 0.0);
                        if (step % 5 == 0) {
                            world.playSound(null, BlockPos.ofFloored(tornado), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.0f, 0.8f);
                        }
                        for (LivingEntity t : targets(world, tornado, 4.5, player)) {
                            Vec3d swirl = t.getPos().subtract(tornado).normalize();
                            t.setVelocity(-swirl.z * 0.5, 0.42, swirl.x * 0.5);
                            t.velocityModified = true;
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 25, 2));
                            if (step % 5 == 0) hurt(world, player, t, 8.0f * dmgMult);
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                        }
                    });
                }
            }

            // ===== X. ТРОЙНОЙ РЕЗОНАНС ВАРДЕНА =====
            case 10 -> {
                for (int beam = 0; beam < 3; beam++) {
                    final int b = beam;
                    StoryManager.schedule(beam * 12, () -> {
                        if (player.isRemoved()) return;
                        Vec3d dir = player.getRotationVec(1.0f);
                        Vec3d origin = player.getPos().add(0, 1.3, 0);
                        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f, 1.0f + b * 0.15f);
                        for (int k = 1; k <= 14; k++) {
                            Vec3d pt = origin.add(dir.multiply(k * 1.2));
                            world.spawnParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                            world.spawnParticles(ParticleTypes.SCULK_SOUL, pt.x, pt.y, pt.z, 2, 0.15, 0.15, 0.15, 0.01);
                        }
                        Box beamBox = new Box(origin, origin.add(dir.multiply(18.0))).expand(2.2);
                        for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, beamBox, e -> isValidTarget(e, player))) {
                            hurt(world, player, t, 14.0f * dmgMult);
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0));
                            Vec3d push = t.getPos().subtract(player.getPos()).normalize().multiply(1.2);
                            t.addVelocity(push.x, 0.35, push.z);
                            t.velocityModified = true;
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
                        }
                    });
                }
                world.spawnParticles(ParticleTypes.SCULK_SOUL, pc.x, pc.y + 1.0, pc.z, 60, 3.0, 1.0, 3.0, 0.1);
            }

            // ===== XI. МЕТЕОРИТНЫЙ ЗАЛП: 8 звезд с неба =====
            case 11 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.4f, 1.1f);
                duo(world, player, 20.0, ally -> {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 260, 1));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 300, 2));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0));
                });
                for (int m = 0; m < 8; m++) {
                    final int idx = m;
                    StoryManager.schedule(m * 6, () -> {
                        double ang = world.random.nextDouble() * Math.PI * 2;
                        double dist = 2.0 + world.random.nextDouble() * 7.0;
                        double mx = player.getX() + Math.cos(ang) * dist;
                        double mz = player.getZ() + Math.sin(ang) * dist;
                        double groundY = player.getY();
                        for (int y = 14; y >= 0; y -= 2) {
                            world.spawnParticles(ParticleTypes.END_ROD, mx, groundY + y, mz, 2, 0.1, 0.2, 0.1, 0.0);
                            world.spawnParticles(ParticleTypes.GLOW, mx, groundY + y, mz, 3, 0.15, 0.2, 0.15, 0.0);
                        }
                        world.spawnParticles(ParticleTypes.FLASH, mx, groundY + 0.8, mz, 1, 0, 0, 0, 0);
                        world.spawnParticles(ParticleTypes.FIREWORK, mx, groundY + 0.8, mz, 35, 1.2, 0.8, 1.2, 0.2);
                        world.playSound(null, BlockPos.ofFloored(mx, groundY, mz), SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.2f, 0.9f);
                        for (LivingEntity t : targets(world, new Vec3d(mx, groundY, mz), 3.2, player)) {
                            hurt(world, player, t, 12.0f * dmgMult);
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 260, 0));
                            t.addVelocity(0, 0.4, 0);
                            t.velocityModified = true;
                        }
                    });
                }
            }

            // ===== XII. МАГМАТИЧЕСКИЙ РАЗЛОМ + ОГНЕННОЕ КОЛЬЦО =====
            case 12 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1.5f, 0.6f);
                duo(world, player, 20.0, ally -> {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 280, 1));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 700, 0));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
                });
                // Разлом по направлению взгляда
                for (int k = 1; k <= 10; k++) {
                    Vec3d crack = pc.add(look.multiply(k * 1.3));
                    world.spawnParticles(ParticleTypes.LAVA, crack.x, crack.y + 0.3, crack.z, 6, 0.3, 0.2, 0.3, 0.0);
                    world.spawnParticles(ParticleTypes.FLAME, crack.x, crack.y + 0.4, crack.z, 14, 0.4, 0.4, 0.4, 0.02);
                }
                for (int w = 0; w < 4; w++) {
                    final int wave = w;
                    StoryManager.schedule(wave * 8, () -> {
                        Vec3d c = player.getPos().add(0, 0.4, 0);
                        ring(world, c, 2.0 + wave * 2.0, 56, ParticleTypes.FLAME, 0.02);
                        ring(world, c, 2.0 + wave * 2.0, 28, ParticleTypes.LAVA, 0.0);
                        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_LAVA_POP, SoundCategory.PLAYERS, 1.2f, 0.6f + wave * 0.15f);
                        for (LivingEntity t : targets(world, player.getPos(), 3.0 + wave * 2.0, player)) {
                            t.setOnFireFor(12);
                            hurt(world, player, t, 12.0f * dmgMult);
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 140, 1));
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
                        }
                    });
                }
            }

            // ===== XIII. ХРОНОСТАЗИС: реальная заморозка мобов на 7 сек =====
            case 13 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 1.5f, 1.6f);
                world.spawnParticles(ParticleTypes.ENCHANT, pc.x, pc.y + 1.5, pc.z, 150, 6.0, 2.0, 6.0, 0.3);
                ring(world, pc.add(0, 1.0, 0), 9.0, 80, ParticleTypes.END_ROD, 0.0);
                duo(world, player, 20.0, ally -> {
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 2));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 300, 2));
                    ally.heal(9.0f);
                });
                List<LivingEntity> frozen = targets(world, pc, 14.0, player);
                for (LivingEntity t : frozen) {
                    hurt(world, player, t, 20.0f * dmgMult);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                    StoryManager.freezeEntity(t, 140); // 7 секунд ПОЛНОЙ остановки
                }
                StoryManager.broadcastMessage(world.getServer(), "§6§l⏳ ХРОНОСТАЗИС! §eВремя остановлено на 7 секунд — враги замерли!");
                for (int i = 0; i < 14; i++) {
                    StoryManager.schedule(i * 10, () -> {
                        for (LivingEntity t : frozen) {
                            if (!t.isAlive()) continue;
                            world.spawnParticles(ParticleTypes.ENCHANT, t.getX(), t.getBodyY(0.6), t.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
                            world.spawnParticles(ParticleTypes.WAX_OFF, t.getX(), t.getBodyY(0.6), t.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
                        }
                    });
                }
                StoryManager.schedule(140, () -> {
                    world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 1.4f, 1.2f);
                    for (LivingEntity t : frozen) {
                        if (!t.isAlive()) continue;
                        hurt(world, player, t, 28.0f * dmgMult);
                        world.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.5), t.getZ(), 25, 0.4, 0.5, 0.4, 0.2);
                    }
                    StoryManager.broadcastMessage(world.getServer(), "§e⏳ Время разморожено — накопленный урон обрушился на врагов!");
                });
            }

            // ===== XIV. ЧЕРНАЯ ДЫРА: 3 сек засасывает, потом взрыв =====
            case 14 -> {
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.5f, 0.5f);
                Vec3d hole = pc.add(look.multiply(5.5)).add(0, 1.5, 0);
                List<LivingEntity> caught = targets(world, hole, 13.0, player);
                for (int i = 0; i < 30; i++) {
                    final int step = i;
                    StoryManager.schedule(i * 2, () -> {
                        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, hole.x, hole.y, hole.z, 40, 2.5, 2.0, 2.5, 0.3);
                        world.spawnParticles(ParticleTypes.PORTAL, hole.x, hole.y, hole.z, 25, 1.2, 1.2, 1.2, 0.4);
                        ring(world, hole, 4.0 - (step % 15) * 0.22, 26, ParticleTypes.ASH, 0.0);
                        if (step % 8 == 0) {
                            world.playSound(null, BlockPos.ofFloored(hole), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.5f);
                        }
                        for (LivingEntity t : caught) {
                            if (!t.isAlive()) continue;
                            Vec3d pull = hole.subtract(t.getPos().add(0, 0.9, 0)).multiply(0.3);
                            t.setVelocity(pull.x, pull.y * 0.6, pull.z);
                            t.velocityModified = true;
                            if (step % 6 == 0) hurt(world, player, t, 6.0f * dmgMult);
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
                        }
                    });
                }
                StoryManager.schedule(62, () -> {
                    world.playSound(null, BlockPos.ofFloored(hole), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, 0.5f);
                    world.playSound(null, BlockPos.ofFloored(hole), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.2f, 1.4f);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, hole.x, hole.y, hole.z, 4, 1.0, 1.0, 1.0, 0);
                    world.spawnParticles(ParticleTypes.END_ROD, hole.x, hole.y, hole.z, 140, 4.0, 3.0, 4.0, 0.4);
                    world.spawnParticles(ParticleTypes.FLASH, hole.x, hole.y, hole.z, 3, 0.5, 0.5, 0.5, 0);
                    for (LivingEntity t : targets(world, hole, 10.0, player)) {
                        hurt(world, player, t, 54.0f * dmgMult);
                        Vec3d blast = t.getPos().subtract(hole).normalize().multiply(2.0);
                        t.setVelocity(blast.x, 0.8, blast.z);
                        t.velocityModified = true;
                        t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 2));
                    }
                });
            }

            // ===== XV. КАТАКЛИЗМ ЗАТМЕНИЯ: 3 волны апокалипсиса =====
            case 15 -> {
                duo(world, player, 25.0, ally -> {
                    ally.heal(ally.getMaxHealth());
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 320, 2));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 320, 1));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 320, 2));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, 3));
                    ally.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 400, 0));
                    world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, ally.getX(), ally.getY() + 1.2, ally.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
                });
                StoryManager.broadcastMessage(world.getServer(), "§5§l🌑 КАТАКЛИЗМ ЗАТМЕНИЯ! §dТри волны первородной мощи обрушиваются на мир!");
                for (int w = 0; w < 3; w++) {
                    final int wave = w;
                    StoryManager.schedule(wave * 25, () -> {
                        if (player.isRemoved()) return;
                        Vec3d c = player.getPos();
                        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.6f, 0.7f + wave * 0.2f);
                        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.6f, 1.0f);
                        world.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.5, c.z, 4, 2.0, 0.6, 2.0, 0.0);
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1.2, c.z, 170, 6.0, 2.0, 6.0, 0.2);
                        world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 1.5, c.z, 4, 2.0, 1.0, 2.0, 0.0);
                        ring(world, c.add(0, 0.5, 0), 4.0 + wave * 3.0, 80, ParticleTypes.END_ROD, 0.02);
                        ring(world, c.add(0, 0.5, 0), 4.0 + wave * 3.0, 60, ParticleTypes.SOUL_FIRE_FLAME, 0.02);
                        for (LivingEntity t : targets(world, c, 7.0 + wave * 3.0, player)) {
                            strikeLightning(world, t);
                            hurt(world, player, t, 24.0f * dmgMult);
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 180, 2));
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0));
                            Vec3d push = t.getPos().subtract(c).normalize().multiply(1.3);
                            t.setVelocity(push.x, 0.55, push.z);
                            t.velocityModified = true;
                            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 300, 0));
                        }
                    });
                }
            }
        }
    }

    private void mudWave(ServerWorld world, ServerPlayerEntity player, Vec3d center, float dmg, boolean mage, double power) {
        for (LivingEntity t : targets(world, center, 7.5, player)) {
            hurt(world, player, t, dmg);
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 140, 2));
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0));
            Vec3d push = t.getPos().subtract(center).normalize().multiply(power);
            t.setVelocity(push.x, 0.4, push.z);
            t.velocityModified = true;
            world.spawnParticles(ParticleTypes.FALLING_WATER, t.getX(), t.getBodyY(0.5), t.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
            if (mage) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
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
        tooltip.add(Text.literal("§a🛡 Способности НИКОГДА не вредят игрокам"));
        tooltip.add(Text.literal("§8Нерушимый сюжетный артефакт Димы и Ульяны"));
    }
}
