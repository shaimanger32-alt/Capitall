package com.shai.capitall.ui.selectcategory

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.data.model.CategoryDefinition
import com.shai.capitall.data.model.CategoryGroup
import com.shai.capitall.databinding.ActivitySelectCategoryBinding
import com.shai.capitall.databinding.ItemCategoryGroupBinding
import com.shai.capitall.databinding.ItemCategoryLeafBinding
import com.shai.capitall.ui.addasset.AddAssetActivity
import com.shai.capitall.util.CategoryCatalog

/**
 * מסך בחירת קטגוריה (עיצוב בהיר, אקורדיון): קבוצות-על צבעוניות הנפתחות לתת-סוגים.
 * בחירת תת-סוג פותחת את טופס "הוסף נכס" עם הקטגוריה שנבחרה מוזנת מראש.
 */
class SelectCategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectCategoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCancel.setOnClickListener { finish() }

        binding.rvGroups.layoutManager = LinearLayoutManager(this)
        binding.rvGroups.adapter = CategoryPickerAdapter(
            groups = CategoryCatalog.groupedForEntry(),
            onLeafClick = ::openForm
        )
    }

    private fun openForm(def: CategoryDefinition) {
        startActivity(
            Intent(this, AddAssetActivity::class.java)
                .putExtra(AddAssetActivity.EXTRA_CATEGORY_KEY, def.key)
        )
        finish()
    }
}

/**
 * אדפטר אקורדיון: מחזיק רשימה שטוחה של שורות (כותרות קבוצה + עלים גלויים)
 * ומרחיב/מכווץ קבוצה בלחיצה על הכותרת.
 */
private class CategoryPickerAdapter(
    private val groups: List<Pair<CategoryGroup, List<CategoryDefinition>>>,
    private val onLeafClick: (CategoryDefinition) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data class Header(val group: CategoryGroup, val expanded: Boolean) : Row
        data class Leaf(val def: CategoryDefinition, val group: CategoryGroup) : Row
    }

    // הקבוצה הראשונה פתוחה כברירת מחדל
    private val expanded: MutableSet<CategoryGroup> =
        groups.firstOrNull()?.let { linkedSetOf(it.first) } ?: linkedSetOf()
    private var rows: List<Row> = buildRows()

    private fun buildRows(): List<Row> = buildList {
        for ((group, defs) in groups) {
            val isOpen = group in expanded
            add(Row.Header(group, isOpen))
            if (isOpen) defs.forEach { add(Row.Leaf(it, group)) }
        }
    }

    private fun toggle(group: CategoryGroup) {
        if (!expanded.remove(group)) expanded.add(group)
        rows = buildRows()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_LEAF

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemCategoryGroupBinding.inflate(inflater, parent, false))
        } else {
            LeafVH(ItemCategoryLeafBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).bind(row)
            is Row.Leaf -> (holder as LeafVH).bind(row)
        }
    }

    private inner class HeaderVH(private val b: ItemCategoryGroupBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Header) {
            val color = Color.parseColor(row.group.colorHex)
            b.tvGroupName.setText(row.group.labelRes)
            b.tvGroupName.setTextColor(color)
            b.groupRow.background.mutate().setTint(ColorUtils.setAlphaComponent(color, SOFT_BG_ALPHA))
            b.ivGroupIcon.setImageResource(row.group.iconRes)
            b.ivGroupIcon.setColorFilter(color)
            (b.ivGroupIcon.parent as android.view.View).background.mutate().setTint(ColorUtils.setAlphaComponent(color, ICON_BG_ALPHA))
            b.ivChevron.setColorFilter(color)
            b.ivChevron.rotation = if (row.expanded) 270f else 90f
            b.groupRow.setOnClickListener { toggle(row.group) }
        }
    }

    private inner class LeafVH(private val b: ItemCategoryLeafBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Leaf) {
            val color = Color.parseColor(row.group.colorHex)
            b.tvLeafName.setText(row.def.labelRes)
            // אייקון ייעודי לקטגוריה (ולא אייקון הקבוצה) — כל שורה מזוהה מיד
            b.ivLeafIcon.setImageResource(row.def.iconRes)
            b.ivLeafIcon.setColorFilter(color)
            (b.ivLeafIcon.parent as android.view.View).background.mutate().setTint(ColorUtils.setAlphaComponent(color, ICON_BG_ALPHA))
            b.leafRow.setOnClickListener { onLeafClick(row.def) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LEAF = 1
        const val SOFT_BG_ALPHA = 28   // רקע רך לכותרת הקבוצה
        const val ICON_BG_ALPHA = 40   // רקע ריבוע האייקון
    }
}
