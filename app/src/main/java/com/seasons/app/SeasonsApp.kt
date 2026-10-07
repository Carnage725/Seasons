package com.seasons.app

import android.app.Application
import com.seasons.app.data.AppDatabase
import com.seasons.app.data.Repository

class SeasonsApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }
}
