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
        Pair<String, Integer> node = Utils.getRandomNodePort(CONFIG.NODES);
        String nodeHost = node.getValue0();
        int nodePort = node.getValue1();
        Thread.sleep(CONFIG.CELL_CONN_DELAY_MILIS);
        // preparation for identification with node
        socket = Utils.cellTryToCreateSocket(nodeHost, nodePort, CONFIG.CELL_CONN_DELAY_MILIS, LOGGER);
        identifier = Utils.createIdentifier(HOST, socket.getLocalPort());

        DataOutputStream socketOutStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream socketInStream = new DataInputStream(socket.getInputStream());

        // identification between cell and node
        Message identMsg = Message.buildIdentify(ProgramType.SERVER, identifier, ProgramType.NODE);
        DecoderEncoder.writeMsg(socketOutStream, identMsg);
        Message nodeIdentMsg = DecoderEncoder.readMsg(socketInStream);

        if (nodeIdentMsg.getNumServicio() != ServiceNumber.Identification) {
            LOGGER.fatal("Número de servicio incorrecto, primer mensaje debió ser identificación: {}", nodeIdentMsg.getNumServicio().toString());
            System.exit(1);
        }
        if (DecoderEncoder.processIdentification(nodeIdentMsg) != ProgramType.NODE) {
            LOGGER.fatal("Conexión a identidad distinta a 'nodo'");
            System.exit(1);
        }
        LOGGER.info("Conectado exitosamente a: {}:{}", HOST, node);

        // Start receiver thread
        new Thread(() -> messageManager.receiverLoop(identifier, socketInStream, socketOutStream, (v) -> {
            LOGGER.info(v);
            return null;
        }), "Server-receiverLoop").start();
        // Start dispatcher
        new Thread(() -> messageManager.dispatcherLoop(identifier, socketOutStream), "Server-dispatcherLoop").start();
    }
}