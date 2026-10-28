package com.marinecircuits.doit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingTest {
    @Test fun quantitiesAreFreeTextAndRequireACompletedNonemptySuffix() {
        assertEquals("Tomatoes" to "2 × 400 g", Shopping.item("Tomatoes [2 × 400 g]"))
        assertEquals("Milk" to "a couple", Shopping.item("Milk [a couple]"))
        for (title in listOf("Milk []", "Milk [  ]", "Milk [two", "Milk[two]", "[two]")) {
            assertEquals(title to null, Shopping.item(title))
        }
    }
}
