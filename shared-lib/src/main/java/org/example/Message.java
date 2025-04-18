package org.example;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public class Message {
    /*
    - Evento
        - No. de servicio: 2 bytes
        - Longitud de evento: 2 bytes
        - Hash de evento: variable
    - Información del mensaje
        - Longitud del mensaje: 4 bytes
        - Mensaje: variable

    - 3 tipos de eventos:
        - Identificación (0)
            - Mensaje inicial que se envía para identificar un Nodo o una Célula en la red. Este
            mensaje puede incluir información como el tipo de entidad (Nodo o Célula).
        - Solicitud (1)
            - Mensaje que se envía desde la Célula-Solicitante con una operación aritmética
            (suma, resta, multiplicación o división) que debe ser procesada por una Célula-
            Servidor. Este mensaje debe incluir la operación y los operandos.
        - Resultado (2)
            - Mensaje enviado por la Célula-Servidor con el resultado de la operación
            solicitada. Este mensaje es procesado solo por la Célula-Solicitante que hizo la
            solicitud, conteniendo el resultado de la operación aritmética.
     */

    // used to facilitate operations regarding the type of the entity/program in the identification phase
    public enum ProgramType {
        NODE() {
            @Override
            public String toSingleString() {
                return "N";
            }
        },
        SERVER() {
            @Override
            public String toSingleString() {
                return "S";
            }
        },
        SOLICITANT() {
            @Override
            public String toSingleString() {
                return "O";
            }
        };

        public abstract String toSingleString();

        public static Optional<ProgramType> fromString(String str) {
            return switch (str) {
                case "N" -> Optional.of(ProgramType.NODE);
                case "S" -> Optional.of(ProgramType.SERVER);
                case "O" -> Optional.of(ProgramType.SOLICITANT);
                default -> Optional.empty();
            };
        }

        public String toString() {
            return switch (this) {
                case NODE -> "Node";
                case SERVER -> "Server";
                case SOLICITANT -> "Solicitant";
            };
        }
    }

    // used to facilitate operations regarding the service number of each message (ex. serializing/deserializing)
    public enum ServiceNumber {
        Identification,
        Request,
        Result;

        public short toShort() {
            return switch (this) {
                case ServiceNumber.Identification -> 0;
                case ServiceNumber.Request -> 1;
                case ServiceNumber.Result -> 2;
            };
        }

        public static Optional<ServiceNumber> fromShort(short number) {
            return switch (number) {
                case 0 -> Optional.of(ServiceNumber.Identification);
                case 1 -> Optional.of(ServiceNumber.Request);
                case 2 -> Optional.of(ServiceNumber.Result);
                default -> Optional.empty();
            };
        }

        public String toString() {
            return switch (this) {
                case ServiceNumber.Identification -> "Identification (0)";
                case ServiceNumber.Request -> "Request (1)";
                case ServiceNumber.Result -> "Result (2)";
            };
        }
    }

    public enum OperationType {
        ADD,
        SUB,
        MUL,
        DIV;

        public static Optional<OperationType> fromString(String str) {
            return switch (str) {
                case "+" -> Optional.of(OperationType.ADD);
                case "-" -> Optional.of(OperationType.SUB);
                case "*" -> Optional.of(OperationType.MUL);
                case "/" -> Optional.of(OperationType.DIV);
                default -> Optional.empty();
            };
        }

        public String toString() {
            return switch (this) {
                case OperationType.ADD -> "+";
                case OperationType.SUB -> "-";
                case OperationType.MUL -> "*";
                case OperationType.DIV -> "/";
            };
        }
    }

    // número de servicio de solicitud: Identification, Request, Result
    private ServiceNumber numServicio;
    // hash del mensaje original (solicitud), usado para identificar respuestas
    private byte[] hashMsg;
    // información del mensaje (mensaje en sí) en JSON
    // Identification: tipo de conexión (nodo, servidor, solicitante)
    // Request: operador y operandos
    // Result: resultado
    private byte[] informacion;

    public byte[] getHashMsg() {
        return hashMsg;
    }

    public void setHashMsg(byte[] hashMsg) {
        this.hashMsg = hashMsg;
    }

    public ServiceNumber getNumServicio() {
        return numServicio;
    }

    public void setNumServicio(ServiceNumber numServicio) {
        this.numServicio = numServicio;
    }

    public byte[] getInformacion() {
        return informacion;
    }

    public void setInformacion(byte[] informacion) {
        this.informacion = informacion;
    }

    public Message(ServiceNumber numServicio, byte[] hashMsg, byte[] informacion) {
        this.numServicio = numServicio;
        this.hashMsg = hashMsg;
        this.informacion = informacion;
    }

    public static Message buildIdentify(ProgramType thisProgramType) {
        JSONObject json = new JSONObject();
        String jsonString = json.put("t", thisProgramType.toSingleString()).toString();
        byte[] jsonArr = jsonString.getBytes(StandardCharsets.UTF_8);
        return new Message(ServiceNumber.Identification, DecoderEncoder.sha256(jsonArr), jsonArr);
    }

    public static Message buildRequest(OperationType operand, int n1, int n2) {
        JSONObject json = new JSONObject();
        json.put("op", operand.toString());
        json.put("n1", n1);
        json.put("n2", n2);
//        System.out.println("Request: " + json.toString());
        byte[] jsonString = json.toString().getBytes(StandardCharsets.UTF_8);
        return new Message(ServiceNumber.Request, DecoderEncoder.sha256(jsonString), jsonString);
    }

    public static Message buildResult(int res, byte[] requestHash) {
        JSONObject json = new JSONObject();
        json.put("res", res);
        return new Message(ServiceNumber.Result, requestHash, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    // general helper methods
    public String toString() {
        String s = "Message: " +
                " ServiceNumber: " + this.numServicio.toString() +
                " hashMsg: " + Arrays.toString(this.hashMsg);
        String b = " informacion: " + bytesToString(this.informacion);
        return s + b;
    }

    public static String bytesToString(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    static final int[] nodePorts = {31010, 31011, 31012, 31013};

    public static int getRandomNodePort() {
        Random random = new Random();
        int randomIndex = random.nextInt(Message.nodePorts.length);
        return nodePorts[randomIndex];
    }
}



