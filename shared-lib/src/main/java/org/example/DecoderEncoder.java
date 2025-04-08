package org.example;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DecoderEncoder {
    public static void escribir(DataOutputStream dos, Message mensaje) throws IOException {
        // 2 bytes
        dos.writeShort(mensaje.getNumServicio());
        byte[] bytesEvento = mensaje.getDatosEvento().getBytes();
        // 2 bytes
        dos.writeShort(bytesEvento.length);
        // (variable) evento
        dos.write(bytesEvento);
        byte[] bytesInfo = mensaje.getInformacion();
        // 4 bytes
        dos.writeInt(bytesInfo.length);
        // (variable) información
        dos.write(bytesInfo);
    }

    public static Message leer(DataInputStream dis) throws IOException {
        // 2 bytes
        short numServicio = dis.readShort();
        // 2 bytes
        short longitudEvento = dis.readShort();
        // (variable) evento
        byte[] evento = new byte[longitudEvento];
        dis.readFully(evento);
        // 4 bytes
        int longitudInfo = dis.readShort();
        // (variable) información
        byte[] info = new byte[longitudInfo];
        dis.readFully(info);

        return new Message(numServicio, new String(evento), info);
    }

    public int processRequest(Message msg) throws RuntimeException {
        JSONObject json = new JSONObject(new String(msg.getInformacion()));
        String op = json.getString("op");
        int n1 = json.getInt("n1");
        int n2 = json.getInt("n2");
        return switch (op) {
            case "+":
                yield n1 + n2;
            case "-":
                yield n1 - n2;
            case "*":
                yield n1 * n2;
            case "/":
                if (n2 == 0)
                    throw new RuntimeException("Division by zero: " + n1 + "/" + n2);
                yield n1 / n2;
            default:
                throw new RuntimeException("Unrecognized operation: " + op);
        };
    }

    public int processResult(Message msg) throws RuntimeException {
        JSONObject json = new JSONObject(new String(msg.getInformacion()));
        return json.getInt("res");
    }

    public Message.CellType processIdentification(Message msg) {
        JSONObject json = new JSONObject(new String(msg.getInformacion()));
        return Message.CellType.fromString(json.getString("type"));
    }
}
