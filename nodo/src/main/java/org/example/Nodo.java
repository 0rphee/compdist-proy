package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Random;


public class Nodo {
    private static final Logger LOGGER = LogManager.getLogger(Nodo.class);
    private static final ConfigReader.Config CONFIG = ConfigReader.readConfig(LOGGER);
    private static final String HOST = "localhost";
    private byte[] identifier;

    Nodo() {
    }

    public static void main(String[] args) throws InterruptedException {
        Nodo nodo = new Nodo();
        nodo.start(args);
    }

    public void start(String[] args) throws InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: Nodo");
        }
        ConnectionHandler connectionHandler = new ConnectionHandler(LOGGER);

        LOGGER.info("Nodos: {}", Arrays.toString(CONFIG.NODES.toArray()));

        /* Random delay to enable Node sync on startup */
        long delay = new Random().nextLong(1, 15) * 300 + 400;
        LOGGER.info("Startup sleeping {}ms", delay);
        Thread.sleep(delay);

        ServerSocket server = createServerSocket(CONFIG.getNodePorts());

        LOGGER.info("Nodo escuchando en {}:{}", server.getInetAddress(), server.getLocalPort());
        this.identifier = Utils.createIdentifier(HOST, server.getLocalPort());

        // connect to other nodes
        for (Pair<String, Integer> node : CONFIG.NODES) {
            if (node.getValue1() == server.getLocalPort())
                continue;
            try {
                LOGGER.info("Tratando de conectarse a {}:{}", node.getValue0(), node.getValue1());
                Socket socket = new Socket(node.getValue0(), node.getValue1());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DecoderEncoder.writeMsg(out, Message.buildIdentify(ProgramType.NODE, identifier, ProgramType.NODE));

                // first msg received should be and identification msg
                ProgramType programType = DecoderEncoder.processIdentification(DecoderEncoder.readMsg(in));
                ConnectionHandler.Connection currentNodeConn = new ConnectionHandler.Connection(programType, socket, in, out);
                connectionHandler.addConnection(currentNodeConn);

                Thread currNodeConnectionThread = new Thread(() -> handle(connectionHandler, currentNodeConn), "currNodeConnectionThread");
                currNodeConnectionThread.start();
                LOGGER.info("Este nodo conectado a nodo: {}", node);
            } catch (IOException ignored) {
            }
        }

        Thread acceptingThread = new Thread(() -> {
            while (true) {
                try {
                    // preparation to identify with the new connection
                    Socket socket = server.accept();
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DecoderEncoder.writeMsg(out, Message.buildIdentify(ProgramType.NODE, identifier, ProgramType.SERVER));

                    // first msg received should be and identification msg
                    ProgramType programType = DecoderEncoder.processIdentification(DecoderEncoder.readMsg(in));
                    ConnectionHandler.Connection currentNodeConn = new ConnectionHandler.Connection(programType, socket, in, out);
                    connectionHandler.addConnection(currentNodeConn);

                    Thread handle = new Thread(() -> handle(connectionHandler, currentNodeConn), "handleThread");
                    handle.start();
                    LOGGER.info("Nueva conexión recibida: {}, {}", socket.getPort(), programType);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "acceptingThread");
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
                LOGGER.info(msg);
                switch (connection.getType()) {
                    case ProgramType.NODE:
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

    private static ServerSocket createServerSocket(int[] nodePorts) {
        for (int port : nodePorts) {
            try {
                return new ServerSocket(port);
            } catch (IOException ignored) {
            }
        }
        throw new RuntimeException("No server ports available");
    }

}