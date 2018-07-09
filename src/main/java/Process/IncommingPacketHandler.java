package Process;

import core.rtp.rtp.RtpPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncommingPacketHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private static final Logger logger = LoggerFactory.getLogger( IncommingPacketHandler.class);

    IncommingPacketHandler(String parserServer){

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        final InetAddress srcAddr = msg.sender().getAddress();
        final ByteBuf buf = msg.content();

        final int rcvPktLength = buf.readableBytes();
        final byte[] rcvPktBuf = new byte[rcvPktLength];
        buf.readBytes(rcvPktBuf);

        RtpPacket rtpPacket = new RtpPacket( RtpPacket.RTP_PACKET_MAX_SIZE, true);
        rtpPacket.getBuffer().put(rcvPktBuf, 0, rcvPktLength).flip();

        logger.debug( "srcAddr : " + srcAddr.toString() + " recv Packet : " + rtpPacket.toString());
    }
}
