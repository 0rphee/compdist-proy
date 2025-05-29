package org.example;

import org.javatuples.Pair;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// Clase para serializar (escribir) y deserializar (leer) mensajes.
public class DecoderEncoder {
    // Escribe un objeto Message en un DataOutputStream según un formato definido.
    public static void writeMsg(DataOutputStream dos, Message msg) throws IOException {
        // 2 bytes: Destinatario (como short).
        dos.writeShort(msg.getReceiver().toShort());
        // 8 bytes: Identificador del remitente.
        dos.write(msg.getSenderIdentifier());
        // 2 bytes: Número de servicio (como short).
        dos.writeShort(msg.getNumServicio().toShort());
        byte[] bytesInfo = msg.getInformacion();
        byte[] bytesHash = msg.getHash();
        // 2 bytes: Longitud del hash del evento/información.
        dos.writeShort(bytesHash.length);
        // (variable): Bytes del hash.
        dos.write(bytesHash);
        // 4 bytes: Longitud de la información de servicio.
        dos.writeInt(bytesInfo.length);
        // (variable): Bytes de la información de servicio.
        dos.write(bytesInfo);
    }

    // Lee desde un DataInputStream y reconstruye un objeto Message.
    public static Message readMsg(DataInputStream dis) throws IOException {
        // Lee los campos en el mismo orden y tipo en que fueron escritos.
        // 2 bytes: Destinatario.
        short destinatario = dis.readShort();
        // 8 bytes: Identificador del remitente.
        byte[] identifier = new byte[8];
        dis.readFully(identifier); // Asegura leer exactamente 8 bytes.
        // 2 bytes: Número de servicio.
        short numServicio = dis.readShort();
        // 2 bytes: Longitud del hash.
        short longitudHash = dis.readShort();
        // (variable): Bytes del hash.
        byte[] hash = new byte[longitudHash];
        dis.readFully(hash);
        // 4 bytes: Longitud de la información.
        int longitudInfo = dis.readInt();
        // (variable): Bytes de la información.
        byte[] infoMsg = new byte[longitudInfo];
        dis.readFully(infoMsg);

        return new Message(
                ProgramType.fromShort(destinatario).orElseThrow( // Convierte short a ProgramType.
                        () -> new IllegalStateException("Unexpected receiver number: " + destinatario)),
                identifier,
                ServiceNumber.fromShort(numServicio).orElseThrow( // Convierte short a ServiceNumber.
                        () -> new IllegalStateException("Unexpected service number: " + numServicio)),
                hash,
                infoMsg);
    }

    // Procesa un mensaje de tipo solicitud (operación aritmética).
    // Extrae los operandos de msg.getInformacion() y realiza la operación.
    public static int processRequest(Message msg) throws RuntimeException, IOException {
        // Usa ByteArrayInputStream para leer desde el array de bytes de información.
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(msg.getInformacion()));
        int n1 = in.readInt(); // Lee el primer operando (4 bytes).
        int n2 = in.readInt(); // Lee el segundo operando (4 bytes).
        return switch (msg.getNumServicio()) { // Determina la operación según el número de servicio.
            case ServiceNumber.Addition -> n1 + n2;
            case ServiceNumber.Subtraction -> n1 - n2;
            case ServiceNumber.Multiplication -> n1 * n2;
            case ServiceNumber.Division -> {
                if (n2 == 0)
                    throw new RuntimeException("División por cero: " + n1 + "/" + n2);
                yield n1 / n2;
            }
            default -> throw new RuntimeException("Invalid service number for request.");
        };
    }

    // Procesa un mensaje de tipo resultado.
    // Extrae el hash de la solicitud original y el resultado numérico.
    public static Pair<byte[], Integer> processResult(Message msg) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(msg.getInformacion()));
        // 2 bytes: Longitud del hash de la solicitud original.
        short hashLen = dis.readShort();
        // (variable): Bytes del hash de la solicitud original.
        byte[] requestEventHash = new byte[hashLen];
        dis.readFully(requestEventHash);
        // 4 bytes: Resultado de la operación.
        int res = dis.readInt();
        return new Pair<>(requestEventHash, res); // Devuelve el hash y el resultado.
    }

    // Procesa un mensaje de tipo identificación.
    // Extrae el ProgramType de la entidad que se identifica.
    public static ProgramType processIdentification(Message msg) {
        // La información es un solo byte que representa el ProgramType.
        byte value = msg.getInformacion()[0];
        return ProgramType.fromByte(value).orElseThrow(
                () -> new RuntimeException("Invalid byte in identification message: " + value)
        );
    }

    // Procesa un mensaje de tipo Ack.
    // Extrae el hash del mensaje original que está siendo reconocido.
    public static byte[] processAck(Message msg) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(msg.getInformacion()));
        // 2 bytes: Longitud del hash del evento original.
        short hashLen = dis.readShort();
        // (variable): Bytes del hash del evento original.
        byte[] hash = new byte[hashLen];
        dis.readFully(hash);
        return hash;
    }
}