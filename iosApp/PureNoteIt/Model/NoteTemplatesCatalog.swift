import Foundation
import PureNoteShared
import SwiftUI

enum IosTemplateCategory: String, CaseIterable, Identifiable {
    case all
    case tech
    case education
    case work
    case life

    var id: String { rawValue }

    func label(isTr: Bool) -> String {
        switch self {
        case .all: return isTr ? "Tümü" : "All"
        case .tech: return isTr ? "Teknoloji" : "Tech"
        case .education: return isTr ? "Eğitim" : "Education"
        case .work: return isTr ? "İş & Finans" : "Work & Finance"
        case .life: return isTr ? "Yaşam" : "Life"
        }
    }
}

struct IosNoteTemplate: Identifiable {
    let id: String
    let iconKey: String
    let accentColor: Color
    let category: IosTemplateCategory
    let titleTr: String
    let titleEn: String
    let descTr: String
    let descEn: String
    let tags: [String]
    let buildBlocks: (_ isTr: Bool) -> [EditableBlock]

    func title(isTr: Bool) -> String { isTr ? titleTr : titleEn }
    func description(isTr: Bool) -> String { isTr ? descTr : descEn }
}

enum NoteTemplatesCatalog {
    private static func mk(
        _ kind: IosBlockKind,
        content: String = "",
        isChecked: Bool = false,
        calloutIcon: String = "💡",
        isExpanded: Bool = true,
        toggleBody: String = "",
        codeLang: String = "kotlin",
        table: [[String]] = []
    ) -> EditableBlock {
        EditableBlock(
            id: SharedIosEditorBridge.shared.doNewUuid(),
            type: kind,
            content: content,
            isChecked: isChecked,
            calloutIcon: calloutIcon,
            mediaPath: nil,
            isExpanded: isExpanded,
            toggleBody: toggleBody,
            codeLanguage: codeLang,
            tableData: table
        )
    }

    static let all: [IosNoteTemplate] = [
        // 1. Bug Analysis & Code Fix (Tech)
        IosNoteTemplate(
            id: "dev_bug_tracker",
            iconKey: "⚡",
            accentColor: Color(red: 0.08, green: 0.40, blue: 0.75),
            category: .tech,
            titleTr: "Hata Analizi & Kod Çözümü",
            titleEn: "Bug Analysis & Code Fix",
            descTr: "Kod bloğu, modül etki tablosu ve doğrulama kontrolleri.",
            descEn: "Code block, module impact table, and verification checklist.",
            tags: ["Kod Bloğu", "Hata Seviyesi", "Aksiyonlar"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Hata: Coroutine Scope Bellek Sızıntısı & ANR Önleme" : "Issue: Coroutine Scope Memory Leak & ANR Prevention", calloutIcon: "⚡"),
                mk(.heading1, content: tr ? "Optimize Edilmiş Çözüm Kodu" : "Optimized Solution Code"),
                mk(.code, content: "// Structured concurrency\nviewModelScope.launch(Dispatchers.IO) {\n    val result = repository.fetchDataSync()\n    withContext(Dispatchers.Main) {\n        _uiState.value = UiState.Success(result)\n    }\n}", codeLang: "kotlin"),
                mk(.heading1, content: tr ? "Modül Etki & Öncelik Tablosu" : "Module Impact Table"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Modül", "Öncelik", "Durum"], ["AuthManager", "P0 - Kritik", "Çözüldü"], ["ImageLoader", "P1 - Orta", "Test Ortamında"], ["SyncWorker", "P2 - Düşük", "Planlandı"]]
                    : [["Module", "Severity", "Status"], ["AuthManager", "P0 - Critical", "Resolved"], ["ImageLoader", "P1 - Medium", "In Staging QA"], ["SyncWorker", "P2 - Low", "Scheduled"]]),
                mk(.heading2, content: tr ? "Doğrulama & Test Kontrolleri" : "Verification Checklist"),
                mk(.todo, content: tr ? "Bellek tahsisini profiler ile kontrol et" : "Profile memory allocation"),
                mk(.todo, content: tr ? "Fiziksel cihazda regresyon testlerini çalıştır" : "Run regression tests on physical device")
            ]
        },

        // 2. Math & Formula Notebook (Education)
        IosNoteTemplate(
            id: "math_notebook",
            iconKey: "♾️",
            accentColor: Color(red: 0.96, green: 0.50, blue: 0.09),
            category: .education,
            titleTr: "Matematik & Formül Defteri",
            titleEn: "Math & Formula Notebook",
            descTr: "LaTeX formülleri, açılır ispat ve çizim taslağı.",
            descEn: "LaTeX formulas, proof toggle, and sketch prompt.",
            tags: ["LaTeX", "Teorem", "İspat"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Konu: Kalkülüsün Temel Teoremi & Alan Entegrasyonu" : "Topic: Fundamental Theorem of Calculus", calloutIcon: "♾️"),
                mk(.heading1, content: tr ? "Temel Matematik Formülleri" : "Core Mathematical Formulas"),
                mk(.math, content: "\\int_{a}^{b} f(x) dx = F(b) - F(a)"),
                mk(.math, content: "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}"),
                mk(.heading1, content: tr ? "Teorem İspatı & Geometrik Yorum" : "Theorem Proof & Geometric Insight"),
                mk(.toggle, content: tr ? "Adım adım türetim adımlarını göster..." : "Reveal step-by-step derivation...", isExpanded: false, toggleBody: tr ? "1. A(x) alan fonksiyonu olsun.\n2. Türev tanımı gereği A'(x) = f(x).\n3. F(b) - F(a) toplam alana eşittir." : "1. Let A(x) be area under f(t).\n2. A'(x) = f(x).\n3. F(b) - F(a) = Area."),
                mk(.heading2, content: tr ? "Geometri & Grafik Çizim Notu" : "Geometry Sketch Area"),
                mk(.callout, content: tr ? "Koordinat eksenlerini çizmek için Çizim aracını kullanın." : "Use the Draw tool to sketch coordinate curves.", calloutIcon: "🎨")
            ]
        },

        // 3. Cornell Notes (Education)
        IosNoteTemplate(
            id: "cornell_notes",
            iconKey: "📖",
            accentColor: Color(red: 0.32, green: 0.18, blue: 0.66),
            category: .education,
            titleTr: "Cornell Notu: Veri Yapıları",
            titleEn: "Cornell Note: Data Structures",
            descTr: "Anahtar kavramlar, aktif hatırlama kartları ve özet.",
            descEn: "Key cues, active recall toggles, and 3-sentence summary.",
            tags: ["Cornell", "Aktif Hatırlama", "Özet"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Ders: Dengeli Arama Ağaçları (AVL & Kırmızı-Siyah)" : "Lecture: Balanced Search Trees (AVL & Red-Black)", calloutIcon: "📖"),
                mk(.heading1, content: tr ? "Anahtar Kavramlar & İpuçları" : "Key Cues & Keywords"),
                mk(.bulletList, content: tr ? "Dengeleme Faktörü: Yükseklik(Sol) - Yükseklik(Sağ) ∈ {-1, 0, 1}" : "Balance Factor: H(left) - H(right) ∈ {-1, 0, 1}"),
                mk(.bulletList, content: tr ? "Döndürmeler: LL, RR (Tekli) ve LR, RL (Çiftli)" : "Rotations: LL, RR (Single) and LR, RL (Double)"),
                mk(.heading1, content: tr ? "Aktif Hatırlama & Soru-Cevap" : "Active Recall (Flashcards)"),
                mk(.toggle, content: tr ? "Soru: AVL ne zaman Kırmızı-Siyah ağaca tercih edilir?" : "Q: When to prefer AVL over Red-Black trees?", isExpanded: false, toggleBody: tr ? "Cevap: Arama işlemleri ekleme/silmeden çok daha fazlaysa." : "Answer: When lookups vastly outnumber writes."),
                mk(.toggle, content: tr ? "Soru: En kötü durum arama karmaşıklığı nedir?" : "Q: Worst-case lookup complexity?", isExpanded: false, toggleBody: tr ? "Cevap: Kesin O(log n)." : "Answer: Strictly O(log n)."),
                mk(.heading1, content: tr ? "3 Cümlelik Ders Özeti" : "3-Sentence Summary"),
                mk(.callout, content: tr ? "AVL ağaçları okuma ağırlıklı indeks yapıları için kesin logaritmik arama garantisi verir." : "AVL trees guarantee O(log n) lookup for read-heavy indexes.", calloutIcon: "💡")
            ]
        },

        // 4. Architecture ADR (Tech)
        IosNoteTemplate(
            id: "architecture_adr",
            iconKey: "🏛️",
            accentColor: Color(red: 0.22, green: 0.28, blue: 0.31),
            category: .tech,
            titleTr: "Mimari Karar: MVI Durum Yönetimi",
            titleEn: "Architecture Decision: MVI State Pattern",
            descTr: "Bağlam, alternatif kıyaslama tablosu ve geçiş adımları.",
            descEn: "Context, alternatives table, and migration steps.",
            tags: ["Mimari ADR", "Kıyaslama", "Yol Haritası"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Durum: Onaylandı | Karar Sahibi: Baş Mimar" : "Status: Accepted | Decision Maker: Lead Architect", calloutIcon: "🏛️"),
                mk(.heading1, content: tr ? "Bağlam & Karşılaşılan Sorun" : "Context & Problem Statement"),
                mk(.paragraph, content: tr ? "Birden fazla asenkron tetikleyici durum tutarsızlığına yol açıyordu. Tek doğruluk kaynağı gerekiyor." : "Multiple async triggers caused inconsistent states. Single source of truth required."),
                mk(.heading1, content: tr ? "Alternatifler Kıyaslama Tablosu" : "Alternatives Comparison"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Kriter", "Klasik MVVM", "MVI StateFlow"], ["Tutarlılık", "Çoklu takip", "Tek Immutable State"], ["Test", "Orta", "Deterministik"]]
                    : [["Criteria", "MVVM", "MVI StateFlow"], ["Consistency", "Multiple streams", "Single Immutable State"], ["Testability", "Medium", "Deterministic"]]),
                mk(.heading2, content: tr ? "Geçiş Adımları" : "Implementation Steps"),
                mk(.todo, content: tr ? "UiIntent ve UiState sözleşmelerini tanımla" : "Define UiIntent and UiState contracts"),
                mk(.todo, content: tr ? "Ekranları tek yönlü veri akışına taşı" : "Migrate screens to UDF")
            ]
        },

        // 5. Language Learning (Education)
        IosNoteTemplate(
            id: "language_learning",
            iconKey: "🗣️",
            accentColor: Color(red: 0.0, green: 0.51, blue: 0.56),
            category: .education,
            titleTr: "İleri Seviye İngilizce Deyimler",
            titleEn: "Advanced English Idioms & Vocab",
            descTr: "Kelime tablosu, sesli telaffuz pratiği ve görevler.",
            descEn: "Vocabulary table, pronunciation recording note, and tasks.",
            tags: ["Kelime", "Telaffuz", "Pratik"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Hedef Dil: İngilizce (B2/C1) | Hedef: Günde 5 deyim" : "Target Language: English/Spanish | Goal: 5 idioms/day", calloutIcon: "🗣️"),
                mk(.heading1, content: tr ? "Kelime & Anlam Tablosu" : "Core Vocabulary Table"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Deyim", "Türkçe Anlamı", "Örnek"], ["Bite the bullet", "Dişini sıkmak", "I decided to bite the bullet."], ["Hit the nail on the head", "Tam üstüne basmak", "Her comment hit the nail on the head."]]
                    : [["Expression", "Meaning", "Example"], ["Bite the bullet", "Face tough situation", "I decided to bite the bullet."], ["Hit the nail on the head", "Exact diagnosis", "Her point hit the nail on the head."]]),
                mk(.heading2, content: tr ? "Telaffuz Pratiği" : "Pronunciation Practice"),
                mk(.callout, content: tr ? "Ses Kaydı aracını kullanarak telaffuzunuzu kaydedip dinleyin." : "Use the Audio tool to record and review pronunciation.", calloutIcon: "🎙️"),
                mk(.todo, content: tr ? "Her deyimle 2 özgün cümle kur" : "Write 2 original sentences per idiom")
            ]
        },

        // 6. Startup Lean Canvas (Work)
        IosNoteTemplate(
            id: "lean_canvas",
            iconKey: "💎",
            accentColor: Color(red: 0.76, green: 0.09, blue: 0.36),
            category: .work,
            titleTr: "Proje Fikir Tuvali: Mobil Girişim",
            titleEn: "Startup Lean Canvas: Mobile App",
            descTr: "Problem, değer önerisi, gelir-maliyet tablosu ve MVP.",
            descEn: "Problem, unique value, revenue/cost table, and MVP actions.",
            tags: ["Değer Önerisi", "Maliyet", "MVP"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Proje: Gizlilik Odaklı Çevrimdışı Not | Aşama: Doğrulama" : "Project: Local-First Privacy Notes | Stage: Validation", calloutIcon: "💎"),
                mk(.heading1, content: tr ? "Problem & Eşsiz Değer Önerisi" : "Problem & Unique Value"),
                mk(.bulletList, content: tr ? "Problem: Bulut uygulamaları abonelik ve veri sızıntısı riski taşıyor." : "Problem: Cloud apps charge fees and risk privacy."),
                mk(.bulletList, content: tr ? "Çözüm: %100 cihazda şifreli SQLite kasa ve uçtan uca Drive sync." : "Solution: Encrypted on-device SQLite + E2EE sync."),
                mk(.heading1, content: tr ? "Gelir & Maliyet Tablosu" : "Revenue & Cost Structure"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Kalem", "Tür", "Tutar"], ["Pro Lisans", "Gelir", "499 ₺"], ["Altyapı", "Maliyet", "0 ₺ (Yerel)"]]
                    : [["Stream", "Type", "Estimate"], ["Pro License", "Revenue", "$24.99"], ["Server Cost", "Cost", "$0 (Local-First)"]]),
                mk(.heading2, content: tr ? "MVP Aksiyonları" : "Next Milestone Actions"),
                mk(.todo, content: tr ? "TestFlight / Kapalı test sürümünü yayınla" : "Publish TestFlight / beta build"),
                mk(.todo, content: tr ? "10 beta kullanıcısıyla görüşme yap" : "Interview 10 beta users")
            ]
        },

        // 7. Nutrition & Meal Plan (Life)
        IosNoteTemplate(
            id: "nutrition_meal",
            iconKey: "🥗",
            accentColor: Color(red: 0.18, green: 0.49, blue: 0.20),
            category: .life,
            titleTr: "Haftalık Beslenme & Yemek Planı",
            titleEn: "Weekly Nutrition & Meal Plan",
            descTr: "Öğün tablosu, kalori takibi ve market alışveriş listesi.",
            descEn: "Meal schedule table, calorie tracking, and grocery checklist.",
            tags: ["Öğün", "Kalori", "Alışveriş"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Günlük Hedef: 2.100 kcal | Protein: 130g | Su: 3L" : "Daily Target: 2,100 kcal | Protein: 130g | Water: 3L", calloutIcon: "🥗"),
                mk(.heading1, content: tr ? "Haftalık Öğün Çizelgesi" : "Weekly Meal Schedule"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Gün", "Ana Öğünler", "Kalori"], ["Pazartesi", "Yulaf & Somon Salata", "2050"], ["Salı", "Omlet, Avokado & Tavuk", "1980"], ["Çarşamba", "Yoğurt & Kinoa Kase", "2100"]]
                    : [["Day", "Main Meals", "Calories"], ["Monday", "Oatmeal & Salmon Salad", "2050"], ["Tuesday", "Eggs, Avocado & Chicken", "1980"], ["Wednesday", "Greek Yogurt & Quinoa", "2100"]]),
                mk(.heading2, content: tr ? "Market Alışveriş Listesi" : "Grocery Shopping Checklist"),
                mk(.todo, content: tr ? "Taze ıspanak, avokado, salatalık ve limon" : "Spinach, avocados, cucumbers, lemons"),
                mk(.todo, content: tr ? "Tavuk göğsü ve taze somon" : "Chicken breast and wild salmon"),
                mk(.todo, content: tr ? "Yulaf ezmesi ve chia tohumu" : "Rolled oats and chia seeds")
            ]
        },

        // 8. Workout Log (Life)
        IosNoteTemplate(
            id: "workout_log",
            iconKey: "💪",
            accentColor: Color(red: 0.85, green: 0.26, blue: 0.08),
            category: .life,
            titleTr: "Antrenman Günlüğü: İtme (Push)",
            titleEn: "Workout Log: Push Day",
            descTr: "Egzersiz, ağırlık, set çizelgesi ve gelişim hedefleri.",
            descEn: "Exercise, weight, set tracker, and progression targets.",
            tags: ["Antrenman", "Ağırlık", "Set"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Odak: Göğüs, Omuz & Arka Kol | Süre: 60 dk" : "Focus: Chest, Shoulders & Triceps | 60 min", calloutIcon: "💪"),
                mk(.heading1, content: tr ? "Hareket, Ağırlık & Set Çizelgesi" : "Exercise & Weight Tracker"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Egzersiz", "Ağırlık (kg)", "Set x Tekrar"], ["Bench Press", "80", "4 x 8"], ["Overhead Press", "50", "3 x 10"], ["Triceps Pushdown", "35", "4 x 15"]]
                    : [["Exercise", "Weight (kg)", "Sets x Reps"], ["Bench Press", "80", "4 x 8"], ["Overhead Press", "50", "3 x 10"], ["Triceps Pushdown", "35", "4 x 15"]]),
                mk(.heading2, content: tr ? "Gelişim Notları" : "Session Notes"),
                mk(.todo, content: tr ? "Gelecek hafta Bench Press 82.5 kg dene" : "Target 82.5 kg Bench Press next week"),
                mk(.todo, content: tr ? "Antrenman sonu 10 dk omuz esneme yap" : "10-min shoulder mobility cooldown")
            ]
        },

        // 9. Gratitude Journal (Life)
        IosNoteTemplate(
            id: "gratitude_journal",
            iconKey: "🌿",
            accentColor: Color(red: 0.33, green: 0.55, blue: 0.18),
            category: .life,
            titleTr: "Zihin & Şükran Günlüğü",
            titleEn: "Mindfulness & Gratitude Journal",
            descTr: "İlham alıntısı, 3 şükran maddesi ve yarınki odak.",
            descEn: "Quote callout, 3 gratitude bullets, and tomorrow's north star.",
            tags: ["Farkındalık", "3 Şükran", "Odak"]
        ) { tr in
            [
                mk(.callout, content: tr ? "\"Huzur içten gelir. Onu dışarıda arama.\" — Buda" : "\"Peace comes from within. Do not seek it without.\"", calloutIcon: "🌿"),
                mk(.heading1, content: tr ? "Bugün Minnettar Olduğum 3 Şey" : "3 Things I am Grateful For Today"),
                mk(.bulletList, content: tr ? "Sakin bir sabah başlangıcı ve odaklanma zamanı." : "A peaceful morning and deep focus time."),
                mk(.bulletList, content: tr ? "Sabırla çözüme kavuşturduğum karmaşık bir problem." : "Solving a tough problem through patience."),
                mk(.bulletList, content: tr ? "Sevdiklerimle paylaştığım samimi bir sohbet." : "A heartfelt conversation with loved ones."),
                mk(.heading1, content: tr ? "Yarın İçin Tek Büyük Odak" : "Tomorrow's North Star"),
                mk(.toggle, content: tr ? "Yarınki niyetimi göster..." : "Reveal tomorrow's commitment...", isExpanded: false, toggleBody: tr ? "Derin çalışma saatlerinde bildirimleri kapat ve berrak zihne odaklan." : "Silence notifications during deep work.")
            ]
        },

        // 10. Monthly Budget (Work)
        IosNoteTemplate(
            id: "budget",
            iconKey: "📊",
            accentColor: Color(red: 0.18, green: 0.49, blue: 0.20),
            category: .work,
            titleTr: "Aylık Bütçe Planı",
            titleEn: "Monthly Budget Plan",
            descTr: "Otomatik dip toplamlı (∑) gelir/gider tablosu.",
            descEn: "Dynamic income & expense table with automatic bottom sum.",
            tags: ["Bütçe", "Otomatik Toplam", "Tasarruf"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Tablo hücrelerini düzenleyin. Dipteki toplam (∑) otomatik güncellenir." : "Edit table cells. Bottom sum (∑) updates automatically.", calloutIcon: "💡"),
                mk(.heading1, content: tr ? "Gelir ve Sabit Giderler" : "Income & Fixed Expenses"),
                mk(.table, content: "Tablo", table: tr
                    ? [["Kategori", "Tutar (₺)", "Not"], ["Maaş / Gelir", "45000", "Ana gelir"], ["Ev Kirası", "15000", "Ayın 1'i"], ["Market & Mutfak", "8000", "Haftalık"], ["Tasarruf", "10000", "Acil fon"]]
                    : [["Category", "Amount ($)", "Notes"], ["Salary", "3500", "Primary"], ["Rent", "1200", "1st of month"], ["Groceries", "500", "Weekly"], ["Savings", "800", "Reserve"]]),
                mk(.heading2, content: tr ? "Tasarruf Hedefleri" : "Savings Targets"),
                mk(.todo, content: tr ? "Kullanılmayan abonelikleri iptal et" : "Cancel unused subscriptions"),
                mk(.todo, content: tr ? "Haftada 5 gün evde yemek hazırla" : "Cook at home 5 days/week")
            ]
        },

        // 11. Meeting Notes (Work)
        IosNoteTemplate(
            id: "meeting",
            iconKey: "📋",
            accentColor: Color(red: 0.42, green: 0.29, blue: 0.24),
            category: .work,
            titleTr: "Toplantı: Proje Yol Haritası",
            titleEn: "Meeting: Project Roadmap",
            descTr: "Gündem maddeleri, alınan kararlar ve aksiyon listesi.",
            descEn: "Agenda items, key decisions, and assigned action tasks.",
            tags: ["Gündem", "Kararlar", "Aksiyon"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Tarih: Bugün | Katılımcılar: Ekip | Süre: 45 dk" : "Date: Today | Attendees: Team | Duration: 45 min", calloutIcon: "📅"),
                mk(.heading1, content: tr ? "Gündem Maddeleri" : "Agenda Items"),
                mk(.bulletList, content: tr ? "Mevcut kilometre taşlarının değerlendirilmesi" : "Review current milestone achievements"),
                mk(.bulletList, content: tr ? "Kritik engeller ve takvim riskleri" : "Key blockers and timeline risks"),
                mk(.heading1, content: tr ? "Alınan Kararlar" : "Key Decisions"),
                mk(.bulletList, content: tr ? "Sürüm hedefi gelecek Cuma olarak netleştirildi" : "Release target locked for next Friday"),
                mk(.heading1, content: tr ? "Aksiyon Listesi & Dağılım" : "Action Items"),
                mk(.todo, content: tr ? "UI bileşen denetimlerinin tamamlanması" : "Complete UI component audits"),
                mk(.todo, content: tr ? "Test ortamı ve QA hazırlığı" : "Prepare staging build & QA")
            ]
        },

        // 12. Travel & Packing (Life)
        IosNoteTemplate(
            id: "travel",
            iconKey: "🧭",
            accentColor: Color(red: 0.01, green: 0.47, blue: 0.74),
            category: .life,
            titleTr: "Seyahat Planı: Roma & Floransa",
            titleEn: "Travel Plan: Rome & Florence",
            descTr: "Belgeler, biletler ve bavul kontrol listesi.",
            descEn: "Travel documents, bookings, and packing checklist.",
            tags: ["Bavul", "Bilet", "Rota"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Hedef: İtalya | Süre: 7 Gün | Uçuş: 10:30" : "Destination: Italy | Duration: 7 Days | Flight: 10:30 AM", calloutIcon: "🧭"),
                mk(.heading1, content: tr ? "Belgeler & Biletler" : "Documents & Essentials"),
                mk(.todo, content: tr ? "Pasaport, vize ve biniş kartı" : "Passport and boarding pass"),
                mk(.todo, content: tr ? "Otel rezervasyon belgesi ve sigorta" : "Hotel voucher & travel insurance"),
                mk(.heading1, content: tr ? "Bavul Kontrol Listesi" : "Packing Checklist"),
                mk(.todo, content: tr ? "Evrensel priz dönüştürücü ve powerbank" : "Power adapter and powerbank"),
                mk(.todo, content: tr ? "Gürültü engelleyici kulaklık" : "Noise-cancelling headphones"),
                mk(.todo, content: tr ? "Rahat yürüyüş ayakkabısı" : "Comfortable walking sneakers")
            ]
        },

        // 13. Weekly Goals & Sprint (Work)
        IosNoteTemplate(
            id: "goals",
            iconKey: "🎯",
            accentColor: Color(red: 0.90, green: 0.32, blue: 0.0),
            category: .work,
            titleTr: "Haftalık Hedefler & Sprint",
            titleEn: "Weekly Goals & Sprint",
            descTr: "P1/P2 öncelikli sprint görevleri ve alışkanlık takibi.",
            descEn: "P1/P2 sprint priorities and personal habits.",
            tags: ["P1 Öncelik", "Sprint", "Alışkanlık"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Haftalık Ana Odak: Sürüm güncellemesini eksiksiz yayınla." : "Weekly North Star: Ship release smoothly.", calloutIcon: "🎯"),
                mk(.heading1, content: tr ? "Öncelik 1 (P1 - Olmazsa Olmaz)" : "Priority 1 (Must-Have)"),
                mk(.todo, content: tr ? "Mağaza ekran görüntüleri ve açıklamayı tamamla" : "Finish store screenshots & copy"),
                mk(.todo, content: tr ? "Fiziksel cihazda uçtan uca testleri gerçekleştir" : "Run end-to-end tests on device"),
                mk(.heading1, content: tr ? "Öncelik 2 (İkincil)" : "Priority 2 (Nice-to-Have)"),
                mk(.todo, content: tr ? "E-posta kutusunu temizle (Inbox Zero)" : "Inbox Zero cleanup"),
                mk(.heading1, content: tr ? "Sağlık & Alışkanlıklar" : "Wellness & Mind"),
                mk(.todo, content: tr ? "Haftada 3 gün en az 30 dk yürüyüş / egzersiz" : "Exercise 30 min (3 days)"),
                mk(.todo, content: tr ? "Kitaptan 50 sayfa oku" : "Read 50 pages")
            ]
        },

        // 14. Book / Media Review (Life)
        IosNoteTemplate(
            id: "review",
            iconKey: "📚",
            accentColor: Color(red: 0.48, green: 0.12, blue: 0.64),
            category: .life,
            titleTr: "Kitap İncelemesi: Atomik Alışkanlıklar",
            titleEn: "Book Review: Atomic Habits",
            descTr: "Puanlama, özet, bölüm toggle notları ve alıntılar.",
            descEn: "Rating card, summary, chapter toggles, and quotes.",
            tags: ["Kitap", "Özet", "Alıntı"]
        ) { tr in
            [
                mk(.callout, content: tr ? "Yazar: James Clear | Puan: 5/5 | Bitiş: Bugün" : "Author: James Clear | Rating: 5/5 | Finished: Today", calloutIcon: "📖"),
                mk(.heading1, content: tr ? "Ana Fikir ve Kısa Özet" : "Core Takeaways"),
                mk(.paragraph, content: tr ? "Her gün yapılan %1'lik küçük iyileşmeler zamanla birikerek devasa değişimler yaratır." : "Small 1% daily improvements compound into remarkable results."),
                mk(.heading1, content: tr ? "Detaylı Bölüm Notları" : "In-depth Chapter Notes"),
                mk(.toggle, content: tr ? "Bölüm özetlerini görmek için tıklayın..." : "Reveal chapter takeaways...", isExpanded: false, toggleBody: tr ? "1. İpucu: Görünür kılın.\n2. İstek: Cazip kılın.\n3. Tepki: Kolaylaştırın.\n4. Ödül: Tatmin edici kılın." : "1. Cue: Make it obvious.\n2. Craving: Make it attractive.\n3. Response: Make it easy.\n4. Reward: Make it satisfying."),
                mk(.heading1, content: tr ? "En Etkileyici Alıntılar" : "Favorite Quotes"),
                mk(.callout, content: "\"You do not rise to the level of your goals. You fall to the level of your systems.\"", calloutIcon: "❝")
            ]
        }
    ]
}
