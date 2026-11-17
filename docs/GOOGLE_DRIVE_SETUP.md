# Setup Google Drive Backup SantetHP

Dokumen ini untuk konfigurasi satu kali pada proyek Google Cloud/Firebase SantetHP. Setiap orang tua tetap mengotorisasi akun Google Drive miliknya sendiri dari aplikasi.

## Konfigurasi yang sudah terdeteksi di repository

- Google Cloud/Firebase project ID: `parentaudioconnect`
- Android application ID untuk build saat ini: `com.santethp`
- Scope yang diminta aplikasi: `https://www.googleapis.com/auth/drive.file`
- Library Android: Google Identity Services melalui `play-services-auth`
- Aplikasi meminta akses per-file (bukan akses seluruh isi Drive).

File `app/google-services.json` sudah memuat Firebase Android app dengan package `com.santethp`. OAuth Android client dibuat terpisah di Google Cloud Console; jangan menaruh client secret atau private key di repository.

## Langkah Google Cloud Console

1. Buka [Google Cloud Console](https://console.cloud.google.com/) dan pilih project `parentaudioconnect`.
2. Buka **APIs & Services → Library**, cari **Google Drive API**, lalu pilih **Enable**.
3. Buka **Google Auth Platform → Branding**. Isi nama aplikasi (SantetHP), email dukungan, dan email kontak developer.
4. Buka **Google Auth Platform → Audience**:
   - Pilih **External** jika aplikasi akan dipakai orang tua di luar satu organisasi Google Workspace.
   - Saat pengujian, biarkan status **Testing** dan tambahkan akun Gmail penguji sebagai **Test users**.
   - Untuk penggunaan umum, lengkapi halaman aplikasi, kebijakan privasi, dan proses publikasi/verifikasi yang diminta Google.
5. Buka **Google Auth Platform → Data Access** dan tambahkan scope:
   `https://www.googleapis.com/auth/drive.file`
6. Buka **Google Auth Platform → Clients → Create client → Android**:
   - Package name: `com.santethp`
   - SHA-1: sidik jari sertifikat dari **release keystore yang sama** dengan yang digunakan untuk menandatangani APK produksi.
   - Jangan gunakan SHA-1 debug untuk credential produksi. Jika ada APK debug terpisah, buat Android OAuth client tambahan dengan package yang sama dan SHA-1 debug.
7. Simpan perubahan. Satu konfigurasi OAuth aplikasi ini dapat digunakan banyak keluarga. Saat login di aplikasi, setiap orang tua memilih/menyetujui akun Gmail miliknya sendiri; file backup masuk ke Drive akun yang memberi persetujuan.

## Mendapatkan SHA-1 release

Jalankan pada mesin/lingkungan yang memiliki release keystore. Gunakan path dan alias yang sama dengan variabel signing build. Repository juga menyediakan workflow manual **Show release signing SHA-1** di GitHub Actions; jalankan workflow itu pada branch fitur ini untuk menampilkan hanya fingerprint SHA-1 (workflow tidak membuat APK dan tidak mencetak password/keystore).

```bash
keytool -list -v -keystore /path/ke/release.keystore -alias ALIAS_RELEASE
```

Salin nilai **SHA1** saja ke kolom Android OAuth client. Jangan mengirim password keystore atau file keystore ke chat/repository.

Jika release keystore hanya tersedia sebagai secret CI, jalankan perintah tersebut di lingkungan CI yang aman; jangan cetak password atau isi keystore ke log.

## Publikasi Firestore Rules

Perubahan rules di `firestore.rules` sudah disimpan di branch fitur. Rules baru mengizinkan signaling P2P untuk tiga persetujuan terpisah: Galeri Anak, Backup Drive, dan Penerimaan dari HP Orang Tua.

Sebelum pengujian perangkat, tinjau lalu terbitkan rules ke project `parentaudioconnect`. Dari lingkungan yang sudah login Firebase CLI dan terhubung ke project yang benar:

```bash
firebase deploy --project parentaudioconnect --only firestore:rules
```

Perintah deploy **belum dijalankan** oleh perubahan repository ini. Pastikan project aktif benar sebelum menerbitkan rules.

## Batas otorisasi saat ini

Implementasi saat ini memakai access token jangka pendek dari Google Identity Services. Token hanya berada di memori aplikasi/service, tidak disimpan sebagai refresh token. Jika token kedaluwarsa atau proses Android dihentikan, orang tua perlu membuka aplikasi dan menyetujui otorisasi ulang agar backup berlanjut.

Mode benar-benar unattended setelah proses aplikasi berhenti membutuhkan alur offline access (server authorization code) dan backend aman untuk menyimpan/memperbarui refresh token. Backend tersebut belum menjadi bagian dari implementasi ini; jangan menaruh client secret atau refresh token di APK.

## Checklist sebelum uji perangkat

- [ ] Google Drive API aktif pada project `parentaudioconnect`.
- [ ] OAuth consent/branding selesai dan akun Gmail penguji terdaftar jika status Testing.
- [ ] Scope `drive.file` tercantum pada Data Access.
- [ ] Android OAuth client memakai package `com.santethp` dan SHA-1 release yang benar.
- [ ] Firestore Rules ditinjau dan diterbitkan ke project yang benar.
- [ ] Build dan pengujian dua perangkat dilakukan setelah ada persetujuan eksplisit.
