package server.module;

import java.time.LocalDateTime;

public class AuditLogger {

    public void log(String action, String dn, String who) {
        System.out.printf("[%s] %-6s | %-25s | by: %s%n",
                LocalDateTime.now(), action.toUpperCase(), dn, who);
    }
}
