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
        // Manejador de las conexiones del nodo.
        ConnectionHandler connectionHandler = new ConnectionHandler(LOGGER);

        LOGGER.info("Nodos: {}", Arrays.toString(CONFIG.NODES.toArray()));

        // Retraso aleatorio para permitir la sincronización de nodos al inicio.
        long delay = new Random().nextLong(1, 15) * 300 + 400;
        LOGGER.info("Startup sleeping {}ms", delay);
        Thread.sleep(delay);

        // Intenta crear un ServerSocket en uno de los puertos configurados.
        ServerSocket server = createServerSocket(CONFIG.getNodePorts());

        LOGGER.info("Nodo escuchando en {}:{}", server.getInetAddress(), server.getLocalPort());
        // Identificador único para este nodo.
        this.identifier = Utils.createIdentifier(HOST, server.getLocalPort());

        // Conectarse a otros nodos especificados en la configuración.
        for (Pair<String, Integer> node : CONFIG.NODES) {
            if (node.getValue1() == server.getLocalPort())
                // No conectarse a sí mismo.
                continue;
            try {
                LOGGER.info("Tratando de conectarse a {}:{}", node.getValue0(), node.getValue1());
                // Establece conexión con otro nodo.
                Socket socket = new Socket(node.getValue0(), node.getValue1());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                // Envía mensaje de identificación al nodo al que se conecta.
                DecoderEncoder.writeMsg(out, Message.buildIdentify(ProgramType.NODE, identifier, ProgramType.NODE));

                // El primer mensaje recibido debe ser una identificación.
                ProgramType programType = DecoderEncoder.processIdentification(DecoderEncoder.readMsg(in));
                ConnectionHandler.Connection currentNodeConn = new ConnectionHandler.Connection(programType, socket, in, out);
                connectionHandler.addConnection(currentNodeConn); // Añade la conexión al manejador.

                // Inicia un hilo para manejar la comunicación con este nodo conectado.
                Thread currNodeConnectionThread = new Thread(() -> handle(connectionHandler, currentNodeConn), "currNodeConnectionThread");
                currNodeConnectionThread.start();
                LOGGER.info("Este nodo conectado a nodo: {}", node);
            } catch (IOException ignored) {
                // Ignora si no se puede conectar a un nodo (puede que aún no esté activo).
            }
        }

        // Hilo para aceptar nuevas conexiones entrantes (de otras células o nodos).
        Thread acceptingThread = new Thread(() -> {
            while (true) {
                try {
                    // Preparación para identificar la nueva conexión.
                    Socket socket = server.accept(); // Espera y acepta una nueva conexión.
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    // Envía un mensaje de identificación a la entidad que se acaba de conectar.
                    // Se identifica como NODO, esperando una respuesta de un SERVIDOR (célula) o NODO.
                    DecoderEncoder.writeMsg(out, Message.buildIdentify(ProgramType.NODE, identifier, ProgramType.SERVER));

                    // El primer mensaje recibido debe ser una identificación.
                    ProgramType programType = DecoderEncoder.processIdentification(DecoderEncoder.readMsg(in)); // Lee la identificación de la entidad conectada.
                    ConnectionHandler.Connection currentNodeConn = new ConnectionHandler.Connection(programType, socket, in, out);
                    connectionHandler.addConnection(currentNodeConn); // Añade la nueva conexión al manejador.

                    // Inicia un hilo para manejar esta nueva conexión.
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
            acceptingThread.join(); // Espera a que el hilo de aceptación termine (aunque en este diseño es un bucle infinito).
        } catch (InterruptedException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
    }

    // Maneja la comunicación para una conexión específica.
    private static void handle(ConnectionHandler connHandler, ConnectionHandler.Connection connection) {
        while (true) {
            try {
                Message msg = connection.readMsg(); // Lee un mensaje de la conexión.
                LOGGER.info(msg);
                switch (connection.getType()) {
                    case ProgramType.NODE:
                        // Si el mensaje viene de otro NODO, lo reenvía solo a células, pues se asume que
                        // todos los nodos están conectados cada uno entre sí (4 en este caso).
                        // Para topologías más grandes/complejas, esta lógica necesitaría revisión.
                        connHandler.sendToClients(msg);
                        break;
                    default: // Si viene de una CÉLULA (SOLICITANTE o SERVIDOR)
                        // Reenvía el mensaje a todos los NODOS y a todos los CLIENTES.
                        // Las células receptoras descartarán el mensaje si no es para ellas.
                        connHandler.sendToNodes(msg);
                        connHandler.sendToClients(msg);
                        break;
                }
            } catch (IOException e) {
                // Si hay un error de IO (ej. desconexión), elimina la conexión y termina el hilo.
                connHandler.removeConnection(connection);
                try {
                    connection.closeSocket();
                } catch (IOException ignored) {
                }
                return;
            }
        }
    }

    // Intenta crear un ServerSocket en la lista de puertos disponibles.
    private static ServerSocket createServerSocket(int[] nodePorts) {
        for (int port : nodePorts) {
            try {
                return new ServerSocket(port); // Devuelve el primer ServerSocket que se pueda crear.
            } catch (IOException ignored) {
                // Si el puerto está en uso, prueba el siguiente.
            }
        }
        LOGGER.fatal("No hay puertos de nodo disponibles");
        throw new RuntimeException("No hay puertos de nodo disponibles"); // Lanza excepción si ningún puerto está disponible.
    }

}