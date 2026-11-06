package com.ilan.meowsolver

import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.BoardReader
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

data class Expected(val name: String, val n: Int, val cats: Int, val solution: IntArray)

object Fixtures {
    private val cl = Fixtures::class.java.classLoader!!
    fun image(name: String): BufferedImage = ImageIO.read(cl.getResource("screens/$name")!!)
    fun board(name: String): Board {
        val im = image(name)
        val w = im.width; val h = im.height
        return BoardReader.read(im.getRGB(0, 0, w, h, null, 0, w), w, h)
    }
    fun expected(): List<Expected> = cl.getResource("expected.csv")!!.readText().lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val p = line.split(',')
            Expected(p[0], p[1].toInt(), p[2].toInt(), p[3].split(' ').map(String::toInt).toIntArray())
        }
}
