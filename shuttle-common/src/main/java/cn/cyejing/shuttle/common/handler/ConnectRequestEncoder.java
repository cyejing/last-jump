package cn.cyejing.shuttle.common.handler;

import cn.cyejing.shuttle.common.model.ConnectRequest;
import cn.cyejing.shuttle.common.model.ConnectRequest.ConnectAddressType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Born
 */
@Slf4j
public class ConnectRequestEncoder extends MessageToByteEncoder<ConnectRequest> {
    private final Socks5AddressEncoder addressEncoder = Socks5AddressEncoder.DEFAULT;

    protected void encode(ChannelHandlerContext ctx, ConnectRequest msg, ByteBuf out) {
        out.writeByte(msg.getVersion().byteValue());
        out.writeByte(msg.getType().byteValue());
        out.writeByte(0x00);
        final ConnectAddressType type = msg.getAddressType();
        out.writeByte(type.byteValue());
        addressEncoder.encodeAddress(type, msg.getRemoteHost(), out);
        out.writeShort(msg.getRemotePort());
    }


}
