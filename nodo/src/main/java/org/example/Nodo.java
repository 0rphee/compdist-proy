package org.example;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Random;


public class Nodo {
    private static final String HOST = "localhost";

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: CelulaSolicitante <PORT>");
        }
        // TODO: port arg
        // int port = Integer.parseInt(args[0]);

        // available ports to setup ServerSocket
        // int[] portsavailable
        ConnectionHandler connectionHandler = new ConnectionHandler();

        System.out.println("PORTS: " + Arrays.toString(Message.nodePorts));

        /* Random delay to enable Node sync on startup */
        long delay = new Random().nextLong(1, 15) * 300 + 400;
        System.out.println("Sleeping " + delay);
        Thread.sleep(delay);

        ServerSocket server = createServerSocket();
        System.out.println("Node escuchando en " + server.getInetAddress() + ":" + server.getLocalPort() + "\n");

        // connect to other nodes
        for (int port : Message.nodePorts) {
            if (port == server.getLocalPort())
                continue;
            try {
                System.out.println("Trying to connect to " + HOST + ":" + port);
                Socket socket = new Socket(HOST, port);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DecoderEncoder.writeMsg(out, Message.buildIdentify(Message.CellType.NODE));

                // first msg received should be and identification msg
                Message.CellType cellType = DecoderEncoder.processIdentification(DecoderEncoder.readMsg(in));
                ConnectionHandler.Connection currentNodeConn = new ConnectionHandler.Connection(cellType, socket, in, out);
                connectionHandler.addConnection(currentNodeConn);

                Thread currNodeConnectionThread = new Thread(() -> handle(connectionHandler, currentNodeConn));
                currNodeConnectionThread.start();
                System.out.println("This node connected to node: " + port);
            } catch (IOException ignored) {
            }
        }

        Thread acceptingThread = new Thread(() -> {
            while (true) {
                try {
                    Socket socket = server.accept();
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DecoderEncoder.writeMsg(out, Message.buildIdentify(Message.CellType.NODE));

                    // first msg received should be and identification msg
                    Message.CellType cellType = DecoderEncoder.processIdentification(DecoderEncoder.readMsg(in));
                    ConnectionHandler.Connection currentNodeConn = new ConnectionHandler.Connection(cellType, socket, in, out);
                    connectionHandler.addConnection(currentNodeConn);

                    Thread handle = new Thread(() -> handle(connectionHandler, currentNodeConn));
                    handle.start();
                    System.out.println("A client or node connected to this node: " + socket.getPort());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        acceptingThread.start();
        try {
            acceptingThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void handle(ConnectionHandler connHandler, ConnectionHandler.Connection connection) {
        while (true) {
            try {
                Message msg = connection.readMsg();
                System.out.println(msg);
                switch (connection.getType()) {
                    case Message.CellType.NODE:
                        // we don't send to other nodes because currently all nodes are connected with each other
                        // in a larger data field (more nodes, with some not connected to others) this is will not work
                        connHandler.sendToClients(msg);
                        break;
                    default:
                        // we send the message to all cells, they will discard the message if it's not for them
                        connHandler.sendToNodes(msg);
                        connHandler.sendToClients(msg);
                        break;
                }
            } catch (IOException e) {
                connHandler.removeConnection(connection);
                try {
                    connection.closeSocket();
                } catch (IOException ignored) {
                }
                return;
            }
        }
    }

    private static ServerSocket createServerSocket() {
        for (int port : Message.nodePorts) {
            try {
                return new ServerSocket(port);
            } catch (IOException ignored) {
            }
        }
        throw new RuntimeException("No server ports available");
    }

}