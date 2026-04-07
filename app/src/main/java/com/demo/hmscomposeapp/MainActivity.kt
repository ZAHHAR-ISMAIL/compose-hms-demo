package com.demo.hmscomposeapp

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.demo.hmscomposeapp.customerdetails.CustomerDetailsFragmentCompose
import com.huawei.hms.maps.MapsInitializer

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)

        val containerId = View.generateViewId()
        setContentView(
            FrameLayout(this).apply {
                id = containerId
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        )

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(containerId, CustomerDetailsFragmentCompose.newInstance())
                .commit()
        }
    }
}
