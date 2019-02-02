package org.schabi.newpipe.about

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_licenses.view.*
import kotlinx.android.synthetic.main.item_software_component.view.*
import org.schabi.newpipe.R
import java.util.*

/**
 * Fragment containing the software licenses
 */
class LicenseFragment : Fragment() {
    private var softwareComponents: Array<SoftwareComponent>? = null
    private var mComponentForContextMenu: SoftwareComponent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        softwareComponents = arguments!!.getParcelableArray(ARG_COMPONENTS) as Array<SoftwareComponent>

        // Sort components by name
        Arrays.sort(softwareComponents) { o1, o2 -> o1.name!!.compareTo(o2.name!!) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_licenses, container, false)

        rootView.appReadLicenseLink.setOnClickListener(OnReadFullLicenseClickListener())

        for (component in softwareComponents!!) {
            val componentView = inflater.inflate(R.layout.item_software_component, container, false)

            componentView.softwareName.text = component.name
            componentView.copyright.text = context!!.getString(R.string.copyright,
                    component.years,
                    component.copyrightOwner,
                    component.license?.abbreviation)

            componentView.tag = component
            componentView.setOnClickListener { v ->
                val context = v.context
                if (context != null) {
                    showLicense(context, component.license!!)
                }
            }
            rootView.softwareComponentsView.addView(componentView)
            registerForContextMenu(componentView)
        }
        return rootView
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val inflater = activity!!.menuInflater
        val component = v.tag as SoftwareComponent
        menu.setHeaderTitle(component.name)
        inflater.inflate(R.menu.software_component, menu)
        super.onCreateContextMenu(menu, v, menuInfo)
        mComponentForContextMenu = v.tag as SoftwareComponent
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        // item.getMenuInfo() is null so we use the tag of the view
        val component = mComponentForContextMenu ?: return false
        when (item!!.itemId) {
            R.id.action_website -> {
                openWebsite(component.link!!)
                return true
            }
            R.id.action_show_license -> showLicense(context, component.license!!)
        }
        return false
    }

    private fun openWebsite(componentLink: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(componentLink))
        startActivity(browserIntent)
    }

    private class OnReadFullLicenseClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            LicenseFragment.showLicense(v.context, StandardLicenses.GPL3)
        }
    }

    companion object {

        private const val ARG_COMPONENTS = "components"

        fun newInstance(softwareComponents: Array<SoftwareComponent>?): LicenseFragment {
            if (softwareComponents == null) {
                throw NullPointerException("softwareComponents is null")
            }
            val fragment = LicenseFragment()
            val bundle = Bundle()
            bundle.putParcelableArray(ARG_COMPONENTS, softwareComponents)
            fragment.arguments = bundle
            return fragment
        }

        /**
         * Shows a popup containing the license
         * @param context the context to use
         * @param license the license to show
         */
        fun showLicense(context: Context?, license: License) {
            LicenseFragmentHelper(context as Activity?).execute(license)
        }
    }
}
