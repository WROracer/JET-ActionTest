package de.wroracer.justenoughtnt.util;

import java.util.ArrayList;

import de.wroracer.justenoughtnt.JustEnoughTNT;
import de.wroracer.justenoughtnt.block.BaseTNTBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Explosion {

    private Level world;
    private BlockPos pos;
    private Entity source;
    private float radius;
    private double dropChance;
    private double randomness;

    private int perTick;
    private int currentTick;
    private ArrayList<ArrayList<BlockPos>> chunkBlocks;

    public Explosion(Level world, BlockPos pos, Entity source, float radius, double dropChance, double randomness) {
        this.world = world;
        this.pos = pos;
        this.source = source;
        this.radius = radius;
        this.dropChance = dropChance;
        this.randomness = randomness;
        this.currentTick = 0;
        this.perTick = 0;
        chunkBlocks = new ArrayList<ArrayList<BlockPos>>();

    }

    public Explosion(Level world, BlockPos pos, Entity source, float radius, double dropChance, double randomness,
            int perTick) {
        this.world = world;
        this.pos = pos;
        this.source = source;
        this.radius = radius;
        this.dropChance = dropChance;
        this.randomness = randomness;
        this.perTick = perTick;
        this.currentTick = 0;
        chunkBlocks = new ArrayList<ArrayList<BlockPos>>();
    }

    public void explode() {
        // get all blocks within the radius from pos

        if (perTick > 0 && currentTick == 0) {
            ArrayList<BlockPos> blocks = getBlocks();
            // split blocks into perTick chunks

            // todo: change to split on blocks per chunk and not on ticks to explode

            for (int i = 0; i < blocks.size(); i += perTick) {
                ArrayList<BlockPos> chunk = new ArrayList<BlockPos>();
                for (int j = 0; j < perTick; j++) {
                    if (i + j < blocks.size()) {
                        chunk.add(blocks.get(i + j));
                    }
                }
                chunkBlocks.add(chunk);
            }

            JustEnoughTNT.LOGGER.info("spread in " + chunkBlocks.size() + " chunks of " + perTick + " blocks");

            modifyEntities();
        }
        if (perTick == 0 && currentTick == 0) {
            ArrayList<BlockPos> blocks = getBlocks();
            modifyEntities();
            modifyWorld(blocks);
        }
    }

    private ArrayList<BlockPos> getBlocks() {
        JustEnoughTNT.LOGGER.info("Getting blocks in radius: " + radius);

        ArrayList<BlockPos> blocks = new ArrayList<BlockPos>();
        for (int x = -(int) radius; x <= radius; x++) {
            for (int y = -(int) radius; y <= radius; y++) {
                for (int z = -(int) radius; z <= radius; z++) {
                    BlockPos blockPos = new BlockPos(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= radius + randomness * (Math.random() - 0.5)) {
                        if (shouldDestroy(blockPos))
                            blocks.add(blockPos);
                    }
                }
            }
        }
        JustEnoughTNT.LOGGER.info("Blocks: " + blocks.size());
        return blocks;
    }

    public boolean tick() {
        if (perTick <= 0) {
            throw new IllegalStateException("Tried to tick explosion when the explosion is set to explode at once");
        }

        if (currentTick == chunkBlocks.size()) {
            return true;
        }

        currentTick++;
        ArrayList<BlockPos> blocks = chunkBlocks.get(currentTick - 1);

        modifyWorld(blocks);
        // chunkBlocks.remove(blocks);

        return currentTick >= chunkBlocks.size();
    }

    private void modifyEntities() {
        ArrayList<Entity> entities = damageEntities();
        for (Entity entity : entities) {
            Vec3 newVelocity = getEntityVelocity(entity);
            Vec3 oldVelocity = entity.getDeltaMovement();
            entity.setDeltaMovement(oldVelocity.add(newVelocity));
        }
    }

    private ArrayList<Entity> damageEntities() {
        // damage entities
        ArrayList<Entity> finalEntities = new ArrayList<Entity>();
        ArrayList<Entity> entities = new ArrayList<Entity>();
        for (Entity entity : world.getEntities(source, new AABB(pos).inflate(radius, radius, radius))) {
            if (entity instanceof LivingEntity) {
                entities.add(entity);
            }
        }
        for (Entity entity : entities) {

            double distance = getEntityDistance(entity);
            if (distance <= radius) {
                double damage = getEntityDamage(distance);
                finalEntities.add(entity);
                // JustEnoughTNT.LOGGER.debug(
                //         "Damageing: " + entity.getEncodeId() + " with damage: " + damage
                //                 + "; distance: " + distance);
                LivingEntity entity2 = (LivingEntity) entity;
                entity2.hurt(DamageSource.explosion((LivingEntity) source), (float) damage);
            }
        }
        return finalEntities;
    }

    private void modifyWorld(ArrayList<BlockPos> blocks) {
        // remove blocks
        for (BlockPos blockPos : blocks) {
            destroyBlock(blockPos);
        }

    }

    public double getEntityDistance(Entity entity) {
        int x = entity.getBlockX();
        int y = entity.getBlockY();
        int z = entity.getBlockZ();
        double distance = Math
                .sqrt(Math.pow(x - pos.getX(), 2) + Math.pow(y - pos.getY(), 2) + Math.pow(z - pos.getZ(), 2));
        return distance;
    }

    public Vec3 getEntityVelocity(Entity entity) {
        // move away from the direction of the explosion
        double distanceFromExplosion = getEntityDistance(entity);
        double x = entity.getBlockX() - pos.getX();
        double y = entity.getBlockY() - pos.getY();
        double z = entity.getBlockZ() - pos.getZ();
        double distance = Math.sqrt(x * x + y * y + z * z);
        x /= distance;
        y /= distance;
        z /= distance;
        x *= (distanceFromExplosion / 2);
        y *= (distanceFromExplosion / 2);
        z *= (distanceFromExplosion / 2);
        return new Vec3(x, y, z);
    }

    public double getEntityDamage(double distance) {
        // the larger the distance, the smaller the damage. minimum damage is 1 at radius
        return Math.max(1, (radius - distance));

    }

    public boolean shouldDestroy(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        boolean isDestructable = block == Blocks.BEDROCK || block == Blocks.AIR;
        return !isDestructable;
    }

    public void destroyBlock(BlockPos pos) {

        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof BaseTNTBlock) {
            // it is one of our tnt blocks
            BaseTNTBlock tntBlock = (BaseTNTBlock) block;
            tntBlock.wasExplodedByJET(world, pos, (LivingEntity) source);
        } else {
            boolean drop = Math.random() <= dropChance;
            world.destroyBlock(pos, drop);
        }

    }

    public Level getLevel() {
        return world;
    }

    public void setLevel(Level world) {
        this.world = world;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public Entity getSource() {
        return source;
    }

    public void setSource(Entity source) {
        this.source = source;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public double getDropChance() {
        return dropChance;
    }

    public void setDropChance(double dropChance) {
        this.dropChance = dropChance;
    }
}
