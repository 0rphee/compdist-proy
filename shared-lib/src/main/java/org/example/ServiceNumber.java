package org.example;

import java.util.Optional;

// Enum para facilitar las operaciones con el número de servicio de cada mensaje (ej. serializar/deserializar).
public enum ServiceNumber {
    Identification, // 0: Identificación de un componente.
    Addition,       // 1: Operación de suma.
    Subtraction,    // 2: Operación de resta.
    Multiplication, // 3: Operación de multiplicación.
    Division,       // 4: Operación de división.
    PrintResult,    // 5: Mensaje con el resultado de una operación.
    Ack             // 99: Mensaje de acuse de recibo (Acknowledgement).
    ;

    // Convierte el tipo de servicio a su valor numérico (short) para la serialización.
    public short toShort() {
        return switch (this) {
            case ServiceNumber.Identification -> 0;
            case ServiceNumber.Addition -> 1;
            case ServiceNumber.Subtraction -> 2;
            case ServiceNumber.Multiplication -> 3;
            case ServiceNumber.Division -> 4;
            case ServiceNumber.PrintResult -> 5;
            case ServiceNumber.Ack -> 99;
        };
    }

    // Convierte un valor numérico (short), leído desde un mensaje, a un tipo de servicio.
    public static Optional<ServiceNumber> fromShort(short number) {
        return switch (number) {
            case 0 -> Optional.of(ServiceNumber.Identification);
            case 1 -> Optional.of(ServiceNumber.Addition);
            case 2 -> Optional.of(ServiceNumber.Subtraction);
            case 3 -> Optional.of(ServiceNumber.Multiplication);
            case 4 -> Optional.of(ServiceNumber.Division);
            case 5 -> Optional.of(ServiceNumber.PrintResult);
            case 99 -> Optional.of(ServiceNumber.Ack);
            default -> Optional.empty();
        };
    }

    // Devuelve una representación en texto del servicio, útil para logging.
    public String toString() {
        return switch (this) {
            case ServiceNumber.Identification -> "Identification (0)";
            case ServiceNumber.Addition -> "Addition (1)";
            case ServiceNumber.Subtraction -> "Subtraction (2)";
            case ServiceNumber.Multiplication -> "Multiplication (3)";
            case ServiceNumber.Division -> "Division (4)";
            case ServiceNumber.PrintResult -> "PrintResult (5)";
            case ServiceNumber.Ack -> "Ack (99)";
        };
    }
}