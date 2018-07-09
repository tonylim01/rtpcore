package Process;

import Audio.AMRWB;
import common.StringUtil;
import core.network.deprecated.RtpPortManager;
import core.network.deprecated.UdpManager;
import core.rtp.ChannelsManager;
import core.rtp.RTPDataChannel;
import core.rtp.rtp.RtpClock;
import core.rtp.rtp.RtpPacket;
import core.rtp.rtp.SsrcGenerator;
import core.scheduler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import x3.player.core.codec.AMRSlience;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class RtpTransfer {

    private static final Logger logger = LoggerFactory.getLogger( RtpTransfer.class);


    long base_timestamp = 1000000L;
    int defaultModeset = 8;
    private Scheduler scheduler;

    private UdpManager udpManager;
    private RTPDataChannel channel;

    private ChannelsManager channelsManager;
    private PriorityQueueScheduler mediaScheduler;

    private InetSocketAddress dst;

    private Clock clock = new WallClock();

    public void start()
    {
        long timestamp = -1;
        int seqNumber = 0;

        timestamp = this.generateTimeStamp();
        this.rtpPacket();
    }

    private void rtpPacket()  {

        mediaScheduler = new PriorityQueueScheduler();
        mediaScheduler.setClock(clock);
        mediaScheduler.start();

        scheduler = new ServiceScheduler();

        udpManager = new UdpManager( scheduler, new RtpPortManager(), new RtpPortManager());

        logger.debug("getBindAddress : " + udpManager.getBindAddress());

        scheduler.start();
        udpManager.start();

        logger.debug("channelsManager Start");
        channelsManager = new ChannelsManager( udpManager);
        channelsManager.setScheduler(mediaScheduler);

        channel = channelsManager.getChannel();

        logger.debug("channelsManager channel : " + channel.getLocalPort());

        try {
            channel.bind(false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int port = channel.getLocalPort();

        logger.debug("port : " + port);

        dst = new InetSocketAddress("192.168.5.70", port);

        channel.setPeer(new InetSocketAddress( "192.168.5.70", 9200));

//        logger.debug();

        DatagramSocket socket = null;





    }

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for(final byte b: a)
            sb.append(String.format("%02x ", b&0xff));
        return sb.toString();
    }

    private long generateTimeStamp()
    {
        long timestamp =  base_timestamp/ 100000L;

        RtpClock rtpClock = new RtpClock( clock);
        rtpClock.setClockRate( 320 );

        // convert to rtp time units
        timestamp = rtpClock.convertToRtpTime(timestamp);

        logger.debug( "Time Stamp : " + timestamp );

        return timestamp;
    }
}
