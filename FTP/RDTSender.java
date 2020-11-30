package FTP;


import javax.xml.crypto.Data;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;


public abstract class RDTSender extends Host {

    protected String ipAddress;
    protected int dataPort;
    protected byte baseSeqNo;
    protected byte ackSeqNo;
    protected byte lastSentSeqNo;
    protected byte[] receiverBuffer;
    protected byte[] senderBuffer;
    protected int timeout;
    protected boolean isEOT;
    protected int sendAttempts;
    protected FileInputStream fileInputStream;
    protected BufferedInputStream bufferedInputStream;
    protected DataOutputStream dataOutputStream;
    protected DataInputStream dataInputStream;
    protected ServerSocket socket;
    protected Socket dataSocket;

    public RDTSender(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws FileNotFoundException {
        this.fileInputStream = fileInputStream;
        this.bufferedInputStream = bufferedInputStream;
        this.dataInputStream = dataInputStream;
        this.dataOutputStream = dataOutputStream;
        this.isEOT = false;
        this.sendAttempts = 0;
        this.receiverBuffer = new byte[BUFFER_SIZE];
        this.senderBuffer = new byte[PAYLOAD_SIZE];
        this.timeout = 1;
    }


    protected void sendPackets(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws IOException {
    }
    protected void receiveAck(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws IOException {
    }
    protected void reTransmit(FileInputStream fileInputStream, BufferedInputStream bufferedInputStream,DataInputStream dataInputStream,DataOutputStream dataOutputStream) throws IOException {
    }

    public void start() throws IOException {
        while (!(lastSentSeqNo == ackSeqNo && isEOT)) {
            try {
                sendPackets(fileInputStream, bufferedInputStream, dataInputStream, dataOutputStream);
                receiveAck(fileInputStream, bufferedInputStream, dataInputStream, dataOutputStream);
            } catch (SocketTimeoutException e) {
                reTransmit(fileInputStream, bufferedInputStream, dataInputStream, dataOutputStream);
            }
        }
        socket.close();
    }
}

