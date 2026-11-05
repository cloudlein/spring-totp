# Panduan Import dan Penggunaan Postman Collection (Spring TOTP)

Folder ini berisi konfigurasi Postman Collection dan Environment lengkap untuk pengujian API **Spring Boot TOTP & Two-Factor Authentication**, dilengkapi dengan script otomatisasi pengujian, token capture, dan generator TOTP.

---

## 📁 Berkas yang Tersedia

| Berkas | Deskripsi |
|---|---|
| [`spring-totp.postman_collection.json`](file:///home/udin/projects/spring-totp/doc/spring-totp.postman_collection.json) | Koleksi endpoint Postman (v2.1.0) mencakup Auth, TOTP, Role, dan User Management. Lengkap dengan pre-request script, test script, dan collection variables. |
| [`spring-totp.postman_environment.json`](file:///home/udin/projects/spring-totp/doc/spring-totp.postman_environment.json) | Konfigurasi Environment lokal (`baseUrl`, kredensial, token, TOTP secret & code, dll). |

---

## 🚀 Cara Import ke Postman

1. Buka aplikasi **Postman**.
2. Klik tombol **Import** di pojok kiri atas (atau tekan `Ctrl + O` / `Cmd + O`).
3. Pilih tab **Files** lalu pilih kedua berkas di folder `doc/`:
   - `spring-totp.postman_collection.json`
   - `spring-totp.postman_environment.json`
4. Di pojok kanan atas Postman, pilih environment aktif: **Spring TOTP Local Environment**.

---

## ⚙️ Variabel Postman

Variabel telah didefinisikan baik pada tingkat **Collection** maupun **Environment**:

| Nama Variabel | Tipe | Nilai Default | Deskripsi |
|---|---|---|---|
| `baseUrl` | default | `http://localhost:8080` | URL dasar server aplikasi Spring Boot. |
| `username` | default | `superadmin` | Username default untuk pengujian login. |
| `password` | secret | `Password123!` | Password default (disesuaikan dengan seed admin). |
| `accessToken` | secret | *(kosong / auto-filled)* | JWT Access Token untuk header `Authorization: Bearer <token>`. |
| `refreshToken` | secret | *(kosong / auto-filled)* | JWT Refresh Token untuk endpoint `/api/v1/auth/refresh-token`. |
| `tempToken` | secret | *(kosong / auto-filled)* | JWT ephemeral token (tipe `MFA_TEMP`) saat akun meminta verifikasi TOTP step-2. |
| `totpSecret` | secret | *(kosong / auto-filled)* | Kunci rahasia TOTP (Base32) yang didapat dari setup TOTP. |
| `totpCode` | default | *(kosong / auto-filled)* | Kode 6-digit TOTP yang dihitung otomatis oleh Pre-request Script. |
| `roleId` | default | `1` | ID Role yang digunakan untuk pengujian endpoint Role CRUD. |
| `userId` | default | `1` | ID User yang digunakan untuk pengujian endpoint User CRUD. |

---

## 🛠️ Fitur Script Otomatis

### 1. 🔑 Auto Token & Secret Capture (Tests Script)
- **Login (`POST /api/v1/auth/login`)**:
  - Jika TOTP belum aktif: langsung menyimpan `accessToken` dan `refreshToken`.
  - Jika TOTP aktif (`mfaRequired: true`): otomatis menyimpan `tempToken` ke variabel.
- **Verify TOTP (`POST /api/v1/auth/totp/verify`)**:
  - Otomatis memperbarui `accessToken` dan `refreshToken` setelah kode OTP diverifikasi.
- **Setup TOTP (`GET /api/v1/auth/totp/setup`)**:
  - Otomatis mengekstrak `secretKey` dan menyimpannya ke `totpSecret`.
- **Refresh Token (`POST /api/v1/auth/refresh-token`)**:
  - Otomatis memperbarui `accessToken` dan `refreshToken`.
- **Get All Roles & Get All Users**:
  - Mengambil ID entitas pertama (`content[0].id`) dan menyimpannya ke `roleId` atau `userId`.

### 2. ⚡ Auto TOTP Generator (Pre-request Script)
Pada request:
- `4. Enable TOTP (Activate 2FA)`
- `5. Verify TOTP (Login Step 2)`

Script pre-request menggunakan algoritma **RFC 6238 HMAC-SHA1** dengan library bawaan Postman (`CryptoJS`) untuk menghitung kode 6 digit secara langsung berdasarkan nilai variabel `totpSecret`.
> **Kemudahan Pengujian:** Anda tidak perlu membuka aplikasi authenticator fisik setiap kali testing, karena variabel `totpCode` akan terisi otomatis dengan OTP valid saat request dikirim!

### 3. 📱 Visualisasi QR Code (Postman Visualizer)
Pada endpoint `3. Setup TOTP`:
Buka tab **Visualize** di bagian Response Postman untuk melihat gambar QR Code secara langsung beserta Secret Key dan URI manual yang siap discan.

---

## 🔄 Alur Skenario Pengujian yang Disarankan

### Skenario A: Login Pertama & Aktivasi TOTP
1. Jalankan **`2. Login`** dengan user `superadmin` / `Password123!`.
   - Script akan menyimpan `accessToken` secara otomatis.
2. Jalankan **`3. Setup TOTP`**.
   - Buka tab **Visualize** untuk melihat QR Code. Variabel `totpSecret` otomatis terisi.
3. Jalankan **`4. Enable TOTP`**.
   - Pre-request script akan otomatis menghitung kode 6-digit dan mengaktifkan 2FA.
   - Skenario notifikasi email asynchronous via Resend akan terpicu di server.

### Skenario B: Logout Sesuai Aturan Bisnis
1. Jalankan **`7. Logout`**.
   - Berhasil logout (karena TOTP sudah aktif). Token di-blacklist di Redis.
   *(Catatan: Jika TOTP belum aktif, server akan mengembalikan 400 Bad Request).*

### Skenario C: Login dengan 2FA Aktif (2-Step Verification)
1. Jalankan kembali **`2. Login`**.
   - Response akan mengembalikan `mfaRequired: true` dan script otomatis menyimpan `tempToken`.
2. Jalankan **`5. Verify TOTP`**.
   - Pre-request script otomatis menghasilkan kode TOTP valid.
   - Token `accessToken` dan `refreshToken` final berhasil didapatkan.

### Skenario D: CRUD Role & User
1. Jalankan request di folder **`02. Role Management`** (`Create`, `Get All`, `Get by ID`, `Update`, `Delete`).
2. Jalankan request di folder **`03. User Management`** (`Create`, `Get All`, `Get by ID`, `Update`, `Delete`).
