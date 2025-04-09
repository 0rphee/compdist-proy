package org.example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;

public class ConnectionHandler {
    private final HashSet<Connection> nodeConnections;
    private final HashSet<Connection> clientConnections;

    public ConnectionHandler() {
        this.nodeConnections = new HashSet<>();
        this.clientConnections = new HashSet<>();
    }

    public void sendToClients(Message msg) throws IOException {
        for (Connection conn : this.clientConnections){
            DataOutputStream out = new DataOutputStream(conn.socket.getOutputStream());
            DecoderEncoder.escribir(out, msg);
        }
    }

    public void sendToNodes(Message msg) throws IOException {
        for (Connection conn : this.nodeConnections){
            DataOutputStream out = new DataOutputStream(conn.socket.getOutputStream());
            DecoderEncoder.escribir(out, msg);
        }
    }

    public void addConnection(Connection conn){
        switch (conn.type) {
            case NODE -> this.nodeConnections.add(conn);
            default -> this.clientConnections.add(conn);
        }
        // System.out.println("New connection of type: " + conn.type);
    }
    public void removeConnection(Connection conn){
        switch (conn.type) {
            case NODE -> this.nodeConnections.remove(conn);
            default -> this.clientConnections.remove(conn);
        }
        System.out.println("Removed connection" + conn.socket.getPort() + " of type: " + conn.type);
    }
    public static final class Connection {
        private final Message.CellType type;
        private final Socket socket;
        private final DataOutputStream dataOutputStream;
        private final DataInputStream dataInputStream;
        public Connection(Socket socket, Message.CellType celltype ) throws IOException {
            this.type = celltype;
            this.socket = socket;
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        }
        public Connection(Message.CellType celltype, Socket socket, DataInputStream dis, DataOutputStream dos) throws IOException {
            this.type = celltype;
            this.socket = socket;
            this.dataInputStream = dis;
            this.dataOutputStream = dos;
        }
        public void sendMsg(Message msg) throws IOException {
            DecoderEncoder.escribir(this.dataOutputStream, msg);
        }
        public Message readMsg() throws IOException {
            return DecoderEncoder.leer(this.dataInputStream);
        }
        public void closeSocket() throws IOException {
            this.socket.close();
        }
        public Message.CellType getType() {
            return this.type;
        }
    }
}
