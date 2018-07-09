package core.rtp.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import core.rtp.rtp.RTPInput;
import core.rtp.rtp.RtpPacket;
import core.sdp.format.RTPFormats;
import core.spi.ConnectionMode;
import io.netty.channel.socket.DatagramPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;

/**
 * Handler that processes incoming RTP packets for audio
 * 
 * @author Tony Lim(tonylim@uangel.com)
 *
 */
public class RtpInboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger log = LogManager.getLogger(RtpInboundHandler.class);

    private final RtpInboundHandlerGlobalContext context;
    private final RtpInboundHandlerFsm fsm;

    public RtpInboundHandler(RtpInboundHandlerGlobalContext context) {
        this.context = context;
        this.fsm = RtpInboundHandlerFsmBuilder.INSTANCE.build(context);
        this.isActive();
        if(!this.isActive()) {
            this.fsm.start();
        }
    }

    public void activate() {
        if(!this.isActive()) {
            this.fsm.start();
        }
    }

    public void deactivate() {
        if(this.isActive()) {
            this.fsm.fire(RtpInboundHandlerEvent.DEACTIVATE);
        }
    }
    
    public boolean isActive() {
        return RtpInboundHandlerState.ACTIVATED.equals(this.fsm.getCurrentState());
    }
    
    public void updateMode(ConnectionMode mode) {
        switch (mode) {
            case INACTIVE:
            case SEND_ONLY:
                this.context.setLoopable(false);
                this.context.setReceivable(false);
                break;

            case RECV_ONLY:
                this.context.setLoopable(false);
                this.context.setReceivable(true);
                break;

            case SEND_RECV:
            case CONFERENCE:
                this.context.setLoopable(false);
                this.context.setReceivable(true);
                break;

            case NETWORK_LOOPBACK:
                this.context.setLoopable(true);
                this.context.setReceivable(false);
                break;

            default:
                this.context.setLoopable(false);
                this.context.setReceivable(false);
                break;
        }
    }
    
    public void setFormatMap(RTPFormats formats) {
        this.context.setFormats(formats);
    }

    public void useJitterBuffer(boolean use) {
        this.context.getJitterBuffer().setInUse(use);
    }

    public RTPInput getRtpInput() {
        return context.getRtpInput();
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

        log.debug( "srcAddr : " + srcAddr.toString() + " recv Packet : " + rtpPacket.toString());

        int version = rtpPacket.getVersion();
        if (version == 0) {
            if (log.isDebugEnabled()) {
                log.debug("RTP Channel " + this.context.getStatistics().getSsrc() + " dropped RTP v0 packet.");
            }
            return;
        }

        log.debug("RTP version " + version + " packet.");

        // Check if channel can receive traffic
        boolean canReceive = (context.isReceivable() || context.isLoopable());

        log.debug("RTP canReceive " + canReceive + " packet.");

        if (!canReceive) {
            if (log.isDebugEnabled()) {
                log.debug("RTP Channel " + this.context.getStatistics().getSsrc() + " dropped packet because channel mode does not allow to receive traffic.");
            }
            return;
        }

        log.debug("getRtpInput : " + context.getStatistics());
        // Check if packet is not empty
        boolean hasData = (rtpPacket.getLength() > 0);
        if (!hasData) {
            if (log.isDebugEnabled()) {
                log.debug("RTP Channel " + this.context.getStatistics().getSsrc() + " dropped packet because payload was empty.");
            }
            return;
        }

        // Process incoming packet
        RtpInboundHandlerPacketReceivedContext txContext = new RtpInboundHandlerPacketReceivedContext(rtpPacket);
        this.fsm.fire(RtpInboundHandlerEvent.PACKET_RECEIVED, txContext);

        // Send packet back if channel is operating in NETWORK_LOOPBACK mode
        if (context.isLoopable()) {
            ctx.channel().writeAndFlush(msg);
        }


    }
}
