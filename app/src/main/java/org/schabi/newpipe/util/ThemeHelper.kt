package org.schabi.newpipe.util

import android.content.Context
import android.preference.PreferenceManager
import android.support.annotation.AttrRes
import android.support.annotation.StyleRes
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import android.view.ContextThemeWrapper
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException

object ThemeHelper {

    /**
     * Apply the selected theme (on NewPipe settings) in the context,
     * themed according with the styles defined for the service .
     *
     * @param context   context that the theme will be applied
     * @param serviceId the theme will be styled to the service with this id,
     *                  pass -1 to get the default style
     */
    @JvmOverloads
    fun setTheme(context: Context, serviceId: Int = -1) {
        context.setTheme(getThemeForService(context, serviceId))
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme
     *
     * @param context context to get the preference
     */
    fun isLightThemeSelected(context: Context): Boolean {
        return getSelectedThemeString(context) == context.resources.getString(R.string.light_theme_key)
    }


    /**
     * Create and return a wrapped context with the default selected theme set.
     *
     * @param baseContext the base context for the wrapper
     * @return a wrapped-styled context
     */
    fun getThemedContext(baseContext: Context): Context {
        return ContextThemeWrapper(baseContext, getThemeForService(baseContext, -1))
    }

    /**
     * Return the selected theme without being styled to any service (see [.getThemeForService]).
     *
     * @param context context to get the selected theme
     * @return the selected style (the default one)
     */
    @StyleRes
    fun getDefaultTheme(context: Context): Int {
        return getThemeForService(context, -1)
    }

    /**
     * Return a dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    fun getDialogTheme(context: Context): Int {
        return if (isLightThemeSelected(context)) R.style.LightDialogTheme else R.style.DarkDialogTheme
    }

    /**
     * Return the selected theme styled according to the serviceId.
     *
     * @param context   context to get the selected theme
     * @param serviceId return a theme styled to this service,
     *                  -1 to get the default
     * @return the selected style (styled)
     */
    @StyleRes
    fun getThemeForService(context: Context, serviceId: Int): Int {
        val lightTheme = context.resources.getString(R.string.light_theme_key)
        val darkTheme = context.resources.getString(R.string.dark_theme_key)
        val blackTheme = context.resources.getString(R.string.black_theme_key)

        val selectedTheme = getSelectedThemeString(context)  // from default SharedPreferences

        val defaultTheme = when (selectedTheme) {
            lightTheme -> R.style.LightTheme
            blackTheme -> R.style.BlackTheme
            darkTheme -> R.style.DarkTheme
            else -> R.style.DarkTheme
        }

        if (serviceId <= -1) {
            return defaultTheme
        }

        val service: StreamingService
        try {
            service = NewPipe.getService(serviceId)
        } catch (ignored: ExtractionException) {
            return defaultTheme
        }

        var themeName = when (selectedTheme) {
            lightTheme -> "LightTheme"
            blackTheme -> "BlackTheme"
            darkTheme -> "DarkTheme"
            else -> "DarkTheme"
        }

        themeName += "." + service.serviceInfo.name
        val resourceId = context.resources.getIdentifier(themeName, "style", context.packageName)

        return if (resourceId > 0) {
            resourceId
        } else defaultTheme
    }

    @StyleRes
    fun getSettingsThemeStyle(context: Context): Int {
        val lightTheme = context.resources.getString(R.string.light_theme_key)
        val darkTheme = context.resources.getString(R.string.dark_theme_key)
        val blackTheme = context.resources.getString(R.string.black_theme_key)

        val selectedTheme = getSelectedThemeString(context)

        return when (selectedTheme) {
            lightTheme -> R.style.LightSettingsTheme
            blackTheme -> R.style.BlackSettingsTheme
            darkTheme -> R.style.DarkSettingsTheme
            else -> R.style.DarkSettingsTheme  // Fallback
        }
    }

    /**
     * Get a resource id getTabFrom a resource styled according to the the context's theme.
     */
    fun resolveResourceIdFromAttr(context: Context, @AttrRes attr: Int): Int {
        val typedArray = context.theme.obtainStyledAttributes(intArrayOf(attr))
        val attributeResourceId = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        return attributeResourceId
    }

    /**
     * Get a color getTabFrom an attr styled according to the the context's theme.
     */
    fun resolveColorFromAttr(context: Context, @AttrRes attrColor: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attrColor, value, true)

        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else value.data

    }

    private fun getSelectedThemeString(context: Context): String? {
        val themeKey = context.getString(R.string.theme_key)
        val defaultTheme = context.resources.getString(R.string.default_theme_value)
        return PreferenceManager.getDefaultSharedPreferences(context).getString(themeKey, defaultTheme)
    }

    /**
     * This will get the R.drawable.* resource to which attr is currently pointing to.
     *
     * @param attr a R.attribute.* resource value
     * @param context the context to use
     * @return a R.drawable.* resource value
     */
    fun getIconByAttr(attr: Int, context: Context): Int {
        return context.obtainStyledAttributes(intArrayOf(attr))
                .getResourceId(0, -1)
    }
}
