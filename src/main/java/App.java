import Process.RtpTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import Process.NettyUDPServer;

public class App {

    private static final Logger logger = LoggerFactory.getLogger( App.class);

    public static void main(String[] args) {

        logger.debug( "RTP Transfer Start !!!" );

//        RtpTransfer rtpTransfer = new RtpTransfer();
//        rtpTransfer.start();

        int port =10240;

        try {
            new NettyUDPServer(port).run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
