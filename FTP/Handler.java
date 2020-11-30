package FTP;

import javax.xml.crypto.Data;
import java.io.*;
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
    private int dataPort;
    private ServerSocket serverSocket;
    private boolean DROP = false;
    private boolean TIMEOUT = false;
    private boolean BITERROR = false;
    private int problem;




    public Handler(Socket socket, int dataPort, String ipAddress) throws IOException {
        this.socket = socket;
        this.dataPort = dataPort;
        this.ipAddress = ipAddress;
        this.curDir = new File(".").getAbsolutePath();

        try {
            this.outputStream = socket.getOutputStream();
            this.inputStream = socket.getInputStream();
            serverSocket = new ServerSocket(dataPort);

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
            case "DROP":
                setDrop(dataBuffer);
                break;
            case "BITERROR":
                setBitError(dataBuffer);
                break;
            case "TIMEOUT":
                setTimeout(dataBuffer);
                break;
            case "PUT":
                //PutWrapper(dataBuffer);
                //handlePutWrapper(dataBuffer);
                experimentalPutWrapper(dataBuffer);
                break;
            case "LIST":
                processList(dataBuffer);
                break;
            case "GET":
                GetWrapper(dataBuffer);
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
        writeToClient(result, "200", "LST");
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
    private void setDrop(byte[] buffer) {
        DROP = true;
        problem = Integer.parseInt(new String(buffer));
    }
    private void setBitError(byte[] buffer) {
        BITERROR = true;
        problem = Integer.parseInt(new String(buffer));

    }
    private void setTimeout(byte[] buffer) {
        TIMEOUT = true;
        problem = Integer.parseInt(new String(buffer));
    }


    //TODO
    private void experimentalPutWrapper(byte[] buffer){
        try {
            experimentalPut(buffer);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    private void experimentalPut(byte[] buffer) throws IOException {
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();
        File f = new File(Paths.get(this.curDir, filename).toString());
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        writeToClient("Ready to receive", "200", "PUT");
        Socket dataSocket = serverSocket.accept();
        DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
        FileOutputStream fileOutputStream = new FileOutputStream(f);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        int filelen = buffer.length-255;
        try {
            Receiver receiver = new Receiver(fileOutputStream, bufferedOutputStream, dataInputStream, dataOutputStream, filelen);
            if(receiver!=null) receiver.startUDPConnection();
        } catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("Request : PUT " + f.getName());
        System.out.println("Request : "+filelen);
        System.out.println("Response : 200 Ready to receive");
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

        writeToClient(curDir, "200", "CWD");
        System.out.println("Request : CD " + curDir);
        System.out.println("Response : 200 Moved to "+ curDir);
    }
    private void PutWrapper(byte[] buffer) {
        try {
            processPutFile(buffer);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    private void GetWrapper(byte[] buffer) {
        try {
            processGetFile(buffer);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    private void processPutFile(byte[] buffer) throws IOException {
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();
        File f = new File(Paths.get(this.curDir, filename).toString());
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        writeToClient("Ready to receive", "200", "PUT");
        Socket dataSocket = serverSocket.accept();
        DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
        FileOutputStream fileOutputStream = new FileOutputStream(f);

        int filelen = buffer.length-255;
        System.out.println("Request : PUT " + f.getName());
        System.out.println("Request : "+filelen);
        System.out.println("Response : 200 Ready to receive");

        for (byte seqNo = 0; seqNo * DataPacket.maxDataSize < filelen; seqNo++) {
            byte[] bytes = dataInputStream.readNBytes(DataPacket.maxChunkSize);
            DataPacket chunk = new DataPacket(bytes);
            fileOutputStream.write(chunk.data);
        }

        dataSocket.close();
        dataInputStream.close();
        fileOutputStream.close();

    }
    private void processGetFile(byte[] buffer){
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();

        File f = new File(Paths.get(this.curDir, filename).toString());
        if (!f.exists()) {
            writeToClient("Failed - No such file exists", "401", "ERR");
            return;
        }
        writeToClient(String.valueOf(f.length()), "200", "GET");


    try {
        Socket dataSocket = serverSocket.accept();
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        FileInputStream fileInputStream = new FileInputStream(f);

        for (byte seqNo = 0; ; seqNo++) {
            byte[] data = fileInputStream.readNBytes(DataPacket.maxDataSize);
            if (data.length == 0) break;
            DataPacket chunk = new DataPacket(seqNo,(short)data.length, data);
            chunk.writeBytes(dataOutputStream);
        }
        System.out.println("Request : GET " + f.getName());
        System.out.println("Response : 200 Containing " + f.length() + " bytes in total");
        dataSocket.close();
        dataOutputStream.close();
        fileInputStream.close();

    }catch (Exception e){
        e.printStackTrace();
    }

    }

    private void handlePutWrapper(byte[] buffer) {
        try {
            handlePUT(buffer);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    private void handlePUT(byte[] buffer) throws IOException {
        String filename = new String(Arrays.copyOfRange(buffer, 0, 255)).trim();
        File f = new File(Paths.get(this.curDir, filename).toString());
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        writeToClient("Ready to receive", "200", "PUT");

        int filelen = buffer.length-255;
        int chunks = (filelen + DataPacket.maxDataSize-1)/DataPacket.maxDataSize;
        Socket dataSocket = serverSocket.accept();
        DataInputStream dataInputStream = new DataInputStream(dataSocket.getInputStream());
        DataOutputStream dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
        FileOutputStream fileOutputStream = new FileOutputStream(f);

        DataPacket[] window = new DataPacket[DataPacket.maxWinSize];
        int SeqBase = 0;
        int inWindow = 0;
        int firstSeqNo = 0;


        while(chunks > 0) {
            byte[] header = dataInputStream.readNBytes(DataPacket.headerSize);
            DataPacket chunk = new DataPacket(header);
            int localSeqNo = chunk.getSeqNo();
            int logicalSeqNo = localSeqNo-firstSeqNo+((localSeqNo-firstSeqNo<-DataPacket.maxWinSize)?DataPacket.maxSeqNo:0);

            if(logicalSeqNo < 0){
                dataOutputStream.writeBytes(Integer.toString(localSeqNo));
            } else if (logicalSeqNo < DataPacket.maxWinSize) {
                chunk.setPayload(dataInputStream.readNBytes(chunk.getSize()));
                if(chunk.calcCHK()) continue;
                int idx = (SeqBase+logicalSeqNo)%DataPacket.maxWinSize;
                window[idx] = chunk;
                inWindow++;
                dataOutputStream.writeBytes(Integer.toString(localSeqNo));
                while (window[SeqBase] != null && inWindow > 0) {
                    System.out.print(firstSeqNo + " ");
                    fileOutputStream.write(window[SeqBase].data);
                    window[SeqBase] = null;
                    firstSeqNo = (byte)((firstSeqNo + 1)%(DataPacket.maxSeqNo));
                    SeqBase = (SeqBase+1)%DataPacket.maxWinSize;
                    inWindow--;
                    chunks--;
                }
            }
        }

        System.out.println("Request : PUT " + f.getName());
        System.out.println("Request : "+filelen);
        System.out.println("Response : 200 Ready to receive");

        System.out.println("\t Completed");
        dataSocket.close();
        dataInputStream.close();
        fileOutputStream.close();

        BITERROR = false;
        TIMEOUT = false;
        DROP = false;
        problem = 0;
    }


}
