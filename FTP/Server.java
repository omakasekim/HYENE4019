package FTP;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class Server {
    private int port;
    private int dataPort;
    private String bindIp;

    private ServerSocket socket;


    public Server() {
        this("127.0.0.1", 2020, 2021);
    }

    public Server(String bindIp, int port, int dataPort) {
        this.bindIp = bindIp;
        this.port = port;
        this.dataPort = dataPort;
    }

    public void listen() {
        try {
            this.socket = new ServerSocket(this.port);
            socket.bind(new InetSocketAddress(this.bindIp, this.port));
        }
        catch (IOException e) {
            // Socket creation failed
        }

        while(true) {
            Handler handler = null;
            try {
                handler = new Handler(socket.accept(), this.dataPort, this.bindIp);
            } catch (IOException e) {
                // Accept socket failed
            }

            if (handler != null ){
                new Thread(handler).start();
                //handler.run();
            }
        }
    }
}
