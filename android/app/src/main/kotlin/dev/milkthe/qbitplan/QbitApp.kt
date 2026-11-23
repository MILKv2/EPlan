package dev.milkthe.qbitplan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punkt startowy aplikacji; @HiltAndroidApp uruchamia wstrzykiwanie zależności. */
@HiltAndroidApp
class QbitApp : Application()
