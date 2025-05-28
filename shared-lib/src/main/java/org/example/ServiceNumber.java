package org.example;

import java.util.Optional;

// used to facilitate operations regarding the service number of each message (ex. serializing/deserializing)
public enum ServiceNumber {
    Identification, // 0
    Addition,       // 1
    Subtraction,   // 2
    Multiplication,  // 3
    Division,       // 4
    PrintResult,    // 5
    Ack             // 99
    ;

    public boolean isRequestToServer() {
        return switch (this) {
            case ServiceNumber.Addition:
            case ServiceNumber.Subtraction:
            case ServiceNumber.Multiplication:
            case ServiceNumber.Division:
                yield true;
            default:
                yield false;
        };
    }

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
