package com.minildap.net;

import com.minildap.models.Group;
import com.minildap.models.User;
import com.minildap.security.PasswordUtils;
import com.minildap.service.*;
import com.minildap.store.GroupStore;
import com.minildap.store.UserStore;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

public class DirectoryServer {

    public static void main(String[] args) throws Exception {
        int port = 5050;
        File userDb = new File("data/users.db");
        File groupDb = new File("data/groups.db");

        UserStore userStore = new UserStore(userDb);
        GroupStore groupStore = new GroupStore(groupDb);

        userStore.load();
        groupStore.load();

        // Bootstrap default admin if not present
        if (userStore.findByUsername("admin") == null) {
            String hash = PasswordUtils.hashSSHA("admin123");
            userStore.addUser(new User("admin", hash, "admin@example.com", "System Admin", "admin"));
            userStore.save();
        }
        if (groupStore.findByName("admins") == null) {
            Group g = new Group("admins");
            g.addMember("admin");
            groupStore.addGroup(g);
            groupStore.save();
        }

        AuthService authService = new AuthService(userStore);
        AuthorizationService authzService = new AuthorizationService(groupStore);
        UserService userService = new UserService(userStore);
        GroupService groupService = new GroupService(groupStore);

        System.out.println("MiniLDAP Server listening on port " + port);
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket client = server.accept();
                System.out.println("Client connected: " + client.getRemoteSocketAddress());
                Thread t = new Thread(new CommandHandler(client, authService, authzService, userService, groupService));
                t.start();
            }
        }
    }
}
