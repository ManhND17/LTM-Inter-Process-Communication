package server.module;

import org.apache.directory.api.ldap.model.name.Dn;

public class AccessControlManager {

    public boolean canRead(Dn requester, Dn target) {
        // Admin hoặc chính chủ thì được phép
        return isAdmin(requester) || requester.equals(target);
    }

    public boolean canModify(Dn requester, Dn target) {
        return isAdmin(requester);
    }

    public boolean canDelete(Dn requester, Dn target) {
        return isAdmin(requester);
    }

    private boolean isAdmin(Dn dn) {
        return dn.getName().contains("uid=admin,ou=system");
    }
}
