package com.shai.capitall.ui.spaces

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.shai.capitall.R
import com.shai.capitall.databinding.DialogInviteCodeBinding

/**
 * דיאלוג קוד ההזמנה — מוצג גם מיד אחרי יצירת תיק וגם מתפריט התיק.
 *
 * שלוש דרכים להוציא את הקוד החוצה, כי לכל אחת יש רגע משלה: לחיצה על הקוד מעתיקה
 * (הכי מהיר), "שתף" פותח את בורר האפליקציות (וואטסאפ), והקוד עצמו גדול וניתן לבחירה
 * כדי שאפשר יהיה פשוט להקריא אותו בטלפון.
 */
object InviteCodeDialog {

    fun show(activity: Activity, spaceName: String, code: String) {
        val binding = DialogInviteCodeBinding.inflate(activity.layoutInflater)
        binding.tvInviteSpaceName.text = spaceName
        binding.tvInviteCode.text = code
        binding.tvInviteCode.setOnClickListener { copy(activity, spaceName, code) }

        AlertDialog.Builder(activity)
            .setTitle(R.string.space_invite_title)
            .setView(binding.root)
            .setPositiveButton(R.string.space_invite_share) { _, _ ->
                share(activity, spaceName, code)
            }
            .setNeutralButton(R.string.space_invite_copy) { _, _ ->
                copy(activity, spaceName, code)
            }
            .setNegativeButton(R.string.space_invite_close, null)
            .show()
    }

    private fun copy(context: Context, spaceName: String, code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(spaceName, code))
        Toast.makeText(context, R.string.space_invite_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * פותח את בורר השיתוף של המערכת. המשתמש בוחר בעצמו לאן ולמי — האפליקציה
     * לא שולחת דבר בכוחות עצמה.
     */
    private fun share(activity: Activity, spaceName: String, code: String) {
        val message = activity.getString(R.string.space_invite_share_text, spaceName, code)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, spaceName)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        activity.startActivity(
            Intent.createChooser(intent, activity.getString(R.string.space_invite_share))
        )
    }
}
