package Audio;

import java.nio.ByteBuffer;

public class AMRWB {

    private static final int[] AMR_FRAME_SIZE = new int[] {
            17, 23, 32, 36, 40, 46, 50, 58, 60
    };

    private static final byte[] AMR_HEADER = { 0x23, 0x21, 0x41, 0x4D, 0x52, 0x2D, 0x57, 0x42, 0x0A };

    public static final int CMF_SIZE = 1;
    public static final int FT_POS = 1;
    public static final int AUDIO_POS = 2;
    public static final int AMRWB_PAYLOAD_TYPE = 100;

    private ByteBuffer buffer;

    public AMRWB(int capacity, boolean allocateDirect) {
        this.buffer = allocateDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }


    public long getFrameType() {
        return ((buffer.get(FT_POS) & 0xff) >> 3) & 0x0f;
    }

    public byte[] getAmrData() {
        byte[] data = new byte[AMR_FRAME_SIZE[(int) this.getFrameType()]];
        buffer.position(FT_POS);
        buffer.get(data, 0, AMR_FRAME_SIZE[(int) this.getFrameType()]);
        return data;
    }
    public int getAmrDataLength() {
        return FT_POS + AMR_FRAME_SIZE[(int) this.getFrameType()];
    }

    @Override
    public String toString() {
        return "AUDIO Packet[FrameType=" + getFrameType() + "]" + "AMR Data : [" + byteArrayToHex(this.getAmrData()) + "]";
    }

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for(final byte b: a)
            sb.append(String.format("%02x ", b&0xff));
        return sb.toString();
    }

}
