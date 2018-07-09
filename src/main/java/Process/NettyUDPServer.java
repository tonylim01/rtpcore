package Process;


import core.rtp.jitter.JitterBuffer;
import core.rtp.netty.RtpInboundHandler;
import core.rtp.netty.RtpInboundHandlerGlobalContext;
import core.rtp.rtp.RTPInput;
import core.rtp.rtp.statistics.RtpStatistics;
import core.scheduler.Clock;
import core.sdp.format.AVProfile;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetAddress;

import static org.mockito.Mockito.mock;

public class NettyUDPServer {

    private int port;

    public NettyUDPServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        final NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            final Bootstrap b = new Bootstrap();
            b.group(group).channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {

                        @Override
                        public void initChannel(final NioDatagramChannel ch) throws Exception {

                            final Clock clock = mock(Clock.class);
                            final RtpStatistics statistics = mock(RtpStatistics.class);
                            final JitterBuffer jitterBuffer = mock(JitterBuffer.class);
                            final RTPInput rtpInput = mock(RTPInput.class);
                            final ChannelPipeline pipeline = ch.pipeline();

                            final RtpInboundHandlerGlobalContext context = new RtpInboundHandlerGlobalContext( clock, statistics, jitterBuffer, rtpInput);
                            context.setReceivable( true );
                            context.setLoopable( false );
                            context.setFormats( AVProfile.audio );

                            pipeline.addLast("logger", new LoggingHandler());
                            pipeline.addLast(new RtpInboundHandler(context));
                        }
                    });

            // Bind and start to accept incoming connections.
            Integer pPort = port;
            InetAddress address  = InetAddress.getByName("192.168.5.70");

            System.out.printf("waiting for message %s %s",String.format(pPort.toString()),String.format( address.toString()));
//            b.bind(address,port).sync().channel().closeFuture().await();
            b.bind( address, port).sync().channel();

            b.bind(address,port+1).sync().channel();
            Channel ch = b.bind(address,port+2).sync().channel();
//            ch.close();
//
//            ch = b.bind(address,port+2).sync().channel();
        } finally {
            System.out.print("In Server Finally");
        }
    }

}
