package com.minildap.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class DirectoryClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5050;

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("Connected to MiniLDAP ("+host+":"+port+")");
            System.out.println("Server: " + in.readLine());

            help();

            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line.trim().isEmpty()) continue;
                out.println(line);
                String resp = in.readLine();
                if (resp == null) { System.out.println("Server closed."); break; }
                if ("__CLOSE__".equals(resp)) { System.out.println("Bye"); break; }
                System.out.println(resp);
                if ("EXIT".equalsIgnoreCase(line.trim())) break;
            }
        }
    }

    private static void help() {
        System.out.println("Commands:");
        System.out.println("  AUTH <username> <password>");
        System.out.println("  LOGOUT");
        System.out.println("  PING");
        System.out.println("  LISTUSER");
        System.out.println("  READUSER <username>");
        System.out.println("  UPDATEUSER <username> <email> <fullName>");
        System.out.println("  ADDUSER <username> <password> <role> [email] [fullName]");
        System.out.println("  DELETEUSER <username>");
        System.out.println("  CREATEGROUP <groupname>");
        System.out.println("  DELETEGROUP <groupname>");
        System.out.println("  ADDUSERTOGROUP <username> <group>");
        System.out.println("  REMOVEUSERFROMGROUP <username> <group>");
        System.out.println("  LISTGROUP");
        System.out.println("  EXIT");
    }
}
