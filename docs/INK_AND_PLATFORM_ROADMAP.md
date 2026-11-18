# Çizim (Ink) ve Platform Yol Haritası

## 1. Mevcut çizim sistemi (özet)
`DrawingCanvasSheet` (Android) ve `DesktopDrawingCanvasDialog` (PC) ayrı bir sayfada/diyalogda çalışır; çizim PNG olarak kaydedilir. Kalem/silgi ayrımı, avuç içi reddi ve şekil araçları vardır. Eksikler: notun içinde doğrudan çizim yok, basınç/eğim yok, vektör saklama yok, zoom/pan yok, el yazısı tanıma yok.

## 2. Hedef: "Mürekkep bloğu" (InkBlock)
Notun içinde, kalem değdiği anda çizen blok.

### Veri modeli (`:shared` `NoteBlock` içine, `kotlinx.serialization`, her alanda `@SerialName`)
```
InkStroke(id, tool: PEN|HIGHLIGHTER|ERASER, colorArgb, baseWidth, points: List<InkPoint>)
InkPoint(x, y, pressure, tiltRad?, tMillis)
InkBlock(strokes: List<InkStroke>, width, height, background: TRANSPARENT|PAPER)
```
- Geriye dönük uyum: eski PNG çizim bloğu olduğu gibi okunur; "Düzenlenebilir yap" ile vektöre dönüştürme sunulmaz (kayıpsız değil), yeni çizimler InkBlock olur.
- Silgi vektörde "stroke silgisi" (çizgiyi komple siler) + piksel silgisi için şeffaf `BlendMode.Clear`; beyaz boyama kaldırılır.

### Giriş mantığı (Android)
- `pointerInput` içinde `PointerType.Stylus`/`Eraser` algılandığında çizim, `Touch` ise kaydırma (stylus-first). Kalem yoksa "parmakla çiz" geçişi.
- Basınç: `PointerInputChange.pressure` → genişlik = `baseWidth * (0.4 + 1.2 * pressure)`; `historical` noktaları korunur.
- Düşük gecikme: önce Compose `Canvas`; yetersizse `androidx.ink` (stroke rendering + low-latency) değerlendirilir.
- Zoom/pan: iki parmak.

### Masaüstü
- Compose Desktop'ta kalem basıncı sınırlı; ilk sürümde fare/dokunma + sabit genişlik, basınç alanı model olarak hazır (varsayılan 0.5).

### El yazısı → metin (isteğe bağlı, çevrimdışı)
ML Kit Digital Ink Recognition (modeller cihaza indirilir; "tamamen çevrimdışı" vaadi için model indirme kullanıcıya açıkça söylenmeli ve README/Gizlilik Politikası güncellenmeli).

### Test planı
- Birim: InkStroke serileştirme/geri uyumluluk, stroke silgisi isabet testi, genişlik-basınç eşlemesi.
- Cihaz: S-Pen/USI kalemle çizim, avuç içi, silgi ucu, koyu/açık tema, PDF dışa aktarma.
- Mimari kuralı: model `:shared`, UI `:app`/`:desktopApp` (ArchitectureRulesTest korunur).

### Aşamalar
1. Model + serileştirme + testler (`:shared`)
2. Android InkBlock UI (kalem öncelikli), PNG dışa aktarma/PDF desteği
3. Basınç, fosforlu kalem, stroke silgisi
4. Zoom/pan, masaüstü karşılığı
5. El yazısı tanıma (opsiyonel)

## 3. Platformlar
- **Android + Windows:** ilk sürüm hedefi. Windows imzası şimdilik yok; GitHub Releases'te SHA-256 özetiyle imzasız yayınlayın ve SmartScreen uyarısını README'de belirtin.
- **macOS:** Compose Desktop `.dmg` hedefi tanımlı ama Mac'te derlenmedi; Apple Developer hesabı (ücretli), imzalama ve notarization gerekir. Keychain erişimi (`java-keyring`) macOS'ta ayrıca test edilmeli.
- **iOS:** Mevcut mimaride hedef yok. `:shared` yalnızca JVM; Hilt, Room, SQLCipher, Tink, BiometricPrompt Android'e özgü. Seçenekler: (a) Kotlin Multiplatform'a taşıma (Room KMP + SQLCipher iOS, Compose Multiplatform iOS) — büyük iş; (b) ayrı SwiftUI istemcisi + ortak şifreli eşitleme protokolü. Karar, kullanıcı talebine göre verilmeli.


## Durum (Faz 1-2 tamamlandı)
- Faz 1: `InkModel.kt` (InkDocument/InkStroke/InkPoint/InkCodec) + `NoteBlock.inkJson` (eski notlarla uyumlu), 6 test.
- Faz 2 (Android): `InkBlockCanvas.kt` satır içi tuval; kalem anında çizer, parmak kaydırır ("Parmak" anahtarıyla çizebilir), basınca duyarlı kalınlık, fosforlu, vuruş silgisi (kalem silgi ucu dahil), geri al. Dışa aktarma/senkron için PNG önizleme otomatik üretilir.
- Cihazda çalıştırılmadı; derleme ve birim testleri geçti.
- Sıradaki: zoom/pan, masaüstü (Windows) karşılığı, el yazısı tanıma.

- Faz 3 (kısmen): Android tuvalinde iki parmakla yakınlaştırma/kaydırma (1x–5x) eklendi; tek parmak sayfayı kaydırmaya devam eder, kalem çizer. Masaüstünde vektör düzenleme henüz yok: masaüstünde düzenlenen çizim PNG olarak kaydedilir ve eski vektör veri temizlenir (telefon masaüstü düzenlemesini ezmesin diye).
- Kalan: masaüstünde vektör çizim görüntüleme/düzenleme, el yazısı tanıma.
