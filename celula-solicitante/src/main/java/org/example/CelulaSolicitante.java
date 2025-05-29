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
    private Label warningArea;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Cliente Calculadora");
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(8);
        grid.setHgap(10);

        // Componentes de la interfaz gráfica
        operationButtons = new Button[]{new Button("+"), new Button("-"), new Button("*"), new Button("/")};
        operand1Field = new TextField();
        operand2Field = new TextField();
        resultArea = new TextField();
        warningArea = new Label();

        resultArea.setEditable(false);

        GridPane gridOperationButtons = new GridPane();
        gridOperationButtons.setHgap(10);

        // Layout
        gridOperationButtons.addRow(0, operationButtons);
        grid.addRow(0, new Label("Operación:"), gridOperationButtons);
        grid.addRow(1, new Label("Operando 1:"), operand1Field);
        grid.addRow(2, new Label("Operando 2:"), operand2Field);
        grid.add(new Label("Resultado"), 0, 3, 2, 1);
        grid.add(resultArea, 0, 4, 2, 1);
        grid.add(warningArea, 0, 6, 2, 1);

        Scene scene = new Scene(grid, 500, 400);
        primaryStage.setScene(scene);

        // Deshabilitar botones hasta estar conectado.
        for (Button btn : operationButtons) {
            btn.setDisable(true);
        }

        // Configurar conexión y listeners.
        setupConnection();
        setupEventHandlers();

        // Al cerrar la ventana.
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
            // Bucle de intentos de conexión.
            while (intentos < 11) {
                try {
                    // Retraso inicial.
                    Thread.sleep(CONFIG.CELL_CONN_DELAY_MILIS);
                    // Obtiene un nodo aleatorio para la conexión.
                    Pair<String, Integer> node = Utils.getRandomNodePort(CONFIG.NODES);
                    String nodeHost = node.getValue0();
                    int nodePort = node.getValue1();

                    LOGGER.info("Intento {}, conectando a {}:{}...", intentos, nodeHost, nodePort);
                    this.socket = Utils.cellTryToCreateSocket(nodeHost, nodePort, CONFIG.CELL_CONN_DELAY_MILIS, LOGGER); // Intenta crear socket con reintentos.
                    this.identifier = Utils.createIdentifier(HOST, this.socket.getLocalPort()); // Crea identificador para esta célula solicitante.
                    this.out = new DataOutputStream(socket.getOutputStream());
                    this.in = new DataInputStream(socket.getInputStream());

                    // Identificarse y verificar la identificación del nodo.
                    Message ident = Message.buildIdentify(ProgramType.SOLICITANT, identifier, ProgramType.NODE);
                    DecoderEncoder.writeMsg(this.out, ident);

                    Message response = DecoderEncoder.readMsg(this.in);
                    // Valida que la respuesta sea una identificación de un nodo.
                    if (response.getNumServicio() != ServiceNumber.Identification ||
                            DecoderEncoder.processIdentification(response) != ProgramType.NODE) {
                        LOGGER.fatal("Error de identificación del servidor: {}", response.getNumServicio());
                        System.exit(1); // Termina si la identificación falla.
                        return;
                    }

                    CelulaSolicitante cel = this;
                    // Hilo receptor de mensajes.
                    new Thread(() -> messageManager.receiverLoop(identifier, in, out, cel::writeRes), "Client-receiverLoop").start();
                    // Hilo despachador de mensajes.
                    new Thread(() -> messageManager.dispatcherLoop(identifier, out), "Client-dispatcherLoop").start();

                    // Habilita los botones de operación en el hilo de la UI.
                    Platform.runLater(() -> {
                        for (Button btn : operationButtons)
                            btn.setDisable(false);
                    });
                    LOGGER.info("Conexión a nodo establecida exitosamente!");
                    return; // Sale del bucle de intentos si la conexión es exitosa.
                } catch (InterruptedException | IOException e) {
                    LOGGER.error("Error de conexión: {}", e.getMessage());
                } finally {
                    intentos += 1;
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
        // Cada click de botón se maneja en un nuevo hilo.
        btn.setOnAction(event -> new Thread(() -> {
                    try {
                        // Obtiene el tipo de operación del texto del botón.
                        OperationType op = OperationType.fromString(
                                btn.getText().trim()
                        ).orElseThrow(() -> new ParseException(btn.getText(), 0));
                        int n1 = Integer.parseInt(operand1Field.getText().trim());
                        int n2 = Integer.parseInt(operand2Field.getText().trim());

                        // Validación de división por cero.
                        if (op == OperationType.DIV && n2 == 0) {
                            String msg = "No se puede dividir entre cero";
                            LOGGER.error(msg);
                            // Actualiza interfaz.
                            Platform.runLater(() -> this.warningArea.setText(msg));
                            return;
                        }
                        // Construye el mensaje de solicitud.
                        Message request = Message.buildRequest(identifier, op, n1, n2);
                        // Añade el mensaje a la cola de despacho.
                        this.messageManager.addMsgToDispatchQueue(request);
                        LOGGER.info("Solicitud añadida a lista de salida: {}", request);
                        // Limpia advertencias en UI.
                        Platform.runLater(() -> this.warningArea.setText(""));
                    } catch (ParseException e) {
                        // Esto no debería ocurrir ya que las operaciones vienen de botones predefinidos.
                        String msg = "Operación no válida";
                        LOGGER.error(msg);
                        Platform.runLater(() -> this.warningArea.setText(msg));
                    } catch (NumberFormatException e) {
                        String msg = "Los operandos deben ser números enteros";
                        LOGGER.error(msg);
                        Platform.runLater(() -> this.warningArea.setText(msg));
                    } catch (IOException e) {
                        LOGGER.error("Error enviando solicitud: {}", e.getMessage());
                    }
                }).start()
        );
    }

    // Método para escribir el resultado en la interfaz, llamado desde el hilo receptor.
    private Void writeRes(String message) {
        Platform.runLater(() -> {
                    // Asegura que la actualización de la UI se haga en el hilo de JavaFX.
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