package nullengine.server.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import nullengine.Platform;
import nullengine.server.network.packet.Packet;
import nullengine.server.network.packet.PacketRaw;
import nullengine.server.network.packet.UnrecognizedPacketException;

public class PacketEncoder extends MessageToByteEncoder<Packet> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) throws Exception {
        var id = Platform.getEngine().getRegistryManager().getRegistry(Packet.class).getId(msg);
        if(id == 0 && !(msg instanceof PacketRaw)){
            throw new UnrecognizedPacketException("No record for packet " + msg.getClass().getSimpleName() + " in Packet registry");
        }
        else{
            var wrapper = new PacketBuf(out);
            wrapper.writeVarInt(id);
            msg.write(wrapper);
        }
    }
}
