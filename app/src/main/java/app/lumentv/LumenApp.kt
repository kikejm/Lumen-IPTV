package app.lumentv

import android.app.Application
import android.content.Context
import app.lumentv.di.AppGraph

class LumenApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.onAppStart()
    }
}

/** Acceso al grafo de dependencias desde cualquier `Context`. */
val Context.graph: AppGraph
    get() = (applicationContext as LumenApp).graph
