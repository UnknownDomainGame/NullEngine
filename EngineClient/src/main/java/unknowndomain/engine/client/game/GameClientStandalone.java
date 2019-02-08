package unknowndomain.engine.client.game;

import com.google.common.collect.Lists;
import unknowndomain.engine.Platform;
import unknowndomain.engine.client.EngineClient;
import unknowndomain.engine.client.event.asset.AssetLoadEvent;
import unknowndomain.engine.client.event.game.RendererRegisterEvent;
import unknowndomain.engine.client.input.controller.EntityCameraController;
import unknowndomain.engine.client.input.controller.EntityController;
import unknowndomain.engine.client.input.keybinding.KeyBinding;
import unknowndomain.engine.client.input.keybinding.KeyBindingManager;
import unknowndomain.engine.client.rendering.Renderer;
import unknowndomain.engine.client.rendering.camera.FirstPersonCamera;
import unknowndomain.engine.client.rendering.texture.TextureTypes;
import unknowndomain.engine.event.game.GameTerminationEvent;
import unknowndomain.engine.game.GameServerFullAsync;
import unknowndomain.engine.math.BlockPos;
import unknowndomain.engine.math.FixStepTicker;
import unknowndomain.engine.player.Player;
import unknowndomain.engine.player.PlayerImpl;
import unknowndomain.engine.player.Profile;
import unknowndomain.engine.world.World;
import unknowndomain.engine.world.WorldCommon;
import unknowndomain.game.Blocks;
import unknowndomain.game.DefaultGameMode;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class GameClientStandalone extends GameServerFullAsync implements GameClient {

    private KeyBindingManager keyBindingManager;

    private ClientContextImpl clientContext;
    private EntityController entityController;

    private WorldCommon world;
    private Player player;

    private FixStepTicker.Dynamic ticker;

    private boolean stopped;

    public GameClientStandalone(EngineClient engine) {
        super(engine);

        this.ticker = new FixStepTicker.Dynamic(this::clientTick, this::renderTick, FixStepTicker.clientTick);
    }

    /**
     * Get player client
     */
    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * @return the client world
     */
    @Override
    public World getWorld() {
        return world;
    }

    public EntityController getEntityController() {
        return entityController;
    }

    public KeyBindingManager getKeyBindingManager() {
        return keyBindingManager;
    }

    @Override
    public ClientContextImpl getClientContext() {
        return clientContext;
    }

    @Override
    protected void constructStage() {
        super.constructStage();

        // TODO: Move it
        getEventBus().register(new DefaultGameMode());

        player = new PlayerImpl(new Profile(UUID.randomUUID(), 12));
    }

    @Override
    protected void registerStage() {
        super.registerStage();

        logger.info("Loading Client-only stuff!");
        keyBindingManager = new KeyBindingManager(getContext().getRegistryManager().getRegistry(KeyBinding.class));
        keyBindingManager.reload();
        Platform.getEngineClient().getWindow().addKeyCallback(keyBindingManager::handleKey);
        Platform.getEngineClient().getWindow().addMouseCallback(keyBindingManager::handleMouse);

        context.post(new AssetLoadEvent(Platform.getEngineClient().getAssetLoadManager(), Platform.getEngineClient().getAssetManager()));

        List<Renderer> registeredRenderers = Lists.newArrayList();
        context.post(new RendererRegisterEvent(registeredRenderers));

        clientContext = new ClientContextImpl(this, registeredRenderers, Platform.getEngineClient().getWindow(), player);
        clientContext.initClient();
        clientContext.setCamera(new FirstPersonCamera(player));
    }

    @Override
    protected void resourceStage() {
        Platform.getEngineClient().getTextureManager().initTextureAtlas(TextureTypes.BLOCK);
    }

    @Override
    protected void finishStage() {
        logger.info("Finishing Game Initialization!");

        // TODO: Remove it
        spawnWorld(null);
        world = (WorldCommon) getWorld("default");
        world.playerJoin(player);
        player.getControlledEntity().getPosition().set(1, 3, 1);

        entityController = new EntityCameraController(player);
        clientContext.getWindow().addCursorCallback((xpos, ypos) -> {
            if (clientContext.getWindow().isCursorHidden()) {
                entityController.handleCursorMove(xpos, ypos);
            }
        });
        // For now until we figure out how to setup games
        getKeyBindingManager().setGameContext(clientContext);

        super.finishStage();
        logger.info("Game Ready!");

        // TODO: Remove it
        Random random = new Random();
        for (int x = -16; x < 16; x++) {
            for (int z = -16; z < 16; z++) {
                for (int top = 3, y = top; y >= 0; y--) {
                    world.setBlock(BlockPos.of(x, y, z), y == top ? Blocks.GRASS : Blocks.DIRT, null);
                }
            }
        }
    }

    @Override
    public void run() {
        super.run();
        ticker.start(); // start to tick
    }

    @Override
    public void terminate() {
        logger.info("Terminating Game!");

        getEventBus().post(new GameTerminationEvent.Pre(this));

        super.terminate();
        ticker.stop();
        clientContext.dispose();
        clientContext.getWindow().close();

        getEventBus().post(new GameTerminationEvent.Post(this));

        logger.info("Terminated Game!");
    }

    private void clientTick() {
        getKeyBindingManager().tick();
        // TODO upload particle physics here
    }

    /**
     * Actual render call
     *
     * @param partialTick
     */
    private void renderTick(double partialTick) {
        Platform.getEngineClient().getWindow().beginRender();
        clientContext.updateFps();
        this.clientContext.render(partialTick);
        Platform.getEngineClient().getWindow().endRender();
    }
}