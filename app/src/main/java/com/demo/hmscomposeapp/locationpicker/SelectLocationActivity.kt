package com.demo.hmscomposeapp.locationpicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

class SelectLocationActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

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
                .replace(
                    containerId,
                    SelectLocationFragment.newInstance(
                        locationDetails = intent.parcelable(Constants.LOCATION_DETAILS_PARAM),
                        flowName = intent.getStringExtra(Constants.FLOW_NAME_PARAM).orEmpty(),
                        poid = intent.getStringExtra(Constants.POID_PARAM),
                        eligibilityResponse = intent.parcelable(Constants.ELIGIBILITY_RESPONSE)
                    )
                )
                .commit()
        }
    }

    companion object {
        fun callingIntent(
            context: Context,
            locationDetails: LocationDetails? = null,
            flowName: String = "",
            poid: String? = null,
            eligibilityResponse: FiberEligibilityResponse? = null
        ): Intent {
            return Intent(context, SelectLocationActivity::class.java).apply {
                putExtra(Constants.LOCATION_DETAILS_PARAM, locationDetails)
                putExtra(Constants.FLOW_NAME_PARAM, flowName)
                putExtra(Constants.POID_PARAM, poid)
                putExtra(Constants.ELIGIBILITY_RESPONSE, eligibilityResponse)
            }
        }
    }
}
