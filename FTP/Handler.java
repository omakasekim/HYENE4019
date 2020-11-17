package FTP;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.NotDirectoryException;
import java.nio.file.Paths;
import java.util.Arrays;



public class Handler implements Runnable {
    private String curDir;
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private String ipAddress;

    //These are Objects for GET and PUT
    private int dataPort;
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private OutputStream dataOutputStream;
    private InputStream dataInputStream;



    public Handler(Socket socket, int dataPort, String ipAddress) {
        this.socket = socket;
        this.dataPort = dataPort;
        this.ipAddress = ipAddress;
        this.curDir = new File(".").getAbsolutePath();

        try {
            this.outputStream = socket.getOutputStream();
            this.inputStream = socket.getInputStream();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while(true) {
            if (parseCmd()<0) {
                return;
            }
        }
    }

    private int write(OutputStream os, byte[] buffer, String statusCode, String type) {
        ByteBuffer bf = ByteBuffer.allocate(12+buffer.length);

        bf.put(Arrays.copyOf(type.getBytes(), 4));
        bf.put(Arrays.copyOf(statusCode.getBytes(), 4));
        bf.put(Arrays.copyOf(String.valueOf(buffer.length).getBytes(), 4));
        bf.put(buffer);

        try {
            os.write(bf.array());
            os.flush();
        }
        catch (IOException e) {
            return -1;
        }

        return buffer.length;
    }

    private int writeToClient(byte[] buffer, String statusCode, String type) {
        write(outputStream, buffer, statusCode, type);

        return 0;
    }

    private int writeToClient(String buffer, String statusCode, String type) {
        write(outputStream, buffer.getBytes(), statusCode, type);

        return 0;
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

    private int DreadToBuf(byte[] dst) {
        int result;

        try {
            result = dataInputStream.read(dst);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        return result;
    }

    private int parseCmd() {
        byte[] header = new byte[8];
        try {
            inputStream.read(header, 0, 8);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }


        String cmd = new String(Arrays.copyOfRange(header, 0, 4)).trim();
        int argSize = -1;
        try {
            argSize = Integer.parseInt(new String(ByteBuffer.wrap(Arrays.copyOfRange(header, 4,8)).array()).trim());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }


        byte[] dataBuffer = new byte[argSize];
        try {
            inputStream.read(dataBuffer, 0, argSize);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        switch (cmd) {
            case "DPUT":
                processDutFile(dataBuffer);
                break;
            case "TEST":
                openDataConnection();
                break;
            case "PUT":
                processPutFile(dataBuffer);
                break;
            case "LIST":
                processList(dataBuffer);
                break;
            case "GET":
                processGetFile(dataBuffer);
                break;
            case "CD":
                processCD(dataBuffer);
                break;
            case "QUIT":
                return -1;
            default:

        }

        return 0;
    }

    private void processList(byte[] buffer) {
        String arg = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();

        String result = "";
        String path = "";
        if (arg.equals(".")) {
            path = curDir;
        } else if (arg.equals("..")) {
            path = Paths.get(curDir, "..").toAbsolutePath().toString();
        } else {
            path = arg;
        }

        try {
            result = getFileList(path);
        } catch (FileNotFoundException fe) {
            writeToClient("", "501", "ERR");
            System.out.println("Request : LIST " + path);
            System.out.println("Response : 501 failed - Directory name is invalid");
            return;
        } catch (NotDirectoryException nde) {
            writeToClient("", "501", "ERR");
            System.out.println("Request : LIST " + path);
            System.out.println("Response : 501 failed - Directory name is invalid");
            return;
        }
        int filecount = Integer.parseInt(result.substring(result.lastIndexOf(",")+1).trim());
        result = result.substring(0,result.lastIndexOf(","));
        System.out.println("Request : LIST " + path);
        System.out.println("Response : 200 Comprising "+ filecount +" entries");
        writeToClient(result, "200", "STR");
    }

    private String getFileList(String path) throws FileNotFoundException, NotDirectoryException {
        File dir = new File(path);
        File[] files = dir.listFiles();

        if (!dir.exists()) {
            throw new FileNotFoundException();
        }

        if (!dir.isDirectory()) {
            throw new NotDirectoryException("Not a directory");
        }

        String out = "";

        for (File f:files) {
            if (f.isDirectory())
                out+= f.getName()+",-"+"\n";
            else
                out +=  f.getName()+","+f.length()+"\n";
        }
        out+=","+files.length;

        return out;
    }

    private void processCD(byte[] buffer) {
        String path = new String(buffer).trim();
        String result = curDir;
        if (path.equals(".")) {
            path = curDir;
        } else if (path.equals("..")) {
            File f = new File(curDir);
            curDir = f.getParent();
        }else if(path.substring(0,2).contentEquals(".\\")) {
            result = curDir + "\\" + path.substring(2);
            curDir = result;
        }else if(path.substring(0,3).contentEquals("..\\")) {
            result = curDir.substring(0, curDir.lastIndexOf("\\")) + "\\" + path.substring(3);
            curDir = result;
        }else {
            File f = new File(path);
            if (f.exists()) {
                if (!f.isDirectory()) {
                    writeToClient("501 failed - Directory is invalid\n", "501", "ERR");
                    System.out.println("Request : CD " + path);
                    System.out.println("Response : 501 failed - Directory name is invalid");
                    return;
                }
                curDir = f.getAbsolutePath();
            } else {
                writeToClient("501 failed - Directory is invalid\n", "501", "ERR");
                System.out.println("Request : CD " + path);
                System.out.println("Response : 501 failed - Directory name is invalid");
                return;
            }
        }

        writeToClient(curDir, "200", "RST");
        System.out.println("Request : CD " + curDir);
        System.out.println("Response : 200 Moved to "+ curDir);
    }

    //TODO: end my life

    private void openDataConnection() {
        try {
            dataSocket = new ServerSocket(this.dataPort);
            //dataSocket.bind(new InetSocketAddress(this.ipAddress, this.dataPort));
            dataConnection = dataSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            this.dataInputStream = dataConnection.getInputStream();
            this.dataOutputStream = dataConnection.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    private void openDataConnectionSC() {
        try
        {
            dataConnection = new Socket(dataSocket.getInetAddress(), this.dataPort);
            this.dataInputStream = dataConnection.getInputStream();
            this.dataOutputStream = dataConnection.getOutputStream();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }
    */
    private void closeDataConnection() {
        try {
            dataInputStream.close();
            dataOutputStream.close();
            dataConnection.close();
            dataSocket.close();

            if(dataSocket != null) {
                dataSocket.close();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private int dataWrite(OutputStream os, byte[] buffer, byte[] SeqNo, byte[] CHKsum, byte[] Size) {
        ByteBuffer bf = ByteBuffer.allocate(12+buffer.length);

        bf.put(Arrays.copyOf(SeqNo, 4));
        bf.put(Arrays.copyOf(CHKsum, 4));
        bf.put(Arrays.copyOf(Size, 4));
        bf.put(buffer);

        try {
            os.write(bf.array());
            os.flush();
        }
        catch (IOException e) {
            return -1;
        }

        return buffer.length;
    }

    private int dataWriteToClient(byte[] buffer, int SeqNo, String CHKsum, int Size) {
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
                dataWrite(dataOutputStream, Arrays.copyOfRange(buffer, chunkcount * 1000, (chunkcount + 1) * 1000), SequenceNumber.array(), CheckSum.array(), fileSize.array());
            }
            else dataWrite(dataOutputStream, Arrays.copyOfRange(buffer, chunkcount * 1000, (chunkcount * 1000)+iterativeRemainder), SequenceNumber.array(), CheckSum.array(), fileSize.array());
            chunkcount++;
        }
        closeDataConnection();
        return 0;
    }



    //TODO: Absolute Pain in the ass of this Assignment.

    private void processPutFile(byte[] buffer) {
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();

        FileOutputStream fileOutputStream;
        File f = new File(Paths.get(this.curDir, filename).toString());
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
            fileOutputStream.write(Arrays.copyOfRange(buffer,255, buffer.length));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void processGetFile(byte[] buffer) {
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();

        File f = new File(Paths.get(this.curDir, filename).toString());
        if (!f.exists()) {
            writeToClient("Failed - No such file exists", "401", "ERR");
            return;
        }

        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream(f);

        } catch (FileNotFoundException e) {
        }

        byte[] out = new byte[(int)f.length()];
        try {
            fileInputStream.read(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
//TODO
        ByteBuffer handshake = ByteBuffer.allocate((int)f.length());
        handshake.put(out);

        ByteBuffer bf = ByteBuffer.allocate(255 + (int)f.length());
        bf.put(Arrays.copyOf(f.getName().getBytes(), 255));
        bf.put(out);
        try{
            writeToClient(bf.array(), "200", "FILE");
            //writeToClient(handshake.array(), "200", "FILE");
            //openDataConnection();
        }catch(Exception e){
            e.printStackTrace();
        }
        //dataWriteToClient(bf.array(), 0, "0000", (int)f.length());
        System.out.println("Request : GET " + filename);
        System.out.println("Response : 200 Containing "+f.length()+" bytes in total");
    }


    private byte[] dataRead() {
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

        try {
            dataInputStream.read(buffer, ((chunkcount*1000)+12), Size);
        } catch (IOException e) {
            e.printStackTrace();
        }

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
    private void processDutFile(byte[] buffer) {
        openDataConnection();
        writeToClient("Ready to receive", "200", "FILE");
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();

        FileOutputStream fileOutputStream;
        File f = new File(Paths.get(this.curDir, filename).toString());
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
        byte[] dataResp = null;

        try {
            //dataResp = new byte[received.length];
            dataResp = new byte[(buffer.length-12)*(((buffer.length-12)/1000)+1)];
            dataResp = dataRead();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            fileOutputStream.write(Arrays.copyOfRange(dataResp,255, buffer.length));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
