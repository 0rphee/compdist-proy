package org.example;

import java.util.Optional;

// used to facilitate operations regarding the type of the entity/program in the identification phase
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

    public abstract byte toByte();

    public abstract short toShort();

    public static Optional<ProgramType> fromShort(short n){
        return switch (n) {
            case 0 -> Optional.of(ProgramType.NODE);
            case 1 -> Optional.of(ProgramType.SERVER);
            case 2 -> Optional.of(ProgramType.SOLICITANT);
            default -> Optional.empty();
        };
    }

    public static Optional<ProgramType> fromByte(byte n) {
        return ProgramType.fromShort(n);
    }

    public String toString() {
        return switch (this) {
            case NODE -> "Node";
            case SERVER -> "Server";
            case SOLICITANT -> "Solicitant";
        };
    }
}
