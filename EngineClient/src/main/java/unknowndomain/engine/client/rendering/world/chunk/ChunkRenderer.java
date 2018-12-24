package unknowndomain.engine.client.rendering.world.chunk;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import unknowndomain.engine.client.rendering.RenderContext;
import unknowndomain.engine.client.rendering.Renderer;
import unknowndomain.engine.client.rendering.block.BlockRenderer;
import unknowndomain.engine.client.rendering.block.ModelBlockRenderer;
import unknowndomain.engine.client.rendering.shader.Shader;
import unknowndomain.engine.client.rendering.shader.ShaderProgram;
import unknowndomain.engine.client.rendering.util.BufferBuilder;
import unknowndomain.engine.event.Listener;
import unknowndomain.engine.event.world.block.BlockChangeEvent;
import unknowndomain.engine.event.world.chunk.ChunkLoadEvent;
import unknowndomain.engine.math.BlockPos;
import unknowndomain.engine.world.chunk.Chunk;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL11.*;
import static unknowndomain.engine.client.rendering.shader.Shader.setUniform;
import static unknowndomain.engine.client.rendering.texture.TextureTypes.BLOCK;

public class ChunkRenderer implements Renderer {
    private final BlockRenderer blockRenderer = new ModelBlockRenderer();
    private final ShaderProgram chunkSolidShader;

    private final LongObjectMap<ChunkMesh> loadedChunkMeshes = new LongObjectHashMap<>();

    private final ThreadPoolExecutor updateExecutor;
    private final BlockingQueue<Runnable> uploadTasks = new LinkedBlockingQueue<>();

    private final int u_ProjMatrix, u_ViewMatrix;

    private RenderContext context;

    public ChunkRenderer(Shader vertex, Shader frag) {
        chunkSolidShader = new ShaderProgram();
        chunkSolidShader.init(vertex, frag);
        u_ProjMatrix = chunkSolidShader.getUniformLocation("u_ProjMatrix");
        u_ViewMatrix = chunkSolidShader.getUniformLocation("u_ViewMatrix");

        ThreadGroup threadGroup = new ThreadGroup("Chunk Baker");
        int threadCount = Runtime.getRuntime().availableProcessors() / 2;
        this.updateExecutor = new ThreadPoolExecutor(threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(), new ThreadFactory() {
            private final AtomicInteger poolNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new BakeChunkThread(threadGroup, r, "Chunk Baker " + poolNumber.getAndIncrement());
            }
        });
    }

    @Override
    public void init(RenderContext context) {
        this.context = context;
    }

    @Override
    public void render() {
        preRenderChunk();

        handleUploadTask();

        for (ChunkMesh chunkMesh : loadedChunkMeshes.values()) {
            if (context.getFrustumIntersection().testAab(chunkMesh.getMin(), chunkMesh.getMax())) {
                chunkMesh.render();
            }
        }

        postRenderChunk();
    }

    protected void preRenderChunk() {
        chunkSolidShader.use();

        glEnable(GL11.GL_BLEND);
        glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL11.GL_CULL_FACE);
//        GL11.glCullFace(GL11.GL_BACK);
//        glFrontFace(GL_CCW);
        glEnable(GL11.GL_TEXTURE_2D);
        glEnable(GL11.GL_DEPTH_TEST);

        Matrix4f projMatrix = context.getWindow().projection();
        Matrix4f viewMatrix = context.getCamera().view((float) context.partialTick());
        setUniform(u_ProjMatrix, projMatrix);
        setUniform(u_ViewMatrix, viewMatrix);

        context.getTextureManager().getTextureAtlas(BLOCK).bind();
    }

    protected void postRenderChunk() {
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL11.GL_CULL_FACE);
        glDisable(GL11.GL_TEXTURE_2D);
        glDisable(GL11.GL_DEPTH_TEST);
        glDisable(GL11.GL_BLEND);
    }

    @Override
    public void dispose() {
        updateExecutor.shutdown();
        chunkSolidShader.dispose();
    }

    public BlockRenderer getBlockRenderer() {
        return blockRenderer;
    }

    public void upload(ChunkMesh chunkMesh, BufferBuilder buffer) {
        ByteBuffer finalBuffer = BufferUtils.createByteBuffer(buffer.build().limit());
        finalBuffer.put(buffer.build());
        finalBuffer.flip();
        uploadTasks.add(new UploadTask(chunkMesh, finalBuffer, buffer.getVertexCount()));

        if (chunkMesh.isDirty()) {
            addBakeChunkTask(chunkMesh);
        }
    }

    public void handleUploadTask() {
        Runnable runnable;
        while ((runnable = uploadTasks.poll()) != null) {
            runnable.run();
        }
    }

    @Listener
    public void onChunkLoad(ChunkLoadEvent event) {
        long chunkIndex = getChunkIndex(event.getChunk());
        loadedChunkMeshes.put(chunkIndex, new ChunkMesh(event.getChunk()));
        markDirty(chunkIndex);
    }

    @Listener
    public void onBlockChange(BlockChangeEvent.Post event) {
        BlockPos pos = event.getPos().toImmutable();
        int chunkX = pos.getX() >> Chunk.CHUNK_BLOCK_POS_BIT,
                chunkY = pos.getY() >> Chunk.CHUNK_BLOCK_POS_BIT,
                chunkZ = pos.getZ() >> Chunk.CHUNK_BLOCK_POS_BIT;
        markDirty(getChunkIndex(event.getPos()));

        // Update neighbor chunks.
        int chunkW = pos.getX() + 1 >> Chunk.CHUNK_BLOCK_POS_BIT;
        if (chunkW != chunkX) {
            markDirty(getChunkIndex(chunkW, chunkY, chunkZ));
        }
        chunkW = pos.getX() - 1 >> Chunk.CHUNK_BLOCK_POS_BIT;
        if (chunkW != chunkX) {
            markDirty(getChunkIndex(chunkW, chunkY, chunkZ));
        }
        chunkW = pos.getY() + 1 >> Chunk.CHUNK_BLOCK_POS_BIT;
        if (chunkW != chunkY) {
            markDirty(getChunkIndex(chunkX, chunkW, chunkZ));
        }
        chunkW = pos.getY() - 1 >> Chunk.CHUNK_BLOCK_POS_BIT;
        if (chunkW != chunkY) {
            markDirty(getChunkIndex(chunkX, chunkW, chunkZ));
        }
        chunkW = pos.getZ() + 1 >> Chunk.CHUNK_BLOCK_POS_BIT;
        if (chunkW != chunkZ) {
            markDirty(getChunkIndex(chunkX, chunkY, chunkW));
        }
        chunkW = pos.getZ() - 1 >> Chunk.CHUNK_BLOCK_POS_BIT;
        if (chunkW != chunkZ) {
            markDirty(getChunkIndex(chunkX, chunkY, chunkW));
        }
    }

    private void markDirty(long index) {
        ChunkMesh chunkMesh = loadedChunkMeshes.get(index);
        if (chunkMesh == null) {
            return;
        }
        if (!chunkMesh.isDirty()) {
            chunkMesh.markDirty();
            addBakeChunkTask(chunkMesh);
        } else {
            chunkMesh.markDirty();
        }
    }

    private void addBakeChunkTask(ChunkMesh chunkMesh) {
        updateExecutor.execute(new BakeChunkTask(this, chunkMesh, getDistanceSqChunkToCamera(chunkMesh.getChunk())));
    }

    private double getDistanceSqChunkToCamera(Chunk chunk) {
        // FIXME:
        if (context.getCamera() == null) {
            return 0;
        }

        Vector3f position = context.getCamera().getPosition(0);
        double x = (chunk.getChunkX() << Chunk.CHUNK_BLOCK_POS_BIT) + 8 - position.x;
        double y = (chunk.getChunkY() << Chunk.CHUNK_BLOCK_POS_BIT) + 8 - position.y;
        double z = (chunk.getChunkZ() << Chunk.CHUNK_BLOCK_POS_BIT) + 8 - position.z;
        return x * x + y * y + z * z;
    }

    private static final int maxPositiveChunkPos = (1 << 20) - 1;

    public static long getChunkIndex(BlockPos pos) {
        return getChunkIndex(pos.getX() >> Chunk.CHUNK_BLOCK_POS_BIT, pos.getY() >> Chunk.CHUNK_BLOCK_POS_BIT, pos.getZ() >> Chunk.CHUNK_BLOCK_POS_BIT);
    }

    public static long getChunkIndex(Chunk chunk) {
        return getChunkIndex(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ());
    }

    public static long getChunkIndex(int chunkX, int chunkY, int chunkZ) {
        return abs(chunkX, maxPositiveChunkPos) << 42 | abs(chunkY, maxPositiveChunkPos) << 21 | abs(chunkZ, maxPositiveChunkPos);
    }

    private static int abs(int value, int maxPositiveValue) {
        return value >= 0 ? value : maxPositiveValue - value;
    }

    private class UploadTask implements Runnable {

        private final ChunkMesh chunkMesh;
        private final ByteBuffer buffer;
        private final int vertexCount;

        public UploadTask(ChunkMesh chunkMesh, ByteBuffer buffer, int vertexCount) {
            this.chunkMesh = chunkMesh;
            this.buffer = buffer;
            this.vertexCount = vertexCount;
        }

        @Override
        public void run() {
            chunkMesh.upload(buffer, vertexCount);
        }
    }
}
