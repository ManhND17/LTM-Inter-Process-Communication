package com.minildap.net;

import com.minildap.models.Group;
import com.minildap.models.User;
import com.minildap.service.*;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class CommandHandler implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final AuthorizationService authzService;
    private final UserService userService;
    private final GroupService groupService;

    private String currentUser = null;
    private String currentRole = null;

    public CommandHandler(Socket socket,
                          AuthService authService,
                          AuthorizationService authzService,
                          UserService userService,
                          GroupService groupService) {
        this.socket = socket;
        this.authService = authService;
        this.authzService = authzService;
        this.userService = userService;
        this.groupService = groupService;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
        ) {
            out.println("{\"status\":\"OK\",\"message\":\"MiniLDAP ready\"}");
            String line;
            while ((line = in.readLine()) != null) {
                String resp = handle(line.trim());
                out.println(resp);
                if ("__CLOSE__".equals(resp)) break;
            }
        } catch (IOException e) {
            // connection closed
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String okMsg(String msg) { return "{\"status\":\"OK\",\"message\":\""+escape(msg)+"\"}"; }
    private String errMsg(String msg) { return "{\"status\":\"ERROR\",\"message\":\""+escape(msg)+"\"}"; }

    private String escape(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }

    private String handle(String cmd) {
        if (cmd.isEmpty()) return errMsg("Empty command");
        String[] parts = cmd.split("\\s+");
        String op = parts[0].toUpperCase();

        try {
            switch (op) {
                case "AUTH": {
                    if (parts.length < 3) return errMsg("Usage: AUTH <username> <password>");
                    User u = authService.authenticate(parts[1], parts[2]);
                    currentUser = u.getUsername();
                    currentRole = u.getRole();
                    List<String> groups = authzService.getUserGroups(currentUser);
                    String gjson = groups.stream().map(g -> "\""+escape(g)+"\"").collect(Collectors.joining(","));
                    return "{\"status\":\"OK\",\"role\":\""+escape(currentRole)+"\",\"groups\":["+gjson+"],\"message\":\"Welcome "+escape(currentUser)+"\"}";
                }
                case "LOGOUT": {
                    currentUser = null; currentRole = null;
                    return okMsg("Logged out");
                }
                case "PING": {
                    return "{\"status\":\"OK\",\"time\":\""+ LocalDateTime.now() +"\"}";
                }
                case "EXIT": {
                    return "__CLOSE__";
                }

                // ---- User ops ----
                case "ADDUSER": {
                    requireAdminOr(op);
                    if (parts.length < 5) return errMsg("Usage: ADDUSER <username> <password> <role> [email] [fullName]");
                    String username = parts[1], password = parts[2], role = parts[3];
                    String email = parts.length >= 5 ? parts[4] : "";
                    String fullName = parts.length >= 6 ? joinFrom(parts,5) : "";
                    userService.createUser(username, password, email, fullName, role);
                    return okMsg("User added");
                }
                case "READUSER": {
                    requireAuth(op);
                    if (parts.length < 2) return errMsg("Usage: READUSER <username>");
                    String username = parts[1];
                    // users can read themselves, dev/admin can read anyone
                    if (!username.equals(currentUser) && !"admin".equalsIgnoreCase(currentRole) && !"developer".equalsIgnoreCase(currentRole))
                        return errMsg("Permission denied");
                    User u = userService.readUser(username);
                    return "{\"status\":\"OK\",\"user\":"+u.toJsonPublic()+"}";
                }
                case "UPDATEUSER": {
                    requireAuth(op);
                    if (parts.length < 4) return errMsg("Usage: UPDATEUSER <username> <email> <fullName>");
                    String username = parts[1];
                    String email = parts[2];
                    String fullName = joinFrom(parts,3);
                    // users can update themselves; dev/admin can update anyone
                    if (!username.equals(currentUser) && !"admin".equalsIgnoreCase(currentRole) && !"developer".equalsIgnoreCase(currentRole))
                        return errMsg("Permission denied");
                    userService.updateUser(username, email, fullName);
                    return okMsg("User updated");
                }
                case "DELETEUSER": {
                    requireAdminOr(op);
                    if (parts.length < 2) return errMsg("Usage: DELETEUSER <username>");
                    userService.deleteUser(parts[1]);
                    return okMsg("User deleted");
                }
                case "LISTUSER": {
                    requireAuth(op);
                    List<User> users = userService.listAllUsers();
                    String body = users.stream().map(User::toJsonPublic).collect(Collectors.joining(","));
                    return "{\"status\":\"OK\",\"users\":["+body+"]}";
                }

                // ---- Group ops ----
                case "CREATEGROUP": {
                    requireAdminOr(op);
                    if (parts.length < 2) return errMsg("Usage: CREATEGROUP <groupname>");
                    groupService.createGroup(parts[1]);
                    return okMsg("Group created");
                }
                case "DELETEGROUP": {
                    requireAdminOr(op);
                    if (parts.length < 2) return errMsg("Usage: DELETEGROUP <groupname>");
                    groupService.deleteGroup(parts[1]);
                    return okMsg("Group deleted");
                }
                case "ADDUSERTOGROUP": {
                    requireAdminOr(op);
                    if (parts.length < 3) return errMsg("Usage: ADDUSERTOGROUP <username> <group>");
                    groupService.addMember(parts[2], parts[1]); // note: <username> <group>
                    return okMsg("Member added");
                }
                case "REMOVEUSERFROMGROUP": {
                    requireAdminOr(op);
                    if (parts.length < 3) return errMsg("Usage: REMOVEUSERFROMGROUP <username> <group>");
                    groupService.removeMember(parts[2], parts[1]);
                    return okMsg("Member removed");
                }
                case "LISTGROUP": {
                    requireAuth(op);
                    List<Group> gs = groupService.listGroups();
                    String body = gs.stream().map(Group::toJson).collect(Collectors.joining(","));
                    return "{\"status\":\"OK\",\"groups\":["+body+"]}";
                }

                default:
                    return errMsg("Unknown command: " + op);
            }
        } catch (Exception e) {
            return errMsg(e.getMessage());
        }
    }

    private void requireAuth(String op) throws Exception {
        if (currentUser == null) throw new Exception("Authenticate first");
        if (!new AuthorizationService(groupService==null?null:null).canExecute(currentUser, currentRole, op)) {
            // we call our existing canExecute logic directly:
            if (!canExecuteSimple(currentRole, op)) throw new Exception("Permission denied");
        }
    }

    private void requireAdminOr(String op) throws Exception {
        if (currentUser == null) throw new Exception("Authenticate first");
        if (!"admin".equalsIgnoreCase(currentRole)) throw new Exception("Permission denied");
    }

    // mirror of AuthorizationService.canExecute to avoid re-instantiation
    private boolean canExecuteSimple(String role, String op) {
        if ("admin".equalsIgnoreCase(role)) return true;
        switch (op) {
            case "LISTUSER":
            case "READUSER":
            case "LISTGROUP":
            case "UPDATEUSER":
                return true;
            default:
                return false;
        }
    }

    private static String joinFrom(String[] arr, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i=start;i<arr.length;i++){
            if (i>start) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
