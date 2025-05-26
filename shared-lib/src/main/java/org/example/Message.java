package org.example;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class Message {
    /*
    - Encabezado
        - Destinatario: 2 bytes. 0 - nodo, 1 - servidor, 2 - solicitante
        - Huella: 8 bytes. Identificador único de nodo/celula.
        - No. de servicio: 2 bytes. 0-ident, 1-suma, 2-resta, 3-mult, 4-div, 5-imprimir res, 99-ack
    - Cuerpo
        - Longitud de evento: 2 bytes.
        - Hash de evento: variable. Hash de la información de servicio.
        - Longitud de información de servicio: 4 bytes.
        - Información nde servicio: variable. Contenido del mensaje: identificador, operandos de operaciones, resultado, acuse.
     */
    // ======================================= CAMPOS =======================================
    // número de servicio de solicitud: Identification, Request, Result
    private final ProgramType receiver; // 2 bytes
    private final byte[] senderIdentifier; // 8 bytes
    private final ServiceNumber numServicio; // 2 bytes
    // hash del mensaje original (solicitud), usado para identificar respuestas
    // la longitud del hash se calcula a la hora de serializar
    private final byte[] hash; // longitud hash 2 bytes + hash variable
    // información del mensaje (mensaje en sí)
    // la longitud de la información se calcula a la hora de serializar
    private final byte[] informacion; // longitud de información 4 bytes + información variable

    // ======================================= CAMPOS =======================================
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

    public static Message buildIdentify(ProgramType thisProgramType, byte[] senderIdentifier, ProgramType receiver) throws IOException {
        byte[] infoArr = new byte[]{thisProgramType.toByte()};
        return new Message(receiver, senderIdentifier, ServiceNumber.Identification, Utils.sha256(infoArr), infoArr);
    }

    public static Message buildRequest(byte[] senderIdentifier, OperationType operand, int n1, int n2) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(8);
        {
            DataOutputStream dataStream = new DataOutputStream(byteStream);
            dataStream.writeInt(n1);
            dataStream.writeInt(n2);
            dataStream.flush();
        }
        byte[] infoArr = byteStream.toByteArray();
        ServiceNumber serviceNumber = switch (operand) {
            case ADD -> ServiceNumber.Addition;
            case SUB -> ServiceNumber.Subtraction;
            case MUL -> ServiceNumber.Multiplication;
            case DIV -> ServiceNumber.Division;
        };
        return new Message(ProgramType.SERVER, senderIdentifier, serviceNumber, Utils.sha256(infoArr), infoArr);
    }

    public static Message buildResult(byte[] senderIdentifier, int res, byte[] requestHash) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(2 + requestHash.length + 4);
        {
            DataOutputStream dataStream = new DataOutputStream(byteStream);
            dataStream.writeShort(requestHash.length);
            dataStream.write(requestHash);
            dataStream.writeInt(res);
            dataStream.flush();
        }
        byte[] infoArr = byteStream.toByteArray();
        return new Message(ProgramType.SOLICITANT, senderIdentifier, ServiceNumber.PrintResult, Utils.sha256(infoArr), infoArr);
    }

    public static Message buildAck(ProgramType receiver, byte[] senderIdentifier, byte[] eventoOriginalHash) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(2 + eventoOriginalHash.length);
        {
            DataOutputStream dataStream = new DataOutputStream(byteStream);
            dataStream.writeShort(eventoOriginalHash.length);
            dataStream.write(eventoOriginalHash);
            dataStream.flush();
        }
        byte[] infoArr = byteStream.toByteArray();
        return new Message(receiver, senderIdentifier, ServiceNumber.Ack, Utils.sha256(infoArr), infoArr);
    }

    public String toString() {
        return String.format("Message { receiver: %s; senderIdentifier: %s; numServicio: %s; hash: %s; informacion %s}", this.receiver, Arrays.toString(this.senderIdentifier), this.numServicio, Arrays.toString(this.hash), Arrays.toString(this.informacion));
    }

}



