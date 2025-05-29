package org.example;

import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;
import java.util.Set;

public class Utils {
    public static byte[] createIdentifier(String hostname, int port) {
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

    public static Pair<String, Integer> getRandomNodePort(Set<Pair<String, Integer>> nodePorts) {
        Object[] nodePortsArr = nodePorts.toArray();
        Random random = new Random();
        int randomIndex = random.nextInt(nodePortsArr.length);
        return (Pair<String,Integer>) nodePortsArr[randomIndex];
    }

    public static String byteArrayToHexString(byte[] byteArray) {
        HexFormat hex = HexFormat.of();
        return hex.formatHex(byteArray);
    }

    public static Socket cellTryToCreateSocket(String host, int nodePort, int delay, Logger LOGGER) throws InterruptedException {
        Socket socket;
        int tryCount = 1;
        do {
            LOGGER.info("Intento {} de crear socket", tryCount);
            try {
                socket = new Socket(host, nodePort);
                break;
            } catch (ConnectException e) {
                tryCount += 1;
                LOGGER.error(e);
                LOGGER.error("Espera de {}ms", delay);
                Thread.sleep(delay);
            } catch (IOException e) {
                LOGGER.fatal(e);
                throw new RuntimeException(e);
            }
        } while (true);
        return socket;
    }
}
