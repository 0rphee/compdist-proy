package org.example;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class CelulaServidor {
    private static final String HOST = "localhost";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: CelulaServidor <PORT>");
        }
        // TODO
        // int port = Integer.parseInt(args[0]);
        int port = Message.getRandomNodePort();
        int delay = 5_000;
        Thread.sleep(delay);

        Socket socket = new Socket(HOST, port);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        Message ident = Message.buildIdentify(Message.CellType.SERVER);

        DecoderEncoder.escribir(out, ident);
        Message nodeIdentMsg = DecoderEncoder.leer(in);

        if (nodeIdentMsg.getNumServicio() != Message.ServiceNumber.Identification) {
            System.err.println("Número de servicio incorrecto, primer mensaje debió ser identificación: " + nodeIdentMsg.getNumServicio().toString());
            System.exit(1);
        }
        if (DecoderEncoder.processIdentification(nodeIdentMsg) != Message.CellType.NODE) {
            System.err.println("Conexión a identidad distinta a 'nodo'");
            System.exit(1);
        }
        System.out.println("Conectado exitosamente a: " + HOST + ":" + port);

        while (true) {
            Message req = DecoderEncoder.leer(in);

            System.out.println("Recibiendo msj: \n" + req.toString());
            if (req.getNumServicio() == Message.ServiceNumber.Request) {
                int res = DecoderEncoder.processRequest(req);
                Message respMsg = Message.buildResult(res, req.getHashMsg());
                DecoderEncoder.escribir(out, respMsg);
                System.out.println("Respondiendo con res: \n" + respMsg);
                System.out.println("!!!!");
            }
        }

    }

}