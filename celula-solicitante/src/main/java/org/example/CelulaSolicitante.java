package org.example;

import java.io.*;
import java.net.Socket;

public class CelulaSolicitante {
    private static final String HOST = "localhost";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CelulaSolicitante <PORT>");
        }
        int port = Integer.parseInt(args[0]);

        try (Socket socket = new Socket(HOST, port)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                System.out.println("Mensaje del Nodo: " + mensaje);
            }

            System.out.println("Nodo desconectado, cerrando Cliente.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}