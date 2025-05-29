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

public abstract class MessageManager {
    // Set to store hashes of processed messages to prevent duplicate processing
    protected final org.apache.logging.log4j.Logger LOGGER;
    private static final Set<Message> waitingForAckMsgs = ConcurrentHashMap.newKeySet();
    // Map of queues for messages awaiting attention/processing, separated by ServiceNumber
    protected final Map<ServiceNumber, LinkedHashSet<Message>> sendingQueues;
    protected final int MAX_PENDING_ACKS;
    protected final int WAIT_MILIS;

    MessageManager(Set<ServiceNumber> serviceNumbers, org.apache.logging.log4j.Logger logger, int maxPendingAcks, int waitMilis) {
        this.LOGGER = logger;
        this.MAX_PENDING_ACKS = maxPendingAcks;
        this.WAIT_MILIS = waitMilis;
        this.sendingQueues = new ConcurrentHashMap<>();
        for (ServiceNumber serviceNumber : serviceNumbers) {
            this.sendingQueues.put(serviceNumber, new LinkedHashSet<>());
        }
    }

    public void addMsgToWaitingForAckList(Message msg) {
        waitingForAckMsgs.add(msg);
        // printWaitingForAckMsgState();
    }

    public void sendMessagesWaitingForAck(DataOutputStream outStream) throws IOException {
        for (Message msg : waitingForAckMsgs) {
            DecoderEncoder.writeMsg(outStream, msg);
        }
    }

    public void registerAck(byte[] originalMsgHash) {
        waitingForAckMsgs.removeIf((msg) -> {
            if (Arrays.equals(msg.getHash(), originalMsgHash)) {
                LOGGER.debug("Mensaje eliminado de espera de Ack ({})", Utils.byteArrayToHexString(originalMsgHash));
                return true;
            } else
                LOGGER.debug("Mensaje no está en lista de espera de Ack ({})", Utils.byteArrayToHexString(originalMsgHash));
            return false;
        });
        // printWaitingForAckMsgState();
    }

    public void printWaitingForAckMsgState() {
        System.out.println("Estado de waitingForAckMsgs: {");
        for (Message msg : waitingForAckMsgs) {
            System.out.println(" - " + Utils.byteArrayToHexString(msg.getHash()));
        }
        System.out.println("}");
    }

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


    public void addMsgToDispatchQueue(Message msg) {
        LinkedHashSet<Message> queue = sendingQueues.get(msg.getNumServicio());
        if (!queue.contains(msg)) {
            sendingQueues.get(msg.getNumServicio()).addLast(msg);
        }
    }

    public abstract void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream);

    public abstract void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Function<String, Void> showResult);

    public static final class ServerMessageManager extends MessageManager {
        ServerMessageManager(Logger logger, int maxPendingAcks, int waitMilis) {
            super(Set.of(new ServiceNumber[]{ServiceNumber.PrintResult}), logger, maxPendingAcks, waitMilis);
        }

        @Override
        public void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream) {
            while (true) {
                // Check if there are more pending Acks than allowed
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
                    }
                    continue;
                }
                // If there are not too many Acks waiting, we send the messages in the sending queues.
//                System.out.println("Antes de enviar mensajes");
//                printDispatchQueuesState();
                for (Map.Entry<ServiceNumber, LinkedHashSet<Message>> entry : this.sendingQueues.entrySet()) {
                    ServiceNumber serviceNumber = entry.getKey();
                    LinkedHashSet<Message> queue = entry.getValue();
                    if (queue.isEmpty()) {
                        continue;
                    }
                    Message nextMsgToSend = queue.removeFirst();
                    LOGGER.info("Despachando mensaje: {} ({})", serviceNumber, Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                    if (serviceNumber == ServiceNumber.PrintResult) {
                        try {
                            DecoderEncoder.writeMsg(outStream, nextMsgToSend);
                            LOGGER.info("Respondiendo con resultado para: {}", Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                            this.addMsgToWaitingForAckList(nextMsgToSend);
                            LOGGER.info("Mensaje añadido a lista de espera de Acks ({})", Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                        } catch (IOException e) {
                            LOGGER.error("Error en hilo de despacho del servidor ({}) al enviar resultado: {}", Utils.byteArrayToHexString(cellIdentifier), e.getMessage());
                        }
                    } else {
                        LOGGER.info("OTRO MENSAJE, no se hizo nada.");
                    }
                }
//                LOGGER.info("Tras envío de mensajes");
//                printDispatchQueuesState();

                try {
                    Thread.sleep(this.WAIT_MILIS); // Small delay before checking queues again
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Hilo de despacho del servidor interrumpido durante la espera.");
                    return;
                }
            }
        }

        @Override
        public void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Function<String, Void> showResult) {
            while (true) {
                try {
                    Message req = DecoderEncoder.readMsg(socketInStream);
                    LOGGER.info("Recibiendo msj: {} ({})", req.getNumServicio(),  Utils.byteArrayToHexString(req.getHash()));

                    switch (req.getNumServicio()) {
                        case Addition:
                        case Subtraction:
                        case Multiplication:
                        case Division:
                            // Send Ack immediately upon receiving a request
                            Message ackMsg = Message.buildAck(ProgramType.SOLICITANT, cellIdentifier, req.getHash());
                            DecoderEncoder.writeMsg(socketOutStream, ackMsg);
                            LOGGER.info("Enviando Ack de request original con hash: {}", Utils.byteArrayToHexString(req.getHash()));
                            // Build result message
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
                    LOGGER.error("Error en hilo de recepción: {}", e.getMessage());
                    break;
                }
            }
        }
    }

    public static final class ClientMessageManager extends MessageManager {
        private static final Set<ByteBuffer> lastMsgsToWaitResult = ConcurrentHashMap.newKeySet();

        ClientMessageManager(org.apache.logging.log4j.Logger logger, int maxPendingAcks, int waitMilis) {
            super(Set.of(new ServiceNumber[]{ServiceNumber.Addition, ServiceNumber.Subtraction, ServiceNumber.Multiplication, ServiceNumber.Division}), logger, maxPendingAcks, waitMilis);
        }

        public void addMsgHashToWaitResultSet(byte[] originalMsgHash) {
            LOGGER.debug("Hash almacenado: {}", Utils.byteArrayToHexString(originalMsgHash));
            lastMsgsToWaitResult.add(ByteBuffer.wrap(originalMsgHash));
        }

        public void removeMsgHashToWaitResultSet(byte[] originalMsgHash) {
            lastMsgsToWaitResult.removeIf((hash) -> Arrays.equals(hash.array(), originalMsgHash));
        }

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
                // Check if there are more pending Acks than allowed
                if (waitingForAckMsgs.size() >= this.MAX_PENDING_ACKS) {
                    try {
                        this.sendMessagesWaitingForAck(outStream);
                        Thread.sleep(this.WAIT_MILIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.fatal("Hilo de despacho del servidor interrumpido durante la espera.");
                        System.exit(1);
                        return;
                    } catch (IOException e) {
                        LOGGER.error("Error en hilo de despacho del servidor ({}) al enviar resultado: {}", Utils.byteArrayToHexString(cellIdentifier), e.getMessage());
                    }
                    continue;
                }
                // If there are not too many Acks waiting, we send the messages in the sending queues.
//                LOGGER.info("Antes de enviar mensajes");
//                printDispatchQueuesState();
                for (Map.Entry<ServiceNumber, LinkedHashSet<Message>> entry : this.sendingQueues.entrySet()) {
                    ServiceNumber serviceNumber = entry.getKey();
                    LinkedHashSet<Message> queue = entry.getValue();
                    if (queue.isEmpty()) {
                        continue;
                    }
                    Message nextMsgToSend = queue.removeFirst();
                    LOGGER.info("Despachando {} ({})", serviceNumber, Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                    try {
                        DecoderEncoder.writeMsg(outStream, nextMsgToSend);
                        this.addMsgToWaitingForAckList(nextMsgToSend);
                        this.addMsgHashToWaitResultSet(nextMsgToSend.getHash());
                        LOGGER.debug("Mensaje añadido a lista de espera de Acks ({})", Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                    } catch (IOException e) {
                        LOGGER.error("Error en hilo de despacho ({}) al enviar resultado: {}", Utils.byteArrayToHexString(cellIdentifier), e.getMessage());
                    }
                }
//                LOGGER.info("Tras envío de mensajes");
//                printDispatchQueuesState();

                try {
                    Thread.sleep(this.WAIT_MILIS); // Small delay before checking queues again
                } catch (InterruptedException e) {
                    LOGGER.error("Hilo de despacho interrumpido durante la espera.");
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
                    Message req = DecoderEncoder.readMsg(socketInStream);
                    LOGGER.info("Recibiendo msj {} ({})", req.getNumServicio(), Utils.byteArrayToHexString(req.getHash()));
                    switch (req.getNumServicio()) {
                        case Addition:
                        case Subtraction:
                        case Multiplication:
                        case Division:
                            // logger.log("Mensaje de solicitud inesperado: " + Utils.byteArrayToHexString(req.getHash()));
                            break;
                        case Ack:
                            byte[] originalMsgHash = DecoderEncoder.processAck(req);
                            LOGGER.info("Recibido Ack con contenido: {}", Utils.byteArrayToHexString(originalMsgHash));
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
                            LOGGER.info("Enviando Ack para {}", Utils.byteArrayToHexString(req.getHash()));

                            // printlastMsgsToWaitResultState();
                            // Mostrar resultado en la interfaz
                            ByteBuffer byteBuffer = ByteBuffer.wrap(resPair.getValue0());
                            if (lastMsgsToWaitResult.contains(byteBuffer)) {
                                this.removeMsgHashToWaitResultSet(resPair.getValue0());
                                showResult.apply(resPair.getValue1().toString());
                            }
                            break;
                    }
                } catch (IOException e) {
                    LOGGER.error("Error en hilo de recepción del servidor: {}", e.getMessage());
                }
            }
        }
    }
}

