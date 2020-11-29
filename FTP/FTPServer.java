package FTP;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class FTPServer {
    public static void main(String[] args) {
        int ctrlport = 2020;
        int dataport = 2021;
        String ipAddress = "127.0.0.1";
        try {
            if (args.length > 0) {
                ipAddress = InetAddress. getLocalHost().toString();
                ctrlport = Integer.parseInt(args[0]);
                dataport = Integer.parseInt(args[1]);
            }
            Server ftpServer = new Server(ipAddress, ctrlport, dataport);
            ftpServer.listen();
        } catch(UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
