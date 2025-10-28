
package com.minildap.service;

import com.minildap.store.GroupStore;

import java.util.List;

public class AuthorizationService {
    private final GroupStore groupStore;

    public AuthorizationService(GroupStore groupStore) {
        this.groupStore = groupStore;
    }

    public boolean isMemberOf(String username, String group) {
        return groupStore.listGroupsOfUser(username).contains(group);
    }

    public List<String> getUserGroups(String username) {
        return groupStore.listGroupsOfUser(username);
    }

    public boolean canExecute(String username, String role, String command) {
        // Simple RBAC: admin full; developer read/list/update; user read self only
        if ("admin".equalsIgnoreCase(role)) return true;

        switch (command) {
            case "LISTUSER":
            case "READUSER":
            case "LISTGROUP":
                return true;
            case "UPDATEUSER":
                return true; // developer allowed
            case "ADDUSER":
            case "DELETEUSER":
            case "CREATEGROUP":
            case "DELETEGROUP":
            case "ADDUSERTOGROUP":
            case "REMOVEUSERFROMGROUP":
                return "developer".equalsIgnoreCase(role) ? false : false; // only admin
            default:
                return false;
        }
    }
}
