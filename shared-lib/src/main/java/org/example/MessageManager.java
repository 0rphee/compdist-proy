package org.example;

import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Clase abstracta para gestionar la lógica de envío y recepción de mensajes.
public abstract class MessageManager {
    protected final org.apache.logging.log4j.Logger LOGGER;
    // Almacena mensajes enviados que están esperando un ACK (confirmación de recepción).
    private static final Set<Message> waitingForAckMsgs = ConcurrentHashMap.newKeySet();

    // Colas de mensajes pendientes de ser enviados, organizadas por tipo de servicio.
    protected final Map<ServiceNumber, LinkedHashSet<Message>> sendingQueues;
    // Máximo número de Acks pendientes antes de que el despachador priorice reenviar mensajes no confirmados.
    protected final int MAX_PENDING_ACKS;
    // Tiempo de espera (en milisegundos) para el bucle del despachador.
    protected final int WAIT_MILIS;

    MessageManager(Set<ServiceNumber> serviceNumbers, org.apache.logging.log4j.Logger logger, int maxPendingAcks, int waitMilis) {
        this.LOGGER = logger;
        this.MAX_PENDING_ACKS = maxPendingAcks;
        this.WAIT_MILIS = waitMilis;
        this.sendingQueues = new ConcurrentHashMap<>();
        // Inicializa una cola para cada tipo de servicio que este manager manejará.
        for (ServiceNumber serviceNumber : serviceNumbers) {
            this.sendingQueues.put(serviceNumber, new LinkedHashSet<>()); // LinkedHashSet para mantener orden de inserción y evitar duplicados.
        }
    }

    // Añade un mensaje a la lista de espera de ACK.
    public void addMsgToWaitingForAckList(Message msg) {
        waitingForAckMsgs.add(msg);
    }

    // Reenvía todos los mensajes que están esperando ACK.
    public void sendMessagesWaitingForAck(DataOutputStream outStream) throws IOException {
        for (Message msg : waitingForAckMsgs) {
            DecoderEncoder.writeMsg(outStream, msg);
        }
    }

    // Registra la recepción de un ACK, eliminando el mensaje correspondiente de la lista de espera.
    public void registerAck(byte[] originalMsgHash) {
        waitingForAckMsgs.removeIf((msg) -> {
            if (Arrays.equals(msg.getHash(), originalMsgHash)) {
                LOGGER.debug("Mensaje eliminado de espera de Ack ({})", Utils.byteArrayToHexString(originalMsgHash));
                return true; // Condición para eliminar.
            }
            return false;
        });
    }

    // Método para debugging.
    public void printWaitingForAckMsgState() {
        System.out.println("Estado de waitingForAckMsgs: {");
        for (Message msg : waitingForAckMsgs) {
            System.out.println(" - " + Utils.byteArrayToHexString(msg.getHash()));
        }
        System.out.println("}");
    }

    // Método para debugging.
    public void printDispatchQueuesState() {
        System.out.println("Estado de dispatchQueues: {");
        for (Map.Entry<ServiceNumber, LinkedHashSet<Message>> entry : sendingQueues.entrySet()) {
            System.out.println("  Estado:" + entry.getKey() + " {");
            for (Message msg : entry.getValue()) {
                System.out.println("    - " + Utils.byteArrayToHexString(msg.getHash()));
            }
            System.out.println("  }");
        }
        System.out.println("}");
    }

    // Añade un mensaje a la cola de despacho correspondiente a su tipo de servicio.
    public void addMsgToDispatchQueue(Message msg) {
        LinkedHashSet<Message> queue = sendingQueues.get(msg.getNumServicio());
        if (queue != null && !queue.contains(msg)) { // Evita duplicados en la cola.
            queue.addLast(msg); // Añade al final de la cola.
        } else if (queue == null) {
            LOGGER.warn("No hay lista de despacho para este servicio: {}", msg.getNumServicio());
        }
    }

    // Bucle principal del hilo despachador (implementación específica en subclases).
    public abstract void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream);

    // Bucle principal del hilo receptor (implementación específica en subclases).
    public abstract void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Function<String, Void> showResult);

    // Implementación del MessageManager para el Servidor (CelulaServidor).
    public static final class ServerMessageManager extends MessageManager {
        ServerMessageManager(Logger logger, int maxPendingAcks, int waitMilis) {
            // El servidor principalmente despacha mensajes de PrintResult.
            super(Set.of(ServiceNumber.PrintResult), logger, maxPendingAcks, waitMilis);
        }

        @Override
        public void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream) {
            while (true) {
                // Si hay demasiados Acks pendientes, prioriza reenviar esos mensajes.
                if (waitingForAckMsgs.size() >= this.MAX_PENDING_ACKS) {
                    try {
                        this.sendMessagesWaitingForAck(outStream);
                        Thread.sleep(this.WAIT_MILIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Hilo de despacho interrumpido durante la espera.");
                        return;
                    } catch (IOException e) {
                        LOGGER.error("Error en hilo de despacho ({}) al enviar resultado: ", Utils.byteArrayToHexString(cellIdentifier), e.getMessage());
                        System.exit(1);
                    }
                    continue;
                }

                // Si no hay muchos Ack's pendientes, procesa las colas de envío normales.
                for (Map.Entry<ServiceNumber, LinkedHashSet<Message>> entry : this.sendingQueues.entrySet()) {
                    ServiceNumber serviceNumber = entry.getKey();
                    LinkedHashSet<Message> queue = entry.getValue();
                    if (queue.isEmpty()) continue;

                    Message nextMsgToSend = queue.removeFirst(); // Obtiene y remueve el primer mensaje.
                    LOGGER.info("Despachando mensaje: {} ({})", serviceNumber, Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                    if (serviceNumber == ServiceNumber.PrintResult) {
                        try {
                            DecoderEncoder.writeMsg(outStream, nextMsgToSend);
                            LOGGER.info("Respondiendo con resultado para: {}", Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                            this.addMsgToWaitingForAckList(nextMsgToSend); // Añade este resultado a la lista de espera de Acks
                            LOGGER.info("Mensaje de resultadoo añadido a lista de espera de Acks ({})", Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                        } catch (IOException e) {
                            LOGGER.fatal("Error en hilo de despacho del servidor ({}) al enviar resultado: {}", Utils.byteArrayToHexString(cellIdentifier), e.getMessage());
                            System.exit(1);
                        }
                    } else {
                        LOGGER.info("OTRO MENSAJE (inesperado en Servidor): {}, no se hizo nada.", serviceNumber);
                    }
                }
                try {
                    Thread.sleep(this.WAIT_MILIS);
                } catch (InterruptedException e) { /* ... */ }
            }
        }

        @Override
        public void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Function<String, Void> showResult) {
            while (true) {
                try {
                    Message req = DecoderEncoder.readMsg(socketInStream);
                    LOGGER.info("Recibiendo msj: {} ({})", req.getNumServicio(), Utils.byteArrayToHexString(req.getHash()));

                    switch (req.getNumServicio()) {
                        case Addition, Subtraction, Multiplication, Division: // Si es una solicitud de operación
                            // Envía ACK inmediatamente.
                            Message ackMsg = Message.buildAck(ProgramType.SOLICITANT, cellIdentifier, req.getHash());
                            DecoderEncoder.writeMsg(socketOutStream, ackMsg);
                            LOGGER.info("Enviando Ack de request original con hash: {}", Utils.byteArrayToHexString(req.getHash()));

                            // Procesa la solicitud y construye el mensaje de resultado.
                            int res = DecoderEncoder.processRequest(req);
                            Message responseMsg = Message.buildResult(cellIdentifier, res, req.getHash());
                            // Add message to dispatch queue
                            this.addMsgToDispatchQueue(responseMsg);
                            LOGGER.info("Mensaje de respuesta añadido a fila de envío: {}", Utils.byteArrayToHexString(req.getHash()));
                            break;
                        case Ack:
                            byte[] originalMsgHash = DecoderEncoder.processAck(req);
                            LOGGER.info("Recibido Ack con contenido: {}", Utils.byteArrayToHexString(originalMsgHash));
                            this.registerAck(originalMsgHash);
                            break;
                        case Identification:
                            LOGGER.info("Recibida identificación de: ", DecoderEncoder.processIdentification(req));
                            break;
                        case PrintResult:
                            break;
                    }
                } catch (IOException e) {
                    LOGGER.fatal("Error en hilo de recepción: {}", e.getMessage());
                    System.exit(1);
                    break;
                }
            }
        }
    }

    // Implementación del MessageManager para el Cliente (CelulaSolicitante).
    public static final class ClientMessageManager extends MessageManager {
        // Almacena hashes de las solicitudes enviadas por el cliente, para las cuales se espera un resultado.
        // Se usa ByteBuffer porque byte[] no funciona bien como clave en Set/Map directamente (compara referencias, no contenido).
        private static final Set<ByteBuffer> lastMsgsToWaitResult = ConcurrentHashMap.newKeySet();

        ClientMessageManager(org.apache.logging.log4j.Logger logger, int maxPendingAcks, int waitMilis) {
            // El cliente despacha solicitudes de operaciones.
            super(Set.of(ServiceNumber.Addition, ServiceNumber.Subtraction, ServiceNumber.Multiplication, ServiceNumber.Division), logger, maxPendingAcks, waitMilis);
        }

        // Añade el hash de una solicitud enviada a la lista de espera de resultados.
        public void addMsgHashToWaitResultSet(byte[] originalMsgHash) {
            LOGGER.debug("Hash de solicitud almacenado para esperar resultado: {}", Utils.byteArrayToHexString(originalMsgHash));
            // Envuelve el byte[] en ByteBuffer para asegurar que las comparaciones de pertenencia en el
            // set funcionen con respecto al contenido del hash.
            lastMsgsToWaitResult.add(ByteBuffer.wrap(originalMsgHash));
        }

        // Elimina un hash de la lista de espera de resultados (cuando el resultado llega).
        public void removeMsgHashToWaitResultSet(byte[] originalMsgHash) {
            lastMsgsToWaitResult.removeIf((hash) -> Arrays.equals(hash.array(), originalMsgHash));
        }

        // Método para debugging.
        public void printlastMsgsToWaitResultState() {
            System.out.println("Estado de lastMessagesToWaitResult: {");
            for (ByteBuffer hash : lastMsgsToWaitResult) {
                System.out.println(" - " + Utils.byteArrayToHexString(hash.array()));
            }
            System.out.println("}");
        }

        @Override
        public void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream) {
            while (true) {
                // Lógica similar al ServerMessageManager para manejar ACKs pendientes.
                if (waitingForAckMsgs.size() >= this.MAX_PENDING_ACKS) {
                    try {
                        this.sendMessagesWaitingForAck(outStream);
                        Thread.sleep(this.WAIT_MILIS);
                    } catch (InterruptedException | IOException e) { /* ... */
                        System.exit(1);
                        return;
                    }
                    continue;
                }

                // Envía solicitudes de operación desde las colas.
                for (Map.Entry<ServiceNumber, LinkedHashSet<Message>> entry : this.sendingQueues.entrySet()) {
                    ServiceNumber serviceNumber = entry.getKey();
                    LinkedHashSet<Message> queue = entry.getValue();
                    if (queue.isEmpty()) continue;

                    Message nextMsgToSend = queue.removeFirst();
                    LOGGER.info("Despachando {} ({})", serviceNumber, Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                    try {
                        DecoderEncoder.writeMsg(outStream, nextMsgToSend);
                        this.addMsgToWaitingForAckList(nextMsgToSend);
                        this.addMsgHashToWaitResultSet(nextMsgToSend.getHash());
                        LOGGER.debug("Mensaje añadido a lista de espera de Acks ({})", Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                    } catch (IOException e) {
                        LOGGER.fatal("Error en hilo de despacho ({}) al enviar resultado: {}", Utils.byteArrayToHexString(cellIdentifier), e.getMessage());
                        System.exit(1);
                    }
                }

                try {
                    Thread.sleep(this.WAIT_MILIS); // Small delay before checking queues again
                } catch (InterruptedException e) {
                    LOGGER.fatal("Hilo de despacho interrumpido durante la espera.");
                    Thread.currentThread().interrupt();
                    System.exit(1);
                    return;
                }
            }
        }

        @Override
        public void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Function<String, Void> showResult) {
            while (true) {
                try {
                    // Lee mensaje entrante.
                    Message req = DecoderEncoder.readMsg(socketInStream);
                    LOGGER.info("Recibiendo msj {} ({})", req.getNumServicio(), Utils.byteArrayToHexString(req.getHash()));
                    switch (req.getNumServicio()) {
                        case Addition, Subtraction, Multiplication, Division:
                            // El cliente no debería recibir solicitudes.
                            LOGGER.warn("Cliente recibió mensaje de solicitud inesperado: {}", req);
                            break;
                        case Ack:
                            // ACK recibido (probablemente por una solicitud que envió el cliente).
                            byte[] originalMsgHash = DecoderEncoder.processAck(req);
                            LOGGER.info("Recibido Ack por el mensaje con hash: {}", Utils.byteArrayToHexString(originalMsgHash));
                            this.registerAck(originalMsgHash);
                            break;
                        case Identification:
                            LOGGER.info("Recibida identificación de: {}", DecoderEncoder.processIdentification(req));
                            break;
                        case PrintResult:
                            // Responder con Ack
                            Pair<byte[], Integer> resPair = DecoderEncoder.processResult(req);
                            LOGGER.info("PrintResult hash acompañante: {}", Utils.byteArrayToHexString(resPair.getValue0()));
                            Message ackMsg = Message.buildAck(ProgramType.SERVER, cellIdentifier, req.getHash());
                            DecoderEncoder.writeMsg(socketOutStream, ackMsg);
                            LOGGER.info("Enviando Ack para el mensaje PrintResult con hash: {}", Utils.byteArrayToHexString(req.getHash()));

                            // Verifica si este resultado corresponde a una solicitud pendiente.
                            ByteBuffer requestHashByteBuffer = ByteBuffer.wrap(resPair.getValue0());
                            if (lastMsgsToWaitResult.contains(requestHashByteBuffer)) {
                                String resStr = resPair.getValue1().toString();
                                LOGGER.info("Resultado correspondiente a solicitud previa. Mostrando en UI: {}", resStr);
                                this.removeMsgHashToWaitResultSet(resPair.getValue0());
                                // Llama a la función para mostrar el resultado en la UI.
                                showResult.apply(resStr);
                            } else {
                                LOGGER.warn("Resultado recibido ({}) pero no se esperaba o ya fue procesado. Hash de solicitud original: {}",
                                        resPair.getValue1(), Utils.byteArrayToHexString(resPair.getValue0()));
                            }
                            break;
                    }
                } catch (IOException e) {
                    LOGGER.error("Error en hilo de recepción del servidor: {}", e.getMessage());
                    System.exit(1);
                }
            }
        }
    }
}

