package org.example;

import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Gestiona las conexiones activas, separando entre nodos y clientes (células).
public class ConnectionHandler {
    private final Logger LOGGER;
    private final Set<Connection> nodeConnections;
    private final Set<Connection> clientConnections;

    public ConnectionHandler(Logger logger) {
        this.LOGGER = logger;
        // Conjuntos concurrentes para almacenar conexiones a otros nodos y a clientes.
        this.nodeConnections = ConcurrentHashMap.newKeySet();
        this.clientConnections = ConcurrentHashMap.newKeySet();
    }

    // Envía un mensaje a todas las conexiones de clientes (células servidoras/solicitantes).
    public void sendToClients(Message msg) throws IOException {
        for (Connection conn : this.clientConnections) {
            conn.sendMsg(msg);
        }
    }

    // Envía un mensaje a todas las conexiones de nodos.
    public void sendToNodes(Message msg) throws IOException {
        for (Connection conn : this.nodeConnections) {
            conn.sendMsg(msg);
        }
    }

    // Añade una nueva conexión, clasificándola como NODO o CLIENTE.
    public void addConnection(Connection conn) {
        switch (conn.type) {
            case NODE -> this.nodeConnections.add(conn);
            default ->
                    this.clientConnections.add(conn); // Células Servidoras y Solicitantes se tratan como clientes del nodo.
        }
        LOGGER.debug("Nueva conexión de tipo: {}", conn.type);
    }

    // Elimina una conexión.
    public void removeConnection(Connection conn) {
        switch (conn.type) {
            case NODE -> this.nodeConnections.remove(conn);
            default -> this.clientConnections.remove(conn);
        }
        LOGGER.debug("Conexión eliminada ({}) de tipo: {}", conn.socket.getPort(), conn.type);
    }

    // Clase interna que representa una conexión individual.
    public static final class Connection {
        private final ProgramType type; // Tipo de entidad al otro lado (NODO, SERVIDOR, SOLICITANTE).
        private final Socket socket;
        private final DataOutputStream dataOutputStream; // Stream de salida para esta conexión.
        private final DataInputStream dataInputStream;   // Stream de entrada para esta conexión.

        public Connection(ProgramType programType, Socket socket, DataInputStream dis, DataOutputStream dos) throws IOException {
            this.type = programType;
            this.socket = socket;
            this.dataInputStream = dis;
            this.dataOutputStream = dos;
        }

        // Envía un mensaje a través de esta conexión.
        public void sendMsg(Message msg) throws IOException {
            DecoderEncoder.writeMsg(this.dataOutputStream, msg);
        }

        // Lee un mensaje de esta conexión.
        public Message readMsg() throws IOException {
            return DecoderEncoder.readMsg(this.dataInputStream);
        }

        public void closeSocket() throws IOException {
            this.socket.close();
        }

        public ProgramType getType() {
            return this.type;
        }
    }
}