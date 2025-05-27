package org.example;

import org.javatuples.Pair;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DecoderEncoder {
    public static void writeMsg(DataOutputStream dos, Message msg) throws IOException {
        // 2 bytes destinatario
        dos.writeShort(msg.getReceiver().toShort());
        // 8 bytes huella identificación de quien envía
        dos.write(msg.getSenderIdentifier());
        // 2 bytes número de servicio
        dos.writeShort(msg.getNumServicio().toShort());
        byte[] bytesInfo = msg.getInformacion();
        byte[] bytesHash = msg.getHash();
        // 2 bytes tamaño hash
        dos.writeShort(bytesHash.length);
        // (variable) hash evento
        dos.write(bytesHash);
        // 4 bytes
        dos.writeInt(bytesInfo.length);
        // (variable) información/mensaje
        dos.write(bytesInfo);
    }

    public static Message readMsg(DataInputStream dis) throws IOException {
        // 2 bytes destinatario
        short destinatario = dis.readShort();
        // 8 bytes huella identificación de quien envía
        byte[] identifier = new byte[8];
        dis.readFully(identifier);
        // 2 bytes número de servicio
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

        return new Message(
                ProgramType.fromShort(destinatario).orElseThrow(
                        () -> new IllegalStateException("Unexpected receiver number: " + destinatario))
                , identifier
                , ServiceNumber.fromShort(numServicio).orElseThrow(
                () -> new IllegalStateException("Unexpected service number: " + numServicio))
                , hash
                , infoMsg);
    }

    public static int processRequest(Message msg) throws RuntimeException, IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msg.getInformacion()));
        int n1 = in.readInt();
        int n2 = in.readInt();
        return switch (msg.getNumServicio()) {
            case ServiceNumber.Addition:
                yield n1 + n2;
            case ServiceNumber.Subtraction:
                yield n1 - n2;
            case ServiceNumber.Multiplication:
                yield n1 * n2;
            case ServiceNumber.Division:
                if (n2 == 0)
                    throw new RuntimeException("Division by zero: " + n1 + "/" + n2);
                yield n1 / n2;
            default:
                throw new RuntimeException("Invalid service number for request.");
        };
    }

    public static Pair<byte[], Integer> processResult(Message msg) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(msg.getInformacion()));
        // 2 bytes
        short hashLen = dis.readShort();
        // variable
        byte[] requestEventHash = new byte[hashLen];
        dis.readFully(requestEventHash);
        // 4 bytes
        int res = dis.readInt();
        return new Pair<byte[], Integer>(requestEventHash, res);
    }

    public static ProgramType processIdentification(Message msg) {
        byte value = msg.getInformacion()[0];
        return ProgramType.fromByte(value).orElseThrow(
                () -> new RuntimeException("Invalid byte in identification message: " + value)
        );
    }

}
