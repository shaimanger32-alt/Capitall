package com.shai.capitall.util.csv

import com.shai.capitall.data.model.Transaction
import java.util.Locale

/** מקור השיוך — קובע את רמת הביטחון שמוצגת למשתמש במסך התצוגה המקדימה. */
enum class CategorySource {
    /** נלמד מהעסקאות הקיימות של המשתמש — הביטחון הגבוה ביותר. */
    HISTORY,

    /** זוהה לפי טבלת מילות מפתח של בתי עסק מוכרים. */
    RULE,

    /** לא זוהה — נפל לקטגוריית ברירת מחדל לפי סימן הסכום, וכדאי שהמשתמש יעבור עליו. */
    FALLBACK
}

data class Categorized(val category: String, val source: CategorySource)

/**
 * משייך קטגוריה לשם בית עסק מקובץ הבנק.
 *
 * סדר העדיפויות מכוון: קודם מה שהמשתמש עצמו כבר סיווג בעבר (כי סיווג אישי גובר תמיד
 * על ניחוש גנרי), אחר כך טבלת בתי עסק מוכרים, ורק אז ברירת מחדל לפי סימן הסכום.
 *
 * לוגיקה טהורה (ללא Android/Firebase) — ראה MerchantCategorizerTest.
 */
class MerchantCategorizer(history: List<Transaction> = emptyList()) {

    /** שם בית עסק מנורמל → הקטגוריה שהמשתמש בחר לו הכי הרבה פעמים. */
    private val learned: Map<String, String> = history
        .filter { it.merchant.isNotBlank() && it.category.isNotBlank() }
        .groupBy { normalize(it.merchant) }
        .mapValues { (_, transactions) ->
            transactions.groupingBy { it.category }.eachCount().maxByOrNull { it.value }!!.key
        }
        .filterKeys { it.isNotBlank() }

    fun categorize(merchant: String, amount: Double): Categorized {
        // כיוון התנועה קובע אילו קטגוריות בכלל רלוונטיות. בלי זה זיכוי מבית עסק מוכר
        // (למשל החזר ממסעדה) היה מקבל קטגוריית הוצאה — ואז בורר הקטגוריה במסך התצוגה
        // המקדימה, שמסונן לפי כיוון, לא היה מציג בכלל את הקטגוריה שהוצגה בשורה.
        val wantsIncome = amount > 0
        val normalized = normalize(merchant)

        if (normalized.isNotBlank()) {
            learned[normalized]
                ?.takeIf { isIncomeCategory(it) == wantsIncome }
                ?.let { return Categorized(it, CategorySource.HISTORY) }

            // התאמה חלקית: דפי בנק מוסיפים סניף/עיר לשם ("שופרסל דיל רמת גן"), ולכן
            // שם היסטורי שמוכל בשם החדש (או להפך) נחשב אותו בית עסק.
            learned.entries.firstOrNull { (known, category) ->
                known.length >= MIN_PARTIAL_LENGTH &&
                    isIncomeCategory(category) == wantsIncome &&
                    (normalized.contains(known) || known.contains(normalized))
            }?.let { return Categorized(it.value, CategorySource.HISTORY) }

            RULES.firstOrNull { rule ->
                isIncomeCategory(rule.category) == wantsIncome &&
                    rule.keywords.any { normalized.contains(it) }
            }?.let { return Categorized(it.category, CategorySource.RULE) }
        }

        val fallback = if (wantsIncome) FALLBACK_INCOME else FALLBACK_EXPENSE
        return Categorized(fallback, CategorySource.FALLBACK)
    }

    private fun isIncomeCategory(key: String): Boolean = key in INCOME_CATEGORIES

    private fun normalize(raw: String): String = BankStatementParser.normalize(raw)

    private data class Rule(val category: String, val keywords: List<String>)

    companion object {
        const val FALLBACK_EXPENSE = "other_expense"
        const val FALLBACK_INCOME = "Income"

        /**
         * מפתחות קטגוריות ההכנסה, כמו ב-[com.shai.capitall.util.CategoryCatalog].
         * מוחזק כאן כרשימה פשוטה כדי שהמחלקה תישאר טהורה וניתנת לבדיקה בלי Android.
         */
        private val INCOME_CATEGORIES = setOf(
            "salary", "business_income", "investment_income", "rent_income", "gift_income", "Income"
        )

        /** מתחת לאורך הזה התאמה חלקית רועשת מדי ("בז" היה תופס כל שם שני). */
        private const val MIN_PARTIAL_LENGTH = 4

        /**
         * טבלת בתי עסק מוכרים. הסדר משמעותי — הכלל הראשון שמתאים מנצח, ולכן כללים
         * ספציפיים מופיעים לפני כללים רחבים (למשל "סופר פארם" לפני "סופר").
         */
        private val RULES: List<Rule> = listOf(
            // בריאות — לפני מזון, כי "סופר פארם" מכיל את "סופר"
            Rule(
                "Health",
                listOf(
                    "סופר פארם", "סופרפארם", "superpharm", "super pharm", "ניו פארם", "בי פארם",
                    "מכבי", "כללית", "מאוחדת", "לאומית שירותי", "קופת חולים", "בית מרקחת",
                    "מרפאה", "רופא", "שיניים", "אופטיק", "פארמה", "pharm", "clinic", "dental"
                )
            ),

            // מזון וסופרמרקטים
            Rule(
                "food",
                listOf(
                    "שופרסל", "רמי לוי", "יינות ביתן", "ויקטורי", "מגה בעיר", "טיב טעם",
                    "אושר עד", "יוחננוף", "חצי חינם", "am pm", "ampm", "טיב", "סופר",
                    "מכולת", "קפה", "ארומה", "קופיקס", "לנדוור", "מסעד", "פיצה", "בורגר",
                    "מקדונלד", "kfc", "דומינוס", "וולט", "wolt", "תן ביס", "10bis", "cibus",
                    "סיבוס", "בייקרי", "מאפ", "restaurant", "cafe", "coffee", "pizza", "burger"
                )
            ),

            // תחבורה
            Rule(
                "Transport",
                listOf(
                    "פז", "סונול", "דלק", "דור אלון", "ten", "סד ש", "תדלוק", "דלקן",
                    "רב קו", "רבקו", "אגד", "דן ", "מטרופולין", "רכבת", "מוניות", "מונית",
                    "gett", "גט טקסי", "uber", "אובר", "פנגו", "pango", "סלופארק", "cellopark",
                    "חניון", "חנייה", "כביש 6", "כביש6", "יס פארק", "טסט", "מוסך", "צמיג",
                    "fuel", "parking", "taxi", "train"
                )
            ),

            // דיור וחשבונות
            Rule(
                "Housing",
                listOf(
                    "חברת החשמל", "חשמל", "מקורות", "מי אביבים", "תאגיד המים", "ארנונה",
                    "עיריית", "מועצה מקומית", "בזק", "הוט", "hot", "yes ", "פרטנר", "סלקום",
                    "גולן טלקום", "רמי לוי תקשורת", "019", "012", "סלולר", "אינטרנט",
                    "ועד בית", "שכירות", "שכר דירה", "אמישראגז", "פזגז", "סופרגז", "גז ",
                    "ביטוח דירה", "electric", "water", "internet", "rent"
                )
            ),

            // פנאי ובידור
            Rule(
                "Entertainment",
                listOf(
                    "נטפליקס", "netflix", "spotify", "ספוטיפיי", "disney", "דיסני",
                    "יס פלאנט", "סינמה", "cinema", "רב חן", "לב סינמה", "תיאטרון", "הופעה",
                    "חדר כושר", "הולמס פלייס", "גו אקטיב", "אנרג'י", "icon", "apple music",
                    "youtube", "playstation", "steam", "xbox", "nintendo", "gym", "spotify"
                )
            ),

            // קניות
            Rule(
                "shopping",
                listOf(
                    "זארה", "zara", "קסטרו", "castro", "פוקס", "fox ", "גולף", "golf",
                    "h m", "hm ", "renuar", "רנואר", "אמריקן איגל", "טרמינל איקס", "terminalx",
                    "איקאה", "ikea", "הום סנטר", "ace", "אייס", "אמזון", "amazon", "עלי אקספרס",
                    "aliexpress", "ebay", "shein", "asos", "next", "אופיס דיפו", "סטימצקי",
                    "צומת ספרים", "כלי בית", "מחסני חשמל", "ksp", "באג", "bug", "אלקטרו"
                )
            ),

            // הכנסות מוכרות
            Rule("salary", listOf("משכורת", "שכר עבודה", "מש. עבודה", "salary", "payroll")),
            Rule("investment_income", listOf("דיבידנד", "ריבית זכות", "dividend", "interest")),
            Rule("rent_income", listOf("שכר דירה מ", "דמי שכירות", "rental income"))
        )
    }
}
