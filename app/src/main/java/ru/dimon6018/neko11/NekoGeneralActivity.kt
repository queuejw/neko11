/*
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
package ru.dimon6018.neko11

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.dimon6018.neko11.activation.NekoActivationActivity
import ru.dimon6018.neko11.controls.CatControlsFragment
import ru.dimon6018.neko11.controls.CatControlsFragment.Companion.showTipAgain
import ru.dimon6018.neko11.ui.activities.NekoAboutActivity
import ru.dimon6018.neko11.ui.activities.NekoAchievementsActivity
import ru.dimon6018.neko11.ui.activities.NekoSettingsActivity
import ru.dimon6018.neko11.ui.fragments.NekoLandFragment
import ru.dimon6018.neko11.workers.Cat
import ru.dimon6018.neko11.workers.NekoWorker
import ru.dimon6018.neko11.workers.PrefState
import ru.dimon6018.neko11.workers.PrefState.PrefsListener


class NekoGeneralActivity : AppCompatActivity(), PrefsListener {

    private var nekoprefs: SharedPreferences? = null
    private var mPrefs: PrefState? = null
    var navbar: BottomNavigationView? = null
    private var promo: String? = null
    private var state = 0
    private var viewPager: ViewPager2? = null
    private var pagerAdapter: FragmentStateAdapter? = null
    private var needWelcomeDialog = false
    private var cord: CoordinatorLayout? = null
    private var uimanager: UiModeManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        nekoprefs = getSharedPreferences(NekoSettingsActivity.SETTINGS, MODE_PRIVATE)
        setupState()
        if (state == 3) {
            finish()
            return
        }
        uimanager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        setupDarkMode()
        setTheme(NekoApplication.getNekoTheme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neko_activity)
        mPrefs = PrefState(this)
        viewPager = findViewById(R.id.pager)
        cord = findViewById(R.id.coordinator)
        navbar = findViewById(R.id.navigation)
        setupNavbarListener()
        mPrefs!!.setListener(this)
        if(mPrefs!!.isMusicEnabled()) {
            mPrefs!!.setMusic(false)
        }
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayUseLogoEnabled(true)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window!!.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        lifecycleScope.launch(Dispatchers.Default) {
            pagerAdapter = NekoAdapter(this@NekoGeneralActivity)
            if (mPrefs!!.backgroundPath != "") {
                try {
                    val bmp = BitmapFactory.decodeFile(mPrefs!!.backgroundPath)
                    val bmpNew = Bitmap.createBitmap(bmp, viewPager!!.x.toInt(), viewPager!!.y.toInt(), bmp.width, bmp.height, null, true)
                    withContext(Dispatchers.Main) {
                        cord?.background = bmpNew.toDrawable(resources)
                    }
                } catch (ex: Exception) {
                    cord?.apply {
                        background = null
                        showSnackBar(ex.toString(), Snackbar.LENGTH_LONG, this)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                viewPager?.apply {
                    if (!nekoprefs?.getBoolean("controlsFirst", false)!!) {
                        currentItem = 0
                        navbar?.selectedItemId = R.id.collection
                    } else {
                        viewPager?.currentItem = 1
                        navbar?.selectedItemId = R.id.controls
                    }
                    registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            super.onPageSelected(position)
                            navbar?.apply {
                                if (position == 0) {
                                    selectedItemId = R.id.collection
                                } else {
                                    checkTwoState()
                                    selectedItemId = R.id.controls
                                }
                            }
                        }
                    })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
     /**   mediaPlayer = MediaPlayer()
        val afd = getResources().assets.openFd("music/music1.mp3")
        mediaPlayer!!.isLooping = true
        mediaPlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.getLength())
        mediaPlayer!!.setAudioAttributes(
                AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
        if(mPrefs!!.isMusicEnabled()) {
            mediaPlayer!!.prepare()
            mediaPlayer!!.start()
            isMusicPlaying = true
        } **/
        if (getAndroidV()) androidVDialog()
        if (needWelcomeDialog) welcomeDialog()
        if(!areNotificationsEnabled(NotificationManagerCompat.from(this))) {
            notificationsDialog()
        }
    }
    private fun areNotificationsEnabled(noman: NotificationManagerCompat) = when {
        noman.areNotificationsEnabled().not() -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            noman.notificationChannels.firstOrNull { channel ->
                channel.importance == NotificationManager.IMPORTANCE_NONE
            } == null
        }
        else -> true
    }
    private fun getAndroidV(): Boolean {
        return (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1)
    }
    private fun androidVDialog() {
            MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setMessage(R.string.unsupported_android)
                    .setNegativeButton(android.R.string.ok, null)
                    .show()
    }
    private fun notificationsDialog() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_warning)
            .setCancelable(false)
            .setMessage(R.string.notifications_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                NekoSettingsActivity.openSettings(this)
            }.show()
    }
    override fun onPrefsChanged() {}

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        (menu as MenuBuilder).setOptionalIconsVisible(true)
        menuInflater.inflate(R.menu.neko_general_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("MissingInflatedId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.aboutMenuId -> {
                if (!DEBUG) {
                    startActivity(Intent(this@NekoGeneralActivity, NekoAboutActivity::class.java))
                } else {
                    mPrefs!!.apply {
                        for (i in 0..10) {
                            addCat(NekoWorker.newRandomCat(this@NekoGeneralActivity, this, true))
                            addNCoins(666)
                        }
                    }
                }
                return true
            }
            R.id.achievementsMenuId -> {
                startActivity(Intent(this@NekoGeneralActivity, NekoAchievementsActivity::class.java))
                return true
            }

            R.id.promoMenuId -> {
                showPromoDialog()
                return true
            }

            R.id.settingsMenuId -> {
                startActivity(Intent(this@NekoGeneralActivity, NekoSettingsActivity::class.java))
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
    public override fun onDestroy() {
        super.onDestroy()
        if(mPrefs != null) {
            mPrefs?.setListener(null)
            if(mPrefs!!.isMusicEnabled()) {
                isMusicPlaying = false
                mediaPlayer?.release()
            }
        }
    }
    public override fun onPause() {
        super.onPause()
        if(isMusicPlaying) {
            mediaPlayer?.pause()
            isMusicPlaying = false
        }
    }

    public override fun onResume() {
        super.onResume()
        if(!isMusicPlaying && mPrefs!!.isMusicEnabled()) {
            mediaPlayer?.start()
            isMusicPlaying = true
        }
    }
    private fun setupDarkMode() {
        when (nekoprefs!!.getInt("theme", 0)) {
            0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    uimanager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    uimanager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    uimanager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }
    }

    private fun showPromoDialog() {
        val context: Context = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ContextThemeWrapper(this, theme) else this
        val view = LayoutInflater.from(context).inflate(R.layout.edit_text_promo, null)
        val text = view.findViewById<EditText>(R.id.editpromo)
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.promo)
                .setIcon(R.drawable.key)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    promo = text.text.toString()
                    checkPromo(promo!!)
                }.show()
    }

    private fun checkPromo(promo: String) {
        val code1availability = nekoprefs!!.getBoolean("code1availability", true)
        val code2availability = nekoprefs!!.getBoolean("code2availability", true)
        val code3availability = nekoprefs!!.getBoolean("code3availability", true)
        val code4availability = nekoprefs!!.getBoolean("code4availability", true)
        val code5availability = nekoprefs!!.getBoolean("code5availability", true)
        val code6availability = nekoprefs!!.getBoolean("code6availability", true)
        val editor = nekoprefs!!.edit()
        if (promo == nekoprefs!!.getString("code1", "")) {
            if (code1availability) {
                showSnackBar(getString(R.string.code_is_true), Snackbar.LENGTH_LONG, navbar)
                mPrefs!!.addNCoins(500)
                mPrefs!!.addLuckyBooster(1)
                mPrefs!!.addMoodBooster(1)
                editor.putBoolean("code1availability", false)
            } else {
                showSnackBar(getString(R.string.code_is_false), Snackbar.LENGTH_LONG, navbar)
            }
        } else if (promo == nekoprefs!!.getString("code2", "")) {
            if (code2availability) {
                showSnackBar(getString(R.string.code_is_true), Snackbar.LENGTH_LONG, navbar)
                mPrefs!!.addNCoins(1000)
                mPrefs!!.addMoodBooster(3)
                mPrefs!!.addLuckyBooster(5)
                editor.putBoolean("code2availability", false)
            } else {
                showSnackBar(getString(R.string.code_is_false), Snackbar.LENGTH_LONG, navbar)
            }
        } else if (promo == nekoprefs!!.getString("code3", "")) {
            if (code3availability) {
                showSnackBar(getString(R.string.code_is_true), Snackbar.LENGTH_LONG, navbar)
                mPrefs!!.addNCoins(2500)
                mPrefs!!.addMoodBooster(6)
                mPrefs!!.addLuckyBooster(7)
                editor.putBoolean("code3availability", false)
            } else {
                showSnackBar(getString(R.string.code_is_false), Snackbar.LENGTH_LONG, navbar)
            }
        } else if (promo == nekoprefs!!.getString("code4", "")) {
            if (code4availability) {
                showSnackBar(getString(R.string.code_is_true), Snackbar.LENGTH_LONG, navbar)
                mPrefs!!.addNCoins(10000)
                mPrefs!!.addMoodBooster(15)
                mPrefs!!.addLuckyBooster(25)
                editor.putBoolean("code4availability", false)
            } else {
                showSnackBar(getString(R.string.code_is_false), Snackbar.LENGTH_LONG, navbar)
            }
        } else if (promo == nekoprefs!!.getString("code5", "")) {
            if (code5availability) {
                showSnackBar(getString(R.string.code_is_true), Snackbar.LENGTH_LONG, navbar)
                mPrefs!!.addNCoins(1000)
                editor.putBoolean("code5availability", false)
            } else {
                showSnackBar(getString(R.string.code_is_false), Snackbar.LENGTH_LONG, navbar)
            }
        } else if (promo == nekoprefs!!.getString("code6", "")) {
            if (code6availability) {
                showSnackBar(getString(R.string.code_is_true), Snackbar.LENGTH_LONG, navbar)
                mPrefs!!.addNCoins(5555)
                mPrefs!!.addMoodBooster(15)
                mPrefs!!.addLuckyBooster(15)
                editor.putBoolean("code6availability", false)
            } else {
                showSnackBar(getString(R.string.code_is_false), Snackbar.LENGTH_LONG, navbar)
            }
        } else if (promo == "hello" || promo == "Hello" || promo == "hi") {
            showSnackBar("Hi!", Snackbar.LENGTH_SHORT, navbar)
        } else if (promo == "give me 1000 cats please") {
            CoroutineScope(Dispatchers.Default).launch {
                runOnUiThread {
                    showSnackBar("enjoy =)", Snackbar.LENGTH_LONG, navbar)
                }
                for (i in 0..1000) {
                    val cat: Cat = NekoWorker.newRandomCat(this@NekoGeneralActivity, mPrefs!!, true)
                    mPrefs?.addCat(cat)
                }
            }
        } else {
            showSnackBar(getString(R.string.wrong_code), Snackbar.LENGTH_LONG, navbar)
        }
        editor.apply()
    }

    private fun setupNavbarListener() {
        navbar?.apply {
            setOnItemSelectedListener { item: MenuItem ->
                if (item.itemId == R.id.collection) {
                    viewPager!!.currentItem = 0
                    return@setOnItemSelectedListener true
                } else if (item.itemId == R.id.controls) {
                    if (checkState() != -1) {
                        viewPager!!.currentItem = 1
                        animate()?.translationY(0f)?.setDuration(200)
                    } else {
                        viewPager!!.currentItem = 1
                        val editor = nekoprefs!!.edit()
                        editor.putInt("state", 0)
                        editor.apply()
                        animate()?.translationY(0f)?.setDuration(200)
                        MaterialAlertDialogBuilder(this@NekoGeneralActivity)
                            .setTitle(R.string.app_name_neko)
                            .setIcon(R.drawable.ic_fullcat_icon)
                            .setMessage(R.string.welcome_dialog_part3)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                                showSnackBar(getString(R.string.welcome_dialog_final), Snackbar.LENGTH_LONG, navbar)
                                setCurrentState(0)
                            }.show()
                    }
                    val editor = nekoprefs!!.edit()
                    editor.putInt("state", 0)
                    editor.apply()
                    return@setOnItemSelectedListener true
                }
                false
            }
            ViewCompat.setOnApplyWindowInsetsListener(this) { v: View, insets: WindowInsetsCompat ->
                val pB = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).top
                v.setPadding(0, 0, 0, pB)
                WindowInsetsCompat.CONSUMED
            }
        }
    }
    fun checkTwoState() {
        if (nekoprefs?.getInt("state", 1) == 2) {
            nekoprefs?.edit()?.putInt("state", 0)?.apply()
            showTipAgain = false
        }
    }
    private fun setupState() {
        when (checkState()) {
            0 -> {}
            1 -> {
                startActivity(Intent(this, NekoActivationActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            2 -> needWelcomeDialog = true
        }
    }
    private fun checkState(): Int {
        state = nekoprefs!!.getInt("state", 1)
        return state
    }
    private fun welcomeDialog() {
        needWelcomeDialog = false
        MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name_neko)
                .setIcon(R.drawable.ic_bowl)
                .setCancelable(false)
                .setMessage(R.string.welcome_dialog)
                .setPositiveButton(R.string.get_prize
                ) { _: DialogInterface?, _: Int -> gift }.show()
    }

    private val gift: Unit
        get() {
            mPrefs?.apply {
                for (i in 0..6) {
                    addCat(NekoWorker.newRandomCat(this@NekoGeneralActivity, this, true))
                }
            }
            setCurrentState(-1)
            MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_name_neko)
                    .setIcon(R.drawable.ic_fullcat_icon)
                    .setMessage(R.string.welcome_dialog_part2)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok
                    ) { _: DialogInterface?, _: Int -> showSnackBar(getString(R.string.open_controls_tip), Snackbar.LENGTH_LONG, navbar) }.show()
        }

    private fun setCurrentState(state: Int) {
        val editor = nekoprefs!!.edit()
        editor.putInt("state", state)
        editor.apply()
    }

    companion object {
        private const val DEBUG = false
        var isMusicPlaying = false
        var mediaPlayer: MediaPlayer? = null

        @JvmStatic
        fun showSnackBar(text: String?, time: Int, view: View?) {
            val snackbar = Snackbar.make(view!!, text!!, time)
            snackbar.setAnchorView(view)
            snackbar.show()
        }
    }

    class NekoAdapter(fragment: FragmentActivity) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            val fragment = if (position == 1) {
                CatControlsFragment()
            } else {
                NekoLandFragment()
            }
            return fragment
        }
    }
}