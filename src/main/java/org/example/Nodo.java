package org.example;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Nodo {
    private static final int PUERTO = 31010;
    private static AtomicInteger clientIdCounter = new AtomicInteger(1);
    private static ConcurrentHashMap<Integer, Socket> clientes = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("Nodo iniciado en el puerto " + PUERTO);

            while (true) {
                Socket clienteSocket = serverSocket.accept();
                int clientId = clientIdCounter.getAndIncrement();
                clientes.put(clientId, clienteSocket);
                System.out.println("Cliente " + clientId + " conectado. Total clientes: " + clientes.size());
                new Thread(() -> manejarCliente(clientId, clienteSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void manejarCliente(int clientId, Socket clienteSocket) {
        try {
            PrintWriter out = new PrintWriter(clienteSocket.getOutputStream(), true);
            out.println("Bienvenido Cliente " + clientId);
            BufferedReader in = new BufferedReader(new InputStreamReader(clienteSocket.getInputStream()));

            while (in.readLine() != null) {
                // Esperar hasta que el cliente cierre la conexión
            }
            clienteSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clientes.remove(clientId);
            System.out.println("Cliente " + clientId + " desconectado. Total clientes: " + clientes.size());
        }
    }
}