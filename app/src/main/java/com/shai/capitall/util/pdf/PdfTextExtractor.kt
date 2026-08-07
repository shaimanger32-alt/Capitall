package com.shai.capitall.util.pdf

import com.shai.capitall.util.csv.ImportError
import com.shai.capitall.util.csv.ImportException
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * מוציא מ-PDF את מקטעי הטקסט יחד עם מיקומיהם, לשימוש [PdfStatementParser].
 *
 * זו השכבה היחידה שתלויה בספריית ה-PDF; כל שחזור הטבלה הוא לוגיקה טהורה ונבדקת
 * שמקבלת [PdfTextSpan] בלבד. כך החלפת הספרייה בעתיד לא נוגעת בלוגיקת הפענוח.
 */
object PdfTextExtractor {

    /**
     * מרווח אנכי שמתווסף לכל עמוד, כדי שקואורדינטות ה-Y יגדלו לאורך המסמך כולו.
     * בלעדיו שורה מעמוד 2 (y=140) הייתה מתמזגת עם הכותרת של עמוד 1 (y=136).
     */
    private const val PAGE_STRIDE = 10_000f

    fun extract(bytes: ByteArray): List<PdfTextSpan> {
        val document = try {
            PDDocument.load(bytes)
        } catch (e: InvalidPasswordException) {
            throw ImportException(ImportError.PDF_LOCKED)
        } catch (e: Exception) {
            throw ImportException(ImportError.PDF_UNREADABLE)
        }

        return document.use { doc ->
            val spans = mutableListOf<PdfTextSpan>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: List<TextPosition>) {
                    if (text.isBlank() || textPositions.isEmpty()) return
                    val offset = (currentPageNo - 1) * PAGE_STRIDE
                    spans.add(
                        PdfTextSpan(
                            text = text,
                            x0 = textPositions.minOf { it.xDirAdj },
                            x1 = textPositions.maxOf { it.xDirAdj + it.widthDirAdj },
                            y = textPositions.first().yDirAdj + offset
                        )
                    )
                }
            }
            // sortByPosition שומר על סדר קריאה יציב במסמכים דו-כיווניים
            stripper.sortByPosition = true
            stripper.getText(doc)
            spans
        }
    }
}
