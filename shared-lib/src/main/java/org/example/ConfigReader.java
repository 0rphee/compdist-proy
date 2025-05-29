package org.example;

import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigReader {
    public static class Config {
        public final Set<Pair<String, Integer>> NODES;
        public final int MAX_PENDING_ACKS;
        public final int SENDER_WAIT_MILIS;
        public final int CELL_CONN_DELAY_MILIS;

        Config(Set<Pair<String, Integer>> nodes, int maxPendingAcks, int senderWaitMilis, int cellConnDelayMilis) {
            NODES = nodes;
            MAX_PENDING_ACKS = maxPendingAcks;
            SENDER_WAIT_MILIS = senderWaitMilis;
            CELL_CONN_DELAY_MILIS = cellConnDelayMilis;
        }

        public int[] getNodePorts(){
            return this.NODES.stream().mapToInt((p) -> p.getValue1()).toArray();
        }

        public static final Config defaultConfig =
                new Config(
                        Set.of(new Pair[]{
                                new Pair("localhost", 31010),
                                new Pair("localhost", 31011),
                                new Pair("localhost", 31012),
                                new Pair("localhost", 31013),
                        })
                        , 10, 5_000, 5_000
                );
    }

    public static Config readConfig(Logger LOGGER) {
        Properties prop = new Properties();
        InputStream input = null;

        Config resConfig = Config.defaultConfig;
        try {
            // Cargar el archivo config.properties desde src/main/resources
            input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties");

            if (input == null) {
                LOGGER.error("No se pudo encontrar config.properties");
                return Config.defaultConfig;
            }
            // Cargar las propiedades
            prop.load(input);

            // Obtener las propiedades
            Set<Pair> a = Arrays.stream(prop.getProperty("NODES").split(",")).map((str) -> {
                String[] split = str.split(":");
                return new Pair(split[0], Integer.parseInt(split[1]));
            }).collect(Collectors.toSet());
            LOGGER.info("Class {}",a.getClass());
            Set<Pair<String, Integer>> NODES = a.stream().map((v) -> (Pair<String,Integer>) v).collect(Collectors.toUnmodifiableSet());

            int MAX_PENDING_ACKS = Integer.parseInt(prop.getProperty("MAX_PENDING_ACKS"));
            int SENDER_WAIT_MILIS = Integer.parseInt(prop.getProperty("SENDER_WAIT_MILIS"));
            int  CELL_CONN_DELAY_MILIS= Integer.parseInt(prop.getProperty("CELL_CONN_DELAY_MILIS"));
            resConfig = new Config(NODES, MAX_PENDING_ACKS, SENDER_WAIT_MILIS, CELL_CONN_DELAY_MILIS);
        } catch (IOException e) {
            LOGGER.error(e);
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    LOGGER.error(e);
                    e.printStackTrace();
                }
            }
        }
        return resConfig;
    }
}
