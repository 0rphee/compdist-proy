package org.example;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class CelulaServidor {
    private static final String HOST = "localhost";
    private static byte[] identifier;
    private static Socket socket;
    private static final MessageManager.ServerMessageManager messageManager = new MessageManager.ServerMessageManager();
    public CelulaServidor() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CelulaServidor celulaServidor = new CelulaServidor();
        celulaServidor.start(args);
    }

    public void start(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: CelulaServidor <PORT>");
        }
        int nodePort = Utils.getRandomNodePort();
        int delay = 5_000;
        Thread.sleep(delay);
        // preparation for identification with node
        socket = Utils.cellTryToCreateSocket(HOST, nodePort, delay);
        identifier = Utils.createIdentifier(HOST, socket.getLocalPort());

        DataOutputStream socketOutStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream socketInStream = new DataInputStream(socket.getInputStream());

        // identification between cell and node
        Message identMsg = Message.buildIdentify(ProgramType.SERVER, identifier, ProgramType.NODE);
        DecoderEncoder.writeMsg(socketOutStream, identMsg);
        Message nodeIdentMsg = DecoderEncoder.readMsg(socketInStream);

        if (nodeIdentMsg.getNumServicio() != ServiceNumber.Identification) {
            System.err.println("Número de servicio incorrecto, primer mensaje debió ser identificación: " + nodeIdentMsg.getNumServicio().toString());
            System.exit(1);
        }
        if (DecoderEncoder.processIdentification(nodeIdentMsg) != ProgramType.NODE) {
            System.err.println("Conexión a identidad distinta a 'nodo'");
            System.exit(1);
        }
        System.out.println("Conectado exitosamente a: " + HOST + ":" + nodePort);

        // Start receiver thread
        new Thread(() -> messageManager.receiverLoop(identifier, socketInStream, socketOutStream, new MessageManager.Logger() {
            @Override
            public void showResult(String str) {
                System.out.println(str);
            }
        }), "Server-receiverLoop").start();
        // Start dispatcher
        new Thread(() -> messageManager.dispatcherLoop(identifier, socketOutStream), "Server-dispatcherLoop").start();
    }
}