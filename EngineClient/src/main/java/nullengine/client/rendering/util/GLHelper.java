package nullengine.client.rendering.util;

import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_INVALID_FRAMEBUFFER_OPERATION;

public class GLHelper {

    @Deprecated
    public static String readText(String path) {
        StringBuilder sb = new StringBuilder();
        try (InputStream a = GLHelper.class.getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(a))) {
            String str;
            while ((str = reader.readLine()) != null) {
                sb.append(str).append('\n');
            }
        } catch (IOException e) {

        }
        return sb.toString();
    }

    private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    public static ByteBuffer getResourcesAsBuffer(String resource, int size) throws IOException {
        ByteBuffer buffer;

        Path path = Paths.get(resource);
        if (Files.isReadable(path)) {
            try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                buffer = BufferUtils.createByteBuffer((int) fc.size() + 1);
                while (fc.read(buffer) != -1) {
                    ;
                }
            }
        } else {
            try (
                    InputStream source = GLHelper.class.getClassLoader().getResourceAsStream(resource);
                    ReadableByteChannel rbc = Channels.newChannel(source)
            ) {
                buffer = BufferUtils.createByteBuffer(size);

                while (true) {
                    int bytes = rbc.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() == 0) {
                        buffer = resizeBuffer(buffer, buffer.capacity() * 3 / 2); // 50%
                    }
                }
            } catch (Exception e) {
                throw new IOException(String.format("cannot load resource: %s", resource), e);
            }
        }

        buffer.flip();
        return buffer.slice();
    }

    public static ByteBuffer getByteBufferFromImage(BufferedImage img) {
        int[] data = new int[img.getWidth() * img.getHeight()];
        img.getRGB(0, 0, img.getWidth(), img.getHeight(), data, 0, img.getWidth());

        ByteBuffer buffer = BufferUtils.createByteBuffer(img.getWidth() * img.getHeight() * 4);
        boolean isAlpha = img.getType() == BufferedImage.TYPE_4BYTE_ABGR ||
                img.getType() == BufferedImage.TYPE_4BYTE_ABGR_PRE ||
                img.getType() == BufferedImage.TYPE_INT_ARGB ||
                img.getType() == BufferedImage.TYPE_INT_ARGB_PRE;

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int pixel = data[y * img.getWidth() + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF));     // Red component
                buffer.put((byte) ((pixel >> 8) & 0xFF));      // Green component
                buffer.put((byte) (pixel & 0xFF));               // Blue component
                if (isAlpha)
                    buffer.put((byte) ((pixel >> 24) & 0xFF));    // Alpha component. Only for RGBA
                else {
                    if (img.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
                        if (pixel == img.getColorModel().getRGB(((IndexColorModel) img.getColorModel()).getTransparentPixel())) {
                            buffer.put((byte) 0);
                        } else {
                            buffer.put((byte) 255);
                        }
                    } else {
                        buffer.put((byte) 255);
                    }
                }
            }
        }
        buffer.flip();
        return buffer;
    }

    public static String getFriendlyErrorEnum(int flag) {
        var error = "";
        switch (flag) {
            case GL_NO_ERROR:
                error = "NO_ERROR";
                break;
            case GL_INVALID_ENUM:
                error = "INVALID_ENUM";
                break;
            case GL_INVALID_VALUE:
                error = "INVALID_VALUE";
                break;
            case GL_INVALID_OPERATION:
                error = "INVALID_OPERATION";
                break;
            case GL_STACK_OVERFLOW:
                error = "STACK_OVERFLOW";
                break;
            case GL_STACK_UNDERFLOW:
                error = "STACK_UNDERFLOW";
                break;
            case GL_OUT_OF_MEMORY:
                error = "OUT_OF_MEMORY";
                break;
            case GL_INVALID_FRAMEBUFFER_OPERATION:
                error = "INVALID_FRAMEBUFFER_OPERATION";
                break;
        }
        return error;
    }
}
