package org.example;

import java.io.*;
import java.net.Socket;

public class CelulaServidor {
    private static final String HOST = "localhost";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: CelulaServidor <PORT>");
        }
        int port = Integer.parseInt(args[0]);

        Socket socket = new Socket(HOST, port);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        Message ident = Message.buildIdentify(Message.CellType.SERVER);
        Messenger.send(out, ident);

        Connection con = new Connection(socket, Messenger.read(in));
        System.out.println("Conectado exitosamente: " + con);

        while (true) {
            Message req = Messenger.read(in);
            double lhs = MessageBuilder.GetLhs(req);
            double rhs = MessageBuilder.GetRhs(req);
            double result = 0;
            switch (req.msg[0]) {
                case Message.RequestType.Add:
                    result = lhs + rhs;
                    break;
                case Message.RequestType.Sub:
                    result = lhs - rhs;
                    break;
                case Message.RequestType.Mul:
                    result = lhs * rhs;
                    break;
                case Message.RequestType.Div:
                    result = lhs / rhs;
                    break;
            }

            Messenger.send(out, MessageBuilder.Restultado(req, result));
            System.out.println("Respondiendo a: " + req);
        }

    }
}