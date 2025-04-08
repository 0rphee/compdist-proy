package org.example;

import org.json.JSONObject;

public class Message {
    /*
    - Evento
        - No. servicio: 2 bytes
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
            public byte[] toByteArray() {
                return new byte[]{'N'};
            }
        },
        SERVER() {
            @Override
            public byte[] toByteArray() {
                return new byte[]{'S'};
            }
        },
        SOLICITANT() {
            @Override
            public byte[] toByteArray() {
                return new byte[]{'O'};
            }
        };

        public abstract byte[] toByteArray();

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
    }

    // número de servicio de solicitud
    private short numServicio;
    // contenido del evento
    private String datosEvento;
    // infromación del mensaje (mensaje en sí)
    private byte[] informacion;

    public short getNumServicio() {
        return numServicio;
    }

    public void setNumServicio(short numServicio) {
        this.numServicio = numServicio;
    }

    public String getDatosEvento() {
        return datosEvento;
    }

    public void setDatosEvento(String datosEvento) {
        this.datosEvento = datosEvento;
    }

    public byte[] getInformacion() {
        return informacion;
    }

    public void setInformacion(byte[] informacion) {
        this.informacion = informacion;
    }

    public static Message buildIdentify(CellType thisCellType) {
        return new Message((short) 0, "TODO", thisCellType.toByteArray());
    }

    public Message(short numServicio, String datosEvento, byte[] informacion) {
        this.numServicio = numServicio;
        this.datosEvento = datosEvento;
        this.informacion = informacion;
    }

    public static Message buildRequest(String operand, int n1, int n2) {
        JSONObject json = new JSONObject();
        json.put("op", operand);
        json.put("n1", n1);
        json.put("n2", n2);
        return new Message((short) 1, "TODO", json.toString().getBytes());
    }

    public static  Message buildResult(int res) {
        return new Message((short) 2, "TODO", new JSONObject(res).toString().getBytes());
    }

}
