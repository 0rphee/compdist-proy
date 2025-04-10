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

public class CelulaSolicitante extends Application {
    private static final String HOST = "localhost";

    private TextField operationField;
    private TextField operand1Field;
    private TextField operand2Field;
    private Button sendButton;
    private TextArea responseArea;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Cliente Calculadora");
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(8);
        grid.setHgap(10);

        // UI Components
        operationField = new TextField();
        operand1Field = new TextField();
        operand2Field = new TextField();
        sendButton = new Button("Enviar Solicitud");
        responseArea = new TextArea();
        responseArea.setEditable(false);
        responseArea.setWrapText(true);

        // Layout
        grid.add(new Label("Operación (+ - * /):"), 0, 0);
        grid.add(operationField, 1, 0);
        grid.add(new Label("Operando 1:"), 0, 1);
        grid.add(operand1Field, 1, 1);
        grid.add(new Label("Operando 2:"), 0, 2);
        grid.add(operand2Field, 1, 2);
        grid.add(sendButton, 1, 3);
        grid.add(new ScrollPane(responseArea), 0, 4, 2, 1);

        Scene scene = new Scene(grid, 500, 400);
        primaryStage.setScene(scene);

        // Disable button until connected
        sendButton.setDisable(true);

        // Setup connection and listeners
        setupConnection();
        setupEventHandlers();

        // Handle window close
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
                Thread.sleep(5000);

                int port = Message.getRandomNodePort();
                log("Conectando a " + HOST + ":" + port + "...");

                socket = new Socket(HOST, port);
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());

                // Send identification
                Message ident = Message.buildIdentify(Message.CellType.SERVER);
                DecoderEncoder.escribir(out, ident);

                // Verify server response
                Message response = DecoderEncoder.leer(in);
                if (response.getNumServicio() != Message.ServiceNumber.Identification ||
                        DecoderEncoder.processIdentification(response) != Message.CellType.NODE) {
                    log("Error de identificación del servidor");
                    return;
                }

                Platform.runLater(() -> sendButton.setDisable(false));
                log("Conexión establecida exitosamente!");

                // Start response listener
                new Thread(this::listenForResponses).start();

            } catch (InterruptedException | IOException e) {
                log("Error de conexión: " + e.getMessage());
            }
        }).start();
    }

    private void setupEventHandlers() {
        sendButton.setOnAction(event -> new Thread(() -> {
            try {
                Message.OperationType op = Message.OperationType.fromString(
                        operationField.getText().trim()
                );
                int n1 = Integer.parseInt(operand1Field.getText().trim());
                int n2 = Integer.parseInt(operand2Field.getText().trim());

                if (op == null) {
                    log("Operación no válida");
                    return;
                }

                if (op == Message.OperationType.DIV && n2 == 0) {
                    log("No se puede dividir por cero");
                    return;
                }

                Message request = Message.buildRequest(op, n1, n2);
                synchronized (out) {
                    DecoderEncoder.escribir(out, request);
                }
                log("Solicitud enviada: " + request);

            } catch (NumberFormatException e) {
                log("Los operandos deben ser números enteros");
            } catch (IOException e) {
                log("Error enviando solicitud: " + e.getMessage());
            }
        }).start());
    }

    private void listenForResponses() {
        try {
            while (!socket.isClosed()) {
                Message response = DecoderEncoder.leer(in);
                if (response.getNumServicio() == Message.ServiceNumber.Result) {
                    log("Resultado recibido: " + response);
                }
            }
        } catch (IOException e) {
            log("Error recibiendo respuesta: " + e.getMessage());
        }
    }

    private void log(String message) {
        Platform.runLater(() ->
                responseArea.appendText(message + "\n")
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}