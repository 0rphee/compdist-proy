package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.ParseException;

public class CelulaSolicitante extends Application {
    private static final String HOST = "localhost";
    private static byte[] identifier;
    private static final MessageManager.ClientMessageManager messageManager = new MessageManager.ClientMessageManager();

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private Button[] operationButtons;
    private TextField operand1Field;
    private TextField operand2Field;
    private TextField resultArea;
    private TextArea logArea;

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
        logArea = new TextArea();

        resultArea.setEditable(false);
        logArea.setEditable(false);
        logArea.setWrapText(true);

        GridPane gridOperationButtons = new GridPane();
        gridOperationButtons.setHgap(10);

        // Layout
        gridOperationButtons.addRow(0, operationButtons);
        grid.addRow(0, new Label("Operación:"), gridOperationButtons);
        grid.addRow(1, new Label("Operando 1:"), operand1Field);
        grid.addRow(2, new Label("Operando 2:"), operand2Field);
        grid.add(new Label("Resultado"), 0, 3, 2, 1);
        grid.add(resultArea, 0, 4, 2, 1);
        grid.add(new Label("Logs"), 0, 5, 2, 1);
        grid.add(new ScrollPane(logArea), 0, 6, 2, 1);

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
            try {
                // Initial delay
                int delay =5000;
                Thread.sleep(delay);
                int port = Utils.getRandomNodePort();

                log("Conectando a " + HOST + ":" + port + "...");
                this.socket = Utils.cellTryToCreateSocket(HOST, port, delay);
                this.identifier = Utils.createIdentifier(HOST, this.socket.getLocalPort());
                this.out = new DataOutputStream(socket.getOutputStream());
                this.in = new DataInputStream(socket.getInputStream());

                // Identify and check node identification
                Message ident = Message.buildIdentify(ProgramType.SOLICITANT, identifier, ProgramType.NODE);
                DecoderEncoder.writeMsg(this.out, ident);

                Message response = DecoderEncoder.readMsg(this.in);
                if (response.getNumServicio() != ServiceNumber.Identification ||
                        DecoderEncoder.processIdentification(response) != ProgramType.NODE) {
                    log("Error de identificación del servidor: " + response.getNumServicio());
                    System.exit(1);
                    return;
                }

                CelulaSolicitante cel = this;
                MessageManager.Logger logger = new MessageManager.Logger() {
                    @Override
                    public void log(String str) {
                        cel.log(str);
                    }

                    @Override
                    public void showResult(String str) {
                        cel.writeRes(str);
                    }
                };
                // receiver thread
                new Thread(() -> messageManager.receiverLoop(identifier, in, out, logger)).start();
                // dispatcher thread
                new Thread(() -> messageManager.dispatcherLoop(identifier, out)).start();
                // Start response listener
                // new Thread(this::listenForResponses).start();
                // if the connection is correctly set up, we can request results

                Platform.runLater(() -> {
                    for (Button btn : operationButtons) {
                        btn.setDisable(false);
                    }
                });

                log("Conexión establecida exitosamente!");
            } catch (InterruptedException | IOException e) {
                log("Error de conexión: " + e.getMessage());
            }
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
                            log("No se puede dividir entre cero");
                            return;
                        }

                        Message request = Message.buildRequest(identifier, op, n1, n2);

                        this.messageManager.addMsgToDispatchQueue(request);
                        // DecoderEncoder.writeMsg(out, request);
                        // this.lastRequestMsg = Optional.of(request);
                        log("Solicitud añadida a lista de salida: " + request);
                    } catch (ParseException e) {
                        // should never happen: operations come from hard-coded buttons
                        log("Operación no válida");
                    } catch (NumberFormatException e) {
                        log("Los operandos deben ser números enteros");
                    } catch (IOException e) {
                        log("Error enviando solicitud: " + e.getMessage());
                    }
                }).start()
        );
    }

    private void writeRes(String message) {
        Platform.runLater(() ->
                resultArea.setText(message)
        );
    }

    private void log(String message) {
        Platform.runLater(() ->
                logArea.appendText(message + "\n")
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
