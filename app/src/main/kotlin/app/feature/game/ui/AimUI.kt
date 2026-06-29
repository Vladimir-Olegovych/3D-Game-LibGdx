package app.feature.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.texture.ColorDrawable
import core.ui.UIGetter

class AimUI : LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        center()
    }

    override fun launch(context: Context) {
        val color = Color.WHITE
        val drawable = ColorDrawable(color.r, color.g, color.b, color.a)

        val vertical = Image(drawable).apply { setSize(3f, 30f) }

        val horizontal = Image(drawable).apply { setSize(30f, 3f) }

        layout.add(vertical).padBottom(15f).row()
        layout.add(horizontal).padTop(-3f)
    }

    override fun getUI(): Actor = layout
}