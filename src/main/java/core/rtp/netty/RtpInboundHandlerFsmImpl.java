/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package core.rtp.netty;

//import core.rtp.rtp.RtpChannel;
import core.rtp.rtp.RtpPacket;
import core.rtp.rtp.statistics.RtpStatistics;
import core.sdp.format.RTPFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class RtpInboundHandlerFsmImpl extends AbstractRtpInboundHandlerFsm {

    private static final Logger log = LogManager.getLogger(RtpInboundHandlerFsmImpl.class);

    private final RtpInboundHandlerGlobalContext context;

    public RtpInboundHandlerFsmImpl(RtpInboundHandlerGlobalContext context) {
        super();
        this.context = context;
    }

    @Override
    public void enterActivated(RtpInboundHandlerState from, RtpInboundHandlerState to, RtpInboundHandlerEvent event, RtpInboundHandlerTransactionContext context) {
        this.context.getRtpInput().activate();
    }

    @Override
    public void enterDeactivated(RtpInboundHandlerState from, RtpInboundHandlerState to, RtpInboundHandlerEvent event, RtpInboundHandlerTransactionContext context) {
        this.context.getRtpInput().deactivate();
        this.context.getJitterBuffer().restart();
    }

    @Override
    public void onPacketReceived(RtpInboundHandlerState from, RtpInboundHandlerState to, RtpInboundHandlerEvent event, RtpInboundHandlerTransactionContext context) {
        final RtpInboundHandlerPacketReceivedContext txContext = (RtpInboundHandlerPacketReceivedContext) context;
        final RtpPacket packet = txContext.getPacket();
        final int payloadType = packet.getPayloadType();
        final RTPFormat format = this.context.getFormats().find(payloadType);
        final RtpStatistics statistics = this.context.getStatistics();

        // RTP keep-alive
        statistics.setLastHeartbeat(this.context.getClock().getTime());
        
        if (format == null) {
            // Drop packet with unknown format
            log.warn("RTP Channel " + statistics.getSsrc() + " dropped packet because payload type " + payloadType + " is unknown.");
        } else {
            // Consume packet

            this.context.getJitterBuffer().write(packet, format);

            log.warn("RTP format : " + format.toString());

            // Update statistics
            statistics.onRtpReceive(packet);
        }
    }

}
