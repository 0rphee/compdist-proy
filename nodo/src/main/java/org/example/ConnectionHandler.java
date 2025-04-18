package org.example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionHandler {
    private final Set<Connection> nodeConnections;
    private final Set<Connection> clientConnections;

    public ConnectionHandler() {
        this.nodeConnections = ConcurrentHashMap.newKeySet();
        this.clientConnections = ConcurrentHashMap.newKeySet();
    }

    public void sendToClients(Message msg) throws IOException {
        for (Connection conn : this.clientConnections) {
            DataOutputStream out = new DataOutputStream(conn.socket.getOutputStream());
            DecoderEncoder.writeMsg(out, msg);
        }
    }

    public void sendToNodes(Message msg) throws IOException {
        for (Connection conn : this.nodeConnections) {
            DataOutputStream out = new DataOutputStream(conn.socket.getOutputStream());
            DecoderEncoder.writeMsg(out, msg);
        }
    }

    public void addConnection(Connection conn) {
        switch (conn.type) {
            case NODE -> this.nodeConnections.add(conn);
            default -> this.clientConnections.add(conn);
        }
        // System.out.println("New connection of type: " + conn.type);
    }

    public void removeConnection(Connection conn) {
        switch (conn.type) {
            case NODE -> this.nodeConnections.remove(conn);
            default -> this.clientConnections.remove(conn);
        }
        System.out.println("Removed connection" + conn.socket.getPort() + " of type: " + conn.type);
    }

    public static final class Connection {
        private final Message.ProgramType type;
        private final Socket socket;
        private final DataOutputStream dataOutputStream;
        private final DataInputStream dataInputStream;

        public Connection(Socket socket, Message.ProgramType programType) throws IOException {
            this.type = programType;
            this.socket = socket;
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        }

        public Connection(Message.ProgramType programType, Socket socket, DataInputStream dis, DataOutputStream dos) throws IOException {
            this.type = programType;
            this.socket = socket;
            this.dataInputStream = dis;
            this.dataOutputStream = dos;
        }

        public void sendMsg(Message msg) throws IOException {
            DecoderEncoder.writeMsg(this.dataOutputStream, msg);
        }

        public Message readMsg() throws IOException {
            return DecoderEncoder.readMsg(this.dataInputStream);
        }

        public void closeSocket() throws IOException {
            this.socket.close();
        }

        public Message.ProgramType getType() {
            return this.type;
        }
    }
}
