package com.recall.android.capture

internal object AccessibilityCapturePolicy {
    fun belongsToEventPackage(eventPackage: String, nodePackage: CharSequence?): Boolean =
        nodePackage?.toString() == eventPackage
}
