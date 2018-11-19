package us.shandian.giga.ui.common

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar

import org.schabi.newpipe.R

abstract class ToolbarActivity : AppCompatActivity() {
    private lateinit var mToolbar: Toolbar

    protected abstract val layoutResource: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResource)

        mToolbar = this.findViewById(R.id.toolbar)

        setSupportActionBar(mToolbar)
    }
}
