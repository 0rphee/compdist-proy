package org.example;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class CelulaServidor {
    private static final String HOST = "localhost";
    private static byte[] identifier;

    public CelulaServidor(){
        identifier = Utils.createIdentifier(HOST, Utils.getRandomNodePort());
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CelulaServidor celulaServidor= new CelulaServidor();
        celulaServidor.start(args);
    }

    public void start(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: CelulaServidor <PORT>");
        }
        // TODO
        // int port = Integer.parseInt(args[0]);
        int port = Utils.getRandomNodePort();
        int delay = 5_000;
        Thread.sleep(delay);


        // preparation for identification with node
        Socket socket;
        do {
            try {
             socket = new Socket(HOST, port);
             break;
            } catch (ConnectException e) {
                e.printStackTrace();
                Thread.sleep(delay);
            }
        } while(true);

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // identification between cell and node
        Message identMsg = Message.buildIdentify(ProgramType.SERVER, identifier, ProgramType.NODE);
        DecoderEncoder.writeMsg(out, identMsg);
        Message nodeIdentMsg = DecoderEncoder.readMsg(in);

        if (nodeIdentMsg.getNumServicio() != ServiceNumber.Identification) {
            System.err.println("Número de servicio incorrecto, primer mensaje debió ser identificación: " + nodeIdentMsg.getNumServicio().toString());
            System.exit(1);
        }
        if (DecoderEncoder.processIdentification(nodeIdentMsg) != ProgramType.NODE) {
            System.err.println("Conexión a identidad distinta a 'nodo'");
            System.exit(1);
        }
        System.out.println("Conectado exitosamente a: " + HOST + ":" + port);

        while (true) {
            Message req = DecoderEncoder.readMsg(in);

            System.out.println("Recibiendo msj: \n" + req);
            if (req.getNumServicio().isRequestToServer()) {
                int res = DecoderEncoder.processRequest(req);
                Message respMsg = Message.buildResult(identifier, res, req.getHash());
                DecoderEncoder.writeMsg(out, respMsg);
                System.out.println("Respondiendo con res: \n" + respMsg);
                System.out.println("!!!!");
            }
        }

    }

}