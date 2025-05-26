package org.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
}
