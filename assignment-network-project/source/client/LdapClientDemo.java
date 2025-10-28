package client;

import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;

public class LdapClientDemo {
    
    private static final String LDAP_HOST = "localhost";
    private static final int LDAP_PORT = 10389;
    private static final String ADMIN_DN = "uid=admin,ou=system";
    private static final String ADMIN_PASSWORD = "secret";
    private static final String BASE_DN = "dc=example,dc=com";
    
    public static void main(String[] args) {
        LdapClientDemo client = new LdapClientDemo();
        
        try {
            System.out.println("===========================================");
            System.out.println("       LDAP Client Demo Started");
            System.out.println("===========================================");
            System.out.println();
            
            client.connectAndAuthenticate();
            client.searchAllUsers();
            client.searchSpecificUser("john");
            client.authenticateUser("uid=john,ou=users,dc=example,dc=com", "password123");
            client.authenticateUser("uid=john,ou=users,dc=example,dc=com", "wrongpassword");
            
            System.out.println("===========================================");
            System.out.println("           Demo hoan tat!");
            System.out.println("===========================================");
            
        } catch (Exception e) {
            System.err.println("Loi: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void connectAndAuthenticate() throws Exception {
        System.out.println("-------------------------------------------");
        System.out.println("1. Ket noi toi LDAP Server");
        System.out.println("-------------------------------------------");
        
        LdapConnection connection = new LdapNetworkConnection(LDAP_HOST, LDAP_PORT);
        connection.bind(ADMIN_DN, ADMIN_PASSWORD);
        
        System.out.println("OK - Ket noi thanh cong!");
        System.out.println("Host: " + LDAP_HOST + ":" + LDAP_PORT);
        System.out.println("Admin: " + ADMIN_DN);
        System.out.println();
        
        connection.close();
    }
    
    public void searchAllUsers() throws Exception {
        System.out.println("-------------------------------------------");
        System.out.println("2. Tim kiem tat ca users");
        System.out.println("-------------------------------------------");
        
        LdapConnection connection = new LdapNetworkConnection(LDAP_HOST, LDAP_PORT);
        connection.bind(ADMIN_DN, ADMIN_PASSWORD);
        
        EntryCursor cursor = connection.search(
            "ou=users," + BASE_DN,
            "(objectClass=inetOrgPerson)",
            SearchScope.SUBTREE,
            "*"
        );
        
        int count = 0;
        while (cursor.next()) {
            Entry entry = cursor.get();
            count++;
            System.out.println();
            System.out.println("User #" + count + ":");
            System.out.println("  DN: " + entry.getDn());
            System.out.println("  Ten: " + entry.get("cn").getString());
            System.out.println("  Email: " + entry.get("mail").getString());
        }
        
        cursor.close();
        connection.close();
        System.out.println();
        System.out.println("OK - Tim thay " + count + " users");
        System.out.println();
    }
    
    public void searchSpecificUser(String uid) throws Exception {
        System.out.println("-------------------------------------------");
        System.out.println("3. Tim kiem user cu the: " + uid);
        System.out.println("-------------------------------------------");
        
        LdapConnection connection = new LdapNetworkConnection(LDAP_HOST, LDAP_PORT);
        connection.bind(ADMIN_DN, ADMIN_PASSWORD);
        
        EntryCursor cursor = connection.search(
            BASE_DN,
            "(uid=" + uid + ")",
            SearchScope.SUBTREE,
            "*"
        );
        
        if (cursor.next()) {
            Entry entry = cursor.get();
            System.out.println();
            System.out.println("OK - Tim thay user:");
            System.out.println("  DN: " + entry.getDn());
            System.out.println("  UID: " + entry.get("uid").getString());
            System.out.println("  CN: " + entry.get("cn").getString());
            System.out.println("  Email: " + entry.get("mail").getString());
        } else {
            System.out.println("KHONG tim thay user!");
        }
        
        cursor.close();
        connection.close();
        System.out.println();
    }
    
    public void authenticateUser(String userDn, String password) throws Exception {
        System.out.println("-------------------------------------------");
        System.out.println("4. Xac thuc user");
        System.out.println("-------------------------------------------");
        System.out.println("User DN: " + userDn);
        System.out.println("Password: " + password);
        
        LdapConnection connection = new LdapNetworkConnection(LDAP_HOST, LDAP_PORT);
        
        try {
            connection.bind(userDn, password);
            System.out.println("OK - Xac thuc thanh cong!");
            connection.close();
        } catch (Exception e) {
            System.out.println("FAIL - Xac thuc that bai!");
            System.out.println("Ly do: " + e.getMessage());
        }
        
        System.out.println();
    }
}