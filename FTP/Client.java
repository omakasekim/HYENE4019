package FTP;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Client {


    private Socket socket;
    private String ipAddress;
    private int serverPort;
    private int dataPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String curDir;

    //Selective Repeat
    protected  final int senderTimeout = 1;
    protected ArrayList<Integer> srDropList;
    protected ArrayList<Integer> srTimeoutList;
    protected ArrayList<Integer> srBitErrList;


    public Client() {
        this("127.0.0.1",2020, 2021);
    }

    public Client(String serverIP, int serverPort, int dataPort) {
        this.ipAddress = serverIP;
        this.serverPort = serverPort;
        this.dataPort = dataPort;
        this.curDir =  System.getProperty("user.dir");

        try {
            socket = new Socket(this.ipAddress, this.serverPort);
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void List(String path) {
        byte[] cmd = makeCommand("LIST", path.getBytes());
        send(cmd);

        byte[] received = null;
        try {
            received = read();
        }catch (Exception e) {
            System.out.printf("Failed - directory name is invalid");
            return;
        }
        if(received == null)
            System.out.println("Failed - directory name is invalid");
        else
            System.out.print(new String(received));
    }

    public void quitConnection() {
        byte[] cmd = makeCommand("QUIT", "".getBytes());
        send(cmd);
    }

    public void Put(String file) throws Exception {
        File f = new File(Paths.get(this.curDir, file).toString());
        if (!f.exists()) {
            System.out.println("File not found");
            return;
        }

        FileInputStream fileInputStream = new FileInputStream(f);
        byte[] out = new byte[(int)f.length()];
        fileInputStream.read(out);
        ByteBuffer bf = ByteBuffer.allocate(255 + (int)f.length());
        bf.put(Arrays.copyOf(f.getName().getBytes(), 255));
        bf.put(out);
        send(makeCommand("PUT", bf.array()));
        fileInputStream.close();
        byte[] received = read();

        Socket dataSocket = new Socket(ipAddress, dataPort);
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        fileInputStream = new FileInputStream(f);

        System.out.print(f.getName() + " transferred\t/");
        System.out.print(f.length()+ " bytes");

        for (byte seqNo = 0; ; seqNo++) {
            byte[] data = fileInputStream.readNBytes(DataPacket.maxDataSize);
            System.out.print("#");
            if (data.length == 0) break;
            DataPacket chunk = new DataPacket(seqNo, (short)data.length, data);
            chunk.writeBytes(dataOutputStream);
        }

        System.out.println("\tCompleted...");
        dataSocket.close();
        dataOutputStream.close();
        fileInputStream.close();
    }

    public void handlePut(String file) throws Exception {
        File f = new File(Paths.get(this.curDir, file).toString());
        if (!f.exists()) {
            System.out.println("File not found");
            return;
        }

        FileInputStream fileInputStream = new FileInputStream(f);
        byte[] out = new byte[(int)f.length()];
        fileInputStream.read(out);
        ByteBuffer bf = ByteBuffer.allocate(255 + (int)f.length());
        bf.put(Arrays.copyOf(f.getName().getBytes(), 255));
        bf.put(out);
        send(makeCommand("PUT", bf.array()));
        fileInputStream.close();
        byte[] received = read();

        Socket dataSocket = new Socket(ipAddress, dataPort);
        BufferedReader dataInputStream = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        fileInputStream = new FileInputStream(f);
        DataPacket[] window = new DataPacket[DataPacket.maxWinSize];
        Timer[] timers = new Timer[DataPacket.maxWinSize];
        int chunks = (int)(f.length() + DataPacket.maxDataSize -1) / DataPacket.maxDataSize;
        int seqBase = 0;
        int inWindow = 0;
        byte baseSeqNo = 0;
        byte nextInLine = 0;

        try {
            while(chunks > 0) {
                Boolean sw = false;
                while(inWindow < DataPacket.maxWinSize && inWindow < chunks) {
                    if(!sw) {
                        System.out.print("Transmitted : ");
                        sw = true;
                    }
                    int idx = (seqBase + inWindow) % DataPacket.maxWinSize;
                    byte[] data = fileInputStream.readNBytes(DataPacket.maxDataSize);
                    window[idx] = new DataPacket(nextInLine, data);
                    if(nextInLine != 2) {
                        synchronized (dataOutputStream) {
                            window[idx].writeBytes(dataOutputStream);
                        }
                    }
                    System.out.println(nextInLine + " ");
                    timers[idx] = new Timer();
                    timers[idx].scheduleAtFixedRate(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        synchronized (dataOutputStream) {
                                            System.out.println(window[idx].getSeqNo() + "Timed out, retransmitting");
                                            window[idx].writeBytes(dataOutputStream);
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            },
                            senderTimeout * 1000, senderTimeout * 1000
                    );
                    inWindow++;
                    nextInLine = (byte) ((nextInLine + 1)*(DataPacket.maxSeqNo + 1));
                }
                if(sw) System.out.println();
                int localAcked = Integer.parseInt(dataInputStream.readLine());
                System.out.println(localAcked + " Acked");
                int logicalAcked=localAcked-baseSeqNo+((localAcked-baseSeqNo<-DataPacket.maxWinSize)?(DataPacket.maxSeqNo+1):0);
                if(0 <= logicalAcked && logicalAcked < inWindow) {
                    int idx = (seqBase + logicalAcked) % DataPacket.maxWinSize;
                    window[idx] = null;
                    timers[idx].cancel();
                    timers[idx] = null;
                    while(window[seqBase] == null && inWindow > 0) {
                        baseSeqNo = (byte) ((baseSeqNo + 1) % (DataPacket.maxSeqNo + 1));
                        seqBase = (seqBase+1)%DataPacket.maxWinSize;
                        inWindow--;
                        chunks--;
                    }
                }
            }
        } finally {
            fileInputStream.close();
            dataOutputStream.close();
            dataInputStream.close();
            dataSocket.close();
        }
        return;
    }

    public void Get(String file) throws IOException {
        send(makeCommand("GET", file.getBytes()));

        byte[] received;
        try {
            received = read();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        File f = new File(file);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        int len = Integer.parseInt(new String(received));
        System.out.printf("Received %s / %d bytes\n", file, len);

        Socket dataSocket = new Socket(ipAddress, dataPort);
        DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
        FileOutputStream fileOutputStream = new FileOutputStream(f);


        for (byte seqNo = 0; seqNo * DataPacket.maxDataSize < len; seqNo++) {
            byte[] bytes = dataInputStream.readNBytes(DataPacket.maxChunkSize);
            System.out.print("#");
            DataPacket chunk = new DataPacket(bytes);
            fileOutputStream.write(chunk.data);
        }

        System.out.println("\t Completed...");
        dataSocket.close();
        dataInputStream.close();
        fileOutputStream.close();

    }

    public void CD(String path) {
        send(makeCommand("CD", path.getBytes()));
        byte[] result;
        try {
            result = read();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if(result == null)
            System.out.println("Failed - directory name is invalid");
        else
            System.out.println(new String(result));
        // System.out.println(new String(result));
    }

    private int send(byte[] buffer) {
        try {
            outputStream.write(buffer);
            outputStream.flush();
        } catch (IOException e) {
            return -1;
        }

        return buffer.length;
    }

    private byte[] read() throws Exception {
        byte[] header = new byte[12];
        try {
            inputStream.read(header, 0, 12);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String type = new String(Arrays.copyOfRange(header, 0, 4)).trim();
        String statusCode = new String(Arrays.copyOfRange(header, 4, 8)).trim();
        int dataLen = Integer.parseInt(new String(Arrays.copyOfRange(header, 8, 12)).trim());

        if(!statusCode.contentEquals("200")) return null;

        byte[] buffer = new byte[dataLen];
        try {
            inputStream.read(buffer, 0, dataLen);
        } catch (IOException e){
            e.printStackTrace();
        }

        return buffer;
    }

    private byte[] makeCommand(String command, byte[] arg) {
        ByteBuffer bf = ByteBuffer.allocate(8 + arg.length);

        byte[] cmd = Arrays.copyOf(command.getBytes(), 4);
        bf.put(cmd);

        byte[] argSize =  Arrays.copyOf(Integer.toString(arg.length).getBytes(),4);
        bf.put(argSize);

        bf.put(arg);


        return bf.array();
    }

    private int readToBuf(byte[] dst) {
        int result;

        try {
            result = inputStream.read(dst);

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        return result;
    }



}
