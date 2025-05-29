package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.*;
import java.net.Socket;

public class CelulaServidor {
    private static final Logger LOGGER = LogManager.getLogger(CelulaServidor.class);
    private static final ConfigReader.Config CONFIG = ConfigReader.readConfig(LOGGER);
    private static final String HOST = "localhost";
    private static byte[] identifier;
    private static Socket socket;
    private static final MessageManager.ServerMessageManager messageManager = new MessageManager.ServerMessageManager(LOGGER, CONFIG.MAX_PENDING_ACKS, CONFIG.SENDER_WAIT_MILIS);

    public CelulaServidor() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CelulaServidor celulaServidor = new CelulaServidor();
        celulaServidor.start(args);
    }

    public void start(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: CelulaServidor");
        }
        // Selecciona un nodo aleatorio de la configuración para conectarse.
        Pair<String, Integer> node = Utils.getRandomNodePort(CONFIG.NODES);
        String nodeHost = node.getValue0();
        int nodePort = node.getValue1();

        // Espera configurada antes de intentar la conexión.
        Thread.sleep(CONFIG.CELL_CONN_DELAY_MILIS);

        // Preparación para la identificación con el nodo.
        socket = Utils.cellTryToCreateSocket(nodeHost, nodePort, CONFIG.CELL_CONN_DELAY_MILIS, LOGGER); // Intenta crear el socket con reintentos.
        identifier = Utils.createIdentifier(HOST, socket.getLocalPort()); // Crea un identificador único para esta célula servidora.

        DataOutputStream socketOutStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream socketInStream = new DataInputStream(socket.getInputStream());

        // Identificación entre la célula y el nodo.
        // Construye y envía un mensaje de identificación al nodo.
        Message identMsg = Message.buildIdentify(ProgramType.SERVER, identifier, ProgramType.NODE);
        DecoderEncoder.writeMsg(socketOutStream, identMsg);
        Message nodeIdentMsg = DecoderEncoder.readMsg(socketInStream); // Lee la respuesta de identificación del nodo.

        // Verifica que el primer mensaje del nodo sea de identificación.
        if (nodeIdentMsg.getNumServicio() != ServiceNumber.Identification) {
            LOGGER.fatal("Número de servicio incorrecto, primer mensaje debió ser identificación: {}", nodeIdentMsg.getNumServicio().toString());
            System.exit(1);
        }
        // Verifica que el nodo se identifique como tal.
        if (DecoderEncoder.processIdentification(nodeIdentMsg) != ProgramType.NODE) {
            LOGGER.fatal("Conexión a identidad distinta a 'nodo'");
            System.exit(1);
        }
        LOGGER.info("Conectado exitosamente a: {}:{}", HOST, node);

        // Inicia el hilo receptor para procesar mensajes entrantes.
        new Thread(() -> messageManager.receiverLoop(identifier, socketInStream, socketOutStream, (v) -> {
            LOGGER.info(v); // Callback para mostrar resultados (el servidor solo haría log, pero nunca lo usa realmente).
            return null;
        }), "Server-receiverLoop").start();
        // Inicia el hilo despachador para enviar mensajes salientes.
        new Thread(() -> messageManager.dispatcherLoop(identifier, socketOutStream), "Server-dispatcherLoop").start();
    }
}