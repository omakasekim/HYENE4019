package FTP;


import java.io.IOException;
import java.util.Scanner;

public class FTPClient {
    public static void main(String[] args) throws Exception {
        int port = 2020;
        int dataPort = 2021;
        String serverHost = "127.0.0.1";
        if (args.length > 0) {
            serverHost = args[0];
            port = Integer.parseInt(args[1]);
            dataPort = Integer.parseInt(args[2]);
        }
        Client ftpClient = new Client(serverHost, port, dataPort);
        Scanner scanner = new Scanner(System.in);

        loop:
        while(true) {
            String cmd = null;
            cmd = scanner.nextLine();
            String[] cmds = new String[2];

            if(cmd.contains("DROP")) {
                cmds = cmd.split(" ");
            }
            if(cmd.contains("TIMEOUT")) {
                cmds = cmd.split(" ");
            }
            if(cmd.contains("BITERROR")) {
                cmds = cmd.split(" ");
            }
            if(cmd.contentEquals("QUIT")) {
                cmds[0] = cmd;
                cmds[1] = null;
            }
            else if(cmd.contentEquals("LIST")) {
                cmds[0] = cmd;
                cmds[1] = ".";
            }
            else if(cmd.contentEquals("GET")) {
                cmds[0] = cmd;
                cmds[1] = ".";
            }
            else if(cmd.contentEquals("PUT")) {
                cmds[0] = cmd;
                cmds[1] = ".";
            }
            else if(cmd.contentEquals("CD")) {
                cmds[0] = cmd;
                cmds[1] = ".";
            }
            else if(cmd.contains("QUIT") && cmd.length() > 4) {
                cmds = cmd.split(" ");
            }
            else if(cmd.contains("LIST") && cmd.length() > 4) {
                cmds = cmd.split(" ");
            }
            else if(cmd.contains("GET") && cmd.length() > 4) {
                cmds = cmd.split(" ");
            }
            else if(cmd.contains("PUT") && cmd.length() > 4) {
                cmds = cmd.split(" ");
            }
            else if(cmd.contains("CD") && cmd.length() > 2) {
                cmds = cmd.split(" ");
            }


            switch (cmds[0]) {
                case "DROP":
                    ftpClient.dropRequest(cmds[1]);
                    break;
                case "BITERROR":
                    ftpClient.bitErrorRequest(cmds[1]);
                    break;
                case "TIMEOUT":
                    ftpClient.timeoutRequest(cmds[1]);
                    break;
                case "GET":
                    if(cmds[1] == null)
                        cmds[1] = ".";
                    ftpClient.Get(cmds[1]);
                    break;
                case "QUIT":
                    ftpClient.quitConnection();
                    break loop;
                case "LIST":
                    if(cmds[1] == null || cmds[1].equals(" "))
                        cmds[1] = ".";
                    ftpClient.List(cmds[1]);
                    break;
                case "PUT":
                    //TODO
                    //ftpClient.Put(cmds[1]);
                    //ftpClient.handlePut(cmds[1]);
                    ftpClient.experimentalPut(cmds[1]);
                    break;
                case "CD":
                    if(cmds[1] == null || cmds[1].equals(" "))
                        ftpClient.CD(".");
                    else ftpClient.CD(cmds[1]);
                    break;
                default:

            }
        }
    }

}

