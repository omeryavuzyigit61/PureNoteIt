# Privacy Policy / Gizlilik Politikası

**Effective Date / Yürürlük Tarihi:** November 10, 2026 / 10 Kasım 2026  
**Application / Uygulama:** PureNoteIt  
**Developer / Geliştirici:** Ömer Yavuz Yiğit  
**Contact / İletişim:** yavuzyigitomer52@gmail.com  

---

## English Version

### 1. Introduction
PureNoteIt ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how our Android and Desktop applications operate and outlines our strict zero-data-collection, offline-first, and encrypted privacy architecture.

By using PureNoteIt, you agree to the practices described in this policy.

### 2. Zero Developer Servers & Optional Encrypted Cloud Sync
PureNoteIt is built as an **Offline-First & Private Knowledge Workspace**:
- **Zero Developer Servers:** PureNoteIt has no backend servers of its own. The developer never collects, views, stores, or receives your notes, sketches, audio recordings, passwords, or OAuth tokens.
- **Encrypted Local Storage:** All your notes, categories, media attachments, and settings are stored locally inside an encrypted SQLite database protected by hardware-backed OS Keystore / Keychain keys.
- **Optional Google Drive Sync (`drive.appdata`):** If you choose to enable Google Drive Sync via one-tap OAuth 2.0 PKCE sign-in, PureNoteIt requests **only** the narrow `https://www.googleapis.com/auth/drive.appdata` permission. Encrypted note payloads and encrypted media blobs are stored strictly inside **your own private Google Drive hidden app folder (`appDataFolder`)**. PureNoteIt never accesses your emails, contacts, photos, or general Drive files.
- **No Tracking or Analytics:** PureNoteIt contains **zero trackers, zero third-party advertising SDKs, and zero behavioral telemetry**. We never track, profile, or sell user activity.

### 3. Bitwarden-Grade AES-256 Cryptography & Vault Security
- **Hardware-Backed Encryption:** When you lock a note or vault, the content is encrypted using military-grade **AES-256-GCM** cryptography.
- **Biometric Authentication:** PureNoteIt integrates with Android's official BiometricPrompt API (Fingerprint, Face Unlock) and local PIN hashing. Biometric biometric data is handled exclusively by the Android hardware Security Module / Keystore and never accessible to the app.

### 4. Device Permissions & Why They Are Needed
PureNoteIt only requests Android permissions strictly required to execute user-initiated actions on the device:

| Permission | Purpose | Data Handling |
| :--- | :--- | :--- |
| `CAMERA` | Document OCR text scanning and capturing note images. | Processed 100% on-device via Google ML Kit. Images are never transmitted. |
| `RECORD_AUDIO` | Live voice dictation and voice recording attachments. | Audio files are saved exclusively in private app storage. |
| `POST_NOTIFICATIONS` | Local reminder and alarm notifications for scheduled notes. | Triggered locally via Android AlarmManager. |
| `SCHEDULE_EXACT_ALARM` | Precise alarm triggering for user-configured note alerts. | Handled entirely by the local operating system. |
| `USE_BIOMETRIC` | Authenticating access to locked notes and vault. | Managed by Android OS Biometric Keystore. |

### 5. Third-Party Libraries
PureNoteIt uses reputable, open-source libraries (e.g., Jetpack Compose, AndroidX, Dagger Hilt, Google ML Kit Text Recognition). Google ML Kit's Vision model runs **entirely on-device** and does not send photos or extracted text to Google servers.

### 6. Children's Privacy
PureNoteIt does not collect any personal identifiable information from anyone, including children under the age of 13.

### 7. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Any changes will be published directly in this repository.

### 8. Contact Us
If you have any questions or suggestions regarding this Privacy Policy, please reach out via email:  
**Developer:** Ömer Yavuz Yiğit  
**Email:** [yavuzyigitomer52@gmail.com](mailto:yavuzyigitomer52@gmail.com)  

---

## Türkçe Versiyon

### 1. Giriş
PureNoteIt ("biz" veya "uygulama"), gizliliğinize en üst düzeyde saygı gösterir. Bu Gizlilik Politikası, yerel (native) Android uygulamamızın nasıl çalıştığını ve sıfır veri toplama ilkesine dayanan "çevrimdışı öncelikli" (offline-first) mimarisini açıklamaktadır.

PureNoteIt uygulamasını kullanarak bu ilkeleri kabul etmiş sayılırsınız.

### 2. Sıfır Geliştirici Sunucusu & İsteğe Bağlı Şifreli Google Drive Eşitleme
PureNoteIt, düşüncelerinizin gizliliğini korumak üzere çevrimdışı öncelikli (offline-first) mimariyle tasarlanmıştır:
- **Geliştirici Sunucusu Yoktur:** PureNoteIt hiçbir geliştirici sunucusu kullanmaz. Geliştirici notlarınızı, ses kayıtlarınızı, çizimlerinizi, şifrelerinizi veya oturum anahtarlarınızı asla görmez, toplamaz ya da almaz.
- **Yerel Şifreli Depolama:** Tüm notlarınız, kategorileriniz, medya dosyalarınız ve ayarlarınız cihazınızdaki şifreli SQLite veritabanında ve donanım destekli Android Keystore / sistem anahtar kasasında saklanır.
- **İsteğe Bağlı Google Drive Eşitleme (`drive.appdata`):** Ayarlar'dan kendi isteğinizle Google Drive'a bağlandığınızda uygulama **yalnızca** en dar kapsam olan `https://www.googleapis.com/auth/drive.appdata` iznini ister. Şifrelenmiş not ve medya verileri doğrudan **kendi Google Drive hesabınızın gizli uygulama alanına (`appDataFolder`)** yazılır; uygulama e-postalarınıza, kişilerinize, fotoğraflarınıza veya diğer Drive dosyalarınıza kesinlikle erişemez.
- **İzleyici ve Reklam Yoktur:** Uygulama içerisinde **hiçbir üçüncü taraf reklam SDK'sı, davranışsal izleyici veya analitik aracı bulunmaz**. Verileriniz asla profillenmez ve satılmaz.

### 3. Bitwarden Seviyesinde AES-256 Şifreleme ve Kasa Güvenliği
- **Donanım Destekli Şifreleme:** Kilitlediğiniz notlar endüstri standardı **AES-256-GCM** algoritması ile cihazınızda şifrelenir.
- **Biyometrik Doğrulama:** PureNoteIt, Android'in resmi BiometricPrompt API'sini (Parmak İzi, Yüz Tanıma) ve yerel PIN algoritmasını kullanır. Biyometrik verileriniz doğrudan cihaz donanımındaki güvenlik modülü (Keystore) tarafından yönetilir, uygulamanın bu verilere erişimi yoktur.

### 4. İstenen İzinler ve Kullanım Amaçları
PureNoteIt yalnızca kullanıcının doğrudan başlattığı işlevleri yerine getirebilmek için gerekli izinleri talep eder:

| İzin | Amaç | Veri İşleme Şekli |
| :--- | :--- | :--- |
| `KAMERA` | Belge tarama (OCR) ve nota fotoğraf ekleme. | Google ML Kit ile %100 cihaz içinde işlenir, sunucuya iletilmez. |
| `SES KAYDI` | Sesli not kaydetme ve konuşmayı metne çevirme (dikte). | Ses dosyaları yalnızca cihazınızın yerel depolama alanında tutulur. |
| `BİLDİRİMLER` | Notlar için kurulan yerel hatırlatıcı ve alarm uyarıları. | Cihazın yerel AlarmManager servisi tarafından tetiklenir. |
| `KESİN ALARM` | Belirlenen saatte tam zamanında alarm çalması için. | Cihaz işletim sistemi tarafından yerel olarak yönetilir. |
| `BİYOMETRİK` | Kilitli notların ve kasanın kilidini açma. | Android Keystore tarafından güvenli biçimde doğrulanır. |

### 5. Üçüncü Taraf Kütüphaneler
Uygulama, Google ML Kit (Cihaz İçi Metin Tanıma), Jetpack Compose ve Room gibi resmi kütüphanelerle geliştirilmiştir. Google ML Kit metin tanıma motoru tamamen çevrimdışı (on-device) çalışır.

### 6. Çocukların Gizliliği
PureNoteIt hiçbir kişisel bilgi toplamadığından 13 yaşın altındaki çocuklar da dahil olmak üzere kimsenin kişisel verilerini işlemez.

### 7. Politika Değişiklikleri
Gizlilik politikamızda yapılabilecek güncellemeler bu dosya üzerinden doğrudan yayınlanacaktır.

### 8. İletişim
Bu Gizlilik Politikası hakkında sorularınız, geri bildirimleriniz veya talepleriniz için bizimle iletişime geçebilirsiniz:  
**Geliştirici:** Ömer Yavuz Yiğit  
**E-posta:** [yavuzyigitomer52@gmail.com](mailto:yavuzyigitomer52@gmail.com)  
