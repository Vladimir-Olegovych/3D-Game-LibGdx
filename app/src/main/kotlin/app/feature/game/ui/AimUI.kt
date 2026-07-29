package app.feature.game.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.texture.DefaultsTextures
import core.ui.UIGetter

class AimUI : LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        center()
    }

    override fun launch(gContext: GContext) {
        val texture = DefaultsTextures.WHITE
        layout.add(Image(texture)).size(6f)
    }

    override fun getUI(): Actor = layout
}