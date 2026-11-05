package com.notes.purenoteit.model

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.notes.purenoteit.R

enum class TemplateCategory(val titleRes: Int) {
    ALL(R.string.template_cat_all),
    TECH(R.string.template_cat_tech),
    EDUCATION(R.string.template_cat_edu),
    WORK(R.string.template_cat_work),
    LIFE(R.string.template_cat_life)
}

data class NoteTemplate(
    val id: String,
    val icon: String,
    val badgeBgColor: Color,
    val badgeContentColor: Color,
    val category: TemplateCategory,
    val titleRes: Int,
    val descRes: Int,
    val defaultTitleTr: String,
    val defaultTitleEn: String,
    val tags: List<String>,
    val buildBlocks: (context: Context) -> List<NoteBlock>
) {
    fun getDefaultTitle(context: Context): String {
        val lang = context.resources.configuration.locales[0].language
        return if (lang.startsWith("en", ignoreCase = true)) defaultTitleEn else defaultTitleTr
    }
}

object NoteTemplates {
    fun getAll(context: Context): List<NoteTemplate> = listOf(
        // 1. Yazılım & Hata Çözüm Notu (Teknoloji)
        NoteTemplate(
            id = "dev_bug_tracker",
            icon = "⚡",
            badgeBgColor = Color(0xFFE3F2FD),
            badgeContentColor = Color(0xFF1565C0),
            category = TemplateCategory.TECH,
            titleRes = R.string.template_dev_title,
            descRes = R.string.template_dev_desc,
            defaultTitleTr = "Hata Analizi & Kod Çözümü",
            defaultTitleEn = "Bug Analysis & Code Fix",
            tags = listOf("💻 Kod Bloğu", "📊 Hata Seviyesi", "✅ Aksiyonlar"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "⚡",
                        content = if (isEn) "Issue: Coroutine Scope Memory Leak & ANR Prevention"
                                  else "Hata: Coroutine Scope Bellek Sızıntısı & ANR Önleme"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "💻 Optimized Solution Code" else "💻 Optimize Edilmiş Çözüm Kodu"
                    ),
                    NoteBlock(
                        type = BlockType.CODE,
                        codeLanguage = "kotlin",
                        content = "// Use viewModelScope with structured concurrency\nviewModelScope.launch(Dispatchers.IO) {\n    val result = repository.fetchDataSync()\n    withContext(Dispatchers.Main) {\n        _uiState.value = UiState.Success(result)\n    }\n}"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📊 Module Impact Table" else "📊 Modül Etki & Öncelik Tablosu"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Module", "Severity", "Status"),
                            listOf("AuthManager", "P0 - Critical", "Resolved"),
                            listOf("ImageLoader", "P1 - Medium", "In Staging QA"),
                            listOf("SyncWorker", "P2 - Low", "Scheduled")
                        ) else listOf(
                            listOf("Modül", "Öncelik", "Durum"),
                            listOf("AuthManager", "P0 - Kritik", "Çözüldü"),
                            listOf("ImageLoader", "P1 - Orta", "Test Ortamında"),
                            listOf("SyncWorker", "P2 - Düşük", "Planlandı")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "✅ Verification Checklist" else "✅ Doğrulama & Test Kontrolleri"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Profile memory allocation with Android Profiler"
                                  else "Android Profiler ile bellek tahsisini kontrol et"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Run automated regression tests on physical device"
                                  else "Fiziksel cihazda regresyon testlerini çalıştır"
                    )
                )
            }
        ),

        // 2. Matematik & Formül Defteri (Eğitim)
        NoteTemplate(
            id = "math_notebook",
            icon = "♾️",
            badgeBgColor = Color(0xFFFFF8E1),
            badgeContentColor = Color(0xFFF57F17),
            category = TemplateCategory.EDUCATION,
            titleRes = R.string.template_math_title,
            descRes = R.string.template_math_desc,
            defaultTitleTr = "Matematik & Formül Defteri",
            defaultTitleEn = "Math & Formula Notebook",
            tags = listOf("📐 LaTeX Formülleri", "✏️ Çizim Taslağı", "💡 İspat"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "♾️",
                        content = if (isEn) "Topic: Fundamental Theorem of Calculus & Area Integration"
                                  else "Konu: Kalkülüsün Temel Teoremi & Alan Entegrasyonu"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📐 Core Mathematical Formulas" else "📐 Temel Matematik Formülleri"
                    ),
                    NoteBlock(
                        type = BlockType.MATH,
                        content = "\\int_{a}^{b} f(x) dx = F(b) - F(a)"
                    ),
                    NoteBlock(
                        type = BlockType.MATH,
                        content = "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🔍 Theorem Proof & Geometric Insight" else "🔍 Teorem İspatı & Geometrik Yorum"
                    ),
                    NoteBlock(
                        type = BlockType.TOGGLE,
                        content = if (isEn) "Click to reveal step-by-step derivation..." else "Adım adım türetim adımlarını göster...",
                        toggleBody = if (isEn) "1. Let A(x) be the area function under f(t) from a to x.\n2. A'(x) = f(x) by definition of the derivative.\n3. Therefore, any antiderivative F(x) satisfies F(b) - F(a) = Area."
                                     else "1. A(x), a'dan x'e kadar f(t) eğrisi altındaki alan fonksiyonu olsun.\n2. Türev tanımı gereği A'(x) = f(x) olur.\n3. Dolayısıyla F(x) ters türevi için F(b) - F(a) toplam alana eşittir.",
                        isExpanded = false
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "✏️ Geometry Sketch Area" else "✏️ Geometri & Grafik Çizim Notu"
                    ),
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🎨",
                        content = if (isEn) "Use the Draw tool from toolbar above to sketch coordinate axes or curves."
                                  else "Koordinat eksenlerini ve eğrileri çizmek için yukarıdaki Çizim aracını kullanabilirsiniz."
                    )
                )
            }
        ),

        // 3. Cornell Ders Notu (Eğitim)
        NoteTemplate(
            id = "cornell_notes",
            icon = "📖",
            badgeBgColor = Color(0xFFEDE7F6),
            badgeContentColor = Color(0xFF512DA8),
            category = TemplateCategory.EDUCATION,
            titleRes = R.string.template_cornell_title,
            descRes = R.string.template_cornell_desc,
            defaultTitleTr = "Cornell Notu: Veri Yapıları",
            defaultTitleEn = "Cornell Note: Data Structures",
            tags = listOf("🎓 Cornell Metodu", "❓ Aktif Hatırlama", "📝 Özet"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "📖",
                        content = if (isEn) "Lecture: Balanced Search Trees (AVL & Red-Black) | Date: Today"
                                  else "Ders: Dengeli Arama Ağaçları (AVL & Kırmızı-Siyah) | Tarih: Bugün"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🎯 Key Cues & Keywords" else "🎯 Anahtar Kavramlar & İpuçları (Cues)"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Balance Factor: Height(left) - Height(right) ∈ {-1, 0, 1}"
                                  else "Dengeleme Faktörü: Yükseklik(Sol) - Yükseklik(Sağ) ∈ {-1, 0, 1}"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Rotations: LL, RR (Single) and LR, RL (Double)"
                                  else "Döndürmeler: LL, RR (Tekli) ve LR, RL (Çiftli rotasyonlar)"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "❓ Active Recall (Flashcards)" else "❓ Aktif Hatırlama & Soru-Cevap"
                    ),
                    NoteBlock(
                        type = BlockType.TOGGLE,
                        content = if (isEn) "Q: When to prefer AVL trees over Red-Black trees?" else "Soru: Hangi durumlarda AVL ağacı Kırmızı-Siyah ağaca tercih edilir?",
                        toggleBody = if (isEn) "Answer: When search operations vastly outnumber insertions/deletions, because AVL is more strictly balanced O(log n)."
                                     else "Cevap: Arama işlemleri ekleme/silme işlemlerinden çok daha fazlaysa; çünkü AVL daha katı dengelidir ve araması daha hızlıdır.",
                        isExpanded = false
                    ),
                    NoteBlock(
                        type = BlockType.TOGGLE,
                        content = if (isEn) "Q: What is the worst-case lookup time complexity?" else "Soru: En kötü durum arama zaman karmaşıklığı nedir?",
                        toggleBody = if (isEn) "Answer: Strictly O(log n) guaranteed by balancing invariant."
                                     else "Cevap: Dengeleme kuralı sayesinde her zaman kesin olarak O(log n)'dir.",
                        isExpanded = false
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📝 3-Sentence Summary" else "📝 3 Cümlelik Ders Özeti"
                    ),
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "💡",
                        content = if (isEn) "AVL trees guarantee logarithmic lookup at the expense of extra rotations on write. Perfect for read-heavy caches and indexing structures."
                                  else "AVL ağaçları yazma anındaki ekstra rotasyonlar pahasına kesin logaritmik arama garantisi verir. Okuma ağırlıklı indeks ve önbellek yapıları için idealdir."
                    )
                )
            }
        ),

        // 4. Sistem Mimarisi & Karar Günlüğü (Teknoloji)
        NoteTemplate(
            id = "architecture_adr",
            icon = "🏛️",
            badgeBgColor = Color(0xFFECEFF1),
            badgeContentColor = Color(0xFF37474F),
            category = TemplateCategory.TECH,
            titleRes = R.string.template_architecture_title,
            descRes = R.string.template_architecture_desc,
            defaultTitleTr = "Mimari Karar: MVI Durum Yönetimi",
            defaultTitleEn = "Architecture Decision: MVI State Pattern",
            tags = listOf("🏛️ Mimari ADR", "📊 Kıyaslama", "🎯 Yol Haritası"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🏛️",
                        content = if (isEn) "Status: Accepted | Decision Maker: Lead Architect | Date: Today"
                                  else "Durum: Onaylandı | Karar Sahibi: Baş Mimar | Tarih: Bugün"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📋 Context & Problem Statement" else "📋 Bağlam & Karşılaşılan Sorun"
                    ),
                    NoteBlock(
                        type = BlockType.PARAGRAPH,
                        content = if (isEn) "Multiple async triggers produced state inconsistencies across Jetpack Compose screens. We need a single source of truth."
                                  else "Birden fazla asenkron tetikleyici ekranlarda durum tutarsızlığına yol açıyordu. Tek bir doğruluk kaynağı (Single Source of Truth) gerekiyor."
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📊 Architecture Alternatives Comparison" else "📊 Mimari Alternatifler Kıyaslama Tablosu"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Criteria", "Classical MVVM", "MVI StateFlow (Chosen)"),
                            listOf("State Consistency", "Multiple LiveData points", "Single Immutable State"),
                            listOf("Testability", "Medium", "High / Deterministic"),
                            listOf("Reactivity", "Manual coordination", "Declarative Flow pipeline")
                        ) else listOf(
                            listOf("Kriter", "Klasik MVVM", "MVI StateFlow (Seçilen)"),
                            listOf("Durum Tutarlılığı", "Çoklu LiveData takibi", "Tek Immutable State"),
                            listOf("Test Edilebilirlik", "Orta seviye", "Yüksek / Deterministik"),
                            listOf("Tepkisellik", "Manuel koordinasyon", "Deklaratif Flow akışı")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "🚀 Implementation Steps" else "🚀 Uygulama ve Geçiş Adımları"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Define sealed interface for UiIntent and UiState"
                                  else "UiIntent ve UiState sealed arayüzlerini tanımla"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Migrate NoteDetailScreen to collectAsStateWithLifecycle"
                                  else "NoteDetailScreen akışını collectAsStateWithLifecycle'a taşı"
                    )
                )
            }
        ),

        // 5. Dil Öğrenme & Telaffuz Defteri (Eğitim)
        NoteTemplate(
            id = "language_learning",
            icon = "🗣️",
            badgeBgColor = Color(0xFFE0F7FA),
            badgeContentColor = Color(0xFF00838F),
            category = TemplateCategory.EDUCATION,
            titleRes = R.string.template_language_title,
            descRes = R.string.template_language_desc,
            defaultTitleTr = "İleri Seviye İngilizce Deyimler",
            defaultTitleEn = "Advanced English Idioms & Vocab",
            tags = listOf("🗣️ Telaffuz", "📊 Kelime Tablosu", "🎧 Ses Kaydı"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🗣️",
                        content = if (isEn) "Target Language: Spanish / Italian | Goal: 5 idioms per session"
                                  else "Hedef Dil: İngilizce (B2/C1 Seviyesi) | Hedef: Seans başına 5 pratik"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📊 Core Vocabulary Table" else "📊 Kelime & Anlam Tablosu"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Expression", "Meaning", "Example Sentence"),
                            listOf("Bite the bullet", "Face a grim situation with courage", "I decided to bite the bullet and resign."),
                            listOf("Hit the nail on the head", "Describe exactly what is causing a situation", "Her comment hit the nail on the head."),
                            listOf("Burn the midnight oil", "Work late into the night", "He is burning the midnight oil for exams.")
                        ) else listOf(
                            listOf("Deyim / İfade", "Türkçe Anlamı", "Örnek Cümle"),
                            listOf("Bite the bullet", "Dişini sıkmak / Cesaretle göğüslemek", "I decided to bite the bullet and finish it."),
                            listOf("Hit the nail on the head", "Tam üstüne basmak / Doğru tespit", "Your explanation hit the nail on the head."),
                            listOf("Burn the midnight oil", "Gecesini gündüzüne katmak", "She burned the midnight oil before launch.")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "🎧 Pronunciation Practice" else "🎧 Telaffuz Pratiği & Aksan"
                    ),
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🎙️",
                        content = if (isEn) "Record yourself reading the example sentences using the Audio tool above to track pronunciation."
                                  else "Telaffuzunuzu dinlemek ve kıyaslamak için araç çubuğundaki Ses Kaydı aracını kullanın."
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Create 2 original sentences using each new idiom"
                                  else "Her deyimle ilgili kendin 2 özgün cümle kur ve tekrar et"
                    )
                )
            }
        ),

        // 6. Girişim & Proje Fikir Tuvali (İş & Finans)
        NoteTemplate(
            id = "lean_canvas",
            icon = "💎",
            badgeBgColor = Color(0xFFFCE4EC),
            badgeContentColor = Color(0xFFC2185B),
            category = TemplateCategory.WORK,
            titleRes = R.string.template_lean_canvas_title,
            descRes = R.string.template_lean_canvas_desc,
            defaultTitleTr = "Proje Fikir Tuvali: Mobil Girişim",
            defaultTitleEn = "Startup Lean Canvas: Mobile App",
            tags = listOf("💎 Değer Önerisi", "📊 Maliyet/Gelir", "🚀 MVP"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "💎",
                        content = if (isEn) "Project Name: Local-First Privacy Notes | Stage: Idea Validation"
                                  else "Proje: Gizlilik Odaklı Çevrimdışı Not | Aşama: Fikir Doğrulama"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🎯 Core Problem & Unique Value" else "🎯 Temel Problem & Eşsiz Değer Önerisi"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Problem: Cloud note apps leak personal data and charge costly recurring fees."
                                  else "Problem: Bulut tabanlı uygulamalar gizliliği ihlal ediyor ve sürekli aylık abonelik istiyor."
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Solution: 100% on-device SQLite database, biometric vault lock, and lifetime license."
                                  else "Çözüm: %100 cihazda saklanan yerel veritabanı, biyometrik kasa kilidi ve ömür boyu lisans."
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📊 Revenue & Cost Structure" else "📊 Gelir Modeli ve Maliyet Kalemleri"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Stream", "Type", "Estimated ($)"),
                            listOf("Lifetime Pro License", "Revenue", "24.99 one-time"),
                            listOf("Play Store Fee (15%)", "Cost", "-3.75 per sale"),
                            listOf("Server Infrastructure", "Cost", "0.00 (Local-First)")
                        ) else listOf(
                            listOf("Kalem", "Tür", "Tahmini Tutar"),
                            listOf("Ömür Boyu Pro Lisans", "Gelir", "499 ₺ tek seferlik"),
                            listOf("Play Store Komisyonu (%15)", "Maliyet", "-75 ₺ satış başı"),
                            listOf("Sunucu & Altyapı Gideri", "Maliyet", "0 ₺ (Yerel Depolama)")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "🚀 Next Milestone Actions" else "🚀 İlk Sürüm (MVP) Aksiyonları"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Launch closed beta test with 20 privacy-conscious developers"
                                  else "20 kişilik geliştirici grubuyla kapalı beta testini başlat"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Prepare ProductHunt launch graphics and brief video"
                                  else "Lansman için mağaza görsellerini ve kısa tanıtımı hazırla"
                    )
                )
            }
        ),

        // 7. Haftalık Yemek & Beslenme Planı (Yaşam & Sağlık)
        NoteTemplate(
            id = "meal_planner",
            icon = "🥑",
            badgeBgColor = Color(0xFFE8F5E9),
            badgeContentColor = Color(0xFF33691E),
            category = TemplateCategory.LIFE,
            titleRes = R.string.template_meal_title,
            descRes = R.string.template_meal_desc,
            defaultTitleTr = "Haftalık Beslenme & Menü Planı",
            defaultTitleEn = "Weekly Meal & Diet Plan",
            tags = listOf("🥑 Kalori Dengesi", "📊 Menü Tablosu", "🛒 Alışveriş"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🥑",
                        content = if (isEn) "Daily Target: 2,100 kcal | Protein: 130g | Water: 3 Liters"
                                  else "Günlük Hedef: 2.100 kcal | Protein: 130g | Su: 3 Litre"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📊 Weekly Meal Schedule" else "📊 7 Günlük Öğün & Kalori Çizelgesi"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Day", "Main Meals", "Calories (kcal)"),
                            listOf("Monday", "Oatmeal & Grilled Salmon Salad", "2050"),
                            listOf("Tuesday", "Eggs, Avocado & Chicken Rice", "1980"),
                            listOf("Wednesday", "Greek Yogurt & Quinoa Bowl", "2100"),
                            listOf("Thursday", "Vegetable Omelet & Tuna Salad", "2020"),
                            listOf("Friday", "Chia Pudding & Lean Beef Steak", "2150")
                        ) else listOf(
                            listOf("Gün", "Ana Öğünler", "Kalori (kcal)"),
                            listOf("Pazartesi", "Yulaf & Izgara Somonlu Salata", "2050"),
                            listOf("Salı", "Omlet, Avokado & Tavuk Pilav", "1980"),
                            listOf("Çarşamba", "Süzme Yoğurt & Kinoalı Kase", "2100"),
                            listOf("Perşembe", "Sebzeli Omlet & Ton Balıklı Salata", "2020"),
                            listOf("Cuma", "Çiya Puding & Izgara Bonfile", "2150")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "🛒 Grocery Shopping Checklist" else "🛒 Market & Manav Alışveriş Listesi"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Fresh spinach, avocados, cucumbers, and lemons"
                                  else "Taze ıspanak, avokado, salatalık ve limon"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Chicken breast (1 kg) and wild-caught salmon fillets"
                                  else "Tavuk göğsü (1 kg) ve taze somon fileto"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Rolled oats, raw almonds, and chia seeds"
                                  else "Yulaf ezmesi, çiğ badem ve chia tohumu"
                    )
                )
            }
        ),

        // 8. Spor & Antrenman Günlüğü (Yaşam & Sağlık)
        NoteTemplate(
            id = "workout_log",
            icon = "💪",
            badgeBgColor = Color(0xFFFFE0B2),
            badgeContentColor = Color(0xFFD84315),
            category = TemplateCategory.LIFE,
            titleRes = R.string.template_workout_title,
            descRes = R.string.template_workout_desc,
            defaultTitleTr = "Antrenman Günlüğü: İtme (Push)",
            defaultTitleEn = "Workout Log: Push Day",
            tags = listOf("💪 Ağırlık Takibi", "📊 Set/Tekrar", "🏆 Rekor"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "💪",
                        content = if (isEn) "Focus: Chest, Shoulders & Triceps | Session Duration: 60 min"
                                  else "Odak: Göğüs, Omuz & Arka Kol | Seans Süresi: 60 dk"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📊 Exercise, Weight & Rep Tracker" else "📊 Hareket, Ağırlık & Set Çizelgesi"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Exercise", "Weight (kg)", "Sets x Reps"),
                            listOf("Barbell Bench Press", "80", "4 x 8"),
                            listOf("Overhead Shoulder Press", "50", "3 x 10"),
                            listOf("Incline Dumbbell Press", "28", "3 x 12"),
                            listOf("Dips (Bodyweight + 10kg)", "85", "3 x 10"),
                            listOf("Cable Triceps Pushdown", "35", "4 x 15")
                        ) else listOf(
                            listOf("Egzersiz", "Ağırlık (kg)", "Set x Tekrar"),
                            listOf("Barbell Bench Press", "80", "4 x 8"),
                            listOf("Overhead Omuz Press", "50", "3 x 10"),
                            listOf("Incline Dumbbell Press", "28", "3 x 12"),
                            listOf("Dips (Ağırlıklı)", "85", "3 x 10"),
                            listOf("Halat Triceps Pushdown", "35", "4 x 15")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "🏆 Session Notes & Form Check" else "🏆 Antrenman Notları & Gelişim"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Bench Press progressive overload: Target 82.5 kg next week"
                                  else "Bench Press progressive overload: Gelecek hafta 82.5 kg dene"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Full 10-minute shoulder mobility cooldown"
                                  else "Antrenman sonu 10 dakika omuz esneme ve soğuma yap"
                    )
                )
            }
        ),

        // 9. Zihin & Şükran Günlüğü (Yaşam & Sağlık)
        NoteTemplate(
            id = "gratitude_journal",
            icon = "🌿",
            badgeBgColor = Color(0xFFF1F8E9),
            badgeContentColor = Color(0xFF558B2F),
            category = TemplateCategory.LIFE,
            titleRes = R.string.template_gratitude_title,
            descRes = R.string.template_gratitude_desc,
            defaultTitleTr = "Zihin & Şükran Günlüğü",
            defaultTitleEn = "Mindfulness & Gratitude Journal",
            tags = listOf("🌿 Farkındalık", "✨ Şükran", "🧘 Dinginlik"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🌿",
                        content = if (isEn) "\"Peace comes from within. Do not seek it without.\" — Buddha"
                                  else "\"Huzur içten gelir. Onu dışarıda arama.\" — Buda"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "✨ 3 Things I am Grateful For Today" else "✨ Bugün Minnettar Olduğum 3 Şey"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "A refreshing peaceful morning and uninterrupted focus time."
                                  else "Sakin bir sabah başlangıcı ve bölünmeyen odaklanma zamanı."
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Overcoming a challenging engineering task through patience."
                                  else "Sabırla üzerine gidip çözüme kavuşturduğum karmaşık bir problem."
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "A warm heartfelt conversation with people who matter."
                                  else "Sevdiklerimle paylaştığım samimi, destekleyici bir sohbet."
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🧘 Tomorrow's Single North Star" else "🧘 Yarın İçin Tek Büyük Odak Noktam"
                    ),
                    NoteBlock(
                        type = BlockType.TOGGLE,
                        content = if (isEn) "Click to reveal tomorrow's commitment..." else "Yarınki niyetimi ve odağımı göster...",
                        toggleBody = if (isEn) "Eliminate notifications during deep work blocks. Protect mental clarity over reactive busyness."
                                     else "Derin çalışma saatlerinde tüm bildirimleri kapat. Tepkisel meşguliyet yerine berrak zihne odaklan.",
                        isExpanded = false
                    )
                )
            }
        ),

        // 10. Aylık Bütçe & Harcama Planı (İş & Finans)
        NoteTemplate(
            id = "budget",
            icon = "📊",
            badgeBgColor = Color(0xFFE8F5E9),
            badgeContentColor = Color(0xFF2E7D32),
            category = TemplateCategory.WORK,
            titleRes = R.string.template_budget_title,
            descRes = R.string.template_budget_desc,
            defaultTitleTr = "Aylık Bütçe Planı",
            defaultTitleEn = "Monthly Budget Plan",
            tags = listOf("📊 Dinamik Tablo", "∑ Otomatik Toplam", "🎯 Tasarruf"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "💡",
                        content = if (isEn) "Edit table cells dynamically. The bottom sum (∑) recalculates automatically."
                                  else "Tablo hücrelerini düzenleyebilirsiniz. Dipteki toplam (∑) otomatik güncellenir."
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "💳 Income & Fixed Expenses" else "💳 Gelir ve Sabit Giderler"
                    ),
                    NoteBlock(
                        type = BlockType.TABLE,
                        tableData = if (isEn) listOf(
                            listOf("Category", "Amount ($)", "Notes"),
                            listOf("Salary / Income", "3500", "Primary salary"),
                            listOf("Rent / Housing", "1200", "Paid on 1st"),
                            listOf("Groceries & Food", "500", "Weekly market"),
                            listOf("Utilities & Bills", "250", "Electric, web, water"),
                            listOf("Savings & Invest", "800", "Emergency fund")
                        ) else listOf(
                            listOf("Kategori", "Tutar (₺)", "Not"),
                            listOf("Maaş / Gelir", "45000", "Ana gelir"),
                            listOf("Ev Kirası", "15000", "Ayın 1'i"),
                            listOf("Market & Mutfak", "8000", "Haftalık alışveriş"),
                            listOf("Faturalar & Aidat", "3500", "Elektrik, su, net"),
                            listOf("Tasarruf & Yatırım", "10000", "Acil fon")
                        )
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_2,
                        content = if (isEn) "🎯 Savings Targets" else "🎯 Tasarruf Hedefleri"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Review and cancel unused monthly subscriptions"
                                  else "Kullanılmayan abonelikleri gözden geçir ve iptal et"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Cook at home 5 days a week"
                                  else "Haftada en az 5 gün evde yemek hazırla"
                    )
                )
            }
        ),

        // 11. Toplantı Notları (İş & Finans)
        NoteTemplate(
            id = "meeting",
            icon = "📋",
            badgeBgColor = Color(0xFFEFEBE9),
            badgeContentColor = Color(0xFF5D4037),
            category = TemplateCategory.WORK,
            titleRes = R.string.template_meeting_title,
            descRes = R.string.template_meeting_desc,
            defaultTitleTr = "Toplantı: Proje Yol Haritası",
            defaultTitleEn = "Meeting: Project Roadmap",
            tags = listOf("👥 Toplantı", "✅ Görevler", "📌 Kararlar"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "📅",
                        content = if (isEn) "Date: Today | Attendees: Team | Duration: 45 min"
                                  else "Tarih: Bugün | Katılımcılar: Ekip | Süre: 45 dk"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "📌 Agenda Items" else "📌 Gündem Maddeleri"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Review of current milestone achievements"
                                  else "Mevcut kilometre taşlarının değerlendirilmesi"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Key blockers and timeline risks"
                                  else "Kritik engeller ve takvim riskleri"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🎯 Key Decisions" else "🎯 Alınan Kararlar"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Release target locked for next Friday"
                                  else "Sürüm hedefi gelecek Cuma olarak netleştirildi"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "✅ Action Items" else "✅ Aksiyon Listesi & Dağılım"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Complete UI component audits (@Alex - Tuesday)"
                                  else "UI bileşen denetimlerinin tamamlanması (@Ahmet - Salı)"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Prepare staging build & QA tests (@Dev - Thursday)"
                                  else "Test ortamı ve QA hazırlığı (@Mehmet - Perşembe)"
                    )
                )
            }
        ),

        // 12. Seyahat & Bavul Listesi (Yaşam & Sağlık)
        NoteTemplate(
            id = "travel",
            icon = "🧭",
            badgeBgColor = Color(0xFFE1F5FE),
            badgeContentColor = Color(0xFF0277BD),
            category = TemplateCategory.LIFE,
            titleRes = R.string.template_travel_title,
            descRes = R.string.template_travel_desc,
            defaultTitleTr = "Seyahat Planı: Roma & Floransa",
            defaultTitleEn = "Travel Plan: Rome & Florence",
            tags = listOf("🧳 Bavul", "🪪 Belgeler", "📍 Rota", "🏨 Otel"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "📍",
                        content = if (isEn) "Destination: Rome, Italy | Dates: Next Month | Hotel: City Center"
                                  else "Hedef: Roma & Floransa | Tarihler: Gelecek Ay | Otel: Şehir Merkezi"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🪪 Documents & Essentials" else "🪪 Belgeler & Biletler"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Check passport validity (>6 months)" else "Pasaport geçerlilik kontrolü (>6 ay)"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Download boarding passes & booking confirmations" else "Uçak biletleri ve otel rezervasyon belgeleri"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🧳 Packing Checklist" else "🧳 Bavul Kontrol Listesi"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Comfortable walking shoes & weather gear" else "Rahat yürüyüş ayakkabıları & mevsime uygun kıyafetler"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Chargers, powerbank, and EU plug adapter" else "Şarj cihazları, powerbank ve priz adaptörü"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Travel medical kit and daily vitamins" else "Kişisel bakım kiti ve seyahat ilk yardım çantası"
                    )
                )
            }
        ),

        // 13. Haftalık Sprint & Hedefler (İş & Finans)
        NoteTemplate(
            id = "goals",
            icon = "🎯",
            badgeBgColor = Color(0xFFFFF3E0),
            badgeContentColor = Color(0xFFE65100),
            category = TemplateCategory.WORK,
            titleRes = R.string.template_goals_title,
            descRes = R.string.template_goals_desc,
            defaultTitleTr = "Haftalık Hedefler & Sprint",
            defaultTitleEn = "Weekly Goals & Sprint",
            tags = listOf("🔥 P1 Öncelikler", "⚡ İkincil", "🧘 Alışkanlıklar"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "🔥",
                        content = if (isEn) "Weekly North Star: Ship the release update smoothly."
                                  else "Haftalık Ana Odak: Sürüm güncellemesini eksiksiz yayınla."
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🚀 Priority 1 (Must-Have)" else "🚀 Öncelik 1 (P1 - Olmazsa Olmaz)"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Finish Play Store listing screenshots & description"
                                  else "Play Store mağaza görselleri ve açıklamasını tamamla"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Complete end-to-end regression testing on physical device"
                                  else "Fiziksel cihazda uçtan uca testleri gerçekleştir"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "⚡ Priority 2 (Nice-to-Have)" else "⚡ Öncelik 2 (P2 - İkincil)"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Inbox Zero: Clear all unread support emails"
                                  else "E-posta kutusunu temizle (Inbox Zero)"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🧘 Wellness & Mind" else "🧘 Sağlık & Kişisel Alışkanlıklar"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Exercise or walk for at least 30 minutes (3 days)"
                                  else "Haftada 3 gün en az 30 dakika yürüyüş / egzersiz yap"
                    ),
                    NoteBlock(
                        type = BlockType.TODO,
                        content = if (isEn) "Read 50 pages from personal development book"
                                  else "Kitaptan en az 50 sayfa oku"
                    )
                )
            }
        ),

        // 14. Kitap / Medya İncelemesi (Yaşam & Sağlık)
        NoteTemplate(
            id = "review",
            icon = "📚",
            badgeBgColor = Color(0xFFF3E5F5),
            badgeContentColor = Color(0xFF7B1FA2),
            category = TemplateCategory.LIFE,
            titleRes = R.string.template_review_title,
            descRes = R.string.template_review_desc,
            defaultTitleTr = "Kitap İncelemesi: Atomik Alışkanlıklar",
            defaultTitleEn = "Book Review: Atomic Habits",
            tags = listOf("⭐ Puanlama", "📖 Özet", "✨ Alıntılar", "👁️ Spoiler"),
            buildBlocks = { ctx ->
                val isEn = ctx.resources.configuration.locales[0].language.startsWith("en", ignoreCase = true)
                listOf(
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "📖",
                        content = if (isEn) "Author: James Clear | Rating: ⭐⭐⭐⭐⭐ (5/5) | Finished: Today"
                                  else "Yazar: James Clear | Puan: ⭐⭐⭐⭐⭐ (5/5) | Bitiş: Bugün"
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "💡 Core Takeaways" else "💡 Ana Fikir ve Kısa Özet"
                    ),
                    NoteBlock(
                        type = BlockType.PARAGRAPH,
                        content = if (isEn) "Small 1% daily changes accumulate into massive compound growth over time."
                                  else "Her gün yapılan %1'lik küçük iyileşmeler zamanla birikerek devasa değişimler yaratır."
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🔍 In-depth Analysis & Spoilers" else "🔍 Detaylı Bölüm Notları"
                    ),
                    NoteBlock(
                        type = BlockType.TOGGLE,
                        content = if (isEn) "Click to reveal key chapter takeaways..." else "Bölüm özetlerini görmek için tıklayın...",
                        toggleBody = if (isEn) "1. Cue: Make it obvious.\n2. Craving: Make it attractive.\n3. Response: Make it easy.\n4. Reward: Make it satisfying."
                                     else "1. İpucu: Görünür kılın.\n2. İstek: Cazip kılın.\n3. Tepki: Kolaylaştırın.\n4. Ödül: Tatmin edici kılın.",
                        isExpanded = false
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "✨ Favorite Quotes" else "✨ En Etkileyici Alıntılar"
                    ),
                    NoteBlock(
                        type = BlockType.CALLOUT,
                        calloutIcon = "❝",
                        content = if (isEn) "\"You do not rise to the level of your goals. You fall to the level of your systems.\""
                                  else "\"Hedeflerinizin seviyesine yükselmezsiniz, sistemlerinizin seviyesine gerilersiniz.\""
                    )
                )
            }
        )
    )
}
