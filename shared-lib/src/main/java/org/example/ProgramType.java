package org.example;

import java.util.Optional;

// Enum para facilitar las operaciones relativas al tipo de entidad/programa en la fase de identificación.
public enum ProgramType {
    NODE() {
        @Override
        public byte toByte() {
            return 0;
        }

        @Override
        public short toShort() {
            return 0;
        }
    }, SERVER() {
        @Override
        public byte toByte() {
            return 1;
        }

        @Override
        public short toShort() {
            return 1;
        }
    }, SOLICITANT() {
        @Override
        public byte toByte() {
            return 2;
        }

        @Override
        public short toShort() {
            return 2;
        }
    };

    // Convierte el tipo de programa a su representación en byte para la serialización.
    public abstract byte toByte();

    // Convierte el tipo de programa a su representación en short para la serialización.
    public abstract short toShort();

    // Convierte un valor numérico (short) de vuelta a un tipo de programa.
    public static Optional<ProgramType> fromShort(short n) {
        return switch (n) {
            case 0 -> Optional.of(ProgramType.NODE);
            case 1 -> Optional.of(ProgramType.SERVER);
            case 2 -> Optional.of(ProgramType.SOLICITANT);
            default -> Optional.empty();
        };
    }

    // Convierte un valor numérico (byte) de vuelta a un tipo de programa.
    public static Optional<ProgramType> fromByte(byte n) {
        return ProgramType.fromShort(n);
    }

    // Proporciona una representación de texto legible para el tipo de programa.
    public String toString() {
        return switch (this) {
            case NODE -> "Node";
            case SERVER -> "Server";
            case SOLICITANT -> "Solicitant";
        };
    }
}