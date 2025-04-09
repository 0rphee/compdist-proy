package org.example;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    public enum CellType {
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

        public static CellType fromString(String str) {
            return switch (str) {
                case "N":
                    yield CellType.NODE;
                case "S":
                    yield CellType.SERVER;
                case "O":
                    yield CellType.SOLICITANT;
                default:
                    throw new RuntimeException("Invalid string in identification message: " + str);
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

        public static ServiceNumber fromShort(short number) {
            return switch (number) {
                case 0 -> ServiceNumber.Identification;
                case 1 -> ServiceNumber.Request;
                case 2 -> ServiceNumber.Result;
                default -> throw new IllegalStateException("Unexpected service number: " + number);
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

        public static OperationType fromString(String str) {
            return switch (str) {
                case "+":
                    yield OperationType.ADD;
                case "-":
                    yield OperationType.SUB;
                case "*":
                    yield OperationType.MUL;
                case "/":
                    yield OperationType.DIV;
                default:
                    throw new RuntimeException("Unrecognized operation: " + str);
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

    // número de servicio de solicitud
    private ServiceNumber numServicio;
    // hash del mensaje original (solicitud)
    private byte[] hashMsg;
    // información del mensaje (mensaje en sí)
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

    public static Message buildIdentify(CellType thisCellType) {
        JSONObject json = new JSONObject();
        String jsonString = json.put("t", thisCellType.toSingleString()).toString();
//        System.out.println("jsonstring"+ jsonString);
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
        return new Message(ServiceNumber.Result, requestHash, json.toString().getBytes());
    }

    //
//    private ServiceNumber numServicio;
//    // hash del mensaje original (solicitud)
//    private byte[] hashMsg;
//    // información del mensaje (mensaje en sí)
//    private byte[] informacion;
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



