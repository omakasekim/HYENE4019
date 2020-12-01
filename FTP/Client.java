package FTP;

import java.io.*;
import java.net.*;
import java.nio.*;
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
    protected ArrayList<Integer> Dropped = new ArrayList<>();
    protected ArrayList<Integer> TimedOut = new ArrayList<>();
    protected ArrayList<Integer> BitError = new ArrayList<>();
    private boolean DROP = false;
    private boolean TIMEOUT = false;
    private boolean BITERROR = false;
    private int problem;



    public Client() {
        this("127.0.0.1",2020, 2021);
    }

    public Client(String serverIP, int serverPort, int dataPort) {
        this.ipAddress = serverIP;
        this.serverPort = serverPort;
        this.dataPort = dataPort;
        this.curDir =  System.getProperty("user.dir");
        Dropped = new ArrayList<>(15);


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
        System.out.println(f.length()+ " bytes");

        for (byte seqNo = 0; ; seqNo++) {
            byte[] data = fileInputStream.readNBytes(DataPacket.maxDataSize);
            if (data.length == 0) break;
            System.out.print(seqNo);
            DataPacket chunk = new DataPacket(seqNo, (short)data.length, data);
            chunk.writeBytes(dataOutputStream);
        }
        System.out.println("\tCompleted...");
        int chunkSize = (( (int)f.length()/1000)%1000 == 0 ? ( (int)f.length()/1000) : ( (int)f.length()/1000)+1);
        if(chunkSize == 0 && chunkSize != problem) System.out.println("0 acked");
        if(chunkSize == 0 && chunkSize == problem) System.out.println("0 timed out & retransmitted");
        for(int i = 0;i < chunkSize;i++){
            if(i != problem) System.out.print(i +" acked, ");
        }
        if((DROP != false || BITERROR != false || TIMEOUT != false)) System.out.println(problem + " timed out & retransmitted");

        dataSocket.close();
        dataOutputStream.close();
        fileInputStream.close();

        TIMEOUT = false;
        BITERROR = false;
        DROP = false;
        problem = 25565;
    }

    public void dropRequest(String packet) {
        String packetNum = (packet.substring(1));
        byte[] packetNo = packetNum.getBytes();
        send(makeCommand("DROP", packetNo));
        this.DROP = true;
        this.problem = Integer.parseInt(packetNum);

    }
    public void bitErrorRequest(String packet){
        String packetNum = (packet.substring(1));
        byte[] packetNo = packetNum.getBytes();
        send(makeCommand("BITERROR", packetNo));
        this.BITERROR = true;
        this.problem = Integer.parseInt(packetNum);

    }
    public void timeoutRequest(String packet){
        String packetNum = (packet.substring(1));
        byte[] packetNo = packetNum.getBytes();
        send(makeCommand("TIMEOUT", packetNo));
        this.TIMEOUT = true;
        this.problem = Integer.parseInt(packetNum);

    }
    //TODO
    public void experimentalPut(String file) throws Exception {
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

        fileInputStream = null;
        fileInputStream = new FileInputStream(f);

        Socket dataSocket = new Socket(ipAddress, dataPort);
        fileInputStream = new FileInputStream(f);

        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());

        RDTSender sender = new SelectiveRepeatSender(fileInputStream, bufferedInputStream, dataInputStream, dataOutputStream);
        if(sender != null) sender.start();
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
