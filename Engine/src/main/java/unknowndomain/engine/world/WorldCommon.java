package unknowndomain.engine.world;

import com.google.common.collect.Sets;
import org.joml.*;
import unknowndomain.engine.block.Block;
import unknowndomain.engine.block.BlockPrototype;
import unknowndomain.engine.block.RayTraceBlockHit;
import unknowndomain.engine.entity.Entity;
import unknowndomain.engine.entity.PlayerEntity;
import unknowndomain.engine.event.world.block.BlockChangeEvent;
import unknowndomain.engine.event.world.block.cause.BlockChangeCause;
import unknowndomain.engine.game.Game;
import unknowndomain.engine.math.AABBs;
import unknowndomain.engine.math.BlockPos;
import unknowndomain.engine.math.FixStepTicker;
import unknowndomain.engine.player.Player;
import unknowndomain.engine.registry.Registries;
import unknowndomain.engine.util.Facing;
import unknowndomain.engine.world.chunk.Chunk;
import unknowndomain.engine.world.chunk.ChunkConstants;
import unknowndomain.engine.world.chunk.ChunkStorage;
import unknowndomain.engine.world.chunk.WorldCommonChunkManager;
import unknowndomain.engine.world.storage.WorldCommonLoader;
import unknowndomain.engine.world.util.FastVoxelRayTrace;
import unknowndomain.game.init.Blocks;

import javax.annotation.Nonnull;
import java.lang.Math;
import java.util.*;

public class WorldCommon implements World, Runnable {
    public static final float CALC_ERROR_FIXING = 1e-6f;
    private final Game game;

    private final PhysicsSystem physicsSystem = new PhysicsSystem(); // prepare for split

    private final ChunkStorage chunkStorage;
    private WorldCommonLoader loader;
    private WorldCommonChunkManager chunkManager;
    private final List<Long> criticalChunks;
    private final List<Player> players = new ArrayList<>();
    private final List<Entity> entityList = new ArrayList<>();
    private final List<Runnable> nextTick = new ArrayList<>();

    private final FixStepTicker ticker;
    private long gameTick;
//    private ExecutorService service;

    public WorldCommon(Game game) {
        this.game = game;
        this.chunkStorage = new ChunkStorage(this);
        this.ticker = new FixStepTicker(this::tick, FixStepTicker.LOGIC_TICK); // TODO: make tps configurable
        criticalChunks = new ArrayList<>();
    }

    public void spawnEntity(Entity entity) {
        BlockPos pos = BlockPos.of(entity.getPosition());
        Chunk chunk = chunkStorage.getOrLoadChunk(pos);
        chunk.getEntities().add(entity);
        entityList.add(entity);
    }

    @Deprecated
    public void playerJoin(Player player) {
        // FIXME:
        var entity = new PlayerEntity(entityList.size(), this);
        player.controlEntity(entity);
        spawnEntity(entity);
        players.add(player);
    }

    @Override
    public Game getGame() {
        return game;
    }

    @Override
    public List<Entity> getEntities() {
        return entityList;
    }

    /**
     * Get the list of chunkpos which the corresponding chunk should be force loaded
     * @return
     */
    public List<Long> getCriticalChunks() {
        return criticalChunks;
    }

    @Nonnull
    @Override
    public RayTraceBlockHit raycast(Vector3fc from, Vector3fc dir, float distance) {
        return raycast(from, dir, distance, Sets.newHashSet(Registries.getBlockRegistry().air()));
    }

    @Nonnull
    @Override
    public RayTraceBlockHit raycast(Vector3fc from, Vector3fc dir, float distance, Set<Block> ignore) {
        Vector3f rayOffset = dir.normalize(new Vector3f()).mul(distance);
        Vector3f dist = rayOffset.add(from, new Vector3f());

        var all = FastVoxelRayTrace.rayTrace(from, dist);

        all.sort(Comparator.comparingDouble(pos -> from.distanceSquared(pos.getX(), pos.getY(), pos.getZ())));

        for (BlockPos pos : all) {
            Block block = getBlock(pos);
            if (ignore.contains(block))
                continue;
            Vector3f local = from.sub(pos.getX(), pos.getY(), pos.getZ(), new Vector3f());
            AABBd[] boxes = block.getBoundingBoxes();
            Vector2d result = new Vector2d();
            for (AABBd box : boxes) {
                boolean hit = box.intersectRay(local.x, local.y, local.z, rayOffset.x, rayOffset.y, rayOffset.z,
                        result);
                if (hit) {
                    Vector3f hitPoint = local.add(rayOffset.mul((float) result.x, new Vector3f()));
                    Facing facing = null;
                    if (hitPoint.x <= 0f + CALC_ERROR_FIXING) {
                        facing = Facing.WEST;
                    } else if (hitPoint.x >= 1f - CALC_ERROR_FIXING) {
                        facing = Facing.EAST;
                    } else if (hitPoint.y <= 0f + CALC_ERROR_FIXING) {
                        facing = Facing.DOWN;
                    } else if (hitPoint.y >= 1f - CALC_ERROR_FIXING) {
                        facing = Facing.UP;
                    } else if (hitPoint.z <= 0f + CALC_ERROR_FIXING) {
                        facing = Facing.SOUTH;
                    } else if (hitPoint.z >= 1f - CALC_ERROR_FIXING) {
                        facing = Facing.NORTH;
                    }
                    if (facing != null) {
                        return new RayTraceBlockHit(this, pos, block, hitPoint, facing);
                    }
                }
            }
        }
        return RayTraceBlockHit.failure();
    }

    protected void tick() {
        if (nextTick.size() != 0) {
            for (Runnable tick : nextTick) { // TODO: limit time
                tick.run();
            }
        }
        physicsSystem.tick(this);
        tickEntityMotion();
        tickChunks();
        tickEntities();
        gameTick++;
    }

    @Override
    public long getGameTick() {
        return gameTick;
    }

    protected void tickChunks() {
        chunkStorage.getChunks().forEach(this::tickChunk);
    }

    protected void tickEntities() {
        for (Entity entity : entityList)
            entity.tick(); // state machine update
    }

    protected void tickEntityMotion() {
        for (Entity entity : this.getEntities()) {
            Vector3d position = entity.getPosition();
            Vector3f motion = entity.getMotion();
            BlockPos oldPosition = BlockPos.of(position);
            position.add(motion);
            BlockPos newPosition = BlockPos.of(position);

            if (!BlockPos.inSameChunk(oldPosition, newPosition)) {
                Chunk oldChunk = chunkStorage.getChunk(oldPosition), newChunk = chunkStorage.getOrLoadChunk(newPosition);
                oldChunk.getEntities().remove(entity);
                newChunk.getEntities().add(entity);
                // entity leaving and enter chunk event
            }
        }
    }

    private void tickChunk(Chunk chunk) {
//        Collection<Block> blocks = chunk.getRuntimeBlock();
//        if (blocks.size() != 0) {
//            for (Block object : blocks) {
//                BlockPrototype.TickBehavior behavior = object.getBehavior(BlockPrototype.TickBehavior.class);
//                if (behavior != null) {
//                    behavior.tick(object);
//                }
//            }
//        }
    }

    @Nonnull
    @Override
    public Block getBlock(int x, int y, int z) {
        Chunk chunk = chunkStorage.getChunkByBlockPos(x, y, z);
        return chunk == null ? Registries.getBlockRegistry().air() : chunk.getBlock(x, y, z);
    }

    @Nonnull
    @Override
    public int getBlockId(int x, int y, int z) {
        Chunk chunk = chunkStorage.getChunkByBlockPos(x, y, z);
        return chunk == null ? Registries.getBlockRegistry().air().getId() : chunk.getBlockId(x, y, z);
    }

    @Nonnull
    @Override
    public Block setBlock(@Nonnull BlockPos pos, @Nonnull Block block, @Nonnull BlockChangeCause cause) {
        Block oldBlock = getBlock(pos);
        if(!getGame().getEventBus().post(new BlockChangeEvent.Pre(this, pos, oldBlock, block,cause))) {
            chunkStorage.getOrLoadChunk(pos.getX() >> ChunkConstants.BITS_X, pos.getY() >> ChunkConstants.BITS_Y, pos.getZ() >> ChunkConstants.BITS_Z)
                    .setBlock(pos, block, cause);
            if(block == Blocks.AIR){
                oldBlock.getComponent(BlockPrototype.DestroyBehavior.class).ifPresent(destroyBehavior -> destroyBehavior.onDestroyed(this,null,pos,oldBlock, cause));
            }else {
                block.getComponent(BlockPrototype.PlaceBehavior.class).ifPresent(placeBehavior -> placeBehavior.onPlaced(this, null, pos, block, cause));
//                oldBlock.getComponent(BlockPrototype.PlaceBehavior.class).ifPresent(placeBehavior -> placeBehavior.onPlaced(this, null, pos, block, cause));
            }
            getGame().getEventBus().post(new BlockChangeEvent.Post(this, pos, oldBlock, block, cause)); // TODO:
            for (Facing facing : Facing.values()) {
                BlockPos pos1 = pos.offset(facing);
                Block block1 = getBlock(pos1);
                block1.getComponent(BlockPrototype.NeighborChangeListener.class).ifPresent(neighborChangeListener -> neighborChangeListener.onNeighborChange(this, pos1, block1, facing.opposite(), pos, block, cause));
            }
        }
        return oldBlock;
    }

    @Override
    public Block removeBlock(@Nonnull BlockPos pos, BlockChangeCause cause) {
        return setBlock(pos, Blocks.AIR, cause);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkY, int chunkZ) {
        return chunkStorage.getChunk(chunkX, chunkY, chunkZ);
    }

    @Override
    public Collection<Chunk> getLoadedChunks() {
        return chunkStorage.getLoadedChunks();
    }

    @Override
    public void run() {
        ticker.start();
    }

    public boolean isStopped() {
        return ticker.isStop();
    }

    public void stop() {
        ticker.stop();
    }

    public WorldCommonLoader getLoader() {
        return loader;
    }

    public void setLoader(WorldCommonLoader loader) {
        this.loader = loader;
    }

    public WorldCommonChunkManager getChunkManager() {
        return chunkManager;
    }

    public void setChunkManager(WorldCommonChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    static class PhysicsSystem {
        public void tick(World world) {
            List<Entity> entityList = world.getEntities();
            for (Entity entity : entityList) {
                Vector3f motion = entity.getMotion();
                if (motion.x == 0 && motion.y == 0 && motion.z == 0)
                    continue;
                Vector3f direction = new Vector3f(motion);
                Vector3d position = entity.getPosition();
                AABBd box = entity.getBoundingBox();
                if (box == null)
                    continue;

                BlockPos localPos = BlockPos.of(((int) Math.floor(position.x)), ((int) Math.floor(position.y)),
                        ((int) Math.floor(position.z)));

//                 int directionX = motion.x == -0 ? 0 : Float.compare(motion.x, 0),
//                 directionY = motion.y == -0 ? 0 : Float.compare(motion.y, 0),
//                 directionZ = motion.z == -0 ? 0 : Float.compare(motion.z, 0);

                AABBd entityBox = AABBs.translate(box, position.add(direction, new Vector3d()), new AABBd());
                List<BlockPos>[] around = AABBs.around(entityBox, motion);
                for (List<BlockPos> ls : around) {
                    ls.add(localPos);
                }
                List<BlockPos> faceX = around[0], faceY = around[1], faceZ = around[2];

                double xFix = Integer.MAX_VALUE, yFix = Integer.MAX_VALUE, zFix = Integer.MAX_VALUE;
                if (faceX.size() != 0) {
                    for (BlockPos pos : faceX) {
                        Block block = world.getBlock(pos);
                        AABBd[] blockBoxes = block.getBoundingBoxes();
                        if (blockBoxes.length != 0)
                            for (AABBd blockBoxLocal : blockBoxes) {
                                AABBd blockBox = AABBs.translate(blockBoxLocal,
                                        new Vector3f(pos.getX(), pos.getY(), pos.getZ()), new AABBd());
                                if (blockBox.testAABB(entityBox)) {
                                    xFix = Math.min(xFix, Math.min(Math.abs(blockBox.maxX - entityBox.minX),
                                            Math.abs(blockBox.minX - entityBox.maxX)));
                                }
                            }
                    }
                }
                if (faceY.size() != 0) {
                    for (BlockPos pos : faceY) {
                        Block block = world.getBlock(pos);
                        AABBd[] blockBoxes = block.getBoundingBoxes();
                        if (blockBoxes.length != 0)
                            for (AABBd blockBox : blockBoxes) {
                                AABBd translated = AABBs.translate(blockBox,
                                        new Vector3f(pos.getX(), pos.getY(), pos.getZ()), new AABBd());
                                if (translated.testAABB(entityBox)) {
                                    yFix = Math.min(yFix, Math.min(Math.abs(translated.maxY - entityBox.minY),
                                            Math.abs(translated.minY - entityBox.maxY)));
                                }
                            }
                    }
                }
                if (faceZ.size() != 0) {
                    for (BlockPos pos : faceZ) {
                        Block block = world.getBlock(pos);
                        AABBd[] blockBoxes = block.getBoundingBoxes();
                        if (blockBoxes.length != 0)
                            for (AABBd blockBox : blockBoxes) {
                                AABBd translated = AABBs.translate(blockBox,
                                        new Vector3f(pos.getX(), pos.getY(), pos.getZ()), new AABBd());
                                if (translated.testAABB(entityBox)) {
                                    zFix = Math.min(zFix, Math.min(Math.abs(translated.maxZ - entityBox.minZ),
                                            Math.abs(translated.minZ - entityBox.maxZ)));
                                }
                            }
                    }
                }
                if (Integer.MAX_VALUE != xFix)
                    motion.x = 0;
                if (Integer.MAX_VALUE != yFix)
                    motion.y = 0;
                if (Integer.MAX_VALUE != zFix) {
                    motion.z = 0;
                }

                // if (motion.y > 0) motion.y -= 0.01f;
                // else if (motion.y < 0) motion.y += 0.01f;
                // if (Math.abs(motion.y) <= 0.01f) motion.y = 0; // physics update
            }
        }
    }
}
