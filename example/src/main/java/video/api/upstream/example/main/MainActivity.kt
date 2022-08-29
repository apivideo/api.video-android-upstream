package video.api.upstream.example.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import video.api.upstream.example.R
import video.api.upstream.example.databinding.ActivityMainBinding
import video.api.upstream.example.preferences.PreferencesActivity
import video.api.upstream.example.utils.DialogHelper

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
    }

    override fun onResume() {
        super.onResume()

        when {
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED) -> {
                launchFragment()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    || shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                DialogHelper.showAlertDialog(
                    this,
                    getString(R.string.permissions),
                    getString(R.string.permission_not_granted)
                )
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if ((permissions[Manifest.permission.CAMERA] == true)
                && (permissions[Manifest.permission.RECORD_AUDIO] == true)
            ) {
                launchFragment()
            } else {
                showPermissionErrorAndFinish()
            }
        }

    private fun launchFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, PreviewFragment())
            .commitNow()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                goToPreferencesActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun goToPreferencesActivity() {
        val intent = Intent(this, PreferencesActivity::class.java)
        startActivity(intent)
    }

    private fun showPermissionErrorAndFinish() {
        DialogHelper.showPermissionAlertDialog(this) { this.finish() }
    }
}