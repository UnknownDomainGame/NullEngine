package engine.client.asset;

import engine.client.asset.reloading.AssetReloadManager;
import engine.client.asset.source.AssetSourceManager;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EngineAssetManager implements AssetManager {

    private final Map<String, AssetType<?>> registeredTypes = new HashMap<>();

    private final AssetSourceManager sourceManager = new AssetSourceManagerImpl();
    private final AssetReloadManagerImpl reloadManager = new AssetReloadManagerImpl();

    public EngineAssetManager() {
        AssetSourceManager.Internal.setInstance(sourceManager);
        AssetURLStreamHandler.initialize();
        AssetManager.Internal.setInstance(this);
    }

    @Override
    public <T> AssetType<T> register(@Nonnull AssetType<T> type) {
        if (registeredTypes.containsKey(type.getName())) {
            throw new IllegalArgumentException(String.format("AssetType %s has been registered.", type.getName()));
        }

        registeredTypes.put(type.getName(), type);
        type.getProvider().init(this, type);
        return type;
    }

    @Override
    public Optional<AssetType<?>> getType(String name) {
        return Optional.ofNullable(registeredTypes.get(name));
    }

    @Override
    public boolean hasType(String name) {
        return registeredTypes.containsKey(name);
    }

    @Override
    public Collection<AssetType<?>> getSupportedTypes() {
        return registeredTypes.values();
    }

    @Nonnull
    @Override
    public <T> Asset<T> create(@Nonnull AssetType<T> type, @Nonnull AssetURL url) {
        Asset<T> asset = new Asset<>(type, url);
        type.getProvider().register(asset);
        return asset;
    }

    @Nonnull
    @Override
    public <T> T loadDirect(@Nonnull AssetType<T> type, @Nonnull AssetURL url) {
        return type.getProvider().loadDirect(url);
    }

    @Override
    public AssetSourceManager getSourceManager() {
        return sourceManager;
    }

    @Override
    public AssetReloadManager getReloadManager() {
        return reloadManager;
    }

    @Override
    public void reload() {
        reloadManager.reload();
    }

    public void dispose() {
        registeredTypes.values().forEach(type -> type.getProvider().dispose());
    }
}
