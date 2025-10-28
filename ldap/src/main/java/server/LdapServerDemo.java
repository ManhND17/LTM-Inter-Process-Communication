package server;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.core.api.InstanceLayout;
public class LdapServerDemo {

    private DirectoryService service;
    private LdapServer ldapServer;

    public static void main(String[] args) {
        try {
            LdapServerDemo server = new LdapServerDemo();
            server.startServer();

            System.out.println("===========================================");
            System.out.println("✅ LDAP Server đã khởi động thành công!");
            System.out.println("===========================================");
            System.out.println("Host: localhost");
            System.out.println("Port: 10389");
            System.out.println("Base DN: dc=example,dc=com");
            System.out.println("Admin DN: uid=admin,ou=system");
            System.out.println("Admin Password: secret");
            System.out.println();
            System.out.println("Users đã tạo:");
            System.out.println("  - john/password123");
            System.out.println("  - jane/password456");
            System.out.println();
            System.out.println("Nhấn Enter để dừng server...");
            System.in.read();
            server.stopServer();

        } catch (Exception e) {
            System.err.println("❌ Lỗi khởi động server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void startServer() throws Exception {
        // ✅ Factory sẽ tự nạp schema LDIF
        DefaultDirectoryServiceFactory factory = new DefaultDirectoryServiceFactory();
        factory.init("default");

        service = factory.getDirectoryService();
        service.getChangeLog().setEnabled(false);
        service.setAllowAnonymousAccess(true);
        InstanceLayout instanceLayout = new InstanceLayout("target/apacheds");  // Hoặc "/tmp/apacheds" nếu trên Linux/Mac
        service.setInstanceLayout(instanceLayout);
        SchemaManager schemaManager = service.getSchemaManager();
        DnFactory dnFactory = service.getDnFactory();

        // Partition chính
        AvlPartition partition = new AvlPartition(schemaManager, dnFactory);
        partition.setId("example");
        partition.setSuffixDn(new Dn("dc=example,dc=com"));
        service.addPartition(partition);

        // Startup sau khi add partition
        service.startup();

        // Base và dữ liệu mẫu
        createBaseStructure(schemaManager);
        addSampleUsers(schemaManager);

        // Cấu hình LDAP server
        ldapServer = new LdapServer();
        ldapServer.setDirectoryService(service);
        ldapServer.setTransports(new TcpTransport(10389));
        ldapServer.start();
    }

    public void stopServer() throws Exception {
        System.out.println("\n🛑 Dừng LDAP Server...");
        if (ldapServer != null) ldapServer.stop();
        if (service != null) service.shutdown();
        System.out.println("✅ LDAP Server đã dừng.");
    }

    private void createBaseStructure(SchemaManager schemaManager) throws Exception {
        Dn baseDn = new Dn("dc=example,dc=com");
        if (!service.getAdminSession().exists(baseDn)) {
            Entry entry = new DefaultEntry(schemaManager, baseDn);
            entry.add("objectClass", "top", "domain");
            entry.add("dc", "example");
            service.getAdminSession().add(entry);
        }

        Dn ouDn = new Dn("ou=users,dc=example,dc=com");
        if (!service.getAdminSession().exists(ouDn)) {
            Entry entry = new DefaultEntry(schemaManager, ouDn);
            entry.add("objectClass", "top", "organizationalUnit");
            entry.add("ou", "users");
            service.getAdminSession().add(entry);
        }
    }

    private void addSampleUsers(SchemaManager schemaManager) throws Exception {
        addUser(schemaManager, "john", "John", "Doe", "john@example.com", "password123");
        addUser(schemaManager, "jane", "Jane", "Smith", "jane@example.com", "password456");
    }

    private void addUser(SchemaManager schemaManager, String uid, String given, String sn,
                         String mail, String password) throws Exception {
        Dn dn = new Dn("uid=" + uid + ",ou=users,dc=example,dc=com");
        if (!service.getAdminSession().exists(dn)) {
            Entry e = new DefaultEntry(schemaManager, dn);
            e.add("objectClass", "top", "person", "inetOrgPerson");
            e.add("uid", uid);
            e.add("cn", given + " " + sn);
            e.add("sn", sn);
            e.add("givenName", given);
            e.add("mail", mail);
            e.add("userPassword", password);
            service.getAdminSession().add(e);
        }
    }
}
