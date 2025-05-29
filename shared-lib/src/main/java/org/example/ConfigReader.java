package org.example;

import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigReader {
    public static class Config {
        public final Set<Pair<String, Integer>> NODES; // Almacena los hosts y puertos de los nodos.
        public final int MAX_PENDING_ACKS; // Máximo número de ACKs pendientes antes de reintentar.
        public final int SENDER_WAIT_MILIS; // Tiempo de espera del despachador.
        public final int CELL_CONN_DELAY_MILIS; // Retraso para reintentos de conexión de células.

        Config(Set<Pair<String, Integer>> nodes, int maxPendingAcks, int senderWaitMilis, int cellConnDelayMilis) {
            NODES = nodes;
            MAX_PENDING_ACKS = maxPendingAcks;
            SENDER_WAIT_MILIS = senderWaitMilis;
            CELL_CONN_DELAY_MILIS = cellConnDelayMilis;
        }

        public int[] getNodePorts() {
            // Extrae solo los puertos de la configuración de NODES.
            return this.NODES.stream().mapToInt(Pair::getValue1).toArray();
        }

        // Configuración por defecto si no se encuentra o falla la carga del archivo.
        public static final Config defaultConfig =
                new Config(
                        Set.of(
                                Pair.with("localhost", 31010),
                                Pair.with("localhost", 31011),
                                Pair.with("localhost", 31012),
                                Pair.with("localhost", 31013)
                        )
                        , 10, 5_000, 5_000
                );
    }

    public static Config readConfig(Logger LOGGER) {
        Properties prop = new Properties();
        InputStream input = null;

        Config resConfig; // Inicia con la configuración por defecto.
        try {
            // Cargar el archivo config.properties desde src/main/resources
            input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties");

            if (input == null) {
                LOGGER.error("No se pudo encontrar config.properties. Se usa configuración por defecto.");
                return Config.defaultConfig;
            }
            // Cargar las propiedades
            prop.load(input);

            // Obtener las propiedades
            // Parsea la cadena NODES="host1:port1,host2:port2" en un Set de Pares.
            Set<Pair> a = Arrays.stream(prop.getProperty("NODES").split(","))
                    .map((str) -> {
                        String[] split = str.split(":");
                        return Pair.with(split[0], Integer.parseInt(split[1])); // Crea un Pair (javatuples) por cada nodo.
                    }).collect(Collectors.toSet());
            LOGGER.info("Class {}", a.getClass()); // Loguea la clase del Set (debug).
            // Convierte el Set<Pair> a Set<Pair<String, Integer>> con casting explícito.
            Set<Pair<String, Integer>> NODES = a.stream().map((v) -> (Pair<String, Integer>) v).collect(Collectors.toUnmodifiableSet());

            int MAX_PENDING_ACKS = Integer.parseInt(prop.getProperty("MAX_PENDING_ACKS"));
            int SENDER_WAIT_MILIS = Integer.parseInt(prop.getProperty("SENDER_WAIT_MILIS"));
            int CELL_CONN_DELAY_MILIS = Integer.parseInt(prop.getProperty("CELL_CONN_DELAY_MILIS"));
            resConfig = new Config(NODES, MAX_PENDING_ACKS, SENDER_WAIT_MILIS, CELL_CONN_DELAY_MILIS);
        } catch (IOException | NumberFormatException |
                 NullPointerException e) {
            LOGGER.error("Error leyendo configuración, usando defaults: {}", e.getMessage());
            resConfig = Config.defaultConfig; // Vuelve a la configuración por defecto en caso de error.
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    LOGGER.error("Error cerrando InputStream de config: {}", e.getMessage());
                }
            }
        }
        return resConfig;
    }
}