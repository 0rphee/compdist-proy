package org.example;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

public class Utils {
    public static int[] nodePorts = {31010, 31011, 31012, 31013};

    public static byte[] createIdentifier(String hostname, int port)  {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        byte[] hash = sha256(hostname.getBytes());
        // Tomar 6 bytes
        byte[] id = new byte[8];
        System.arraycopy(hash, 0, id, 0, 6);
        // añadir el puerto
        id[6] = (byte) ((port >> 8) & 0xFF);
        id[7] = (byte) (port & 0xFF);
        return id;
    }
    public static byte[] sha256(byte[] bytesToHash) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // should never happen
            throw new RuntimeException(e);
        }
        return digest.digest(bytesToHash);
    }

    public static int getRandomNodePort() {
        Random random = new Random();
        int randomIndex = random.nextInt(Utils.nodePorts.length);
        return nodePorts[randomIndex];
    }

    public static String bytesToString(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
    public static String byteArrayToHexString(byte[] byteArray) {
        HexFormat hex = HexFormat.of();
        return hex.formatHex(byteArray);
    }
    public static Socket cellTryToCreateSocket(String HOST, int nodePort, int delay) throws InterruptedException {
        Socket socket;
        int tryCount = 1;
        do {
            System.out.printf("Intento %d de crear socket\n", tryCount);
            try {
                socket = new Socket(HOST, nodePort);
                break;
            } catch (ConnectException e) {
                tryCount += 1;
                e.printStackTrace();
                System.out.println("Espera de " + delay + "ms");
                Thread.sleep(delay);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } while (true);
        return socket;
    }
}
