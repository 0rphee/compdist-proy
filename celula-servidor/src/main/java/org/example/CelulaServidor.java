package org.example;

import java.io.*;
import java.net.Socket;

public class CelulaServidor {
    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 31010;

        try (Socket socket = new Socket(host, puerto)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            in.lines().forEach((str) -> System.out.println("Mensaje del Nodo: " + str));

            /*
            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                System.out.println("Mensaje del Nodo: " + mensaje);
            }
            */
            System.out.println("Nodo desconectado, cerrando Cliente.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}