# PureNoteIt Güvenlik Denetim Notları (Kasım 2026)

Kapsam: statik kod incelemesi (Android `:app`, `:shared`, `:desktopApp`). Derleme ve test çalıştırılmadı; bulgular kodda okunan davranışa dayanır. Bağımsız bir güvenlik denetimi değildir.

## Bu turda yapılan düzeltmeler
| # | Değişiklik | Dosya |
|---|---|---|
| 1 | Kullanılmayan `AlarmSoundService` bildirimi ve `FOREGROUND_SERVICE` izni kaldırıldı (hiçbir yerden başlatılmıyordu; Android 14+ servis tipi/Play beyanı gereksinimini ortadan kaldırır). Sınıf dosyası kodda duruyor, silinebilir. | `AndroidManifest.xml` |
| 2 | Masaüstünde python/js/java/kotlin/c/c++/bash çalıştırma artık her kod metni için açık onay ister (oturum başına, kod değişirse yeniden sorar). | `DesktopHomeStateHolder.kt`, `DesktopBlockEditor.kt`, `DesktopContract.kt` |
| 3 | LAN eşleştirme kodu en az 8 karakter; sunucu 5 hatalı token sonrası 60 sn kilitleniyor. | `DesktopHomeStateHolder.kt`, `LanWebSocketSyncTransport.kt`, `DesktopSyncDialog.kt` |
| 4 | `jitpack.io` deposu kaldırıldı (hiçbir bağımlılık kullanmıyor; bir şey kırılırsa satırı geri ekleyin ve `content { includeGroupByRegex(...) }` ile kısıtlayın). | `settings.gradle.kts` |
| 5 | README/GEMINI.md sürüm ve platform bilgisi güncellendi; "askeri seviye / Bitwarden seviyesi" ifadeleri kaldırıldı; şifreleme kapsamı notu eklendi. | `README.md`, `GEMINI.md` |

## Doğrulanan ama DEĞİŞTİRİLMEYEN bulgular (karar gerekir)
1. ~~Android medya dosyaları şifresiz.~~ **DÜZELTİLDİ:** `:shared` `MediaDiskCipher` (parçalı AES-256-GCM, `PNM1`) + `EncryptedMediaPathStorage`; Android anahtarı `AndroidMediaKeyManager` ile Keystore'da sarılı (`noBackupFilesDir`). Yeni medya anında şifrelenir, eski düz dosyalar uygulama açılışında şifrelenir, yedek (`.purevault`) çözülmüş içerikle alınır ve geri yüklemede yeniden şifrelenir. Kalan risk: çözülmüş geçici kopyalar süreç çalışırken `cacheDir/media_plain` içinde durur (açılışta silinir). Cihazda (emülatör/gerçek) elle doğrulanmadı; birim testleri `:shared:desktopTest` içinde.
2. **SQLCipher anahtarı kullanıcı doğrulamasına bağlı değil.** `SqlCipherDatabaseManager` Keystore anahtarını `setUserAuthenticationRequired` olmadan üretiyor; veritabanı uygulama açılınca PIN'siz açılabiliyor. Bu, kayıp/çalıntı cihaz ve dosya kopyalama senaryolarına karşı korur; kilidi açık cihazı kullanan kişiye karşı korumaz (kilitli notlar ayrıca PIN/biyometri ile korunuyor). Tüm uygulamayı PIN'e bağlamak UX kararıdır.
3. **`USE_EXACT_ALARM` izni.** Play politikası bu izni alarm/takvim uygulamalarına tanıyor. Not uygulaması için Play Console beyanında "alarm tipi hatırlatıcılar" gerekçesi hazır olmalı; reddedilirse `SCHEDULE_EXACT_ALARM` + kullanıcıyı ayara yönlendirme akışına geçilmeli (kod zaten izin yoksa `setAndAllowWhileIdle`'a düşüyor).
4. **RASP (`AppIntegrityGuard`).** İmza kontrolü belirli bir sertifika hash'ine sabitlenmiyor (yalnızca AOSP test anahtarı ve yasaklı DN ifadeleri); Play App Signing ile yanlış pozitif riski bu açıdan yok. Frida portu/`TracerPid` tespiti root'lu veya profil araçlı gerçek cihazlarda kasayı kilitleyebilir; gerçek bir koruma değil, caydırıcı bir katman olarak görülmeli.
5. **LAN sync düz HTTP.** Zarflar E2EE, ancak taşıma şifresiz ve token, kodun SHA-256'sı olarak başlıkta gidiyor; aynı ağdaki biri token'ı dinleyip çevrimdışı kod tahmini yapabilir. Kod uzunluğu 8'e çıkarıldı; asıl çözüm: PAKE benzeri eşleştirme veya TLS + sertifika sabitleme.
6. **Masaüstü OAuth `clientSecret`** derleme sırasında binary'ye gömülüyor (Google bunu masaüstü uygulamalarında gizli saymıyor; PKCE var).
7. **Bağımlılık doğrulama kapalı.** Bir kez çalıştırın: `./gradlew --write-verification-metadata sha256 help` ve `gradle/verification-metadata.xml` dosyasını commit edin.
8. **Masaüstü kod çalıştırma izolasyonu** gerçek sandbox değil (geçici dizin, temiz ortam, timeout, çıktı sınırı). Onay kapısı riski azaltır, ortadan kaldırmaz.

## Doğru çalışan taraflar (okunarak doğrulandı)
- Android: `allowBackup=false`, yedek/aktarım dışlama kuralları, cleartext kapalı, `BootReceiver` action kontrolü yapıyor, PendingIntent `FLAG_IMMUTABLE`, SQLCipher + atomik düz→şifreli göç.
- Masaüstü keyring: hata durumunda `KEYSTORE_UNAVAILABLE` ile başarısız oluyor, düz metne düşmüyor; veritabanı ve medya şifreli.


## Güncelleme: LAN senkron TLS
- Yerel ağ sunucusu artık HTTPS (Netty, oturuma özel kendinden imzalı sertifika). Adres `https://ip:port#<sha256 parmak izi>` biçimindedir; istemci yalnızca bu parmak izine sahip sertifikayı kabul eder, parmak izi yoksa bağlanmaz.
- Eşleşme kodu hâlâ yetkilendirme belirtecidir (8+ karakter, 5 hatada 60 sn kilit), ama artık ağda düz metin olarak görünmez.
- Windows paketleme (MSI) Netty ile henüz doğrulanmadı.

- `USE_EXACT_ALARM` izni kaldırıldı (Play politika riski). Hatırlatıcılar `SCHEDULE_EXACT_ALARM` ile çalışır; izin verilmediyse kod otomatik olarak yaklaşık alarma düşer (ReminderManager).

- Masaüstü `clientSecret`: derleme sırasında `local.properties`/ortam değişkeninden enjekte edilir, git’e girmez (`local.properties` ve `desktopApp/build/` .gitignore’da). Google “masaüstü uygulaması” istemcilerinde bu değeri gizli saymaz (PKCE asıl korumadır); dağıtılan ikilide yer alması kabul edilmiş artık risktir. İstenirse Google Cloud’dan döndürülebilir (rotate).
- Kod çalıştırıcı: ortam değişkenleri beyaz listeye indirilmiş, geçici klasörde çalışır, zaman aşımı + çıktı sınırı + süreç ağacı sonlandırma var, çalıştırmadan önce açık onay istenir. İşletim sistemi düzeyinde izolasyon (konteyner/AppContainer) yoktur; kod kullanıcı yetkileriyle çalışır ve arayüz bunu açıkça söyler.
