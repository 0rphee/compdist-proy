package org.example;

import java.util.Optional;

public enum OperationType {
    ADD, SUB, MUL, DIV;

    public static Optional<OperationType> fromString(String str) {
        return switch (str) {
            case "+" -> Optional.of(OperationType.ADD);
            case "-" -> Optional.of(OperationType.SUB);
            case "*" -> Optional.of(OperationType.MUL);
            case "/" -> Optional.of(OperationType.DIV);
            default -> Optional.empty();
        };
    }

    public String toString() {
        return switch (this) {
            case OperationType.ADD -> "+";
            case OperationType.SUB -> "-";
            case OperationType.MUL -> "*";
            case OperationType.DIV -> "/";
        };
    }
}
