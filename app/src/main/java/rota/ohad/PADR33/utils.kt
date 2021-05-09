package rota.ohad.PADR33

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics

fun knobDiameter() : Int{
    val width = Resources.getSystem().displayMetrics.widthPixels
    val height = Resources.getSystem().displayMetrics.heightPixels
    val knobDia = if (width > height) height*0.2 else width * 0.5
    return  knobDia.toInt()
}
fun convertDpToPx (dp: Float, context: Context): Float {
    return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
}
