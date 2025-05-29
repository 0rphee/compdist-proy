package org.example;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class Message {
    /*
    Estructura del Mensaje:
    - Encabezado
        - Destinatario (ProgramType): 2 bytes. (0=nodo, 1=servidor, 2=solicitante)
        - Huella (senderIdentifier): 8 bytes. Identificador único del remitente.
        - No. de servicio (ServiceNumber): 2 bytes. (0=ident, 1=suma, ..., 5=printRes, 99=ack)
    - Cuerpo
        - Longitud de hash: 2 bytes.
        - Hash de evento/información: (variable). Hash de `informacion`.
        - Longitud de información de servicio: 4 bytes.
        - Información de servicio: (variable). Contenido específico del mensaje.
     */
    // ======================================= CAMPOS =======================================
    private final ProgramType receiver;       // Destinatario del mensaje.
    private final byte[] senderIdentifier;    // Quién envía el mensaje.
    private final ServiceNumber numServicio;  // Qué tipo de servicio/acción representa el mensaje.
    // Hash de `informacion`. Usado para identificar unívocamente el contenido del mensaje,
    // o para referenciar un mensaje original en respuestas o ACKs.
    private final byte[] hash;
    // Contenido específico del mensaje (ej: operandos, resultado, tipo de programa en identificación).
    private final byte[] informacion;

    // ======================================= GETTERS =======================================
    public ProgramType getReceiver() {
        return receiver;
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getSenderIdentifier() {
        return senderIdentifier;
    }

    public ServiceNumber getNumServicio() {
        return numServicio;
    }

    public byte[] getInformacion() {
        return informacion;
    }

    public Message(ProgramType receiver, byte[] senderIdentifier, ServiceNumber numServicio, byte[] hash, byte[] informacion) {
        this.receiver = receiver;
        this.senderIdentifier = senderIdentifier;
        this.numServicio = numServicio;
        this.hash = hash;
        this.informacion = informacion;
    }

    // Construye un mensaje de identificación.
    // `informacion` contiene el ProgramType del remitente.
    public static Message buildIdentify(ProgramType thisProgramType, byte[] senderIdentifier, ProgramType receiver) throws IOException {
        byte[] infoArr = new byte[]{thisProgramType.toByte()}; // Información es el tipo de programa del emisor.
        return new Message(receiver, senderIdentifier, ServiceNumber.Identification, Utils.sha256(infoArr), infoArr);
    }

    // Construye un mensaje de solicitud de operación.
    // `informacion` contiene los dos operandos enteros.
    public static Message buildRequest(byte[] senderIdentifier, OperationType operand, int n1, int n2) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(8); // Pre-aloja para dos enteros (4 bytes cada uno).
        try (DataOutputStream dataStream = new DataOutputStream(byteStream)) {
            dataStream.writeInt(n1);
            dataStream.writeInt(n2);
            dataStream.flush();
        }
        byte[] infoArr = byteStream.toByteArray();
        ServiceNumber serviceNumber = switch (operand) { // Convierte OperationType a ServiceNumber.
            case ADD -> ServiceNumber.Addition;
            case SUB -> ServiceNumber.Subtraction;
            case MUL -> ServiceNumber.Multiplication;
            case DIV -> ServiceNumber.Division;
        };
        return new Message(ProgramType.SERVER, senderIdentifier, serviceNumber, Utils.sha256(infoArr), infoArr);
    }

    // Construye un mensaje de resultado.
    // `informacion` contiene el hash de la solicitud original y el resultado de la operación.
    public static Message buildResult(byte[] senderIdentifier, int res, byte[] requestHash) throws IOException {
        // Estima tamaño: short para longitud de hash (2) + longitud de hash + int para resultado (4).
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(2 + requestHash.length + 4);
        try (DataOutputStream dataStream = new DataOutputStream(byteStream)) {
            dataStream.writeShort(requestHash.length); // Longitud del hash de la solicitud original.
            dataStream.write(requestHash);             // Hash de la solicitud original.
            dataStream.writeInt(res);                  // Resultado numérico.
            dataStream.flush();
        }
        byte[] infoArr = byteStream.toByteArray();
        return new Message(ProgramType.SOLICITANT, senderIdentifier, ServiceNumber.PrintResult, Utils.sha256(infoArr), infoArr);
    }

    // Construye un mensaje de Acuse de Recibo (Ack).
    // `informacion` contiene el hash del mensaje original que se está reconociendo.
    public static Message buildAck(ProgramType receiver, byte[] senderIdentifier, byte[] eventoOriginalHash) throws IOException {
        // Estima tamaño: short para longitud de hash (2) + longitud de hash.
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(2 + eventoOriginalHash.length);
        try (DataOutputStream dataStream = new DataOutputStream(byteStream)) {
            dataStream.writeShort(eventoOriginalHash.length); // Longitud del hash del mensaje original.
            dataStream.write(eventoOriginalHash);             // Hash del mensaje original.
        }
        byte[] infoArr = byteStream.toByteArray();
        // El hash de este mensaje ACK se calcula sobre `infoArr` (que es el hash del mensaje original).
        return new Message(receiver, senderIdentifier, ServiceNumber.Ack, Utils.sha256(infoArr), infoArr);
    }

    public String toString() {
        return String.format("Message { receiver: %s; senderIdentifier: %s; numServicio: %s; hash: %s; informacion %s}",
                this.receiver, Utils.byteArrayToHexString(this.senderIdentifier), this.numServicio, Utils.byteArrayToHexString(this.hash), Arrays.toString(this.informacion));
    }
}