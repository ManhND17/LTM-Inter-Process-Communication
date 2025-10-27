# 🪟 LDAP User Management System - Windows Setup Guide

## 📋 Yêu cầu hệ thống

- **Windows 10/11** (64-bit)
- **Python 3.8+** 
- **Docker Desktop for Windows** (để chạy OpenLDAP server)

---

## 🚀 Hướng dẫn cài đặt trên Windows

### Bước 1: Cài đặt Python

1. Tải Python từ: https://www.python.org/downloads/
2. **Quan trọng**: Tích chọn "Add Python to PATH" khi cài đặt
3. Kiểm tra cài đặt:
```cmd
python --version
pip --version
```

### Bước 2: Cài đặt Docker Desktop

1. Tải Docker Desktop: https://www.docker.com/products/docker-desktop
2. Cài đặt và khởi động Docker Desktop
3. Đợi Docker khởi động hoàn tất (icon Docker màu xanh trên system tray)

### Bước 3: Cài đặt thư viện Python

**⚠️ Lưu ý quan trọng cho Windows:**

Thư viện `python-ldap` khó cài trên Windows. Có 2 cách:

#### **Cách 1: Dùng python-ldap (Khuyến nghị cho Windows)**

Cài đặt bản wheel đã build sẵn:

```cmd
pip install python-ldap-3.4.0-cp311-cp311-win_amd64.whl
```

Hoặc tải wheel phù hợp từ: https://www.lfd.uci.edu/~gohlke/pythonlibs/#python-ldap

#### **Cách 2: Dùng ldap3 (Dễ hơn, Pure Python)**

```cmd
pip install ldap3
```

**Tôi khuyên dùng ldap3 vì:**
- Pure Python, không cần compile
- Cài đặt dễ dàng trên Windows
- API hiện đại hơn
- Tương thích tốt

### Bước 4: Khởi động OpenLDAP Server

Mở **PowerShell** hoặc **Command Prompt** và chạy:

```powershell
docker run -d -p 389:389 -p 636:636 --name openldap -e LDAP_ORGANISATION="MyCompany" -e LDAP_DOMAIN="example.com" -e LDAP_ADMIN_PASSWORD="admin123" osixia/openldap:latest
```

Kiểm tra Docker container đang chạy:
```cmd
docker ps
```

### Bước 5: Tạo thư mục project

```cmd
mkdir ldap_project
cd ldap_project
```

---

## 📁 Cấu trúc Project

```
ldap_project/
│
├── ldap_manager_ldap3.py      # Phiên bản dùng ldap3 (Windows friendly)
├── ldap_manager_original.py   # Phiên bản dùng python-ldap (nếu cài được)
├── requirements.txt            # Danh sách thư viện
├── config.py                   # File cấu hình
└── README.md                   # Hướng dẫn
```

---

## 💻 Code Files

### File 1: `requirements.txt`

```txt
ldap3>=2.9.1
```

### File 2: `config.py`

```python
"""
Cấu hình LDAP Server
"""

LDAP_CONFIG = {
    'server': 'localhost',
    'port': 389,
    'use_ssl': False,
    'base_dn': 'dc=example,dc=com',
    'admin_dn': 'cn=admin,dc=example,dc=com',
    'admin_password': 'admin123',
    'users_ou': 'ou=users,dc=example,dc=com',
    'groups_ou': 'ou=groups,dc=example,dc=com'
}
```

### File 3: `ldap_manager_ldap3.py`

Xem file trong artifact tiếp theo (code đầy đủ với ldap3)

---

## 🎯 Cách chạy

### Chạy bằng Python trực tiếp:

```cmd
python ldap_manager_ldap3.py
```

### Chạy từ PowerShell:

```powershell
python ldap_manager_ldap3.py
```

---

## 🐛 Xử lý lỗi thường gặp trên Windows

### Lỗi 1: "pip không được nhận dạng"
**Giải pháp:**
```cmd
python -m pip install ldap3
```

### Lỗi 2: "Docker không khởi động được"
**Giải pháp:**
- Bật Hyper-V trong Windows Features
- Khởi động lại máy
- Chạy Docker Desktop với quyền Administrator

### Lỗi 3: "python-ldap cài không được"
**Giải pháp:**
- Dùng ldap3 thay thế (khuyến nghị)
- Hoặc cài Visual C++ Build Tools

### Lỗi 4: "Connection refused [Errno 10061]"
**Giải pháp:**
- Kiểm tra Docker container đang chạy: `docker ps`
- Restart container: `docker restart openldap`
- Đợi 10 giây để LDAP khởi động hoàn tất

### Lỗi 5: "Module not found"
**Giải pháp:**
```cmd
pip install -r requirements.txt
```

---

## 📊 Test kết nối LDAP

### Test bằng PowerShell:

```powershell
Test-NetConnection -ComputerName localhost -Port 389
```

### Test bằng Python:

```python
from ldap3 import Server, Connection

server = Server('localhost', port=389)
conn = Connection(server, 'cn=admin,dc=example,dc=com', 'admin123')
print("Kết nối OK!" if conn.bind() else "Kết nối thất bại!")
```

---

## 🔧 Quản lý Docker Container

### Xem logs:
```cmd
docker logs openldap
```

### Dừng container:
```cmd
docker stop openldap
```

### Khởi động lại:
```cmd
docker start openldap
```

### Xóa container:
```cmd
docker rm -f openldap
```

---

## 📞 Support

Nếu gặp vấn đề, hãy:
1. Kiểm tra Docker đang chạy
2. Kiểm tra port 389 không bị chiếm dụng
3. Xem logs container: `docker logs openldap`
4. Thử restart container

---

## ✅ Checklist trước khi chạy

- [ ] Python đã cài và trong PATH
- [ ] Docker Desktop đã cài và đang chạy
- [ ] ldap3 đã cài: `pip list | findstr ldap3`
- [ ] OpenLDAP container đang chạy: `docker ps`
- [ ] Port 389 không bị chiếm: `netstat -an | findstr 389`

---

## 🎓 Next Steps

Sau khi chạy thành công demo, bạn có thể:
1. Tạo REST API với Flask
2. Thêm Web UI với Flask + Bootstrap
3. Tích hợp JWT authentication
4. Kết nối với ứng dụng thực tế