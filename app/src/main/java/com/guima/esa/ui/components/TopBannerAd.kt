package com.guima.esa.ui.components

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.guima.esa.R
import kotlin.math.max

private const val DEBUG_BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun TopBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuredAdUnitId = context.getString(R.string.admob_banner_top_unit_id)
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val adUnitId = if (isDebugBuild) DEBUG_BANNER_TEST_ID else configuredAdUnitId

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthDp = max(maxWidth.value.toInt(), 320)
        val adSize = remember(availableWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, availableWidthDp)
        }
        val adView = remember(context, adUnitId, availableWidthDp) {
            AdView(context).apply {
                this.adUnitId = adUnitId
                setAdSize(adSize)
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                        Log.e("TopBannerAd", "Falha ao carregar banner: ${error.message}")
                    }
                }
            }
        }

        LaunchedEffect(adView) {
            adView.loadAd(AdRequest.Builder().build())
        }

        DisposableEffect(adView) {
            onDispose { adView.destroy() }
        }

        AndroidView(
            factory = { adView },
            modifier = Modifier
                .fillMaxWidth()
                .height(adSize.height.dp)
        )
    }
}
