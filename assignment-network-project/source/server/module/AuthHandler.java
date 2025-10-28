package server.module;

import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.DirectoryService;

public class AuthHandler {

    private final DirectoryService service;

    public AuthHandler(DirectoryService service) {
        this.service = service;
    }

    public boolean authenticate(String dn, String password) {
        try {
            // Kiểm tra DN tồn tại
            Dn userDn = new Dn(dn);
            if (!service.getAdminSession().exists(userDn)) {
                System.out.println("❌ DN không tồn tại: " + dn);
                return false;
            }

            // Gọi phương thức xác thực chuẩn
            service.getSession(userDn, password.getBytes());
            System.out.println("✅ Auth success for " + dn);
            return true;
        } catch (LdapAuthenticationException e) {
            System.out.println("❌ Sai mật khẩu cho DN: " + dn);
            return false;
        } catch (Exception e) {
            System.out.println("❌ Lỗi xác thực: " + e.getMessage());
            return false;
        }
    }
}
