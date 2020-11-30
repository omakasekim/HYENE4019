package FTP;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;


public class Receiver extends Host {

    private FileOutputStream fileOutputStream;
    private BufferedOutputStream bufferedOutputStream;
    private byte[] senderBuffer;
    private Map<Byte, DataPacket> recievedPackets;
    private byte baseSeqNum;
    protected DataOutputStream dataOutputStream;
    protected DataInputStream dataInputStream;
    protected ServerSocket socket;
    protected Socket dataSocket;
    protected int length;

    public Receiver(FileOutputStream fileOutputStream, BufferedOutputStream bufferedOutputStream, DataInputStream dataInputStream, DataOutputStream dataOutputStream, int filelen) throws IOException {
        this.fileOutputStream = fileOutputStream;
        this.bufferedOutputStream = bufferedOutputStream;
        this.dataInputStream = dataInputStream;
        this.dataOutputStream = dataOutputStream;
        this.senderBuffer = new byte[BUFFER_SIZE];
        this.recievedPackets = new HashMap<>();
        this.baseSeqNum = 0;
        this.length = filelen;
    }


    public void startUDPConnection() throws IOException {
        while (true) {
            dataInputStream.read(senderBuffer);
            System.out.println("WAITING FOR PACKET...");
            DataPacket packet = new DataPacket(senderBuffer);
            if (packet.getSeqNo() == length) {
                selectiveRepeat(packet, socket);
                ResponsePacket resppacket = new ResponsePacket(packet.getSeqNo(), packet.getChkSum());
                resppacket.writeBytes(dataOutputStream);
                break;
            }
            else selectiveRepeat(packet, socket);
        }
        dataSocket.close();
        bufferedOutputStream.close();
        fileOutputStream.close();
    }


    public void selectiveRepeat(DataPacket packet, ServerSocket socket) throws IOException {
        if (isSeqNumInWindow(baseSeqNum, packet.getSeqNo())) {
            if (!recievedPackets.containsKey(packet.getSeqNo())) {
                recievedPackets.put(packet.getSeqNo(), packet);
            }
        }
        if (isSeqNumInCurrOrPrevWindow(baseSeqNum, packet.getSeqNo())) {

            sendAckPacket(packet.getSeqNo(),packet.getChkSum());
        }
        // if packets are in order write them to the file and increment base sequence number
        while (recievedPackets.containsKey(baseSeqNum)) {
            System.out.print(packet.getSeqNo()+" ");
            bufferedOutputStream.write(recievedPackets.get(baseSeqNum).getPayload());
            recievedPackets.remove(baseSeqNum);
            baseSeqNum = (byte) incrementSeqNumber(baseSeqNum);
        }
    }

    public void sendAckPacket(byte seqNo, short ChkSum) throws IOException {
        ResponsePacket packet = new ResponsePacket(seqNo, ChkSum);
        packet.writeBytes(dataOutputStream);

    }

}
