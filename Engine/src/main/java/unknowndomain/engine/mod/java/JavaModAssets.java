package unknowndomain.engine.mod.java;

import unknowndomain.engine.Platform;
import unknowndomain.engine.mod.ModAssets;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.stream.Stream;

public class JavaModAssets implements ModAssets {

    private FileSystem fileSystem;

    public JavaModAssets(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public Path get(String first) {
        Path path = fileSystem.getPath(first);
        if (Files.notExists(path)) {
            return null;
        }
        return path;
    }

    @Override
    public Path get(String first, String... more) {
        Path path = fileSystem.getPath(first, more);
        if (Files.notExists(path)) {
            return null;
        }
        return path;
    }

    @Override
    public InputStream openStream(String first) throws IOException {
        Path path = get(first);
        if (path == null) {
            return null;
        }
        return Files.newInputStream(path);
    }

    @Override
    public InputStream openStream(String first, String... more) throws IOException {
        Path path = get(first, more);
        if (path == null) {
            return null;
        }
        return Files.newInputStream(path);
    }

    @Override
    public Stream<Path> list(String first) {
        Path path = get(first);
        if(path == null) return Stream.empty();
        try {
            return Files.isDirectory(path) ? Files.list(path) : Stream.empty();
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("cannot list files of path %s", path), e);
            return Stream.empty();
        }
    }

    @Override
    public Stream<Path> list(String first, String... more) {
        Path path = get(first, more);
        if(path == null) return Stream.empty();
        try {
            return Files.isDirectory(path) ? Files.list(path) : Stream.empty();
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("cannot list files of path %s", path), e);
            return Stream.empty();
        }
    }

    @Override
    public boolean exists(String first) {
        return Files.exists(fileSystem.getPath(first));
    }

    @Override
    public boolean exists(String first, String... more) {
        return Files.exists(fileSystem.getPath(first, more));
    }

    @Override
    public void copy(Path target, boolean forceCopying, String first) {
        if (Files.notExists(target)) {
            try {
                Files.createFile(target);
            } catch (IOException e) {
                Platform.getLogger().warn(String.format("Exception thrown when attempted to create file %s", target), e);
                return;
            }
        }
        Path source = get(first);
        if (source == null) {
            Platform.getLogger().warn(String.format("Source file not exists when copying file from %s to %s", first, target));
            return;
        }

        try {
            Files.copy(source, target, forceCopying ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES} : new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES});
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("Exception thrown when copying file from %s to %s", source, target), e);
        }
    }

    @Override
    public void copy(Path target, boolean forceCopying, String first, String... more) {
        if (Files.notExists(target)) {
            try {
                Files.createFile(target);
            } catch (IOException e) {
                Platform.getLogger().warn(String.format("Exception thrown when attempted to create file %s", target), e);
                return;
            }
        }
        Path source = get(first, more);
        if (source == null) {
            Platform.getLogger().warn(String.format("Source file not exists when copying file from %s to %s", Path.of(first, more), target));
            return;
        }

        try {
            Files.copy(source, target, forceCopying ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES} : new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES});
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("Exception thrown when copying file from %s to %s", source, target), e);
        }
    }

    @Override
    public void copy(OutputStream output, String first) {
        try (var input = openStream(first)) {
            input.transferTo(output);
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("Exception thrown when copying file from %s", get(first)), e);
        }
    }

    @Override
    public void copy(OutputStream output, String first, String... more) {
        try (var input = openStream(first, more)) {
            input.transferTo(output);
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("Exception thrown when copying file from %s", get(first)), e);
        }
    }

    @Override
    public void copy(Writer writer, String first) {
        try (var reader = new FileReader(get(first).toFile())) {
            reader.transferTo(writer);
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("Exception thrown when copying file from %s", get(first)), e);
        }
    }

    @Override
    public void copy(Writer writer, String first, String... more) {
        try (var reader = new FileReader(get(first, more).toFile())) {
            reader.transferTo(writer);
        } catch (IOException e) {
            Platform.getLogger().warn(String.format("Exception thrown when copying file from %s", get(first)), e);
        }
    }
}
