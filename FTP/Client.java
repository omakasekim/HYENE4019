package FTP;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

public class Client {


    private Socket socket;
    private String ipAddress;
    private int serverPort;
    private int dataPort;
    private InputStream inputStream;
    private OutputStream outputStream;

    //for data
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private OutputStream dataOutputStream;
    private InputStream dataInputStream;


    public Client() {
        this("127.0.0.1",2020, 2021);
    }

    public Client(String serverIP, int serverPort, int dataPort) {
        this.ipAddress = serverIP;
        this.serverPort = serverPort;
        this.dataPort = dataPort;

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

    public void data() {
        send(makeCommand("TEST","".getBytes()));
        openDataConnection();
        int test = 0;
        try {
            test = dataInputStream.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(test);
    }
    private void openDataConnection() {
        while(true){
            try
            {
                dataConnection = new Socket(ipAddress, dataPort);
                if(dataConnection != null)break;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            this.dataInputStream = dataConnection.getInputStream();
            this.dataOutputStream = dataConnection.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void closeDataConnection() {
        try {
            dataInputStream.close();
            dataOutputStream.close();
            dataConnection.close();
            //dataSocket.close();

            if(dataSocket != null) {
                dataSocket.close();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void quitConnection() {
        byte[] cmd = makeCommand("QUIT", "".getBytes());
        send(cmd);
    }

    public void Put(String file) {
        File f = new File(file);
        if (!f.exists()) {
            System.out.println("File not found");
            return;
        }

        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(f);

        } catch (Exception e) {
        }

        byte[] out = new byte[(int)f.length()];
        try {
            fileInputStream.read(out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteBuffer bf = ByteBuffer.allocate(255 + (int)f.length());
        bf.put(Arrays.copyOf(f.getName().getBytes(), 255));
        bf.put(out);

        byte[] cmd = makeCommand("PUT", bf.array());

        send(cmd);

        System.out.printf("%s transferred / %d bytes\n", f.getName(), f.length());
    }

    public void Get(String file) {
        send(makeCommand("GET", file.getBytes()));

        byte[] received;
        try {
            received = read();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    //TODO from here
        System.out.printf("Received %s / %d bytes\n", file, received.length-255);
        for (int i = 0; i < ((received.length-255)/1000)+1; i++) {
            System.out.print('#');
        }
        System.out.println("\t Completed...");
        //TODO to here

        FileOutputStream fileOutputStream;
        File f = new File(file);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try {
            fileOutputStream = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            // WTF?
            return;
        }

        try {
            fileOutputStream.write(Arrays.copyOfRange(received,255, received.length));
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        //if(!statusCode.contentEquals("200")) return null;

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


    //TODO: End my life
    public void dataPut(String file) {
        File f = new File(file);
        if (!f.exists()) {
            System.out.println("File not found");
            return;
        }
        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(f);

        } catch (Exception e) {
        }

        byte[] out = new byte[(int)f.length()];
        try {
            fileInputStream.read(out);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ByteBuffer bf = ByteBuffer.allocate(255 + (int)f.length());
        bf.put(Arrays.copyOf(f.getName().getBytes(), 255));
        bf.put(out);

        byte[] cmd = makeCommand("DPUT", bf.array());
        send(cmd);
        while(true){
            openDataConnection();
            if (dataConnection!=null)break;
        }
        byte[] received;
        try {
            received = read();
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        if(received!=null){
            try {
                System.out.printf("%s transferred / %d bytes\n", f.getName(), f.length());
                dataWriteToServer(out, 0, "0000", (int)f.length());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            System.out.println("Failed for unknown reason");
            closeDataConnection();
            return;
        }

    }

    public void dataGet(String file) {
        send(makeCommand("GET", file.getBytes()));

        byte[] received;
        try {
            received = read();
            while(true){
                openDataConnection();
                if (dataConnection!=null)break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        byte[] dataResp = null;
        if(received.length>0) {
            try {
                //dataResp = new byte[received.length];
                dataResp = new byte[(received.length+12)*((received.length/1000)+1)];
                dataResp = dataRead();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            System.out.println("Failed – Such file does not exist!");
            closeDataConnection();
            return;
        }
        System.out.printf("Received %s / %d bytes\n", file, received.length);

        FileOutputStream fileOutputStream;
        File f = new File(file);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try {
            fileOutputStream = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            return;
        }

        try {
            //TODO
            fileOutputStream.write(Arrays.copyOfRange(dataResp,0, dataResp.length));
        } catch (IOException e) {
            e.printStackTrace();
        }
        closeDataConnection();
    }

    private int dataSend(OutputStream os, byte[] buffer, byte[] SeqNo, byte[] CHKsum, byte[] Size) {
        ByteBuffer bf = ByteBuffer.allocate(12+buffer.length);

        bf.put(Arrays.copyOf(SeqNo, 4));
        bf.put(Arrays.copyOf(CHKsum, 4));
        bf.put(Arrays.copyOf(Size, 4));
        bf.put(buffer);

        try {
            dataOutputStream.write(bf.array());
            dataOutputStream.flush();
        }
        catch (IOException e) {
            return -1;
        }

        return buffer.length;
    }
    private int dataWriteToServer(byte[] buffer, int SeqNo, String CHKsum, int Size) {
        ByteBuffer SequenceNumber = ByteBuffer.allocate(4);
        String SQN = Integer.toString(SeqNo);
        SequenceNumber.put(Arrays.copyOf(SQN.getBytes(), 4));


        ByteBuffer CheckSum = ByteBuffer.allocate(4);
        CheckSum.put(Arrays.copyOf(CHKsum.getBytes(), 4));

        ByteBuffer fileSize = ByteBuffer.allocate(4);
        String FSIZE = Integer.toString(Size);
        fileSize.put(Arrays.copyOf(FSIZE.getBytes(), 4));
/*
        byte[] CheckSum = new byte[4];
        CheckSum = CHKsum.getBytes();

        byte[] fileSize = new byte[4];
        String fsize = Integer.toString(Size);
        fileSize = fsize.getBytes();
*/
        int chunkNum =(Size/1000)+1;
        int chunkcount = 0;
        int iterativeRemainder = Size;

        while(chunkNum > chunkcount){
            if(iterativeRemainder > 1000) {
                iterativeRemainder = iterativeRemainder - (chunkNum * 1000);
                dataSend(dataOutputStream, Arrays.copyOfRange(buffer, chunkcount * 1000, (chunkcount + 1) * 1000), SequenceNumber.array(), CheckSum.array(), fileSize.array());
            }
            else dataSend(dataOutputStream, Arrays.copyOfRange(buffer, chunkcount * 1000, (chunkcount * 1000)+iterativeRemainder), SequenceNumber.array(), CheckSum.array(), fileSize.array());
            chunkcount++;
        }

        while(chunkNum > chunkcount){
            if(iterativeRemainder > 1012) {
                iterativeRemainder = iterativeRemainder - (chunkNum * 1012);
                System.out.print('#');
            }
            System.out.print('#');
            chunkcount++;
        }
        System.out.println("\tCompleted...");
        closeDataConnection();
        return 0;
    }
    private byte[] dataRead() throws Exception {
        byte[] header = new byte[12];
        try {
            dataInputStream.read(header, 0, 12);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int SeqNo = Integer.parseInt(new String(Arrays.copyOfRange(header, 0, 4)).trim());
        String CHKsum = new String(Arrays.copyOfRange(header, 4, 8)).trim();
        int Size = Integer.parseInt(new String(Arrays.copyOfRange(header, 8, 12)).trim());

        //if(!CHKsum.contentEquals("0000")) return null;


        int chunkNum = (int)Math.ceil(Size/1000)+1;
        int chunkcount = 0;
        int iterativeRemainder = Size;
        byte[] buffer = new byte[chunkNum*(12+Size)];

        dataInputStream.read(buffer,0, Size);

        while(chunkNum > chunkcount){
            if(iterativeRemainder > 1012) {
                iterativeRemainder = iterativeRemainder - (chunkNum * 1012);
                System.out.print('#');
            }
            System.out.print('#');
            chunkcount++;
        }
        System.out.println("\tCompleted...");
        /*
        try {
            while(chunkNum > chunkcount){
                if(iterativeRemainder > 1012) {
                    iterativeRemainder = iterativeRemainder - (chunkNum * 1012);
                    dataInputStream.read(buffer, chunkcount*1000+12, 1012);
                    System.out.print('#');
                }
                dataInputStream.read(buffer, ((chunkcount*1000)+12), ((chunkcount*1000)+12)+Size);
                System.out.print('#');
                chunkcount++;
            }
            System.out.println("\tCompleted...");
        } catch (IOException e){
            e.printStackTrace();
        }
    */
        return buffer;
    }


}
