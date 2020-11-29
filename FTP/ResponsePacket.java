package FTP;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ResponsePacket {

    public static final int maxChunkSize = 3;

    protected byte seqNo;
    protected short chkSum;


    public ResponsePacket(byte seqNo) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
    }

    public ResponsePacket(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        this.seqNo = wrapped.get();
        this.chkSum = wrapped.getShort();
    }

    public void writeBytes(DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeByte(seqNo);
        dataOutputStream.writeShort(chkSum);
    }

    public boolean calcCHK() {
        return chkSum != 0x000;
    }

}
