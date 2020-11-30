package FTP;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ResponsePacket implements Comparable<ResponsePacket> {

    public static final int maxChunkSize = 3;

    protected byte seqNo;
    protected short chkSum;


    public ResponsePacket(byte seqNo) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
    }

    public ResponsePacket(byte seqNo, byte chkSum) {
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

    public byte getSeqNo(){ return seqNo; }
    public boolean calcCHK() {
        return chkSum != 0x000;
    }

    @Override
    public int compareTo(ResponsePacket comparePacket) {
        if(this.getSeqNo() - comparePacket.getSeqNo() < 0) return -1;
        else if(this.getSeqNo() - comparePacket.getSeqNo() > 0) return 1;
        else return 0;
    }

}
