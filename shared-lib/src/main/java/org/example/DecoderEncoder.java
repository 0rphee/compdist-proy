package org.example;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DecoderEncoder {
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

    public static void writeMsg(DataOutputStream dos, Message mensaje) throws IOException {
        // 2 bytes
        dos.writeShort(mensaje.getNumServicio().toShort());
        // 2 bytes
        byte[] bytesInfo = mensaje.getInformacion();
        byte[] bytesHash = mensaje.getHashMsg();
        dos.writeShort(bytesHash.length);
        // (variable) hash evento
        dos.write(bytesHash);
        // 4 bytes
        dos.writeInt(bytesInfo.length);
        // (variable) información/mensaje
        dos.write(bytesInfo);
    }

    public static Message readMsg(DataInputStream dis) throws IOException {
        // 2 bytes
        short numServicio = dis.readShort();
        // 2 bytes
        short longitudHash = dis.readShort();
        // (variable) hash
        byte[] hash = new byte[longitudHash];
        dis.readFully(hash);
        // 4 bytes
        int longitudInfo = dis.readInt();
        // (variable) información
        byte[] infoMsg = new byte[longitudInfo];
        dis.readFully(infoMsg);

        return new Message(Message.ServiceNumber.fromShort(numServicio), hash, infoMsg);
    }

    public static int processRequest(Message msg) throws RuntimeException {
        JSONObject json = new JSONObject(new String(msg.getInformacion()));
        String op = json.getString("op");
        int n1 = json.getInt("n1");
        int n2 = json.getInt("n2");
        return switch (Message.OperationType.fromString(op).orElseThrow(() -> new RuntimeException("Division by zero: " + n1 + "/" + n2))) {
            case Message.OperationType.ADD:
                yield n1 + n2;
            case Message.OperationType.SUB:
                yield n1 - n2;
            case Message.OperationType.MUL:
                yield n1 * n2;
            case Message.OperationType.DIV:
                if (n2 == 0)
                    throw new RuntimeException("Division by zero: " + n1 + "/" + n2);
                yield n1 / n2;
        };
    }

    public static int processResult(Message msg) {
        JSONObject json = new JSONObject(new String(msg.getInformacion()));
        return json.getInt("res");
    }

    public static Message.CellType processIdentification(Message msg) {
        JSONObject json = new JSONObject(new String(msg.getInformacion()));
        return Message.CellType.fromString(json.getString("t"));
    }

}
