/*
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

package core.rtp;

import core.network.deprecated.ProtocolHandler;
import core.network.deprecated.UdpManager;
import core.rtp.jitter.FixedJitterBuffer;
import core.rtp.rtp.RTPInput;
import core.rtp.rtp.RTPOutput;
import core.rtp.rtp.RtpClock;
import core.rtp.rtp.RtpPacket;
import core.scheduler.PriorityQueueScheduler;
import core.scheduler.Task;
import core.sdp.format.RTPFormat;
import core.sdp.format.RTPFormats;
import core.spi.FormatNotSupportedException;
import core.spi.format.AudioFormat;
import core.spi.format.FormatFactory;
import core.spi.format.Formats;
import core.spi.memory.Frame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;


/**
 * 
 * @author Tony Lim
 */
@Deprecated
public class RTPDataChannel {
	
	private Logger logger = LogManager.getLogger( RTPDataChannel.class);

	private final static AudioFormat LINEAR_FORMAT = FormatFactory.createAudioFormat( "LINEAR", 8000, 16, 1);

	private final static int PORT_ANY = -1;

	private final long ssrc = System.currentTimeMillis();

	// Available Channels
	private ChannelsManager channelsManager;
	private DatagramChannel rtpChannel;
	private DatagramChannel rtcpChannel;

	private boolean rtpChannelBound = false;
	private boolean rtcpChannelBound = false;

	// Receiver and transmitter
	private RTPInput input;
	private RTPOutput output;

	// tx task - sender
	private TxTask tx = new TxTask();

	// RTP clock
	private RtpClock rtpClock, oobClock;

	// allowed jitter
	private int jitterBufferSize;

	// Media stream format
	private RTPFormats rtpFormats = new RTPFormats();

	// Remote peer address
	private SocketAddress remotePeer;
	private int sn;

	private int count;

	private RTPHandler rtpHandler;

	private volatile long rxCount;
	private volatile long txCount;

	private FixedJitterBuffer rxBuffer;

	private Formats formats = new Formats();

	private Boolean shouldReceive = false;
	private Boolean shouldLoop = false;

	private HeartBeat heartBeat;
	private long lastPacketReceived;

	private RTPChannelListener rtpChannelListener;
	private PriorityQueueScheduler scheduler;
	private UdpManager udpManager;

	private boolean sendDtmf = false;

	protected RTPDataChannel(ChannelsManager channelsManager, int channelId) {
		this.channelsManager = channelsManager;
		this.jitterBufferSize = channelsManager.getJitterBufferSize();

		// open data channel
		rtpHandler = new RTPHandler();

		// create clock with RTP units
		rtpClock = new RtpClock( channelsManager.getClock());
		oobClock = new RtpClock( channelsManager.getClock());

		rxBuffer = new FixedJitterBuffer(rtpClock, jitterBufferSize);

		scheduler = channelsManager.getScheduler();
		udpManager = channelsManager.getUdpManager();
		// receiver
		input = new RTPInput(scheduler, rxBuffer);
		rxBuffer.setListener(input);

		// transmittor
		output = new RTPOutput(scheduler, this);

		heartBeat = new HeartBeat();

		formats.add(LINEAR_FORMAT);
	}


	public void setOutputFormats(Formats fmts)
			throws FormatNotSupportedException {
		output.setFormats(fmts);
	}

	public void setRtpChannelListener(RTPChannelListener rtpChannelListener) {
		this.rtpChannelListener = rtpChannelListener;
	}


	/**
	 * Binds channel to the first available port.
	 * 
	 * @throws SocketException
	 */
	public void bind(boolean isLocal) throws IOException, SocketException {
		try {
			rtpChannel = udpManager.open(rtpHandler);

			// if control enabled open rtcp channel as well
			if (channelsManager.getIsControlEnabled()) {
				rtcpChannel = udpManager.open(new RTCPHandler());
			}
		} catch (IOException e) {
			throw new SocketException(e.getMessage());
		}

		// bind data channel
		if (!isLocal) {
			logger.debug("!isLocal !!!!!!!!!!!!!!!!!!!!!!!!!");
			this.rxBuffer.setInUse(true);
			udpManager.bind(rtpChannel, PORT_ANY);
		} else {
			logger.debug("isLocal !!!!!!!!!!!!!!!!!!!!!!!!!");
			this.rxBuffer.setInUse(false);
			udpManager.bindLocal(rtpChannel, PORT_ANY);
		}
		this.rtpChannelBound = true;

		// if control enabled open rtcp channel as well
		if (channelsManager.getIsControlEnabled()) {
			if (!isLocal)
				udpManager.bind(rtcpChannel, rtpChannel.socket()
						.getLocalPort() + 1);
			else
				udpManager.bindLocal(rtcpChannel, rtpChannel.socket()
						.getLocalPort() + 1);
		}
	}

	public void bind(DatagramChannel channel) throws IOException {
		this.rxBuffer.setInUse(true);
		this.rtpChannel = channel;
		this.udpManager.open(this.rtpChannel, this.rtpHandler);
		this.rtpChannelBound = true;
	}

	public boolean isDataChannelBound() {
		return rtpChannelBound;
	}

	/**
	 * Gets the port number to which this channel is bound.
	 * 
	 * @return the port number.
	 */
	public int getLocalPort() {
		return rtpChannel != null ? rtpChannel.socket().getLocalPort() : 0;
	}

	/**
	 * Sets the address of remote peer.
	 * 
	 * @param address
	 *            the address object.
	 */
	public void setPeer(SocketAddress address) {
		this.remotePeer = address;
		boolean connectImmediately = false;
		if (rtpChannel != null) {
			if (rtpChannel.isConnected())
				try {
					rtpChannel.disconnect();
				} catch (IOException e) {
					logger.error(e);
				}

			connectImmediately = udpManager
					.connectImmediately((InetSocketAddress) address);
			if (connectImmediately)
				try {
					rtpChannel.connect(address);
				} catch (IOException e) {
					logger.info("Can not connect to remote address , please check that you are not using local address - 127.0.0.X to connect to remote");
					logger.error(e);
				}
		}

		if (udpManager.getRtpTimeout() > 0 && !connectImmediately) {
			if (shouldReceive) {
				lastPacketReceived = scheduler.getClock().getTime();
				scheduler.submitHeatbeat(heartBeat);
			} else {
				heartBeat.cancel();
			}
		}
	}

	/**
	 * Closes this socket.
	 */
	public void close() {
		if (rtpChannel != null) {
			if (rtpChannel.isConnected()) {
				try {
					rtpChannel.disconnect();
				} catch (IOException e) {
					logger.error(e);
				}
				try {
					rtpChannel.socket().close();
					rtpChannel.close();
				} catch (IOException e) {
					logger.error(e);
				}
			}
		}

		if (rtcpChannel != null) {
			rtcpChannel.socket().close();
		}

		// System.out.println("RX COUNT:" + rxCount + ",TX COUNT:" + txCount);
		rxCount = 0;
		txCount = 0;
		input.deactivate();
		output.deactivate();
		this.tx.clear();

		heartBeat.cancel();
		sendDtmf = false;
	}

	public int getPacketsLost() {
		return input.getPacketsLost();
	}

	public long getPacketsReceived() {
		return rxCount;
	}

	public long getPacketsTransmitted() {
		return txCount;
	}

	/**
	 * Modifies the map between format and RTP payload number
	 * 
	 * @param rtpFormats
	 *            the format map
	 */
	public void setFormatMap(RTPFormats rtpFormats) {

		this.rtpHandler.flush();
		this.rtpFormats = rtpFormats;
	}

	public void send(Frame frame) {
		///XXX WebRTC hack - dataChannel only available after ICE negotiation!
		if (rtpChannel != null && rtpChannel.isConnected())
			tx.perform(frame);
	}


	/**
	 * Checks whether the data channel is available for media exchange.
	 * 
	 * @return
	 */
	public boolean isAvailable() {
		// The channel is available is is connected
		boolean available = this.rtpChannel != null && this.rtpChannel.isConnected();
		// In case of WebRTC calls the DTLS handshake must be completed
		return available;
	}

	/**
	 * Implements IO operations for RTP protocol.
	 * 
	 * This class is attached to channel and when channel is ready for IO the
	 * scheduler will call either receive or send.
	 */
	private class RTPHandler implements ProtocolHandler {
		// The schedulable task for read operation
		private RxTask rx = new RxTask();

		private volatile boolean isReading = false;

		private SelectionKey selectionKey;

		public void receive(DatagramChannel channel) {
			RTPDataChannel.this.count++;
			rx.perform();
		}

		public boolean isReadable() {
			return !this.isReading;
		}

		public boolean isWriteable() {
			return true;
		}

		private void flush() {
			if (rtpChannelBound) {
				rx.flush();
			}
		}

		public void onClosed() {
			if (rtpChannelListener != null)
				rtpChannelListener.onRtpFailure();
		}

		public void send(DatagramChannel channel) {
		}

		public void setKey(SelectionKey key) {
			this.selectionKey = key;
		}
	}

	/**
	 * Implements IO operations for RTCP protocol.
	 * 
	 */
	private class RTCPHandler implements ProtocolHandler {

		public void receive(DatagramChannel channel) {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public void send(DatagramChannel channel) {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public void setKey(SelectionKey key) {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public boolean isReadable() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public boolean isWriteable() {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		public void onClosed() {

		}
	}

	/**
	 * Implements scheduled rx job.
	 * 
	 */
	private class RxTask {

		// RTP packet representation
		private RtpPacket rtpPacket = new RtpPacket( RtpPacket.RTP_PACKET_MAX_SIZE, true);
		private RTPFormat format;
		private SocketAddress currAddress;

		private RxTask() {
			super();
		}
		
		private void flush() {
			SocketAddress currAddress;
			try {
				// lets clear the receiver
				currAddress = rtpChannel.receive(rtpPacket.getBuffer());
				rtpPacket.getBuffer().clear();

				while (currAddress != null) {
					currAddress = rtpChannel.receive(rtpPacket.getBuffer());
					rtpPacket.getBuffer().clear();
				}
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		public long perform() {
				// Make sure the DTLS is completed for WebRTC calls
			perform2();

			return 0;
		}
		
		private void perform2() {
			try {
                currAddress=null;
                try {
                	currAddress = receiveRtpPacket(rtpPacket);
                	logger.debug( "Recv Messgae" + rtpPacket.toString() );
					if (currAddress != null && !rtpChannel.isConnected()) {
						rxBuffer.restart();
						rtpChannel.connect(currAddress);
					} else if (currAddress != null && rxCount == 0) {
						rxBuffer.restart();
					}
                } catch(PortUnreachableException e) {
                	try {
                		// ICMP unreachable received.
                		// Disconnect and wait for new packet.
                		rtpChannel.disconnect();
                	}
                	catch(IOException ex) {
                		logger.error(ex.getMessage(), ex);
                	}
                }
                catch (IOException e) {  
                	logger.error(e.getMessage(), e);                	
                }
                                	
				while (currAddress != null) {
					lastPacketReceived = scheduler.getClock().getTime();

					if (rtpPacket.getVersion() != 0 && (shouldReceive || shouldLoop)) {
						// RTP v0 packets is used in some application.
						// Discarding since we do not handle them
						// Queue packet into the receiver jitter buffer
						if (rtpPacket.getBuffer().limit() > 0) {
							if (shouldLoop && rtpChannel.isConnected()) {
								sendRtpPacket(rtpPacket);
								rxCount++;
								txCount++;
							} else if (!shouldLoop) {
								format = rtpFormats.find(rtpPacket.getPayloadType());
								rxBuffer.write(rtpPacket, format);
								rxCount++;
							}
						}
					}
					currAddress = receiveRtpPacket(rtpPacket);
                }
            }
        	catch(PortUnreachableException e) {
            	// ICMP unreachable received
            	// Disconnect and wait for new packet
            	try {
            		rtpChannel.disconnect();
            	} catch(IOException ex) {
            		logger.error(ex.getMessage(), ex);            		
            	}
            } catch (Exception e) {
            	logger.error(e.getMessage(), e);            	
            }
            rtpHandler.isReading = false;
		}
	}

	/**
	 * Writer job.
	 */
	private class TxTask {
		private RtpPacket rtpPacket = new RtpPacket(
                RtpPacket.RTP_PACKET_MAX_SIZE, true);
		private RtpPacket oobPacket = new RtpPacket(
                RtpPacket.RTP_PACKET_MAX_SIZE, true);
		private RTPFormat fmt;
		private long timestamp = -1;
		private long dtmfTimestamp = -1;

		private TxTask() {
		}

		/**
		 * if connection is reused fmt could point to old codec , which in case
		 * will be incorrect
		 * 
		 */
		public void clear() {
			this.timestamp = -1;
			this.dtmfTimestamp = -1;
			this.fmt = null;
		}

		public void perform(Frame frame) {
			// discard frame if format is unknown
			if (frame.getFormat() == null) {
				frame.recycle();
				return;
			}

			// if current rtp format is unknown determine it
			if (fmt == null || !fmt.getFormat().matches(frame.getFormat())) {
				fmt = rtpFormats.getRTPFormat(frame.getFormat());
				// format still unknown? discard packet
				if (fmt == null) {
					frame.recycle();
					return;
				}
				// update clock rate
				rtpClock.setClockRate(fmt.getClockRate());
			}

			// ignore frames with duplicate timestamp
			if (frame.getTimestamp() / 1000000L == timestamp) {
				frame.recycle();
				return;
			}

			// convert to milliseconds first
			timestamp = frame.getTimestamp() / 1000000L;

			// convert to rtp time units
			timestamp = rtpClock.convertToRtpTime(timestamp);
			rtpPacket.wrap(false, fmt.getID(), sn++, timestamp, ssrc,
					frame.getData(), frame.getOffset(), frame.getLength());

			frame.recycle();
			try {
				if (rtpChannel.isConnected()) {
					sendRtpPacket(rtpPacket);
					txCount++;
				}
			} catch (PortUnreachableException e) {
				// icmp unreachable received
				// disconnect and wait for new packet
				try {
					rtpChannel.disconnect();
				} catch (IOException ex) {
					logger.error(ex);
				}
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	private class HeartBeat extends Task {

		public HeartBeat() {
			super();
		}

		public int getQueueNumber() {
			return PriorityQueueScheduler.HEARTBEAT_QUEUE;
		}

		@Override
		public long perform() {
			if (scheduler.getClock().getTime() - lastPacketReceived > udpManager.getRtpTimeout() * 1000000000L) {
				if (rtpChannelListener != null)
					rtpChannelListener.onRtpFailure();
			} else {
				scheduler.submitHeatbeat(this);
			}
			return 0;
		}
	}


	private SocketAddress receiveRtpPacket(RtpPacket packet) throws IOException {
		SocketAddress address = null;
		
		// WebRTC handler can return null if packet is not valid
		if(packet != null) {
			// Clear the buffer for a fresh read
			ByteBuffer buf = packet.getBuffer();
			buf.clear();
			
			// receive RTP packet from the network
			address = rtpChannel.receive(buf);
			
			// put the pointer at the beginning of the buffer 
			buf.flip();
		}
		return address;
	}

	private void sendRtpPacket(RtpPacket packet) throws IOException {
		// Do not send data while DTLS handshake is ongoing. WebRTC calls only.
		// SRTP handler returns null if an error occurs
		if(packet != null) {
			// Rewind buffer
			ByteBuffer buf = packet.getBuffer();
			buf.rewind();
			
			// send RTP packet to the network
			rtpChannel.send(buf, rtpChannel.socket().getRemoteSocketAddress());
		}
	}
	
	public String getExternalAddress() {
		return this.udpManager.getExternalAddress();
	}
}
