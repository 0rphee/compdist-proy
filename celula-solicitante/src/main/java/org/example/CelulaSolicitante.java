package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.ParseException;

public class CelulaSolicitante extends Application {
    private static final Logger LOGGER = LogManager.getLogger(CelulaSolicitante.class);
    private static final ConfigReader.Config CONFIG = ConfigReader.readConfig(LOGGER);
    private static final String HOST = "localhost";
    private byte[] identifier;
    private static final MessageManager.ClientMessageManager messageManager = new MessageManager.ClientMessageManager(LOGGER, CONFIG.MAX_PENDING_ACKS, CONFIG.SENDER_WAIT_MILIS);

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private Button[] operationButtons;
    private TextField operand1Field;
    private TextField operand2Field;
    private TextField resultArea;
    private TextField warningArea;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Cliente Calculadora");
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(8);
        grid.setHgap(10);

        // UI Components
        operationButtons = new Button[]{new Button("+"), new Button("-"), new Button("*"), new Button("/")};
        operand1Field = new TextField();
        operand2Field = new TextField();
        resultArea = new TextField();
        warningArea = new TextField();

        resultArea.setEditable(false);
        warningArea.setEditable(true);

        GridPane gridOperationButtons = new GridPane();
        gridOperationButtons.setHgap(10);

        // Layout
        gridOperationButtons.addRow(0, operationButtons);
        grid.addRow(0, new Label("Operación:"), gridOperationButtons);
        grid.addRow(1, new Label("Operando 1:"), operand1Field);
        grid.addRow(2, new Label("Operando 2:"), operand2Field);
        grid.add(new Label("Resultado"), 0, 3, 2, 1);
        grid.add(resultArea, 0, 4, 2, 1);
        grid.add(warningArea, 0, 4, 2, 1);

        Scene scene = new Scene(grid, 500, 400);
        primaryStage.setScene(scene);

        // Disable buttons until connected
        for (Button btn : operationButtons) {
            btn.setDisable(true);
        }

        // Setup connection and listeners
        setupConnection();
        setupEventHandlers();

        // When window is closed
        primaryStage.setOnCloseRequest(event -> {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        primaryStage.show();
    }

    private void setupConnection() {
        new Thread(() -> {
            int intentos = 1;
            while (intentos < 11) {
                try {
                    // Initial delay
                    Thread.sleep(CONFIG.CELL_CONN_DELAY_MILIS);
                    Pair<String, Integer> node = Utils.getRandomNodePort(CONFIG.NODES);
                    String nodeHost = node.getValue0();
                    int nodePort = node.getValue1();

                    LOGGER.info("Inntento {}, conectando a {}:{}...", intentos, nodeHost, nodePort);
                    this.socket = Utils.cellTryToCreateSocket(nodeHost, nodePort, CONFIG.CELL_CONN_DELAY_MILIS, LOGGER);
                    this.identifier = Utils.createIdentifier(HOST, this.socket.getLocalPort());
                    this.out = new DataOutputStream(socket.getOutputStream());
                    this.in = new DataInputStream(socket.getInputStream());

                    // Identify and check node identification
                    Message ident = Message.buildIdentify(ProgramType.SOLICITANT, identifier, ProgramType.NODE);
                    DecoderEncoder.writeMsg(this.out, ident);

                    Message response = DecoderEncoder.readMsg(this.in);
                    if (response.getNumServicio() != ServiceNumber.Identification ||
                            DecoderEncoder.processIdentification(response) != ProgramType.NODE) {
                        LOGGER.fatal("Error de identificación del servidor: {}", response.getNumServicio());
                        System.exit(1);
                        return;
                    }

                    CelulaSolicitante cel = this;
                    // receiver thread
                    new Thread(() -> messageManager.receiverLoop(identifier, in, out, cel::writeRes), "Client-receiverLoop").start();
                    // dispatcher thread
                    new Thread(() -> messageManager.dispatcherLoop(identifier, out), "Client-dispatcherLoop").start();
                    // Start response listener

                    Platform.runLater(() -> {
                        for (Button btn : operationButtons)
                            btn.setDisable(false);
                    });
                    LOGGER.info("Conexión a nodo establecida exitosamente!");
                    return;
                } catch (InterruptedException | IOException e) {
                    LOGGER.error("Error de conexión: " + e.getMessage());
                }
            }
            LOGGER.fatal("Máximo número de intentos de conexión alcanzado. Cierre de aplicación.");
            System.exit(1);
        }).start();
    }

    private void setupEventHandlers() {
        for (Button btn : operationButtons) {
            setupButtonEventHandler(btn);
        }
    }

    private void setupButtonEventHandler(Button btn) {
        btn.setOnAction(event -> new Thread(() -> {
                    try {
                        // we get the string from the button text because this function
                        // is applied to each button: + - * /
                        OperationType op = OperationType.fromString(
                                btn.getText().trim()
                        ).orElseThrow(() -> new ParseException(btn.getText(), 0));
                        int n1 = Integer.parseInt(operand1Field.getText().trim());
                        int n2 = Integer.parseInt(operand2Field.getText().trim());

                        if (op == OperationType.DIV && n2 == 0) {
                            String msg = "No se puede dividir entre cero";
                            LOGGER.error(msg);
                            this.warningArea.setText(msg);
                            return;
                        }
                        Message request = Message.buildRequest(identifier, op, n1, n2);
                        this.messageManager.addMsgToDispatchQueue(request);
                        // DecoderEncoder.writeMsg(out, request);
                        // this.lastRequestMsg = Optional.of(request);
                        LOGGER.info("Solicitud añadida a lista de salida: {}", request);
                        this.warningArea.setText("");
                    } catch (ParseException e) {
                        // should never happen: operations come from hard-coded buttons
                        String msg = "Operación no válida";
                        LOGGER.error(msg);
                        this.warningArea.setText(msg);
                    } catch (NumberFormatException e) {
                        String msg = "Los operandos deben ser números enteros";
                        LOGGER.error(msg);
                        this.warningArea.setText(msg);
                    } catch (IOException e) {
                        LOGGER.error("Error enviando solicitud: {}", e.getMessage());
                    }
                }).start()
        );
    }

    private Void writeRes(String message) {
        Platform.runLater(() -> {
                    LOGGER.info("Resultado mostrado: {}", message);
                    resultArea.setText(message);
                }
        );
        return null;
    }

    public static void main(String[] args) {
        javafx.application.Application.launch(args);
    }
}
