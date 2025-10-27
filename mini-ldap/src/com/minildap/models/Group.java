package com.minildap.models;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Group {
    private String name;
    private Set<String> members = new LinkedHashSet<>();

    public Group(String name) { this.name = name; }

    public String getName() { return name; }
    public Set<String> getMembers() { return members; }

    public void addMember(String username) { members.add(username); }
    public void removeMember(String username) { members.remove(username); }

    public String toCsv() {
        String joined = members.stream().collect(Collectors.joining(";"));
        return name + "," + joined;
    }

    public static Group fromCsv(String line) {
        String[] p = line.split(",", -1);
        Group g = new Group(p[0]);
        if (p.length > 1 && !p[1].isEmpty()) {
            for (String m : p[1].split(";", -1)) g.addMember(m);
        }
        return g;
    }

    public String toJson() {
        String ms = members.stream().map(m -> "\""+m.replace("\"","\\\"")+"\"").collect(Collectors.joining(","));
        return "{\"name\":\""+name.replace("\"","\\\"")+"\",\"members\":["+ms+"]}";
    }
}
