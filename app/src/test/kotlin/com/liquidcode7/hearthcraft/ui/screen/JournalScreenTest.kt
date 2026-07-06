package com.liquidcode7.hearthcraft.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalScreenTest {

    @Test
    fun `warden ability is titled The Horn of Gondor`() {
        val (title, _) = roleAbility("warden")!!
        assertEquals("The Horn of Gondor", title)
    }

    @Test
    fun `ranged fighter ability is titled Black Arrow`() {
        val (title, _) = roleAbility("fighter", "ranged")!!
        assertEquals("Black Arrow", title)
    }

    @Test
    fun `melee fighter ability is titled Bullroarer's Five-Iron`() {
        val (title, _) = roleAbility("fighter", "melee")!!
        assertEquals("Bullroarer's Five-Iron", title)
    }

    @Test
    fun `fighter defaults to ranged when build is not specified`() {
        val (title, _) = roleAbility("fighter")!!
        assertEquals("Black Arrow", title)
    }

    @Test
    fun `keeper ability is titled Hands of Healing`() {
        val (title, _) = roleAbility("keeper")!!
        assertEquals("Hands of Healing", title)
    }

    @Test
    fun `captain ability mentions ere the sun rises`() {
        val (title, desc) = roleAbility("captain")!!
        assertEquals("Wrath, Ruin, and the Red Dawn", title)
        assertTrue(desc.contains("ere the sun rises"))
    }

    @Test
    fun `unknown role returns null`() {
        assertNull(roleAbility("nobody"))
    }
}
