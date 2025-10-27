"""
Centralized User Directory System using LDAP Protocol
Phiên bản Windows - Sử dụng ldap3 (Pure Python)

Yêu cầu cài đặt:
pip install ldap3

Khởi động OpenLDAP Server (Docker):
docker run -d -p 389:389 -p 636:636 --name openldap -e LDAP_ORGANISATION="MyCompany" -e LDAP_DOMAIN="example.com" -e LDAP_ADMIN_PASSWORD="admin123" osixia/openldap:latest
"""

from ldap3 import Server, Connection, ALL, MODIFY_ADD, MODIFY_DELETE, MODIFY_REPLACE
from ldap3.core.exceptions import LDAPException, LDAPBindError, LDAPEntryAlreadyExistsResult
import hashlib
import base64
from typing import List, Dict, Optional


class LDAPManager:
    """Quản lý LDAP Server - Authentication, Authorization, CRUD Operations"""
    
    def __init__(self, server: str, port: int, base_dn: str, admin_dn: str, admin_password: str):
        """
        Khởi tạo kết nối LDAP
        
        Args:
            server: LDAP server hostname (vd: localhost)
            port: LDAP port (vd: 389)
            base_dn: Base Distinguished Name (vd: dc=example,dc=com)
            admin_dn: Admin DN (vd: cn=admin,dc=example,dc=com)
            admin_password: Mật khẩu admin
        """
        self.server = Server(server, port=port, get_info=ALL)
        self.base_dn = base_dn
        self.admin_dn = admin_dn
        self.admin_password = admin_password
        self.conn = None
        
    def connect(self) -> bool:
        """Kết nối đến LDAP server với quyền admin"""
        try:
            self.conn = Connection(
                self.server,
                user=self.admin_dn,
                password=self.admin_password,
                auto_bind=True
            )
            print(f"✓ Kết nối LDAP thành công: {self.server}")
            return True
        except LDAPBindError as e:
            print(f"✗ Lỗi xác thực admin: {e}")
            return False
        except LDAPException as e:
            print(f"✗ Lỗi kết nối LDAP: {e}")
            return False
    
    def disconnect(self):
        """Đóng kết nối LDAP"""
        if self.conn:
            self.conn.unbind()
            print("✓ Đã ngắt kết nối LDAP")
    
    # ==================== AUTHENTICATION ====================
    
    def authenticate_user(self, username: str, password: str) -> bool:
        """
        Xác thực người dùng qua LDAP
        
        Args:
            username: Tên đăng nhập
            password: Mật khẩu
            
        Returns:
            True nếu xác thực thành công
        """
        try:
            user_dn = f"uid={username},ou=users,{self.base_dn}"
            temp_conn = Connection(
                self.server,
                user=user_dn,
                password=password,
                auto_bind=True
            )
            temp_conn.unbind()
            print(f"✓ Xác thực thành công: {username}")
            return True
        except LDAPBindError:
            print(f"✗ Xác thực thất bại: Sai mật khẩu hoặc user không tồn tại ({username})")
            return False
        except LDAPException as e:
            print(f"✗ Lỗi xác thực: {e}")
            return False
    
    # ==================== AUTHORIZATION ====================
    
    def get_user_groups(self, username: str) -> List[str]:
        """
        Lấy danh sách các group mà user thuộc về
        
        Args:
            username: Tên đăng nhập
            
        Returns:
            Danh sách tên các group
        """
        try:
            search_filter = f"(memberUid={username})"
            self.conn.search(
                search_base=f"ou=groups,{self.base_dn}",
                search_filter=search_filter,
                attributes=['cn']
            )
            
            groups = [entry.cn.value for entry in self.conn.entries]
            print(f"✓ User '{username}' thuộc {len(groups)} group(s): {groups}")
            return groups
        except LDAPException as e:
            print(f"✗ Lỗi lấy danh sách group: {e}")
            return []
    
    def check_user_permission(self, username: str, required_group: str) -> bool:
        """
        Kiểm tra quyền truy cập của user dựa trên group
        
        Args:
            username: Tên đăng nhập
            required_group: Group yêu cầu
            
        Returns:
            True nếu user có quyền
        """
        groups = self.get_user_groups(username)
        has_permission = required_group in groups
        
        if has_permission:
            print(f"✓ User '{username}' có quyền truy cập (group: {required_group})")
        else:
            print(f"✗ User '{username}' KHÔNG có quyền truy cập (cần group: {required_group})")
        
        return has_permission
    
    # ==================== USER CRUD ====================
    
    def create_user(self, username: str, password: str, full_name: str, email: str) -> bool:
        """
        Tạo user mới trong LDAP
        
        Args:
            username: Tên đăng nhập (uid)
            password: Mật khẩu
            full_name: Họ và tên đầy đủ
            email: Email
            
        Returns:
            True nếu tạo thành công
        """
        try:
            user_dn = f"uid={username},ou=users,{self.base_dn}"
            
            # Mã hóa mật khẩu SSHA (Salted SHA)
            salt = hashlib.sha1(username.encode()).digest()[:4]
            pwd_hash = hashlib.sha1(password.encode() + salt).digest()
            ssha_password = '{SSHA}' + base64.b64encode(pwd_hash + salt).decode()
            
            # Tách họ và tên
            name_parts = full_name.split()
            given_name = name_parts[0] if name_parts else full_name
            surname = name_parts[-1] if len(name_parts) > 1 else full_name
            
            attributes = {
                'objectClass': ['inetOrgPerson', 'posixAccount', 'top'],
                'uid': username,
                'cn': full_name,
                'sn': surname,
                'givenName': given_name,
                'mail': email,
                'userPassword': ssha_password,
                'uidNumber': str(self._get_next_uid()),
                'gidNumber': '10000',
                'homeDirectory': f'/home/{username}',
                'loginShell': '/bin/bash'
            }
            
            self.conn.add(user_dn, attributes=attributes)
            
            if self.conn.result['result'] == 0:
                print(f"✓ Tạo user thành công: {username} ({full_name})")
                return True
            else:
                print(f"✗ Lỗi tạo user: {self.conn.result['description']}")
                return False
                
        except LDAPEntryAlreadyExistsResult:
            print(f"✗ User đã tồn tại: {username}")
            return False
        except LDAPException as e:
            print(f"✗ Lỗi tạo user: {e}")
            return False
    
    def get_user(self, username: str) -> Optional[Dict]:
        """
        Lấy thông tin user
        
        Args:
            username: Tên đăng nhập
            
        Returns:
            Dictionary chứa thông tin user hoặc None
        """
        try:
            user_dn = f"uid={username},ou=users,{self.base_dn}"
            self.conn.search(
                search_base=user_dn,
                search_filter='(objectClass=*)',
                attributes=['uid', 'cn', 'mail', 'uidNumber']
            )
            
            if self.conn.entries:
                entry = self.conn.entries[0]
                user_info = {
                    'username': entry.uid.value,
                    'full_name': entry.cn.value,
                    'email': entry.mail.value if hasattr(entry, 'mail') else '',
                    'uid_number': entry.uidNumber.value
                }
                print(f"✓ Tìm thấy user: {user_info}")
                return user_info
            else:
                print(f"✗ Không tìm thấy user: {username}")
                return None
            
        except LDAPException as e:
            print(f"✗ Lỗi lấy thông tin user: {e}")
            return None
    
    def update_user(self, username: str, email: Optional[str] = None, 
                    full_name: Optional[str] = None) -> bool:
        """
        Cập nhật thông tin user
        
        Args:
            username: Tên đăng nhập
            email: Email mới (optional)
            full_name: Họ tên mới (optional)
            
        Returns:
            True nếu cập nhật thành công
        """
        try:
            user_dn = f"uid={username},ou=users,{self.base_dn}"
            changes = {}
            
            if email:
                changes['mail'] = [(MODIFY_REPLACE, [email])]
            
            if full_name:
                name_parts = full_name.split()
                surname = name_parts[-1] if len(name_parts) > 1 else full_name
                changes['cn'] = [(MODIFY_REPLACE, [full_name])]
                changes['sn'] = [(MODIFY_REPLACE, [surname])]
            
            if changes:
                self.conn.modify(user_dn, changes)
                
                if self.conn.result['result'] == 0:
                    print(f"✓ Cập nhật user thành công: {username}")
                    return True
                else:
                    print(f"✗ Lỗi cập nhật: {self.conn.result['description']}")
                    return False
            
            return True
            
        except LDAPException as e:
            print(f"✗ Lỗi cập nhật user: {e}")
            return False
    
    def delete_user(self, username: str) -> bool:
        """
        Xóa user khỏi LDAP
        
        Args:
            username: Tên đăng nhập
            
        Returns:
            True nếu xóa thành công
        """
        try:
            user_dn = f"uid={username},ou=users,{self.base_dn}"
            self.conn.delete(user_dn)
            
            if self.conn.result['result'] == 0:
                print(f"✓ Xóa user thành công: {username}")
                return True
            else:
                print(f"✗ Lỗi xóa user: {self.conn.result['description']}")
                return False
                
        except LDAPException as e:
            print(f"✗ Lỗi xóa user: {e}")
            return False
    
    def list_all_users(self) -> List[Dict]:
        """
        Liệt kê tất cả users trong hệ thống
        
        Returns:
            Danh sách các user
        """
        try:
            self.conn.search(
                search_base=f"ou=users,{self.base_dn}",
                search_filter='(objectClass=inetOrgPerson)',
                attributes=['uid', 'cn', 'mail']
            )
            
            users = []
            for entry in self.conn.entries:
                users.append({
                    'username': entry.uid.value,
                    'full_name': entry.cn.value,
                    'email': entry.mail.value if hasattr(entry, 'mail') else ''
                })
            
            print(f"✓ Tìm thấy {len(users)} user(s)")
            return users
            
        except LDAPException as e:
            print(f"✗ Lỗi liệt kê users: {e}")
            return []
    
    # ==================== GROUP CRUD ====================
    
    def create_group(self, group_name: str, description: str = "") -> bool:
        """
        Tạo group mới trong LDAP
        
        Args:
            group_name: Tên group
            description: Mô tả group
            
        Returns:
            True nếu tạo thành công
        """
        try:
            group_dn = f"cn={group_name},ou=groups,{self.base_dn}"
            
            attributes = {
                'objectClass': ['posixGroup', 'top'],
                'cn': group_name,
                'gidNumber': str(self._get_next_gid())
            }
            
            if description:
                attributes['description'] = description
            
            self.conn.add(group_dn, attributes=attributes)
            
            if self.conn.result['result'] == 0:
                print(f"✓ Tạo group thành công: {group_name}")
                return True
            else:
                print(f"✗ Lỗi tạo group: {self.conn.result['description']}")
                return False
                
        except LDAPEntryAlreadyExistsResult:
            print(f"✗ Group đã tồn tại: {group_name}")
            return False
        except LDAPException as e:
            print(f"✗ Lỗi tạo group: {e}")
            return False
    
    def add_user_to_group(self, username: str, group_name: str) -> bool:
        """
        Thêm user vào group
        
        Args:
            username: Tên đăng nhập
            group_name: Tên group
            
        Returns:
            True nếu thêm thành công
        """
        try:
            group_dn = f"cn={group_name},ou=groups,{self.base_dn}"
            changes = {
                'memberUid': [(MODIFY_ADD, [username])]
            }
            
            self.conn.modify(group_dn, changes)
            
            if self.conn.result['result'] == 0:
                print(f"✓ Thêm user '{username}' vào group '{group_name}' thành công")
                return True
            elif self.conn.result['result'] == 20:  # Type or value exists
                print(f"⚠ User '{username}' đã có trong group '{group_name}'")
                return True
            else:
                print(f"✗ Lỗi: {self.conn.result['description']}")
                return False
                
        except LDAPException as e:
            print(f"✗ Lỗi thêm user vào group: {e}")
            return False
    
    def remove_user_from_group(self, username: str, group_name: str) -> bool:
        """
        Xóa user khỏi group
        
        Args:
            username: Tên đăng nhập
            group_name: Tên group
            
        Returns:
            True nếu xóa thành công
        """
        try:
            group_dn = f"cn={group_name},ou=groups,{self.base_dn}"
            changes = {
                'memberUid': [(MODIFY_DELETE, [username])]
            }
            
            self.conn.modify(group_dn, changes)
            
            if self.conn.result['result'] == 0:
                print(f"✓ Xóa user '{username}' khỏi group '{group_name}' thành công")
                return True
            elif self.conn.result['result'] == 16:  # No such attribute
                print(f"⚠ User '{username}' không có trong group '{group_name}'")
                return False
            else:
                print(f"✗ Lỗi: {self.conn.result['description']}")
                return False
                
        except LDAPException as e:
            print(f"✗ Lỗi xóa user khỏi group: {e}")
            return False
    
    def delete_group(self, group_name: str) -> bool:
        """
        Xóa group khỏi LDAP
        
        Args:
            group_name: Tên group
            
        Returns:
            True nếu xóa thành công
        """
        try:
            group_dn = f"cn={group_name},ou=groups,{self.base_dn}"
            self.conn.delete(group_dn)
            
            if self.conn.result['result'] == 0:
                print(f"✓ Xóa group thành công: {group_name}")
                return True
            else:
                print(f"✗ Lỗi xóa group: {self.conn.result['description']}")
                return False
                
        except LDAPException as e:
            print(f"✗ Lỗi xóa group: {e}")
            return False
    
    def list_all_groups(self) -> List[Dict]:
        """
        Liệt kê tất cả groups trong hệ thống
        
        Returns:
            Danh sách các group và members
        """
        try:
            self.conn.search(
                search_base=f"ou=groups,{self.base_dn}",
                search_filter='(objectClass=posixGroup)',
                attributes=['cn', 'memberUid', 'description']
            )
            
            groups = []
            for entry in self.conn.entries:
                members = entry.memberUid.values if hasattr(entry, 'memberUid') else []
                groups.append({
                    'name': entry.cn.value,
                    'members': members,
                    'description': entry.description.value if hasattr(entry, 'description') else ''
                })
            
            print(f"✓ Tìm thấy {len(groups)} group(s)")
            return groups
            
        except LDAPException as e:
            print(f"✗ Lỗi liệt kê groups: {e}")
            return []
    
    # ==================== UTILITY METHODS ====================
    
    def _get_next_uid(self) -> int:
        """Lấy UID number tiếp theo cho user mới"""
        try:
            self.conn.search(
                search_base=f"ou=users,{self.base_dn}",
                search_filter='(uidNumber=*)',
                attributes=['uidNumber']
            )
            
            if self.conn.entries:
                uids = [int(entry.uidNumber.value) for entry in self.conn.entries]
                return max(uids) + 1 if uids else 10001
            return 10001
            
        except:
            return 10001
    
    def _get_next_gid(self) -> int:
        """Lấy GID number tiếp theo cho group mới"""
        try:
            self.conn.search(
                search_base=f"ou=groups,{self.base_dn}",
                search_filter='(gidNumber=*)',
                attributes=['gidNumber']
            )
            
            if self.conn.entries:
                gids = [int(entry.gidNumber.value) for entry in self.conn.entries]
                return max(gids) + 1 if gids else 20001
            return 20001
            
        except:
            return 20001
    
    def initialize_directory_structure(self) -> bool:
        """
        Khởi tạo cấu trúc thư mục LDAP (OUs cho users và groups)
        Chỉ cần chạy 1 lần khi setup lần đầu
        """
        try:
            # Tạo OU cho users
            users_dn = f"ou=users,{self.base_dn}"
            users_attrs = {
                'objectClass': ['organizationalUnit', 'top'],
                'ou': 'users'
            }
            
            self.conn.add(users_dn, attributes=users_attrs)
            if self.conn.result['result'] == 0:
                print(f"✓ Tạo OU users thành công")
            elif self.conn.result['result'] == 68:  # Already exists
                print(f"⚠ OU users đã tồn tại")
            
            # Tạo OU cho groups
            groups_dn = f"ou=groups,{self.base_dn}"
            groups_attrs = {
                'objectClass': ['organizationalUnit', 'top'],
                'ou': 'groups'
            }
            
            self.conn.add(groups_dn, attributes=groups_attrs)
            if self.conn.result['result'] == 0:
                print(f"✓ Tạo OU groups thành công")
            elif self.conn.result['result'] == 68:  # Already exists
                print(f"⚠ OU groups đã tồn tại")
            
            return True
            
        except LDAPException as e:
            print(f"✗ Lỗi khởi tạo cấu trúc: {e}")
            return False


# ==================== DEMO APPLICATION ====================

def print_separator(title: str = ""):
    """In dòng phân cách"""
    print("\n" + "="*70)
    if title:
        print(f"  {title}")
        print("="*70)


def demo_authentication(ldap_mgr: LDAPManager):
    """Demo xác thực người dùng"""
    print_separator("1. AUTHENTICATION - XÁC THỰC NGƯỜI DÙNG")
    
    # Test xác thực thành công
    print("\n[Test 1] Xác thực với thông tin đúng:")
    ldap_mgr.authenticate_user("nguyenvana", "password123")
    
    # Test xác thực thất bại - sai mật khẩu
    print("\n[Test 2] Xác thực với mật khẩu sai:")
    ldap_mgr.authenticate_user("nguyenvana", "wrongpassword")
    
    # Test xác thực thất bại - user không tồn tại
    print("\n[Test 3] Xác thực với user không tồn tại:")
    ldap_mgr.authenticate_user("userkhongtontai", "password123")


def demo_authorization(ldap_mgr: LDAPManager):
    """Demo phân quyền dựa trên group"""
    print_separator("2. AUTHORIZATION - PHÂN QUYỀN THEO GROUP")
    
    print("\n[Test 1] Kiểm tra quyền admin của nguyenvana:")
    ldap_mgr.check_user_permission("nguyenvana", "admins")
    
    print("\n[Test 2] Kiểm tra quyền developer của tranthib:")
    ldap_mgr.check_user_permission("tranthib", "developers")
    
    print("\n[Test 3] Kiểm tra quyền admin của tranthib (không có quyền):")
    ldap_mgr.check_user_permission("tranthib", "admins")
    
    print("\n[Test 4] Lấy tất cả group của nguyenvana:")
    ldap_mgr.get_user_groups("nguyenvana")


def demo_user_crud(ldap_mgr: LDAPManager):
    """Demo CRUD operations cho User"""
    print_separator("3. USER CRUD - QUẢN LÝ NGƯỜI DÙNG")
    
    # CREATE
    print("\n[CREATE] Tạo user mới:")
    ldap_mgr.create_user(
        username="phamvanc",
        password="password789",
        full_name="Phạm Văn C",
        email="phamvanc@example.com"
    )
    
    # READ
    print("\n[READ] Đọc thông tin user vừa tạo:")
    ldap_mgr.get_user("phamvanc")
    
    # UPDATE
    print("\n[UPDATE] Cập nhật thông tin user:")
    ldap_mgr.update_user(
        username="phamvanc",
        email="phamvanc.new@example.com",
        full_name="Phạm Văn C Updated"
    )
    
    print("\n[READ] Đọc lại thông tin sau khi update:")
    ldap_mgr.get_user("phamvanc")
    
    # LIST
    print("\n[LIST] Liệt kê tất cả users:")
    users = ldap_mgr.list_all_users()
    for i, user in enumerate(users, 1):
        print(f"  {i}. {user['username']:15} - {user['full_name']:20} - {user['email']}")
    
    # DELETE
    print("\n[DELETE] Xóa user:")
    ldap_mgr.delete_user("phamvanc")
    
    print("\n[VERIFY] Kiểm tra user đã bị xóa:")
    ldap_mgr.get_user("phamvanc")


def demo_group_crud(ldap_mgr: LDAPManager):
    """Demo CRUD operations cho Group"""
    print_separator("4. GROUP CRUD - QUẢN LÝ NHÓM")
    
    # CREATE GROUP
    print("\n[CREATE] Tạo group mới:")
    ldap_mgr.create_group("testers", "QA Testing Team")
    
    # ADD USERS TO GROUP
    print("\n[ADD MEMBER] Thêm users vào group:")
    ldap_mgr.add_user_to_group("tranthib", "testers")
    ldap_mgr.add_user_to_group("nguyenvana", "testers")
    
    # CHECK MEMBERSHIP
    print("\n[CHECK] Kiểm tra membership:")
    ldap_mgr.get_user_groups("tranthib")
    
    # LIST GROUPS
    print("\n[LIST] Liệt kê tất cả groups:")
    groups = ldap_mgr.list_all_groups()
    for i, group in enumerate(groups, 1):
        members_str = ', '.join(group['members']) if group['members'] else 'Empty'
        print(f"  {i}. {group['name']:15} - Members: {members_str}")
        if group['description']:
            print(f"     → {group['description']}")
    
    # REMOVE USER FROM GROUP
    print("\n[REMOVE MEMBER] Xóa user khỏi group:")
    ldap_mgr.remove_user_from_group("nguyenvana", "testers")
    
    # DELETE GROUP
    print("\n[DELETE] Xóa group:")
    ldap_mgr.delete_group("testers")


def setup_demo_data(ldap_mgr: LDAPManager):
    """Tạo dữ liệu demo ban đầu"""
    print_separator("SETUP - TẠO DỮ LIỆU DEMO")
    
    # Khởi tạo cấu trúc thư mục
    ldap_mgr.initialize_directory_structure()
    
    # Tạo các group
    print("\n[GROUPS] Tạo các group demo:")
    ldap_mgr.create_group("admins", "System Administrators")
    ldap_mgr.create_group("developers", "Software Developers")
    ldap_mgr.create_group("users", "Regular Users")
    
    # Tạo các user
    print("\n[USERS] Tạo các user demo:")
    ldap_mgr.create_user(
        username="nguyenvana",
        password="password123",
        full_name="Nguyễn Văn A",
        email="nguyenvana@example.com"
    )
    
    ldap_mgr.create_user(
        username="tranthib",
        password="password456",
        full_name="Trần Thị B",
        email="tranthib@example.com"
    )
    
    # Gán users vào groups
    print("\n[MEMBERSHIP] Gán users vào groups:")
    ldap_mgr.add_user_to_group("nguyenvana", "admins")
    ldap_mgr.add_user_to_group("nguyenvana", "developers")
    ldap_mgr.add_user_to_group("tranthib", "developers")
    ldap_mgr.add_user_to_group("tranthib", "users")


def check_docker_running():
    """Kiểm tra Docker container đang chạy"""
    import subprocess
    try:
        result = subprocess.run(
            ['docker', 'ps', '--filter', 'name=openldap', '--format', '{{.Names}}'],
            capture_output=True,
            text=True,
            timeout=5
        )
        return 'openldap' in result.stdout
    except:
        return False


def main():
    """Chương trình chính - Demo đầy đủ hệ thống"""
    
    print("""
╔══════════════════════════════════════════════════════════════════╗
║     CENTRALIZED USER DIRECTORY SYSTEM - LDAP MANAGEMENT          ║
║     Hệ thống Quản lý Người dùng Tập trung với LDAP              ║
║     Version: Windows Compatible (ldap3)                          ║
╚══════════════════════════════════════════════════════════════════╝
    """)
    
    # Kiểm tra Docker
    print("🔍 Kiểm tra Docker container...")
    if not check_docker_running():
        print("\n❌ OpenLDAP container không chạy!")
        print("\n💡 Hướng dẫn khởi động:")
        print("   Mở PowerShell hoặc Command Prompt và chạy:")
        print("   docker run -d -p 389:389 -p 636:636 \\")
        print("     --name openldap \\")
        print("     -e LDAP_ORGANISATION='MyCompany' \\")
        print("     -e LDAP_DOMAIN='example.com' \\")
        print("     -e LDAP_ADMIN_PASSWORD='admin123' \\")
        print("     osixia/openldap:latest")
        print("\n   Đợi 10 giây rồi chạy lại script này.")
        input("\nNhấn Enter để thoát...")
        return
    
    print("✓ Docker container đang chạy\n")
    
    # Cấu hình LDAP
    LDAP_CONFIG = {
        'server': 'localhost',
        'port': 389,
        'base_dn': 'dc=example,dc=com',
        'admin_dn': 'cn=admin,dc=example,dc=com',
        'admin_password': 'admin123'
    }
    
    # Khởi tạo LDAP Manager
    ldap_mgr = LDAPManager(**LDAP_CONFIG)
    
    try:
        # Kết nối đến LDAP server
        if not ldap_mgr.connect():
            print("\n❌ Không thể kết nối đến LDAP server!")
            print("💡 Thử restart container: docker restart openldap")
            input("\nNhấn Enter để thoát...")
            return
        
        # Setup dữ liệu demo
        print("\n⚠️  Setup dữ liệu demo?")
        print("    y = Tạo dữ liệu mới (chọn nếu chạy lần đầu)")
        print("    n = Sử dụng dữ liệu hiện có")
        
        setup_choice = input("\n    Nhập lựa chọn [y/n]: ").lower().strip()
        if setup_choice == 'y':
            setup_demo_data(ldap_mgr)
        
        # Chạy các demo
        demo_authentication(ldap_mgr)
        demo_authorization(ldap_mgr)
        demo_user_crud(ldap_mgr)
        demo_group_crud(ldap_mgr)
        
        print_separator("KẾT THÚC DEMO")
        print("\n✅ Demo hoàn tất!")
        print("\n📚 Các chức năng đã demo:")
        print("   ✓ Authentication - Xác thực người dùng")
        print("   ✓ Authorization - Phân quyền theo group")
        print("   ✓ User CRUD - Tạo, đọc, sửa, xóa user")
        print("   ✓ Group CRUD - Quản lý group và membership")
        
        print("\n🎯 Next Steps:")
        print("   1. Thử tạo REST API với Flask")
        print("   2. Thêm Web UI cho dễ quản lý")
        print("   3. Tích hợp với ứng dụng thực tế")
        
    except KeyboardInterrupt:
        print("\n\n⚠️  Dừng chương trình bởi người dùng")
    except Exception as e:
        print(f"\n❌ Lỗi: {e}")
        import traceback
        traceback.print_exc()
    finally:
        # Đóng kết nối
        ldap_mgr.disconnect()
        input("\n\nNhấn Enter để thoát...")


if __name__ == "__main__":
    main()