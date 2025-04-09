package org.example;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class CelulaSolicitante {
    private static final String HOST = "localhost";

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length < 1) {
            System.err.println("Usage: CelulaSolicitante <PORT>");
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

        try (Scanner scanner = new Scanner(System.in)) {
            boolean print_msg = true;
            while (true) {
                if (print_msg) {
                    System.out.print("Tipo de operación (+|-|*|/): ");
                    System.out.flush();
                } else {
                    print_msg = true;
                }

                String opStr = scanner.nextLine();
                if (opStr.isEmpty()) {
                    print_msg = false;
                    continue;
                }
                Message.OperationType op = Message.OperationType.fromString(opStr);

                System.out.print("Operando 1: ");
                System.out.flush();
                int n1 = scanner.nextInt();

                System.out.print("Operando 2: ");
                System.out.flush();
                int n2 = scanner.nextInt();

                if (op == Message.OperationType.DIV && n2 == 0) {
                    System.out.println("División entre 0 no permitida");
                    continue;
                }

                Message newReq = Message.buildRequest(op, n1, n2);
                DecoderEncoder.escribir(out, newReq);
                System.out.println("Mandando solicitud");
                // System.out.println(newReq.toString());

                do {
                    Message response = DecoderEncoder.leer(in);
                    // System.out.println(response.toString());
                    if ((response.getNumServicio() == Message.ServiceNumber.Result) && Arrays.equals(response.getHashMsg(), newReq.getHashMsg())) {
                        System.out.println("Respuesta: " + response);
                    }
                } while (true);

            }
        }
    }

}
