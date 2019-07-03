package nullengine.world;

import nullengine.block.Block;
import nullengine.block.component.DestroyBehavior;
import nullengine.block.component.NeighborChangeListener;
import nullengine.block.component.PlaceBehavior;
import nullengine.entity.Entity;
import nullengine.entity.PlayerEntity;
import nullengine.event.world.block.BlockChangeEvent;
import nullengine.event.world.block.BlockDestroyEvent;
import nullengine.event.world.block.BlockPlaceEvent;
import nullengine.event.world.block.BlockReplaceEvent;
import nullengine.event.world.block.cause.BlockChangeCause;
import nullengine.game.Game;
import nullengine.math.AABBs;
import nullengine.math.BlockPos;
import nullengine.math.FixStepTicker;
import nullengine.player.Player;
import nullengine.registry.Registries;
import nullengine.util.Facing;
import nullengine.world.chunk.Chunk;
import nullengine.world.chunk.ChunkConstants;
import nullengine.world.chunk.WorldCommonChunkManager;
import nullengine.world.collision.WorldCollisionManager;
import nullengine.world.collision.WorldCollisionManagerImpl;
import nullengine.world.gen.ChunkGenerator;
import nullengine.world.storage.WorldCommonLoader;
import org.apache.commons.lang3.Validate;
import org.joml.AABBd;
import org.joml.Vector3d;
import org.joml.Vector3f;
import unknowndomaingame.foundation.init.Blocks;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WorldCommon implements World, Runnable {

    private final Game game;

    private final PhysicsSystem physicsSystem = new PhysicsSystem(); // prepare for split
    private final WorldCollisionManager collisionManager = new WorldCollisionManagerImpl(this);

    //private final ChunkStorage chunkStorage;
    private WorldCommonLoader loader;
    private WorldCommonChunkManager chunkManager;
    private final List<Long> criticalChunks;
    private final List<Player> players = new ArrayList<>();
    private final List<Entity> entityList = new ArrayList<>();
    private final List<Runnable> nextTick = new ArrayList<>();
    private WorldInfo worldInfo;

    private final FixStepTicker ticker;
    private long gameTick;
//    private ExecutorService service;

    public WorldCommon(Game game, WorldCommonLoader loader, ChunkGenerator chunkGenerator) {
        this.game = game;
        //this.chunkStorage = new ChunkStorage(this);
        this.loader = loader;
        this.chunkManager = new WorldCommonChunkManager(this, chunkGenerator);
        this.ticker = new FixStepTicker(this::tick, FixStepTicker.LOGIC_TICK); // TODO: make tps configurable
        criticalChunks = new ArrayList<>();
    }

    public void spawnEntity(Entity entity) {
        BlockPos pos = ChunkConstants.toChunkPos(BlockPos.of(entity.getPosition()));
        Chunk chunk = chunkManager.loadChunk(pos.getX(), pos.getY(), pos.getZ());
        chunk.getEntities().add(entity);
        entityList.add(entity);
    }


    public void onPlayerJoin(@Nonnull Player player) {
        Player player1=Validate.notNull(player);
        var entity = new PlayerEntity(entityList.size(), this);
        player1.controlEntity(entity);
        spawnEntity(entity);
        players.add(player);
    }

    @Override
    public Game getGame() {
        return game;
    }

    @Override
    public WorldInfo getWorldInfo() {
        return worldInfo;
    }

    @Override
    public List<Entity> getEntities() {
        return entityList;
    }

    /**
     * Get the list of chunkpos which the corresponding chunk should be force loaded
     *
     * @return
     */
    public List<Long> getCriticalChunks() {
        return criticalChunks;
    }

    @Override
    public WorldCollisionManager getCollisionManager() {
        return collisionManager;
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
        chunkManager.getChunks().forEach(this::tickChunk);
    }

    protected void tickEntities() {
        for (Entity entity : entityList)
            entity.tick(); // state machine update
    }

    protected void tickEntityMotion() {
        for (Entity entity : this.getEntities()) {
            Vector3d position = entity.getPosition();
            Vector3f motion = entity.getMotion();
            BlockPos oldPosition = ChunkConstants.toChunkPos(BlockPos.of(position));
            position.add(motion);
            BlockPos newPosition = ChunkConstants.toChunkPos(BlockPos.of(position));

            if (!BlockPos.inSameChunk(oldPosition, newPosition)) {
                Chunk oldChunk = chunkManager.loadChunk(oldPosition.getX(), oldPosition.getY(), oldPosition.getZ()),
                        newChunk = chunkManager.loadChunk(newPosition.getX(), newPosition.getY(), newPosition.getZ());
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
        Chunk chunk = chunkManager.loadChunk(x >> ChunkConstants.BITS_X, y >> ChunkConstants.BITS_Y, z >> ChunkConstants.BITS_Z);
        return chunk == null ? Registries.getBlockRegistry().air() : chunk.getBlock(x, y, z);
    }

    @Nonnull
    @Override
    public int getBlockId(int x, int y, int z) {
        Chunk chunk = chunkManager.loadChunk(x >> ChunkConstants.BITS_X, y >> ChunkConstants.BITS_Y, z >> ChunkConstants.BITS_Z);
        return chunk == null ? Registries.getBlockRegistry().air().getId() : chunk.getBlockId(x, y, z);
    }

    @Nonnull
    @Override
    public Block setBlock(@Nonnull BlockPos pos, @Nonnull Block block, @Nonnull BlockChangeCause cause) {
        Block oldBlock = getBlock(pos);
        if (!getGame().getEventBus().post(new BlockChangeEvent.Pre(this, pos, oldBlock, block, cause))) {
            chunkManager.loadChunk(pos.getX() >> ChunkConstants.BITS_X, pos.getY() >> ChunkConstants.BITS_Y, pos.getZ() >> ChunkConstants.BITS_Z)
                    .setBlock(pos, block, cause);
            if (block == Blocks.AIR) {
                oldBlock.getComponent(DestroyBehavior.class).ifPresent(destroyBehavior -> destroyBehavior.onDestroyed(this, null, pos, oldBlock, cause));
                getGame().getEventBus().post(new BlockDestroyEvent(this, pos, oldBlock, cause));
            } else if (oldBlock == Blocks.AIR) {
                block.getComponent(PlaceBehavior.class).ifPresent(placeBehavior -> placeBehavior.onPlaced(this, null, pos, block, cause));
                getGame().getEventBus().post(new BlockPlaceEvent(this, pos, block, cause));
            }else{
                oldBlock.getComponent(DestroyBehavior.class).ifPresent(destroyBehavior -> destroyBehavior.onDestroyed(this, null, pos, oldBlock, cause));
                block.getComponent(PlaceBehavior.class).ifPresent(placeBehavior -> placeBehavior.onPlaced(this, null, pos, block, cause));
                getGame().getEventBus().post(new BlockReplaceEvent(this, pos, oldBlock, block, cause));
            }
            getGame().getEventBus().post(new BlockChangeEvent.Post(this, pos, oldBlock, block, cause)); // TODO:
            for (Facing facing : Facing.values()) {
                BlockPos pos1 = pos.offset(facing);
                Block block1 = getBlock(pos1);
                block1.getComponent(NeighborChangeListener.class).ifPresent(neighborChangeListener -> neighborChangeListener.onNeighborChange(this, pos1, block1, facing.opposite(), pos, block, cause));
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
        return chunkManager.loadChunk(chunkX, chunkY, chunkZ);
    }

    @Override
    public Collection<Chunk> getLoadedChunks() {
        return chunkManager.getChunks();
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
				//Vector3f motion = entity.getMotion();
				//if (motion.x == 0 && motion.y == 0 && motion.z == 0)
					//continue;
				Vector3f direction = new Vector3f();
				Vector3d position = entity.getPosition();
				AABBd box = entity.getBoundingBox();
				if (box == null)
				    continue;
				else setBoxXYZ(world,box,position,direction);
			}
		}
		public void setBoxXYZ (World world,AABBd box,Vector3d position,Vector3f direction){
			BlockPos localPos = BlockPos.of(((int) Math.floor(position.x)), ((int) Math.floor(position.y)),
				((int) Math.floor(position.z)));
			//                 int directionX = motion.x == -0 ? 0 : Float.compare(motion.x, 0),
			//                 directionY = motion.y == -0 ? 0 : Float.compare(motion.y, 0),
			//                 directionZ = motion.z == -0 ? 0 : Float.compare(motion.z, 0);
			AABBd entityBox = AABBs.translate(box, position.add(direction, new Vector3d()), new AABBd());
			List<BlockPos>[] around = AABBs.around(entityBox, direction);
			for (List<BlockPos> ls : around) {
				ls.add(localPos);
			}
			List<BlockPos> faceX = around[0], faceY = around[1], faceZ = around[2];

			double xFix = Integer.MAX_VALUE, yFix = Integer.MAX_VALUE, zFix = Integer.MAX_VALUE;
			if (faceX.size() != 0) {
				for (BlockPos pos : faceX) {
					Block block = world.getBlock(pos);
					AABBd[] blockBoxes = block.getShape().getBoundingBoxes(world, pos, block);
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
					AABBd[] blockBoxes = block.getShape()
						.getBoundingBoxes(world, pos, block);
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
					AABBd[] blockBoxes = block.getShape()
						.getBoundingBoxes(world, pos, block);
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
				direction.x = 0;
			if (Integer.MAX_VALUE != yFix)
				direction.y = 0;
			if (Integer.MAX_VALUE != zFix) {
				direction.z = 0;
			}

			// if (motion.y > 0) motion.y -= 0.01f;
			// else if (motion.y < 0) motion.y += 0.01f;
			// if (Math.abs(motion.y) <= 0.01f) motion.y = 0; // physics update
				}
			}
		}
    

