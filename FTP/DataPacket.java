package FTP;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DataPacket implements Comparable<DataPacket> {

    public static final int headerSize = 5;
    public static final int maxDataSize = 1000;
    public static final int maxChunkSize = maxDataSize + 5;

    protected byte seqNo;
    protected short chkSum;
    protected short size;
    public byte[] data;

    //Parameter
    public static final int maxSeqNo = 15;
    public static final int maxWinSize = 5;

    public DataPacket(byte seqNo, byte[] data) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
        this.size = (short) data.length;
        this.data = data;
    }
    public DataPacket(byte seqNo, short size, byte[] data) {
        this.seqNo = seqNo;
        this.chkSum = 0x0000;
        this.size = size;
        this.data = data;
    }

    public DataPacket(byte seqNo, short size, byte chkSum, byte[] data) {
        this.seqNo = seqNo;
        this.size = size;
        this.chkSum = chkSum;
        this.data = data;
    }

    public DataPacket(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        this.seqNo = wrapped.get();
        this.chkSum = wrapped.getShort();
        this.size = wrapped.getShort();
        this.data = new byte[wrapped.remaining()];
        wrapped.get(this.data);
    }

    public void writeBytes(DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeByte(seqNo);
        dataOutputStream.writeShort(chkSum);
        dataOutputStream.writeShort(size);
        dataOutputStream.write(data,0, Math.min(data.length, maxDataSize));
    }
    public byte getSeqNo(){
        return seqNo;
    }
    public short getSize() { return size; }
    public byte[] getPayload() {  return data; }
    public boolean calcCHK() {
        return chkSum != 0x0000;
    }
    public void setPayload(byte[] payload) {
        this.data = payload;
    }
    @Override
    public int compareTo(DataPacket comparePacket) {
        if(this.getSeqNo() - comparePacket.getSeqNo() < 0) return -1;
        else if(this.getSeqNo() - comparePacket.getSeqNo() > 0) return 1;
        else return 0;
    }

}
