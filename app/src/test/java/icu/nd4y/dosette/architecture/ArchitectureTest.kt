package icu.nd4y.dosette.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Guards the layering convention the packages imply: `domain` is pure Kotlin.
 * Until now this was only a convention; a broken import compiled fine.
 */
class ArchitectureTest {
    @Test
    fun `domain does not touch the Android framework`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.packagee?.name?.startsWith("icu.nd4y.dosette.domain") == true }
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith("android.") || import.name.startsWith("androidx.")
                }
            }
    }

    @Test
    fun `domain does not depend on data, ui or reminders layers`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.packagee?.name?.startsWith("icu.nd4y.dosette.domain") == true }
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith("icu.nd4y.dosette.data") ||
                        import.name.startsWith("icu.nd4y.dosette.ui") ||
                        import.name.startsWith("icu.nd4y.dosette.reminders")
                }
            }
    }
}
