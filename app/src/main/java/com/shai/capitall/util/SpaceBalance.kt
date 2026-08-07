package com.shai.capitall.util

import com.shai.capitall.data.model.Transaction
import kotlin.math.abs
import kotlin.math.min

/**
 * חישוב "מי חייב למי" בתיק משותף. לוגיקה טהורה (ללא Android/Firebase) — ראה SpaceBalanceTest.
 *
 * ## המודל
 * לכל חבר מחושב **מאזן** = כמה הוציא בפועל פחות חלקו ההוגן. הפיצול שווה בין כל החברים.
 *
 * הסכומים מיוצגים כ"הוצאה נטו" (outlay): הוצאה (‎-120) נחשבת ‎+120 שיצאו מהכיס,
 * והכנסה (‎+400) נחשבת ‎-400 שנכנסו אליו. הייצוג האחיד הזה גורם לאותה נוסחה לעבוד
 * לשני הכיוונים — מי שקיבל כסף משותף לכיסו חייב לאחרים את חלקם, בדיוק כמו שמי
 * ששילם עבור כולם זכאי לקבל בחזרה.
 */
object SpaceBalance {

    /** מאזן חבר יחיד. [net] חיובי = מגיע לו כסף; שלילי = הוא חייב. */
    data class MemberBalance(
        val userId: String,
        val outlay: Double,
        val fairShare: Double
    ) {
        val net: Double get() = outlay - fairShare
    }

    /** העברה מוצעת לסגירת החוב. */
    data class Settlement(
        val fromUserId: String,
        val toUserId: String,
        val amount: Double
    )

    /** סכומים שקטנים מזה נחשבים אפס — מנטרל רעש נקודה צפה בחלוקה. */
    private const val EPSILON = 0.01

    /**
     * מאזן לכל חבר בתיק. חברים ללא עסקאות נכללים גם הם — אחרת מי שלא שילם כלום
     * פשוט לא היה מופיע, ודווקא הוא זה שחייב.
     */
    fun balances(transactions: List<Transaction>, memberIds: List<String>): List<MemberBalance> {
        if (memberIds.isEmpty()) return emptyList()

        val outlayByMember = memberIds.associateWith { 0.0 }.toMutableMap()
        for (tx in transactions) {
            val payer = tx.payerId
            // עסקה של מי שכבר אינו חבר בתיק לא נספרת לאיש, אבל כן נכנסת לסך הכולל
            if (payer in outlayByMember) {
                outlayByMember[payer] = outlayByMember.getValue(payer) - tx.amount
            }
        }

        val total = transactions.sumOf { -it.amount }
        val fairShare = total / memberIds.size

        return memberIds.map { MemberBalance(it, outlayByMember.getValue(it), fairShare) }
    }

    /**
     * העברות לסגירת המאזן, בשיטה חמדנית: בכל צעד מזווגים את החייב הגדול ביותר
     * עם הזכאי הגדול ביותר ומעבירים את הקטן מבין השניים. כך אחד מהם מתאפס בכל
     * צעד, והתוצאה היא **לכל היותר n−1 העברות** — המינימום התיאורטי למקרה הכללי.
     */
    fun settlements(balances: List<MemberBalance>): List<Settlement> {
        val debtors = balances.filter { it.net < -EPSILON }
            .map { it.userId to -it.net }
            .sortedByDescending { it.second }
            .toMutableList()
        val creditors = balances.filter { it.net > EPSILON }
            .map { it.userId to it.net }
            .sortedByDescending { it.second }
            .toMutableList()

        val transfers = mutableListOf<Settlement>()
        var d = 0
        var c = 0
        while (d < debtors.size && c < creditors.size) {
            val (debtor, owed) = debtors[d]
            val (creditor, due) = creditors[c]
            val amount = min(owed, due)

            if (amount > EPSILON) {
                transfers += Settlement(debtor, creditor, amount)
            }
            debtors[d] = debtor to (owed - amount)
            creditors[c] = creditor to (due - amount)

            if (debtors[d].second <= EPSILON) d++
            if (creditors[c].second <= EPSILON) c++
        }
        return transfers
    }

    /** סך ההוצאות בתיק (ערך חיובי), להצגה בכותרת. */
    fun totalExpenses(transactions: List<Transaction>): Double =
        transactions.filter { it.amount < 0 }.sumOf { abs(it.amount) }

    /** סך ההכנסות בתיק. */
    fun totalIncome(transactions: List<Transaction>): Double =
        transactions.filter { it.amount > 0 }.sumOf { it.amount }
}
