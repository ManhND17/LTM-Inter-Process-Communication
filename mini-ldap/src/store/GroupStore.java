package com.minildap.store;

import com.minildap.models.Group;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GroupStore {
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final File file;

    public GroupStore(File file) { this.file = file; }

    public synchronized void load() throws IOException {
        groups.clear();
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while((line=br.readLine())!=null){
                if (line.trim().isEmpty()) continue;
                Group g = Group.fromCsv(line);
                groups.put(g.getName(), g);
            }
        }
    }

    public synchronized void save() throws IOException {
        file.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file,false))) {
            for (Group g : groups.values()) pw.println(g.toCsv());
        }
    }

    public synchronized Group findByName(String name) { return groups.get(name); }

    public synchronized void addGroup(Group g) { groups.put(g.getName(), g); }

    public synchronized void removeGroup(String name) { groups.remove(name); }

    public synchronized void addMember(String group, String username) {
        Group g = groups.get(group);
        if (g != null) g.addMember(username);
    }

    public synchronized void removeMember(String group, String username) {
        Group g = groups.get(group);
        if (g != null) g.removeMember(username);
    }

    public synchronized List<Group> getAllGroups() {
        return new ArrayList<>(groups.values());
    }

    public synchronized List<String> listGroupsOfUser(String username) {
        return groups.values().stream()
                .filter(g -> g.getMembers().contains(username))
                .map(Group::getName)
                .collect(Collectors.toList());
    }
}
