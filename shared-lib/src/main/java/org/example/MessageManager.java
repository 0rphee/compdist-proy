package org.example;

import org.javatuples.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class MessageManager {
    // Set to store hashes of processed messages to prevent duplicate processing
    private static final Set<Message> waitingForAckMsgs = ConcurrentHashMap.newKeySet();
    // Map of queues for messages awaiting attention/processing, separated by ServiceNumber
    protected final Map<ServiceNumber, LinkedHashSet<Message>> sendingQueues;
    private static final int MAX_PENDING_ACKS = 10;
    private static final int WAIT_MILIS = 5_000;

    MessageManager(Set<ServiceNumber> serviceNumbers) {
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
                System.out.println("Mensaje eliminado de espera de Ack (" + Utils.byteArrayToHexString(originalMsgHash) + ")");
                return true;
            } else
                System.out.println("Mensaje no está en lista de espera de Ack (" + Utils.byteArrayToHexString(originalMsgHash) + ")");
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
        LinkedHashSet queue = sendingQueues.get(msg.getNumServicio());
        if (!queue.contains(msg)) {
            sendingQueues.get(msg.getNumServicio()).addLast(msg);
        }
    }

    public interface Logger {
        default void log(String str) {
            System.out.println(str);
        }

        void showResult(String str);
    }

    public abstract void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream);

    public abstract void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Logger logger);

    public static final class ServerMessageManager extends MessageManager {
        ServerMessageManager() {
            super(Set.of(new ServiceNumber[]{ServiceNumber.PrintResult}));
        }

        @Override
        public void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream) {
            while (true) {
                // Check if there are more pending Acks than allowed
                if (waitingForAckMsgs.size() >= MessageManager.MAX_PENDING_ACKS) {
                    try {
                        this.sendMessagesWaitingForAck(outStream);
                        Thread.sleep(MessageManager.WAIT_MILIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Hilo de despacho del servidor interrumpido durante la espera.");
                        return;
                    } catch (IOException e) {
                        System.err.println("Error en hilo de despacho del servidor (" + Utils.byteArrayToHexString(cellIdentifier) + ") al enviar resultado: " + e.getMessage());
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
                    System.out.println("Servidor - Despachando mensaje: " + serviceNumber + " (" + Utils.byteArrayToHexString(nextMsgToSend.getHash()) + ")");
                    if (serviceNumber == ServiceNumber.PrintResult) {
                        try {
                            DecoderEncoder.writeMsg(outStream, nextMsgToSend);
                            System.out.println("Servidor - Respondiendo con resultado para: " + Utils.byteArrayToHexString(nextMsgToSend.getHash()));
                            this.addMsgToWaitingForAckList(nextMsgToSend);
                            System.out.println("Servidor - Mensaje añadido a lista de espera de Acks (" + Utils.byteArrayToHexString(nextMsgToSend.getHash()) + ")");
                        } catch (IOException e) {
                            System.err.println("Error en hilo de despacho del servidor (" + Utils.byteArrayToHexString(cellIdentifier) + ") al enviar resultado: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Servidor - OTRO MENSAJE, no se hizo nada.");
                    }
                }
//                System.out.println("Tras envío de mensajes");
//                printDispatchQueuesState();

                try {
                    Thread.sleep(MessageManager.WAIT_MILIS); // Small delay before checking queues again
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Hilo de despacho del servidor interrumpido durante la espera.");
                    return;
                }
            }
        }

        @Override
        public void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Logger logger) {
            while (true) {
                try {
                    Message req = DecoderEncoder.readMsg(socketInStream);
                    System.out.println("Servidor - Recibiendo msj: " + req.getNumServicio() + " - Hash: " + Utils.byteArrayToHexString(req.getHash()));

                    switch (req.getNumServicio()) {
                        case Addition:
                        case Subtraction:
                        case Multiplication:
                        case Division:
                            // Send ACK immediately upon receiving a request
                            Message ackMsg = Message.buildAck(ProgramType.SOLICITANT, cellIdentifier, req.getHash());
                            DecoderEncoder.writeMsg(socketOutStream, ackMsg);
                            logger.log("Servidor - Enviando ACK de request original con hash: " + Utils.byteArrayToHexString(req.getHash()));
                            // Build result message
                            int res = DecoderEncoder.processRequest(req);
                            Message responseMsg = Message.buildResult(cellIdentifier, res, req.getHash());
                            // Add message to dispatch queue
                            this.addMsgToDispatchQueue(responseMsg);
                            logger.log("Servidor - Mensaje de respuesta " + Utils.byteArrayToHexString(req.getHash()) + " añadido a fila de envío.");
                            break;
                        case Ack:
                            byte[] originalMsgHash = DecoderEncoder.processAck(req);
                            logger.log("Servidor - Recibido ACK, con contenido: " + Utils.byteArrayToHexString(originalMsgHash));
                            this.registerAck(originalMsgHash);
                            break;
                        case Identification:
                            logger.log("Servidor - Recibida identificación de: " + DecoderEncoder.processIdentification(req));
                            break;
                        case PrintResult:
                            // logger.log("Servidor - Recibido PrintResult inesperado: " + Utils.byteArrayToHexString(req.getHash()));
                            break;
                    }
                } catch (IOException e) {
                    System.err.println("Error en hilo de recepción del servidor: " + e.getMessage());
                    break;
                }
            }
        }
    }

    public static final class ClientMessageManager extends MessageManager {
        private static final Set<ByteBuffer> lastMsgsToWaitResult = ConcurrentHashMap.newKeySet();

        public void addMsgHashToWaitResultSet(byte[] originalMsgHash) {
            System.out.println("Client - Hash stored: " + Utils.byteArrayToHexString(originalMsgHash));
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


        ClientMessageManager() {
            super(Set.of(new ServiceNumber[]{ServiceNumber.Addition, ServiceNumber.Subtraction, ServiceNumber.Multiplication, ServiceNumber.Division}));
        }

        @Override
        public void dispatcherLoop(byte[] cellIdentifier, DataOutputStream outStream) {
            while (true) {
                // Check if there are more pending Acks than allowed
                if (waitingForAckMsgs.size() >= MessageManager.MAX_PENDING_ACKS) {
                    try {
                        this.sendMessagesWaitingForAck(outStream);
                        Thread.sleep(MessageManager.WAIT_MILIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Hilo de despacho del servidor interrumpido durante la espera.");
                        return;
                    } catch (IOException e) {
                        System.err.println("Error en hilo de despacho del servidor (" + Utils.byteArrayToHexString(cellIdentifier) + ") al enviar resultado: " + e.getMessage());
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
                    System.out.println("Cliente - Despachando " + serviceNumber + " (" + Utils.byteArrayToHexString(nextMsgToSend.getHash()) + ")");
                    try {
                        DecoderEncoder.writeMsg(outStream, nextMsgToSend);
                        this.addMsgToWaitingForAckList(nextMsgToSend);
                        this.addMsgHashToWaitResultSet(nextMsgToSend.getHash());
                        System.out.println("Cliente - Mensaje añadido a lista de espera de Acks (" + Utils.byteArrayToHexString(nextMsgToSend.getHash()) + ")");
                    } catch (IOException e) {
                        System.err.println("Error en hilo de despacho del cliente (" + Utils.byteArrayToHexString(cellIdentifier) + ") al enviar resultado: " + e.getMessage());
                    }
                }
//                System.out.println("Tras envío de mensajes");
//                printDispatchQueuesState();

                try {
                    Thread.sleep(MessageManager.WAIT_MILIS); // Small delay before checking queues again
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Hilo de despacho del servidor interrumpido durante la espera.");
                    return;
                }
            }
        }

        @Override
        public void receiverLoop(byte[] cellIdentifier, DataInputStream socketInStream, DataOutputStream socketOutStream, Logger logger) {
            while (true) {
                try {
                    Message req = DecoderEncoder.readMsg(socketInStream);
                    logger.log("Cliente - Recibiendo msj: " + req.getNumServicio() + " - Hash: " + Utils.byteArrayToHexString(req.getHash()));

                    switch (req.getNumServicio()) {
                        case Addition:
                        case Subtraction:
                        case Multiplication:
                        case Division:
                            // logger.log("Cliente - Mensaje de solicitud inesperado: " + Utils.byteArrayToHexString(req.getHash()));
                            break;
                        case Ack:
                            byte[] originalMsgHash = DecoderEncoder.processAck(req);
                            logger.log("Cliente - Recibido ACK, con contenido: " + Utils.byteArrayToHexString(originalMsgHash));
                            this.registerAck(originalMsgHash);
                            break;
                        case Identification:
                            logger.log("Cliente - Recibida identificación de: " + DecoderEncoder.processIdentification(req));
                            break;
                        case PrintResult:
                            // Responder con ACK
                            Pair<byte[], Integer> resPair = DecoderEncoder.processResult(req);
                            logger.log("Cliente - PrintResult hash acompañante: " + Utils.byteArrayToHexString(resPair.getValue0()));
                            Message ackMsg = Message.buildAck(ProgramType.SERVER, cellIdentifier, req.getHash());
                            DecoderEncoder.writeMsg(socketOutStream, ackMsg);
                            logger.log("Cliente - Enviando ACK para " + Utils.byteArrayToHexString(req.getHash()));

                            printlastMsgsToWaitResultState();

                            // Mostrar resultado en la interfaz
                            ByteBuffer byteBuffer =  ByteBuffer.wrap(resPair.getValue0());
                            boolean condition = lastMsgsToWaitResult.contains(byteBuffer);
                            System.out.println("Cliente - condition: " + condition);
                            if (condition) {
                                this.removeMsgHashToWaitResultSet(resPair.getValue0());
                                logger.showResult(resPair.getValue1().toString());
                            }
                            break;
                    }
                } catch (IOException e) {
                    System.err.println("Error en hilo de recepción del servidor: " + e.getMessage());
                    break;
                }
            }
        }
    }
}

