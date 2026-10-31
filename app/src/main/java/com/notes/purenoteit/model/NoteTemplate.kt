package com.notes.purenoteit.model

import android.content.Context
import com.notes.purenoteit.R

data class NoteTemplate(
    val id: String,
    val icon: String,
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
        // 1. Toplantı Notları
        NoteTemplate(
            id = "meeting",
            icon = "💼",
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

        // 2. Aylık Bütçe & Harcama Planı
        NoteTemplate(
            id = "budget",
            icon = "💰",
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
                            listOf("Ev Kirası", "15000", "Ayın 1\'i"),
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

        // 3. Kitap / Film / Dizi İncelemesi
        NoteTemplate(
            id = "review",
            icon = "📚",
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
                                  else "Her gün yapılan %1\'lik küçük iyileşmeler zamanla birikerek devasa değişimler yaratır."
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
        ),

        // 4. Seyahat & Bavul Listesi
        NoteTemplate(
            id = "travel",
            icon = "✈️",
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
                    ),
                    NoteBlock(
                        type = BlockType.HEADING_1,
                        content = if (isEn) "🗺️ Must-Visit Spots" else "🗺️ Gezilecek Yerler & Müzeler"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Colosseum & Roman Forum (Early morning)" else "Kolezyum & Roma Forumu (Sabah erken saatte)"
                    ),
                    NoteBlock(
                        type = BlockType.BULLET_LIST,
                        content = if (isEn) "Trevi Fountain & Spanish Steps" else "Trevi Çeşmesi ve İspanyol Merdivenleri"
                    )
                )
            }
        ),

        // 5. Haftalık Sprint & Hedefler
        NoteTemplate(
            id = "goals",
            icon = "🎯",
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
                        content = if (isEn) "Weekly North Star: Ship the 2.0 release update smoothly."
                                  else "Haftalık Ana Odak: 2.0 sürüm güncellemesini eksiksiz yayınla."
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
        )
    )
}
