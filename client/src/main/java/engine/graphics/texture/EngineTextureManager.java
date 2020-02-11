package engine.graphics.texture;

import engine.client.asset.*;
import engine.client.asset.exception.AssetLoadException;
import engine.client.asset.reloading.AssetReloadListener;
import engine.client.asset.source.AssetSourceManager;
import engine.graphics.image.BufferedImage;
import engine.util.Color;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class EngineTextureManager implements TextureManager, AssetProvider<Texture2D> {

    private final List<Asset<Texture2D>> assets = new ArrayList<>();

    private final Texture2D whiteTexture;

    private AssetSourceManager sourceManager;

    public EngineTextureManager() {
        this.whiteTexture = Texture2D.builder().build(new BufferedImage(1, 1, Color.WHITE));
    }

    @Override
    public Texture2D getWhiteTexture() {
        return whiteTexture;
    }

    @Override
    public void init(AssetManager manager, AssetType<Texture2D> type) {
        sourceManager = manager.getSourceManager();
        manager.getReloadManager().addListener(AssetReloadListener.builder().name("Texture").runnable(this::reload).build());
    }

    @Override
    public void register(Asset<Texture2D> asset) {
        assets.add(asset);
    }

    @Override
    public void unregister(Asset<Texture2D> asset) {
        var glTexture = asset.get();
        if (glTexture != null) {
            glTexture.dispose();
        }
        assets.remove(asset);
    }

    private void reload() {
        assets.forEach(asset -> {
            var glTexture = asset.get();
            if (glTexture != null) {
                glTexture.dispose();
            }
            asset.reload();
        });
    }

    @Nonnull
    @Override
    public Texture2D loadDirect(AssetURL url) {
        var localPath = sourceManager.getPath(url.toFileLocation("texture", ".png"));
        if (localPath.isEmpty()) {
            throw new AssetLoadException("Cannot load texture because missing asset. Path: " + url.toFileLocation("texture", ".png"));
        }

        try (var channel = Files.newByteChannel(localPath.get())) {
            var buffer = ByteBuffer.allocateDirect(Math.toIntExact(channel.size()));
            channel.read(buffer);
            buffer.flip();
            return Texture2D.builder().build(BufferedImage.load(buffer));
        } catch (IOException e) {
            throw new AssetLoadException("Cannot load texture because catch exception. Path: " + url.toFileLocation("texture", ".png"), e);
        }
    }

    @Override
    public void dispose() {
        assets.forEach(asset -> {
            var texture = asset.get();
            if (texture != null) {
                texture.dispose();
            }
        });
    }
}
