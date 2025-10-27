package com.minildap.service;

import com.minildap.models.Group;
import com.minildap.store.GroupStore;

import java.util.List;

public class GroupService {
    private final GroupStore groupStore;

    public GroupService(GroupStore groupStore) { this.groupStore = groupStore; }

    public void createGroup(String name) throws Exception {
        if (groupStore.findByName(name) != null) throw new Exception("Group exists");
        groupStore.addGroup(new Group(name));
        groupStore.save();
    }

    public void deleteGroup(String name) throws Exception {
        if (groupStore.findByName(name) == null) throw new Exception("Group not found");
        groupStore.removeGroup(name);
        groupStore.save();
    }

    public void addMember(String group, String username) throws Exception {
        if (groupStore.findByName(group) == null) throw new Exception("Group not found");
        groupStore.addMember(group, username);
        groupStore.save();
    }

    public void removeMember(String group, String username) throws Exception {
        if (groupStore.findByName(group) == null) throw new Exception("Group not found");
        groupStore.removeMember(group, username);
        groupStore.save();
    }

    public List<Group> listGroups() {
        return groupStore.getAllGroups();
    }
}
