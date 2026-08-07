package com.shai.capitall.ui.spaces

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.shai.capitall.R
import com.shai.capitall.data.model.Space
import com.shai.capitall.databinding.ActivitySpacesBinding
import com.shai.capitall.util.bindEmptyState
import com.shai.capitall.util.hapticConfirm
import com.shai.capitall.util.hapticTap

/**
 * רשימת התיקים המשותפים של המשתמש, ויצירה/הצטרפות.
 * התיק האישי אינו מופיע כאן — הוא הדשבורד עצמו, ואינו ניתן לשיתוף.
 */
class SpacesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpacesBinding
    private lateinit var viewModel: SpacesViewModel

    private val adapter by lazy {
        SpacesAdapter(
            onClick = { space -> openSpace(space) },
            onCodeClick = { space -> InviteCodeDialog.show(this, space.name, space.inviteCode) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpacesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[SpacesViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvSpaces.layoutManager = LinearLayoutManager(this)
        binding.rvSpaces.adapter = adapter

        binding.btnCreateSpace.setOnClickListener {
            it.hapticTap()
            promptCreate()
        }
        binding.btnJoinSpace.setOnClickListener {
            it.hapticTap()
            promptJoin()
        }

        binding.emptyState.root.bindEmptyState(
            titleRes = R.string.spaces_empty_title,
            bodyRes = R.string.spaces_empty_body,
            actionRes = R.string.spaces_create,
            onAction = { promptCreate() }
        )

        viewModel.state.observe(this) { render(it) }
        viewModel.createdSpace.observe(this) { space ->
            if (space == null) return@observe
            InviteCodeDialog.show(this, space.name, space.inviteCode)
            viewModel.consumeCreatedSpace()
        }
        viewModel.busy.observe(this) { busy ->
            binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        }
        viewModel.message.observe(this) { message ->
            if (message == null) return@observe
            val text = if (message.argument != null) {
                getString(message.messageRes, message.argument)
            } else {
                getString(message.messageRes)
            }
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    private fun render(state: SpacesUiState) {
        val hasSpaces = state is SpacesUiState.Ready
        binding.rvSpaces.visibility = if (hasSpaces) View.VISIBLE else View.GONE
        binding.tvSpacesHeader.visibility = if (hasSpaces) View.VISIBLE else View.GONE
        binding.emptyState.root.visibility =
            if (state is SpacesUiState.Empty) View.VISIBLE else View.GONE

        when (state) {
            is SpacesUiState.Ready -> adapter.submitList(state.spaces)
            is SpacesUiState.Error -> Toast.makeText(this, state.messageRes, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    private fun openSpace(space: Space) {
        startActivity(
            Intent(this, SpaceDetailActivity::class.java)
                .putExtra(SpaceDetailActivity.EXTRA_SPACE_ID, space.id)
        )
    }

    // ---------- דיאלוגים ----------

    /** שדה טקסט בתוך דיאלוג, עם שוליים שתואמים ל-AlertDialog. */
    private fun dialogInput(hint: String, allCaps: Boolean = false): Pair<FrameLayout, EditText> {
        val input = EditText(this).apply {
            this.hint = hint
            setSingleLine()
            inputType = if (allCaps) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        val container = FrameLayout(this).apply {
            val pad = (22 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        return container to input
    }

    private fun promptCreate() {
        val (container, input) = dialogInput(getString(R.string.spaces_name_hint))
        AlertDialog.Builder(this)
            .setTitle(R.string.spaces_create)
            .setMessage(R.string.spaces_create_body)
            .setView(container)
            .setPositiveButton(R.string.spaces_create_confirm) { _, _ ->
                binding.root.hapticConfirm()
                viewModel.createSpace(input.text.toString())
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }

    private fun promptJoin() {
        val (container, input) = dialogInput(getString(R.string.spaces_code_hint), allCaps = true)
        AlertDialog.Builder(this)
            .setTitle(R.string.spaces_join)
            .setMessage(R.string.spaces_join_body)
            .setView(container)
            .setPositiveButton(R.string.spaces_join_confirm) { _, _ ->
                binding.root.hapticConfirm()
                viewModel.joinSpace(input.text.toString())
            }
            .setNegativeButton(R.string.portfolio_delete_cancel, null)
            .show()
    }
}
