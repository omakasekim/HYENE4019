package FTP;


import javax.xml.crypto.Data;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.*;

public class SelectiveRepeatSender extends RDTSender {

    private PriorityQueue<DataPacket> priorityQueue;
    private Set<Byte> receivedAckFor;
    private Map<Byte, DataPacket> map;


    public SelectiveRepeatSender(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream, DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws FileNotFoundException {
        super(fileInputStream, bufferedInputStream, dataInputStream, dataOutputStream);
        this.priorityQueue = new PriorityQueue<>();
        this.receivedAckFor = new HashSet<>();
        this.map = new HashMap<>();
    }

    public void sendPackets(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws IOException {
        while (map.size() < WINDOW_SIZE && !isEOT) {
            int dataLen = bufferedInputStream.read(senderBuffer);
            if (dataLen < 1) {
                bufferedInputStream.close();
                fileInputStream.close();
                isEOT = true;
                break;
            }
            lastSentSeqNo = (byte) incrementSeqNumber(lastSentSeqNo);
            DataPacket packet = new DataPacket(lastSentSeqNo, senderBuffer);
            priorityQueue.offer(packet);
            map.put(packet.getSeqNo(), packet);
            packet.writeBytes(dataOutputStream);
            sendAttempts++;

        }
    }

    public void receiveAck(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws IOException {
        if (priorityQueue.isEmpty()) return;
        // Check if the timer of the first packet has run out. If so throw an exception
        int time = 1;//(int) (1 - System.currentTimeMillis());
        if (time <= 0) {
            throw new SocketTimeoutException("PACKET TIMED OUT");
        }
        // set the waiting time such that receive() waits only until the time a packet times out
        byte[] AckPack = new byte[3];
        dataInputStream.read(AckPack);
        //change to response packet
        DataPacket packet = new DataPacket(AckPack);
        if (isSeqNumInWindow(baseSeqNo, packet.getSeqNo())) {
            receivedAckFor.add(packet.getSeqNo());
            priorityQueue.remove(map.get(packet.getSeqNo()));
        }
        while (receivedAckFor.contains(baseSeqNo)) {
            receivedAckFor.remove(baseSeqNo);
            map.remove(baseSeqNo);
            ackSeqNo = baseSeqNo;
            baseSeqNo = (byte) incrementSeqNumber(baseSeqNo);
        }
    }

    public void reTransmit(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws IOException {
        while(!priorityQueue.isEmpty() && 1 < System.currentTimeMillis()) {
            DataPacket packet = priorityQueue.poll();
            priorityQueue.offer(packet);
            packet.writeBytes(dataOutputStream);
            sendAttempts++;
        }
    }
}
