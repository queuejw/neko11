/*
 * Copyright (C) 2020 The Android Open Source Project
 * Copyright (C) 2023 Dmitry Frolkov <dimon6018t@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.dimon6018.neko11.ui.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.dimon6018.neko11.NekoApplication
import ru.dimon6018.neko11.NekoGeneralActivity.Companion.showSnackBar
import ru.dimon6018.neko11.R
import ru.dimon6018.neko11.controls.CatControlsFragment
import ru.dimon6018.neko11.ui.activities.NekoSettingsActivity
import ru.dimon6018.neko11.ui.activities.minigames.NekoMinigameM
import ru.dimon6018.neko11.workers.Cat
import ru.dimon6018.neko11.workers.NekoWorker
import ru.dimon6018.neko11.workers.PrefState
import ru.dimon6018.neko11.workers.PrefState.PrefsListener
import ru.dimon6018.neko11.workers.skins.SkinItems
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Random

class NekoLandFragment : Fragment(), PrefsListener {
    private var mPrefs: PrefState? = null
    private var mAdapter: CatAdapter? = null
    private var mPendingShareCat: Cat? = null
    private var numCats = 0
    private var numCatsP = 0
    private var nekoprefs: SharedPreferences? = null
    private var recyclerView: RecyclerView? = null
    private var loadHolder: LinearLayout? = null
    private var counter: MaterialTextView? = null
    private var bottomsheet: BottomSheetDialog? = null
    private var skinsSheet: BottomSheetDialog? = null
    private var coloredText: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        mPrefs = PrefState(requireContext())
        nekoprefs = requireContext().getSharedPreferences(NekoSettingsActivity.SETTINGS, Context.MODE_PRIVATE)
        super.onCreate(savedInstanceState)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.neko_activity_content, container, false)
        coloredText = nekoprefs!!.getBoolean("coloredText", false)
        recyclerView = view.findViewById(R.id.holder)
        counter = view.findViewById(R.id.catCounter)
        if (coloredText!!) {
            counter?.setTextColor(NekoApplication.getTextColor(requireContext()))
        }
        recyclerView!!.setItemAnimator(null)
        loadHolder = view.findViewById(R.id.loadHolderView)
        mPrefs?.setListener(this)
        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        iconSize = requireContext().resources.getDimensionPixelSize(R.dimen.neko_display_size)
        bottomsheet = BottomSheetDialog(requireContext())
        skinsSheet = BottomSheetDialog(requireContext())
        CoroutineScope(Dispatchers.Default).launch {
            mAdapter = CatAdapter()
            if (nekoprefs!!.getBoolean("skinsConfigured", false)) {
                nekoprefs!!.edit().putBoolean("skinsConfigured", true).apply()
                mPrefs?.let {
                    it.setupHats()
                    it.setupSuits()
                }
            }
            requireActivity().runOnUiThread {
                recyclerView!!.setAdapter(mAdapter)
            }
        }
    }
    override fun onDestroy() {
        mPrefs?.setListener(null)
        super.onDestroy()
    }
    private fun updateCats(): Int {
        lifecycleScope.launch(Dispatchers.Default) {
            val list: MutableList<Cat> = mPrefs!!.cats.toMutableList()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                //See https://github.com/chris-blay/AndroidNougatEasterEgg/blob/master/workspace/src/com/covertbagel/neko/NekoLand.java
                when (mPrefs!!.sortState) {
                    2 -> list.sortWith { cat: Cat, cat2: Cat -> cat.name!!.compareTo(cat2.name!!) }
                    1 -> {
                        val hsv = FloatArray(3)
                        list.sortWith { cat: Cat, cat2: Cat ->
                            Color.colorToHSV(cat.bodyColor, hsv)
                            val bodyH1 = hsv[0]
                            Color.colorToHSV(cat2.bodyColor, hsv)
                            val bodyH2 = hsv[0]
                            bodyH1.compareTo(bodyH2)
                        }
                    }
                    0 -> {}
                }
            }
            withContext(Dispatchers.Main) {
                numCatsP = list.size
                mAdapter?.setCats(list)
                updateLM()
                updateCounter(numCatsP)
            }
        }
        return numCatsP
    }

    private fun updateLM() {
        recyclerView?.setLayoutManager(GridLayoutManager(context, mPrefs!!.catsInLineLimit))
    }

    private fun updateCounter(catsNum: Int) {
        val editor = nekoprefs!!.edit()
        editor.putInt("num", catsNum)
        editor.apply()
        loadHolder?.visibility = View.GONE
        counter!!.text = getString(R.string.cat_counter, catsNum)
    }
    private fun onCatClick(cat: Cat) {
        val context = requireActivity()
        val textLayout: TextInputLayout?
        val catEditor: EditText?
        val catImage: ImageView?
        val status: MaterialTextView?
        val age: MaterialTextView?
        val mood: MaterialTextView?
        val actionsLimit: MaterialTextView?
        val wash: MaterialCardView?
        val caress: MaterialCardView?
        val touch: MaterialCardView?
        val skins: MaterialCardView?
        var catMood: Int = mPrefs!!.getMoodPref(cat)
        if (bottomsheet == null) {
            bottomsheet = BottomSheetDialog(context)
        }
        bottomsheet!!.setContentView(R.layout.neko_cat_bottomsheet)
        bottomsheet!!.dismissWithAnimation = true
        val bottomSheetInternal: View? = bottomsheet!!.findViewById(com.google.android.material.R.id.design_bottom_sheet)
        BottomSheetBehavior.from(bottomSheetInternal!!).peekHeight = resources.getDimensionPixelSize(R.dimen.bottomsheet)
        BottomSheetBehavior.from(bottomSheetInternal).state = BottomSheetBehavior.STATE_EXPANDED
        val icon: Drawable = cat.createIconBitmap(iconSize!!, iconSize!!, mPrefs!!.getIconBackground()).toDrawable(resources)
        textLayout = bottomSheetInternal.findViewById(R.id.catNameField)
        catEditor = bottomSheetInternal.findViewById(R.id.catEditName)
        catImage = bottomSheetInternal.findViewById(R.id.cat_icon)
        status = bottomSheetInternal.findViewById(R.id.status_title)
        age = bottomSheetInternal.findViewById(R.id.cat_age)
        mood = bottomSheetInternal.findViewById(R.id.mood_title)
        actionsLimit = bottomSheetInternal.findViewById(R.id.actionsLimitTip)
        wash = bottomSheetInternal.findViewById(R.id.wash_cat_sheet)
        caress = bottomSheetInternal.findViewById(R.id.caress_cat_sheet)
        touch = bottomSheetInternal.findViewById(R.id.touch_cat_sheet)
        skins = bottomSheetInternal.findViewById(R.id.skins_sheet)
        try {
            mood!!.text = context.getString(R.string.mood, NekoApplication.getCatMood(context, cat))
            age!!.text = getString(R.string.cat_age, cat.age)
            status!!.text = getString(R.string.cat_status_string, cat.status)
            catEditor!!.setText(cat.name)
        } catch (_: ClassCastException) {
            mood!!.text = getString(R.string.error)
            mPrefs!!.removeMood(cat)
            mPrefs!!.setMood(cat, 3)
        }
        catImage?.setImageDrawable(icon)
        if (coloredText!!) {
            age!!.setTextColor(NekoApplication.getTextColor(context))
            mood.setTextColor(NekoApplication.getTextColor(context))
            catEditor!!.setTextColor(NekoApplication.getTextColor(context))
            status!!.setTextColor(NekoApplication.getTextColor(context))
        }
        updateCatActions(cat, wash!!, caress!!, touch!!, actionsLimit!!)
        bottomSheetInternal.findViewById<View>(R.id.delete_sheet).setOnClickListener {
            bottomsheet!!.dismiss()
            showCatRemoveDialog(cat, context)
        }
        textLayout!!.setEndIconOnClickListener {
            bottomsheet!!.dismiss()
            MaterialAlertDialogBuilder(context)
                    .setIcon(icon)
                    .setTitle(R.string.rename_title)
                    .setMessage(R.string.name_changed)
                    .setNegativeButton(android.R.string.ok, null)
                    .show()
            cat.name = catEditor!!.text.toString().trim { it <= ' ' }
            bottomsheet = null
            mPrefs!!.addCat(cat)
        }
        catImage!!.setOnClickListener {
            bottomsheet!!.dismiss()
            showCatFull(cat)
        }
        bottomSheetInternal.findViewById<View>(R.id.save_sheet).setOnClickListener {
            bottomsheet!!.dismiss()
            checkPerms(cat, context)
        }
        bottomSheetInternal.findViewById<View>(R.id.games_sheet).setOnClickListener {
            NekoMinigameM.setCat(cat)
            startActivity(Intent(requireActivity(), NekoMinigameM::class.java))
        }
        bottomSheetInternal.findViewById<View>(R.id.boosters_sheet).setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(context)
            val v = this.getLayoutInflater().inflate(R.layout.cat_boosters_dialog, null)
            dialog.setView(v)
            val counter0 = v.findViewById<MaterialTextView>(R.id.counter0)
            val counter1 = v.findViewById<MaterialTextView>(R.id.counter1)
            val item0 = v.findViewById<MaterialCardView>(R.id.moodBooster)
            val item1 = v.findViewById<MaterialCardView>(R.id.luckyBooster)
            counter0.text = getString(R.string.booster_items, mPrefs!!.moodBoosters)
            counter1.text = getString(R.string.booster_items, mPrefs!!.luckyBoosters)
            item0.isEnabled = mPrefs!!.moodBoosters != 0
            item1.isEnabled = mPrefs!!.luckyBoosters != 0
            dialog.setCancelable(true)
            dialog.setNegativeButton(android.R.string.cancel, null)
            val alertd = dialog.create()
            updateCatActions(cat, wash, caress, touch, actionsLimit)
            item0.setOnClickListener {
                mPrefs!!.removeMoodBooster(1)
                mPrefs!!.setMood(cat, 5)
                mood.text = context.getString(R.string.mood, NekoApplication.getCatMood(context, cat))
                bottomsheet!!.dismiss()
                alertd.dismiss()
            }
            item1.setOnClickListener {
                if (!PrefState.isLuckyBoosterActive) {
                    mPrefs!!.removeLuckyBooster(1)
                    PrefState.isLuckyBoosterActive = true
                    if (NekoWorker.isWorkScheduled) {
                        NekoWorker.stopFoodWork(getContext())
                        NekoWorker.scheduleFoodWork(getContext(), CatControlsFragment.randomfood / 4)
                        PrefState.isLuckyBoosterActive = false
                    }
                    alertd.dismiss()
                } else {
                    MaterialAlertDialogBuilder(context)
                            .setIcon(icon)
                            .setTitle(R.string.ops)
                            .setMessage(R.string.booster_actived_sub)
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
            }
            alertd.show()
        }
        wash.setOnClickListener {
            mPrefs!!.setCanInteract(cat, mPrefs!!.isCanInteract(cat) - 1)
            updateCatActions(cat, wash, caress, touch, actionsLimit)
            val r = Random()
            val result = r.nextInt(5 + 1)
            if (result != 1) {
                val statusArray = context.resources.getStringArray(R.array.toy_messages)
                val a = statusArray[r.nextInt(context.resources.getStringArray(R.array.toy_messages).size)]
                catMood += 1
                mPrefs!!.setMood(cat, catMood)
                showSnackBar(a, Toast.LENGTH_SHORT, bottomSheetInternal)
                NekoWorker.notifyCat(requireContext(), cat, resources.getString(R.string.meow))
                mPrefs!!.addNCoins(26)
                mPrefs!!.setCatDirty(false, cat.seed)
            } else {
                NekoWorker.notifyCat(requireContext(), cat, resources.getString(R.string.shh))
                catMood -= 1
                mPrefs!!.setMood(cat, catMood)
                MaterialAlertDialogBuilder(context)
                        .setIcon(icon)
                        .setTitle(R.string.ops)
                        .setMessage(R.string.action1_fail)
                        .setNegativeButton(android.R.string.ok, null)
                        .show()
            }
            mood.text = context.getString(R.string.mood, NekoApplication.getCatMood(context, cat))
            refreshBottomSheetIcon(cat, catImage, mPrefs!!)
        }
        caress.setOnClickListener {
            mPrefs!!.setCanInteract(cat, mPrefs!!.isCanInteract(cat) - 1)
            updateCatActions(cat, wash, caress, touch, actionsLimit)
            val r = Random()
            val result = r.nextInt(5 + 1)
            if (result != 1) {
                val moods = arrayOf(4, 5)
                val b = moods[r.nextInt(moods.size)]
                catMood = b
                mPrefs!!.setMood(cat, b)
                showSnackBar("❤️", Toast.LENGTH_SHORT, bottomSheetInternal)
                NekoWorker.notifyCat(requireContext(), cat, resources.getString(R.string.meow))
                mPrefs!!.addNCoins(31)
            } else {
                NekoWorker.notifyCat(requireContext(), cat, resources.getString(R.string.shh))
                catMood -= 1
                mPrefs!!.setMood(cat, catMood)
                MaterialAlertDialogBuilder(context)
                        .setIcon(icon)
                        .setTitle(R.string.ops)
                        .setMessage(R.string.action2_fail)
                        .setNegativeButton(android.R.string.ok, null)
                        .show()
            }
            mood.text = context.getString(R.string.mood, NekoApplication.getCatMood(context, cat))
        }
        touch.setOnClickListener {
            mPrefs!!.setCanInteract(cat, mPrefs!!.isCanInteract(cat) - 1)
            updateCatActions(cat, wash, caress, touch, actionsLimit)
            val r = Random()
            val result = r.nextInt(5 + 1)
            if (result != 1) {
                val moods = arrayOf(4, 5)
                val b = moods[r.nextInt(moods.size)]
                catMood = b
                mPrefs!!.setMood(cat, b)
                val statusArray = context.resources.getStringArray(R.array.toy_messages)
                val a = statusArray[r.nextInt(context.resources.getStringArray(R.array.toy_messages).size)]
                NekoWorker.notifyCat(requireContext(), cat, a)
                mPrefs!!.addNCoins(16)
            } else {
                catMood -= 1
                mPrefs!!.setMood(cat, catMood)
                NekoWorker.notifyCat(requireContext(), cat, resources.getString(R.string.shh))
                MaterialAlertDialogBuilder(context)
                        .setIcon(icon)
                        .setTitle(R.string.ops)
                        .setMessage(R.string.touch_cat_error)
                        .setNegativeButton(android.R.string.ok, null)
                        .show()
            }
            mood.text = context.getString(R.string.mood, NekoApplication.getCatMood(context, cat))
        }
        skins!!.setOnClickListener {
            skinsBottomSheet(context, cat)
            bottomsheet!!.dismiss()
        }
        bottomsheet!!.show()
    }
    private fun skinsBottomSheet(context: Context, cat: Cat) {
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        skinsSheet!!.setContentView(R.layout.cat_skins_dialog)
        skinsSheet!!.dismissWithAnimation = true
        val bottomSheetInternal = skinsSheet!!.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        BottomSheetBehavior.from(bottomSheetInternal!!).peekHeight = context.resources.getDimensionPixelSize(R.dimen.bottomsheet_skins)
        getSuits(context)
        getHats(context)
        val catPreview: ImageView = bottomSheetInternal.findViewById(R.id.catSkinPreview)
        val coins: MaterialTextView = bottomSheetInternal.findViewById(R.id.skinCoinsText)
        val recyclerViewSuit: RecyclerView = bottomSheetInternal.findViewById(R.id.recyclerViewSuits)
        val recyclerViewHats: RecyclerView = bottomSheetInternal.findViewById(R.id.recyclerViewHats)
        val suitAdapter = SkinSuitsAdapter(suitList!!, requireContext(), cat, catPreview, coins)
        val hatAdapter = SkinHatsAdapter(hatList!!, requireContext(), cat, catPreview, coins)
        catPreview.setImageBitmap(cat.createIconBitmap(iconSize!!, iconSize!!, mPrefs!!.getIconBackground()))
        coins.text = getString(R.string.coins, mPrefs!!.nCoins)
        recyclerViewSuit.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewSuit.adapter = suitAdapter
        recyclerViewSuit.addItemDecoration(MarginItemDecoration(4))
        recyclerViewHats.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewHats.adapter = hatAdapter
        recyclerViewHats.addItemDecoration(MarginItemDecoration(4))
        skinsSheet!!.show()
    }

    @SuppressLint("DiscouragedApi")
    private fun getSuits(context: Context) {
        suitList = ArrayList()
        var count = 0
        while (count != SUITS) {
            val suit = SkinItems()
            if (count == 0) {
                suit.text = getString(R.string.nothing)
                count += 1
                suitList!!.add(suit)
                continue
            } else {
                suit.text = ""
            }
            try {
                val id = context.resources.getIdentifier("suit_$count", "mipmap", context.packageName)
                suit.image = AppCompatResources.getDrawable(context, id)
            } catch (e: Exception) {
                Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
            }
            try {
                val id = context.resources.getIdentifier("suit_$count", "string", context.packageName)
                suit.text = getString(id)
            } catch (e: Exception) {
                Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
            }
            suitList!!.add(suit)
            count += 1
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getHats(context: Context) {
        hatList = ArrayList()
        var count = 0
        while (count != HATS) {
            val suit = SkinItems()
            if (count == 0) {
                suit.text = getString(R.string.nothing)
                count += 1
                hatList!!.add(suit)
                continue
            } else {
                suit.text = ""
            }
            try {
                val id = context.resources.getIdentifier("hat_$count", "mipmap", context.packageName)
                suit.image = AppCompatResources.getDrawable(context, id)
            } catch (e: Exception) {
                Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
            }
            try {
                val id = context.resources.getIdentifier("hat_$count", "string", context.packageName)
                suit.text = getString(id)
            } catch (e: Exception) {
                Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
            }
            hatList!!.add(suit)
            count += 1
        }
    }

    private fun updateCatActions(cat: Cat, wash: View, caress: View, touch: View, actionsLimit: View) {
        if (mPrefs!!.isCanInteract(cat) <= 0) {
            wash.isEnabled = false
            wash.alpha = 0.5f
            caress.isEnabled = false
            caress.alpha = 0.5f
            touch.isEnabled = false
            touch.alpha = 0.5f
            actionsLimit.visibility = View.VISIBLE
        }
    }

    private fun checkPerms(cat: Cat, activity: Activity) {

        if (ContextCompat.checkSelfPermission(activity, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activity, permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ActivityCompat.requestPermissions(activity, arrayOf(permission.WRITE_EXTERNAL_STORAGE, permission.READ_EXTERNAL_STORAGE, permission.MANAGE_EXTERNAL_STORAGE), 1)
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(permission.WRITE_EXTERNAL_STORAGE, permission.READ_EXTERNAL_STORAGE), 1)
            }
        }
        shareCat(requireActivity(), cat, true)
    }
    private fun showCatRemoveDialog(cat: Cat, context: Context) {
        val size = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        MaterialAlertDialogBuilder(context)
                .setIcon(cat.createIconBitmap(size, size, mPrefs!!.getIconBackground()).toDrawable(resources))
                .setTitle(R.string.delete_cat_title)
                .setMessage(R.string.delete_cat_message)
                .setPositiveButton(R.string.delete_cat_title) { _: DialogInterface?, _: Int -> onCatRemove(cat) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun onCatRemove(cat: Cat) {
        val random = Random(cat.seed)
        mPrefs!!.removeCat(cat)
        mPrefs!!.addNCoins(random.nextInt(50 - 10) + 5)
    }

    private fun showCatFull(cat: Cat) {
        val context: Context = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ContextThemeWrapper(context, requireActivity().theme) else requireActivity()
        val view = LayoutInflater.from(context).inflate(R.layout.cat_fullscreen_view, null)
        val ico = view.findViewById<ImageView>(R.id.cat_ico)
        ico.setImageBitmap(cat.createIconBitmap(EXPORT_BITMAP_SIZE, EXPORT_BITMAP_SIZE, 0))
        MaterialAlertDialogBuilder(context)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    override fun onPrefsChanged() {
        updateCats()
    }

    private inner class CatAdapter : RecyclerView.Adapter<CatHolder>() {

        private lateinit var mCats: MutableList<Cat>
        private lateinit var mCatsReserved: MutableList<Cat>
        fun setCats(cats: MutableList<Cat>) {
            mCats = cats
            mCatsReserved = cats
            val diffUtilCallback = DiffUtilCallback(mCatsReserved, mCats)
            val diffResult = DiffUtil.calculateDiff(diffUtilCallback, false)
            diffResult.dispatchUpdatesTo(mAdapter!!)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatHolder {
            return CatHolder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.cat_view, parent, false))
        }

        override fun onBindViewHolder(holder: CatHolder, position: Int) {
            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                holder.imageView.setImageBitmap(mCats[position].createIconBitmap(mPrefs!!.catIconSize, mPrefs!!.catIconSize, mPrefs!!.getIconBackground()))
            } else {
                holder.imageView.setImageIcon(mCats[position].createIcon(mPrefs!!.catIconSize, mPrefs!!.catIconSize, mPrefs!!.getIconBackground()))
            }
            holder.textView.text = mCats[position].name
            if (coloredText!!) {
                holder.textView.setTextColor(NekoApplication.getTextColor(context!!))
            }
            holder.itemView.setOnClickListener { onCatClick(mCats[position]) }
        }

        override fun getItemCount(): Int {
            return mCats.size
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == STORAGE_PERM_REQUEST) {
            if (mPendingShareCat != null) {
                shareCat(requireActivity(),mPendingShareCat!!, true)
                mPendingShareCat = null
            }
        }
    }

    class DiffUtilCallback(private val oldCatsList: MutableList<Cat>, private val newCatsList: MutableList<Cat>) : DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldCatsList.size
        }

        override fun getNewListSize(): Int {
            return newCatsList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldCats = oldCatsList[oldItemPosition]
            val newCats = newCatsList[newItemPosition]
            return oldCats.seed == newCats.seed
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldCats = oldCatsList[oldItemPosition]
            val newCats = newCatsList[newItemPosition]
            return oldCats.name == newCats.name
        }
    }

    private class CatHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.icon)
        val textView: TextView = itemView.findViewById(R.id.title)
    }

    override fun onResume() {
        super.onResume()
        mPrefs?.apply {
            setListener(this@NekoLandFragment)
            numCats = updateCats()
            if(isDialogEnabled()) {
                setRunDialog(false)
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(R.drawable.ic_game)
                    .setMessage(R.string.cat_run_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    override fun onPause() {
        mPrefs!!.setListener(null)
        if (bottomsheet?.isShowing == true) {
            bottomsheet?.dismiss()
        }
        super.onPause()
    }

    companion object {
        @JvmField
        var CHAN_ID = "NEKO11"
        const val STORAGE_PERM_REQUEST = 123
        const val EXPORT_BITMAP_SIZE = 700

        const val SUITS = 12
        const val HATS = 12

        private var iconSize: Int? = null
        private var hatPricesArray: IntArray = intArrayOf(0, 1, 100, 250, 150, 300, 200, 666, 200, 250, 1000, 50, 250)
        private var suitPricesArray: IntArray = intArrayOf(0, 1, 150, 200, 300, 500, 300, 250, 200, 250, 300, 400)
        private var suitList: ArrayList<SkinItems>? = null
        private var hatList: ArrayList<SkinItems>? = null

        fun refreshBottomSheetIcon(cat: Cat, catPreview: ImageView, mPrefs: PrefState) {
            val newCat = mPrefs.catBySeed(cat.seed)
            CoroutineScope(Dispatchers.Default).launch {
                val icon = newCat?.createIconBitmap(iconSize!!, iconSize!!, mPrefs.getIconBackground())
                withContext(Dispatchers.Main) {
                    catPreview.setImageBitmap(icon)
                }
                cancel()
            }
        }
        fun shareCat(activity: Activity, cat: Cat, iShouldShowDialog: Boolean) {
            val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Cats")
            if (!dir.exists() && !dir.mkdirs()) {
                return
            }
            val png = File(dir, cat.name!!.replace("[/ #:]+".toRegex(), "_") + ".png")
            val bitmap = cat.createIconBitmap(EXPORT_BITMAP_SIZE, EXPORT_BITMAP_SIZE, 0)
            try {
                val os: OutputStream = FileOutputStream(png)
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, os)
                os.close()
                MediaScannerConnection.scanFile(
                        activity, arrayOf(png.toString()), arrayOf("image/png"),
                        null)
                if (iShouldShowDialog) {
                    MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.app_name_neko)
                            .setIcon(R.drawable.ic_success)
                            .setMessage(activity.getString(R.string.picture_saved_successful, cat.name))
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                if (iShouldShowDialog) {
                    MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.app_name_neko)
                            .setIcon(R.drawable.ic_warning)
                            .setMessage(R.string.permission_denied)
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
            }
        }
    }

    class SkinSuitsAdapter(private val dataSet: ArrayList<SkinItems>, val context: Context, private val cat: Cat, private val catPreview: ImageView, private val coins: MaterialTextView) :
            RecyclerView.Adapter<SkinSuitsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.skin_text)
            val price: TextView = view.findViewById(R.id.price_text)
            val imageView: ImageView = view.findViewById(R.id.skin_image)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.skin_layout, viewGroup, false)

            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val items = dataSet[position]
            val prefs = PrefState(context)
            viewHolder.apply {
                textView.text = items.text
                imageView.setImageDrawable(items.image)
                val priceTxt = suitPricesArray[position].toString() + " NCoin"
                if (priceTxt == "0 NCoin") {
                    price.text = ""
                } else {
                    price.text = priceTxt
                }
                if (prefs.getPrefsAccess().getBoolean("is_suit_purchased_$position", false)) {
                    price.text = ""
                }
                itemView.setOnClickListener {
                    if (position != 0) {
                        if (prefs.getPrefsAccess()
                                .getBoolean("is_suit_purchased_$position", false)
                        ) {
                            prefs.setCatSuit(position, cat.seed)
                            refreshBottomSheetIcon(cat, catPreview, prefs)
                        } else {
                            MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.skins)
                                .setMessage(
                                    context.getString(
                                        R.string.skins_purchase_dialog,
                                        items.text,
                                        hatPricesArray[position]
                                    )
                                )
                                .setIcon(R.drawable.ic_skins)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                                    purchase(prefs, position, viewHolder)
                                }.show()
                        }
                    } else {
                        prefs.setCatSuit(position, cat.seed)
                        refreshBottomSheetIcon(cat, catPreview, prefs)
                    }
                }
            }
        }
        private fun purchase(prefs: PrefState, position: Int, viewHolder: ViewHolder) {
            if (prefs.nCoins < suitPricesArray[position]) {
                MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.skins)
                        .setMessage(R.string.ncoins_err)
                        .setIcon(R.drawable.ic_skins)
                        .setNegativeButton(android.R.string.ok, null
                        ).show()
            } else {
                prefs.apply {
                    removeNCoins(suitPricesArray[position])
                    getEditor().putBoolean("is_suit_purchased_$position", true).apply()
                    setCatSuit(position, cat.seed)
                }
                coins.text = context.getString(R.string.coins, prefs.nCoins)
                viewHolder.price.text = ""
                val newCat = prefs.catBySeed(cat.seed)
                CoroutineScope(Dispatchers.Default).launch {
                    val icon = newCat?.createIconBitmap(iconSize!!, iconSize!!, prefs.getIconBackground())
                    withContext(Dispatchers.Main) {
                        catPreview.setImageBitmap(icon)
                    }
                    cancel()
                }
            }
        }
        override fun getItemCount() = dataSet.size
    }

    class SkinHatsAdapter(private val dataSet: ArrayList<SkinItems>, val context: Context, private val cat: Cat, private val catPreview: ImageView, private val coins: MaterialTextView) :
            RecyclerView.Adapter<SkinHatsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.skin_text)
            val price: TextView = view.findViewById(R.id.price_text)
            val imageView: ImageView = view.findViewById(R.id.skin_image)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.skin_layout, viewGroup, false)

            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val items = dataSet[position]
            val prefs = PrefState(context)
            viewHolder.textView.text = items.text
            viewHolder.imageView.setImageDrawable(items.image)
            val price = hatPricesArray[position].toString() + " NCoin"
            if (price == "0 NCoin") {
                viewHolder.price.text = ""
            } else {
                viewHolder.price.text = price
            }
            if (prefs.getPrefsAccess().getBoolean("is_hat_purchased_$position", false)) {
                viewHolder.price.text = ""
            }
            viewHolder.itemView.setOnClickListener {
                if (position != 0) {
                    if (prefs.getPrefsAccess().getBoolean("is_hat_purchased_$position", false)) {
                        prefs.setCatHat(position, cat.seed)
                        refreshBottomSheetIcon(cat, catPreview, prefs)
                    } else {
                        MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.skins)
                                .setMessage(context.getString(R.string.skins_purchase_dialog, items.text, hatPricesArray[position]))
                                .setIcon(R.drawable.ic_skins)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.yes) {  _: DialogInterface?, _: Int ->
                                    purchase(prefs, position, viewHolder)
                                }.show()
                    }
                } else {
                    prefs.setCatHat(position, cat.seed)
                    refreshBottomSheetIcon(cat, catPreview, prefs)
                }
            }
        }
        private fun purchase(prefs: PrefState, position: Int, viewHolder: ViewHolder) {
            if (prefs.nCoins < hatPricesArray[position]) {
                MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.skins)
                        .setMessage(R.string.ncoins_err)
                        .setIcon(R.drawable.ic_skins)
                        .setNegativeButton(android.R.string.ok, null
                        ).show()
            } else {
                prefs.removeNCoins(hatPricesArray[position])
                prefs.getEditor().putBoolean("is_hat_purchased_$position", true).apply()
                prefs.setCatHat(position, cat.seed)
                coins.text = context.getString(R.string.coins, prefs.nCoins)
                viewHolder.price.text = ""
                val newCat = prefs.catBySeed(cat.seed)
                CoroutineScope(Dispatchers.Default).launch {
                    val icon = newCat?.createIconBitmap(iconSize!!, iconSize!!, prefs.getIconBackground())
                    withContext(Dispatchers.Main) {
                        catPreview.setImageBitmap(icon)
                    }
                    cancel()
                }
            }
        }
        override fun getItemCount() = dataSet.size
    }
    class MarginItemDecoration(private val spaceSize: Int) : ItemDecoration() {
        override fun getItemOffsets(
                outRect: Rect, view: View,
                parent: RecyclerView,
                state: RecyclerView.State
        ) {
            with(outRect) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    top = spaceSize
                }
                left = spaceSize
                right = spaceSize
                bottom = spaceSize
            }
        }
    }
}
